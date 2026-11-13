package org.levimc.launcher.core.mods.inbuilt.nativemod;

public final class MovementMod {
    private static boolean libraryLoaded;
    private static boolean initialized;
    private static long lastInitAttempt;

    private MovementMod() {}

    private static synchronized boolean loadLibrary() {
        if (libraryLoaded) return true;
        try {
            System.loadLibrary("movementmod");
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

    public static synchronized boolean setEnabled(boolean enabled) {
        if (enabled && !initialize()) return false;
        if (!initialized) return !enabled;
        nativeSetEnabled(enabled);
        return true;
    }

    public static boolean isEnabled() {
        return initialized && nativeIsEnabled();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void setFlyEnabled(boolean enabled) { if (initialized) nativeSetFlyEnabled(enabled); }
    public static void setFlyMode(int mode) { if (initialized) nativeSetFlyMode(mode); }
    public static void setFlySpeed(float speed) { if (initialized) nativeSetFlySpeed(speed); }
    public static void setFlyUp(float up) { if (initialized) nativeSetFlyUp(up); }
    public static void setFlyDown(float down) { if (initialized) nativeSetFlyDown(down); }
    public static void setFlyCancelCorrections(boolean enabled) { if (initialized) nativeSetFlyCancelCorrections(enabled); }

    public static void setJesusEnabled(boolean enabled) { if (initialized) nativeSetJesusEnabled(enabled); }
    public static void setJesusMode(int mode) { if (initialized) nativeSetJesusMode(mode); }
    public static void setJesusDepth(float depth) { if (initialized) nativeSetJesusDepth(depth); }

    public static void setScaffoldEnabled(boolean enabled) { if (initialized) nativeSetScaffoldEnabled(enabled); }
    public static void setScaffoldMode(int mode) { if (initialized) nativeSetScaffoldMode(mode); }
    public static void setScaffoldPitch(float pitch) { if (initialized) nativeSetScaffoldPitch(pitch); }
    public static void setScaffoldInterval(int interval) { if (initialized) nativeSetScaffoldInterval(interval); }
    public static void setScaffoldRotate(boolean rotate) { if (initialized) nativeSetScaffoldRotate(rotate); }

    public static void setAutoJumpEnabled(boolean enabled) { if (initialized) nativeSetAutoJumpEnabled(enabled); }
    public static void setAutoJumpHeight(float height) { if (initialized) nativeSetAutoJumpHeight(height); }
    public static void setAutoJumpMode(int mode) { if (initialized) nativeSetAutoJumpMode(mode); }

    public static void setDebug(boolean enabled) { if (initialized) nativeSetDebug(enabled); }

    private static native boolean nativeInit();
    private static native void nativeSetEnabled(boolean enabled);
    private static native boolean nativeIsEnabled();

    private static native void nativeSetFlyEnabled(boolean enabled);
    private static native void nativeSetFlyMode(int mode);
    private static native void nativeSetFlySpeed(float speed);
    private static native void nativeSetFlyUp(float up);
    private static native void nativeSetFlyDown(float down);
    private static native void nativeSetFlyCancelCorrections(boolean enabled);

    private static native void nativeSetJesusEnabled(boolean enabled);
    private static native void nativeSetJesusMode(int mode);
    private static native void nativeSetJesusDepth(float depth);

    private static native void nativeSetScaffoldEnabled(boolean enabled);
    private static native void nativeSetScaffoldMode(int mode);
    private static native void nativeSetScaffoldPitch(float pitch);
    private static native void nativeSetScaffoldInterval(int interval);
    private static native void nativeSetScaffoldRotate(boolean rotate);

    private static native void nativeSetAutoJumpEnabled(boolean enabled);
    private static native void nativeSetAutoJumpHeight(float height);
    private static native void nativeSetAutoJumpMode(int mode);

    private static native void nativeSetDebug(boolean enabled);
}
