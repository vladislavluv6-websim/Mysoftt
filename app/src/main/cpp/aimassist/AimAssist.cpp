#include "AimAssist.hpp"

#include <android/log.h>

#include "AntiBot.hpp"
#include "DiagLog.hpp"
#include "Esp.hpp"

#include <algorithm>
#include <cfloat>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <random>

#include "GameEcs.hpp"
#include "Offsets.hpp"
#include "CombatState.hpp"

namespace aimbot {
namespace {

constexpr const char *kTag = "AimAssist";
constexpr float kPi = 3.14159265358979323846f;
constexpr float kRadToDeg = 180.0f / kPi;
constexpr float kDegToRad = kPi / 180.0f;
constexpr float kMaxDt = 0.05f;

float clampf(float value, float minValue, float maxValue) {
  return std::max(minValue, std::min(maxValue, value));
}

int64_t monotonicMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

float normalizeYaw(float yaw) {
  yaw = std::fmod(yaw + 180.0f, 360.0f);
  if (yaw < 0.0f) yaw += 360.0f;
  return yaw - 180.0f;
}

float shortestDelta(float current, float target) {
  return std::fmod(target - current + 540.0f, 360.0f) - 180.0f;
}

int64_t nowMs() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::steady_clock::now().time_since_epoch())
      .count();
}

// Направление взгляда из углов Bedrock: pitch < 0 — вверх, yaw > 0 — вправо.
Vec3 directionFromAngles(const ViewAngles &view) {
  const float yaw = view.yaw * kDegToRad;
  const float pitch = view.pitch * kDegToRad;
  const float cp = std::cos(pitch);
  Vec3 dir{};
  dir.x = -std::sin(yaw) * cp;
  dir.y = -std::sin(pitch);
  dir.z = std::cos(yaw) * cp;
  return dir;
}

// Пересечение луча с AABB (slab method). Возвращает ближайшее t в [0, maxT].
bool rayIntersectsAabb(const Vec3 &origin, const Vec3 &dir, const Aabb &box,
                       float maxT, float &hitT) {
  float tMin = 0.0f;
  float tMax = maxT;
  const float o[3] = {origin.x, origin.y, origin.z};
  const float d[3] = {dir.x, dir.y, dir.z};
  const float lo[3] = {box.min.x, box.min.y, box.min.z};
  const float hi[3] = {box.max.x, box.max.y, box.max.z};

  for (int axis = 0; axis < 3; ++axis) {
    if (std::fabs(d[axis]) < 1e-6f) {
      if (o[axis] < lo[axis] || o[axis] > hi[axis]) return false;
      continue;
    }
    const float inv = 1.0f / d[axis];
    float t1 = (lo[axis] - o[axis]) * inv;
    float t2 = (hi[axis] - o[axis]) * inv;
    if (t1 > t2) std::swap(t1, t2);
    if (t1 > tMin) tMin = t1;
    if (t2 < tMax) tMax = t2;
    if (tMin > tMax) return false;
  }

  hitT = tMin;
  return tMin <= maxT;
}


} // namespace

AimAssist &AimAssist::instance() {
  static AimAssist module;
  return module;
}

// ============================================================
// ModuleBase
// ============================================================
void AimAssist::onEnable() {
  m_hasLocal = false;
  m_velYaw = 0.0f;
  m_velPitch = 0.0f;
  m_target = {};
  m_targetHistory.clear();
  m_historyActor = nullptr;
  m_stickyLeft = 0;
  m_targetGrace = 0;
  m_frameCounter = 0;
  m_lastStepYaw = 0.0f;
  m_lastStepPitch = 0.0f;
  m_lastDelta = 0.0f;
  m_lastFactor = 0.0f;
  m_deadzoneHit = false;
  m_aimEngageAtMs = 0;
  m_targetLockMs = 0;
  m_lastAimActor = nullptr;
  m_jitterYaw = 0.0f;
  m_jitterPitch = 0.0f;
  m_lastSwitchMs = 0;
  m_triggerDelayForTargetMs = 0;
  m_fatigueScale = 1.0f;
  m_nextFatigueMs = 0;
  m_fatigueUntilMs = 0;
  m_tickAccumYaw = 0.0f;
  m_tickAccumPitch = 0.0f;
  m_lastTickYaw = 0.0f;
  m_lastTickPitch = 0.0f;
  m_prevTickDeltaDeg = 0.0f;
  m_haveTickSample = false;
  m_lastNetTickMs = 0;
  m_serverViewSinceMs = 0;
  m_flickTicksLeft = 0;
  m_haveEyeSample = false;
  m_lastEyeY = 0.0f;
  m_lastEyeMs = 0;
  m_vertVel = 0.0f;
  m_fallingSinceMs = 0;
  resetHumanProfile();
  m_fastAimTurnApplied = false;
  // Per-session reseed: a fixed seed gives an identical noise trace every
  // launch, which is easy for server-side ML to fingerprint.
  m_rng.seed(static_cast<std::minstd_rand::result_type>(
      std::chrono::steady_clock::now().time_since_epoch().count() ^
      reinterpret_cast<uintptr_t>(this)));
  m_onTarget.store(false, std::memory_order_release);
  // Release-публикация после сброса состояния: render-поток увидит
  // согласованные поля, а не полу-инициализированные.
  m_enabled.store(true, std::memory_order_release);
  // Fast aim стартует разворот в тот же кадр, без ожидания тика поиска.
  const Settings bootSettings = getSettings();
  if (bootSettings.fastAim) {
    void *player = m_bridge.getLocalPlayer();
    if (player) findTargetInternal(player, bootSettings);
  }
  aimbot::diagLog(kTag, "module enabled");
}

void AimAssist::onDisable() {
  m_enabled.store(false, std::memory_order_release);
  clearTarget();
  m_velYaw = 0.0f;
  m_velPitch = 0.0f;
  m_onTarget.store(false, std::memory_order_release);
  aimbot::diagLog(kTag, "module disabled");
}

void AimAssist::onTick() { updateNetwork(); }

void AimAssist::onRender(float dt) { updateVisual(dt); }

void AimAssist::onConfigLoad() {
  // Здесь можно вызвать конфиг-систему клиента.
  // В текущем APK настройки приходят через setRange/setStrength/setLegitMode.
  m_hasLocal = false;
  m_velYaw = 0.0f;
  m_velPitch = 0.0f;
  clearTarget();
}

// ============================================================
// Настройки
// ============================================================
Settings AimAssist::getSettings() const {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  return m_settings;
}

void AimAssist::setSettings(const Settings &settings) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings = settings;
}

void AimAssist::setEnabled(bool enabled) {
  {
    std::lock_guard<std::mutex> lock(m_settingsMutex);
    m_settings.enabled = enabled;
  }
  if (enabled) {
    onEnable();
  } else {
    onDisable();
  }
}

bool AimAssist::isEnabled() const {
  return m_enabled.load(std::memory_order_acquire);
}

void AimAssist::setRange(float range) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.range = clampf(range, 3.0f, 20.0f);
}

void AimAssist::setStrength(float strength) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.strength = clampf(strength, 0.0f, 100.0f);
}

void AimAssist::setFov(float fovDeg) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.fovDeg = clampf(fovDeg, 1.0f, 360.0f);
}

void AimAssist::setWallCheck(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.visibleCheck = enabled;
}

void AimAssist::setIgnoreInvisible(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.ignoreInvisible = enabled;
}

void AimAssist::setTargetPlayers(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.targetPlayers = enabled;
}

void AimAssist::setTargetMobs(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.targetMobs = enabled;
}

void AimAssist::setBodyPart(int bodyPart) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  switch (std::max(0, std::min(4, bodyPart))) {
  case 1:
    m_settings.targetBone = TargetBone::Chest;
    break;
  case 2:
    m_settings.targetBone = TargetBone::Legs;
    break;
  case 3:
    m_settings.targetBone = TargetBone::UpperChest;
    break;
  case 4:
    m_settings.targetBone = TargetBone::LowerChest;
    break;
  default:
    m_settings.targetBone = TargetBone::Head;
    break;
  }
}

void AimAssist::setMaxSpeed(float degPerSecond) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.maxDegPerSecond = clampf(degPerSecond, 60.0f, 2000.0f);
}

void AimAssist::setSmoothing(float speed) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.smoothingSpeed = clampf(speed, 1.0f, 60.0f);
}

void AimAssist::setAcceleration(float degPerSecond2) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.maxAccelDegPerSecond2 = clampf(degPerSecond2, 100.0f, 12000.0f);
}

void AimAssist::setDeadzone(float deg) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.deadzoneDeg = clampf(deg, 0.0f, 5.0f);
}

void AimAssist::setFullSpeedRange(float deg) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.fullSpeedRangeDeg = clampf(deg, 1.0f, 90.0f);
}

void AimAssist::setStickyTicks(int ticks) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.stickyTicks = std::max(0, std::min(40, ticks));
}

void AimAssist::setTargetLock(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.targetLock = enabled;
  if (!enabled) {
    m_stickyLeft = 0;
  }
}

void AimAssist::setFastAim(bool enabled) {
  {
    std::lock_guard<std::mutex> lock(m_settingsMutex);
    m_settings.fastAim = enabled;
    if (enabled) {
      m_settings.targetPriority = TargetPriority::Angle;
    }
  }
  if (enabled && m_enabled.load(std::memory_order_acquire)) {
    // Не ждём следующего кадра: захватываем цель сразу.
    void *player = m_bridge.getLocalPlayer();
    if (player) findTargetInternal(player, getSettings());
  }
}

void AimAssist::setFastAimTime(float timeMs) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.fastAimTimeMs = clampf(timeMs, 20.0f, 200.0f);
}

void AimAssist::setFastAimMaxSpeed(float degPerSecond) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.fastAimMaxSpeed = clampf(degPerSecond, 120.0f, 4000.0f);
}

void AimAssist::setTargetGrace(int frames) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.targetGraceFrames = std::max(0, std::min(60, frames));
}

void AimAssist::setPreferClosest(bool preferClosest) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.targetPriority = preferClosest ? TargetPriority::Distance
                                            : TargetPriority::Angle;
}

void AimAssist::setLegitMode(bool legit) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.legit = legit;
  if (legit) {
    m_settings.smoothingSpeed = 60.0f;
    m_settings.maxDegPerSecond = 2200.0f;
    m_settings.maxAccelDegPerSecond2 = 12000.0f;
    m_settings.deadzoneDeg = 0.0f;
    m_settings.fullSpeedRangeDeg = 60.0f;
    m_settings.targetSmoothFrames = 0;
  } else {
    m_settings.smoothingSpeed = 70.0f;
    m_settings.maxDegPerSecond = 2800.0f;
    m_settings.maxAccelDegPerSecond2 = 16000.0f;
    m_settings.deadzoneDeg = 0.0f;
    m_settings.fullSpeedRangeDeg = 70.0f;
    m_settings.targetSmoothFrames = 0;
  }
}

