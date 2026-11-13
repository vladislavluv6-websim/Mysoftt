package org.levimc.launcher.core.mods.inbuilt.nativemod;

public final class EspMod {
    private static boolean libraryLoaded;
    private static volatile boolean initialized;
    private static long lastInitAttempt;
    private static final NativeModInit.Task INIT_TASK = new NativeModInit.Task();

    private EspMod() {}

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
        INIT_TASK.request(EspMod::initialize, callback);
    }

    /** Requires a completed {@link #initializeAsync}; never blocks the caller. */
    public static boolean setEnabled(boolean enabled) {
        if (enabled && !initialized) return false;
        if (!initialized) return !enabled;
        nativeSetEnabled(enabled);
        return true;
    }

    public static void setPlayers(boolean enabled) {
        if (initialized) nativeSetPlayers(enabled);
    }

    public static void setMobs(boolean enabled) {
        if (initialized) nativeSetMobs(enabled);
    }

    public static void setRange(float range) {
        if (initialized) nativeSetRange(range);
    }

    public static void setPlayerColor(int argb) {
        if (initialized) nativeSetPlayerColor(argb);
    }

    public static void setMobColor(int argb) {
        if (initialized) nativeSetMobColor(argb);
    }

    public static void setArmoredColor(int argb) {
        if (initialized) nativeSetArmoredColor(argb);
    }

    public static void setArmorColors(boolean enabled) {
        if (initialized) nativeSetArmorColors(enabled);
    }

    public static void setEspBoxStyle(int style) {
        if (initialized) nativeSetEspBoxStyle(style);
    }

    public static String[] getEspBoxes() {
        return initialized ? nativeGetEspBoxes() : null;
    }

    public static void setNameTags(boolean enabled) {
        if (initialized) nativeSetNameTags(enabled);
    }

    public static void setNameTagScreenSize(float width, float height) {
        if (initialized) nativeSetNameTagScreenSize(width, height);
    }

    public static String[] getNameTags() {
        return initialized ? nativeGetNameTags() : null;
    }

    public static void setTargetEsp(boolean enabled) {
        if (initialized) nativeSetTargetEsp(enabled);
    }

    public static void setTargetEspHold(int holdMs) {
        if (initialized) nativeSetTargetEspHold(holdMs);
    }

    public static void setFriendNames(String names) {
        if (initialized) nativeSetFriendNames(names == null ? "" : names);
    }

    public static String getTargetEsp() {
        return initialized ? nativeGetTargetEsp() : null;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static native boolean nativeInit();
    private static native void nativeSetEnabled(boolean enabled);
    private static native void nativeSetPlayers(boolean enabled);
    private static native void nativeSetMobs(boolean enabled);
    private static native void nativeSetRange(float range);
    private static native void nativeSetPlayerColor(int argb);
    private static native void nativeSetMobColor(int argb);
    private static native void nativeSetArmoredColor(int argb);
    private static native void nativeSetArmorColors(boolean enabled);
    private static native void nativeSetEspBoxStyle(int style);
    private static native String[] nativeGetEspBoxes();
    private static native void nativeSetNameTags(boolean enabled);
    private static native void nativeSetNameTagScreenSize(float width, float height);
    private static native String[] nativeGetNameTags();
    private static native void nativeSetTargetEsp(boolean enabled);
    private static native void nativeSetTargetEspHold(int holdMs);
    private static native void nativeSetFriendNames(String names);
    private static native String nativeGetTargetEsp();
}
