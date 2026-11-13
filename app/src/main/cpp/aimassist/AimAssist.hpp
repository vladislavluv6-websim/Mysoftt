#pragma once

#include <atomic>
#include <chrono>
#include <cstdint>
#include <deque>
#include <mutex>
#include <random>
#include <vector>

#include "ClientBridge.hpp"
#include "ModuleBase.hpp"

namespace aimbot {

struct Angles {
  float pitch = 0.0f;
  float yaw = 0.0f;
};

enum class ActivationMode : int {
  Always = 0,
  Key = 1,
  Attack = 2,
};

enum class TargetPriority : int {
  Angle = 0,
  Distance = 1,
};

enum class TargetBone : int {
  Head = 0,
  Chest = 1,
  Legs = 2,
  // Верх груди / шея: между грудью и головой. Компромисс, когда грудь
  // слишком «центровая», а голова уводит прицел слишком высоко.
  UpperChest = 3,
  // Чуть ниже груди: между грудью и животом, ниже линии сосков.
  LowerChest = 4,
};

struct Settings {
  bool enabled = false;

  ActivationMode activationMode = ActivationMode::Always;
  int activationKey = 0;

  float fovDeg = 360.0f;
  float range = 8.0f;
  float smoothingSpeed = 60.0f;
  float maxDegPerSecond = 2200.0f;
  float maxAccelDegPerSecond2 = 12000.0f;
  float deadzoneDeg = 0.0f;
  float fullSpeedRangeDeg = 60.0f;

  TargetPriority targetPriority = TargetPriority::Angle;
  TargetBone targetBone = TargetBone::Head;

  int stickyTicks = 0;
  // Target lock: держим одну цель, пока она жива и в радиусе. Переключение
  // только если прицел стоит точно на другой цели.
  bool targetLock = true;
  // Fast aim: довод по экспоненте за fastAimTimeMs, без шума/overshoot/
  // deadzone/бюджета. Всегда ближайшая цель и lock до смерти/выхода из range.
  bool fastAim = true;
  float fastAimTimeMs = 35.0f;
  float fastAimMaxSpeed = 2600.0f;
  float switchHysteresisDeg = 0.75f;
  // EMA-лаг позиции цели (в кадрах при 120 Гц). Даёт «догоняющее» наведение,
  // как у живой руки, вместо мгновенной привязки к центру хитбокса.
  int targetSmoothFrames = 0;
  int targetGraceFrames = 3;

  float randomJitterDeg = 0.0f;
  int randomJitterIntervalMs = 150;

  bool visibleCheck = true;
  bool ignoreInvisible = false;
  bool teamCheck = false;
  bool targetPlayers = true;
  bool targetMobs = true;
  bool debugOverlay = false;
  bool freezeNetwork = false;
  // Синхронизация body yaw на сетевом тике. По умолчанию выключена:
  // applyTurnDelta уже двигает view, а лишняя запись компонента даёт дрожание.
  bool syncBodyYaw = false;

  // Совместимость с текущим UI клиента.
  bool legit = true;
  float strength = 100.0f;
  bool triggerBot = false;
  int triggerDelayMs = 250;
  // Пауза между ударами триггера (мс). Реакция берётся из triggerDelayMs.
  int triggerIntervalMs = 620;

  // Триггер работает по пересечению луча прицела с хитбоксом, а не по
  // углу до цели aim assist. Дальность — до поверхности хитбокса.
  // Ванильный предел атаки в Bedrock — 3.0 блока от глаз.
  float triggerRange = 3.0f;
  // Допуск по хитбоксу: компенсирует интерполяцию позиции между тиками
  // и расхождение клиент/сервер. Больше 0.1 уже похоже на reach-хак.
  float triggerBoxTolerance = 0.04f;

  // ------------------------------------------------------------
  // Humanization / устойчивость к серверным анти-читам
  // (Polar ML, FairPlay, GhostAC/NovaAC, Geyser-прокси и т.п.)
  // ------------------------------------------------------------
  // Реакция перед началом доводки: живой игрок не начинает вести цель
  // в тот же кадр, в котором она появилась.
  bool humanize = true;
  float reactionDelayMs = 45.0f;
  float reactionJitterMs = 90.0f;
  // Дополнительный вклад в шум сопровождения (0 — только trackingNoiseDeg).
  float aimTremorDeg = 0.0f;
  // Верхний предел поворота за один серверный тик (50 мс). Защита от
  // «телепорт-снапов», которые ловят rotation-check'и.
  float maxTickDeltaDeg = 32.0f;

