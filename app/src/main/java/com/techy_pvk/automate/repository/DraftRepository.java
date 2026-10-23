package com.techy_pvk.automate.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.SequenceTrigger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the sequence the builder is currently editing, so it survives the builder being
 * reclaimed while the app is backgrounded (the Tap/Swipe pickers send the whole app to the
 * background so their overlay can sit on top of another app - Android may kill the process
 * while it's away). Blocking I/O: call from a background thread.
 */
public class DraftRepository {

    private static final String PREFS = "AutoClickerDraft";
    private static final String KEY_ID = "draft_id";
    private static final String KEY_NAME = "draft_name";
    private static final String KEY_STEPS = "draft_steps";
    private static final String KEY_REPEAT = "draft_repeat";
    private static final String KEY_TRIGGER = "draft_trigger";
    private static final String KEY_AUTO_RESTORE = "draft_auto_restore";
    private static final String KEY_SAVED_AT = "draft_saved_at";

    private static final Type STEPS_TYPE = new TypeToken<List<GestureStep>>() {}.getType();

    /** Snapshot of the builder form. */
    public static final class Draft {
        public final String id;
        public final String name;
        public final String repeatText;
        public final List<GestureStep> steps;
        public final SequenceTrigger trigger;
        public final boolean autoRestore;
        public final long savedAt;

        public Draft(String id, String name, String repeatText, List<GestureStep> steps,
                     SequenceTrigger trigger, boolean autoRestore, long savedAt) {
            this.id = id;
            this.name = name;
            this.repeatText = repeatText;
            this.steps = steps;
            this.trigger = trigger;
            this.autoRestore = autoRestore;
            this.savedAt = savedAt;
        }
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public DraftRepository(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The stored draft, or null if there isn't one. */
    @Nullable
    public Draft load() {
        String stepsJson = prefs.getString(KEY_STEPS, null);
        if (stepsJson == null) return null;

        List<GestureStep> steps = null;
        SequenceTrigger trigger = null;
        try {
            steps = gson.fromJson(stepsJson, STEPS_TYPE);
            String triggerJson = prefs.getString(KEY_TRIGGER, null);
            trigger = triggerJson != null ? gson.fromJson(triggerJson, SequenceTrigger.class) : null;
        } catch (RuntimeException ignored) {
            // A corrupt draft is just "no draft".
            return null;
        }

        return new Draft(
                prefs.getString(KEY_ID, null),
                prefs.getString(KEY_NAME, ""),
                String.valueOf(prefs.getInt(KEY_REPEAT, 1)),
                steps != null ? steps : new ArrayList<>(),
                trigger != null ? trigger : new SequenceTrigger(),
                prefs.getBoolean(KEY_AUTO_RESTORE, false),
                prefs.getLong(KEY_SAVED_AT, 0));
    }

    public void save(@NonNull Draft draft) {
        int repeat;
        try {
            repeat = Integer.parseInt(draft.repeatText.trim());
        } catch (NumberFormatException e) {
            repeat = 1;
        }
        prefs.edit()
                .putString(KEY_ID, draft.id)
                .putString(KEY_NAME, draft.name)
                .putInt(KEY_REPEAT, repeat)
                .putString(KEY_STEPS, gson.toJson(draft.steps))
                .putString(KEY_TRIGGER, gson.toJson(draft.trigger))
                .putBoolean(KEY_AUTO_RESTORE, draft.autoRestore)
                .putLong(KEY_SAVED_AT, draft.savedAt)
                .apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
