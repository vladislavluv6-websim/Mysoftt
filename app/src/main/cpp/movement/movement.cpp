#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>

#include "GameEcs.hpp"
#include "pl/memory/Hook.hpp"
#include "pl/memory/Signature.hpp"

// Movement module: Fly / Jesus / Scaffold / AutoJump.
//
// Все фичи работают от одного набора хуков на игровой тик (LocalPlayer::normalTick
// / Actor::baseTick) и на сетевой слой (подтверждение/отмена коррекций).
// Профиль "bypass" держит клиента и сервер консистентными: позиция, которую
// видит сервер, всегда правдоподобна, а входящие CorrectPlayerMovePrediction
// гасятся только пока активна фича, ровно как в Speed-модуле.
namespace {

constexpr const char *kTag = "MovementMod";
constexpr const char *kMinecraftLibrary = "libminecraftpe.so";

constexpr const char *kNormalTickSignature =
    "? ? ? FC ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? 91 "
    "? ? ? D1 54 D0 3B D5 F3 03 00 AA ? ? ? F9 ? ? ? F8 ? ? ? 39";

constexpr const char *kClientInstanceUpdateSignature =
    "? ? ? FC ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? A9 ? ? ? 91 "
    "? ? ? D1 59 D0 3B D5 ? ? ? 52 F5 03 02 2A";

constexpr const char *kClientInstanceGetLocalPlayerSignature =
    "? ? ? D1 ? ? ? A9 ? ? ? F9 ? ? ? 91 53 D0 3B D5 E8 03 00 AA ? ? ? 91 ? ? ? F9 "
    "? ? ? 91 ? ? ? F8 ? ? ? ? ? ? ? 91 ? ? ? ? ? ? ? 36 ? ? ? 91 ? ? ? 52 ? ? ? ? "
    "? ? ? ? E0 03 1F AA ? ? ? F9 ? ? ? F8 1F 01 09 EB ? ? ? 54 ? ? ? A9 ? ? ? F9 "
    "? ? ? 91 C0 03 5F D6 ? ? ? ? C0 03 5F D6";

// Actor -> StateVectorComponent* (position, previous position, velocity).
constexpr uintptr_t kActorStateVectorComponent = 0x228;
constexpr uintptr_t kStatePosition = 0;
constexpr uintptr_t kStatePreviousPosition = 12;
constexpr uintptr_t kStateVelocity = 24;

// ActorRotationComponent: { pitch, yaw }.
constexpr uintptr_t kActorRotationComponent = 0x238;

// ClientInstance -> GameMode* (SurvivalMode), как в AimAssist/Offsets.hpp.
constexpr uintptr_t kClientInstanceGameMode = 0xA08;

// 1.26.60.04_RC3: GameMode::useItemOn(ItemStack&, BlockPos const&, uint8 face).
// Проверено по дизассемблеру: пролог `sub sp, sp, #0xf0`, x1 — ItemStack*,
// x2 — BlockPos* (3 int), w3 — грань (0=DOWN,1=UP,2=NORTH,3=SOUTH,4=WEST,
// 5=EAST). Сборка ItemUseInventoryTransaction уходит в тот же сетевой стек,
// что и обычная установка, поэтому сервер принимает блок.
constexpr uintptr_t kUseItemOnFileOffset = 0x100a059c;

// PlayerAuthInputPacket::write (0x90) — нужен только для бита START_JUMPING,
// чтобы AutoJump выглядел как настоящий ввод прыжка.
constexpr uintptr_t kPlayerAuthInputWriteOffset = 0xdaa4864;
constexpr uintptr_t kPacketInputData = 0x88;
constexpr int kInputStartJumping = 31;

constexpr float kWorldLimit = 3.0e7f;

using NormalTickFn = void (*)(void *);
using ClientInstanceUpdateFn = void *(*)(void *, bool);
using GetLocalPlayerFn = void *(*)(void *);
using PredictionHandleFn = void *(*)(void *, void *, void *, void *);
using UseItemOnFn = bool (*)(void *, void *, void *, uint8_t);
using PacketWriteFn = void *(*)(void *, void *, void *, void *);

using BaseTickFn = void (*)(void *);
NormalTickFn g_normalTickOriginal = nullptr;
BaseTickFn g_baseTickOriginal = nullptr;
ClientInstanceUpdateFn g_clientInstanceUpdateOriginal = nullptr;
GetLocalPlayerFn g_getLocalPlayer = nullptr;
PredictionHandleFn g_predictionHandleOriginal = nullptr;
UseItemOnFn g_useItemOnOriginal = nullptr;
PacketWriteFn g_packetWriteOriginal = nullptr;

// Master switch + feature switches.
std::atomic_bool g_enabled{false};

std::atomic_bool g_flyEnabled{false};
std::atomic_int g_flyMode{0};          // 0 = Normal, 1 = Bypass (glide)
std::atomic<float> g_flySpeed{1.0f};   // horizontal multiplier (1.0 = walk)
std::atomic<float> g_flyUp{0.42f};
std::atomic<float> g_flyDown{0.42f};
std::atomic_bool g_flyCancelCorrections{true};

std::atomic_bool g_jesusEnabled{false};
std::atomic_int g_jesusMode{0};        // 0 = Surface, 1 = Hover
std::atomic<float> g_jesusDepth{0.0f};

std::atomic_bool g_scaffoldEnabled{false};
std::atomic_int g_scaffoldMode{0};     // 0 = Below, 1 = Ahead
std::atomic<float> g_scaffoldPitch{85.0f};
std::atomic_int g_scaffoldInterval{0};
std::atomic_bool g_scaffoldRotate{true};

std::atomic_bool g_autoJumpEnabled{false};
std::atomic<float> g_autoJumpHeight{1.25f};
std::atomic_int g_autoJumpMode{0};     // 0 = Manual, 1 = Auto

std::atomic_bool g_debug{false};

std::atomic<void *> g_clientLocalPlayer{nullptr};
std::atomic<void *> g_localPlayer{nullptr};
std::atomic<void *> g_clientInstance{nullptr};
std::atomic<UseItemOnFn> g_useItemOn{nullptr};
std::atomic<void *> g_capturedItem{nullptr};
std::atomic<int> g_jumpBitTicks{0};
std::atomic<bool> g_releaseJump{false};

uintptr_t g_gameBase = 0;
bool g_packetHookInstalled = false;

std::atomic<bool> g_onGround{true};
std::atomic<bool> g_cheatingPublished{false};

std::mutex g_initMutex;
bool g_clientHookInstalled = false;
bool g_tickHookInstalled = false;
bool g_baseTickHookInstalled = false;
bool g_baseTickHookAttempted = false;
bool g_predictionHookInstalled = false;
bool g_installed = false;
uintptr_t g_normalTickAddress = 0;
uintptr_t g_baseTickAddress = 0;
std::chrono::steady_clock::time_point g_lastInitAttempt{};

int g_lastAutoJumpTick = -100;

void logInfo(const char *message) {
  __android_log_print(ANDROID_LOG_INFO, kTag, "%s", message);
}

void logWarning(const char *message) {
  __android_log_print(ANDROID_LOG_WARN, kTag, "%s", message);
}

float horizontalLength(float x, float z) { return std::sqrt(x * x + z * z); }

// ---------------------------------------------------------------------------
// Inbound CorrectPlayerMovePrediction cancellation (bypass profile).
//
// Пока активна летающая/плавающая фича, серверные коррекции позиции
// отменяются, чтобы клиент не отбрасывало назад. Диспатчер ищем через RTTI,
// а не по версионному адресу, — тот же приём, что и в Speed-модуле.
// ---------------------------------------------------------------------------
struct LibSegment {
  uintptr_t start;
  uintptr_t end;
};

struct LibSegments {
  LibSegment readable[8];
  int readableCount = 0;
  LibSegment writable[8];
  int writableCount = 0;
};

int collectLibSegments(struct dl_phdr_info *info, size_t, void *data) {
  if (!info->dlpi_name || !std::strstr(info->dlpi_name, kMinecraftLibrary)) {
    return 0;
  }
  auto *out = static_cast<LibSegments *>(data);
  for (int i = 0; i < info->dlpi_phnum; ++i) {
    const ElfW(Phdr) &ph = info->dlpi_phdr[i];
    if (ph.p_type != PT_LOAD) continue;
    const uintptr_t start = info->dlpi_addr + ph.p_vaddr;
    const uintptr_t end = start + ph.p_memsz;
    if (ph.p_flags & PF_R) {
      if (out->readableCount < 8) {
        out->readable[out->readableCount++] = {start, end};
      }
    }
    if (ph.p_flags & PF_W) {
      if (out->writableCount < 8) {
        out->writable[out->writableCount++] = {start, end};
      }
    }
  }
  return 1;
}

bool rangeContains(const LibSegment *ranges, int count, uintptr_t address,
                   size_t size) {
  if (address < 0x1000) return false;
  for (int i = 0; i < count; ++i) {
    if (address < ranges[i].start || address > ranges[i].end) continue;
    if (size <= ranges[i].end - address) return true;
  }
  return false;
}

bool inWritable(const LibSegments &lib, uintptr_t address, size_t size) {
  return rangeContains(lib.writable, lib.writableCount, address, size);
}

bool inReadable(const LibSegments &lib, uintptr_t address, size_t size) {
  return rangeContains(lib.readable, lib.readableCount, address, size);
}

bool isMinecraftAddress(uintptr_t address) {
  Dl_info info{};
  if (dladdr(reinterpret_cast<void *>(address), &info) == 0) return true;
  return info.dli_fname == nullptr ||
         std::strstr(info.dli_fname, kMinecraftLibrary) != nullptr;
}

bool readableMemory(uintptr_t address, size_t size) {
  if (address < 0x1000) return false;
  iovec local{reinterpret_cast<void *>(address), size};
  iovec remote{reinterpret_cast<void *>(address), size};
  return process_vm_readv(getpid(), &local, 1, &remote, 1, 0) ==
         static_cast<ssize_t>(size);
}

// ItemStack валиден, если его первый qword — указатель на vtable внутри
// libminecraftpe. Это защищает от вызова useItemOn с мусорным/освобождённым
// указателем (иначе — SIGSEGV, как было в первой версии Scaffold).
bool plausibleItem(void *item) {
  if (!item) return false;
  const uintptr_t address = reinterpret_cast<uintptr_t>(item);
  if ((address & 0x7) != 0 || !readableMemory(address, sizeof(uintptr_t))) {
    return false;
  }
  const uintptr_t vtable = *reinterpret_cast<uintptr_t *>(address);
  if (vtable < 0x1000 || !readableMemory(vtable, sizeof(uintptr_t))) {
    return false;
  }
  return isMinecraftAddress(vtable);
}

bool useItemOnDetour(void *gameMode, void *item, void *blockPos, uint8_t face) {
  if (plausibleItem(item)) {
    g_capturedItem.store(item, std::memory_order_relaxed);
  }
  return g_useItemOnOriginal
             ? g_useItemOnOriginal(gameMode, item, blockPos, face)
             : false;
}

bool installUseItemOnHook(uintptr_t gameBase) {
  if (g_useItemOnOriginal) return true;
  if (gameBase == 0) return false;
  const uintptr_t address = gameBase + kUseItemOnFileOffset;
  // sub sp, sp, #0xf0 (пролог GameMode::useItemOn в 1.26.60.04)
  static const unsigned char kPrologue[4] = {0xff, 0xc3, 0x03, 0xd1};
  if (!readableMemory(address, sizeof(kPrologue)) ||
      std::memcmp(reinterpret_cast<const void *>(address), kPrologue,
                  sizeof(kPrologue)) != 0) {
    logWarning("GameMode::useItemOn prologue mismatch (unsupported build)");
    return false;
  }
  if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(address),
                       reinterpret_cast<pl::memory::FuncPtr>(useItemOnDetour),
                       reinterpret_cast<pl::memory::FuncPtr *>(
                           &g_useItemOnOriginal)) != 0) {
    logWarning("Unable to hook GameMode::useItemOn");
    return false;
  }
  g_useItemOn.store(g_useItemOnOriginal, std::memory_order_relaxed);
  logInfo("GameMode::useItemOn hooked (Scaffold placement enabled)");
  return true;
}

