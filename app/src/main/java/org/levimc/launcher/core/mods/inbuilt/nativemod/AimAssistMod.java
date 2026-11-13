package org.levimc.launcher.core.mods.inbuilt.nativemod;

public final class AimAssistMod {
    private static boolean libraryLoaded;
    private static volatile boolean initialized;
    private static long lastInitAttempt;
    private static final NativeModInit.Task INIT_TASK = new NativeModInit.Task();

    private AimAssistMod() {}

    private static synchronized boolean loadLibrary() {
        if (libraryLoaded) return true;
        try {
            System.loadLibrary("aimassist");
            libraryLoaded = true;
        } catch (UnsatisfiedLinkError ignored) {
            libraryLoaded = false;
        }
        return libraryLoaded;
    }

    public static synchronized boolean initialize() {
        if (initialized) return true;
        long now = System.currentTimeMillis();
        if (now - lastInitAttempt < 2000) return false;
        lastInitAttempt = now;
        if (!loadLibrary()) return false;
        initialized = nativeInit();
        return initialized;
    }

    /**
     * Initializes on a background thread (signature scan takes seconds) and
     * reports the result on the main thread.
     */
    public static void initializeAsync(NativeModInit.Callback callback) {
        if (initialized) {
            if (callback != null) callback.onResult(true);
            return;
        }
        INIT_TASK.request(AimAssistMod::initialize, callback);
    }

    /** Requires a completed {@link #initializeAsync}; never blocks the caller. */
    public static boolean setEnabled(boolean enabled, float range, float strength) {
        if (enabled && !initialized) return false;
        if (!initialized) return !enabled;
        setRange(range);
        setStrength(strength);
        nativeSetEnabled(enabled);
        return true;
    }

    public static void setRange(float range) {
        if (initialized) nativeSetRange(range);
    }

    public static void setStrength(float strength) {
        if (initialized) nativeSetStrength(strength);
    }

    public static void setLegitMode(boolean enabled) {
        if (initialized) nativeSetLegitMode(enabled);
    }

    public static void setBodyPart(int bodyPart) {
        if (initialized) nativeSetBodyPart(bodyPart);
    }

    public static void setMaxSpeed(float degPerSecond) {
        if (initialized) nativeSetMaxSpeed(degPerSecond);
    }

    public static void setSmoothing(float speed) {
        if (initialized) nativeSetSmoothing(speed);
    }

    public static void setAcceleration(float degPerSecond2) {
        if (initialized) nativeSetAcceleration(degPerSecond2);
    }

    public static void setDeadzone(float deg) {
        if (initialized) nativeSetDeadzone(deg);
    }

    public static void setFullSpeedRange(float deg) {
        if (initialized) nativeSetFullSpeedRange(deg);
    }

    public static void setStickyTicks(int ticks) {
        if (initialized) nativeSetStickyTicks(ticks);
    }

    public static void setTargetLock(boolean enabled) {
        if (initialized) nativeSetTargetLock(enabled);
    }

    public static void setTargetGrace(int frames) {
        if (initialized) nativeSetTargetGrace(frames);
    }

    public static void setFastAim(boolean enabled) {
        if (initialized) nativeSetFastAim(enabled);
    }

    public static void setFastAimTime(float timeMs) {
        if (initialized) nativeSetFastAimTime(timeMs);
    }

    public static void setFastAimMaxSpeed(float degPerSecond) {
        if (initialized) nativeSetFastAimMaxSpeed(degPerSecond);
    }

    public static void setSwingStyle(int style) {
        if (initialized) nativeSetSwingStyle(Math.max(0, Math.min(5, style)));
    }

    public static void setSwingSpeed(int percent) {
        if (initialized) nativeSetSwingSpeed(Math.max(50, Math.min(200, percent)));
    }

    public static void setSwingAmplitude(int percent) {
        if (initialized) nativeSetSwingAmplitude(Math.max(0, Math.min(150, percent)));
    }

    public static void setPreferClosest(boolean enabled) {
        if (initialized) nativeSetPreferClosest(enabled);
    }

