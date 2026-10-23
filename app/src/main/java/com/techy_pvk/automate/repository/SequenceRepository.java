package com.techy_pvk.automate.repository;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.techy_pvk.automate.data.AlarmScheduler;
import com.techy_pvk.automate.data.AppDatabase;
import com.techy_pvk.automate.data.AppShortcuts;
import com.techy_pvk.automate.data.GeofenceHelper;
import com.techy_pvk.automate.data.SequenceDao;
import com.techy_pvk.automate.data.SequenceMapper;
import com.techy_pvk.automate.data.SequenceShareHelper;
import com.techy_pvk.automate.data.SequenceStore;
import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.util.AppExecutors;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Single source of truth for saved sequences, as far as the UI is concerned.
 *
 *  - Reads are LiveData: the home list re-renders by itself after any save, delete or import.
 *  - Writes and one-shot loads run on a background thread and call back on the main thread.
 *  - After every change it re-syncs everything derived from the sequence list that lives
 *    OUTSIDE the app's own database - launcher shortcuts, geofences, scheduled alarms - so those
 *    can never drift from what's on screen.
 *
 * Callbacks are always delivered on the main thread, and run even if the screen that asked has
 * since been destroyed - callers must not assume their view still exists (ViewModels don't).
 */
public class SequenceRepository {

    private static volatile SequenceRepository instance;

    private final Context appContext;
    private final SequenceDao dao;
    private final AppExecutors executors;

    private MediatorLiveData<List<AutomationSequence>> all; // created lazily, on the main thread

    public static SequenceRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SequenceRepository.class) {
                if (instance == null) {
                    Context app = context.getApplicationContext();
                    instance = new SequenceRepository(app,
                            AppDatabase.getInstance(app).sequenceDao(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    SequenceRepository(@NonNull Context appContext, @NonNull SequenceDao dao, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.dao = dao;
        this.executors = executors;
        // Pull sequences out of the pre-Room SharedPreferences store (once, ever) before the first
        // query result is meaningful. If the live query wins the race it simply re-emits when the
        // migration's inserts land - Room invalidates it.
        executors.diskIO().execute(() -> SequenceStore.ensureMigrated(appContext));
    }

    /** All saved sequences, kept current automatically. The model conversion (JSON parsing)
     *  happens off the main thread. */
    @MainThread
    @NonNull
    public LiveData<List<AutomationSequence>> observeAll() {
        if (all == null) {
            all = new MediatorLiveData<>();
            all.addSource(dao.observeAll(), entities ->
                    executors.diskIO().execute(() -> all.postValue(SequenceMapper.toModels(entities))));
        }
        return all;
    }

    /** Loads one sequence; the callback gets null if it doesn't exist. */
    public void getById(@NonNull String id, @NonNull Consumer<AutomationSequence> callback) {
        executors.diskIO().execute(() -> {
            AutomationSequence sequence = SequenceStore.getById(appContext, id);
            executors.mainThread().execute(() -> callback.accept(sequence));
        });
    }

    /** Inserts or updates, then re-syncs shortcuts/geofences/alarms. */
    public void save(@NonNull AutomationSequence sequence, @Nullable Runnable onSaved) {
        executors.diskIO().execute(() -> {
            SequenceStore.upsert(appContext, sequence);
            syncSystemRegistrationsNow();
            if (onSaved != null) executors.mainThread().execute(onSaved);
        });
    }

    public void delete(@NonNull String id, @Nullable Runnable onDeleted) {
        executors.diskIO().execute(() -> {
            AlarmScheduler.cancel(appContext, id); // a deleted sequence must not keep a pending alarm
            SequenceStore.delete(appContext, id);
            syncSystemRegistrationsNow();
            if (onDeleted != null) executors.mainThread().execute(onDeleted);
        });
    }

    /** Cleans an untrusted sequence (see SequenceSanitizer), gives it a fresh id so it can never
     *  overwrite an existing one, saves it, and reports the stored result - or null if nothing
     *  usable was left to import. */
    public void importSequence(@NonNull AutomationSequence raw, @NonNull Consumer<AutomationSequence> callback) {
        executors.diskIO().execute(() -> {
            AutomationSequence clean = SequenceSanitizer.sanitize(raw);
            if (clean != null) {
                clean.id = UUID.randomUUID().toString();
                SequenceStore.upsert(appContext, clean);
                syncSystemRegistrationsNow();
            }
            executors.mainThread().execute(() -> callback.accept(clean));
        });
    }

    /** Writes the share file and hands back the chooser Intent, or null on failure. */
    public void createShareIntent(@NonNull AutomationSequence sequence, @NonNull Consumer<Intent> callback) {
        executors.diskIO().execute(() -> {
            Intent intent = null;
            try {
                intent = SequenceShareHelper.createShareIntent(appContext, sequence);
            } catch (IOException ignored) {
                // Reported to the caller as null.
            }
            Intent result = intent;
            executors.mainThread().execute(() -> callback.accept(result));
        });
    }

    /** Re-registers launcher shortcuts, geofences and alarms from the current database contents.
     *  Called when the home screen resumes: that's also what picks up a location permission the
     *  user only just granted, since geofences can't be registered without it. */
    public void syncSystemRegistrations() {
        executors.diskIO().execute(this::syncSystemRegistrationsNow);
    }

    private void syncSystemRegistrationsNow() {
        List<AutomationSequence> sequences = SequenceStore.loadAll(appContext);
        AppShortcuts.refresh(appContext, sequences);
        GeofenceHelper.registerAll(appContext, sequences);
        AlarmScheduler.scheduleAll(appContext, sequences);
    }
}