void AimAssist::setTriggerBot(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.triggerBot = enabled;
  m_triggerSinceActor = nullptr;
  m_triggerSinceMs = 0;
  m_triggerDelayForTargetMs = 0;
  m_nextAttackAtMs = 0;
  if (!enabled) {
    m_onTarget.store(false, std::memory_order_release);
  }
  aimbot::diagLog(kTag, "setTriggerBot(%d)",
                      enabled ? 1 : 0);
}

void AimAssist::setTriggerDelay(int delayMs) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.triggerDelayMs = std::max(120, std::min(400, delayMs));
}

void AimAssist::setTriggerInterval(int intervalMs) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.triggerIntervalMs = std::max(150, std::min(2000, intervalMs));
}

void AimAssist::setTriggerRange(float range) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.triggerRange = std::max(2.0f, std::min(5.0f, range));
}

void AimAssist::setTriggerTolerance(float tolerance) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.triggerBoxTolerance = std::max(0.0f, std::min(0.25f, tolerance));
}

void AimAssist::setHumanization(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.humanize = enabled;
  if (!enabled) {
    m_aimEngageAtMs = 0;
    m_lastAimActor = nullptr;
    m_targetLockMs = 0;
    resetHumanProfile();
  }
}

void AimAssist::setReactionDelay(int baseMs, int jitterMs) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.reactionDelayMs =
      static_cast<float>(std::max(0, std::min(600, baseMs)));
  m_settings.reactionJitterMs =
      static_cast<float>(std::max(0, std::min(400, jitterMs)));
}

void AimAssist::setAimTremor(float degrees) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.aimTremorDeg = std::max(0.0f, std::min(0.5f, degrees));
}

void AimAssist::setAntiCheatMode(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.antiCheatMode = enabled;
}

void AimAssist::setAssistCone(float degrees) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.assistConeDeg = clampf(degrees, 10.0f, 360.0f);
}

void AimAssist::setAttackMissChance(float chance) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.attackMissChance = clampf(chance, 0.0f, 0.35f);
}

void AimAssist::setServerViewGuard(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.serverViewGuard = enabled;
}

void AimAssist::setSustainedTickDelta(float degrees) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.sustainedTickDeltaDeg = clampf(degrees, 4.0f, 40.0f);
}

void AimAssist::setOnlyCrit(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.onlyCrit = enabled;
  if (enabled) {
    m_fallingSinceMs = 0;
  }
}

void AimAssist::setAntiBot(bool enabled) {
  {
    std::lock_guard<std::mutex> lock(m_settingsMutex);
    m_settings.antiBot = enabled;
  }
  AntiBot::instance().setEnabled(enabled);
}

void AimAssist::setRandomAimPoint(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.randomAimPoint = enabled;
}

void AimAssist::setNeuralRotation(bool enabled) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.neuralRotation = enabled;
}

void AimAssist::setKillaura(bool enabled) {
  {
    std::lock_guard<std::mutex> lock(m_settingsMutex);
    m_settings.killaura = enabled;
  }
  if (!enabled) {
    m_bridge.setSilentAim(false, 0.0f, 0.0f);
  }
}

void AimAssist::setKillauraConeDeg(float deg) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.killauraConeDeg = clampf(deg, 10.0f, 360.0f);
}

void AimAssist::setKillauraReactionMs(float ms) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_settings.killauraReactionMs = clampf(ms, 0.0f, 600.0f);
}

void AimAssist::setRotationRecording(bool enabled) {
  {
    std::lock_guard<std::mutex> lock(m_settingsMutex);
    if (enabled && !m_rotRecording) {
      m_rotPattern.clear();
      m_rotPlayIndex = 0;
      m_havePrevLocal = false;
    }
    m_rotRecording = enabled;
  }
  if (!enabled) saveRotationPattern();
}

void AimAssist::setRotationPatternPath(const std::string &path) {
  std::lock_guard<std::mutex> lock(m_settingsMutex);
  m_rotPatternPath = path;
  loadRotationPattern();
}

void AimAssist::saveRotationPattern() {
  if (m_rotPatternPath.empty()) return;
  std::ofstream out(m_rotPatternPath, std::ios::binary | std::ios::trunc);
  if (!out) return;
  const uint32_t count = static_cast<uint32_t>(m_rotPattern.size());
  out.write(reinterpret_cast<const char *>(&count), sizeof(count));
  if (count) {
    out.write(reinterpret_cast<const char *>(m_rotPattern.data()),
              static_cast<std::streamsize>(count) * sizeof(float));
  }
}

void AimAssist::loadRotationPattern() {
  if (m_rotPatternPath.empty()) return;
  std::ifstream in(m_rotPatternPath, std::ios::binary);
  if (!in) return;
  uint32_t count = 0;
  in.read(reinterpret_cast<char *>(&count), sizeof(count));
  if (!in || count == 0 || count > 500000) return;
  m_rotPattern.resize(count);
  in.read(reinterpret_cast<char *>(m_rotPattern.data()),
          static_cast<std::streamsize>(count) * sizeof(float));
  if (!in) m_rotPattern.clear();
  m_rotPlayIndex = 0;
}

void AimAssist::setTouchActive(bool) {
  // Ручной look больше не ставит наведение на паузу. View всё равно
  // перечитывается каждый кадр, поэтому цель не теряется.
}

void AimAssist::notifyCameraLook() {
  // Наведение не должно замолкать, пока игрок двигает камеру.
  // Фактический view читается в updateVisual каждый кадр.
}

void AimAssist::setActivationHeld(bool held) {
  m_activationHeld.store(held, std::memory_order_release);
}

bool AimAssist::isOnTarget() const {
  return m_onTarget.load(std::memory_order_acquire);
}

DebugState AimAssist::getDebugState() const {
  DebugState state{};
  const Settings settings = getSettings();
  state.enabled = m_enabled.load(std::memory_order_acquire);
  state.hasTarget = m_target.valid;
  state.onTarget = m_onTarget.load(std::memory_order_acquire);
  state.deadzoneHit = m_deadzoneHit;
  state.freezeNetwork = settings.freezeNetwork;
  state.localPitch = m_localPitch.load(std::memory_order_relaxed);
  state.localYaw = m_localYaw.load(std::memory_order_relaxed);
  state.targetPitch = m_target.pitch;
  state.targetYaw = m_target.yaw;
  state.delta = m_lastDelta;
  state.factor = m_lastFactor;
  state.dt = m_lastDt;
  state.target = m_target.actor;
  state.targetDistance = m_target.distance;
  return state;
}

// ============================================================
// Активность / пауза
// ============================================================
bool AimAssist::isActive(const Settings &settings) const {
  if (!settings.enabled) return false;
  switch (settings.activationMode) {
  case ActivationMode::Always:
    return true;
  case ActivationMode::Key:
  case ActivationMode::Attack:
    return m_activationHeld.load(std::memory_order_acquire);
  }
  return false;
}

