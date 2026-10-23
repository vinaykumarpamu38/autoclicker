package com.techy_pvk.automate.repository;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.BatteryConditionType;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.MusicPlaybackCondition;
import com.techy_pvk.automate.model.PowerSaveCondition;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes a sequence parsed from an untrusted shared file safe to store.
 *
 * Why this matters: an imported .automateseq arrives from WhatsApp/Drive/anyone, and Gson
 * happily produces objects with null fields (an unknown enum name becomes null, a JSON null
 * stays null). Once such a sequence is in the database, the home list calls step.summary() on
 * it every time it draws - and summary() switches on step.type, so a single null type would
 * throw on every launch: a crash loop that survives restarts and can only be fixed by
 * clearing app data. Cleaning the object up before it is ever saved closes that door.
 */
public final class SequenceSanitizer {

    public static final int MAX_STEPS = 500;
    public static final int MAX_NAME_LENGTH = 80;
    private static final long MAX_DURATION_MS = 10 * 60 * 1000L;
    public static final String DEFAULT_NAME = "Imported sequence";

    private SequenceSanitizer() {}

    /** Returns a cleaned copy, or null if nothing usable is left (no valid steps). The id is
     *  left untouched - the caller assigns a fresh one. */
    public static AutomationSequence sanitize(AutomationSequence raw) {
        if (raw == null) return null;

        AutomationSequence clean = new AutomationSequence();
        clean.id = raw.id;
        clean.name = cleanName(raw.name);
        clean.repeatCount = raw.repeatCount < 0 ? 1 : raw.repeatCount;

        List<GestureStep> steps = new ArrayList<>();
        if (raw.steps != null) {
            for (GestureStep step : raw.steps) {
                if (steps.size() >= MAX_STEPS) break;
                GestureStep cleaned = cleanStep(step);
                if (cleaned != null) steps.add(cleaned);
            }
        }
        if (steps.isEmpty()) return null;
        clean.steps = steps;

        clean.trigger = cleanTrigger(raw.trigger);
        clean.autoRestore = raw.autoRestore && clean.trigger.supportsAutoRestore();
        return clean;
    }

    private static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return DEFAULT_NAME;
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    private static GestureStep cleanStep(GestureStep step) {
        if (step == null || step.type == null) return null;
        step.delayBeforeMs = clamp(step.delayBeforeMs, 0, MAX_DURATION_MS);
        step.durationMs = clamp(step.durationMs, 0, MAX_DURATION_MS);
        step.matchTimeoutMs = clamp(step.matchTimeoutMs, 0, MAX_DURATION_MS);
        step.brightnessPercent = (int) clamp(step.brightnessPercent, 1, 100);
        return step;
    }

    private static SequenceTrigger cleanTrigger(SequenceTrigger raw) {
        if (raw == null || raw.type == null) return new SequenceTrigger(); // MANUAL

        // A location trigger with impossible coordinates would register a nonsense geofence -
        // fall back to manual instead of guessing.
        if (raw.type == TriggerType.LOCATION
                && (Double.isNaN(raw.locationLatitude) || Double.isNaN(raw.locationLongitude)
                || Math.abs(raw.locationLatitude) > 90 || Math.abs(raw.locationLongitude) > 180)) {
            return new SequenceTrigger();
        }

        if (raw.locationTransition == null) raw.locationTransition = LocationTransition.ENTER;
        if (raw.batteryConditionType == null) raw.batteryConditionType = BatteryConditionType.LEVEL_DROPPED_TO;
        if (raw.musicPlaybackCondition == null) raw.musicPlaybackCondition = MusicPlaybackCondition.STARTED;
        if (raw.powerSaveCondition == null) raw.powerSaveCondition = PowerSaveCondition.ENABLED;
        if (!(raw.locationRadiusMeters >= 1f)) raw.locationRadiusMeters = 150f; // also catches NaN
        raw.batteryLevelPercent = (int) clamp(raw.batteryLevelPercent, 1, 100);
        raw.scheduledHour = (int) clamp(raw.scheduledHour, 0, 23);
        raw.scheduledMinute = (int) clamp(raw.scheduledMinute, 0, 59);
        if (raw.scheduledDays == null || raw.scheduledDays.length != 7) {
            raw.scheduledDays = new boolean[]{true, true, true, true, true, true, true};
        }
        return raw;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