void *packetWriteDetour(void *packet, void *a1, void *a2, void *a3) {
  if (packet && g_jumpBitTicks.load(std::memory_order_relaxed) > 0) {
    auto *bits = reinterpret_cast<uint64_t *>(
        reinterpret_cast<char *>(packet) + kPacketInputData);
    bits[kInputStartJumping >> 6] |= 1ULL << (kInputStartJumping & 63);
    g_jumpBitTicks.fetch_sub(1, std::memory_order_relaxed);
  }
  return g_packetWriteOriginal
             ? g_packetWriteOriginal(packet, a1, a2, a3)
             : nullptr;
}

bool installPacketHook(uintptr_t gameBase) {
  if (g_packetHookInstalled) return true;
  if (gameBase == 0) return false;
  const uintptr_t address = gameBase + kPlayerAuthInputWriteOffset;
  static const unsigned char kPrologue[16] = {
      0xfd, 0x7b, 0xbc, 0xa9, 0xf7, 0x0b, 0x00, 0xf9,
      0xf6, 0x57, 0x02, 0xa9, 0xf4, 0x4f, 0x03, 0xa9};
  if (!readableMemory(address, sizeof(kPrologue)) ||
      std::memcmp(reinterpret_cast<const void *>(address), kPrologue,
                  sizeof(kPrologue)) != 0) {
    logWarning("PlayerAuthInput::write prologue mismatch");
    return false;
  }
  if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(address),
                       reinterpret_cast<pl::memory::FuncPtr>(packetWriteDetour),
                       reinterpret_cast<pl::memory::FuncPtr *>(
                           &g_packetWriteOriginal)) != 0) {
    logWarning("Unable to hook PlayerAuthInput::write");
    return false;
  }
  g_packetHookInstalled = true;
  logInfo("PlayerAuthInput::write hooked (AutoJump input bit)");
  return true;
}

