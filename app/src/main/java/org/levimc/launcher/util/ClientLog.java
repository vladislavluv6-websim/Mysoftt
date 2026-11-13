package org.levimc.launcher.util;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Persistent client log written to the app media dir
 * (Android/media/&lt;pkg&gt;/logs/client.log) so it can be read/exported even
 * when logcat is not accessible on the device. Kept intentionally tiny: only
 * important lifecycle/module events are written.
 */
public final class ClientLog {
    private static final String TAG = "LeviClient";
    private static final Object LOCK = new Object();
    private static volatile File logFile;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private ClientLog() {
    }

    public static void init(Context context) {
        if (logFile != null) return;
        try {
            File root = LauncherStorage.getAppRoot(context);
            File dir = new File(root, "logs");
            if (!dir.exists() && !dir.mkdirs()) return;
            logFile = new File(dir, "client.log");
        } catch (Throwable ignored) {
        }
    }

    public static File getLogFile() {
        return logFile;
    }

    private static volatile boolean crashHookInstalled = false;

    public static void installCrashHook() {
        if (crashHookInstalled) return;
        crashHookInstalled = true;
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                log("Crash", "uncaught in " + thread.getName() + ": " + error);
                for (StackTraceElement frame : error.getStackTrace()) {
                    log("Crash", "  at " + frame);
                }
                try {
                    Runtime rt = Runtime.getRuntime();
                    log("Crash", "heap used=" + (rt.totalMemory() - rt.freeMemory())
                            + " max=" + rt.maxMemory());
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    public static void log(String tag, String message) {
        Log.i(TAG, tag + ": " + message);
        File file = logFile;
        if (file == null) return;
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                String line = TIME.format(new Date()) + " [" + tag + "] " + message + "\n";
                out.write(line.getBytes());
            } catch (Throwable ignored) {
            }
        }
    }
}
