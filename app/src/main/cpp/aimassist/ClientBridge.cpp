#include "ClientBridge.hpp"

#include <android/log.h>

#include "DiagLog.hpp"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <csetjmp>
#include <csignal>
#include <cstdint>
#include <cstring>
#include <cstdio>
#include <mutex>
#include <pthread.h>
#include <type_traits>
#include <utility>
#include <vector>

#include "AimAssist.hpp"
#include "Memory.hpp"
#include "Offsets.hpp"
#include "CombatState.hpp"
#include "pl/memory/Hook.hpp"
#include "pl/memory/Signature.hpp"

namespace aimbot {
namespace {

constexpr const char *kTag = "AimAssistBridge";


struct DistanceSortedActor {
  void *actor = nullptr;
  float distance = 0.0f;
  float pad = 0.0f;
};

struct ActorVec {
  DistanceSortedActor *begin = nullptr;
  DistanceSortedActor *end = nullptr;
  DistanceSortedActor *cap = nullptr;
};

struct Rotation {
  float pitch = 0.0f;
  float yaw = 0.0f;
};

using NormalTickFn = void (*)(void *);
// ClientInstance::update(this, bool, bool) — оба флага передаются в
// оригинал как есть, иначе третий аргумент уходит мусором.
using ClientInstanceUpdateFn = void *(*)(void *, bool, bool);
using GetLocalPlayerFn = void *(*)(void *);
using ApplyTurnDeltaFn = void (*)(void *, const Rotation &);
using FetchNearbyActorsFn = ActorVec (*)(void *, const Vec3 *, int);
using ActorIsPlayerFn = bool (*)(void *);
// Touch-HUD builder: принимает указатель на объект-layout с вектором контролов.
using ControlBuilderFn = void (*)(void *);
using PlayerAuthInputWriteFn = void *(*)(void *, void *, void *, void *);

NormalTickFn g_normalTickOriginal = nullptr;
ClientInstanceUpdateFn g_clientUpdateOriginal = nullptr;
GetLocalPlayerFn g_getLocalPlayer = nullptr;
ApplyTurnDeltaFn g_applyTurnDelta = nullptr;
FetchNearbyActorsFn g_fetchNearbyActors = nullptr;
ActorIsPlayerFn g_actorIsPlayer = nullptr;
ControlBuilderFn g_controlBuilderOriginal = nullptr;
PlayerAuthInputWriteFn g_playerAuthWriteOriginal = nullptr;

std::atomic<void *> g_clientInstance{nullptr};
std::atomic<void *> g_localPlayer{nullptr};
std::atomic<int> g_swingStyle{0};
std::atomic<int> g_swingArmTicks{0};
// Swing tuning in percent: speed stretches/compresses the curve, amplitude
// scales the easing deviation from the vanilla linear progress.
std::atomic<int> g_swingSpeed{100};
std::atomic<int> g_swingAmplitude{100};
std::atomic<uintptr_t> g_attackControl{0};
std::atomic<uintptr_t> g_controlLayout{0};
std::atomic<bool> g_ctlScanStarted{false};
std::atomic<bool> g_initialized{false};
std::atomic<bool> g_hooksInstalled{false};
std::mutex g_initMutex;

std::chrono::steady_clock::time_point g_lastFrame{};
std::atomic<ClientBridge::MovePacketSender> g_movePacketSender{nullptr};
std::atomic<bool> g_silentAimActive{false};
std::atomic<float> g_silentAimPitch{0.0f};
std::atomic<float> g_silentAimYaw{0.0f};

void *playerAuthInputWriteDetour(void *packet, void *a1, void *a2, void *a3) {
  if (packet && g_silentAimActive.load(std::memory_order_relaxed)) {
    // Silent aim: write the aim rotation into the outgoing packet only. The
    // local camera/actor rotation is untouched, so the user sees nothing, but
    // the server receives the rotation the attack is aligned with.
    const float pitch = g_silentAimPitch.load(std::memory_order_relaxed);
    const float yaw = g_silentAimYaw.load(std::memory_order_relaxed);
    if (std::isfinite(pitch) && std::isfinite(yaw)) {
      auto *rot = reinterpret_cast<float *>(
          static_cast<char *>(packet) + offsets::kPlayerAuthInputRot);
      rot[0] = pitch;
      rot[1] = yaw;
      *reinterpret_cast<float *>(static_cast<char *>(packet) +
                                 offsets::kPlayerAuthInputYHeadRot) = yaw;
    }
  }
  if (packet && combatstate::sprintSuppressed()) {
    auto *bits = reinterpret_cast<uint64_t *>(
        static_cast<char *>(packet) + offsets::kPlayerAuthInputInputData);
    auto clearBit = [bits](int bit) {
      bits[bit >> 6] &= ~(1ULL << (bit & 63));
    };
    clearBit(4);   // SPRINT_DOWN
    clearBit(20);  // SPRINTING
    clearBit(25);  // START_SPRINTING
    bits[26 >> 6] |= 1ULL << (26 & 63);  // STOP_SPRINTING
  }
  return g_playerAuthWriteOriginal
             ? g_playerAuthWriteOriginal(packet, a1, a2, a3)
             : nullptr;
}

bool isPlausiblePointer(uintptr_t address) {
  return mem::isPlausiblePointer(address);
}

// process_vm_readv проверяет, что страница реально отображена. Это важно:
// просто memcpy по «правдоподобному» указателю из компонента актёра роняет игру,
// если клиент успел пересоздать актёра или оффсет не совпал с версией.
template <typename T>
bool safeRead(uintptr_t address, T &out) {
  return mem::read(address, out);
}

template <typename T>
bool safeWrite(uintptr_t address, const T &value) {
  return mem::write(address, value);
}

// Safe reimplementation of Actor::isPlayer (verified against the game's
// disassembly at libminecraftpe+0xfd8e120). It checks presence of the player
// component (hash 0xf95d258f) via the actor's component table using only
// guaranteed-safe reads, so it can never fault inside the game and can never
// leave a game lock held (which a SIGSEGV longjmp would).
bool playerComponentQuery(uintptr_t actor) {
  uintptr_t ctx = 0;
  if (!safeRead(actor + 0x10, ctx) || !isPlausiblePointer(ctx)) return false;
  constexpr uint32_t kHash = 0xf95d258fu;
  uintptr_t begin = 0;
  uintptr_t end = 0;
  if (!safeRead(ctx + 0x38, begin) || !safeRead(ctx + 0x40, end)) return false;
  if (!isPlausiblePointer(begin) || !isPlausiblePointer(end) || end < begin) {
    return false;
  }
  const uint64_t span = end - begin;
  if (span < sizeof(void *) || span > (1u << 20)) return false;
  const uint64_t count = span / sizeof(void *);
  const uint64_t index = (count - 1) & static_cast<uint64_t>(kHash);
  uint32_t field18 = 0;
  if (!safeRead(actor + 0x18, field18)) return false;
  uintptr_t probe = begin + index * sizeof(void *);
  uintptr_t table = 0;
  if (!safeRead(ctx + 0x50, table) || !isPlausiblePointer(table)) return false;
  uintptr_t tail = 0;
  if (!safeRead(ctx + 0x58, tail)) return false;
  for (int step = 0; step < 64; ++step) {
    uintptr_t packed = 0;
    if (!safeRead(probe, packed)) return false;
    if (packed == static_cast<uintptr_t>(-1)) return false;
    uintptr_t slot = table + packed * 32;
    uint32_t slotHash = 0;
    if (!safeRead(slot + 8, slotHash)) return false;
    if (slotHash != kHash) {
      probe = slot;
      continue;
    }
    if (slot == tail) return false;
    uintptr_t entry = 0;
    if (!safeRead(slot + 0x10, entry) || entry == 0) return false;
    uintptr_t a = 0;
    uintptr_t b = 0;
    if (!safeRead(entry + 8, a) || !safeRead(entry + 16, b)) return false;
    if (b < a) return false;
    const uint64_t idx2 = (static_cast<uint64_t>(field18) >> 11) & 0x7Fu;
    if (idx2 >= ((b - a) >> 3)) return false;
    uintptr_t node = 0;
    if (!safeRead(a + idx2 * 8, node) || node == 0) return false;
    const uint32_t low = field18 & 0x3FFFFu;
    const uint32_t high = field18 & 0xFFFC0000u;
    const uint32_t off = low & 0x7FFu;
    uint32_t value = 0;
    if (!safeRead(node + static_cast<uintptr_t>(off) * 4, value)) return false;
    return (value ^ high) < 0x40000u;
  }
  return false;
}

bool isLiveActor(void *actor) {
  const auto base = reinterpret_cast<uintptr_t>(actor);
  if (!isPlausiblePointer(base)) return false;
  uintptr_t vtable = 0;
  if (!safeRead(base, vtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(vtable))) {
    return false;
  }
  uintptr_t stateVector = 0;
  if (!safeRead(base + offsets::kActorStateVectorComponent, stateVector) ||
      !isPlausiblePointer(stateVector)) {
    return false;
  }
  Vec3 position{};
  if (!safeRead(stateVector + offsets::kStatePosition, position)) return false;
  return std::isfinite(position.x) && std::isfinite(position.y) &&
         std::isfinite(position.z) && std::fabs(position.x) < 3.0e7f &&
         std::fabs(position.y) < 3.0e7f && std::fabs(position.z) < 3.0e7f;
}

// std::string в libc++ держит короткие строки inline (SSO), а длинные — в куче.
// Имя action ("button.destroy_or_attack", 24 символа) всегда в куче, но
// проверяем оба варианта, чтобы не зависеть от ABI-деталей строки.
bool controlActionEquals(uintptr_t control, const char *expected) {
  const size_t len = std::strlen(expected);
  char buf[64];
  if (len >= sizeof(buf)) return false;
  const uintptr_t stringAddr = control + offsets::kControlActionString;
  // libc++ SSO: данные короткой строки могут начинаться с +0 или с +1
  // (в __short первый байт — __size_, дальше __data_). Пробуем оба.
  if (mem::read(stringAddr, buf, len) &&
      std::memcmp(buf, expected, len) == 0) {
    return true;
  }
  if (mem::read(stringAddr + 1, buf, len) &&
      std::memcmp(buf, expected, len) == 0) {
    return true;
  }
  uintptr_t data = 0;
  if (mem::read(stringAddr, data) && mem::isPlausiblePointer(data) &&
      mem::read(data, buf, len) && std::memcmp(buf, expected, len) == 0) {
    return true;
  }
  return false;
}

// Хук общего touch-HUD builder'а: запоминаем объект-layout. Кнопку атаки
// ищем заново при каждом обращении, потому что вектор контролов может быть
// перемещён (реаллокация/merge) — указатель на элемент тогда протухает,
// а сам layout-объект остаётся стабильным.
void controlBuilderDetour(void *layout) {
  if (g_controlBuilderOriginal) {
    g_controlBuilderOriginal(layout);
  }
  if (!layout) return;
  g_controlLayout.store(reinterpret_cast<uintptr_t>(layout),
                        std::memory_order_relaxed);
}

// Ищет контрол кнопки атаки в текущем векторе layout-объекта.
uintptr_t findAttackControl(uintptr_t layout) {
  if (!layout) return 0;
  uintptr_t begin = 0;
  uintptr_t end = 0;
  if (!safeRead(layout + offsets::kControlVectorBegin, begin) ||
      !safeRead(layout + offsets::kControlVectorEnd, end)) {
    return 0;
  }
  if (!mem::isPlausiblePointer(begin) || !mem::isPlausiblePointer(end) ||
      end <= begin || (end - begin) > 0x8000) {
    return 0;
  }
  for (uintptr_t p = begin; p + offsets::kControlStride <= end;
       p += offsets::kControlStride) {
    if (controlActionEquals(p, offsets::kAttackActionName)) {
      return p;
    }
  }
  return 0;
}

// Резервный путь, если хук builder'а установился уже после постройки HUD:
// ищем контрол атаки по куче (action-строка + иконка). Определяется ниже,
// после readMaps(). Скан идёт в фоновом потоке, чтобы не морозить кадр.
uintptr_t scanHeapForAttackControl();
void *ctlScanThread(void *);

// Сырой дамп структуры контрола + попытка прочитать action-строку. Нужен,
// чтобы разобрать кандидата, даже если его action не из известного списка.
void logControlRaw(uintptr_t ctl, const char *why) {
  unsigned char raw[offsets::kControlStride];
  if (!mem::read(ctl, raw, sizeof(raw))) return;
  char action[48] = {0};
  char head[8] = {0};
  if (mem::read(ctl, head, 7) && std::memcmp(head, "button.", 7) == 0) {
    std::memcpy(action, head, 7);
    mem::read(ctl + 7, action + 7, sizeof(action) - 8);
  } else {
    uintptr_t p = 0;
    if (mem::read(ctl, p) && mem::isPlausiblePointer(p)) {
      mem::read(p, action, sizeof(action) - 1);
    }
  }
  for (size_t i = 0; i < sizeof(action); ++i) {
    if (action[i] < 0x20 || action[i] > 0x7e) {
      action[i] = 0;
      break;
    }
  }
  aimbot::diagLog(kTag, "ctl scan: cand %s @0x%zx action='%s'", why, ctl,
                  action);
  for (size_t off = 0; off < sizeof(raw); off += 16) {
    aimbot::diagLog(kTag,
                    "  %02zx: %02x %02x %02x %02x %02x %02x %02x %02x "
                    "%02x %02x %02x %02x %02x %02x %02x %02x",
                    off, raw[off + 0], raw[off + 1], raw[off + 2],
                    raw[off + 3], raw[off + 4], raw[off + 5], raw[off + 6],
                    raw[off + 7], raw[off + 8], raw[off + 9], raw[off + 10],
                    raw[off + 11], raw[off + 12], raw[off + 13], raw[off + 14],
                    raw[off + 15]);
  }
  float rect[8];
  std::memcpy(rect, raw + offsets::kControlRect, sizeof(rect));
  aimbot::diagLog(kTag,
                  "  rect24: %.5f %.5f %.5f %.5f | %.5f %.5f %.5f %.5f",
                  rect[0], rect[1], rect[2], rect[3], rect[4], rect[5],
                  rect[6], rect[7]);
  float posX = 0.0f;
  float posY = 0.0f;
  float scale = 0.0f;
  std::memcpy(&posX, raw + offsets::kControlPosX, sizeof(posX));
  std::memcpy(&posY, raw + offsets::kControlPosY, sizeof(posY));
  std::memcpy(&scale, raw + offsets::kControlScale, sizeof(scale));
  aimbot::diagLog(kTag, "  pos54=%.5f pos58=%.5f scale64=%.5f", posX, posY,
                  scale);
}

// Дамп структуры найденной кнопки атаки. Нужен, чтобы по рантайм-значениям
// вычислить, в каком поле лежит экранная позиция (в файле она нулевая и
// выставляется layout-проходом уже после конструктора).
bool dumpAttackControl() {
  const uintptr_t layout = g_controlLayout.load(std::memory_order_relaxed);
  uintptr_t ctl = findAttackControl(layout);
  if (!ctl) {
    ctl = g_attackControl.load(std::memory_order_relaxed);
  }
  if (!ctl && !g_ctlScanStarted.exchange(true)) {
    pthread_t thread;
    if (pthread_create(&thread, nullptr, &ctlScanThread, nullptr) == 0) {
      pthread_detach(thread);
      aimbot::diagLog(kTag, "touch ctl: layout=0x%zx, background scan started",
                      layout);
    }
  }
  if (!ctl) {
    aimbot::diagLog(kTag, "touch ctl: not captured yet (layout=0x%zx)", layout);
    return false;
  }
  g_attackControl.store(ctl, std::memory_order_relaxed);
  unsigned char raw[offsets::kControlStride];
  if (!mem::read(ctl, raw, sizeof(raw))) {
    aimbot::diagLog(kTag, "touch ctl: read failed @0x%zx", ctl);
    return false;
  }
  aimbot::diagLog(kTag, "touch ctl @0x%zx raw:", ctl);
  for (size_t off = 0; off < sizeof(raw); off += 16) {
    aimbot::diagLog(kTag,
                    "  %02zx: %02x %02x %02x %02x %02x %02x %02x %02x "
                    "%02x %02x %02x %02x %02x %02x %02x %02x",
                    off, raw[off + 0], raw[off + 1], raw[off + 2],
                    raw[off + 3], raw[off + 4], raw[off + 5], raw[off + 6],
                    raw[off + 7], raw[off + 8], raw[off + 9], raw[off + 10],
                    raw[off + 11], raw[off + 12], raw[off + 13], raw[off + 14],
                    raw[off + 15]);
  }
  float rect[8];
  std::memcpy(rect, raw + offsets::kControlRect, sizeof(rect));
  aimbot::diagLog(kTag,
                  "  rect24: %.5f %.5f %.5f %.5f | %.5f %.5f %.5f %.5f",
                  rect[0], rect[1], rect[2], rect[3], rect[4], rect[5],
                  rect[6], rect[7]);
  float posX = 0.0f;
  float posY = 0.0f;
  float scale = 0.0f;
  std::memcpy(&posX, raw + offsets::kControlPosX, sizeof(posX));
  std::memcpy(&posY, raw + offsets::kControlPosY, sizeof(posY));
  std::memcpy(&scale, raw + offsets::kControlScale, sizeof(scale));
  aimbot::diagLog(kTag, "  pos54=%.5f pos58=%.5f scale64=%.5f", posX, posY,
                  scale);
  return true;
}

std::atomic<uintptr_t> g_gameModeOffset{0};
std::atomic<uintptr_t> g_gameModeDirect{0};
std::atomic<bool> g_scanRunning{false};
std::atomic<uintptr_t> g_scanClient{0};

// Анимация взмаха руки. Оффсеты найдены дифф-пробой (лог 18): сразу после
// настоящего удара на локальном игроке игра выставляет
//   +0x404 float — прогресс взмаха (0..1, шаг 1/6 за тик),
//   +0x408 int   — счётчик тиков взмаха (0..5),
//   +0x435 bool  — mSwinging (1 во время взмаха).
// Наш прямой вызов 0x1009cba4 этих полей не трогает, поэтому рука не машет.
// g_tickCount тикает на каждом нормалтике локального игрока; после выставления
// полей несколько тиков пишем их значения — проверить, что движок сам
// продвигает анимацию.
std::atomic<uint64_t> g_tickCount{0};
std::atomic<uint64_t> g_lastSwingTick{0};
std::atomic<int> g_swingTriggers{0};

// Хук на обработчик списка атак (0xce2c78c): каждый тик получает WeakPtr на
// настоящий ClientInstance (arg2), лочит его и читает [ClientInstance+0xA08].
// Это ровно тот GameMode, что использует игра, — надёжнее любого скана.
std::atomic<uintptr_t> g_lockWeakPtr{0};   // 0xfdb3b20
std::atomic<uintptr_t> g_clientVtable{0};  // 0x1388FE70
void *g_attackListOriginal = nullptr;
// Хук на саму реализацию атаки 0x1009cba4. Срабатывает на ЛЮБОЙ удар
// (в т.ч. обычный удар игрока по кнопке), поэтому надёжно отдаёт настоящий
// GameMode из x0 — даже если обработчик списка атак 0xce2c78c не зовётся.
void *g_gameModeAttackImplOriginal = nullptr;

// Проверяет, что объект похож на GameMode по структуре, а не по слоту vtable
// (attack не виртуальный и в vtable его нет). Опираемся на поля, которые
// реально читает GameMode::attack: +0x8 (Level/ClientInstance), +0xC0 (объект
// с vtable в игре), +0xC8 (bool-флаг). Это отсекает прежние ложные срабатывания.
bool isGameModeObject(uintptr_t candidate) {
  uintptr_t vtable = 0;
  if (!safeRead(candidate, vtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(vtable))) {
    return false;
  }
  uintptr_t level = 0;
  if (!safeRead(candidate + offsets::kGameModeOwner, level) ||
      !isPlausiblePointer(level) ||
      mem::isGameAddress(reinterpret_cast<const void *>(level)) ||
      level == candidate) {
    return false;
  }
  uintptr_t levelVtable = 0;
  if (!safeRead(level, levelVtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(levelVtable))) {
    return false;
  }
  uintptr_t aux = 0;
  if (!safeRead(candidate + offsets::kGameModeAux, aux) ||
      !isPlausiblePointer(aux) ||
      mem::isGameAddress(reinterpret_cast<const void *>(aux)) ||
      aux == candidate) {
    return false;
  }
  uintptr_t auxVtable = 0;
  if (!safeRead(aux, auxVtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(auxVtable))) {
    return false;
  }
  uint8_t flag = 0;
  if (!safeRead(candidate + offsets::kGameModeBool, flag) || flag > 1) {
    return false;
  }
  return true;
}

// Проверка для указателя, полученного напрямую из игры (getGameMode или хук
// обработчика атак): vtable в libminecraftpe, а +0x8 (Level) и +0xC0 (aux) —
// валидные объекты, чьи vtable тоже в libminecraftpe, и bool-флаг 0/1.
// Не требует, чтобы +0x8/+0xC0 были именно кучей (Level может быть где угодно),
// но отсекает мусорные указатели, на которых 0x1009cba4 уронил бы игру.
bool isGameModeLike(uintptr_t candidate) {
  if (candidate == 0) return false;
  uintptr_t vtable = 0;
  if (!safeRead(candidate, vtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(vtable))) {
    return false;
  }
  uintptr_t level = 0;
  if (!safeRead(candidate + offsets::kGameModeOwner, level) ||
      !isPlausiblePointer(level) || level == candidate) {
    return false;
  }
  uintptr_t levelVtable = 0;
  if (!safeRead(level, levelVtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(levelVtable))) {
    return false;
  }
  uintptr_t aux = 0;
  if (!safeRead(candidate + offsets::kGameModeAux, aux) ||
      !isPlausiblePointer(aux) || aux == candidate) {
    return false;
  }
  uintptr_t auxVtable = 0;
  if (!safeRead(aux, auxVtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(auxVtable))) {
    return false;
  }
  uint8_t flag = 0;
  if (!safeRead(candidate + offsets::kGameModeBool, flag) || flag > 1) {
    return false;
  }
  return true;
}

// GameMode лежит внутри ClientInstance, но оффсет зависит от версии игры.
// Перебираем указатели в начале ClientInstance и ищем объект GameMode.
// Приоритет — кандидат, у которого [8] или [0x10] указывает на ClientInstance.
void *probeGameMode(uintptr_t clientAddr, uintptr_t &foundOffset) {
  foundOffset = 0;
  constexpr size_t kScanSize = 0x2000;
  constexpr size_t kChunkSize = 0x100;
  alignas(8) unsigned char chunk[kChunkSize];

  uintptr_t ownerMatch = 0;
  uintptr_t ownerMatchOffset = 0;
  uintptr_t anyMatch = 0;
  uintptr_t anyMatchOffset = 0;
  int logged = 0;
  int failedChunks = 0;
  for (size_t chunkBase = 0; chunkBase < kScanSize; chunkBase += kChunkSize) {
    if (!mem::read(clientAddr + chunkBase, chunk, kChunkSize)) {
      ++failedChunks;
      continue;
    }
    for (size_t inner = 0; inner + 16 <= kChunkSize; inner += 8) {
      const size_t off = chunkBase + inner;
      uintptr_t candidate = 0;
      std::memcpy(&candidate, chunk + inner, sizeof(candidate));
      if (!isPlausiblePointer(candidate) || !isGameModeObject(candidate)) {
        continue;
      }

      uintptr_t level = 0;
      uintptr_t aux = 0;
      uintptr_t owner10 = 0;
      uint8_t flag = 0xFF;
      safeRead(candidate + offsets::kGameModeOwner, level);
      safeRead(candidate + offsets::kGameModeAux, aux);
      safeRead(candidate + 0x10, owner10);
      safeRead(candidate + offsets::kGameModeBool, flag);
      const bool strong = level == clientAddr || owner10 == clientAddr;
      if (logged < 8) {
        aimbot::diagLog(kTag,
                        "gm cand off=0x%zx gm=%p level=%p aux=%p flag=%u "
                        "strong=%d",
                        off, reinterpret_cast<void *>(candidate),
                        reinterpret_cast<void *>(level),
                        reinterpret_cast<void *>(aux),
                        static_cast<unsigned>(flag), strong ? 1 : 0);
        ++logged;
      }
      // Возвращаем только сильное совпадение (+0x8/+0x10 == ClientInstance),
      // иначе это может быть чужой объект и вызов attack уронит игру.
      if (strong && ownerMatch == 0) {
        ownerMatch = candidate;
        ownerMatchOffset = off;
      }
      if (anyMatch == 0) {
        anyMatch = candidate;
        anyMatchOffset = off;
      }
    }
  }
  if (failedChunks != 0) {
    aimbot::diagLog(kTag, "gm probe: %d unreadable chunks", failedChunks);
  }

  if (ownerMatch != 0) {
    foundOffset = ownerMatchOffset;
    aimbot::diagLog(kTag, "gm probe: found off=0x%zx gm=%p (owner match)",
                    foundOffset, reinterpret_cast<void *>(ownerMatch));
    return reinterpret_cast<void *>(ownerMatch);
  }
  if (anyMatch != 0) {
    aimbot::diagLog(kTag,
                    "gm probe: structural candidate off=0x%zx gm=%p, "
                    "deferring to heap scan",
                    anyMatchOffset, reinterpret_cast<void *>(anyMatch));
  } else {
    aimbot::diagLog(kTag, "gm probe: no candidate in 0x2000 bytes");
  }
  return nullptr;
}

// Разбор /proc/self/maps: диапазон самой библиотеки игры и список rw-регионов.
struct MapsInfo {
  uintptr_t libStart = 0;
  uintptr_t libEnd = 0;
  std::vector<std::pair<uintptr_t, uintptr_t>> rw;
};

MapsInfo readMaps() {
  MapsInfo info;
  FILE *file = std::fopen("/proc/self/maps", "r");
  if (!file) return info;
  char line[512];
  while (std::fgets(line, sizeof(line), file)) {
    unsigned long long start = 0;
    unsigned long long end = 0;
    char perms[8] = {0};
    char path[256] = {0};
    const int fields =
        std::sscanf(line, "%llx-%llx %7s %*s %*s %*s %255s", &start, &end,
                    perms, path);
    if (fields < 3) continue;
    if (fields >= 4 && std::strstr(path, "libminecraftpe.so")) {
      if (info.libStart == 0 || start < info.libStart) info.libStart = start;
      if (end > info.libEnd) info.libEnd = end;
    }
    if (perms[0] == 'r' && perms[1] == 'w') {
      info.rw.emplace_back(start, end);
    }
  }
  std::fclose(file);
  return info;
}

// Резервный поиск кнопки атаки по куче. Сканируем весь rw-heap (приоритет —
// malloc/scudo/[heap]), ищем сразу несколько action-строк и иконку атаки,
// чтобы понять, какой именно контрол и строка используются в текущей схеме.
uintptr_t scanHeapForAttackControl() {
  static const char *const kActions[] = {
      "button.destroy_or_attack",
      "button.build_or_attack",
      "button.build_or_attack_or_block_select",
      "button.destroy_or_interact",
      "button.build_or_interact",
      "button.interact",
      "button.block_select",
  };
  constexpr size_t kActionCount = sizeof(kActions) / sizeof(kActions[0]);
  auto isAttackAction = [](uintptr_t ctl) {
    for (const char *a : kActions) {
      if (controlActionEquals(ctl, a)) return true;
    }
    return false;
  };

  struct Region {
    uintptr_t start;
    uintptr_t end;
    int prio;
  };
  std::vector<Region> regions;
  FILE *file = std::fopen("/proc/self/maps", "r");
  if (file) {
    char line[512];
    while (std::fgets(line, sizeof(line), file)) {
      unsigned long long start = 0;
      unsigned long long end = 0;
      char perms[8] = {0};
      char path[256] = {0};
      const int fields =
          std::sscanf(line, "%llx-%llx %7s %*s %*s %*s %255s", &start, &end,
                      perms, path);
      if (fields < 3) continue;
      if (!(perms[0] == 'r' && perms[1] == 'w')) continue;
      int prio = 2;
      if (std::strstr(path, "libc_malloc") || std::strstr(path, "scudo") ||
          std::strstr(path, "[heap]")) {
        prio = 0;
      } else if (std::strstr(path, "libminecraftpe.so")) {
        prio = 1;
      }
      regions.push_back({static_cast<uintptr_t>(start),
                         static_cast<uintptr_t>(end), prio});
    }
    std::fclose(file);
  }
  if (regions.empty()) {
    aimbot::diagLog(kTag, "ctl scan: no rw regions");
    return 0;
  }
  std::stable_sort(regions.begin(), regions.end(),
                   [](const Region &a, const Region &b) {
                     return a.prio < b.prio;
                   });

  struct Needle {
    const char *s;
    size_t len;
    bool action;
  };
  Needle needles[kActionCount + 3];
  size_t needleCount = 0;
  for (size_t i = 0; i < kActionCount; ++i) {
    needles[needleCount++] = {kActions[i], std::strlen(kActions[i]), true};
  }
  needles[needleCount++] = {"textures/ui/attack", 18, false};
  needles[needleCount++] = {"textures/ui/attack_pressed", 25, false};
  needles[needleCount++] = {"textures/ui/interact", 20, false};

  constexpr size_t kChunk = 1u << 20;
  std::vector<unsigned char> buffer(kChunk);
  std::vector<size_t> counts(needleCount, 0);
  std::vector<uintptr_t> iconAddrs;
  std::vector<uintptr_t> actionAddrs;

  size_t totalMB = 0;
  size_t heapMB = 0;
  size_t heapRegions = 0;
  for (const auto &r : regions) {
    totalMB += (r.end - r.start) >> 20;
    if (r.prio == 0) {
      heapMB += (r.end - r.start) >> 20;
      ++heapRegions;
    }
  }
  aimbot::diagLog(kTag, "ctl scan: rw regions=%zu total=%zu MB heap=%zu/%zu MB",
                  regions.size(), totalMB, heapRegions, heapMB);

  // heap/malloc-регионы сканируем целиком, прочие — до 512 МБ суммарно.
  size_t otherBudget = 512u << 20;
  for (const auto &region : regions) {
    const bool full = region.prio <= 1;
    for (uintptr_t pos = region.start; pos < region.end; pos += kChunk) {
      const size_t size = static_cast<size_t>(
          std::min<uintptr_t>(kChunk, region.end - pos));
      if (!full) {
        if (otherBudget <= size) break;
        otherBudget -= size;
      }
      if (!mem::read(pos, buffer.data(), size)) continue;
      const unsigned char *base = buffer.data();
      const unsigned char *end = base + size;
      for (size_t k = 0; k < needleCount; ++k) {
        const char *lit = needles[k].s;
        const size_t L = needles[k].len;
        const unsigned char *p = base;
        while (p + L <= end) {
          const void *hit = std::memchr(p, static_cast<int>(lit[0]),
                                        static_cast<size_t>(end - p - L + 1));
          if (!hit) break;
          const auto *h = static_cast<const unsigned char *>(hit);
          if (std::memcmp(h, lit, L) == 0) {
            ++counts[k];
            const uintptr_t at = pos + static_cast<uintptr_t>(h - base);
            if (needles[k].action) {
              if (actionAddrs.size() < 64) actionAddrs.push_back(at);
            } else if (L == 18) {
              if (iconAddrs.size() < 64) iconAddrs.push_back(at);
            }
          }
          p = h + 1;
        }
      }
    }
  }
  for (size_t k = 0; k < needleCount; ++k) {
    aimbot::diagLog(kTag, "ctl scan: '%s' hits=%zu", needles[k].s, counts[k]);
  }

  // Путь 1: иконка атаки inline в контроле. Контрол 8-выровнен, поэтому
  // правильная дельта даёт выровненный ctl с валидной action-строкой.
  for (uintptr_t a : iconAddrs) {
    for (int delta = 0x6C; delta <= 0x7A; ++delta) {
      if (a < static_cast<uintptr_t>(delta)) continue;
      const uintptr_t ctl = a - static_cast<uintptr_t>(delta);
      if ((ctl & 7) != 0) continue;
      if (isAttackAction(ctl)) {
        aimbot::diagLog(kTag, "ctl scan: icon->control @0x%zx (delta=0x%x)",
                        ctl, delta);
        return ctl;
      }
    }
  }

  // Путь 2: короткая action-строка inline (SSO) в контроле (+0 или +1).
  for (uintptr_t a : actionAddrs) {
    const uintptr_t candidates[2] = {a, a - 1};
    for (uintptr_t ctl : candidates) {
      if ((ctl & 7) != 0) continue;
      bool iconOk = false;
      char icon[24] = {0};
      for (uintptr_t ioff : {offsets::kControlIcon,
                             offsets::kControlIcon + 1}) {
        if (mem::read(ctl + ioff, icon, 18) &&
            std::memcmp(icon, "textures/ui/attack", 18) == 0) {
          iconOk = true;
          break;
        }
      }
      if (iconOk) {
        aimbot::diagLog(kTag, "ctl scan: action->control @0x%zx", ctl);
        return ctl;
      }
    }
  }

  // Путь 3: длинная action-строка в куче — ищем указатель на неё (+0x00).
  for (const auto &region : regions) {
    for (uintptr_t pos = region.start; pos < region.end; pos += kChunk) {
      const size_t size = static_cast<size_t>(
          std::min<uintptr_t>(kChunk, region.end - pos));
      if (!mem::read(pos, buffer.data(), size)) continue;
      for (size_t i = 0; i + 8 <= size; i += 8) {
        uintptr_t value = 0;
        std::memcpy(&value, buffer.data() + i, sizeof(value));
        bool isLit = false;
        for (uintptr_t lit : actionAddrs) {
          if (value == lit) {
            isLit = true;
            break;
          }
        }
        if (!isLit) continue;
        const uintptr_t ctl = pos + i;
        if ((ctl & 7) != 0) continue;
        if (isAttackAction(ctl)) {
          aimbot::diagLog(kTag, "ctl scan: ptr->control @0x%zx", ctl);
          return ctl;
        }
      }
    }
  }

  // Диагностика: иконка найдена, но action не из списка — снимаем сырые
  // кандидаты, чтобы увидеть фактическую action-строку.
  int dumped = 0;
  for (uintptr_t a : iconAddrs) {
    const uintptr_t cands[2] = {a - 0x71, a - 0x70};
    for (uintptr_t ctl : cands) {
      if ((ctl & 7) != 0) continue;
      char head[8] = {0};
      bool looks = false;
      if (mem::read(ctl, head, 7) && std::memcmp(head, "button.", 7) == 0) {
        looks = true;
      } else {
        uintptr_t p = 0;
        if (mem::read(ctl, p) && mem::isPlausiblePointer(p) &&
            mem::read(p, head, 7) && std::memcmp(head, "button.", 7) == 0) {
          looks = true;
        }
      }
      if (!looks) continue;
      logControlRaw(ctl, "icon");
      if (++dumped >= 3) {
        aimbot::diagLog(kTag, "ctl scan: no control matched");
        return 0;
      }
    }
  }

  aimbot::diagLog(kTag, "ctl scan: no control matched");
  return 0;
}

void *ctlScanThread(void *) {
  const uintptr_t control = scanHeapForAttackControl();
  if (control) {
    g_attackControl.store(control, std::memory_order_relaxed);
    aimbot::diagLog(kTag, "touch ctl: found by scan @0x%zx", control);
  } else {
    aimbot::diagLog(kTag, "touch ctl: scan found nothing");
  }
  return nullptr;
}

// Резервный поиск GameMode по куче (если хук getGameMode не сработал).
// Структура: [0] — vtable в libminecraftpe, +0x8 — Level (heap) с vtable в
// игре, +0xC0 — объект (heap) с vtable в игре, +0xC8 — bool 0/1. Указатели
// внутрь libminecraftpe в +0x8/+0xC0 отбрасываются — это главный источник
// прежних ложных срабатываний. Поля читаются прямо из буфера, syscall'ы
// остаются только для проверки двух vtable у прошедших дешёвый фильтр.
void *scanHeapForGameMode(uintptr_t clientAddr, uintptr_t &storageAddr) {
  storageAddr = 0;
  const MapsInfo maps = readMaps();
  if (maps.libStart == 0) {
    aimbot::diagLog(kTag, "gm scan: game lib not found in maps");
    return nullptr;
  }

  struct Region {
    uintptr_t start;
    uintptr_t end;
    int prio;
  };
  std::vector<Region> regions;
  {
    FILE *file = std::fopen("/proc/self/maps", "r");
    if (file) {
      char line[512];
      while (std::fgets(line, sizeof(line), file)) {
        unsigned long long start = 0;
        unsigned long long end = 0;
        char perms[8] = {0};
        char path[256] = {0};
        const int fields =
            std::sscanf(line, "%llx-%llx %7s %*s %*s %*s %255s", &start, &end,
                        perms, path);
        if (fields < 3) continue;
        if (!(perms[0] == 'r' && perms[1] == 'w')) continue;
        int prio = 2;
        if (std::strstr(path, "libc_malloc") || std::strstr(path, "scudo") ||
            std::strstr(path, "[heap]")) {
          prio = 0;
        } else if (std::strstr(path, "libminecraftpe.so")) {
          prio = 1;
        }
        regions.push_back({static_cast<uintptr_t>(start),
                           static_cast<uintptr_t>(end), prio});
      }
      std::fclose(file);
    }
  }
  // Регион самого ClientInstance сканируем первым: GameMode обычно рядом.
  for (auto &r : regions) {
    if (clientAddr >= r.start && clientAddr < r.end) {
      r.prio = -1;
      break;
    }
  }
  std::stable_sort(regions.begin(), regions.end(),
                   [](const Region &a, const Region &b) {
                     return a.prio < b.prio;
                   });

  uintptr_t clientVtable = 0;
  mem::read(clientAddr, &clientVtable, sizeof(clientVtable));

  constexpr size_t kChunk = 1u << 20;
  constexpr size_t kLimit = 2048u << 20;
  std::vector<unsigned char> buffer(kChunk);
  size_t scanned = 0;

  uintptr_t bestAddr = 0;
  uintptr_t bestLevel = 0;
  uintptr_t bestAux = 0;
  int bestFlag = 0xFF;
  int bestScore = -1;
  size_t libHits = 0;
  size_t heapCheap = 0;
  size_t structHits = 0;
  int loggedCandidates = 0;
  bool done = false;

  const auto inLib = [&](uintptr_t p) {
    return p >= maps.libStart && p < maps.libEnd;
  };

  // Точный проход: ищем ClientInstance по известному vtable (0x1388FE70) и
  // берём его [+0xA08] — это и есть GameMode, который использует игра. Наш
  // ClientInstance из update-хука может быть другим (его [+0xA08] == 0),
  // поэтому перебираем все экземпляры ClientInstance в куче и выбираем тот,
  // у которого поле не пустое и похоже на GameMode.
  const uintptr_t clientVtableAddr = maps.libStart + 0x1388FE70;
  uintptr_t exactGm = 0;
  uintptr_t exactClient = 0;
  int exactFound = 0;
  for (const auto &r : regions) {
    if (exactGm != 0) break;
    for (uintptr_t pos = r.start; pos < r.end; pos += kChunk) {
      const size_t size =
          static_cast<size_t>(std::min<uintptr_t>(kChunk, r.end - pos));
      if (!mem::read(pos, buffer.data(), size)) continue;
      if (size < 0xA10) continue;
      for (size_t i = 0; i + 0xA10 <= size; i += 8) {
        uintptr_t value = 0;
        std::memcpy(&value, buffer.data() + i, sizeof(value));
        if (value != clientVtableAddr) continue;
        const uintptr_t candidateClient = pos + i;
        uintptr_t gm = 0;
        std::memcpy(&gm, buffer.data() + i + offsets::kClientInstanceGameMode,
                    sizeof(gm));
        if (gm == 0) continue;
        ++exactFound;
        const bool strictOk = isGameModeObject(gm);
        if (exactFound <= 8) {
          aimbot::diagLog(kTag, "gm ci: client=%p gm=%p strict=%d",
                          reinterpret_cast<void *>(candidateClient),
                          reinterpret_cast<void *>(gm), strictOk ? 1 : 0);
        }
        if (strictOk || isGameModeLike(gm)) {
          exactGm = gm;
          exactClient = candidateClient;
          break;
        }
      }
      if (exactGm != 0) break;
    }
  }
  if (exactGm != 0) {
    storageAddr = exactGm;
    aimbot::diagLog(kTag, "gm scan: exact client=%p gm=%p",
                    reinterpret_cast<void *>(exactClient),
                    reinterpret_cast<void *>(exactGm));
    return reinterpret_cast<void *>(exactGm);
  }

  for (const auto &r : regions) {
    if (done || scanned >= kLimit) break;
    for (uintptr_t pos = r.start; pos < r.end && scanned < kLimit;
         pos += kChunk) {
      const size_t size =
          static_cast<size_t>(std::min<uintptr_t>(kChunk, r.end - pos));
      if (!mem::read(pos, buffer.data(), size)) continue;
      scanned += size;
      if (size < 0xD0) continue; // нужны поля до +0xC8 включительно
      for (size_t i = 0; i + 0xD0 <= size; i += 8) {
        uintptr_t value = 0;
        std::memcpy(&value, buffer.data() + i, sizeof(value));
        if (value < maps.libStart || value >= maps.libEnd) continue;
        ++libHits;

        const uintptr_t object = pos + i;
        uintptr_t level = 0;
        uintptr_t aux = 0;
        std::memcpy(&level, buffer.data() + i + offsets::kGameModeOwner,
                    sizeof(level));
        std::memcpy(&aux, buffer.data() + i + offsets::kGameModeAux,
                    sizeof(aux));
        const uint8_t flag = buffer[i + offsets::kGameModeBool];

        // Дешёвые проверки из буфера: bool-флаг, ненулевые не
        // самоуказывающие heap-поля. Указатели внутрь libminecraftpe
        // отбрасываются.
        if (flag > 1) continue;
        if (level == 0 || aux == 0) continue;
        if (level == object || aux == object) continue;
        if (inLib(level) || inLib(aux)) continue;
        ++heapCheap;

        uintptr_t levelVtable = 0;
        uintptr_t auxVtable = 0;
        if (!mem::read(level, &levelVtable, sizeof(levelVtable)) ||
            !inLib(levelVtable)) {
          continue;
        }
        if (!mem::read(aux, &auxVtable, sizeof(auxVtable)) ||
            !inLib(auxVtable)) {
          continue;
        }
        ++structHits;

        int score = 0;
        if (level == clientAddr) score += 2;
        if (flag == 0) score += 2;
        if (score > bestScore) {
          bestScore = score;
          bestAddr = object;
          bestLevel = level;
          bestAux = aux;
          bestFlag = flag;
        }
        if (loggedCandidates < 8) {
          aimbot::diagLog(kTag,
                          "gm cand obj=%p vt_off=0x%zx level=%p aux=%p "
                          "flag=%u score=%d",
                          reinterpret_cast<void *>(object),
                          static_cast<size_t>(value - maps.libStart),
                          reinterpret_cast<void *>(level),
                          reinterpret_cast<void *>(aux),
                          static_cast<unsigned>(flag), score);
          ++loggedCandidates;
        }
        if (score >= 4) {
          done = true;
          break;
        }
      }
    }
  }

  aimbot::diagLog(kTag,
                  "gm scan: base=%p client_vt_off=0x%zx libHits=%zu "
                  "heapCheap=%zu structHits=%zu scanned=%zu MB",
                  reinterpret_cast<void *>(maps.libStart),
                  static_cast<size_t>(clientVtable - maps.libStart), libHits,
                  heapCheap, structHits, scanned >> 20);

  // Слабый кандидат (score 0) не отдаём: вызов attack на чужом объекте
  // роняет игру. Точный проход выше — основной источник GameMode.
  if (bestAddr != 0 && bestScore >= 2) {
    storageAddr = bestAddr;
    aimbot::diagLog(kTag,
                    "gm scan: gm=%p level=%p aux=%p flag=%d scanned=%zu MB",
                    reinterpret_cast<void *>(bestAddr),
                    reinterpret_cast<void *>(bestLevel),
                    reinterpret_cast<void *>(bestAux), bestFlag,
                    scanned >> 20);
    return reinterpret_cast<void *>(bestAddr);
  }
  aimbot::diagLog(kTag, "gm scan: not found, scanned %zu MB", scanned >> 20);
  return nullptr;
}

void *gameModeScanThread(void *) {
  const uintptr_t clientAddr = g_scanClient.load(std::memory_order_acquire);
  uintptr_t storageAddr = 0;
  void *found = clientAddr ? scanHeapForGameMode(clientAddr, storageAddr)
                           : nullptr;
  if (found) {
    // Диагностика: структурный скан может дать ложное срабатывание, а вызов
    // 0x1009cba4 на чужом объекте роняет игру. Для атаки используем только
    // GameMode из хука обработчика атак (attackListDetour).
    aimbot::diagLog(kTag, "gm scan: candidate=%p (ignored for attack)", found);
  }
  g_scanRunning.store(false, std::memory_order_release);
  return nullptr;
}

void startGameModeScan(uintptr_t clientAddr) {
  bool expected = false;
  if (!g_scanRunning.compare_exchange_strong(expected, true,
                                             std::memory_order_acq_rel)) {
    return;
  }
  g_scanClient.store(clientAddr, std::memory_order_release);
  pthread_t thread{};
  if (pthread_create(&thread, nullptr, &gameModeScanThread, nullptr) != 0) {
    g_scanRunning.store(false, std::memory_order_release);
    return;
  }
  pthread_detach(thread);
}

bool readRotationComponent(void *actor, Rotation &out) {
  if (!actor) return false;
  const auto base = reinterpret_cast<uintptr_t>(actor);
  uintptr_t component = 0;
  if (!safeRead(base + offsets::kActorRotationComponent, component) ||
      !isPlausiblePointer(component)) {
    return false;
  }
  if (!safeRead(component, out)) return false;
  if (!std::isfinite(out.pitch) || !std::isfinite(out.yaw)) return false;
  return true;
}

bool writeRotationComponent(void *actor, const Rotation &value) {
  if (!actor) return false;
  const auto base = reinterpret_cast<uintptr_t>(actor);
  uintptr_t component = 0;
  if (!safeRead(base + offsets::kActorRotationComponent, component) ||
      !isPlausiblePointer(component)) {
    return false;
  }
  return safeWrite(component, value);
}

float frameDelta() {
  const auto now = std::chrono::steady_clock::now();
  if (g_lastFrame.time_since_epoch().count() == 0) {
    g_lastFrame = now;
    return 1.0f / 60.0f;
  }
  const float dt =
      std::chrono::duration<float>(now - g_lastFrame).count();
  g_lastFrame = now;
  return std::clamp(dt, 0.0f, 0.05f);
}

void *clientUpdateDetour(void *client, bool value, bool second) {
  static uint64_t s_updateCalls = 0;
  const uint64_t updateCall = ++s_updateCalls;
  if (updateCall <= 3 || (updateCall % 600) == 0) {
    aimbot::diagLog(kTag, "clientUpdate#%llu client=%p orig=%p",
                    static_cast<unsigned long long>(updateCall), client,
                    reinterpret_cast<void *>(g_clientUpdateOriginal));
  }

  void *result = g_clientUpdateOriginal
                     ? g_clientUpdateOriginal(client, value, second)
                     : nullptr;

  if (client) {
    g_clientInstance.store(client, std::memory_order_relaxed);
    if (g_getLocalPlayer) {
      void *localPlayer = g_getLocalPlayer(client);
      g_localPlayer.store(isLiveActor(localPlayer) ? localPlayer : nullptr,
                          std::memory_order_release);
    }
    // Скан touch-HUD (кнопка атаки) удалён: он читал гигабайты памяти в
    // фоне, из-за чего Android вешал ANR при старте, и относился к способу
    // «нажимать кнопку», который не используется.
    // ClientInstance::update вызывается в кадровом цикле.
    AimAssist::instance().onRender(frameDelta());
  } else {
    g_clientInstance.store(nullptr, std::memory_order_release);
    g_localPlayer.store(nullptr, std::memory_order_release);
  }
  return result;
}

// 0xce2c78c(void *a0, void *a1, void *a2, void *a3): игровой обработчик атак.
// a2 — WeakPtr на настоящий ClientInstance (его лок делает игра перед
// getGameMode). Ловим его и кэшируем реальный GameMode из [+0xA08].
void *attackListDetour(void *a0, void *a1, void *a2, void *a3) {
  static std::atomic<uint64_t> s_calls{0};
  const uint64_t call = s_calls.fetch_add(1, std::memory_order_relaxed) + 1;
  const uintptr_t lockFn = g_lockWeakPtr.load(std::memory_order_relaxed);
  const uintptr_t clientVt = g_clientVtable.load(std::memory_order_relaxed);
  // lock() не на каждом вызове: функция может зваться часто. Ранних вызовов
  // достаточно, чтобы поймать GameMode, плюс периодическая перепроверка.
  const bool wantProbe = (call <= 8) || ((call % 120) == 0);
  if (a2 && lockFn && clientVt && wantProbe) {
    using LockFn = void *(*)(void *, int);
    void *owner = reinterpret_cast<LockFn>(lockFn)(a2, 0);
    if (owner) {
      const uintptr_t ownerAddr = reinterpret_cast<uintptr_t>(owner);
      uintptr_t vt = 0;
      uintptr_t gm = 0;
      safeRead(ownerAddr, vt);
      safeRead(ownerAddr + offsets::kClientInstanceGameMode, gm);
      const bool isClient = (vt == clientVt);
      // Настоящий GameMode — ровно [owner+0xA08] из этого же хука: игра сама
      // передаёт его в 0x1009cba4, поэтому объект корректный по построению.
      // Мягкая проверка отсекает мусор, если a2 окажется не WeakPtr.
      const bool gmOk = gm != 0 && isGameModeLike(gm);
      uintptr_t gmOwner = 0;
      uintptr_t gmAux = 0;
      uint8_t gmFlag = 0xFF;
      safeRead(gm + offsets::kGameModeOwner, gmOwner);
      safeRead(gm + offsets::kGameModeAux, gmAux);
      safeRead(gm + offsets::kGameModeBool, gmFlag);
      const uintptr_t prev = g_gameModeDirect.load(std::memory_order_acquire);
      if (call <= 6 || (call % 300) == 0 || (gmOk && prev != gm)) {
        aimbot::diagLog(kTag,
                        "gm hook #%llu: a2=%p owner=%p vt=%p mine=%p gm=%p "
                        "isClient=%d gmOk=%d lvl=%p aux=%p flag=%u prev=%p",
                        static_cast<unsigned long long>(call), a2, owner,
                        reinterpret_cast<void *>(vt),
                        reinterpret_cast<void *>(clientVt),
                        reinterpret_cast<void *>(gm),
                        static_cast<int>(isClient), static_cast<int>(gmOk),
                        reinterpret_cast<void *>(gmOwner),
                        reinterpret_cast<void *>(gmAux),
                        static_cast<unsigned>(gmFlag),
                        reinterpret_cast<void *>(prev));
      }
      if (gmOk) {
        g_gameModeDirect.store(gm, std::memory_order_release);
      }
    } else if (call <= 6) {
      aimbot::diagLog(kTag, "gm hook #%llu: a2=%p lock=null",
                      static_cast<unsigned long long>(call), a2);
    }
  } else if (call <= 6) {
    aimbot::diagLog(kTag,
                    "gm hook #%llu: a2=%p lockFn=%p clientVt=%p (skip)",
                    static_cast<unsigned long long>(call), a2,
                    reinterpret_cast<void *>(lockFn),
                    reinterpret_cast<void *>(clientVt));
  }
  using OrigFn = void *(*)(void *, void *, void *, void *);
  auto orig = reinterpret_cast<OrigFn>(g_attackListOriginal);
  return orig ? orig(a0, a1, a2, a3) : nullptr;
}

// 0x1009cba4(GameMode *gm, Actor *actor, int actionType, Vec3 const *pos) —
// реализация удара. Срабатывает на любой удар, включая обычный удар игрока,
// поэтому ловит настоящий GameMode (x0) даже без обработчика списка атак.
// Заодно ищем, по какому оффсету gm лежит в ClientInstance/LocalPlayer —
// чтобы в будущем брать его без удара игрока.
int attackImplDetour(void *gm, void *actor, int actionType, const void *pos) {
  // Игра сама передаёт в 0x1009cba4 валидный GameMode (x0) при каждом ударе.
  // Доверяем ему напрямую: любые структурные проверки лишь отсекали настоящий
  // объект (поля Level/aux у разных режимов лежат иначе). Запоминаем x0 как
  // есть — это ровно тот указатель, с которым игра успешно бьёт.
  if (gm) {
    const uintptr_t g = reinterpret_cast<uintptr_t>(gm);
    uintptr_t vtable = 0;
    uintptr_t level = 0;
    uintptr_t aux = 0;
    uint8_t flag = 0xFF;
    safeRead(g, vtable);
    safeRead(g + offsets::kGameModeOwner, level);
    safeRead(g + offsets::kGameModeAux, aux);
    safeRead(g + offsets::kGameModeBool, flag);
    const uintptr_t prev = g_gameModeDirect.load(std::memory_order_acquire);
    if (prev != g) {
      aimbot::diagLog(kTag,
                      "gm attack-impl: gm=%p vt=%p lvl=%p aux=%p flag=%u "
                      "actor=%p type=%d prev=%p",
                      gm, reinterpret_cast<void *>(vtable),
                      reinterpret_cast<void *>(level),
                      reinterpret_cast<void *>(aux), static_cast<unsigned>(flag),
                      actor, actionType, reinterpret_cast<void *>(prev));
      // Ищем, где gm лежит относительно нашего client/LocalPlayer.
      const uintptr_t clientAddr =
          reinterpret_cast<uintptr_t>(g_clientInstance.load());
      const uintptr_t playerAddr =
          reinterpret_cast<uintptr_t>(g_localPlayer.load());
      for (uintptr_t off = 0; off <= 0x1400; off += 8) {
        uintptr_t v = 0;
        if (clientAddr && safeRead(clientAddr + off, v) && v == g) {
          aimbot::diagLog(kTag, "gm locate: [client+0x%zx] == gm", off);
          break;
        }
      }
      for (uintptr_t off = 0; off <= 0x1400; off += 8) {
        uintptr_t v = 0;
        if (playerAddr && safeRead(playerAddr + off, v) && v == g) {
          aimbot::diagLog(kTag, "gm locate: [player+0x%zx] == gm", off);
          break;
        }
      }
    }
    // Кэшируем только «наш» GameMode: тот, чей owner (Level, +0x8) совпадает
    // с g_localPlayer. У чужого уровня (сервер/другое измерение) owner другой,
    // и такой указатель не бьёт по нашим целям.
    const uintptr_t localLvl =
        reinterpret_cast<uintptr_t>(g_localPlayer.load());
    if (localLvl == 0 || level == localLvl) {
      g_gameModeDirect.store(g, std::memory_order_release);
    }
  }
  using ImplFn = int (*)(void *, void *, int, const void *);
  auto orig = reinterpret_cast<ImplFn>(g_gameModeAttackImplOriginal);
  return orig ? orig(gm, actor, actionType, pos) : 0;
}

// Проверка анимации: после выставления полей взмаха несколько тиков логируем
// состояние игрока. Движок должен сам наращивать +0x404 (прогресс) и +0x408
// (счётчик), а через ~6 тиков снять mSwinging (+0x435). Если значения не
// двигаются — поля/путь неверные.
void verifySwingTick(void *base) {
  const uint64_t tick = g_tickCount.load(std::memory_order_relaxed);
  const uint64_t last = g_lastSwingTick.load(std::memory_order_relaxed);
  if (last == 0 || tick < last || tick - last >= 10) return;
  if (g_swingTriggers.load(std::memory_order_relaxed) > 5) return;

  const uintptr_t p = reinterpret_cast<uintptr_t>(base);
  float time = 0.0f;
  float timePrev = 0.0f;
  int32_t counter = 0;
  uint16_t state = 0;
  safeRead(p + offsets::kPlayerSwingTime, time);
  safeRead(p + offsets::kPlayerSwingTimePrev, timePrev);
  safeRead(p + offsets::kPlayerSwingTicks, counter);
  safeRead(p + offsets::kPlayerSwingState, state);
  aimbot::diagLog(kTag,
                  "swing verify age=%llu counter=%d time=%.4f prev=%.4f "
                  "state=0x%x swinging=%u",
                  static_cast<unsigned long long>(tick - last),
                  static_cast<int>(counter), static_cast<double>(time),
                  static_cast<double>(timePrev), static_cast<unsigned>(state),
                  static_cast<unsigned>(state >> 8));
}

void applySwingStyle(void *actor) {
  const int style = g_swingStyle.load(std::memory_order_relaxed);
  if (style <= 0 || !actor) return;
  if (g_swingArmTicks.load(std::memory_order_relaxed) > 0) {
    g_swingArmTicks.fetch_sub(1, std::memory_order_relaxed);
  }
  const auto base = reinterpret_cast<uintptr_t>(actor);
  uint16_t state = 0;
  if (!safeRead(base + offsets::kPlayerSwingState, state)) return;
  if ((state & 0x100) == 0) return;
  float time = 0.0f;
  if (!safeRead(base + offsets::kPlayerSwingTime, time)) return;
  if (!std::isfinite(time)) return;
  const int speedPct = g_swingSpeed.load(std::memory_order_relaxed);
  const int amplitudePct = g_swingAmplitude.load(std::memory_order_relaxed);
  const float p = std::clamp(time * (static_cast<float>(speedPct) / 100.0f),
                             0.0f, 1.0f);
  float eased = p;
  switch (style) {
    case 1: eased = std::pow(p, 1.55f); break;
    case 2: eased = std::pow(p, 0.65f); break;
    case 3: eased = std::pow(p, 2.2f); break;
    case 4: eased = 1.0f - std::pow(1.0f - p, 2.4f); break;
    case 5: eased = std::clamp(p + 0.07f * std::sin(p * 18.849556f), 0.0f, 1.0f); break;
    default: return;
  }
  const float amount = std::clamp(static_cast<float>(amplitudePct) / 100.0f,
                                  0.0f, 1.5f);
  const float out = std::clamp(p + (eased - p) * amount, 0.0f, 1.0f);
  float prev = 0.0f;
  if (safeRead(base + offsets::kPlayerSwingTimePrev, prev) && prev > out) {
    safeWrite(base + offsets::kPlayerSwingTimePrev, out);
  }
  safeWrite(base + offsets::kPlayerSwingTime, out);
}

void normalTickDetour(void *actor) {
  if (g_normalTickOriginal) {
    g_normalTickOriginal(actor);
  }
  static uint64_t s_tickCalls = 0;
  const uint64_t tickCall = ++s_tickCalls;
  if (tickCall <= 3 || (tickCall % 600) == 0) {
    aimbot::diagLog(kTag, "normalTick#%llu actor=%p local=%p",
                    static_cast<unsigned long long>(tickCall), actor,
                    g_localPlayer.load(std::memory_order_relaxed));
  }
  // Тик локального игрока — 20 Гц. Сетевую синхронизацию делаем только здесь.
  if (actor && actor == g_localPlayer.load(std::memory_order_relaxed)) {
    applySwingStyle(actor);
    g_tickCount.fetch_add(1, std::memory_order_relaxed);
    if (aimbot::diagLogActive()) {
      verifySwingTick(actor);
    }
    AimAssist::instance().onTick();
  }
}

} // namespace

ClientBridge &ClientBridge::instance() {
  static ClientBridge bridge;
  return bridge;
}

bool ClientBridge::initialize() {
  std::lock_guard<std::mutex> lock(g_initMutex);
  if (g_initialized.load(std::memory_order_acquire)) {
    return true;
  }

  const uintptr_t normalTick =
      pl::memory::resolveSignature(offsets::kNormalTickSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t clientUpdate =
      pl::memory::resolveSignature(offsets::kClientInstanceUpdateSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t getLocalPlayer =
      pl::memory::resolveSignature(
          offsets::kClientInstanceGetLocalPlayerSignature,
          offsets::kMinecraftLibrary);
  const uintptr_t applyTurnDelta =
      pl::memory::resolveSignature(offsets::kApplyTurnDeltaSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t fetchNearbyActors =
      pl::memory::resolveSignature(offsets::kFetchNearbyActorsSignature,
                                   offsets::kMinecraftLibrary);
  const uintptr_t actorIsPlayer =
      pl::memory::resolveSignature(offsets::kActorIsPlayerSignature,
                                   offsets::kMinecraftLibrary);

  const auto validGameFn = [](uintptr_t address) {
    return address != 0 && mem::isGameAddress(
                                   reinterpret_cast<const void *>(address));
  };
  if (!validGameFn(normalTick) || !validGameFn(clientUpdate) ||
      !validGameFn(getLocalPlayer) || !validGameFn(applyTurnDelta) ||
      !validGameFn(fetchNearbyActors) || !validGameFn(actorIsPlayer)) {
    aimbot::diagLog(kTag,
                        "signature resolve failed: tick=%p update=%p "
                        "local=%p delta=%p nearby=%p isPlayer=%p",
                        reinterpret_cast<void *>(normalTick),
                        reinterpret_cast<void *>(clientUpdate),
                        reinterpret_cast<void *>(getLocalPlayer),
                        reinterpret_cast<void *>(applyTurnDelta),
                        reinterpret_cast<void *>(fetchNearbyActors),
                        reinterpret_cast<void *>(actorIsPlayer));
    return false;
  }

  g_getLocalPlayer = reinterpret_cast<GetLocalPlayerFn>(getLocalPlayer);
  g_applyTurnDelta = reinterpret_cast<ApplyTurnDeltaFn>(applyTurnDelta);
  g_fetchNearbyActors =
      reinterpret_cast<FetchNearbyActorsFn>(fetchNearbyActors);
  g_actorIsPlayer = reinterpret_cast<ActorIsPlayerFn>(actorIsPlayer);

  if (!g_hooksInstalled.load(std::memory_order_acquire)) {
    if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(normalTick),
                         reinterpret_cast<pl::memory::FuncPtr>(&normalTickDetour),
                         reinterpret_cast<pl::memory::FuncPtr *>(
                             &g_normalTickOriginal)) != 0) {
      aimbot::diagLog(kTag,
                          "normalTick hook failed");
      return false;
    }
    if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(clientUpdate),
                         reinterpret_cast<pl::memory::FuncPtr>(
                             &clientUpdateDetour),
                         reinterpret_cast<pl::memory::FuncPtr *>(
                             &g_clientUpdateOriginal)) != 0) {
      aimbot::diagLog(kTag,
                          "ClientInstance::update hook failed");
      return false;
    }
    // Хук обработчика атак: надёжно достаёт настоящий GameMode (attackListDetour).
    const MapsInfo maps = readMaps();
    if (maps.libStart != 0) {
      const uintptr_t attackList =
          maps.libStart + offsets::kAttackListProcessorFileOffset;
      g_lockWeakPtr.store(maps.libStart + offsets::kLockWeakPtrFileOffset,
                          std::memory_order_release);
      g_clientVtable.store(
          maps.libStart + offsets::kClientInstanceVtableFileOffset,
          std::memory_order_release);
      if (pl::memory::hook(
              reinterpret_cast<pl::memory::FuncPtr>(attackList),
              reinterpret_cast<pl::memory::FuncPtr>(&attackListDetour),
              reinterpret_cast<pl::memory::FuncPtr *>(
                  &g_attackListOriginal)) != 0) {
        aimbot::diagLog(kTag, "attack list hook failed @0x%zx", attackList);
      } else {
        aimbot::diagLog(kTag, "attack list hooked @0x%zx", attackList);
      }
    } else {
      aimbot::diagLog(kTag, "attack list hook: maps libStart=0");
    }
    // TriggerBot critical window: strip sprint bits from PlayerAuthInput
    // right before serialisation. Prologue-verified so a version bump simply
    // disables the feature instead of hijacking a different function.
    if (maps.libStart != 0) {
      const uintptr_t authWrite =
          maps.libStart + offsets::kPlayerAuthInputWriteFileOffset;
      static const unsigned char kPrologue[16] = {
          0xfd, 0x7b, 0xbc, 0xa9, 0xf7, 0x0b, 0x00, 0xf9,
          0xf6, 0x57, 0x02, 0xa9, 0xf4, 0x4f, 0x03, 0xa9};
      if (std::memcmp(reinterpret_cast<const void *>(authWrite), kPrologue,
                      sizeof(kPrologue)) == 0) {
        if (pl::memory::hook(
                reinterpret_cast<pl::memory::FuncPtr>(authWrite),
                reinterpret_cast<pl::memory::FuncPtr>(
                    &playerAuthInputWriteDetour),
                reinterpret_cast<pl::memory::FuncPtr *>(
                    &g_playerAuthWriteOriginal)) != 0) {
          aimbot::diagLog(kTag, "auth input hook failed @0x%zx", authWrite);
        } else {
          aimbot::diagLog(kTag, "auth input hooked @0x%zx", authWrite);
        }
      } else {
        aimbot::diagLog(kTag,
                        "auth input prologue mismatch @0x%zx", authWrite);
      }
    }
    // Хук реализации удара 0x1009cba4: ловит настоящий GameMode на любом
    // ударе (в т.ч. обычном ударе игрока), не полагаясь на 0xce2c78c.
    if (maps.libStart != 0) {
      const uintptr_t attackImpl =
          maps.libStart + offsets::kGameModeAttackImplFileOffset;
      if (pl::memory::hook(
              reinterpret_cast<pl::memory::FuncPtr>(attackImpl),
              reinterpret_cast<pl::memory::FuncPtr>(&attackImplDetour),
              reinterpret_cast<pl::memory::FuncPtr *>(
                  &g_gameModeAttackImplOriginal)) != 0) {
        aimbot::diagLog(kTag, "attack impl hook failed @0x%zx", attackImpl);
      } else {
        aimbot::diagLog(kTag, "attack impl hooked @0x%zx", attackImpl);
      }
    }
    // Хук touch-HUD builder'а и поиск кнопки атаки удалены: сканирование
    // памяти ради кнопки вешало ANR при старте, а способ «нажимать кнопку»
    // не используется (бьём напрямую через GameMode).
    // ClientInstance::getGameMode (`ldr x0,[x0,#0xa08]; ret`) НЕ хукаем:
    // функция длиной 8 байт, inline-хук портит соседний код и роняет игру.
    // GameMode ищем только чтением памяти (безопасно) — см. scanHeapForGameMode.
    g_hooksInstalled.store(true, std::memory_order_release);
  }