bool resolvePredictionHandle(uintptr_t &handle, uintptr_t &slotAddress) {
  LibSegments lib;
  dl_iterate_phdr(collectLibSegments, &lib);
  if (lib.readableCount == 0 || lib.writableCount == 0) return false;

  static const char kDispatcherType[] =
      "31PacketHandlerDispatcherInstanceI33CorrectPlayerMovePredictionPacket"
      "Lb0EE";
  const size_t typeLen = sizeof(kDispatcherType) - 1;

  uintptr_t typeInfo = 0;
  for (int w = 0; w < lib.writableCount && typeInfo == 0; ++w) {
    const uintptr_t begin = (lib.writable[w].start + 7) & ~uintptr_t(7);
    const uintptr_t end = lib.writable[w].end;
    for (uintptr_t field = begin; field + sizeof(uintptr_t) <= end;
         field += sizeof(uintptr_t)) {
      const uintptr_t name = *reinterpret_cast<uintptr_t *>(field);
      if (!inReadable(lib, name, typeLen + 1)) continue;
      if (std::memcmp(reinterpret_cast<const void *>(name), kDispatcherType,
                      typeLen) != 0 ||
          reinterpret_cast<const char *>(name)[typeLen] != '\0') {
        continue;
      }
      const uintptr_t candidate = field - sizeof(uintptr_t);
      if (!inWritable(lib, candidate, 2 * sizeof(uintptr_t))) continue;
      const uintptr_t vtableOfTypeInfo =
          *reinterpret_cast<uintptr_t *>(candidate);
      if (vtableOfTypeInfo == 0) continue;
      typeInfo = candidate;
      break;
    }
  }
  if (!typeInfo) return false;

  for (int w = 0; w < lib.writableCount; ++w) {
    const uintptr_t begin = (lib.writable[w].start + 7) & ~uintptr_t(7);
    const uintptr_t end = lib.writable[w].end;
    for (uintptr_t field = begin; field + sizeof(uintptr_t) <= end;
         field += sizeof(uintptr_t)) {
      if (*reinterpret_cast<uintptr_t *>(field) != typeInfo) continue;
      const uintptr_t vtable = field + sizeof(uintptr_t);
      if (!inWritable(lib, vtable, 3 * sizeof(uintptr_t))) continue;
      const uintptr_t candidateSlot = vtable + 2 * sizeof(uintptr_t);
      const uintptr_t slot = *reinterpret_cast<uintptr_t *>(candidateSlot);
      if (!inReadable(lib, slot, 4) || !isMinecraftAddress(slot)) continue;
      handle = slot;
      slotAddress = candidateSlot;
      return true;
    }
  }
  return false;
}

void *predictionHandleDetour(void *a1, void *a2, void *a3, void *a4) {
  if (g_enabled.load(std::memory_order_relaxed) &&
      g_cheatingPublished.load(std::memory_order_relaxed)) {
    return nullptr;
  }
  return g_predictionHandleOriginal
             ? g_predictionHandleOriginal(a1, a2, a3, a4)
             : nullptr;
}

