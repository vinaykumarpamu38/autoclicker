package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.techy_pvk.automate.model.AutomationSequence;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Synchronous sequence access for code that runs OUTSIDE any UI lifecycle: AutomationService,
 * TriggerMatcher, and the boot / geofence / alarm / Bluetooth receivers. They have no screen to
 * observe, are woken by the system, and need the answer right now.
 *
 * UI code must NOT use this - go through SequenceRepository (LiveData + background executor).
 * Both share SequenceMapper, so they always read and write the same format.
 */
public class SequenceStore {

    private static final Gson GSON = new Gson();

    // One-time migration from the pre-Room SharedPreferences store, so upgrading doesn't
    // silently lose whatever sequences were already saved.
    private static final String OLD_PREFS = "AutoClickerSequences";
    private static final String OLD_KEY_SEQUENCES = "sequences_json";
    private static final String MIGRATION_PREFS = "AutoMateMigration";
    private static final String KEY_MIGRATED = "migrated_to_room";
    private static boolean migrationChecked = false;

    /** Safe to call repeatedly and from any thread; does the work at most once per process. */
    public static synchronized void ensureMigrated(Context context) {
        if (migrationChecked) return;
        migrationChecked = true;

        SharedPreferences migrationPrefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE);
        if (migrationPrefs.getBoolean(KEY_MIGRATED, false)) return;

        SharedPreferences oldPrefs = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE);
        String json = oldPrefs.getString(OLD_KEY_SEQUENCES, null);
        if (json != null) {
            Type type = new TypeToken<List<AutomationSequence>>() {}.getType();
            List<AutomationSequence> old = GSON.fromJson(json, type);
            if (old != null && !old.isEmpty()) {
                SequenceDao dao = AppDatabase.getInstance(context).sequenceDao();
                for (AutomationSequence sequence : old) {
                    dao.upsert(SequenceMapper.toEntity(sequence));
                }
            }
        }
        migrationPrefs.edit().putBoolean(KEY_MIGRATED, true).apply();
    }

    public static List<AutomationSequence> loadAll(Context context) {
        ensureMigrated(context);
        return SequenceMapper.toModels(AppDatabase.getInstance(context).sequenceDao().getAll());
    }

    public static void saveAll(Context context, List<AutomationSequence> sequences) {
        SequenceDao dao = AppDatabase.getInstance(context).sequenceDao();
        for (AutomationSequence sequence : sequences) {
            dao.upsert(SequenceMapper.toEntity(sequence));
        }
    }

    public static void upsert(Context context, AutomationSequence sequence) {
        AppDatabase.getInstance(context).sequenceDao().upsert(SequenceMapper.toEntity(sequence));
    }

    public static void delete(Context context, String id) {
        AppDatabase.getInstance(context).sequenceDao().deleteById(id);
    }

    public static AutomationSequence getById(Context context, String id) {
        ensureMigrated(context);
        AutomationSequenceEntity row = AppDatabase.getInstance(context).sequenceDao().getById(id);
        return row != null ? SequenceMapper.toModel(row) : null;
    }
}
