#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <link.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>

#include "GameEcs.hpp"
#include "CombatState.hpp"
#include "pl/memory/Hook.hpp"
#include "pl/memory/Signature.hpp"

// Speed module: server-consistent sprint-jump boost.
//
// Safe profile keeps client and server positions identical: no reset bursts,
// no phantom Y, no correction cancellation.  Horizontal boost is applied only
// in air and stays under the vanilla sprint-jump cap, so Polar/Geyser see the
// same movement the client simulates.  The optional desync path remains for
// the aggressive legacy profile, but it is off by default.
namespace {

constexpr const char *kTag = "SpeedMod";
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
constexpr float kMaxExtraBlocksPerTick = 0.0f;

// 1.26.60.04: PlayerAuthInputPacket::write(BinaryStream&, CerealContext&).
// The function writes packet id 0x90 and delegates payload serialisation to
// the cereal context.  Hooking it lets us rewrite mPos/mPosDelta right
// before the packet is encoded/encrypted, exactly like the JS packet hook.
constexpr uintptr_t kPlayerAuthInputWriteOffset = 0xdaa4864;

// PlayerAuthInputPacket layout in 1.26.60.04 (validated against the packet's
// own validate() routine and its copy constructor):
//   +0x30 mRot (Vec2 pitch/yaw)
//   +0x38 mPos (Vec3)
//   +0x44 mYHeadRot
//   +0x48 mPosDelta (Vec3)
//   +0x88 InputData bitset (uint64 array)
constexpr uintptr_t kPacketPos = 0x38;
constexpr uintptr_t kPacketPosDelta = 0x48;
constexpr uintptr_t kPacketInputData = 0x88;

// PlayerAuthInputData bits (Bedrock protocol).
constexpr int kInputSneaking = 8;
constexpr int kInputSprinting = 20;
constexpr int kInputStartJumping = 31;
constexpr int kInputSprintDown = 4;
constexpr int kInputStartSprinting = 25;
constexpr int kInputStopSprinting = 26;

// Speed S1 constants from the userscript.
constexpr float V_GRAVITY = 0.08f;
constexpr float GEYSER_LIMIT = 0.07f;
constexpr float VANILLA_CAP = 0.28f;
constexpr float VANILLA_CAP_SAFE = VANILLA_CAP;
constexpr float AIR_CAP_SAFE = 0.36f;
constexpr float FAST_MOVE_THRESHOLD = 1.0f;
constexpr float PHYSICAL_HOP_MIN = 0.10f;
constexpr float STUCK_THRESHOLD = 0.06f;

using NormalTickFn = void (*)(void *);
using BaseTickFn = void (*)(void *);
using ClientInstanceUpdateFn = void *(*)(void *, bool);
using GetLocalPlayerFn = void *(*)(void *);
using PlayerAuthInputWriteFn = void *(*)(void *, void *, void *, void *);
using PredictionHandleFn = void *(*)(void *, void *, void *, void *);

NormalTickFn g_normalTickOriginal = nullptr;
BaseTickFn g_baseTickOriginal = nullptr;
ClientInstanceUpdateFn g_clientInstanceUpdateOriginal = nullptr;
GetLocalPlayerFn g_getLocalPlayer = nullptr;
PlayerAuthInputWriteFn g_playerAuthWriteOriginal = nullptr;
PredictionHandleFn g_predictionHandleOriginal = nullptr;

std::atomic_bool g_enabled{false};
std::atomic_bool g_speedEnabled{true};
std::atomic_int g_speedMode{0};  // 0 = Air, 1 = Ground, 2 = Both
std::atomic<float> g_motionBoost{0.004f};
std::atomic<float> g_maxSpeed{0.34f};
std::atomic<float> g_gapMax{0.0f};
std::atomic<int> g_resetIntervalMs{3500};
std::atomic<int> g_maxResetTicks{3};
std::atomic_bool g_hopEnabled{true};
constexpr float kMiniJumpHeight = 0.42f;
std::atomic<float> g_hopHeight{kMiniJumpHeight};
std::atomic<int> g_hopInterval{1};
std::atomic_bool g_maskY{false};
std::atomic<int> g_phantomHoldTicks{3};
std::atomic<int> g_fakeJumpTicks{4};
std::atomic_bool g_cancelCorrections{false};
std::atomic_bool g_pauseOnFall{false};
std::atomic_bool g_pauseIfSneaking{true};
std::atomic_bool g_pauseIfNotMoving{true};
std::atomic_bool g_autoSprint{true};
std::atomic_bool g_debug{false};
std::atomic<float> g_legacyMultiplier{1.0f};

std::atomic<void *> g_clientLocalPlayer{nullptr};
std::atomic<void *> g_localPlayer{nullptr};
std::atomic<uint32_t> g_tickLogCounter{0};
std::atomic<uint32_t> g_mismatchLogCounter{0};

// State shared between the game tick and the network write hook.
std::atomic<bool> g_onGround{true};
std::atomic<bool> g_moving{false};
std::atomic<bool> g_sneaking{false};
std::atomic<bool> g_pendingJump{false};
std::atomic<bool> g_phantomBusy{false};
std::atomic<int> g_phantomRequestSeq{0};
std::atomic<float> g_phantomRequestH{0.0f};
std::atomic<float> g_lastRealDist{1.0f};
std::atomic<bool> g_cheatingPublished{false};
std::atomic<uint64_t> g_tickCounter{0};

std::mutex g_initMutex;
bool g_clientHookInstalled = false;
bool g_tickHookInstalled = false;
bool g_baseTickHookInstalled = false;
bool g_baseTickHookAttempted = false;
bool g_packetHookInstalled = false;
bool g_predictionHookInstalled = false;
bool g_installed = false;
uintptr_t g_normalTickAddress = 0;
uintptr_t g_baseTickAddress = 0;
std::chrono::steady_clock::time_point g_lastInitAttempt{};

// ---------------------------------------------------------------------------
// Packet-side state (network thread).  All fields below are guarded by
// g_packetMutex.
// ---------------------------------------------------------------------------
std::mutex g_packetMutex;
float g_lastSentX = 0.0f;
float g_lastSentY = 0.0f;
float g_lastSentZ = 0.0f;
float g_lastRealX = 0.0f;
float g_lastRealY = 0.0f;
float g_lastRealZ = 0.0f;
bool g_hasSentPos = false;
bool g_phantomActive = false;
int g_phantomTick = 0;
bool g_phantomInit = false;
int g_phantomRecent = 0;
float g_phantomTargetH = 0.0f;
float g_phantomBaselineY = 0.0f;
float g_serverY = 0.0f;
int g_seenPhantomSeq = 0;
bool g_cheating = false;
bool g_inResetPhase = false;
int g_resetTicksLeft = 0;
int64_t g_lastResetAt = 0;

void logInfo(const char *message) {
  __android_log_print(ANDROID_LOG_INFO, kTag, "%s", message);
}

void logWarning(const char *message) {
  __android_log_print(ANDROID_LOG_WARN, kTag, "%s", message);
}

void logState() {
  if (!g_debug.load(std::memory_order_relaxed)) return;
  __android_log_print(
      ANDROID_LOG_INFO, kTag,
      "speed en=%d mode=%d boost=%.3f max=%.2f gap=%.2f hop=%d h=%.4f maskY=%d "
      "corr=%d onGround=%d moving=%d sneak=%d dist=%.3f",
      g_speedEnabled.load(std::memory_order_relaxed) ? 1 : 0,
      g_speedMode.load(std::memory_order_relaxed),
      static_cast<double>(g_motionBoost.load(std::memory_order_relaxed)),
      static_cast<double>(g_maxSpeed.load(std::memory_order_relaxed)),
      static_cast<double>(g_gapMax.load(std::memory_order_relaxed)),
      g_hopEnabled.load(std::memory_order_relaxed) ? 1 : 0,
      static_cast<double>(g_hopHeight.load(std::memory_order_relaxed)),
      g_maskY.load(std::memory_order_relaxed) ? 1 : 0,
      g_cancelCorrections.load(std::memory_order_relaxed) ? 1 : 0,
      g_onGround.load(std::memory_order_relaxed) ? 1 : 0,
      g_moving.load(std::memory_order_relaxed) ? 1 : 0,
      g_sneaking.load(std::memory_order_relaxed) ? 1 : 0,
      static_cast<double>(g_lastRealDist.load(std::memory_order_relaxed)));
}

float horizontalLength(float x, float z) { return std::sqrt(x * x + z * z); }

int64_t monotonicMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

void requestPhantom(float height) {
  g_phantomBusy.store(true, std::memory_order_relaxed);
  g_phantomRequestH.store(height, std::memory_order_relaxed);
  g_phantomRequestSeq.fetch_add(1, std::memory_order_release);
}

void resetPacketStateLocked() {
  g_hasSentPos = false;
  g_phantomActive = false;
  g_phantomTick = 0;
  g_phantomInit = false;
  g_phantomRecent = 0;
  g_phantomTargetH = 0.0f;
  g_phantomBaselineY = 0.0f;
  g_serverY = 0.0f;
  g_cheating = false;
  g_cheatingPublished.store(false, std::memory_order_relaxed);
  g_phantomBusy.store(false, std::memory_order_relaxed);
  g_inResetPhase = false;
  g_resetTicksLeft = 0;
  g_lastResetAt = 0;
  g_seenPhantomSeq = g_phantomRequestSeq.load(std::memory_order_acquire);
}

void resetAllState() {
  {
    std::lock_guard<std::mutex> lock(g_packetMutex);
    resetPacketStateLocked();
  }
  g_lastRealDist.store(1.0f, std::memory_order_relaxed);
  g_pendingJump.store(false, std::memory_order_relaxed);
  g_phantomBusy.store(false, std::memory_order_relaxed);
  g_cheatingPublished.store(false, std::memory_order_relaxed);
  g_phantomRequestSeq.store(0, std::memory_order_relaxed);
  g_phantomRequestH.store(0.0f, std::memory_order_relaxed);
  g_tickCounter.store(0, std::memory_order_relaxed);
}

// ---------------------------------------------------------------------------
// Packet bypass (ported from Speed S1's onPacket("player_auth_input")).
// ---------------------------------------------------------------------------
void applyPacketBypass(void *packet) {
  if (!packet) return;

  auto *pos = reinterpret_cast<float *>(static_cast<char *>(packet) + kPacketPos);
  auto *delta =
      reinterpret_cast<float *>(static_cast<char *>(packet) + kPacketPosDelta);
  auto *inputBits =
      reinterpret_cast<uint64_t *>(static_cast<char *>(packet) + kPacketInputData);

  if (!std::isfinite(pos[0]) || !std::isfinite(pos[1]) ||
      !std::isfinite(pos[2])) {
    return;
  }

  const bool speedOn = g_enabled.load(std::memory_order_relaxed) &&
                       g_speedEnabled.load(std::memory_order_relaxed);
  const bool autoSprint = g_autoSprint.load(std::memory_order_relaxed);
  const bool sneaking = ((inputBits[kInputSneaking >> 6] >>
                          (kInputSneaking & 63)) &
                         1u) != 0;
  g_sneaking.store(sneaking, std::memory_order_relaxed);

  // TriggerBot critical-hit window: the server must see a non-sprinting
  // player on the attack tick, so strip every sprint bit and emit the stop
  // edge until the shared window expires.
  const bool sprintSuppressed = combatstate::sprintSuppressed();
  if (sprintSuppressed) {
    auto clearBit = [inputBits](int bit) {
      inputBits[bit >> 6] &= ~(1ULL << (bit & 63));
    };
    clearBit(kInputSprintDown);
    clearBit(kInputSprinting);
    clearBit(kInputStartSprinting);
    inputBits[kInputStopSprinting >> 6] |=
        1ULL << (kInputStopSprinting & 63);
  }

  // Sprint itself is driven through MoveInputComponent every tick (the same
  // thing the userscript's requestSprint(1) does). The userscript never
  // touches the sprint bits in player_auth_input, so neither do we: forcing
  // them here can desync the local state from the server.
  (void)autoSprint;
  (void)sneaking;

  if (g_pendingJump.exchange(false, std::memory_order_acq_rel)) {
    inputBits[kInputStartJumping >> 6] |= 1ULL << (kInputStartJumping & 63);
  }

  const int phantomSeq = g_phantomRequestSeq.load(std::memory_order_acquire);
  const float gapMax = g_gapMax.load(std::memory_order_relaxed);
  const int resetIntervalMs = g_resetIntervalMs.load(std::memory_order_relaxed);
  const int maxResetTicks = g_maxResetTicks.load(std::memory_order_relaxed);
  const int fakeJumpTicks = g_fakeJumpTicks.load(std::memory_order_relaxed);
  const int phantomHoldTicks = g_phantomHoldTicks.load(std::memory_order_relaxed);
  const bool maskY = g_maskY.load(std::memory_order_relaxed);
  const bool debug = g_debug.load(std::memory_order_relaxed);

  std::lock_guard<std::mutex> lock(g_packetMutex);

  static uint64_t s_shapeRng = 0x9E3779B97F4A7C15ULL;
  auto nextShapeRand = []() {
    s_shapeRng ^= s_shapeRng << 13;
    s_shapeRng ^= s_shapeRng >> 7;
    s_shapeRng ^= s_shapeRng << 17;
    return static_cast<float>((s_shapeRng >> 40) & 0xFFFFFFu) /
           static_cast<float>(0x1000000u);
  };
  const float shapedGapMax = gapMax * (0.86f + 0.28f * nextShapeRand());
  const int shapedResetMs =
      std::max(900, static_cast<int>(resetIntervalMs *
                                     (0.82f + 0.36f * nextShapeRand())));
  const bool onGround = g_onGround.load(std::memory_order_relaxed);
  const float sendCap = onGround ? VANILLA_CAP_SAFE : AIR_CAP_SAFE;

  // Start a new phantom when the tick hook requested one.
  if (phantomSeq != g_seenPhantomSeq) {
    g_seenPhantomSeq = phantomSeq;
    if (maskY) {
      g_phantomActive = true;
      g_phantomTick = 0;
      g_phantomInit = false;
      g_phantomTargetH = g_phantomRequestH.load(std::memory_order_relaxed);
    }
  }

  const float realX = pos[0];
  const float realY = pos[1];
  const float realZ = pos[2];

  // Teleport / dimension change / respawn: rebase, do not treat as movement.
  if (g_hasSentPos) {
    const float jumpX = realX - g_lastRealX;
    const float jumpY = realY - g_lastRealY;
    const float jumpZ = realZ - g_lastRealZ;
    if (horizontalLength(jumpX, jumpZ) > 8.0f || std::fabs(jumpY) > 8.0f) {
      resetPacketStateLocked();
    }
  }

  if (!g_hasSentPos) {
    g_lastSentX = g_lastRealX = realX;
    g_lastSentY = g_lastRealY = realY;
    g_lastSentZ = g_lastRealZ = realZ;
    g_serverY = realY;
    g_hasSentPos = true;
    g_lastRealDist.store(1.0f, std::memory_order_relaxed);
    return;
  }

  if (!speedOn && !g_phantomActive && g_phantomRecent == 0) {
    g_serverY = realY;
    g_cheating = false;
    g_cheatingPublished.store(false, std::memory_order_relaxed);
    g_phantomBusy.store(false, std::memory_order_relaxed);
    g_lastSentX = g_lastRealX = realX;
    g_lastSentY = g_lastRealY = realY;
    g_lastSentZ = g_lastRealZ = realZ;
    g_lastRealDist.store(1.0f, std::memory_order_relaxed);
    return;
  }

  const int64_t now = monotonicMs();
  float packetY = realY;

  // Phantom Y: hides a low hop from the server simulation.
  if (g_phantomActive) {
    if (!g_phantomInit) {
      g_serverY = realY;
      g_phantomBaselineY = realY;
      g_phantomInit = true;
    }
    if (g_phantomTargetH < PHYSICAL_HOP_MIN) {
      g_serverY = g_phantomBaselineY + g_phantomTargetH;
      packetY = g_serverY;
      ++g_phantomTick;
      if (g_phantomTick >= fakeJumpTicks) {
        g_phantomActive = false;
        g_phantomRecent = 5;
        g_phantomBusy.store(false, std::memory_order_relaxed);
      }
    } else {
      const float vJump = std::sqrt(2.0f * V_GRAVITY * g_phantomTargetH);
      g_serverY += vJump * std::pow(0.98f, static_cast<float>(g_phantomTick)) -
                   V_GRAVITY;
      ++g_phantomTick;
      const float gap = g_serverY - realY;
      if (gap > GEYSER_LIMIT) {
        g_serverY = realY + GEYSER_LIMIT;
      } else if (gap < -GEYSER_LIMIT) {
        g_serverY = realY - GEYSER_LIMIT;
      }
      packetY = g_serverY;
      if (g_phantomTick >= phantomHoldTicks) {
        g_phantomActive = false;
        g_phantomRecent = 5;
        g_phantomBusy.store(false, std::memory_order_relaxed);
      }
    }
  } else if (g_phantomRecent > 0) {
    --g_phantomRecent;
    const float gapY = realY - g_serverY;
    if (std::fabs(gapY) > 0.005f) {
      g_serverY += gapY * 0.6f;
      if (std::fabs(realY - g_serverY) < 0.005f) g_serverY = realY;
      packetY = g_serverY;
    } else {
      g_serverY = realY;
      packetY = g_serverY;
    }
  } else {
    g_serverY = realY;
    packetY = g_serverY;
  }

  const float realDX = realX - g_lastRealX;
  const float realDZ = realZ - g_lastRealZ;
  const float realDist = horizontalLength(realDX, realDZ);

  const float gapBeforeX = realX - g_lastSentX;
  const float gapBeforeZ = realZ - g_lastSentZ;
  const float gapBefore = horizontalLength(gapBeforeX, gapBeforeZ);

  const bool allowScale = realDist <= FAST_MOVE_THRESHOLD;

  if (realDist < STUCK_THRESHOLD && speedOn) {
    const float stuckX = g_lastSentX + realDX;
    const float stuckZ = g_lastSentZ + realDZ;

    pos[0] = stuckX;
    pos[1] = packetY;
    pos[2] = stuckZ;
    delta[0] = realDX;
    delta[1] = packetY - g_lastSentY;
    delta[2] = realDZ;

    g_cheating = false;
    g_cheatingPublished.store(false, std::memory_order_relaxed);
    g_inResetPhase = false;
    g_lastResetAt = now;

    g_lastSentX = stuckX;
    g_lastSentY = packetY;
    g_lastSentZ = stuckZ;
    g_lastRealX = realX;
    g_lastRealY = realY;
    g_lastRealZ = realZ;
    g_lastRealDist.store(realDist, std::memory_order_relaxed);
    return;
  }

  if (speedOn && allowScale) {
    if (g_inResetPhase) {
      --g_resetTicksLeft;
      if (g_resetTicksLeft <= 0 || gapBefore < 0.03f) {
        g_inResetPhase = false;
        g_lastResetAt = now;
      }
    } else if (now - g_lastResetAt >= shapedResetMs && gapBefore > 0.08f) {
      g_inResetPhase = true;
      g_resetTicksLeft = maxResetTicks;
    }
  }

  float packetX;
  float packetZ;
  bool shouldScale = false;

  if (speedOn && allowScale && realDist > 0.005f) {
    if (g_inResetPhase) {
      // Догоняем серверную позицию теми же легальными шагами, что и
      // обычное движение: без скачков 0.68 блока за один auth-input.
      const float gapLen = horizontalLength(realX - g_lastSentX,
                                            realZ - g_lastSentZ);
      if (gapLen > 1e-5f) {
        const float step = std::min(gapLen, sendCap);
        packetX = g_lastSentX +
                  (realX - g_lastSentX) * (step / gapLen);
        packetZ = g_lastSentZ +
                  (realZ - g_lastSentZ) * (step / gapLen);
      } else {
        packetX = g_lastSentX;
        packetZ = g_lastSentZ;
      }
    } else {
      const float roomToGrow = std::max(0.0f, shapedGapMax - gapBefore);
      const float minSendDist = std::max(sendCap, realDist - roomToGrow);
      const float sentDist = std::min(std::min(realDist, minSendDist), sendCap);

      if (sentDist < realDist - 0.001f) {
        shouldScale = true;
        const float scale = sentDist / realDist;
        packetX = g_lastSentX + realDX * scale;
        packetZ = g_lastSentZ + realDZ * scale;
      } else {
        packetX = g_lastSentX + realDX;
        packetZ = g_lastSentZ + realDZ;
      }
    }
  } else {
    packetX = g_lastSentX + realDX;
    packetZ = g_lastSentZ + realDZ;
  }

  float gapX = realX - packetX;
  float gapZ = realZ - packetZ;
  float gapXZ = horizontalLength(gapX, gapZ);

  if (realDist < 0.02f && gapXZ > 0.15f) {
    const float catchup = std::min(gapXZ * 0.35f, sendCap * 0.35f);
    packetX += (gapX / gapXZ) * catchup;
    packetZ += (gapZ / gapXZ) * catchup;
    gapX = realX - packetX;
    gapZ = realZ - packetZ;
    gapXZ = horizontalLength(gapX, gapZ);
  }

  if (gapXZ > gapMax + 6.0f) {
    packetX = realX;
    packetZ = realZ;
    if (debug) __android_log_print(ANDROID_LOG_INFO, kTag, "speed: desync rebase");
  }

  pos[0] = packetX;
  pos[1] = packetY;
  pos[2] = packetZ;
  delta[0] = packetX - g_lastSentX;
  delta[1] = packetY - g_lastSentY;
  delta[2] = packetZ - g_lastSentZ;

  g_cheating = g_phantomActive || g_phantomRecent > 0 || shouldScale ||
               g_inResetPhase;
  g_cheatingPublished.store(g_cheating, std::memory_order_relaxed);
  g_lastSentX = packetX;
  g_lastSentY = packetY;
  g_lastSentZ = packetZ;
  g_lastRealX = realX;
  g_lastRealY = realY;
  g_lastRealZ = realZ;
  g_lastRealDist.store(realDist, std::memory_order_relaxed);

  if (debug) {
    static uint32_t s_packetLog = 0;
    if ((s_packetLog++ % 20) == 0) {
      __android_log_print(
          ANDROID_LOG_INFO, kTag,
          "pkt real=(%.2f,%.2f,%.2f) sent=(%.2f,%.2f,%.2f) gap=%.2f "
          "scale=%d reset=%d phantom=%d",
          realX, realY, realZ, packetX, packetY, packetZ,
          static_cast<double>(gapXZ), shouldScale ? 1 : 0,
          g_inResetPhase ? 1 : 0, g_phantomActive ? 1 : 0);
    }
  }
}

void *playerAuthInputWriteDetour(void *packet, void *a1, void *a2, void *a3) {
  if (g_enabled.load(std::memory_order_relaxed) && packet) {
    applyPacketBypass(packet);
  }
  return g_playerAuthWriteOriginal
             ? g_playerAuthWriteOriginal(packet, a1, a2, a3)
             : nullptr;
}

// ---------------------------------------------------------------------------
// Movement tick: hop + horizontal boost.
// ---------------------------------------------------------------------------
struct TickState {
  void *actor = nullptr;
  float posX = 0.0f;
  float posZ = 0.0f;
  float baseVx = 0.0f;
  float baseVz = 0.0f;
  bool applied = false;
};

thread_local TickState g_tickState;
int g_lastHopTick = -100;

void applySpeed(void *actor) {
  g_tickState.applied = false;
  if (!actor) return;

  void *localPlayer = g_localPlayer.load(std::memory_order_relaxed);
  if (localPlayer && actor != localPlayer) {
    uint32_t count =
        g_mismatchLogCounter.fetch_add(1, std::memory_order_relaxed);
    if (count == 0) {
      __android_log_print(ANDROID_LOG_WARN, kTag,
                          "tick actor %p does not match local player %p",
                          actor, localPlayer);
    }
  }

  const bool master = g_enabled.load(std::memory_order_relaxed);
  if (!master) return;

  auto *base = reinterpret_cast<char *>(actor);
  auto stateVector = *reinterpret_cast<uintptr_t *>(
      base + kActorStateVectorComponent);
  if (stateVector < 0x1000 || (stateVector & 0x7) != 0) return;

  auto *position = reinterpret_cast<float *>(stateVector + kStatePosition);
  auto *previous =
      reinterpret_cast<float *>(stateVector + kStatePreviousPosition);
  auto *velocity = reinterpret_cast<float *>(stateVector + kStateVelocity);

  constexpr float kWorldLimit = 3.0e7f;
  if (!std::isfinite(position[0]) || !std::isfinite(position[1]) ||
      !std::isfinite(position[2]) || !std::isfinite(previous[0]) ||
      !std::isfinite(previous[1]) || !std::isfinite(previous[2]) ||
      std::fabs(position[0]) > kWorldLimit ||
      std::fabs(position[1]) > kWorldLimit ||
      std::fabs(position[2]) > kWorldLimit) {
    return;
  }

  if (horizontalLength(position[0] - previous[0],
                       position[2] - previous[2]) > 10.0f) {
    return;
  }

  float vx = velocity[0];
  float vz = velocity[2];
  float vy = velocity[1];
  if (!std::isfinite(vx) || !std::isfinite(vz) || !std::isfinite(vy)) return;

  const float dx = position[0] - previous[0];
  const float dz = position[2] - previous[2];
  const float dy = position[1] - previous[1];
  const float hSpeed = horizontalLength(vx, vz);
  const float moveDist = horizontalLength(dx, dz);
  const bool moving = moveDist > 0.003f || hSpeed > 0.005f;
  // Ground: prefer the ECS OnGroundFlagComponent; the vertical-delta heuristic
  // is only a fallback because at rest Bedrock keeps vy around -0.078 after
  // the gravity step is applied following the collision resolution.
  static thread_local int groundStreak = 0;
  bool onGround = gameecs::hasOnGround(actor);
  if (onGround) {
    groundStreak = 3;
  } else {
    const bool groundedSample =
        std::fabs(dy) < 0.0015f && vy <= 0.001f && vy > -0.5f;
    groundStreak = groundedSample ? groundStreak + 1 : 0;
    // Two consecutive samples reject the single apex tick of a jump.
    onGround = groundStreak >= 2;
  }

  g_onGround.store(onGround, std::memory_order_relaxed);
  g_moving.store(moving, std::memory_order_relaxed);

  const int tick = static_cast<int>(
      g_tickCounter.fetch_add(1, std::memory_order_relaxed) + 1);

  gameecs::MoveInputState moveInput;
  const bool haveMoveInput = gameecs::readMoveInput(actor, moveInput);
  const bool sneak =
      haveMoveInput ? moveInput.sneaking
                    : g_sneaking.load(std::memory_order_relaxed);
  const bool sneakBlocked =
      g_pauseIfSneaking.load(std::memory_order_relaxed) && sneak;
  const bool speedOn = g_speedEnabled.load(std::memory_order_relaxed);
  const bool hopOn = g_hopEnabled.load(std::memory_order_relaxed);
  const float hopHeight = g_hopHeight.load(std::memory_order_relaxed);
  const bool fakeMode = hopHeight < PHYSICAL_HOP_MIN;
  const bool maskY = g_maskY.load(std::memory_order_relaxed);

  // Native sprint request: the packet bit alone does not engage the local
  // sprint state, so mirror it into MoveInputComponent.
  if (g_autoSprint.load(std::memory_order_relaxed) && !sneakBlocked &&
      !combatstate::sprintSuppressed()) {
    if (haveMoveInput && !moveInput.sprinting) {
      gameecs::setMoveSprinting(actor, true);
    }
  }

  // Manual jump in fake mode: replace the client jump impulse with the same
  // low ballistic velocity the auto-hop uses, then let the phantom mask it.
  if (hopOn && fakeMode && !sneakBlocked && haveMoveInput &&
      moveInput.jumping) {
    const float vTarget = std::sqrt(2.0f * V_GRAVITY * hopHeight);
    velocity[1] = vTarget;
    if (maskY && !g_phantomBusy.load(std::memory_order_relaxed)) {
      requestPhantom(hopHeight);
    }
    g_lastHopTick = tick;
    if (g_debug.load(std::memory_order_relaxed)) {
      __android_log_print(ANDROID_LOG_INFO, kTag,
                          "user jump intercepted h=%.13f v=%.3f",
                          static_cast<double>(hopHeight),
                          static_cast<double>(vTarget));
    }
  }

  // Low hop / physical hop.
  if (hopOn && onGround && vy <= 0.02f && !sneakBlocked &&
      tick - g_lastHopTick >= g_hopInterval.load(std::memory_order_relaxed)) {
    if (fakeMode) {
      g_lastHopTick = tick;
      if (maskY && !g_phantomBusy.load(std::memory_order_relaxed)) {
        requestPhantom(hopHeight);
      }
      if (g_debug.load(std::memory_order_relaxed)) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "fake hop h=%.13f",
                            static_cast<double>(hopHeight));
      }
    } else {
      const float vTarget = std::sqrt(2.0f * V_GRAVITY * hopHeight);
      // Jump through the same motion path the client uses: set vertical
      // velocity to the ballistic value for this hop.
      velocity[1] = vTarget;
      g_lastHopTick = tick;
      g_pendingJump.store(true, std::memory_order_release);
      if (maskY) requestPhantom(hopHeight);
      if (g_debug.load(std::memory_order_relaxed)) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "physical hop h=%.3f v=%.3f",
                            static_cast<double>(hopHeight),
                            static_cast<double>(vTarget));
      }
    }
    vy = velocity[1];
  }

  if (!speedOn) return;
  if (g_pauseIfNotMoving.load(std::memory_order_relaxed) && !moving) return;
  if (g_pauseOnFall.load(std::memory_order_relaxed) && !onGround && vy < -0.05f)
    return;
  if (sneakBlocked) return;

  const bool stuckAtWall =
      (moving && onGround && moveDist <= 0.003f) ||
      (moving &&
       g_lastRealDist.load(std::memory_order_relaxed) < STUCK_THRESHOLD);
  if (stuckAtWall) return;

  const int mode = g_speedMode.load(std::memory_order_relaxed);
  const bool should =
      (mode == 0 && !onGround) || (mode == 1 && onGround) || mode == 2;
  if (!should || !moving) return;

  const float currentVx = velocity[0];
  const float currentVz = velocity[2];
  const float currentSpeed = horizontalLength(currentVx, currentVz);
  const float maxSpeed = g_maxSpeed.load(std::memory_order_relaxed);
  const float boost = g_motionBoost.load(std::memory_order_relaxed);

  // Safe air profile must not carry >vanilla horizontal speed onto the
  // ground: otherwise the server sees a capped packet and keeps a permanent
  // 0.06 gap after every landing.
  if (mode == 0 && onGround && currentSpeed > VANILLA_CAP &&
      currentSpeed <= maxSpeed && currentSpeed > 1e-5f) {
    const float clampScale = VANILLA_CAP / currentSpeed;
    velocity[0] *= clampScale;
    velocity[2] *= clampScale;
    return;
  }

  if (currentSpeed < 0.005f || currentSpeed >= maxSpeed) return;

  const float nx = currentVx / currentSpeed;
  const float nz = currentVz / currentSpeed;
  const float add = std::min(boost, maxSpeed - currentSpeed);

  g_tickState.actor = actor;
  g_tickState.posX = position[0];
  g_tickState.posZ = position[2];
  g_tickState.baseVx = currentVx;
  g_tickState.baseVz = currentVz;
  g_tickState.applied = true;

  velocity[0] = currentVx + nx * add;
  velocity[2] = currentVz + nz * add;

  if (g_debug.load(std::memory_order_relaxed)) {
    uint32_t count = g_tickLogCounter.load(std::memory_order_relaxed);
    if ((count % 20) == 0) {
      __android_log_print(
          ANDROID_LOG_INFO, kTag,
          "boost h=%.3f -> %.3f (add %.3f) ground=%d",
          static_cast<double>(currentSpeed),
          static_cast<double>(horizontalLength(velocity[0], velocity[2])),
          static_cast<double>(add), onGround ? 1 : 0);
    }
  }
}