// ============================================================
// Основной визуальный апдейт (каждый кадр)
// ============================================================
void AimAssist::updateVisual(float dt) {
  dt = clampf(dt, 0.0f, kMaxDt);
  m_lastDt = dt;

  static uint64_t s_visualCalls = 0;
  const uint64_t visualCall = ++s_visualCalls;

  void *player = m_bridge.getLocalPlayer();
  if (visualCall <= 3 || (visualCall % 600) == 0) {
    aimbot::diagLog(kTag, "updateVisual#%llu client=%p player=%p",
                    static_cast<unsigned long long>(visualCall),
                    m_bridge.getClientInstance(), player);
  }
  if (!player) {
    m_onTarget.store(false, std::memory_order_release);
    m_triggerActor.store(nullptr, std::memory_order_release);
    static uint64_t s_noPlayer = 0;
    if ((++s_noPlayer % 240) == 1) {
      aimbot::diagLog(kTag,
                          "updateVisual: no local player (client=%p)",
                          m_bridge.getClientInstance());
    }
    return;
  }

  ViewAngles current{};
  if (!m_bridge.getViewAngles(player, current)) {
    m_onTarget.store(false, std::memory_order_release);
    m_triggerActor.store(nullptr, std::memory_order_release);
    static uint64_t s_noAngles = 0;
    if ((++s_noAngles % 240) == 1) {
      aimbot::diagLog(kTag, "updateVisual: getViewAngles failed (player=%p)",
                      player);
    }
    return;
  }

  Vec3 eyeNow{};
  const bool haveEye = m_bridge.getEyePosition(player, eyeNow);
  if (haveEye) {
    m_eye = eyeNow;
    // Вертикальная скорость локального игрока — нужна для only crit.
    // Считаем по низу хитбокса (ноги), а не по глазам: приседание меняет
    // высоту глаз и давало ложное «падение».
    Aabb localBounds{};
    if (m_bridge.getActorBounds(player, localBounds)) {
      updateVerticalMotion(localBounds.min.y, nowMs());
    }
  }

  const Settings settings = getSettings();

  // ActorRotationComponent — фактический view камеры. Синхронизируем local
  // каждый кадр, чтобы ручное движение и ходьба не оставляли устаревший угол.
  m_localPitch.store(current.pitch, std::memory_order_relaxed);
  m_localYaw.store(current.yaw, std::memory_order_relaxed);
  m_hasLocal = true;


  // ---------------------------------------------------------------
  // Триггер: работает всегда, даже когда aim assist выключен.
  // Решение — пересечение луча прицела с хитбоксом игрока/моба,
  // а не угол до цели, выбранной aim assist.
  // ---------------------------------------------------------------
  const float actorRange =
      std::max(settings.range, settings.triggerRange + 0.5f);
  if (settings.triggerBot || m_enabled.load(std::memory_order_acquire)) {
    // Aim assist needs a fresh list every frame for responsive acquisition.
    m_bridge.fillNearbyActors(player, actorRange, m_actors);
  }
  if (settings.antiBot) {
    AntiBot::instance().update(m_actors, player);
  }
  if (settings.killaura) {
    updateKillaura(player, settings, dt);
    return;
  }
  if (settings.triggerBot && haveEye) {
    updateTrigger(player, settings, current, eyeNow);
  } else {
    m_onTarget.store(false, std::memory_order_release);
    m_triggerActor.store(nullptr, std::memory_order_release);
    m_triggerDistance.store(0.0f, std::memory_order_relaxed);
  }

  // Диагностика: раз в ~2 секунды (120 кадров) пишем состояние триггера.
  // Помогает понять, где именно обрывается цепочка на устройстве.
  static uint64_t s_diagFrame = 0;
  if ((++s_diagFrame % 120) == 0) {
    aimbot::diagLog(kTag,
        "diag player=%p view=(%.1f,%.1f) eye=(%.2f,%.2f,%.2f) actors=%zu "
        "trigger=%d range=%.2f target=%p onTarget=%d gm=%p",
        player, static_cast<double>(current.pitch),
        static_cast<double>(current.yaw), static_cast<double>(eyeNow.x),
        static_cast<double>(eyeNow.y), static_cast<double>(eyeNow.z),
        m_actors.size(), settings.triggerBot ? 1 : 0,
        static_cast<double>(settings.triggerRange),
        m_triggerActor.load(std::memory_order_acquire),
        m_onTarget.load(std::memory_order_acquire) ? 1 : 0,
        m_bridge.getGameMode());
  }

  if (!m_enabled.load(std::memory_order_acquire)) {
    return;
  }

  if (!isActive(settings)) {
    clearTarget();
    return;
  }

  // Поиск цели каждый кадр: иначе цель, прошедшая рядом/сбоку, теряется
  // на несколько кадров. Список актёров переиспользует capacity.
  m_frameCounter++;
  findTargetInternal(player, settings, true);

  if (!m_target.valid) {
    // Цели нет: не двигаем камеру вообще. Скорость гасим плавно,
    // чтобы при появлении цели не было накопленного импульса.
    m_velYaw *= 0.85f;
    m_velPitch *= 0.85f;
    m_lastAimActor = nullptr;
    m_aimEngageAtMs = 0;
    m_targetLockMs = 0;
    resetHumanProfile();
    return;
  }

  // Высота цели задаётся режимом: голова / грудь / ноги.
  m_target.point = getTargetPoint(m_target.actor, settings.targetBone);
  const Angles latest = calculateAnglesToPoint(m_target.point);
  m_target.yaw = latest.yaw;
  m_target.pitch = latest.pitch;
  const float dxTarget = m_target.point.x - m_eye.x;
  const float dyTarget = m_target.point.y - m_eye.y;
  const float dzTarget = m_target.point.z - m_eye.z;
  m_target.distance = std::sqrt(dxTarget * dxTarget + dyTarget * dyTarget +
                                dzTarget * dzTarget);
  if (m_target.distance > settings.range + 1.5f) {
    clearTarget();
    return;
  }

  const int64_t aimNow = nowMs();

  // Target ESP живёт на валидной цели AimAssist, а не только на ударе:
  // иначе без TriggerBot модуль всегда пустой.
  Esp::instance().markTargetEspHit(m_target.actor);

  // Fast aim: без человеческого профиля и EMA позиции. Чистая экспонента
  // плюс лимит скорости и бюджет на серверный тик.
  if (settings.fastAim) {
    if (m_lastAimActor != m_target.actor) {
      m_lastAimActor = m_target.actor;
      m_aimEngageAtMs = aimNow + randomInt(45, 165);
      m_fastTimeScale = randomFloat(0.82f, 1.22f);
      m_fastSpeedScale = randomFloat(0.82f, 1.12f);
      m_fastStealthScale = randomFloat(0.65f, 1.35f);
      m_nextFastPauseMs = aimNow + randomInt(700, 1700);
      m_fastPauseUntilMs = 0;
      resetHumanProfile();
    }
    if (aimNow < m_aimEngageAtMs) {
      m_fastAimTurnApplied = false;
      return;
    }
    if (aimNow >= m_nextFastPauseMs) {
      m_nextFastPauseMs = aimNow + randomInt(900, 2100);
      if (randomFloat(0.0f, 1.0f) < 0.18f) {
        m_fastPauseUntilMs = aimNow + randomInt(35, 110);
      }
    }
    if (aimNow < m_fastPauseUntilMs) {
      m_fastAimTurnApplied = false;
      return;
    }
    m_targetLockMs = aimNow;
    m_fastAimTurnApplied = false;
    stabilizeFastAimAngles(dt);
    updateFastAimStealth(settings, dt);
    const float beforeYaw = m_localYaw.load(std::memory_order_relaxed);
    const float beforePitch = m_localPitch.load(std::memory_order_relaxed);
    applySmoothRotationInternal(m_target.yaw, m_target.pitch, dt, settings);
    const float afterYaw = m_localYaw.load(std::memory_order_relaxed);
    const float afterPitch = m_localPitch.load(std::memory_order_relaxed);
    m_lastStepYaw = shortestDelta(beforeYaw, afterYaw);
    m_lastStepPitch = afterPitch - beforePitch;
    m_lastDelta = std::sqrt(m_lastStepYaw * m_lastStepYaw +
                            m_lastStepPitch * m_lastStepPitch);
    if (settings.debugOverlay) {
      aimbot::diagLog(kTag,
          "fast aim target=%p step=(p %.3f y %.3f) dt=%.4f",
          m_target.actor, static_cast<double>(m_lastStepPitch),
          static_cast<double>(m_lastStepYaw),
          static_cast<double>(m_lastDt));
    }
    return;
  }

  // Человеческая реакция: на новую цель камера выходит не мгновенно.
  // Триггер от этого не зависит и продолжает работать во время задержки.
  if (settings.humanize) {
    if (m_lastAimActor != m_target.actor) {
      m_lastAimActor = m_target.actor;
      // Новая цель — новый моторный профиль. Ошибки/смещения прошлой цели
      // не переносятся, иначе переключение выглядит как «телепорт» точки.
      resetHumanProfile();
      int reactionJitter = 0;
      if (settings.reactionJitterMs > 0.0f) {
        reactionJitter =
            randomInt(0, static_cast<int>(settings.reactionJitterMs));
      }
      m_aimEngageAtMs = aimNow +
                        static_cast<int64_t>(
                            std::max(0.0f, settings.reactionDelayMs)) +
                        reactionJitter;
    }
    if (aimNow < m_aimEngageAtMs) {
      // Держим камеру неподвижно, но не копим скорость.
      m_velYaw *= 0.6f;
      m_velPitch *= 0.6f;
      return;
    }
  } else {
    if (m_lastAimActor != m_target.actor) {
      resetHumanProfile();
    }
    m_lastAimActor = m_target.actor;
    m_aimEngageAtMs = 0;
  }

  const Vec3 point = smoothedPoint(m_target, settings);
  Angles aim = calculateAnglesToPoint(point);

  if (m_targetLockMs == 0) {
    m_targetLockMs = aimNow;
  }

  if (settings.humanize) {
    updateHumanProfile(settings, dt, aimNow);
    updateFatigue(settings, aimNow);
  }

  // Окно «флика»: быстрый поворот разрешён только сразу после захвата
  // цели или при большой ошибке. Постоянное сопровождение на 500°/с —
  // сильный маркер ML-моделей.
  {
    const float curYaw = m_localYaw.load(std::memory_order_relaxed);
    const float curPitch = m_localPitch.load(std::memory_order_relaxed);
    const float errYaw = shortestDelta(curYaw, aim.yaw);
    const float errPitch = aim.pitch - curPitch;
    const float err = std::sqrt(errYaw * errYaw + errPitch * errPitch);
    if (m_targetLockMs == aimNow || err > 25.0f) {
      m_flickTicksLeft =
          std::max(m_flickTicksLeft, settings.flickTickBudget);
    }
  }

  const float beforeYaw = m_localYaw.load(std::memory_order_relaxed);
  const float beforePitch = m_localPitch.load(std::memory_order_relaxed);
  applySmoothRotationInternal(aim.yaw, aim.pitch, dt, settings);
  const float afterYaw = m_localYaw.load(std::memory_order_relaxed);
  const float afterPitch = m_localPitch.load(std::memory_order_relaxed);

  const float stepYaw = shortestDelta(beforeYaw, afterYaw);
  const float stepPitch = afterPitch - beforePitch;
  if (std::isfinite(stepYaw) && std::isfinite(stepPitch) &&
      std::fabs(stepYaw) < 45.0f && std::fabs(stepPitch) < 45.0f &&
      (std::fabs(stepYaw) > 0.0001f || std::fabs(stepPitch) > 0.0001f)) {
    // Только applyTurnDelta. Никаких записей rotation-компонента в кадре:
    // именно они давали feedback-петлю и дрожание камеры.
    m_bridge.applyTurnDelta(player, -stepPitch, stepYaw);
  }
  m_lastStepYaw = stepYaw;
  m_lastStepPitch = stepPitch;

  // m_onTarget здесь не трогаем: его владелец — updateTrigger (ray-vs-hitbox).

  if (settings.debugOverlay) {
    const auto now = std::chrono::steady_clock::now();
    if (m_lastDebug.time_since_epoch().count() == 0 ||
        std::chrono::duration_cast<std::chrono::milliseconds>(now - m_lastDebug)
                .count() >= 1000) {
      m_lastDebug = now;
      aimbot::diagLog(kTag,
          "local=(p %.2f y %.2f) target=(p %.2f y %.2f) delta=%.2f "
          "factor=%.3f step=(p %.3f y %.3f) dt=%.4f deadzone=%d target=%p",
          afterPitch, afterYaw, m_target.pitch, m_target.yaw, m_lastDelta,
          m_lastFactor, m_lastStepPitch, m_lastStepYaw, m_lastDt,
          m_deadzoneHit ? 1 : 0, m_target.actor);
    }
  }
}