  g_initialized.store(true, std::memory_order_release);
  aimbot::diagLog(kTag,
                      "bridge initialized: tick=%p update=%p delta=%p",
                      reinterpret_cast<void *>(normalTick),
                      reinterpret_cast<void *>(clientUpdate),
                      reinterpret_cast<void *>(applyTurnDelta));
  return true;
}

void ClientBridge::shutdown() {
  g_initialized.store(false, std::memory_order_release);
  g_localPlayer.store(nullptr, std::memory_order_release);
  g_clientInstance.store(nullptr, std::memory_order_release);
}

void ClientBridge::setClientInstance(void *client) {
  g_clientInstance.store(client, std::memory_order_release);
}

void *ClientBridge::getClientInstance() const {
  return g_clientInstance.load(std::memory_order_acquire);
}

void *ClientBridge::getLocalPlayer() const {
  void *client = g_clientInstance.load(std::memory_order_acquire);
  if (!client || !g_getLocalPlayer) {
    g_localPlayer.store(nullptr, std::memory_order_release);
    return nullptr;
  }
  uintptr_t clientVtable = 0;
  if (!safeRead(reinterpret_cast<uintptr_t>(client), clientVtable) ||
      !mem::isGameAddress(reinterpret_cast<const void *>(clientVtable))) {
    g_clientInstance.store(nullptr, std::memory_order_release);
    g_localPlayer.store(nullptr, std::memory_order_release);
    return nullptr;
  }
  void *player = g_getLocalPlayer(client);
  if (!isLiveActor(player)) {
    g_localPlayer.store(nullptr, std::memory_order_release);
    return nullptr;
  }
  g_localPlayer.store(player, std::memory_order_release);
  return player;
}