void applyPositionFallback(void *actor) {
  if (!g_tickState.applied || g_tickState.actor != actor) return;

  auto *base = reinterpret_cast<char *>(actor);
  auto stateVector = *reinterpret_cast<uintptr_t *>(
      base + kActorStateVectorComponent);
  if (stateVector < 0x1000 || (stateVector & 0x7) != 0) return;

  auto *position = reinterpret_cast<float *>(stateVector + kStatePosition);
  if (!std::isfinite(position[0]) || !std::isfinite(position[2])) return;

  const float actual = horizontalLength(position[0] - g_tickState.posX,
                                        position[2] - g_tickState.posZ);
  const float baseMovement =
      horizontalLength(g_tickState.baseVx, g_tickState.baseVz);
  if (baseMovement < 1.0e-5f || actual < 1.0e-5f ||
      actual >= baseMovement * 1.15f) {
    return;
  }

  const float maxSpeed = g_maxSpeed.load(std::memory_order_relaxed);
  const float currentSpeed =
      horizontalLength(g_tickState.baseVx, g_tickState.baseVz);
  if (currentSpeed < 0.005f || currentSpeed >= maxSpeed) return;
  const float add =
      std::min(g_motionBoost.load(std::memory_order_relaxed),
               maxSpeed - currentSpeed);
  const float nx = g_tickState.baseVx / currentSpeed;
  const float nz = g_tickState.baseVz / currentSpeed;
  float extraX = nx * add;
  float extraZ = nz * add;
  const float extraLength = horizontalLength(extraX, extraZ);
  if (extraLength > kMaxExtraBlocksPerTick) {
    const float scale = kMaxExtraBlocksPerTick / extraLength;
    extraX *= scale;
    extraZ *= scale;
  }
  position[0] += extraX;
  position[2] += extraZ;
}