bool installPredictionHook() {
  if (g_predictionHookInstalled) return true;
  uintptr_t handle = 0;
  uintptr_t slotAddress = 0;
  if (!resolvePredictionHandle(handle, slotAddress)) {
    logWarning("CorrectPlayerMovePrediction dispatcher not found");
    return false;
  }

  const long pageSize = sysconf(_SC_PAGESIZE);
  if (pageSize <= 0) return false;
  const uintptr_t page = slotAddress & ~static_cast<uintptr_t>(pageSize - 1);
  const size_t span = static_cast<size_t>(slotAddress - page) + sizeof(void *);
  if (mprotect(reinterpret_cast<void *>(page), span,
               PROT_READ | PROT_WRITE) != 0) {
    logWarning("Unable to unprotect CorrectPlayerMovePrediction vtable");
    return false;
  }
  auto *slot = reinterpret_cast<void **>(slotAddress);
  g_predictionHandleOriginal = reinterpret_cast<PredictionHandleFn>(*slot);
  *slot = reinterpret_cast<void *>(predictionHandleDetour);
  __builtin___clear_cache(reinterpret_cast<char *>(slotAddress),
                          reinterpret_cast<char *>(slotAddress + sizeof(void *)));
  mprotect(reinterpret_cast<void *>(page), span, PROT_READ);

  g_predictionHookInstalled = true;
  logInfo("CorrectPlayerMovePrediction vtable patched");
  return true;
}

// ---------------------------------------------------------------------------
// Per-tick feature logic.
// ---------------------------------------------------------------------------
struct ActorState {
  void *actor = nullptr;
  float *position = nullptr;
  float *previous = nullptr;
  float *velocity = nullptr;
  float *rotation = nullptr;
  bool valid = false;
};

bool readState(void *actor, ActorState &out) {
  if (!actor) return false;
  auto *base = reinterpret_cast<char *>(actor);
  auto stateVector = *reinterpret_cast<uintptr_t *>(
      base + kActorStateVectorComponent);
  if (stateVector < 0x1000 || (stateVector & 0x7) != 0) return false;

  auto *position = reinterpret_cast<float *>(stateVector + kStatePosition);
  auto *previous =
      reinterpret_cast<float *>(stateVector + kStatePreviousPosition);
  auto *velocity = reinterpret_cast<float *>(stateVector + kStateVelocity);

  if (!std::isfinite(position[0]) || !std::isfinite(position[1]) ||
      !std::isfinite(position[2]) || !std::isfinite(previous[0]) ||
      !std::isfinite(previous[1]) || !std::isfinite(previous[2])) {
    return false;
  }
  if (std::fabs(position[0]) > kWorldLimit ||
      std::fabs(position[1]) > kWorldLimit ||
      std::fabs(position[2]) > kWorldLimit) {
    return false;
  }
  if (horizontalLength(position[0] - previous[0],
                       position[2] - previous[2]) > 10.0f) {
    return false;
  }

  out.actor = actor;
  out.position = position;
  out.previous = previous;
  out.velocity = velocity;
  out.rotation = reinterpret_cast<float *>(base + kActorRotationComponent);
  out.valid = true;
  return true;
}

// Регулировка высоты прыжка. Скорость считается от ванильного импульса:
// v = 0.42 * sqrt(h/1.25), т.е. при h=1.25 это ровно ванильный прыжок.
// В авто-режиме выставляем РЕАЛЬНЫЙ ввод прыжка (MoveInputComponent), чтобы
// клиент/сервер видели обычное нажатие — именно «прыжок без ввода» и палился.
// Скорость пишем одновременно; т.к. vy>0, ванильный джамп её не перезапишет.
void applyAutoJump(ActorState &s, int tick, bool onGround) {
  static thread_local bool prevJump = false;

  gameecs::MoveInputState moveInput;
  const bool haveInput = gameecs::readMoveInput(s.actor, moveInput);
  const bool jumping = haveInput && moveInput.jumping;
  const bool manualEdge = jumping && !prevJump;  // только новое нажатие
  prevJump = jumping;

  if (!onGround) return;

  const bool autoJumpMode = g_autoJumpMode.load(std::memory_order_relaxed) == 1;
  if (!manualEdge && !autoJumpMode) return;
  if (autoJumpMode && !manualEdge &&
      horizontalLength(s.velocity[0], s.velocity[2]) < 0.02f) {
    return;
  }
  if (tick - g_lastAutoJumpTick < 3) return;

  float h = g_autoJumpHeight.load(std::memory_order_relaxed);
  if (h < 0.2f) h = 0.2f;
  if (h > 20.0f) h = 20.0f;  // до 20 блоков

  // v = 0.42 * sqrt(h / 1.25): при h=1.25 скорость ровно ванильная.
  const float v = 0.42f * std::sqrt(h / 1.25f);
  s.velocity[1] = v;

  if (autoJumpMode && !manualEdge) {
    // Настоящий ввод прыжка -> START_JUMPING уходит штатно, не «фантомно».
    gameecs::setMoveJumping(s.actor, true);
    g_releaseJump.store(true, std::memory_order_relaxed);
    g_jumpBitTicks.store(1, std::memory_order_relaxed);
  }
  g_lastAutoJumpTick = tick;
}