// ============================================================
// Сетевой тик 20 Гц
// ============================================================
void AimAssist::updateNetwork() {
  const float localYaw = m_localYaw.load(std::memory_order_relaxed);
  const float localPitch = m_localPitch.load(std::memory_order_relaxed);
  // Это ровно та ротация, которую сервер получит в ближайшем
  // PlayerAuthInput/tick. Храним её отдельно от локальной камеры.
  m_networkYaw.store(localYaw, std::memory_order_relaxed);
  m_networkPitch.store(localPitch, std::memory_order_relaxed);

  const Settings settings = getSettings();
  const int64_t tickNow = monotonicMs();
  if (!m_haveTickSample) {
    m_lastTickYaw = localYaw;
    m_lastTickPitch = localPitch;
    m_prevTickDeltaDeg = 0.0f;
    m_haveTickSample = true;
  } else {
    const float tickDeltaYaw = shortestDelta(m_lastTickYaw, localYaw);
    const float tickDeltaPitch = localPitch - m_lastTickPitch;
    const float tickMagnitude =
        std::sqrt(tickDeltaYaw * tickDeltaYaw + tickDeltaPitch * tickDeltaPitch);
    m_lastTickYaw = localYaw;
    m_lastTickPitch = localPitch;
    m_prevTickDeltaDeg = tickMagnitude;

    static uint64_t s_netTicks = 0;
    const uint64_t netTicks = ++s_netTicks;
    if (settings.debugOverlay && (netTicks % 20) == 1) {
      aimbot::diagLog(kTag,
                      "net tick dy=%.2f dp=%.2f |d|=%.2f flick=%d "
                      "age=%lldms",
                      static_cast<double>(tickDeltaYaw),
                      static_cast<double>(tickDeltaPitch),
                      static_cast<double>(tickMagnitude), m_flickTicksLeft,
                      static_cast<long long>(tickNow - m_lastNetTickMs));
    }
  }
  m_lastNetTickMs = tickNow;
  m_tickAccumYaw = 0.0f;
  m_tickAccumPitch = 0.0f;
  if (m_flickTicksLeft > 0) {
    --m_flickTicksLeft;
  }

  if (m_stickyLeft > 0) {
    --m_stickyLeft;
  }

  if (settings.freezeNetwork || !m_enabled.load(std::memory_order_acquire)) {
    return;
  }

  void *player = m_bridge.getLocalPlayer();
  if (!player) return;

  // Body yaw — только 20 Гц и только если явно включено.
  // В 1.26.x applyTurnDelta обычно уже обновляет rotation-компонент.
  if (settings.syncBodyYaw) {
    m_bridge.setBodyYaw(player, localYaw);
  }

  // В 1.26.x локальные углы сами попадают в PlayerAuthInput.
  // Если нужен явный MovePlayerPacket — подключи sender через
  // ClientBridge::setMovePacketSender().
  m_bridge.sendMovePacket(player, localYaw, localPitch);
}

// ============================================================
// Поиск цели
// ============================================================
void AimAssist::findTarget() {
  void *player = m_bridge.getLocalPlayer();
  if (!player) return;
  const Settings settings = getSettings();
  findTargetInternal(player, settings);
}

void AimAssist::findTargetInternal(void *player, const Settings &settings,
                                   bool actorsAlreadyFilled) {
  if (!player) return;

  Vec3 eye{};
  if (!m_bridge.getEyePosition(player, eye)) {
    clearTarget();
    return;
  }
  m_eye = eye;

  if (!actorsAlreadyFilled) {
    m_bridge.fillNearbyActors(player, settings.range, m_actors);
    if (settings.antiBot) {
      AntiBot::instance().update(m_actors, player);
    }
  }

  Target bestAngle{};
  Target bestDistance{};
  bestAngle.angle = FLT_MAX;
  bestDistance.distance = FLT_MAX;

  // Для выбора цели берём фактический view, а не local: если applyTurnDelta
  // и компонент камеры разошлись, старая логика «не видела» игрока прямо
  // под прицелом и теряла цель при проходе сбоку/сзади.
  ViewAngles view{};
  const bool hasView = m_bridge.getViewAngles(player, view);
  const float localYaw =
      hasView ? view.yaw : m_localYaw.load(std::memory_order_relaxed);
  const float localPitch =
      hasView ? view.pitch : m_localPitch.load(std::memory_order_relaxed);

  // Жёсткий конус захвата даже при FOV=360. Развороты на 90-180° —
  // самый явный маркер aimbot/aimassist для серверных эвристик.
  // Fast aim с anti-cheat профилем не разворачивается за спину: снапы
  // на 90-180° — первый маркер rotation-check'ов. Без профиля — 360°.
  const float assistFov =
      settings.killaura
          ? std::min(settings.fovDeg, settings.killauraConeDeg)
          : (settings.antiCheatMode
                 ? std::min(settings.fovDeg, settings.assistConeDeg)
                 : settings.fovDeg);

  Target current = m_target;
  bool currentStillValid = false;

  // Актёр, в чей хитбокс сейчас упирается прицел (посчитан updateTrigger).
  // Его aim assist обязан вести первым: иначе доводка утащит камеру с
  // хитбокса и триггер перестанет бить.
  void *hitActor = (settings.triggerBot && !settings.fastAim)
                       ? m_triggerActor.load(std::memory_order_acquire)
                       : nullptr;
  Target triggerTarget{};

  for (void *actor : m_actors) {
    if (!actor || actor == player) continue;
    const bool playerActor = m_bridge.isPlayer(actor);
    if (playerActor ? !settings.targetPlayers : !settings.targetMobs) continue;
    if (settings.ignoreInvisible && isInvisible(actor)) continue;
    if (settings.teamCheck && isSameTeam(player, actor)) continue;
    if (playerActor && Esp::instance().isFriend(actor)) continue;
    if (playerActor && settings.antiBot &&
        AntiBot::instance().isBot(actor)) {
      continue;
    }

    Vec3 targetPosition{};
    if (!m_bridge.getPosition(actor, targetPosition)) continue;

    const Vec3 point = getTargetPoint(actor, settings.targetBone);
    const Angles angles = calculateAnglesToPoint(point);

    const float deltaYaw = shortestDelta(localYaw, angles.yaw);
    const float deltaPitch = angles.pitch - localPitch;
    const float angular =
        std::sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
    const float dx = point.x - eye.x;
    const float dy = point.y - eye.y;
    const float dz = point.z - eye.z;
    const float distance = std::sqrt(dx * dx + dy * dy + dz * dz);

    if (distance > settings.range) continue;
    if (assistFov < 359.5f && angular > assistFov) continue;

    if (settings.visibleCheck &&
        !m_bridge.hasLineOfSight(player, eye, point)) {
      continue;
    }

    Target candidate{};
    candidate.actor = actor;
    candidate.point = point;
    candidate.distance = distance;
    candidate.angle = angular;
    candidate.yaw = angles.yaw;
    candidate.pitch = angles.pitch;
    candidate.valid = true;

    if (hitActor && actor == hitActor) {
      triggerTarget = candidate;
    }

    if (actor == current.actor) {
      current = candidate;
      currentStillValid = true;
    }

    if (candidate.angle < bestAngle.angle) {
      bestAngle = candidate;
    }
    if (candidate.distance < bestDistance.distance) {
      bestDistance = candidate;
    }
  }

  Target chosen = settings.targetPriority == TargetPriority::Angle
                      ? bestAngle
                      : bestDistance;

  // Приоритет цели триггера: не даём aim assist увести прицел с хитбокса.
  const bool triggerOverride = triggerTarget.valid;
  if (triggerOverride) {
    chosen = triggerTarget;
  }

  // Target lock: удерживаем выбранную цель, пока она валидна. Быстрое
  // переключение остаётся только когда прицел стоит точно на другой цели.
  if (settings.targetLock && currentStillValid && !triggerOverride) {
    chosen = current;
    if (bestAngle.valid && bestAngle.actor != current.actor &&
        bestAngle.angle <= 1.2f) {
      chosen = bestAngle;
    }
  }

  bool better = triggerOverride;
  bool directHit = false;
  if (!triggerOverride && currentStillValid && chosen.valid) {
    directHit = chosen.actor != current.actor && chosen.angle <= 1.5f;
    if (settings.targetPriority == TargetPriority::Angle) {
      better = directHit ||
               chosen.angle + settings.switchHysteresisDeg < current.angle;
    } else {
      better = directHit ||
               (chosen.distance + 1.0f < current.distance &&
                chosen.angle <= current.angle + 10.0f);
    }
    // Минимальное время удержания цели. Без него assist перескакивает
    // между двумя близкими целями каждый кадр (multi-aura паттерн).
    const int64_t switchNow = nowMs();
    const int switchDelay = m_currentSwitchDelayMs > 0
                                ? m_currentSwitchDelayMs
                                : settings.switchDelayMs;
    if (!directHit && switchDelay > 0 &&
        m_lastSwitchMs != 0 &&
        switchNow - m_lastSwitchMs < switchDelay) {
      better = false;
    }
  }

  // Sticky target: пока тики не вышли, держим текущую цель, но игрок,
  // оказавшийся прямо под прицелом, перехватывает цель сразу.
  if (currentStillValid && m_stickyLeft > 0 && !better) {
    m_target = current;
    m_target.valid = true;
    m_targetGrace = settings.targetGraceFrames;
    return;
  }

  if (currentStillValid && !better) {
    m_target = current;
    m_target.valid = true;
    m_targetGrace = settings.targetGraceFrames;
    return;
  }

  if (chosen.valid) {
    const bool switched = chosen.actor != m_target.actor;
    m_target = chosen;
    if (switched) {
      // Не переносим скорость с прошлой цели: иначе рывок на переключении.
      m_velYaw = 0.0f;
      m_velPitch = 0.0f;
      m_targetHistory.clear();
      m_historyActor = chosen.actor;
      m_aimEngageAtMs = 0;
      m_lastAimActor = nullptr;
      m_lastSwitchMs = nowMs();
      m_currentSwitchDelayMs =
          std::max(35, settings.switchDelayMs + randomInt(-30, 60));
      m_aimPointActor = nullptr;
      m_nextAimPointMs = 0;
      m_serverViewSinceMs = 0;
      resetHumanProfile();
    }
    m_stickyLeft = std::max(0, settings.stickyTicks);
    m_target.valid = true;
    m_targetGrace = settings.targetGraceFrames;
  } else if (currentStillValid) {
    m_target = current;
    m_target.valid = true;
    m_targetGrace = settings.targetGraceFrames;
  } else if (m_target.valid && m_targetGrace > 0) {
    // Актёр мог выпасть из nearby-списка на кадр при проходе рядом/сзади.
    // Держим последнюю цель ещё несколько кадров, пока позиция читается.
    Vec3 stillThere{};
    if (m_bridge.getPosition(m_target.actor, stillThere)) {
      --m_targetGrace;
      m_target.valid = true;
    } else {
      clearTarget();
    }
  } else {
    clearTarget();
  }
}