bool isMinecraftAddress(uintptr_t address) {
  Dl_info info{};
  if (dladdr(reinterpret_cast<void *>(address), &info) == 0) return true;
  return info.dli_fname == nullptr ||
         std::strstr(info.dli_fname, kMinecraftLibrary) != nullptr;
}

void baseTickDetour(void *actor) {
  void *localPlayer = g_localPlayer.load(std::memory_order_relaxed);
  const bool isLocal = localPlayer && actor == localPlayer;
  if (isLocal) applySpeed(actor);
  if (g_baseTickOriginal) g_baseTickOriginal(actor);
  if (isLocal) applyPositionFallback(actor);
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

  g_baseTickAddress = baseTick;
  g_baseTickHookInstalled = true;
  __android_log_print(ANDROID_LOG_INFO, kTag,
                      "Actor::baseTick hooked at %p (vtable[25])",
                      reinterpret_cast<void *>(baseTick));
  return true;
}

void normalTickDetour(void *actor) {
  if (actor) {
    void *clientLocal = g_clientLocalPlayer.load(std::memory_order_relaxed);
    if (!clientLocal || actor == clientLocal) {
      void *previous =
          g_localPlayer.exchange(actor, std::memory_order_acq_rel);
      if (!previous) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "local player from normalTick=%p", actor);
      } else if (previous != actor) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
                            "local player changed %p -> %p", previous, actor);
      }
    }
    if (!g_clientLocalPlayer.load(std::memory_order_relaxed) &&
        !g_baseTickHookInstalled && !g_baseTickHookAttempted) {
      installBaseTickHook(actor);
    }
  }

  if (!g_baseTickHookInstalled) applySpeed(actor);
  if (g_normalTickOriginal) g_normalTickOriginal(actor);
  if (!g_baseTickHookInstalled) applyPositionFallback(actor);
}