  // ------------------------------------------------------------
  // Anti-cheat профиль (включён по умолчанию)
  // ------------------------------------------------------------
  // Жёсткий конус захвата. Даже если FOV выставлен в 360, assist не
  // разворачивается на цель за спиной: снапы на 90-180° — главный маркер
  // aim-эвристик.
  bool antiCheatMode = true;
  float assistConeDeg = 90.0f;
  // Шум сопровождения (Gauss-Markov / OU): плавно «плавает» вокруг цели,
  // из-за чего траектория не является точной функцией угла до цели.
  float trackingNoiseDeg = 0.09f;
  // Постоянное смещение точки прицеливания от центра хитбокса. Игрок не
  // держит прицел математически точно в центре; это ломает проверки
  // "aim placement"/"target offset" и статистику идеальной точности.
  float restOffsetDeg = 0.15f;
  // Одноразовый перелёт при захвате новой цели (доля начальной ошибки).
  float overshootFactor = 0.07f;
  // Медленная вариация «моторной» скорости (доля единицы).
  float motorVariation = 0.22f;
  // Минимальное время удержания текущей цели перед переключением, мс.
  int switchDelayMs = 100;
  // Триггер: максимальный угол от прицела до центра цели для «мягкого»
  // добивания, если луч не попал в AABB. В legit-режиме не используется.
  float attackMaxAngleDeg = 2.0f;
  // Вероятность «промаха» (пропуска удара) для humanization.
  float attackMissChance = 0.02f;
  // Разброс интервала между ударами, мс (±).
  int attackIntervalJitterMs = 90;
  // Задержка реакции триггера на новую цель (разброс), мс.
  int triggerDelayJitterMs = 55;
  // Бить только когда луч реально пересекает хитбокс (без «добивания»
  // по минимальному углу). Это самая шумная для анти-читов ветка.
  bool legitAttackOnly = true;

  bool antiBot = true;
  bool randomAimPoint = true;
  // Neural/recorded rotation: replay a recorded human approach pattern.
  bool neuralRotation = false;
  // Killaura: 360° auto-attack (ignore the FOV cone, attack the nearest target).
  bool killaura = false;
  float killauraConeDeg = 90.0f;
  float killauraReactionMs = 140.0f;

  // ------------------------------------------------------------
  // Only crit
  // ------------------------------------------------------------
  // Бить только в падении (после прыжка), чтобы каждый удар был критом.
  bool onlyCrit = false;
  // Минимальная скорость падения по Y, блоков/с.
  float critMinFallSpeed = 0.17f;
  // Сколько миллисекунд нужно непрерывно падать перед ударом.
  int critSettleMs = 20;

  // ------------------------------------------------------------
  // Сетевое формирование ротации (то, что реально видит сервер)
  // ------------------------------------------------------------
  // Бить только если ротация, уже отправленная на сервер (последний
  // тик 20 Гц), тоже смотрит на цель. Убирает «удар при взгляде в сторону»
  // из-за рассинхрона кадр/тик — прямая проверка Polar/GhostAC.
  bool serverViewGuard = true;
  float serverViewToleranceDeg = 10.0f;
  // Устойчивая скорость сопровождения: не больше N градусов за тик 50 мс
  // (12°/тик = 240°/с). Быстрее — только в коротком окне флика.
  float sustainedTickDeltaDeg = 12.0f;
  // Максимальный прирост поворота за тик между соседними тиками
  // (сглаживание ускорения, «ступенек» скорости).
  float maxTickAccelDeg = 20.0f;
  // Сколько тиков длится окно быстрого флика после захвата/большой ошибки.
  int flickTickBudget = 3;
};

struct DebugState {
  bool enabled = false;
  bool hasTarget = false;
  bool onTarget = false;
  bool deadzoneHit = false;
  bool freezeNetwork = false;

  float localPitch = 0.0f;
  float localYaw = 0.0f;
  float targetPitch = 0.0f;
  float targetYaw = 0.0f;
  float delta = 0.0f;
  float factor = 0.0f;
  float dt = 0.0f;

  void *target = nullptr;
  float targetDistance = 0.0f;
};

class AimAssist : public ModuleBase {
public:
  static AimAssist &instance();

  // ModuleBase
  void onEnable() override;
  void onDisable() override;
  void onTick() override;
  void onRender(float dt) override;
  void onConfigLoad() override;

  // Публичный API из ТЗ.
  void updateVisual(float dt);
  void updateNetwork();
  void findTarget();
  Angles calculateAngles(void *entity) const;
  void applySmoothRotation(float targetYaw, float targetPitch, float dt);
  void renderDebug();

