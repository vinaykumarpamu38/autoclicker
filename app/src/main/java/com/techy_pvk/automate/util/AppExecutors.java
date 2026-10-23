package com.techy_pvk.automate.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The two places work is allowed to run: a single background thread for disk / system-service
 * work, and the main thread for anything that touches UI state.
 *
 * diskIO() is deliberately a SINGLE thread: tasks run strictly in the order they were
 * submitted, so "load the draft, then consume the picked point" or "save, then re-register
 * geofences" can never interleave or reorder. The data here is tiny (a handful of sequences),
 * so one thread is nowhere near a bottleneck.
 */
public final class AppExecutors {

    private static volatile AppExecutors instance;

    private final Executor diskIO;
    private final Executor mainThread;

    /** Public so unit tests can pass same-thread executors. */
    public AppExecutors(@NonNull Executor diskIO, @NonNull Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors(Executors.newSingleThreadExecutor(), new MainThreadExecutor());
                }
            }
        }
        return instance;
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            handler.post(command);
        }
    }
}