void *clientInstanceUpdateDetour(void *clientInstance, bool value) {
  void *result = g_clientInstanceUpdateOriginal
                     ? g_clientInstanceUpdateOriginal(clientInstance, value)
                     : nullptr;

  if (clientInstance && g_getLocalPlayer) {
    void *localPlayer = g_getLocalPlayer(clientInstance);
    g_clientLocalPlayer.store(localPlayer, std::memory_order_release);
    if (localPlayer) {
      void *previous =
          g_localPlayer.exchange(localPlayer, std::memory_order_acq_rel);
      if (localPlayer != previous) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "local player=%p",
                            localPlayer);
        resetAllState();
      }
      installBaseTickHook(localPlayer);
    } else {
      void *previous =
          g_localPlayer.exchange(nullptr, std::memory_order_acq_rel);
      if (previous) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "local player cleared on disconnect");
        resetAllState();
      }
    }
  } else {
    g_clientLocalPlayer.store(nullptr, std::memory_order_release);
    if (g_localPlayer.exchange(nullptr, std::memory_order_acq_rel)) {
      resetAllState();
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// Inbound CorrectPlayerMovePrediction cancellation (cancelCorrections).
//
// The userscript cancels the correction packet while the desync is active.
// Bedrock routes the packet through the dispatcher vtable; locate that vtable
// through RTTI instead of a version-specific address, then hook its handle
// thunk.  The thunk is only called for CorrectPlayerMovePrediction.
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
    if (size <= ranges[i].end - address) {
      return true;
    }
  }
  return false;
}