  // JNI / UI.
  void setEnabled(bool enabled);
  bool isEnabled() const;
  void setRange(float range);
  void setStrength(float strength);
  void setFov(float fovDeg);
  void setWallCheck(bool enabled);
  void setIgnoreInvisible(bool enabled);
  void setTargetPlayers(bool enabled);
  void setTargetMobs(bool enabled);
  void setBodyPart(int bodyPart);
  void setMaxSpeed(float degPerSecond);
  void setSmoothing(float speed);
  void setAcceleration(float degPerSecond2);
  void setDeadzone(float deg);
  void setFullSpeedRange(float deg);
  void setStickyTicks(int ticks);
  void setTargetLock(bool enabled);
  void setFastAim(bool enabled);
  void setFastAimTime(float timeMs);
  void setFastAimMaxSpeed(float degPerSecond);
  void setTargetGrace(int frames);
  void setPreferClosest(bool preferClosest);
  void setLegitMode(bool legit);
  void setTriggerBot(bool enabled);
  void setTriggerDelay(int delayMs);
  void setTriggerInterval(int intervalMs);
  void setTriggerRange(float range);
  void setTriggerTolerance(float tolerance);
  void setHumanization(bool enabled);
  void setReactionDelay(int baseMs, int jitterMs);
  void setAimTremor(float degrees);
  void setAntiCheatMode(bool enabled);
  void setAssistCone(float degrees);
  void setAttackMissChance(float chance);
  void setServerViewGuard(bool enabled);
  void setSustainedTickDelta(float degrees);
  void setOnlyCrit(bool enabled);
  void setAntiBot(bool enabled);
  void setRandomAimPoint(bool enabled);
  void setRotationRecording(bool enabled);
  void setNeuralRotation(bool enabled);
  void setKillaura(bool enabled);
  void setKillauraConeDeg(float deg);
  void setKillauraReactionMs(float ms);
  void setRotationPatternPath(const std::string &path);
  void setTouchActive(bool active);
  void notifyCameraLook();
  void setActivationHeld(bool held);
  bool isOnTarget() const;

  Settings getSettings() const;
  void setSettings(const Settings &settings);
  DebugState getDebugState() const;

private:
  struct Target {
    void *actor = nullptr;
    Vec3 point{};
    float distance = 0.0f;
    float angle = 0.0f;
    float yaw = 0.0f;
    float pitch = 0.0f;
    bool valid = false;
  };

  AimAssist() = default;

  bool isActive(const Settings &settings) const;
  bool isInvisible(void *actor) const;
  bool isSameTeam(void *local, void *other) const;
  Vec3 getTargetPoint(void *actor, TargetBone bone) const;
  Vec3 jitteredAimPoint(void *actor, const Vec3 &base, TargetBone bone);
  Vec3 randomHitPoint(void *actor, const Vec3 &hitPoint);
  Angles calculateAnglesToPoint(const Vec3 &point) const;
  void findTargetInternal(void *player, const Settings &settings,
                          bool actorsAlreadyFilled = false);
  void stabilizeFastAimAngles(float dt);
  void updateFastAimStealth(const Settings &settings, float dt);
  void updateTrigger(void *player, const Settings &settings,
                     const ViewAngles &view, const Vec3 &eye);
  void updateKillaura(void *player, const Settings &settings, float dt);
  void applySmoothRotationInternal(float targetYaw, float targetPitch,
                                   float dt, const Settings &settings);
  void clearTarget();
  void syncLocalFromView(void *player);
  Vec3 smoothedPoint(const Target &target, const Settings &settings);

  // Human motor model: OU-шум сопровождения, «дрожащая» точка покоя,
  // одноразовый перелёт и медленная вариация скорости.
  void resetHumanProfile();
  void updateHumanProfile(const Settings &settings, float dt, int64_t now);
  void updateFatigue(const Settings &settings, int64_t now);
  void updateVerticalMotion(float feetY, int64_t now);
  bool isFallingForCrit(const Settings &settings, int64_t now);
  void applyTickBudget(float &stepYaw, float &stepPitch,
                       const Settings &settings, float dt);
  float randomFloat(float lo, float hi);
  int randomInt(int lo, int hi);

  ClientBridge &m_bridge = ClientBridge::instance();

  mutable std::mutex m_settingsMutex;
  Settings m_settings;

  std::atomic<bool> m_enabled{false};
  std::atomic<bool> m_activationHeld{false};
  std::atomic<float> m_localYaw{0.0f};
  std::atomic<float> m_localPitch{0.0f};
  std::atomic<float> m_networkYaw{0.0f};
  std::atomic<float> m_networkPitch{0.0f};
  std::atomic<bool> m_onTarget{false};

  // Цель под прицелом (ray-vs-hitbox), независимая от aim assist.
  std::atomic<void *> m_triggerActor{nullptr};
  std::atomic<float> m_triggerDistance{0.0f};

  // Таймер атаки триггера. Живёт на игровом потоке (ClientInstance::update).
  int64_t m_triggerSinceMs = 0;
  int64_t m_nextAttackAtMs = 0;
  int m_triggerDelayForTargetMs = 0;
  void *m_triggerSinceActor = nullptr;
  int64_t m_lastSwitchMs = 0;

  bool m_hasLocal = false;