// ============================================================
// Триггер: луч прицела против хитбоксов
// ============================================================
void AimAssist::updateKillaura(void *player, const Settings &settings,
                              float dt) {
  if (!player) return;
  const int64_t now = nowMs();
  Vec3 eye{};
  if (!m_bridge.getEyePosition(player, eye)) return;
  m_eye = eye;
  const float reach = settings.range + 0.6f;
  void *best = nullptr;
  float bestDist = 1e9f;
  for (void *actor : m_actors) {
    if (!actor || actor == player) continue;
    const bool isPlayerActor = m_bridge.isPlayer(actor);
    if (isPlayerActor ? !settings.targetPlayers : !settings.targetMobs) continue;
    if (isPlayerActor && Esp::instance().isFriend(actor)) continue;
    if (isPlayerActor && settings.antiBot &&
        AntiBot::instance().isBot(actor)) {
      continue;
    }
    if (settings.ignoreInvisible && isInvisible(actor)) continue;
    Vec3 pos{};
    if (!m_bridge.getPosition(actor, pos)) continue;
    const float dx = pos.x - eye.x;
    const float dy = pos.y - eye.y;
    const float dz = pos.z - eye.z;
    const float distance = std::sqrt(dx * dx + dy * dy + dz * dz);
    if (distance > reach) continue;
    if (settings.visibleCheck &&
        !m_bridge.hasLineOfSight(player, eye, pos)) {
      continue;
    }
    if (distance < bestDist) {
      bestDist = distance;
      best = actor;
    }
  }

  if (!best) {
    m_bridge.setSilentAim(false, 0.0f, 0.0f);
    m_triggerActor.store(nullptr, std::memory_order_release);
    m_onTarget.store(false, std::memory_order_release);
    m_triggerSinceActor = nullptr;
    m_triggerSinceMs = 0;
    m_lastAimActor = nullptr;
    return;
  }

  m_triggerActor.store(best, std::memory_order_release);
  m_onTarget.store(true, std::memory_order_release);

  const Vec3 point = getTargetPoint(best, settings.targetBone);
  const Angles aim = calculateAnglesToPoint(point);
  // SILENT aura: only the outgoing PlayerAuthInput rotation is set to the
  // target; the local camera never moves. The attack is then aligned on the
  // server side, so it registers with no visible aiming.
  m_bridge.setSilentAim(true, aim.pitch, aim.yaw);

  if (m_triggerSinceActor != best) {
    m_triggerSinceActor = best;
    m_triggerSinceMs = now;
    m_triggerDelayForTargetMs =
        static_cast<int>(settings.killauraReactionMs) + randomInt(0, 60);
  }
  if (now - m_triggerSinceMs < m_triggerDelayForTargetMs) return;
  if (m_nextAttackAtMs != 0 && now < m_nextAttackAtMs) return;

  if (m_bridge.attackActor(best, point)) {
    const int interval =
        std::max(180, settings.triggerIntervalMs) + randomInt(-40, 70);
    m_nextAttackAtMs = now + interval;
  }
}

void AimAssist::updateTrigger(void *player, const Settings &settings,
                              const ViewAngles &view, const Vec3 &eye) {
  if (!player || !settings.triggerBot) {
    m_triggerActor.store(nullptr, std::memory_order_release);
    m_triggerDistance.store(0.0f, std::memory_order_relaxed);
    m_onTarget.store(false, std::memory_order_release);
    return;
  }

  const Vec3 dir = directionFromAngles(view);
  const float reach = clampf(settings.triggerRange, 2.0f, 5.0f);
  const float tolerance = clampf(settings.triggerBoxTolerance, 0.0f, 0.25f);

  void *bestActor = nullptr;
  float bestDistance = reach + tolerance;

  int tested = 0;
  int skippedBounds = 0;
  float minAngle = 1e9f;
  float minAngleDist = 0.0f;
  Vec3 minAngleCenter{};
  void *minAngleActor = nullptr;

  void *lockedActor = nullptr;
  if (settings.targetLock && m_target.valid) lockedActor = m_target.actor;
  if (lockedActor && settings.antiBot &&
      AntiBot::instance().isBot(lockedActor)) {
    lockedActor = nullptr;
  }

  for (void *actor : m_actors) {
    if (!actor || actor == player) continue;
    if (lockedActor && actor != lockedActor) continue;

    const bool playerActor = m_bridge.isPlayer(actor);
    if (playerActor ? !settings.targetPlayers : !settings.targetMobs) continue;
    if (settings.ignoreInvisible && isInvisible(actor)) continue;
    if (settings.teamCheck && isSameTeam(player, actor)) continue;
    if (playerActor && Esp::instance().isFriend(actor)) continue;
    if (playerActor && settings.antiBot &&
        AntiBot::instance().isBot(actor)) {
      continue;
    }

    Aabb bounds{};
    if (!m_bridge.getActorBounds(actor, bounds)) {
      ++skippedBounds;
      continue;
    }

    {
      const Vec3 center{(bounds.min.x + bounds.max.x) * 0.5f,
                        (bounds.min.y + bounds.max.y) * 0.5f,
                        (bounds.min.z + bounds.max.z) * 0.5f};
      const float dx = center.x - eye.x;
      const float dy = center.y - eye.y;
      const float dz = center.z - eye.z;
      const float len = std::sqrt(dx * dx + dy * dy + dz * dz);
      if (len > 1e-4f) {
        const float dot =
            clampf((dir.x * dx + dir.y * dy + dir.z * dz) / len, -1.0f, 1.0f);
        const float angle = std::acos(dot) * kRadToDeg;
        if (angle < minAngle) {
          minAngle = angle;
          minAngleDist = len;
          minAngleCenter = center;
          minAngleActor = actor;
        }
      }
    }
    ++tested;

    // Допуск по горизонтали: позиция актёра интерполируется, и хитбокс
    // может отставать от фактического положения на кадр-два.
    bounds.min.x -= tolerance;
    bounds.min.z -= tolerance;
    bounds.max.x += tolerance;
    bounds.max.z += tolerance;
    bounds.min.y -= tolerance * 0.5f;
    bounds.max.y += tolerance * 0.5f;

    float hit = 0.0f;
    if (!rayIntersectsAabb(eye, dir, bounds, bestDistance, hit)) continue;
    if (hit >= bestDistance) continue;

    bestDistance = hit;
    bestActor = actor;
  }

  // Если луч прошёл рядом с хитбоксом (интерполяция позиции между
  // тиками), допускаем удар по очень маленькому угловому допуску. Это
  // компенсирует рассинхрон клиент/сервер, но не превращается в killaura:
  // допуск жёстко ограничен attackMaxAngleDeg (2°) и ванильной дистанцией.
  bool fallbackHit = false;
  if (!bestActor && minAngleActor &&
      minAngle <= std::max(0.5f, settings.attackMaxAngleDeg) &&
      minAngleDist <= reach + tolerance) {
    bestActor = minAngleActor;
    bestDistance = minAngleDist;
    fallbackHit = true;
  }

  static uint64_t s_triggerLog = 0;
  if ((++s_triggerLog % 60) == 1) {
    aimbot::diagLog(
        kTag,
        "trigger actors=%zu tested=%d noBounds=%d dir=(%.2f,%.2f,%.2f) "
        "eye=(%.2f,%.2f,%.2f) minAng=%.1f minDist=%.2f center=(%.2f,%.2f,%.2f) "
        "minActor=%p best=%p bestDist=%.2f reach=%.2f p=%d m=%d",
        m_actors.size(), tested, skippedBounds, static_cast<double>(dir.x),
        static_cast<double>(dir.y), static_cast<double>(dir.z),
        static_cast<double>(eye.x), static_cast<double>(eye.y),
        static_cast<double>(eye.z), static_cast<double>(minAngle),
        static_cast<double>(minAngleDist), static_cast<double>(minAngleCenter.x),
        static_cast<double>(minAngleCenter.y),
        static_cast<double>(minAngleCenter.z), minAngleActor, bestActor,
        static_cast<double>(bestDistance), static_cast<double>(reach),
        settings.targetPlayers ? 1 : 0, settings.targetMobs ? 1 : 0);
  }

  m_triggerActor.store(bestActor, std::memory_order_release);
  m_triggerDistance.store(bestActor ? bestDistance : 0.0f,
                          std::memory_order_relaxed);
  m_onTarget.store(bestActor != nullptr, std::memory_order_release);

  if (!bestActor) {
    m_triggerSinceActor = nullptr;
    m_triggerSinceMs = 0;
    return;
  }

  // Атака выполняется прямо здесь, на игровом потоке: тот же путь, что у
  // внутренних читов (Killaura/Aura) — GameMode::attack, без инъекции
  // касаний и без участия UI-потока.
  const int64_t now = monotonicMs();
  if (settings.onlyCrit) {
    // Keep the server-side sprint state clear for the whole fall, not just
    // on the attack frame: the attack packet is processed before the next
    // auth input, so the previous ticks must already be non-sprinting.
    if (isFallingForCrit(settings, now)) {
      combatstate::suppressSprint(150);
      if (player) {
        gameecs::setMoveSprinting(player, false);
        gameecs::setSprintingFlag(player, false);
      }
    }
  }

  if (m_triggerSinceActor != bestActor) {
    m_triggerSinceActor = bestActor;
    m_triggerSinceMs = now;
    // Реакция на новую цель — со случайным разбросом, а не константа.
    m_triggerDelayForTargetMs = settings.triggerDelayMs;
    const int jitter = std::max(0, settings.triggerDelayJitterMs);
    if (jitter > 0) {
      m_triggerDelayForTargetMs += randomInt(0, jitter);
    }
    const float reactionRoll = randomFloat(0.0f, 1.0f);
    if (reactionRoll < 0.08f) {
      m_triggerDelayForTargetMs += randomInt(20, 70);
    }
    if (settings.killaura) {
      m_triggerDelayForTargetMs =
          std::max(m_triggerDelayForTargetMs,
                   static_cast<int>(settings.killauraReactionMs));
    }
    m_triggerDelayForTargetMs =
        std::max(0, std::min(650, m_triggerDelayForTargetMs));
  }
  // В режиме only crit окно падения короткое: не тратим его на полную
  // «человеческую» задержку и случайные пропуски.
  const int64_t effectiveDelay =
      settings.onlyCrit ? std::min(m_triggerDelayForTargetMs, 40)
                        : static_cast<int64_t>(m_triggerDelayForTargetMs);
  if (now - m_triggerSinceMs < effectiveDelay) return;
  if (m_nextAttackAtMs != 0 && now < m_nextAttackAtMs) return;

  // Не бьём в кадре быстрого довода. Polar/ML смотрят не только на угол
  // прицела, но и на угловую скорость в момент удара: удар на 20-30°/тик
  // выглядит как killaura даже при идеальном попадании.
  if (settings.antiCheatMode) {
    const float attackSpeedLimit =
        std::max(14.0f, settings.sustainedTickDeltaDeg);
    if (m_prevTickDeltaDeg > attackSpeedLimit) {
      m_nextAttackAtMs = now + 45;
      return;
    }
  }

  // Не бьём сразу после смены цели и иногда «промахиваемся» (не жмём).
  if (settings.humanize && !settings.onlyCrit) {
    if (m_lastSwitchMs != 0 && now - m_lastSwitchMs < 120) return;
    if (settings.attackMissChance > 0.0f &&
        randomFloat(0.0f, 1.0f) < settings.attackMissChance) {
      m_nextAttackAtMs = now + randomInt(80, 220);
      return;
    }
  }

  Vec3 hitPoint{};
  if (fallbackHit) {
    hitPoint = minAngleCenter;
    bestDistance = minAngleDist;
  } else {
    hitPoint.x = eye.x + dir.x * bestDistance;
    hitPoint.y = eye.y + dir.y * bestDistance;
    hitPoint.z = eye.z + dir.z * bestDistance;
  }
  if (settings.randomAimPoint) {
    hitPoint = randomHitPoint(bestActor, hitPoint);
  }

  // Главная сетевая проверка: бьём только если ротация, которую сервер
  // уже получил на последнем тике 20 Гц, тоже смотрит на точку удара.
  // Иначе Java-античит (Polar/GhostAC) сравнивает удар со старой
  // ротацией и видит «удар смотря в сторону».
  if (settings.serverViewGuard && m_haveTickSample) {
    const int64_t netAgeMs = now - m_lastNetTickMs;
    if (netAgeMs >= 0 && netAgeMs <= 150) {
      ViewAngles netView{};
      netView.pitch = m_networkPitch.load(std::memory_order_relaxed);
      netView.yaw = m_networkYaw.load(std::memory_order_relaxed);
      const Vec3 netDir = directionFromAngles(netView);
      const float hx = hitPoint.x - eye.x;
      const float hy = hitPoint.y - eye.y;
      const float hz = hitPoint.z - eye.z;
      const float hlen = std::sqrt(hx * hx + hy * hy + hz * hz);
      float netAngle = 180.0f;
      if (hlen > 1e-4f) {
        const float dot = clampf(
            (netDir.x * hx + netDir.y * hy + netDir.z * hz) / hlen, -1.0f,
            1.0f);
        netAngle = std::acos(dot) * kRadToDeg;
      }
      float guardTolerance = settings.serverViewToleranceDeg;
      if (settings.onlyCrit) {
        guardTolerance = std::max(guardTolerance, 18.0f);
      } else if (settings.antiCheatMode && settings.fastAim) {
        // В Fast aim серверная ротация должна почти совпадать с ударом.
        guardTolerance = std::min(guardTolerance, 4.0f);
      }
      if (netAngle > guardTolerance) {
        m_serverViewSinceMs = 0;
        m_nextAttackAtMs = now + 40;
        static uint64_t s_guardLog = 0;
        if ((++s_guardLog % 20) == 1) {
          aimbot::diagLog(
              kTag,
              "trigger guard: server view off by %.2f deg (tol %.1f), "
              "age=%lldms",
              static_cast<double>(netAngle),
              static_cast<double>(guardTolerance),
              static_cast<long long>(netAgeMs));
        }
        return;
      }
      // Сервер должен видеть прицел на цели минимум один тик. Крит-окно
      // короткое, поэтому там ждём только фактическое совпадение ротации.
      if (!settings.onlyCrit) {
        if (m_serverViewSinceMs == 0) {
          m_serverViewSinceMs = now;
        }
        if (now - m_serverViewSinceMs < 30) {
          m_nextAttackAtMs = now + 15;
          return;
        }
      }
    }
  }

  // Only crit: удар только в падении после прыжка. На земле/в подъёме
  // атака не отправляется, поэтому каждый удар идёт критом.
  if (settings.onlyCrit && !isFallingForCrit(settings, now)) {
    return;
  }

  {
    static uint64_t s_fireLog = 0;
    const uint64_t n = ++s_fireLog;
    if (n <= 5 || (n % 20) == 0) {
      aimbot::diagLog(kTag, "trigger fire #%llu actor=%p dist=%.2f gm=%p",
                      static_cast<unsigned long long>(n), bestActor,
                      static_cast<double>(bestDistance),
                      m_bridge.getGameMode());
    }
  }
  // TriggerBot always resets sprint on the attack tick, even when the player
  // is running. Critical hits need a non-sprinting server state, and keeping
  // the reset unconditional avoids a sprinting hit packet after a chase.
  combatstate::suppressSprint(120);
  if (player) {
    gameecs::setMoveSprinting(player, false);
    gameecs::setSprintingFlag(player, false);
  }

  if (m_bridge.attackActor(bestActor, hitPoint)) {
    // Target ESP появляется только на факте удара TriggerBot.
    Esp::instance().markTargetEspHit(bestActor);
    // Анимация взмаха применяется только к удару TriggerBot.
    m_bridge.armSwing();
    // Интервал между ударами: база + разброс + редкая «задумчивость».
    int64_t next = now + settings.triggerIntervalMs;
    if (settings.attackIntervalJitterMs > 0) {
      next += randomInt(-settings.attackIntervalJitterMs,
                        settings.attackIntervalJitterMs);
    }
    if (settings.humanize && randomFloat(0.0f, 1.0f) < 0.08f) {
      next += randomInt(120, 320);
    } else if (randomFloat(0.0f, 1.0f) < 0.10f) {
      next -= randomInt(30, 90);
    }
    m_nextAttackAtMs =
        std::max(now + (settings.onlyCrit ? 80 : 120), next);
  } else {
    // GameMode ещё не готов (меню/загрузка) — не долбим каждый кадр.
    m_nextAttackAtMs = now + 100;
  }
}