bool ClientBridge::getViewAngles(void *player, ViewAngles &out) const {
  if (!player) return false;

  // ActorRotationComponent — это фактический view камеры. applyTurnDelta
  // меняет именно его, поэтому не смешиваем его с отдельным head getter.
  Rotation rotation{};
  if (!readRotationComponent(player, rotation)) {
    return false;
  }
  out.pitch = rotation.pitch;
  out.yaw = rotation.yaw;
  return true;
}

bool ClientBridge::applyTurnDelta(void *player, float pitchUp, float yaw) {
  if (!player || !g_applyTurnDelta) return false;
  if (!std::isfinite(pitchUp) || !std::isfinite(yaw)) return false;
  if (std::fabs(pitchUp) > 90.0f || std::fabs(yaw) > 180.0f) return false;
  const Rotation delta{pitchUp, yaw};
  g_applyTurnDelta(player, delta);
  return true;
}

bool ClientBridge::setBodyYaw(void *player, float yaw) {
  Rotation rotation{};
  if (!readRotationComponent(player, rotation)) {
    return false;
  }
  rotation.yaw = yaw;
  return writeRotationComponent(player, rotation);
}

bool ClientBridge::getPosition(void *actor, Vec3 &out) const {
  if (!actor) return false;
  const auto base = reinterpret_cast<uintptr_t>(actor);
  uintptr_t stateVector = 0;
  if (!safeRead(base + offsets::kActorStateVectorComponent, stateVector) ||
      !isPlausiblePointer(stateVector)) {
    return false;
  }
  float position[3]{};
  if (!safeRead(stateVector + offsets::kStatePosition, position)) {
    return false;
  }
  if (!std::isfinite(position[0]) || !std::isfinite(position[1]) ||
      !std::isfinite(position[2])) {
    return false;
  }
  out.x = position[0];
  out.y = position[1];
  out.z = position[2];
  return true;
}

