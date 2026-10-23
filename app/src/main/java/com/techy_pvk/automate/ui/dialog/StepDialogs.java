package com.techy_pvk.automate.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.AppInfo;
import com.techy_pvk.automate.model.DndMode;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.RingerModeOption;
import com.techy_pvk.automate.model.ScrollDirection;
import com.techy_pvk.automate.model.StepType;
import com.techy_pvk.automate.util.InputParsers;

import java.util.List;
import java.util.function.Consumer;

/**
 * The "Add a step" dialogs. Each one collects input and hands a finished GestureStep to a
 * callback - they hold no state of their own and never touch storage; the ViewModel decides what
 * to do with the step. (These used to be ~600 lines of private methods inside the builder Activity.)
 */
public final class StepDialogs {

    /** Labels for the step-type chooser. Order must match SequenceBuilderActivity.onStepTypeChosen(). */
    public static final String[] STEP_TYPE_LABELS = {
            "Tap", "Long Click", "Swipe", "Scroll", "Wait", "Open App",
            "Click by Text", "Click by Resource ID", "Click by Content Description",
            "Type Text", "Back", "Home", "Recent Apps", "Set Ringer Mode",
            "Set Do Not Disturb", "Set Brightness",
    };

    private static final long DEFAULT_DELAY_MS = 500;

    private StepDialogs() {}

    /** A step with no parameters of its own: Back, Home or Recent Apps. */
    @NonNull
    public static GestureStep simpleGlobalStep(@NonNull StepType type) {
        GestureStep step = new GestureStep();
        step.type = type;
        step.delayBeforeMs = DEFAULT_DELAY_MS;
        return step;
    }

    public static AlertDialog showChooser(@NonNull Context context, @NonNull Consumer<Integer> onChosen) {
        return new AlertDialog.Builder(context)
                .setTitle("Add a step")
                .setItems(STEP_TYPE_LABELS, (dialog, which) -> onChosen.accept(which))
                .show();
    }

    public static AlertDialog showWait(@NonNull Context context, @NonNull Consumer<GestureStep> onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_wait_step, null);
        EditText msInput = view.findViewById(R.id.input_wait_ms);
        msInput.setText("1000");

        return new AlertDialog.Builder(context)
                .setTitle("Add a wait")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    GestureStep step = new GestureStep();
                    step.type = StepType.WAIT;
                    step.delayBeforeMs = InputParsers.parseLongOrDefault(msInput.getText().toString(), 1000);
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static AlertDialog showLaunchApp(@NonNull Context context, @NonNull List<AppInfo> apps,
                                     @NonNull Consumer<GestureStep> onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_launch_app_step, null);
        Spinner appSpinner = view.findViewById(R.id.spinner_apps);
        appSpinner.setAdapter(appAdapter(context, apps));

        return new AlertDialog.Builder(context)
                .setTitle("Open an app")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    AppInfo app = (AppInfo) appSpinner.getSelectedItem();
                    if (app == null) return;
                    GestureStep step = new GestureStep();
                    step.type = StepType.LAUNCH_APP;
                    step.packageName = app.packageName;
                    step.appLabel = app.label;
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static AlertDialog showScroll(@NonNull Context context, @NonNull Consumer<GestureStep> onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_scroll_step, null);
        RadioGroup directionGroup = view.findViewById(R.id.radio_group_scroll_direction);
        EditText durationInput = view.findViewById(R.id.input_scroll_duration);
        durationInput.setText("300");

        return new AlertDialog.Builder(context)
                .setTitle("Add a scroll")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    int checkedId = directionGroup.getCheckedRadioButtonId();
                    ScrollDirection direction;
                    if (checkedId == R.id.radio_scroll_up) {
                        direction = ScrollDirection.UP;
                    } else if (checkedId == R.id.radio_scroll_left) {
                        direction = ScrollDirection.LEFT;
                    } else if (checkedId == R.id.radio_scroll_right) {
                        direction = ScrollDirection.RIGHT;
                    } else {
                        direction = ScrollDirection.DOWN;
                    }

