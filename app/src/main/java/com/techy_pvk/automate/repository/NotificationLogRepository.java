package com.techy_pvk.automate.repository;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.techy_pvk.automate.data.NotificationLogStore;
import com.techy_pvk.automate.model.NotificationLogEntry;
import com.techy_pvk.automate.util.AppExecutors;
import com.techy_pvk.automate.util.PrefsLiveData;

import java.util.List;

/** The Notification Log as live data. A background trigger appending an entry while this
 *  screen is open shows up immediately - no refresh call, no onResume() reload. */
public class NotificationLogRepository {

    private static volatile NotificationLogRepository instance;

    private final Context appContext;
    private final AppExecutors executors;
    private LiveData<List<NotificationLogEntry>> entries; // created lazily, on the main thread

    public static NotificationLogRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (NotificationLogRepository.class) {
                if (instance == null) {
                    instance = new NotificationLogRepository(context.getApplicationContext(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    NotificationLogRepository(@NonNull Context appContext, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    @MainThread
    @NonNull
    public LiveData<List<NotificationLogEntry>> observe() {
        if (entries == null) {
            entries = new PrefsLiveData<List<NotificationLogEntry>>(
                    appContext.getSharedPreferences(NotificationLogStore.PREFS, Context.MODE_PRIVATE),
                    executors.diskIO()) {
                @Override
                protected List<NotificationLogEntry> load() {
                    return NotificationLogStore.loadAll(appContext);
                }
            };
        }
        return entries;
    }

    public void clear(@Nullable Runnable onCleared) {
        executors.diskIO().execute(() -> {
            NotificationLogStore.clear(appContext);
            if (onCleared != null) executors.mainThread().execute(onCleared);
        });
    }
}