// Полёт: прямой контроль вертикальной скорости; в bypass-режиме коррекции
// сервера гасятся, чтобы клиента не отбрасывало. В воздухе добавляем
// гравитационную компенсацию, иначе зависание медленно проседает.
void applyFly(ActorState &s, bool onGround) {
  gameecs::MoveInputState moveInput;
  const bool haveInput = gameecs::readMoveInput(s.actor, moveInput);

  const float up = g_flyUp.load(std::memory_order_relaxed);
  const float down = g_flyDown.load(std::memory_order_relaxed);
  const int mode = g_flyMode.load(std::memory_order_relaxed);

  float targetVy;
  if (haveInput && moveInput.jumping) {
    targetVy = up;
  } else if (haveInput && moveInput.sneaking) {
    targetVy = -down;
  } else {
    // Normal зависает на месте, Bypass плавно «оседает», как под гравитацией.
    targetVy = (mode == 0) ? 0.0f : -0.02f;
  }
  // Никакой «компенсации гравитации»: движок интегрирует записанную скорость
  // напрямую, поэтому +0.08 заставлял персонажа улетать вверх (баг прошлой
  // версии). 0f = честное зависание, вверх — только при зажатом прыжке.
  s.velocity[1] = targetVy;

  const float speed = g_flySpeed.load(std::memory_order_relaxed);
  if (speed > 0.01f && std::fabs(speed - 1.0f) > 0.01f) {
    const float hSpeed = horizontalLength(s.velocity[0], s.velocity[2]);
    if (hSpeed > 0.005f) {
      const float scaled = hSpeed * speed;
      const float k = scaled / hSpeed;
      s.velocity[0] *= k;
      s.velocity[2] *= k;
    }
  }

  // Гасим коррекции только когда реально летим (в воздухе), чтобы не терять
  // важные пакеты (телепорт/респавн) на земле.
  if (g_flyCancelCorrections.load(std::memory_order_relaxed) && !onGround) {
    g_cheatingPublished.store(true, std::memory_order_relaxed);
  }
}

// Jesus: ходьба по воде «как по суше». Воду определяем по ECS-флагу
// WasInWater/WaterTravelFlagComponent (хеши entt выведены из имён классов),
// плюс узкий фолбэк по устойчивому медленному погружению (вода тормозит
// падение). Гасим погружение (vy<=0 -> 0), не тянем вверх; прыжок работает.
// Активность разделяем с пост-тиком, чтобы перебить водную физику движка.
std::atomic<bool> g_jesusActive{false};
thread_local float g_jesusPreY = 0.0f;
thread_local bool g_jesusPreValid = false;

void applyJesus(ActorState &s, bool onGround) {
  static thread_local int streak = 0;
  if (onGround) {
    streak = 0;
    g_jesusActive.store(false, std::memory_order_relaxed);
    return;
  }

  const bool inWater = gameecs::inWater(s.actor);
  const float vy = s.velocity[1];
  if (vy < 0.0f && vy > -0.10f) {
    ++streak;
  } else if (!inWater) {
    streak = 0;
  }
  const bool active = inWater || streak >= 2;
  if (!active) {
    g_jesusActive.store(false, std::memory_order_relaxed);
    return;
  }

  gameecs::MoveInputState moveInput;
  const bool haveInput = gameecs::readMoveInput(s.actor, moveInput);
  if (haveInput && moveInput.jumping) {
    g_jesusActive.store(false, std::memory_order_relaxed);
    return;  // можно выпрыгнуть
  }

  if (vy < 0.0f) {
    s.velocity[1] = 0.0f;  // стоим на поверхности, не тонем
    g_cheatingPublished.store(true, std::memory_order_relaxed);
  }
  g_jesusActive.store(true, std::memory_order_relaxed);
}

// ItemStack пригоден для установки: валиден и непуст.
bool usableHeldItem(void *item) {
  if (!plausibleItem(item)) return false;
  uint8_t count = 0;
  const uintptr_t countAddr = reinterpret_cast<uintptr_t>(item) + 0x22;
  if (!readableMemory(countAddr, 1)) return false;
  std::memcpy(&count, reinterpret_cast<const void *>(countAddr), 1);
  return count > 0;
}

