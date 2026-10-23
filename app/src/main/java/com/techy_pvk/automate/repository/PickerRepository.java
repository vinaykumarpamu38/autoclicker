package com.techy_pvk.automate.repository;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.StepType;
import com.techy_pvk.automate.service.FloatingPickerService;

/**
 * Reads the point(s) the floating picker overlay captured. FloatingPickerService is a
 * different component with no direct link to the builder screen, so it leaves the result in
 * SharedPreferences and the builder collects it when it comes back to the foreground.
 * Blocking I/O: call from a background thread.
 */
public class PickerRepository {

    private final SharedPreferences prefs;

    public PickerRepository(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FloatingPickerService.PREFS, Context.MODE_PRIVATE);
    }

    /** Returns the captured point as a ready-to-add step and clears it, or null if the picker
     *  hasn't saved anything since the last call. */
    @Nullable
    public GestureStep consumePendingStep() {
        String pendingType = prefs.getString(FloatingPickerService.KEY_PENDING_TYPE, null);
        if (pendingType == null) return null;

        GestureStep step = new GestureStep();
        step.x1 = prefs.getFloat(FloatingPickerService.KEY_X1, 0);
        step.y1 = prefs.getFloat(FloatingPickerService.KEY_Y1, 0);

        if (FloatingPickerService.MODE_SWIPE.equals(pendingType)) {
            step.type = StepType.SWIPE;
            step.x2 = prefs.getFloat(FloatingPickerService.KEY_X2, 0);
            step.y2 = prefs.getFloat(FloatingPickerService.KEY_Y2, 0);
            step.durationMs = 300;
        } else if (FloatingPickerService.MODE_LONG_CLICK.equals(pendingType)) {
            step.type = StepType.LONG_CLICK;
            step.durationMs = 700; // comfortably past the platform's long-press timeout
        } else {
            step.type = StepType.TAP;
            step.durationMs = 80;
        }
        step.delayBeforeMs = 500;

        prefs.edit().clear().apply();
        return step;
    }
}