Angles AimAssist::calculateAngles(void *entity) const {
  Angles angles{};
  void *player = m_bridge.getLocalPlayer();
  if (!player || !entity) return angles;

  Vec3 eye{};
  if (!m_bridge.getEyePosition(player, eye)) return angles;

  const Settings settings = getSettings();
  const Vec3 point = getTargetPoint(entity, settings.targetBone);
  const float dx = point.x - eye.x;
  const float dy = point.y - eye.y;
  const float dz = point.z - eye.z;
  const float horizontal = std::max(0.0001f, std::sqrt(dx * dx + dz * dz));

  angles.yaw = normalizeYaw(std::atan2(-dx, dz) * kRadToDeg);
  angles.pitch =
      clampf(-std::atan2(dy, horizontal) * kRadToDeg, -89.9f, 89.9f);
  return angles;
}

Angles AimAssist::calculateAnglesToPoint(const Vec3 &point) const {
  Angles angles{};
  const float dx = point.x - m_eye.x;
  const float dy = point.y - m_eye.y;
  const float dz = point.z - m_eye.z;
  const float horizontal = std::max(0.0001f, std::sqrt(dx * dx + dz * dz));

  angles.yaw = normalizeYaw(std::atan2(-dx, dz) * kRadToDeg);
  angles.pitch =
      clampf(-std::atan2(dy, horizontal) * kRadToDeg, -89.9f, 89.9f);
  return angles;
}

void AimAssist::stabilizeFastAimAngles(float dt) {
  if (!m_haveStableAim || m_stableAimActor != m_target.actor) {
    m_haveStableAim = true;
    m_stableAimActor = m_target.actor;
    m_stableAimYaw = m_target.yaw;
    m_stableAimPitch = m_target.pitch;
    return;
  }

  const float errYaw = shortestDelta(m_stableAimYaw, m_target.yaw);
  const float errPitch = m_target.pitch - m_stableAimPitch;
  const float err = std::sqrt(errYaw * errYaw + errPitch * errPitch);

  if (err > 6.0f) {
    m_stableAimYaw = m_target.yaw;
    m_stableAimPitch = m_target.pitch;
    return;
  }

  // Малые дрожания сглаживаем, заметное движение цели пропускаем без
  // задержки: alpha растёт до 1 уже на 0.35°, поэтому скорость доводки
  // не меняется.
  const float baseAlpha =
      1.0f - std::exp(-clampf(dt, 0.0f, 0.05f) * 55.0f);
  const float motion = clampf(err / 0.35f, 0.0f, 1.0f);
  const float alpha = baseAlpha + (1.0f - baseAlpha) * motion;
  m_stableAimYaw = normalizeYaw(m_stableAimYaw + errYaw * alpha);
  m_stableAimPitch =
      clampf(m_stableAimPitch + errPitch * alpha, -89.9f, 89.9f);
  m_target.yaw = m_stableAimYaw;
  m_target.pitch = m_stableAimPitch;
}

void AimAssist::updateFastAimStealth(const Settings &, float) {
  m_noiseYaw = 0.0f;
  m_noisePitch = 0.0f;
  m_restYaw = 0.0f;
  m_restPitch = 0.0f;
  m_restTargetYaw = 0.0f;
  m_restTargetPitch = 0.0f;
}

// ============================================================
// Плавный довод
// ============================================================
void AimAssist::applySmoothRotation(float targetYaw, float targetPitch,
                                    float dt) {
  const Settings settings = getSettings();
  applySmoothRotationInternal(targetYaw, targetPitch, dt, settings);
}