// Scaffold: ставит блок в целевую клетку, перебирая 6 соседних блоков как
// опору (клик по грани соседа, обращённой к цели). Так работает и «башня» под
// собой (при прыжке/падении опора — блок под ногами/уступ), и мост вперёд.
// Использует штатный GameMode::useItemOn с реальным ItemStack из руки, поэтому
// транзакция и предсказание идут обычным путём и сервер принимает блок.
void applyScaffold(ActorState &s, int tick, bool onGround) {
  const int step = std::max(1, g_scaffoldInterval.load(std::memory_order_relaxed));
  if (step > 1 && (tick % step) != 0) return;

  UseItemOnFn useItemOn = g_useItemOn.load(std::memory_order_relaxed);
  if (!useItemOn) return;

  void *client = g_clientInstance.load(std::memory_order_relaxed);
  if (!client) return;
  auto gmPtr = *reinterpret_cast<uintptr_t *>(
      reinterpret_cast<char *>(client) + kClientInstanceGameMode);
  if (gmPtr < 0x1000 || (gmPtr & 0x7) != 0) return;

  if (g_scaffoldRotate.load(std::memory_order_relaxed) && s.rotation) {
    const float pitch = g_scaffoldPitch.load(std::memory_order_relaxed);
    s.rotation[0] = std::max(-90.0f, std::min(-pitch, 0.0f));
  }

  // Кандидаты предмета для установки: последний реально использованный, затем
  // слоты контейнеров рук (0x00/0x10). Перебираем до 4 — какой реально
  // поставит блок, тот и используется (не зависит от точной раскладки
  // EquipmentComponent). Всё валидируется, мусор в useItemOn не уходит.
  void *candidates[4] = {nullptr, nullptr, nullptr, nullptr};
  int candidateCount = 0;
  auto addCandidate = [&](void *it) {
    if (!usableHeldItem(it) || candidateCount >= 4) return;
    for (int i = 0; i < candidateCount; ++i) {
      if (candidates[i] == it) return;
    }
    candidates[candidateCount++] = it;
  };
  addCandidate(g_capturedItem.load(std::memory_order_relaxed));
  const unsigned long handOffsets[2] = {0x00, 0x10};
  for (int oi = 0; oi < 2; ++oi) {
    for (int slot = 0; slot < 4; ++slot) {
      addCandidate(gameecs::equipmentItemStack(s.actor, handOffsets[oi], slot));
    }
  }
  if (candidateCount == 0) return;

  // Целевая клетка, куда ставим блок.
  const int feetX = static_cast<int>(std::floor(s.position[0]));
  const int feetY = static_cast<int>(std::floor(s.position[1]));
  const int feetZ = static_cast<int>(std::floor(s.position[2]));
  int tx = feetX;
  int ty = feetY - 1;   // клетка прямо под ногами — «башня»/ловля падения
  int tz = feetZ;
  const int mode = g_scaffoldMode.load(std::memory_order_relaxed);
  if (mode == 0 && onGround) return;  // на земле под собой ставить нечего
  if (mode == 1) {
    ty = feetY - 1;     // мост на уровне пола
    float dx = s.velocity[0];
    float dz = s.velocity[2];
    if (horizontalLength(dx, dz) < 0.02f && s.rotation) {
      const float yawRad = s.rotation[1] * 3.14159265f / 180.0f;
      dx = -std::sin(yawRad);
      dz = std::cos(yawRad);
    }
    if (std::fabs(dx) >= std::fabs(dz)) {
      tx += (dx >= 0.0f) ? 1 : -1;
    } else {
      tz += (dz >= 0.0f) ? 1 : -1;
    }
  }

  struct BlockPos {
    int32_t x;
    int32_t y;
    int32_t z;
  };

  // 6 направлений к соседу и грань соседа, обращённая к цели.
  // d=+Y -> DOWN(0), -Y -> UP(1), +Z -> NORTH(2), -Z -> SOUTH(3),
  // +X -> WEST(4), -X -> EAST(5).
  static const int dn[6][3] = {{0, 1, 0},  {0, -1, 0}, {0, 0, 1},
                               {0, 0, -1}, {1, 0, 0},  {-1, 0, 0}};
  static const uint8_t face[6] = {0, 1, 2, 3, 4, 5};

  bool placed = false;
  void *usedItem = nullptr;
  for (int c = 0; c < candidateCount && !placed; ++c) {
    for (int k = 0; k < 6 && !placed; ++k) {
      BlockPos ref{tx + dn[k][0], ty + dn[k][1], tz + dn[k][2]};
      placed =
          useItemOn(reinterpret_cast<void *>(gmPtr), candidates[c], &ref,
                    face[k]);
      if (placed) usedItem = candidates[c];
    }
  }

  if (g_debug.load(std::memory_order_relaxed)) {
    static uint32_t s_log = 0;
    if ((s_log++ % 6) == 0) {
      __android_log_print(ANDROID_LOG_INFO, kTag,
                          "scaffold target (%d,%d,%d) placed=%d gm=%p "
                          "item=%p cands=%d",
                          tx, ty, tz, placed ? 1 : 0,
                          reinterpret_cast<void *>(gmPtr), usedItem,
                          candidateCount);
    }
  }
}

void applyFeatures(void *actor) {
  const bool master = g_enabled.load(std::memory_order_relaxed);
  if (!master) return;

  ActorState state;
  if (!readState(actor, state)) return;

  static thread_local int groundStreak = 0;
  static thread_local uint32_t tickCounter = 0;
  const int tick = static_cast<int>(++tickCounter);

  bool onGround = gameecs::hasOnGround(actor);
  if (onGround) {
    groundStreak = 3;
  } else {
    const float dy = state.position[1] - state.previous[1];
    const float vy = state.velocity[1];
    const bool groundedSample =
        std::fabs(dy) < 0.0015f && vy <= 0.001f && vy > -0.5f;
    groundStreak = groundedSample ? groundStreak + 1 : 0;
    onGround = groundStreak >= 2;
  }
  g_onGround.store(onGround, std::memory_order_relaxed);

  const bool fly = g_flyEnabled.load(std::memory_order_relaxed);
  const bool jesus = g_jesusEnabled.load(std::memory_order_relaxed);
  const bool scaffold = g_scaffoldEnabled.load(std::memory_order_relaxed);
  const bool autoJump = g_autoJumpEnabled.load(std::memory_order_relaxed);

  g_cheatingPublished.store(false, std::memory_order_relaxed);

  if (autoJump) applyAutoJump(state, tick, onGround);
  if (fly) applyFly(state, onGround);
  if (jesus) {
    applyJesus(state, onGround);
    g_jesusPreY = state.position[1];
    g_jesusPreValid = g_jesusActive.load(std::memory_order_relaxed);
  }
  if (scaffold) applyScaffold(state, tick, onGround);

  if (g_debug.load(std::memory_order_relaxed)) {
    static uint32_t s_log = 0;
    if ((s_log++ % 40) == 0) {
      __android_log_print(
          ANDROID_LOG_INFO, kTag,
          "move fly=%d jesus=%d scaffold=%d jump=%d onGround=%d "
          "vy=%.3f y=%.2f",
          fly ? 1 : 0, jesus ? 1 : 0, scaffold ? 1 : 0, autoJump ? 1 : 0,
          onGround ? 1 : 0, static_cast<double>(state.velocity[1]),
          static_cast<double>(state.position[1]));
    }
  }
}

void resetFeatures() {
  g_cheatingPublished.store(false, std::memory_order_relaxed);
  g_capturedItem.store(nullptr, std::memory_order_relaxed);
  g_jumpBitTicks.store(0, std::memory_order_relaxed);
  g_lastAutoJumpTick = -100;
}