void ClientBridge::setSilentAim(bool active, float pitch, float yaw) {
  g_silentAimYaw.store(yaw, std::memory_order_relaxed);
  g_silentAimPitch.store(pitch, std::memory_order_relaxed);
  g_silentAimActive.store(active, std::memory_order_relaxed);
}

bool ClientBridge::isPlayer(void *actor) const {
  if (!actor) return false;
  const auto now = std::chrono::steady_clock::now();
  {
    std::lock_guard<std::mutex> lock(m_playerCacheMutex);
    const auto it = m_playerCache.find(actor);
    if (it != m_playerCache.end() &&
        now - it->second.second < std::chrono::milliseconds(400)) {
      return it->second.first;
    }
  }
  const bool value = playerComponentQuery(reinterpret_cast<uintptr_t>(actor));
  {
    std::lock_guard<std::mutex> lock(m_playerCacheMutex);
    if (m_playerCache.size() > 4096) m_playerCache.clear();
    m_playerCache[actor] = {value, now};
  }
  return value;
}

int ClientBridge::getActorType(void *) const {
  // В этой версии тип не нужен: fetchNearbyActors уже отдаёт mob-список,
  // а isPlayer отсекает не-игроков.
  return offsets::kActorTypeMob;
}

uint64_t ClientBridge::getActorFlags(void *) const {
  return 0;
}

