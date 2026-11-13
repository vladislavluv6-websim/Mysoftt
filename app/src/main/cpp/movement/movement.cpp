#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <sys/mman.h>
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

// Установка блока делегируется игровому методу GameMode::useItemOn (сборка
// транзакции item-use-on-block попадает в тот же сетевой стек, что и обычная
// игра, поэтому сервер принимает блок). Оффсет версионно-зависим и по умолчанию
// не закреплён (0): без пина перед сборкой модуль не ставит блок и логирует это.
// Порядок аргументов ABI — (GameMode*, int x, int y, int z, int face,
// const float* hitPos, bool isFirstEvent).
constexpr uintptr_t kUseItemOnFileOffset = 0x0;

constexpr float kGravity = 0.08f;
constexpr float kWorldLimit = 3.0e7f;

using NormalTickFn = void (*)(void *);
using BaseTickFn = void (*)(void *);
using ClientInstanceUpdateFn = void *(*)(void *, bool);
using GetLocalPlayerFn = void *(*)(void *);
using PredictionHandleFn = void *(*)(void *, void *, void *, void *);
using UseItemOnFn = void (*)(void *, int, int, int, int, const float *, bool);

NormalTickFn g_normalTickOriginal = nullptr;
BaseTickFn g_baseTickOriginal = nullptr;
ClientInstanceUpdateFn g_clientInstanceUpdateOriginal = nullptr;
GetLocalPlayerFn g_getLocalPlayer = nullptr;
PredictionHandleFn g_predictionHandleOriginal = nullptr;

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

uintptr_t g_gameBase = 0;

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

float jumpVelocityFor(float height) {
  // v = sqrt(2 * g * h): высота апекса в блоках.
  return std::sqrt(2.0f * kGravity * std::max(0.05f, height));
}

// Регулировка высоты прыжка: подменяем импульс прыжка ванильной формулой.
void applyAutoJump(ActorState &s, int tick) {
  const bool onGround = g_onGround.load(std::memory_order_relaxed);
  if (!onGround) return;

  gameecs::MoveInputState moveInput;
  const bool haveInput = gameecs::readMoveInput(s.actor, moveInput);
  const bool manualJump = haveInput && moveInput.jumping;
  const bool autoJump = g_autoJumpMode.load(std::memory_order_relaxed) == 1;
  if (!manualJump && !autoJump) return;
  if (tick - g_lastAutoJumpTick < 2) return;

  const float h = g_autoJumpHeight.load(std::memory_order_relaxed);
  s.velocity[1] = jumpVelocityFor(h);
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
  if (!onGround) targetVy += kGravity;
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

  if (g_flyCancelCorrections.load(std::memory_order_relaxed)) {
    g_cheatingPublished.store(true, std::memory_order_relaxed);
  }
}

// Jesus: ходим по воде — гасим вертикальную скорость на поверхности воды.
void applyJesus(ActorState &s) {
  const bool onGround = g_onGround.load(std::memory_order_relaxed);
  if (onGround) return;

  const float vy = s.velocity[1];
  // Сигнатура погружения в воду: умеренное отрицательное vy (вода тормозит
  // падение), без него на земле игрок падает быстрее.
  if (vy < 0.05f && vy > -0.62f) {
    const int mode = g_jesusMode.load(std::memory_order_relaxed);
    const float depth = g_jesusDepth.load(std::memory_order_relaxed);
    // Компенсация гравитации держит поверхность стабильной.
    s.velocity[1] = kGravity + ((mode == 0) ? 0.0f : 0.02f);
    if (depth > 0.0f) {
      s.position[1] += depth;
    }
    g_cheatingPublished.store(true, std::memory_order_relaxed);
  }
}

// Scaffold: целимся вниз/вперёд и ставим блок под собой (мосты).
//
// Позиция цели детерминирована (блок под ногами либо перед игроком по yaw),
// а сама установка делегируется игровому методу GameMode::useItemOn. Если
// оффсет не закреплён (kUseItemOnFileOffset == 0) — блок не ставится, но
// игровой поток не падает.
UseItemOnFn resolveUseItemOn() {
  if (kUseItemOnFileOffset == 0 || g_gameBase == 0) return nullptr;
  return reinterpret_cast<UseItemOnFn>(g_gameBase + kUseItemOnFileOffset);
}