// Применяем движение в Actor::baseTick через vtable[25] — именно там Bedrock
// интегрирует velocity. Без этого записи из normalTick затираются физикой
// (поэтому Jesus/AutoJump «ничего не делали»). Так же делает Speed-модуль;
// pl::memory::hook поддерживает цепочку, так что двойной перехват безопасен.
void applyMovementPost(void *actor) {
  if (!actor) return;
  if (g_releaseJump.exchange(false, std::memory_order_relaxed)) {
    gameecs::setMoveJumping(actor, false);
  }
  if (!g_jesusEnabled.load(std::memory_order_relaxed)) return;
  if (!g_jesusActive.load(std::memory_order_relaxed)) return;
  ActorState state;
  if (!readState(actor, state)) return;
  if (state.velocity[1] < 0.0f) {
    state.velocity[1] = 0.0f;
  }
  // Водная физика движка за тик слегка утягивает вниз — возвращаем Y на
  // уровень входа (пока просадка небольшая). Так реально стоишь на воде, а не
  // медленно тонешь. Большую просадку (вход в воду сверху) не трогаем.
  if (g_jesusPreValid) {
    const float dy = g_jesusPreY - state.position[1];
    if (dy > 0.0f && dy < 0.15f) {
      state.position[1] = g_jesusPreY;
    }
  }
}

void baseTickDetour(void *actor) {
  void *localPlayer = g_localPlayer.load(std::memory_order_relaxed);
  const bool isLocal = localPlayer && actor == localPlayer;
  if (isLocal) applyFeatures(actor);
  if (g_baseTickOriginal) g_baseTickOriginal(actor);
  if (isLocal) applyMovementPost(actor);
}

bool installBaseTickHook(void *localPlayer) {
  if (g_baseTickHookInstalled) return true;
  if (g_baseTickHookAttempted || !localPlayer) return false;
  g_baseTickHookAttempted = true;

  auto **vtable = *reinterpret_cast<void ***>(localPlayer);
  if (!vtable) return false;

  const uintptr_t baseTick = reinterpret_cast<uintptr_t>(vtable[25]);
  if (!baseTick || baseTick == g_normalTickAddress) return false;
  if (!isMinecraftAddress(baseTick)) return false;
  if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(baseTick),
                       reinterpret_cast<pl::memory::FuncPtr>(baseTickDetour),
                       reinterpret_cast<pl::memory::FuncPtr *>(
                           &g_baseTickOriginal)) != 0) {
    logWarning("Unable to hook Actor::baseTick");
    return false;
  }

  g_baseTickAddress = baseTick;
  g_baseTickHookInstalled = true;
  logInfo("Actor::baseTick hooked (vtable[25])");
  return true;
}

void normalTickDetour(void *actor) {
  if (actor) {
    void *clientLocal = g_clientLocalPlayer.load(std::memory_order_relaxed);
    if (!clientLocal || actor == clientLocal) {
      g_localPlayer.exchange(actor, std::memory_order_acq_rel);
    }
    if (!g_baseTickHookInstalled && !g_baseTickHookAttempted) {
      installBaseTickHook(actor);
    }
  }

  void *local = g_localPlayer.load(std::memory_order_relaxed);
  if (!g_baseTickHookInstalled && (!local || actor == local)) {
    applyFeatures(actor);
  }
  if (g_normalTickOriginal) g_normalTickOriginal(actor);
  if (!g_baseTickHookInstalled && (!local || actor == local)) {
    applyMovementPost(actor);
  }
}

void *clientInstanceUpdateDetour(void *clientInstance, bool value) {
  void *result = g_clientInstanceUpdateOriginal
                     ? g_clientInstanceUpdateOriginal(clientInstance, value)
                     : nullptr;

  g_clientInstance.store(clientInstance, std::memory_order_release);

  if (clientInstance && g_getLocalPlayer) {
    void *localPlayer = g_getLocalPlayer(clientInstance);
    g_clientLocalPlayer.store(localPlayer, std::memory_order_release);
    if (localPlayer) {
      void *previous =
          g_localPlayer.exchange(localPlayer, std::memory_order_acq_rel);
      if (localPlayer != previous) resetFeatures();
      installBaseTickHook(localPlayer);
    } else {
      if (g_localPlayer.exchange(nullptr, std::memory_order_acq_rel)) {
        resetFeatures();
      }
    }
  } else {
    g_clientLocalPlayer.store(nullptr, std::memory_order_release);
    if (g_localPlayer.exchange(nullptr, std::memory_order_acq_rel)) {
      resetFeatures();
    }
  }

  return result;
}

