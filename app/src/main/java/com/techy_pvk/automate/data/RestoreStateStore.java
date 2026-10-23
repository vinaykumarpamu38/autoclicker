package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Auto-Restore is currently scoped to the one "restorable" step type that exists so far -
 * Set Ringer Mode. Extending this to brightness/DND/volume later just means adding another
 * saveXSnapshot/takeXSnapshot pair here, and another branch in TriggerMatcher's snapshot/
 * restore helpers - the per-sequence storage and lifecycle stays the same.
 */
public final class RestoreStateStore {

    private static final String PREFS = "AutoMateRestoreState";
    private static final String KEY_PREFIX_RINGER = "ringer_";

    private RestoreStateStore() {}

    public static void saveRingerModeSnapshot(Context context, String sequenceId, int ringerMode) {
        prefs(context).edit().putInt(KEY_PREFIX_RINGER + sequenceId, ringerMode).apply();
    }

    /** Returns the saved value and clears it (a snapshot is only ever consumed once), or null
     *  if there was nothing pending - e.g. the app was reinstalled between the two events, or
     *  auto-restore wasn't actually enabled when the start side fired. */
    public static Integer takeRingerModeSnapshot(Context context, String sequenceId) {
        SharedPreferences prefs = prefs(context);
        String key = KEY_PREFIX_RINGER + sequenceId;
        if (!prefs.contains(key)) return null;
        int value = prefs.getInt(key, -1);
        prefs.edit().remove(key).apply();
        return value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