void ClientBridge::appendNearbyActors(void *player, const Vec3 &extent,
                                      int actorType,
                                      std::vector<void *> &out) const {
  if (!player || !g_fetchNearbyActors) return;

  const ActorVec actors = g_fetchNearbyActors(player, &extent, actorType);
  const auto begin = reinterpret_cast<uintptr_t>(actors.begin);
  const auto end = reinterpret_cast<uintptr_t>(actors.end);
  if (!isPlausiblePointer(begin) || !isPlausiblePointer(end) || end <= begin) {
    return;
  }

  size_t count = static_cast<size_t>(end - begin) /
                 sizeof(DistanceSortedActor);
  if (count == 0) return;
  if (count > 1024) count = 1024;
  if (out.capacity() < out.size() + count) {
    out.reserve(out.size() + count);
  }
  for (size_t i = 0; i < count; ++i) {
    DistanceSortedActor entry{};
    if (!safeRead(begin + i * sizeof(DistanceSortedActor), entry)) break;
    if (!entry.actor) continue;
    // Полная проверка живости: vtable в libminecraftpe, StateVectorComponent
    // и валидная позиция. Без неё «правдоподобный» мусорный указатель из
    // чужой версии оффсетов роняет игру при первом же isPlayer/getActorBounds.
    if (!isLiveActor(entry.actor)) continue;
    // Один и тот же актёр может прийти из двух наборов (mob + all).
    bool duplicate = false;
    for (void *existing : out) {
      if (existing == entry.actor) {
        duplicate = true;
        break;
      }
    }
    if (!duplicate) {
      out.push_back(entry.actor);
    }
  }
}

