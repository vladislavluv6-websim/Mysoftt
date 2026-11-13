package org.levimc.launcher.core.mods.inbuilt.nativemod;

public final class SpeedMod {
    private static boolean libraryLoaded;
    private static boolean initialized;
    private static long lastInitAttempt;

    private SpeedMod() {}

    private static synchronized boolean loadLibrary() {
        if (libraryLoaded) return true;
        try {
            System.loadLibrary("speedmod");
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

    public static boolean setEnabled(boolean enabled, float multiplier) {
        if (enabled && !initialize()) return false;
        if (!initialized) return !enabled;
        setMultiplier(multiplier);
        nativeSetEnabled(enabled);
        return true;
    }

    public static void setMultiplier(float multiplier) {
        if (initialized) nativeSetMultiplier(multiplier);
    }

    public static void setSpeedEnabled(boolean enabled) {
        if (initialized) nativeSetSpeedEnabled(enabled);
    }

    public static void setSpeedMode(int mode) {
        if (initialized) nativeSetSpeedMode(mode);
    }

    public static void setMotionBoost(float boost) {
        if (initialized) nativeSetMotionBoost(boost);
    }

    public static void setMaxSpeed(float maxSpeed) {
        if (initialized) nativeSetMaxSpeed(maxSpeed);
    }

    public static void setGapMax(float gapMax) {
        if (initialized) nativeSetGapMax(gapMax);
    }

    public static void setResetInterval(int ms) {
        if (initialized) nativeSetResetInterval(ms);
    }

    public static void setMaxResetTicks(int ticks) {
        if (initialized) nativeSetMaxResetTicks(ticks);
    }

    public static void setHopEnabled(boolean enabled) {
        if (initialized) nativeSetHopEnabled(enabled);
    }

    public static void setHopHeight(float height) {
        if (initialized) nativeSetHopHeight(height);
    }

    public static void setHopInterval(int interval) {
        if (initialized) nativeSetHopInterval(interval);
    }

    public static void setMaskY(boolean enabled) {
        if (initialized) nativeSetMaskY(enabled);
    }

    public static void setPhantomHoldTicks(int ticks) {
        if (initialized) nativeSetPhantomHoldTicks(ticks);
    }

    public static void setFakeJumpTicks(int ticks) {
        if (initialized) nativeSetFakeJumpTicks(ticks);
    }

    public static void setCancelCorrections(boolean enabled) {
        if (initialized) nativeSetCancelCorrections(enabled);
    }

    public static void setPauseOnFall(boolean enabled) {
        if (initialized) nativeSetPauseOnFall(enabled);
    }

    public static void setPauseIfSneaking(boolean enabled) {
        if (initialized) nativeSetPauseIfSneaking(enabled);
    }

    public static void setPauseIfNotMoving(boolean enabled) {
        if (initialized) nativeSetPauseIfNotMoving(enabled);
    }

    public static void setAutoSprint(boolean enabled) {
        if (initialized) nativeSetAutoSprint(enabled);
    }

    public static void setDebug(boolean enabled) {
        if (initialized) nativeSetDebug(enabled);
    }

    public static boolean isEnabled() {
        return initialized && nativeIsEnabled();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static native boolean nativeInit();
    private static native void nativeSetEnabled(boolean enabled);
    private static native boolean nativeIsEnabled();
    private static native void nativeSetMultiplier(float multiplier);
    private static native void nativeSetSpeedEnabled(boolean enabled);
    private static native void nativeSetSpeedMode(int mode);
    private static native void nativeSetMotionBoost(float boost);
    private static native void nativeSetMaxSpeed(float maxSpeed);
    private static native void nativeSetGapMax(float gapMax);
    private static native void nativeSetResetInterval(int ms);
    private static native void nativeSetMaxResetTicks(int ticks);
    private static native void nativeSetHopEnabled(boolean enabled);
    private static native void nativeSetHopHeight(float height);
    private static native void nativeSetHopInterval(int interval);
    private static native void nativeSetMaskY(boolean enabled);
    private static native void nativeSetPhantomHoldTicks(int ticks);
    private static native void nativeSetFakeJumpTicks(int ticks);
    private static native void nativeSetCancelCorrections(boolean enabled);
    private static native void nativeSetPauseOnFall(boolean enabled);
    private static native void nativeSetPauseIfSneaking(boolean enabled);
    private static native void nativeSetPauseIfNotMoving(boolean enabled);
    private static native void nativeSetAutoSprint(boolean enabled);
    private static native void nativeSetDebug(boolean enabled);
}