                    GestureStep step = new GestureStep();
                    step.type = StepType.SCROLL;
                    step.scrollDirection = direction;
                    step.durationMs = InputParsers.parseLongOrDefault(durationInput.getText().toString(), 300);
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Click by Text / Resource ID / Content Description - same dialog, different match type. */
    public static AlertDialog showClickBy(@NonNull Context context, @NonNull StepType type, @NonNull String title,
                                   @NonNull String hint, @NonNull Consumer<GestureStep> onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_click_by_step, null);
        TextView label = view.findViewById(R.id.text_click_label);
        EditText valueInput = view.findViewById(R.id.input_match_value);
        EditText timeoutInput = view.findViewById(R.id.input_match_timeout);
        label.setText(hint);
        valueInput.setHint(hint);
        timeoutInput.setText("3000");

        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    String value = valueInput.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(context, "Enter a value to match", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    GestureStep step = new GestureStep();
                    step.type = type;
                    step.targetValue = value;
                    step.matchTimeoutMs = InputParsers.parseLongOrDefault(timeoutInput.getText().toString(), 3000);
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static AlertDialog showTypeText(@NonNull Context context, @NonNull Consumer<GestureStep> onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_type_text_step, null);
        EditText textInput = view.findViewById(R.id.input_type_text);
        EditText targetInput = view.findViewById(R.id.input_type_target);
        EditText timeoutInput = view.findViewById(R.id.input_type_timeout);
        timeoutInput.setText("3000");

        return new AlertDialog.Builder(context)
                .setTitle("Type text")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    GestureStep step = new GestureStep();
                    step.type = StepType.TYPE_TEXT;
                    step.textToType = textInput.getText().toString();
                    step.targetValue = targetInput.getText().toString().trim();
                    step.matchTimeoutMs = InputParsers.parseLongOrDefault(timeoutInput.getText().toString(), 3000);
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static AlertDialog showRingerMode(@NonNull Context context, @NonNull Consumer<GestureStep> onAdded) {
        String[] labels = {"Normal", "Vibrate", "Silent (needs Do Not Disturb access)"};
        RingerModeOption[] options = {RingerModeOption.NORMAL, RingerModeOption.VIBRATE, RingerModeOption.SILENT};

        return new AlertDialog.Builder(context)
                .setTitle("Set ringer mode to")
                .setItems(labels, (dialog, which) -> {
                    GestureStep step = new GestureStep();
                    step.type = StepType.SET_RINGER_MODE;
                    step.ringerMode = options[which];
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static AlertDialog showDnd(@NonNull Context context, @NonNull Consumer<GestureStep> onAdded) {
        String[] labels = {
                "Off (needs Do Not Disturb access)",
                "Priority Only (needs Do Not Disturb access)",
                "Alarms Only (needs Do Not Disturb access)",
                "Total Silence (needs Do Not Disturb access)",
        };
        DndMode[] options = {DndMode.OFF, DndMode.PRIORITY_ONLY, DndMode.ALARMS_ONLY, DndMode.TOTAL_SILENCE};

        return new AlertDialog.Builder(context)
                .setTitle("Set Do Not Disturb to")
                .setItems(labels, (dialog, which) -> {
                    GestureStep step = new GestureStep();
                    step.type = StepType.SET_DND;
                    step.dndMode = options[which];
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static AlertDialog showBrightness(@NonNull Context context, @NonNull Consumer<GestureStep> onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_brightness_step, null);
        EditText percentInput = view.findViewById(R.id.input_brightness_percent);
        percentInput.setText("50");

        return new AlertDialog.Builder(context)
                .setTitle("Set brightness to (needs Modify System Settings access)")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    Integer percent = InputParsers.parseIntInRange(percentInput.getText().toString(), 1, 100);
                    if (percent == null) {
                        Toast.makeText(context, "Enter a value from 1 to 100", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    GestureStep step = new GestureStep();
                    step.type = StepType.SET_BRIGHTNESS;
                    step.brightnessPercent = percent;
                    step.delayBeforeMs = DEFAULT_DELAY_MS;
                    onAdded.accept(step);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shared by every dialog that offers an app list. */
    @NonNull
    static ArrayAdapter<AppInfo> appAdapter(@NonNull Context context, @NonNull List<AppInfo> apps) {
        ArrayAdapter<AppInfo> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, apps);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
}