void ClientBridge::fillNearbyActors(void *player, float range,
                                    std::vector<void *> &out) const {
  out.clear();
  if (!player || !g_fetchNearbyActors) return;

  const Vec3 extent{range, range, range};
  appendNearbyActors(player, extent, offsets::kActorTypeMob, out);

  // На части сборок mob-маска не содержит игроков. Если игроков в списке
  // нет — запрашиваем полный набор; дубликаты отсекаются в appendNearbyActors.
  bool hasPlayer = false;
  for (void *actor : out) {
    if (isPlayer(actor)) {
      hasPlayer = true;
      break;
    }
  }
  if (!hasPlayer) {
    appendNearbyActors(player, extent, offsets::kActorTypeAll, out);
  }
}

bool ClientBridge::getActorAabb(void *actor, Aabb &out) const {
  if (!actor) return false;
  const auto base = reinterpret_cast<uintptr_t>(actor);
  uintptr_t aabbComponent = 0;
  if (!safeRead(base + offsets::kActorAabbComponent, aabbComponent) ||
      !isPlausiblePointer(aabbComponent)) {
    return false;
  }
  if (!safeRead(aabbComponent, out)) return false;
  if (!std::isfinite(out.min.x) || !std::isfinite(out.min.y) ||
      !std::isfinite(out.min.z) || !std::isfinite(out.max.x) ||
      !std::isfinite(out.max.y) || !std::isfinite(out.max.z)) {
    return false;
  }
  return out.max.x > out.min.x && out.max.y > out.min.y &&
         out.max.z > out.min.z;
}