bool inWritable(const LibSegments &lib, uintptr_t address, size_t size) {
  return rangeContains(lib.writable, lib.writableCount, address, size);
}

bool inReadable(const LibSegments &lib, uintptr_t address, size_t size) {
  return rangeContains(lib.readable, lib.readableCount, address, size);
}

bool readableInMinecraft(uintptr_t address, size_t size) {
  LibSegments lib;
  dl_iterate_phdr(collectLibSegments, &lib);
  return inReadable(lib, address, size);
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
      const uintptr_t slot =
          *reinterpret_cast<uintptr_t *>(candidateSlot);
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
      g_cancelCorrections.load(std::memory_order_relaxed) &&
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
  g_predictionHandleOriginal =
      reinterpret_cast<PredictionHandleFn>(*slot);
  *slot = reinterpret_cast<void *>(predictionHandleDetour);
  __builtin___clear_cache(reinterpret_cast<char *>(slotAddress),
                          reinterpret_cast<char *>(slotAddress +
                                                   sizeof(void *)));
  mprotect(reinterpret_cast<void *>(page), span, PROT_READ);

  g_predictionHookInstalled = true;
  __android_log_print(ANDROID_LOG_INFO, kTag,
                      "CorrectPlayerMovePrediction vtable patched at %p "
                      "(handler %p)",
                      reinterpret_cast<void *>(slotAddress),
                      reinterpret_cast<void *>(handle));
  return true;
}

