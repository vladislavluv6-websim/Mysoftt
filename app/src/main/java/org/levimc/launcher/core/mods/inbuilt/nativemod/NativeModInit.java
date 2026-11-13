package org.levimc.launcher.core.mods.inbuilt.nativemod;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs native mod initialization off the UI thread.
 *
 * nativeInit scans libminecraftpe.so for signatures; with AimAssist and ESP
 * together this takes several seconds and caused an ANR when run on the UI
 * thread. One background thread serializes all native inits.
 */
public final class NativeModInit {
    public interface Callback {
        void onResult(boolean ok);
    }

    interface Initializer {
        boolean initialize();
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "native-mod-init");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NativeModInit() {}

    /** Per-mod state: deduplicates concurrent requests and queues callbacks. */
    static final class Task {
        private final Object lock = new Object();
        private final List<Callback> pending = new ArrayList<>();
        private boolean running;

        void request(Initializer initializer, Callback callback) {
            synchronized (lock) {
                if (callback != null) pending.add(callback);
                if (running) return;
                running = true;
            }
            EXECUTOR.execute(() -> {
                boolean ok;
                try {
                    ok = initializer.initialize();
                } catch (Throwable t) {
                    ok = false;
                }
                final boolean result = ok;
                final List<Callback> callbacks;
                synchronized (lock) {
                    callbacks = new ArrayList<>(pending);
                    pending.clear();
                    running = false;
                }
                MAIN.post(() -> {
                    for (Callback cb : callbacks) cb.onResult(result);
                });
            });
        }
    }
}
