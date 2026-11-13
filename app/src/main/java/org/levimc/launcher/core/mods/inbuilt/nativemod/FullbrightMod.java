package org.levimc.launcher.core.mods.inbuilt.nativemod;

public final class FullbrightMod {
    private static boolean libraryLoaded;
    private static volatile boolean initialized;
    private static volatile boolean enabled;
    private static long lastInitAttempt;
    private static final NativeModInit.Task INIT_TASK = new NativeModInit.Task();

    private FullbrightMod() {}

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

    public static void initializeAsync(NativeModInit.Callback callback) {
        if (initialized) {
            if (callback != null) callback.onResult(true);
            return;
        }
        INIT_TASK.request(FullbrightMod::initialize, callback);
    }

    public static boolean setEnabled(boolean value) {
        if (value && !initialized) return false;
        if (!initialized) return !value;
        if (enabled != value) {
            nativeSetEnabled(value);
            enabled = value;
        }
        return true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    private static native boolean nativeInit();
    private static native void nativeSetEnabled(boolean enabled);
    private static native boolean nativeIsEnabled();
}