  Target m_target;
  std::vector<void *> m_actors;
  std::deque<Vec3> m_targetHistory;
  void *m_historyActor = nullptr;

  int m_frameCounter = 0;
  int m_stickyLeft = 0;
  int m_targetGrace = 0;
  int64_t m_lastJitterMs = 0;
  // Humanization state.
  int64_t m_aimEngageAtMs = 0;
  int64_t m_targetLockMs = 0;
  void *m_lastAimActor = nullptr;
  float m_jitterYaw = 0.0f;
  float m_jitterPitch = 0.0f;

  // Gauss-Markov шум сопровождения (градусы).
  float m_noiseYaw = 0.0f;
  float m_noisePitch = 0.0f;
  float m_noiseTargetYaw = 0.0f;
  float m_noiseTargetPitch = 0.0f;
  int64_t m_nextNoiseMs = 0;
  // Медленно блуждающая точка покоя относительно центра хитбокса.
  float m_restYaw = 0.0f;
  float m_restPitch = 0.0f;
  float m_restTargetYaw = 0.0f;
  float m_restTargetPitch = 0.0f;
  int64_t m_nextRestMs = 0;
  // Одноразовый перелёт при захвате цели (затухает экспоненциально).
  float m_overYaw = 0.0f;
  float m_overPitch = 0.0f;
  // Медленная вариация моторной скорости.
  float m_motorScale = 1.0f;
  int64_t m_nextMotorMs = 0;
  // Усталость: периодическое кратковременное снижение скорости.
  float m_fatigueScale = 1.0f;
  int64_t m_nextFatigueMs = 0;
  int64_t m_fatigueUntilMs = 0;

  // Вертикальная скорость локального игрока для режима only crit.
  bool m_haveEyeSample = false;
  float m_lastEyeY = 0.0f;
  int64_t m_lastEyeMs = 0;
  float m_vertVel = 0.0f;
  int64_t m_fallingSinceMs = 0;

  // Сетевое формирование: бюджет поворота на тик 20 Гц.
  float m_tickAccumYaw = 0.0f;
  float m_tickAccumPitch = 0.0f;
  float m_lastTickYaw = 0.0f;
  float m_lastTickPitch = 0.0f;
  float m_prevTickDeltaDeg = 0.0f;
  bool m_haveTickSample = false;
  int64_t m_lastNetTickMs = 0;
  int64_t m_serverViewSinceMs = 0;
  int m_flickTicksLeft = 0;

  float m_velYaw = 0.0f;
  float m_velPitch = 0.0f;
  float m_lastStepYaw = 0.0f;
  float m_lastStepPitch = 0.0f;
  float m_lastDelta = 0.0f;
  float m_lastFactor = 0.0f;
  float m_lastDt = 0.0f;
  bool m_deadzoneHit = false;
  // Fast aim сам вызывает applyTurnDelta; внешний onTick не должен
  // применять тот же шаг второй раз.
  bool m_fastAimTurnApplied = false;
  // Fast aim: micro-фильтр углов цели. Гасит дрожание от 20-Гц
  // интерполяции позиции/хитбокса, не меняя скорость доводки на больших
  // ошибках (там значения сбрасываются напрямую).
  bool m_haveStableAim = false;
  void *m_fastStealthActor = nullptr;
  int64_t m_nextFastRestMs = 0;
  void *m_stableAimActor = nullptr;
  float m_stableAimYaw = 0.0f;
  float m_stableAimPitch = 0.0f;
  // Random per-engagement profile for Fast aim (stealth mode): never reuse
  // the same time constant / speed / settle radius, otherwise ML heuristics
  // see a deterministic motor pattern across fights.
  float m_fastTimeScale = 1.0f;
  float m_catchupRate = 0.0f;
  bool m_rotRecording = false;
  bool m_havePrevLocal = false;
  float m_prevLocalYaw = 0.0f;
  float m_prevLocalPitch = 0.0f;
  std::vector<float> m_rotPattern;
  size_t m_rotPlayIndex = 0;
  std::string m_rotPatternPath;
  void saveRotationPattern();
  void loadRotationPattern();
  float m_lastTargetYaw = 0.0f;
  float m_lastTargetPitch = 0.0f;
  float m_fastSpeedScale = 1.0f;
  float m_fastStealthScale = 1.0f;
  int64_t m_nextFastPauseMs = 0;
  int64_t m_fastPauseUntilMs = 0;

  void *m_aimPointActor = nullptr;
  Vec3 m_aimPointOffset{};
  int64_t m_nextAimPointMs = 0;
  int m_currentSwitchDelayMs = 0;

  mutable Vec3 m_eye{};
  std::minstd_rand m_rng{0x5EED1234u};
  std::chrono::steady_clock::time_point m_lastDebug{};
};

} // namespace aimbot