void AimAssist::applySmoothRotationInternal(float targetYaw, float targetPitch,
                                            float dt,
                                            const Settings &settings) {
  dt = clampf(dt, 0.0f, kMaxDt);
  if (dt <= 0.0f) {
    return;
  }

  // Один шаг на кадр: applyTurnDelta нельзя вызывать несколько раз за кадр,
  // иначе камера получает несколько мелких рывков и дрожит.
  const float h = dt;

  // ------------------------------------------------------------
  // Fast aim.
  //
  // antiCheatMode=false: прежняя чистая экспонента (быстро, но палевно).
  // antiCheatMode=true: быстрый довод внутри человеческого конверта —
  // случайный профиль на каждый бой, разгон с ограничением ускорения,
  // OU-шум/смещение точки, бюджет поворота на серверный тик и пауза
  // реакции. Это не «гарантия обхода» ML: серверные модели ловят саму
  // статистику идеального трекинга, поэтому профиль рандомизирован.
  // ------------------------------------------------------------
  if (settings.fastAim) {
    const float curYaw = m_localYaw.load(std::memory_order_relaxed);
    const float curPitch = m_localPitch.load(std::memory_order_relaxed);
    const float fastDt = clampf(dt, 0.001f, 0.05f);
    const float strengthScale =
        clampf(settings.strength / 100.0f, 0.15f, 1.0f);
    const float timeMs =
        clampf(settings.fastAimTimeMs / strengthScale * m_fastTimeScale,
               12.0f, 200.0f);
    const float alpha = 1.0f - std::exp(-fastDt * 1000.0f / timeMs);

    float errYaw = shortestDelta(curYaw, targetYaw);
    float errPitch = targetPitch - curPitch;
    const float errMag =
        std::sqrt(errYaw * errYaw + errPitch * errPitch);

    const float maxSpeed =
        clampf(settings.fastAimMaxSpeed * strengthScale * m_fastSpeedScale *
                   m_motorScale,
               300.0f, 4000.0f);

    float stepYaw;
    float stepPitch;
    if (settings.legit) {
      // Legit rotation ported from a working killaura: a rate-limited
      // shortest-arc approach whose speed eases with a smoothstep as it nears
      // the target (never a constant rate), plus a catchup term that tracks the
      // target's own angular velocity so a strafing target is not trailed.
      const float t = clampf(errMag / 45.0f, 0.0f, 1.0f);
      const float smooth = t * t * (3.0f - 2.0f * t);
      const float targetRate =
          (std::fabs(shortestDelta(m_lastTargetYaw, targetYaw)) +
           std::fabs(targetPitch - m_lastTargetPitch)) /
          std::max(fastDt, 1e-3f);
      m_catchupRate += (targetRate - m_catchupRate) *
                       (1.0f - std::exp(-fastDt / 0.12f));
      float rate = maxSpeed * (0.35f + 0.65f * smooth) +
                   clampf(m_catchupRate, 0.0f, 900.0f) * 0.5f;
      rate = clampf(rate, 30.0f, 4000.0f);
      const float stepLen = std::min(errMag, rate * fastDt);
      stepYaw = errMag > 1e-6f ? errYaw * (stepLen / errMag) : errYaw;
      stepPitch = errMag > 1e-6f ? errPitch * (stepLen / errMag) : errPitch;
    } else {
      stepYaw = errYaw * alpha;
      stepPitch = errPitch * alpha;
      const float maxStep = maxSpeed * fastDt;
      const float stepMag =
          std::sqrt(stepYaw * stepYaw + stepPitch * stepPitch);
      if (stepMag > maxStep && stepMag > 1e-6f) {
        const float scale = maxStep / stepMag;
        stepYaw *= scale;
        stepPitch *= scale;
      }
    }
    m_lastTargetYaw = targetYaw;
    m_lastTargetPitch = targetPitch;
    if (errMag < 0.01f) {
      stepYaw = errYaw;
      stepPitch = errPitch;
    }

    // Ограничение именно на серверный тик 20 Гц. Обычное сопровождение
    // сюда не упирается, а длинные снапы Polar/ML видят как rotation-check.
    if (settings.antiCheatMode) {
      const float tickCap =
          clampf(settings.maxTickDeltaDeg, 12.0f, 60.0f);
      float ny = m_tickAccumYaw + stepYaw;
      float np = m_tickAccumPitch + stepPitch;
      const float mag = std::sqrt(ny * ny + np * np);
      if (mag > tickCap && mag > 1e-5f) {
        const float k = tickCap / mag;
        ny *= k;
        np *= k;
        stepYaw = ny - m_tickAccumYaw;
        stepPitch = np - m_tickAccumPitch;
      }
      m_tickAccumYaw += stepYaw;
      m_tickAccumPitch += stepPitch;
    }

    m_localYaw.store(normalizeYaw(curYaw + stepYaw),
                     std::memory_order_relaxed);
    m_localPitch.store(clampf(curPitch + stepPitch, -89.9f, 89.9f),
                       std::memory_order_relaxed);
    m_lastDelta = errMag;
    m_lastFactor = 1.0f;
    m_lastDt = fastDt;
    m_deadzoneHit = false;

    void *rawPlayer = m_bridge.getLocalPlayer();
    if (rawPlayer && (std::fabs(stepYaw) > 0.00005f ||
                      std::fabs(stepPitch) > 0.00005f)) {
      m_bridge.applyTurnDelta(rawPlayer, -stepPitch, stepYaw);
      m_fastAimTurnApplied = true;
    }
    return;
  }

  const float strengthScale = clampf(settings.strength / 100.0f, 0.15f, 1.0f);
  const float curYaw = m_localYaw.load(std::memory_order_relaxed);
  const float curPitch = m_localPitch.load(std::memory_order_relaxed);
  float errYaw = shortestDelta(curYaw, targetYaw);
  float errPitch = targetPitch - curPitch;
  const float errMag =
      std::sqrt(errYaw * errYaw + errPitch * errPitch);

  const float timeMs = clampf(
      1800.0f / std::max(1.0f, settings.smoothingSpeed) / strengthScale,
      10.0f, 200.0f);
  const float alpha = 1.0f - std::exp(-h * 1000.0f / timeMs);
  float stepYaw = errYaw * alpha;
  float stepPitch = errPitch * alpha;

  const float maxStep =
      settings.maxDegPerSecond * (0.5f + 0.5f * strengthScale) * m_motorScale * h;
  const float stepMag =
      std::sqrt(stepYaw * stepYaw + stepPitch * stepPitch);
  if (stepMag > maxStep && stepMag > 1e-6f) {
    const float scale = maxStep / stepMag;
    stepYaw *= scale;
    stepPitch *= scale;
  }
  if (errMag < 0.01f) {
    stepYaw = errYaw;
    stepPitch = errPitch;
  }

  m_localYaw.store(normalizeYaw(curYaw + stepYaw),
                   std::memory_order_relaxed);
  m_localPitch.store(clampf(curPitch + stepPitch, -89.9f, 89.9f),
                     std::memory_order_relaxed);
  m_velYaw = stepYaw / std::max(h, 0.001f);
  m_velPitch = stepPitch / std::max(h, 0.001f);
  m_lastDelta = errMag;
  m_lastFactor = clampf(
      std::sqrt(stepYaw * stepYaw + stepPitch * stepPitch) /
          std::max(0.001f, settings.maxDegPerSecond * h),
      0.0f, 1.0f);
  m_deadzoneHit = false;
}

// ============================================================
// Человеческий моторный профиль
// ============================================================
float AimAssist::randomFloat(float lo, float hi) {
  if (hi <= lo) return lo;
  std::uniform_real_distribution<float> dist(lo, hi);
  return dist(m_rng);
}

int AimAssist::randomInt(int lo, int hi) {
  if (hi <= lo) return lo;
  std::uniform_int_distribution<int> dist(lo, hi);
  return dist(m_rng);
}

void AimAssist::resetHumanProfile() {
  m_noiseYaw = 0.0f;
  m_noisePitch = 0.0f;
  m_restYaw = 0.0f;
  m_restPitch = 0.0f;
  m_restTargetYaw = 0.0f;
  m_restTargetPitch = 0.0f;
  m_overYaw = 0.0f;
  m_overPitch = 0.0f;
  m_motorScale = 1.0f;
  m_nextMotorMs = 0;
  m_nextRestMs = 0;
  m_targetLockMs = 0;
}

void AimAssist::updateHumanProfile(const Settings &settings, float, int64_t now) {
  // Only the smooth motor-speed variation stays; all per-frame noise, rest
  // offset and overshoot were removed so the aim never trembles.
  if (now >= m_nextMotorMs) {
    const float var = clampf(settings.motorVariation, 0.0f, 0.45f);
    m_motorScale = randomFloat(1.0f - var, 1.0f + var);
    m_nextMotorMs = now + randomInt(180, 480);
  }
  m_noiseYaw = 0.0f;
  m_noisePitch = 0.0f;
  m_restYaw = 0.0f;
  m_restPitch = 0.0f;
  m_overYaw = 0.0f;
  m_overPitch = 0.0f;
}

void AimAssist::updateFatigue(const Settings &settings, int64_t now) {
  if (!settings.humanize) {
    m_fatigueScale = 1.0f;
    return;
  }
  if (m_nextFatigueMs == 0) {
    m_nextFatigueMs = now + randomInt(2500, 6000);
    m_fatigueUntilMs = 0;
  }
  if (now >= m_nextFatigueMs && now >= m_fatigueUntilMs) {
    m_fatigueScale = randomFloat(0.78f, 0.95f);
    m_fatigueUntilMs = now + randomInt(300, 900);
    m_nextFatigueMs = now + randomInt(2500, 6000);
  } else if (now >= m_fatigueUntilMs) {
    m_fatigueScale += (1.0f - m_fatigueScale) * 0.08f;
    if (m_fatigueScale > 0.999f) m_fatigueScale = 1.0f;
  }
}

// Бюджет поворота на один тик сервера (50 мс). Ограничивает то, что
// реально уйдёт в PlayerAuthInput: устойчивую скорость, прирост скорости
// между тиками и суммарную дельту. Короткий флик разрешён отдельно.
void AimAssist::applyTickBudget(float &stepYaw, float &stepPitch,
                                const Settings &settings, float) {
  const float cap = (m_flickTicksLeft > 0)
                        ? std::max(1.0f, settings.maxTickDeltaDeg)
                        : std::min(std::max(1.0f, settings.maxTickDeltaDeg),
                                   std::max(1.0f, settings.sustainedTickDeltaDeg));
  const float growth = std::max(0.0f, settings.maxTickAccelDeg);
  const float allowed = std::min(cap, std::fabs(m_prevTickDeltaDeg) + growth);

  bool clampedYaw = false;
  bool clampedPitch = false;
  auto limitAxis = [&](float &step, float accum, bool &clamped) {
    const float target = accum + step;
    if (std::fabs(target) > allowed) {
      const float limited = std::copysign(allowed, target);
      const float next = limited - accum;
      // Не разворачиваем камеру бюджетом резко в противоположную сторону,
      // но и не обнуляем шаг (обнуление даёт дрожание на высокой скорости).
      if (next * step < 0.0f) {
        step = next * 0.35f;
      } else {
        step = next;
      }
      clamped = true;
    }
  };
  limitAxis(stepYaw, m_tickAccumYaw, clampedYaw);
  limitAxis(stepPitch, m_tickAccumPitch, clampedPitch);

  // Суммарная дельта по обеим осям тоже не должна превышать бюджет.
  const float ny = m_tickAccumYaw + stepYaw;
  const float np = m_tickAccumPitch + stepPitch;
  const float mag = std::sqrt(ny * ny + np * np);
  if (mag > allowed && mag > 1e-5f) {
    const float k = allowed / mag;
    stepYaw = ny * k - m_tickAccumYaw;
    stepPitch = np * k - m_tickAccumPitch;
    clampedYaw = true;
    clampedPitch = true;
  }

  if (clampedYaw) m_velYaw *= 0.5f;
  if (clampedPitch) m_velPitch *= 0.5f;

  m_tickAccumYaw += stepYaw;
  m_tickAccumPitch += stepPitch;
}

