package com.techy_pvk.automate.util;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import java.util.concurrent.Executor;

/**
 * A LiveData backed by a SharedPreferences file: loads on a background thread when it gains
 * its first observer, and reloads whenever the file changes - including writes made by a
 * Service or another screen. That's what lets the Notification Log screen update live as a
 * background trigger appends to it, and the drawer greeting update the moment the profile name
 * is edited, with no manual refresh calls.
 */
public abstract class PrefsLiveData<T> extends LiveData<T> {

    private final SharedPreferences prefs;
    private final Executor diskIO;

    // SharedPreferences only keeps a weak reference to listeners, so this has to be a field -
    // a listener held only by a local variable would be garbage-collected and silently stop firing.
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (sharedPreferences, key) -> reload();

    protected PrefsLiveData(@NonNull SharedPreferences prefs, @NonNull Executor diskIO) {
        this.prefs = prefs;
        this.diskIO = diskIO;
    }

    /** Reads the current value. Runs on the background executor, never the main thread. */
    protected abstract T load();

    private void reload() {
        diskIO.execute(() -> postValue(load()));
    }

    @Override
    protected void onActive() {
        prefs.registerOnSharedPreferenceChangeListener(listener);
        reload();
    }

    @Override
    protected void onInactive() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