bool initialize() {
  std::lock_guard<std::mutex> lock(g_initMutex);
  if (g_installed) return true;

  const auto now = std::chrono::steady_clock::now();
  if (g_lastInitAttempt.time_since_epoch().count() != 0 &&
      now - g_lastInitAttempt < std::chrono::seconds(2)) {
    return false;
  }
  g_lastInitAttempt = now;

  const uintptr_t normalTickAddress =
      pl::memory::resolveSignature(kNormalTickSignature, kMinecraftLibrary);
  const uintptr_t clientUpdateAddress = pl::memory::resolveSignature(
      kClientInstanceUpdateSignature, kMinecraftLibrary);
  const uintptr_t localPlayerAddress = pl::memory::resolveSignature(
      kClientInstanceGetLocalPlayerSignature, kMinecraftLibrary);

  if (!normalTickAddress || !clientUpdateAddress || !localPlayerAddress) {
    logWarning("Unable to resolve required Minecraft signatures");
    return false;
  }

  g_normalTickAddress = normalTickAddress;
  if (!g_getLocalPlayer) {
    g_getLocalPlayer = reinterpret_cast<GetLocalPlayerFn>(localPlayerAddress);
  }

  __android_log_print(ANDROID_LOG_INFO, kTag,
                      "signatures: normalTick=%p clientUpdate=%p "
                      "getLocalPlayer=%p",
                      reinterpret_cast<void *>(normalTickAddress),
                      reinterpret_cast<void *>(clientUpdateAddress),
                      reinterpret_cast<void *>(localPlayerAddress));

  if (!g_clientHookInstalled) {
    if (pl::memory::hook(
            reinterpret_cast<pl::memory::FuncPtr>(clientUpdateAddress),
            reinterpret_cast<pl::memory::FuncPtr>(clientInstanceUpdateDetour),
            reinterpret_cast<pl::memory::FuncPtr *>(
                &g_clientInstanceUpdateOriginal)) != 0) {
      logWarning("Unable to hook ClientInstance::update");
      return false;
    }
    g_clientHookInstalled = true;
  }

  if (!g_tickHookInstalled) {
    if (pl::memory::hook(
            reinterpret_cast<pl::memory::FuncPtr>(normalTickAddress),
            reinterpret_cast<pl::memory::FuncPtr>(normalTickDetour),
            reinterpret_cast<pl::memory::FuncPtr *>(&g_normalTickOriginal)) !=
        0) {
      logWarning("Unable to hook LocalPlayer::normalTick");
      return false;
    }
    g_tickHookInstalled = true;
  }

  Dl_info info{};
  if (dladdr(reinterpret_cast<void *>(normalTickAddress), &info) != 0) {
    g_gameBase = reinterpret_cast<uintptr_t>(info.dli_fbase);
  }

  if (!installUseItemOnHook(g_gameBase)) {
    logWarning("Scaffold placement disabled (useItemOn hook failed)");
  }
  if (!installPacketHook(g_gameBase)) {
    logWarning("AutoJump input bit disabled (packet hook failed)");
  }

  if (!installPredictionHook()) {
    logWarning("Correction cancellation disabled");
  }

  g_installed = true;
  logInfo("Movement module initialized");
  return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_MovementMod_nativeInit(
    JNIEnv *, jclass) {
  return initialize() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_MovementMod_nativeSetEnabled(
    JNIEnv *, jclass, jboolean enabled) {
  const bool value = enabled == JNI_TRUE;
  g_enabled.store(value, std::memory_order_relaxed);
  if (!value) resetFeatures();
  __android_log_print(ANDROID_LOG_INFO, kTag, "enabled=%d", value ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_MovementMod_nativeIsEnabled(
    JNIEnv *, jclass) {
  return g_enabled.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

#define MOVEMENT_BOOL_SETTER(name, field)                                     \
  extern "C" JNIEXPORT void JNICALL                                           \
      Java_org_levimc_launcher_core_mods_inbuilt_nativemod_MovementMod_##name(\
          JNIEnv *, jclass, jboolean value) {                                 \
    field.store(value == JNI_TRUE, std::memory_order_relaxed);                \
  }

#define MOVEMENT_INT_SETTER(name, field)                                      \
  extern "C" JNIEXPORT void JNICALL                                           \
      Java_org_levimc_launcher_core_mods_inbuilt_nativemod_MovementMod_##name(\
          JNIEnv *, jclass, jint value) {                                     \
    field.store(static_cast<int>(value), std::memory_order_relaxed);          \
  }

#define MOVEMENT_FLOAT_SETTER(name, field)                                    \
  extern "C" JNIEXPORT void JNICALL                                           \
      Java_org_levimc_launcher_core_mods_inbuilt_nativemod_MovementMod_##name(\
          JNIEnv *, jclass, jfloat value) {                                   \
    field.store(static_cast<float>(value), std::memory_order_relaxed);        \
  }

MOVEMENT_BOOL_SETTER(nativeSetFlyEnabled, g_flyEnabled)
MOVEMENT_INT_SETTER(nativeSetFlyMode, g_flyMode)
MOVEMENT_FLOAT_SETTER(nativeSetFlySpeed, g_flySpeed)
MOVEMENT_FLOAT_SETTER(nativeSetFlyUp, g_flyUp)
MOVEMENT_FLOAT_SETTER(nativeSetFlyDown, g_flyDown)
MOVEMENT_BOOL_SETTER(nativeSetFlyCancelCorrections, g_flyCancelCorrections)

MOVEMENT_BOOL_SETTER(nativeSetJesusEnabled, g_jesusEnabled)
MOVEMENT_INT_SETTER(nativeSetJesusMode, g_jesusMode)
MOVEMENT_FLOAT_SETTER(nativeSetJesusDepth, g_jesusDepth)

MOVEMENT_BOOL_SETTER(nativeSetScaffoldEnabled, g_scaffoldEnabled)
MOVEMENT_INT_SETTER(nativeSetScaffoldMode, g_scaffoldMode)
MOVEMENT_FLOAT_SETTER(nativeSetScaffoldPitch, g_scaffoldPitch)
MOVEMENT_INT_SETTER(nativeSetScaffoldInterval, g_scaffoldInterval)
MOVEMENT_BOOL_SETTER(nativeSetScaffoldRotate, g_scaffoldRotate)

MOVEMENT_BOOL_SETTER(nativeSetAutoJumpEnabled, g_autoJumpEnabled)
MOVEMENT_FLOAT_SETTER(nativeSetAutoJumpHeight, g_autoJumpHeight)
MOVEMENT_INT_SETTER(nativeSetAutoJumpMode, g_autoJumpMode)

MOVEMENT_BOOL_SETTER(nativeSetDebug, g_debug)