    public static void setFov(float fovDeg) {
        if (initialized) nativeSetFov(fovDeg);
    }

    public static void setWallCheck(boolean enabled) {
        if (initialized) nativeSetWallCheck(enabled);
    }

    public static void setIgnoreInvisible(boolean enabled) {
        if (initialized) nativeSetIgnoreInvisible(enabled);
    }

    public static void setNeuralRotation(boolean enabled) {
        if (initialized) nativeSetNeuralRotation(enabled);
    }

    public static void setKillaura(boolean enabled) {
        if (initialized) nativeSetKillaura(enabled);
    }

    public static void setKillauraConeDeg(float deg) {
        if (initialized) nativeSetKillauraConeDeg(deg);
    }

    public static void setKillauraReactionMs(float ms) {
        if (initialized) nativeSetKillauraReactionMs(ms);
    }

    public static void setRotationRecording(boolean enabled) {
        if (initialized) nativeSetRotationRecording(enabled);
    }

    public static void setRotationPatternPath(String path) {
        if (initialized) nativeSetRotationPatternPath(path == null ? "" : path);
    }

    public static void setTargetPlayers(boolean enabled) {
        if (initialized) nativeSetTargetPlayers(enabled);
    }

    public static void setTargetMobs(boolean enabled) {
        if (initialized) nativeSetTargetMobs(enabled);
    }

    public static void setTriggerBot(boolean enabled) {
        if (initialized) nativeSetTriggerBot(enabled);
    }

    public static void setTriggerDelay(int delayMs) {
        if (initialized) nativeSetTriggerDelay(delayMs);
    }

    /** Пауза между ударами триггера, мс. */
    public static void setTriggerInterval(int intervalMs) {
        if (initialized) nativeSetTriggerInterval(intervalMs);
    }

    /** Reach триггера: до поверхности хитбокса, в блоках. */
    public static void setTriggerRange(float range) {
        if (initialized) nativeSetTriggerRange(range);
    }

    /** Допуск по хитбоксу для компенсации интерполяции, в блоках. */
    public static void setTriggerTolerance(float tolerance) {
        if (initialized) nativeSetTriggerTolerance(tolerance);
    }

    /** Включает humanization: реакция, микро-тремор, лимит поворота. */
    public static void setHumanization(boolean enabled) {
        if (initialized) nativeSetHumanization(enabled);
    }

    /** Задержка реакции на новую цель: база и случайный разброс, мс. */
    public static void setReactionDelay(int baseMs, int jitterMs) {
        if (initialized) nativeSetReactionDelay(baseMs, jitterMs);
    }

    /** Амплитуда микро-тремора камеры в градусах. */
    public static void setAimTremor(float degrees) {
        if (initialized) nativeSetAimTremor(degrees);
    }

    /** Anti-cheat профиль: конус захвата, шум сопровождения, без снапов. */
    public static void setAntiCheatMode(boolean enabled) {
        if (initialized) nativeSetAntiCheatMode(enabled);
    }

    /** Жёсткий конус захвата цели в градусах (10..180). */
    public static void setAssistCone(float degrees) {
        if (initialized) nativeSetAssistCone(degrees);
    }

    /** Вероятность пропуска удара триггера (0..0.35). */
    public static void setAttackMissChance(float chance) {
        if (initialized) nativeSetAttackMissChance(chance);
    }

    /** Бить только если серверная (тиковая) ротация тоже смотрит на цель. */
    public static void setServerViewGuard(boolean enabled) {
        if (initialized) nativeSetServerViewGuard(enabled);
    }

    /** Устойчивая скорость сопровождения в градусах за тик 50 мс. */
    public static void setSustainedTickDelta(float degrees) {
        if (initialized) nativeSetSustainedTickDelta(degrees);
    }

    /** Trigger Bot бьёт только в падении (криты). */
    public static void setOnlyCrit(boolean enabled) {
        if (initialized) nativeSetOnlyCrit(enabled);
    }

    public static void setAntiBot(boolean enabled) {
        if (initialized) nativeSetAntiBot(enabled);
    }