bool ClientBridge::getActorBounds(void *actor, Aabb &out) const {
  Vec3 position{};
  if (!getPosition(actor, position)) return false;

  Aabb aabb{};
  if (getActorAabb(actor, aabb)) {
    const float height = aabb.max.y - aabb.min.y;
    const float width = std::max(aabb.max.x - aabb.min.x, aabb.max.z - aabb.min.z);
    const float cx = (aabb.min.x + aabb.max.x) * 0.5f;
    const float cy = (aabb.min.y + aabb.max.y) * 0.5f;
    const float cz = (aabb.min.z + aabb.max.z) * 0.5f;
    const float dx = cx - position.x;
    const float dy = cy - position.y;
    const float dz = cz - position.z;
    // Хитбокс должен быть рядом с позицией актёра и иметь разумный размер.
    if (height >= 0.1f && height <= 8.0f && width >= 0.05f && width <= 8.0f &&
        dx * dx + dy * dy + dz * dz <= 16.0f) {
      out = aabb;
      return true;
    }
  }

  // Fallback: у игроков StateVector-позиция находится на уровне глаз,
  // у мобов — на уровне ног.
  const bool player = isPlayer(actor);
  const float feetY = player ? position.y - offsets::kEyeHeight : position.y;
  const float halfWidth = 0.3f;
  const float height = player ? 1.8f : 1.0f;
  out.min = {position.x - halfWidth, feetY, position.z - halfWidth};
  out.max = {position.x + halfWidth, feetY + height, position.z + halfWidth};
  return true;
}

bool ClientBridge::getEyePosition(void *player, Vec3 &out) const {
  Aabb bounds{};
  if (!getActorBounds(player, bounds)) return false;
  const float height = bounds.max.y - bounds.min.y;
  out.x = (bounds.min.x + bounds.max.x) * 0.5f;
  out.z = (bounds.min.z + bounds.max.z) * 0.5f;
  out.y = bounds.min.y + std::min(offsets::kEyeHeight, height * 0.9f);
  return true;
}