bool installPacketHook(uintptr_t gameBase) {
  if (g_packetHookInstalled) return true;
  if (gameBase == 0) return false;

  const uintptr_t address = gameBase + kPlayerAuthInputWriteOffset;
  static const unsigned char kPrologue[16] = {
      0xfd, 0x7b, 0xbc, 0xa9, 0xf7, 0x0b, 0x00, 0xf9,
      0xf6, 0x57, 0x02, 0xa9, 0xf4, 0x4f, 0x03, 0xa9};
  if (!readableInMinecraft(address, sizeof(kPrologue))) {
    logWarning("PlayerAuthInput::write address is not readable");
    return false;
  }
  if (std::memcmp(reinterpret_cast<const void *>(address), kPrologue,
                  sizeof(kPrologue)) != 0) {
    __android_log_print(
        ANDROID_LOG_WARN, kTag,
        "PlayerAuthInput::write prologue mismatch at %p (unsupported version)",
        reinterpret_cast<void *>(address));
    return false;
  }

  if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(address),
                       reinterpret_cast<pl::memory::FuncPtr>(
                           playerAuthInputWriteDetour),
                       reinterpret_cast<pl::memory::FuncPtr *>(
                           &g_playerAuthWriteOriginal)) != 0) {
    __android_log_print(ANDROID_LOG_WARN, kTag,
                        "Unable to hook PlayerAuthInput::write at %p",
                        reinterpret_cast<void *>(address));
    return false;
  }

  g_packetHookInstalled = true;
  __android_log_print(ANDROID_LOG_INFO, kTag,
                      "PlayerAuthInput::write hooked at %p",
                      reinterpret_cast<void *>(address));
  return true;
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

  __android_log_print(
      ANDROID_LOG_INFO, kTag,
      "1.26.60 signatures: normalTick=%p clientUpdate=%p getLocalPlayer=%p",
      reinterpret_cast<void *>(normalTickAddress),
      reinterpret_cast<void *>(clientUpdateAddress),
      reinterpret_cast<void *>(localPlayerAddress));

  if (!g_getLocalPlayer) {
    g_getLocalPlayer = reinterpret_cast<GetLocalPlayerFn>(localPlayerAddress);
  }

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
    if (pl::memory::hook(reinterpret_cast<pl::memory::FuncPtr>(
                             normalTickAddress),
                         reinterpret_cast<pl::memory::FuncPtr>(normalTickDetour),
                         reinterpret_cast<pl::memory::FuncPtr *>(
                             &g_normalTickOriginal)) != 0) {
      logWarning("Unable to hook LocalPlayer::normalTick");
      return false;
    }
    g_tickHookInstalled = true;
  }

  Dl_info info{};
  uintptr_t gameBase = 0;
  if (dladdr(reinterpret_cast<void *>(normalTickAddress), &info) != 0) {
    gameBase = reinterpret_cast<uintptr_t>(info.dli_fbase);
  }
  if (!installPacketHook(gameBase)) {
    logWarning("Packet bypass disabled (PlayerAuthInput hook not installed)");
  }
  if (!installPredictionHook()) {
    logWarning("Correction cancellation disabled");
  }

  g_installed = true;
  logInfo("Speed module initialized");
  logState();
  return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_nativeInit(
    JNIEnv *, jclass) {
  return initialize() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_nativeSetEnabled(
    JNIEnv *, jclass, jboolean enabled) {
  const bool value = enabled == JNI_TRUE;
  g_enabled.store(value, std::memory_order_relaxed);
  if (!value) {
    resetAllState();
    g_lastHopTick = -100;
  }
  __android_log_print(ANDROID_LOG_INFO, kTag, "enabled=%d", value ? 1 : 0);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_nativeIsEnabled(
    JNIEnv *, jclass) {
  return g_enabled.load(std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_nativeSetMultiplier(
    JNIEnv *, jclass, jfloat multiplier) {
  // Legacy API from the old multiplier-only module.  Keep it as a hint but
  // the new module is configured by the explicit setters below.
  if (multiplier < 1.0f) multiplier = 1.0f;
  if (multiplier > 5.0f) multiplier = 5.0f;
  g_legacyMultiplier.store(multiplier, std::memory_order_relaxed);
}

#define SPEED_BOOL_SETTER(name, field)                                        \
  extern "C" JNIEXPORT void JNICALL                                           \
      Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_##name(   \
          JNIEnv *, jclass, jboolean value) {                                 \
    field.store(value == JNI_TRUE, std::memory_order_relaxed);                \
  }

#define SPEED_INT_SETTER(name, field)                                         \
  extern "C" JNIEXPORT void JNICALL                                           \
      Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_##name(   \
          JNIEnv *, jclass, jint value) {                                     \
    field.store(static_cast<int>(value), std::memory_order_relaxed);          \
  }

#define SPEED_FLOAT_SETTER(name, field)                                       \
  extern "C" JNIEXPORT void JNICALL                                           \
      Java_org_levimc_launcher_core_mods_inbuilt_nativemod_SpeedMod_##name(   \
          JNIEnv *, jclass, jfloat value) {                                   \
    field.store(static_cast<float>(value), std::memory_order_relaxed);        \
  }

SPEED_BOOL_SETTER(nativeSetSpeedEnabled, g_speedEnabled)
SPEED_INT_SETTER(nativeSetSpeedMode, g_speedMode)
SPEED_FLOAT_SETTER(nativeSetMotionBoost, g_motionBoost)
SPEED_FLOAT_SETTER(nativeSetMaxSpeed, g_maxSpeed)
SPEED_FLOAT_SETTER(nativeSetGapMax, g_gapMax)
SPEED_INT_SETTER(nativeSetResetInterval, g_resetIntervalMs)
SPEED_INT_SETTER(nativeSetMaxResetTicks, g_maxResetTicks)
SPEED_BOOL_SETTER(nativeSetHopEnabled, g_hopEnabled)
SPEED_FLOAT_SETTER(nativeSetHopHeight, g_hopHeight)
SPEED_INT_SETTER(nativeSetHopInterval, g_hopInterval)
SPEED_BOOL_SETTER(nativeSetMaskY, g_maskY)
SPEED_INT_SETTER(nativeSetPhantomHoldTicks, g_phantomHoldTicks)
SPEED_INT_SETTER(nativeSetFakeJumpTicks, g_fakeJumpTicks)
SPEED_BOOL_SETTER(nativeSetCancelCorrections, g_cancelCorrections)
SPEED_BOOL_SETTER(nativeSetPauseOnFall, g_pauseOnFall)
SPEED_BOOL_SETTER(nativeSetPauseIfSneaking, g_pauseIfSneaking)
SPEED_BOOL_SETTER(nativeSetPauseIfNotMoving, g_pauseIfNotMoving)
SPEED_BOOL_SETTER(nativeSetAutoSprint, g_autoSprint)
SPEED_BOOL_SETTER(nativeSetDebug, g_debug)