void AimAssist::updateVerticalMotion(float feetY, int64_t now) {
  if (!m_haveEyeSample) {
    m_lastEyeY = feetY;
    m_lastEyeMs = now;
    m_haveEyeSample = true;
    m_vertVel = 0.0f;
    return;
  }
  const float dt = clampf(
      static_cast<float>(now - m_lastEyeMs) / 1000.0f, 0.001f, 0.10f);
  const float instant = (feetY - m_lastEyeY) / dt;
  // Респавн/телепорт/смена измерения — сбрасываем, иначе получим ложное
  // «падение» с огромной скоростью.
  if (std::fabs(instant) > 40.0f) {
    m_vertVel = 0.0f;
    m_fallingSinceMs = 0;
  } else {
    const float alpha = clampf(dt / 0.07f, 0.0f, 1.0f);
    m_vertVel += (instant - m_vertVel) * alpha;
  }
  m_lastEyeY = feetY;
  m_lastEyeMs = now;
}

// Крит в Bedrock засчитывается в падении (не на земле, вертикальная
// скорость отрицательная). Ждём несколько миллисекунд непрерывного
// падения, чтобы не поймать шум на вершине прыжка.
bool AimAssist::isFallingForCrit(const Settings &settings, int64_t now) {
  void *player = m_bridge.getLocalPlayer();
  if (!player) return false;
  // Ported from the PC client's SolsticeCriticalPolicy: a crit needs the player
  // airborne, not rising (jump pressed), and already falling faster than the
  // gate (-0.17). No settle window — the window is the descent itself.
  if (gameecs::hasOnGround(player)) {
    m_fallingSinceMs = 0;
    return false;
  }
  gameecs::MoveInputState input{};
  if (gameecs::readMoveInput(player, input) && input.jumping) {
    m_fallingSinceMs = 0;
    return false;
  }
  const float gate = -std::max(0.10f, settings.critMinFallSpeed);
  if (!(m_vertVel <= gate)) {
    m_fallingSinceMs = 0;
    return false;
  }
  return true;
}

// ============================================================
// Вспомогательное
// ============================================================
Vec3 AimAssist::getTargetPoint(void *actor, TargetBone) const {
  Aabb bounds{};
  if (!m_bridge.getActorBounds(actor, bounds)) {
    Vec3 position{};
    m_bridge.getPosition(actor, position);
    return position;
  }
  const Vec3 center{(bounds.min.x + bounds.max.x) * 0.5f,
                    (bounds.min.y + bounds.max.y) * 0.5f,
                    (bounds.min.z + bounds.max.z) * 0.5f};

  // Current crosshair direction (from the live camera).
  const float pitch = m_localPitch.load(std::memory_order_relaxed);
  const float yaw = m_localYaw.load(std::memory_order_relaxed);
  const float pr = pitch * 0.01745329252f;
  const float yr = yaw * 0.01745329252f;
  const Vec3 dir{-std::sin(yr) * std::cos(pr), -std::sin(pr),
                 std::cos(yr) * std::cos(pr)};

  // Ray vs AABB (slab). If the crosshair already hits the box, return a point
  // on the ray so the aim error is 0 and the camera does not move — this is
  // what removes the tremor while tracking. Otherwise return the fixed box
  // centre so the approach target is stable (no feedback with the live ray).
  float tmin = 0.0f;
  float tmax = 1.0e9f;
  const float o[3] = {m_eye.x, m_eye.y, m_eye.z};
  const float d[3] = {dir.x, dir.y, dir.z};
  const float lo[3] = {bounds.min.x, bounds.min.y, bounds.min.z};
  const float hi[3] = {bounds.max.x, bounds.max.y, bounds.max.z};
  bool hits = true;
  for (int axis = 0; axis < 3; ++axis) {
    if (!std::isfinite(o[axis]) || !std::isfinite(d[axis])) {
      hits = false;
      break;
    }
    if (std::fabs(d[axis]) < 1.0e-7f) {
      if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
        hits = false;
        break;
      }
      continue;
    }
    float t1 = (lo[axis] - o[axis]) / d[axis];
    float t2 = (hi[axis] - o[axis]) / d[axis];
    if (t1 > t2) std::swap(t1, t2);
    tmin = std::max(tmin, t1);
    tmax = std::min(tmax, t2);
    if (tmin > tmax) {
      hits = false;
      break;
    }
  }
  if (hits && tmax >= 0.0f) {
    const float t = std::max(0.0f, tmin);
    return {m_eye.x + dir.x * t, m_eye.y + dir.y * t, m_eye.z + dir.z * t};
  }
  return center;
}

Vec3 AimAssist::jitteredAimPoint(void *actor, const Vec3 &base,
                                 TargetBone bone) {
  const int64_t now = nowMs();
  if (actor != m_aimPointActor || now >= m_nextAimPointMs) {
    m_aimPointActor = actor;
    const bool head = bone == TargetBone::Head;
    const float horizontal = head ? 0.0016f : 0.0026f;
    const float vertical = head ? 0.0012f : 0.0022f;
    m_aimPointOffset.x = randomFloat(-horizontal, horizontal);
    m_aimPointOffset.y = randomFloat(-vertical, vertical);
    m_aimPointOffset.z = randomFloat(-horizontal, horizontal);
    m_nextAimPointMs = now + randomInt(2500, 5000);
  }

  Aabb bounds{};
  if (!m_bridge.getActorBounds(actor, bounds)) return base;

  Vec3 point{base.x + m_aimPointOffset.x,
             base.y + m_aimPointOffset.y,
             base.z + m_aimPointOffset.z};
  const float marginX = std::min(0.08f, (bounds.max.x - bounds.min.x) * 0.2f);
  const float marginY = std::min(0.08f, (bounds.max.y - bounds.min.y) * 0.2f);
  const float marginZ = std::min(0.08f, (bounds.max.z - bounds.min.z) * 0.2f);
  point.x = clampf(point.x, bounds.min.x + marginX, bounds.max.x - marginX);
  point.y = clampf(point.y, bounds.min.y + marginY, bounds.max.y - marginY);
  point.z = clampf(point.z, bounds.min.z + marginZ, bounds.max.z - marginZ);
  return point;
}

Vec3 AimAssist::randomHitPoint(void *actor, const Vec3 &hitPoint) {
  Aabb bounds{};
  if (!m_bridge.getActorBounds(actor, bounds)) return hitPoint;

  const float marginX = std::min(0.05f, (bounds.max.x - bounds.min.x) * 0.15f);
  const float marginY = std::min(0.05f, (bounds.max.y - bounds.min.y) * 0.15f);
  const float marginZ = std::min(0.05f, (bounds.max.z - bounds.min.z) * 0.15f);
  Vec3 point{hitPoint.x + randomFloat(-0.05f, 0.05f),
             hitPoint.y + randomFloat(-0.06f, 0.06f),
             hitPoint.z + randomFloat(-0.05f, 0.05f)};
  point.x = clampf(point.x, bounds.min.x + marginX, bounds.max.x - marginX);
  point.y = clampf(point.y, bounds.min.y + marginY, bounds.max.y - marginY);
  point.z = clampf(point.z, bounds.min.z + marginZ, bounds.max.z - marginZ);
  return point;
}

Vec3 AimAssist::smoothedPoint(const Target &target,
                              const Settings &settings) {
  if (m_historyActor != target.actor) {
    m_historyActor = target.actor;
    m_targetHistory.clear();
  }

  // EMA позиции цели по времени, а не окно из N кадров: стабильно при любом FPS.
  const float lag = clampf(static_cast<float>(settings.targetSmoothFrames) / 120.0f,
                           0.0f, 0.25f);
  const float dt = clampf(m_lastDt, 0.0f, kMaxDt);
  if (m_targetHistory.empty() || dt <= 0.0f) {
    if (m_targetHistory.empty()) {
      m_targetHistory.push_back(target.point);
    } else {
      m_targetHistory.back() = target.point;
    }
  } else if (lag > 0.0001f) {
    const float alpha = 1.0f - std::exp(-dt / lag);
    Vec3 &smoothed = m_targetHistory.back();
    smoothed.x += (target.point.x - smoothed.x) * alpha;
    smoothed.y += (target.point.y - smoothed.y) * alpha;
    smoothed.z += (target.point.z - smoothed.z) * alpha;
  } else {
    m_targetHistory.back() = target.point;
  }
  return m_targetHistory.back();
}

bool AimAssist::isInvisible(void *actor) const {
  return gameecs::isInvisible(actor);
}

bool AimAssist::isSameTeam(void *, void *) const {
  // В этой версии team-оффсет не заполнен. Подставь свой team id.
  return false;
}

void AimAssist::clearTarget() {
  m_target = {};
  m_targetHistory.clear();
  m_historyActor = nullptr;
  m_stickyLeft = 0;
  m_targetGrace = 0;
  m_velYaw = 0.0f;
  m_velPitch = 0.0f;
  m_lastAimActor = nullptr;
  m_aimEngageAtMs = 0;
  m_targetLockMs = 0;
  m_serverViewSinceMs = 0;
  m_haveStableAim = false;
  m_stableAimActor = nullptr;
  m_fastStealthActor = nullptr;
  m_nextFastRestMs = 0;
  m_fastTimeScale = 1.0f;
  m_fastSpeedScale = 1.0f;
  m_fastStealthScale = 1.0f;
  m_nextFastPauseMs = 0;
  m_fastPauseUntilMs = 0;
  m_aimPointActor = nullptr;
  m_aimPointOffset = {};
  m_nextAimPointMs = 0;
  m_currentSwitchDelayMs = 0;
  resetHumanProfile();
  // m_onTarget принадлежит триггеру: очистка цели aim assist не должна
  // гасить триггер посреди кадра.
}

void AimAssist::syncLocalFromView(void *player) {
  ViewAngles current{};
  if (!m_bridge.getViewAngles(player, current)) {
    return;
  }
  m_localPitch.store(current.pitch, std::memory_order_relaxed);
  m_localYaw.store(current.yaw, std::memory_order_relaxed);
  m_hasLocal = true;
}

void AimAssist::renderDebug() {
  // На Android нет общего draw-контекста. Отдай DebugState в Java-оверлей
  // клиента и нарисуй там. Этот метод оставлен точкой входа для твоего draw.
}

} // namespace aimbot