    public static void setRandomAimPoint(boolean enabled) {
        if (initialized) nativeSetRandomAimPoint(enabled);
    }

    public static boolean isOnTarget() {
        return initialized && nativeIsOnTarget();
    }

    public static boolean isEnabled() {
        return initialized && nativeIsEnabled();
    }

    public static void setTouchActive(boolean active) {
        if (initialized) nativeSetTouchActive(active);
    }

    public static void notifyCameraLook() {
        if (initialized) nativeNotifyCameraLook();
    }

    public static void setActivationHeld(boolean held) {
        if (initialized) nativeSetActivationHeld(held);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /** Включает запись диагностики в файл (в дополнение к logcat). */
    public static void setDiagLogPath(String path) {
        if (!loadLibrary()) return;
        nativeSetDiagLogPath(path);
    }

    private static native boolean nativeInit();
    private static native void nativeSetDiagLogPath(String path);
    private static native void nativeSetEnabled(boolean enabled);
    private static native boolean nativeIsEnabled();
    private static native void nativeSetRange(float range);
    private static native void nativeSetStrength(float strength);
    private static native void nativeSetFov(float fovDeg);
    private static native void nativeSetWallCheck(boolean enabled);
    private static native void nativeSetIgnoreInvisible(boolean enabled);
    private static native void nativeSetNeuralRotation(boolean enabled);
    private static native void nativeSetKillaura(boolean enabled);
    private static native void nativeSetKillauraConeDeg(float deg);
    private static native void nativeSetKillauraReactionMs(float ms);
    private static native void nativeSetRotationRecording(boolean enabled);
    private static native void nativeSetRotationPatternPath(String path);
    private static native void nativeSetTargetPlayers(boolean enabled);
    private static native void nativeSetTargetMobs(boolean enabled);
    private static native void nativeSetLegitMode(boolean enabled);
    private static native void nativeSetBodyPart(int bodyPart);
    private static native void nativeSetMaxSpeed(float degPerSecond);
    private static native void nativeSetSmoothing(float speed);
    private static native void nativeSetAcceleration(float degPerSecond2);
    private static native void nativeSetDeadzone(float deg);
    private static native void nativeSetFullSpeedRange(float deg);
    private static native void nativeSetStickyTicks(int ticks);
    private static native void nativeSetTargetLock(boolean enabled);
    private static native void nativeSetTargetGrace(int frames);
    private static native void nativeSetFastAim(boolean enabled);
    private static native void nativeSetFastAimTime(float timeMs);
    private static native void nativeSetFastAimMaxSpeed(float degPerSecond);
    private static native void nativeSetSwingStyle(int style);
    private static native void nativeSetSwingSpeed(int percent);
    private static native void nativeSetSwingAmplitude(int percent);
    private static native void nativeSetPreferClosest(boolean enabled);
    private static native void nativeSetTriggerBot(boolean enabled);
    private static native void nativeSetTriggerDelay(int delayMs);
    private static native void nativeSetTriggerInterval(int intervalMs);
    private static native void nativeSetTriggerRange(float range);
    private static native void nativeSetTriggerTolerance(float tolerance);
    private static native void nativeSetHumanization(boolean enabled);
    private static native void nativeSetReactionDelay(int baseMs, int jitterMs);
    private static native void nativeSetAimTremor(float degrees);
    private static native void nativeSetAntiCheatMode(boolean enabled);
    private static native void nativeSetAssistCone(float degrees);
    private static native void nativeSetAttackMissChance(float chance);
    private static native void nativeSetServerViewGuard(boolean enabled);
    private static native void nativeSetSustainedTickDelta(float degrees);
    private static native void nativeSetOnlyCrit(boolean enabled);
    private static native void nativeSetAntiBot(boolean enabled);
    private static native void nativeSetRandomAimPoint(boolean enabled);
    private static native boolean nativeIsOnTarget();
    private static native void nativeSetTouchActive(boolean active);
    private static native void nativeNotifyCameraLook();
    private static native void nativeSetActivationHeld(boolean held);
}