void *ClientBridge::getGameMode() const {
  // ПРЯМОЙ источник (подтверждён хуком удара в логе 16):
  //   gm+0x8 (Level/owner) == g_localPlayer
  //   [g_localPlayer + 0xA08] == gm
  // То есть GameMode лежит в [level+0xA08] — ровно то, что делает геттер
  // 0x102095c4 (ldr x0,[x0,#0xa08]). Читаем напрямую: без удара, без хука,
  // без фонового скана. Отбрасываем только если vtable не в libminecraftpe.
  {
    const uintptr_t lvl = reinterpret_cast<uintptr_t>(
        g_localPlayer.load(std::memory_order_acquire));
    if (lvl) {
      uintptr_t gm = 0;
      uintptr_t vt = 0;
      if (safeRead(lvl + offsets::kClientInstanceGameMode, gm) && gm &&
          safeRead(gm, vt) &&
          mem::isGameAddress(reinterpret_cast<const void *>(vt))) {
        static bool s_logged = false;
        if (!s_logged) {
          s_logged = true;
          uintptr_t l2 = 0, aux = 0;
          uint8_t fl = 0xFF;
          safeRead(gm + offsets::kGameModeOwner, l2);
          safeRead(gm + offsets::kGameModeAux, aux);
          safeRead(gm + offsets::kGameModeBool, fl);
          aimbot::diagLog(kTag,
                          "gm direct: level=%p [level+0x%zx]=%p vt=%p "
                          "lvl=%p aux=%p flag=%u",
                          reinterpret_cast<void *>(lvl),
                          static_cast<size_t>(offsets::kClientInstanceGameMode),
                          reinterpret_cast<void *>(gm),
                          reinterpret_cast<void *>(vt),
                          reinterpret_cast<void *>(l2),
                          reinterpret_cast<void *>(aux),
                          static_cast<unsigned>(fl));
        }
        return reinterpret_cast<void *>(gm);
      }
    }
  }

  void *client = getClientInstance();
  if (!client) return nullptr;
  const uintptr_t clientAddr = reinterpret_cast<uintptr_t>(client);

  const auto valid = [](uintptr_t gameMode) -> bool {
    return gameMode != 0 && isPlausiblePointer(gameMode) &&
           isGameModeObject(gameMode);
  };

  // Уже найденный объект (кэш). Владелец мог быть не наш ClientInstance,
  // поэтому здесь достаточно проверки по vtable.
  const uintptr_t cachedDirect =
      g_gameModeDirect.load(std::memory_order_acquire);
  if (cachedDirect != 0) {
    // Сюда попадает только GameMode, который игра сама передала в свой же
    // обработчик удара 0x1009cba4. Значит это гарантированно валидный `this`
    // для удара — принимаем без структурных проверок (они отсекали настоящий
    // объект). Достаточно убедиться, что vtable лежит в libminecraftpe.
    uintptr_t cachedVtable = 0;
    if (safeRead(cachedDirect, cachedVtable) &&
        mem::isGameAddress(reinterpret_cast<const void *>(cachedVtable))) {
      return reinterpret_cast<void *>(cachedDirect);
    }
    g_gameModeDirect.store(0, std::memory_order_release);
  }

  // Поле ClientInstance+0xA08.
  const uintptr_t cachedOffset =
      g_gameModeOffset.load(std::memory_order_acquire);
  if (cachedOffset != 0) {
    uintptr_t gameMode = 0;
    if (safeRead(clientAddr + cachedOffset, gameMode) && valid(gameMode)) {
      return reinterpret_cast<void *>(gameMode);
    }
    g_gameModeOffset.store(0, std::memory_order_release);
  }

  uintptr_t known = 0;
  if (safeRead(clientAddr + offsets::kClientInstanceGameMode, known) &&
      valid(known)) {
    g_gameModeOffset.store(offsets::kClientInstanceGameMode,
                           std::memory_order_release);
    return reinterpret_cast<void *>(known);
  }

  // Дорогой поиск — в фоновом потоке, чтобы не подвешивать кадр.
  static uint64_t s_probeCounter = 0;
  static int64_t s_lastProbeMs = 0;
  const int64_t now =
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::steady_clock::now().time_since_epoch())
          .count();
  if (s_probeCounter >= 3 || now - s_lastProbeMs < 15000) {
    return nullptr;
  }
  s_lastProbeMs = now;
  ++s_probeCounter;

  uintptr_t slotValue = 0;
  uintptr_t slotOwner = 0;
  uintptr_t vtable = 0;
  safeRead(clientAddr + offsets::kClientInstanceGameMode, slotValue);
  safeRead(slotValue + offsets::kGameModeOwner, slotOwner);
  safeRead(clientAddr, vtable);
  aimbot::diagLog(kTag,
                  "gm probe #%llu: slot[A08]=%p owner=%p client=%p vt=%p "
                  "base=%p",
                  static_cast<unsigned long long>(s_probeCounter),
                  reinterpret_cast<void *>(slotValue),
                  reinterpret_cast<void *>(slotOwner),
                  reinterpret_cast<void *>(clientAddr),
                  reinterpret_cast<void *>(vtable),
                  reinterpret_cast<void *>(mem::gameBase(
                      reinterpret_cast<const void *>(vtable))));

  // Шаг 1: перебор полей в начале ClientInstance (дёшево, синхронно).
  uintptr_t foundOffset = 0;
  void *found = probeGameMode(clientAddr, foundOffset);
  if (found) {
    g_gameModeOffset.store(foundOffset, std::memory_order_release);
    return found;
  }

  // Шаг 2: скан кучи больше не запускаем — структурные кандидаты оказались
  // актёрами (ложные срабатывания). GameMode берём из хука удара
  // (attackImplDetour) / обработчика списка атак (attackListDetour).
  return nullptr;
}

bool ClientBridge::attackActor(void *actor, const Vec3 &hitPoint) {
  if (!actor) return false;

  static std::atomic<int> s_attackLog{0};
  const int logIndex = s_attackLog.fetch_add(1, std::memory_order_relaxed);
  const bool logThis = logIndex < 8;

  void *gameMode = getGameMode();
  if (!gameMode) {
    if (logThis) {
      aimbot::diagLog(kTag,
                          "attack: gameMode=null client=%p", getClientInstance());
    }
    return false;
  }

  const uintptr_t gm = reinterpret_cast<uintptr_t>(gameMode);
  // vtable GameMode лежит в libminecraftpe — по ней получаем базу библиотеки.
  uintptr_t vtable = 0;
  if (!safeRead(gm, vtable) || !isPlausiblePointer(vtable)) {
    return false;
  }
  const uintptr_t gameBase = mem::gameBase(
      reinterpret_cast<const void *>(vtable));
  if (gameBase == 0) {
    if (logThis) {
      aimbot::diagLog(kTag, "attack: gameBase=null gm=%p vt=%p", gameMode,
                      reinterpret_cast<void *>(vtable));
    }
    return false;
  }

  // gm получен из собственного вызова игры (0x1009cba4), поэтому это валидный
  // `this`: строгую проверку полей убрали — она лишь отсекала настоящий
  // GameMode. Только логируем поля для диагностики (vtable уже проверен выше
  // через gameBase != 0).
  uintptr_t level = 0;
  uintptr_t aux = 0;
  uint8_t flag = 0xFF;
  uintptr_t levelVt = 0;
  uintptr_t auxVt = 0;
  safeRead(gm + offsets::kGameModeOwner, level);
  safeRead(gm + offsets::kGameModeAux, aux);
  safeRead(gm + offsets::kGameModeBool, flag);
  safeRead(level, levelVt);
  safeRead(aux, auxVt);
  if (logThis) {
    aimbot::diagLog(kTag,
                    "attack guard gm=%p vt=%p lvl=%p lvt=%p aux=%p avt=%p "
                    "flag=%u",
                    gameMode, reinterpret_cast<void *>(vtable),
                    reinterpret_cast<void *>(level),
                    reinterpret_cast<void *>(levelVt),
                    reinterpret_cast<void *>(aux),
                    reinterpret_cast<void *>(auxVt),
                    static_cast<unsigned>(flag));
  }

  // 0x1009cba4(this, actor, actionType, Vec3 const&) — реализация удара:
  // собирает и отправляет ItemUseOnActorInventoryTransaction. actionType=0 —
  // путь обработчика списка атак 0xce2c78c -> 0xce2c894 (GameMode::attack),
  // который бьёт по актёру и наносит урон. Обёртку 0x100a11c8 (=1) не берём:
  // у неё флаговый гейт (глобал 0x141d7040) и, судя по логу, это путь
  // взаимодействия, а не урона.
  const uintptr_t attackFn = gameBase + offsets::kGameModeAttackImplFileOffset;
  if (logThis) {
    aimbot::diagLog(
        kTag,
        "attack #%d gm=%p fn_off=0x%zx actor=%p "
        "hit=(%.2f,%.2f,%.2f)",
        logIndex, gameMode,
        static_cast<size_t>(offsets::kGameModeAttackImplFileOffset), actor,
        static_cast<double>(hitPoint.x), static_cast<double>(hitPoint.y),
        static_cast<double>(hitPoint.z));
  }

  using AttackFn = void (*)(void *, void *, int, const Vec3 *);
  reinterpret_cast<AttackFn>(attackFn)(gameMode, actor, 0, &hitPoint);

  // Анимация взмаха руки. Прямой вызов 0x1009cba4 не запускает взмах, поэтому
  // зовём штатную функцию старта взмаха 0xfa887f0(player, 0, source) — её же
  // вызывает LocalPlayer::normalTick (0xaf60da8). Она пишет counter=-1 и
  // +0x434=0x100|source, дальше движок сам анимирует 6 тиков.
  void *swingPlayer = g_localPlayer.load(std::memory_order_relaxed);
  uintptr_t swingVtable = 0;
  if (swingPlayer &&
      safeRead(reinterpret_cast<uintptr_t>(swingPlayer), swingVtable) &&
      mem::isGameAddress(reinterpret_cast<const void *>(swingVtable))) {
    const uintptr_t p = reinterpret_cast<uintptr_t>(swingPlayer);
    int32_t counterBefore = 0;
    uint16_t stateBefore = 0;
    safeRead(p + offsets::kPlayerSwingTicks, counterBefore);
    safeRead(p + offsets::kPlayerSwingState, stateBefore);

    constexpr int kAttackSwingSource = 2;
    using SwingFn = int (*)(void *, int, int);
    const uintptr_t swingFn =
        gameBase + offsets::kPlayerSwingFnFileOffset;

    int32_t counterAfter = 0;
    uint16_t stateAfter = 0;
    int started =
        reinterpret_cast<SwingFn>(swingFn)(swingPlayer, 0, 0);
    safeRead(p + offsets::kPlayerSwingTicks, counterAfter);
    safeRead(p + offsets::kPlayerSwingState, stateAfter);

    if (started <= 0 || (stateAfter & 0x100u) == 0) {
      started =
          reinterpret_cast<SwingFn>(swingFn)(swingPlayer, 0, kAttackSwingSource);
      safeRead(p + offsets::kPlayerSwingTicks, counterAfter);
      safeRead(p + offsets::kPlayerSwingState, stateAfter);
    }

    // Some client builds reject the native start call (wrong source arg or a
    // gated handler). Seed the swing state directly so the module still
    // animates instead of silently doing nothing.
    if (started <= 0 || (stateAfter & 0x100u) == 0) {
      safeWrite(p + offsets::kPlayerSwingTicks, static_cast<int32_t>(-1));
      safeWrite(p + offsets::kPlayerSwingState,
                static_cast<uint16_t>(0x100u | kAttackSwingSource));
      safeWrite(p + offsets::kPlayerSwingTime, 0.0f);
      safeWrite(p + offsets::kPlayerSwingTimePrev, 0.0f);
      safeRead(p + offsets::kPlayerSwingTicks, counterAfter);
      safeRead(p + offsets::kPlayerSwingState, stateAfter);
    }

    g_lastSwingTick.store(g_tickCount.load(std::memory_order_relaxed),
                          std::memory_order_relaxed);
    const int n = g_swingTriggers.fetch_add(1, std::memory_order_relaxed) + 1;
    if (n <= 8) {
      aimbot::diagLog(kTag,
                      "swing trigger #%d player=%p started=%d counter %d->%d "
                      "state 0x%x->0x%x",
                      n, swingPlayer, started ? 1 : 0,
                      static_cast<int>(counterBefore),
                      static_cast<int>(counterAfter),
                      static_cast<unsigned>(stateBefore),
                      static_cast<unsigned>(stateAfter));
    }
  }
  return true;
}

uint32_t ClientBridge::getActorCategories(void *actor) const {
  if (!actor) return 0;
  uint32_t categories = 0;
  if (!safeRead(reinterpret_cast<uintptr_t>(actor) + offsets::kActorCategories,
                categories)) {
    return 0;
  }
  return categories;
}

bool ClientBridge::hasLineOfSight(void *player, const Vec3 &, const Vec3 &) const {
  // Сигнатура Level::raycast зависит от версии. Пока не заполнишь её —
  // visibleCheck работает как no-op, чтобы не ломать наведение.
  (void)player;
  return true;
}

void ClientBridge::setMovePacketSender(MovePacketSender sender) {
  g_movePacketSender.store(sender, std::memory_order_release);
}

void ClientBridge::setSwingStyle(int style) {
  g_swingStyle.store(std::clamp(style, 0, 5), std::memory_order_relaxed);
  if (style <= 0) {
    g_swingArmTicks.store(0, std::memory_order_relaxed);
  }
}

void ClientBridge::setSwingSpeed(int percent) {
  g_swingSpeed.store(std::clamp(percent, 50, 200), std::memory_order_relaxed);
}

void ClientBridge::setSwingAmplitude(int percent) {
  g_swingAmplitude.store(std::clamp(percent, 0, 150),
                         std::memory_order_relaxed);
}

void ClientBridge::armSwing() {
  if (g_swingStyle.load(std::memory_order_relaxed) <= 0) return;
  g_swingArmTicks.store(10, std::memory_order_relaxed);
}

void ClientBridge::sendMovePacket(void *player, float yaw, float pitch) const {
  MovePacketSender sender = g_movePacketSender.load(std::memory_order_acquire);
  if (sender) {
    sender(player, yaw, pitch);
  }
}

} // namespace aimbot