void applyScaffold(ActorState &s, int tick) {
  const int interval = g_scaffoldInterval.load(std::memory_order_relaxed);
  if (interval > 0 && (tick % (interval + 1)) != 0) return;

  const bool onGround = g_onGround.load(std::memory_order_relaxed);
  if (onGround) return;

  const int mode = g_scaffoldMode.load(std::memory_order_relaxed);
  float targetX = s.position[0];
  float targetZ = s.position[2];
  if (mode == 1 && s.rotation) {
    // Мост вперёд: блок в направлении взгляда, на один блок ниже ног.
    const float yawRad = s.rotation[1] * 3.14159265f / 180.0f;
    const float dx = -std::sin(yawRad);
    const float dz = std::cos(yawRad);
    targetX += dx * 0.6f;
    targetZ += dz * 0.6f;
  }
  const int blockX = static_cast<int>(std::floor(targetX));
  const int blockY = static_cast<int>(std::floor(s.position[1])) - 1;
  const int blockZ = static_cast<int>(std::floor(targetZ));

  if (g_scaffoldRotate.load(std::memory_order_relaxed) && s.rotation) {
    const float pitch = g_scaffoldPitch.load(std::memory_order_relaxed);
    // Ограничиваем наклон, чтобы камера смотрела «в пол» перед установкой.
    s.rotation[0] = std::max(-90.0f, std::min(-pitch, 0.0f));
  }

  UseItemOnFn useItemOn = g_useItemOn.load(std::memory_order_relaxed);
  if (!useItemOn) return;

  void *client = g_clientInstance.load(std::memory_order_relaxed);
  if (!client) return;
  auto gmPtr = *reinterpret_cast<uintptr_t *>(
      reinterpret_cast<char *>(client) + kClientInstanceGameMode);
  if (gmPtr < 0x1000 || (gmPtr & 0x7) != 0) return;

  // Точка попадания: центр верхней грани целевого блока (face = UP).
  const float hit[3] = {blockX + 0.5f,
                        static_cast<float>(blockY) + 1.0f, blockZ + 0.5f};
  useItemOn(reinterpret_cast<void *>(gmPtr), blockX, blockY, blockZ, 1, hit,
            true);

  if (g_debug.load(std::memory_order_relaxed)) {
    static uint32_t s_log = 0;
    if ((s_log++ % 20) == 0) {
      __android_log_print(ANDROID_LOG_INFO, kTag,
                          "scaffold place (%d,%d,%d) gm=%p", blockX, blockY,
                          blockZ, reinterpret_cast<void *>(gmPtr));
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

  bool cheat = false;

  if (autoJump) applyAutoJump(state, tick);
  if (fly) {
    applyFly(state, onGround);
    if (g_flyCancelCorrections.load(std::memory_order_relaxed)) cheat = true;
  }
  if (jesus) {
    applyJesus(state);
    cheat = true;
  }
  if (scaffold) applyScaffold(state, tick);

  g_cheatingPublished.store(cheat, std::memory_order_relaxed);

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
  g_lastAutoJumpTick = -100;
}

void baseTickDetour(void *actor) {
  void *localPlayer = g_localPlayer.load(std::memory_order_relaxed);
  const bool isLocal = localPlayer && actor == localPlayer;
  if (isLocal) applyFeatures(actor);
  if (g_baseTickOriginal) g_baseTickOriginal(actor);
}

bool installBaseTickHook(void *localPlayer) {
  if (g_baseTickHookInstalled) return true;
  if (g_baseTickHookAttempted || !localPlayer) return false;
  g_baseTickHookAttempted = true;

  auto **vtable = *reinterpret_cast<void ***>(localPlayer);
  if (!vtable) {
    logWarning("Local player vtable is null");
    return false;
  }

  const uintptr_t baseTick = reinterpret_cast<uintptr_t>(vtable[25]);
  if (!baseTick || baseTick == g_normalTickAddress) {
    logWarning("Actor::baseTick vtable entry is invalid");
    return false;
  }
  if (!isMinecraftAddress(baseTick)) {
    logWarning("Actor::baseTick vtable entry is not in libminecraftpe");
    return false;
  }
  if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(baseTick),
                       reinterpret_cast<pl::memory::FuncPtr>(baseTickDetour),
                       reinterpret_cast<pl::memory::FuncPtr *>(
                           &g_baseTickOriginal)) != 0) {
    logWarning("Unable to hook Actor::baseTick");
    return false;
  }

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
    if (!g_clientLocalPlayer.load(std::memory_order_relaxed) &&
        !g_baseTickHookInstalled && !g_baseTickHookAttempted) {
      installBaseTickHook(actor);
    }
  }

  if (!g_baseTickHookInstalled) applyFeatures(actor);
  if (g_normalTickOriginal) g_normalTickOriginal(actor);
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
  g_useItemOn.store(resolveUseItemOn(), std::memory_order_relaxed);
  if (!g_useItemOn.load(std::memory_order_relaxed)) {
    logWarning("Scaffold placement offset not pinned for this build; "
               "Scaffold will not place blocks until kUseItemOnFileOffset is set");
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
