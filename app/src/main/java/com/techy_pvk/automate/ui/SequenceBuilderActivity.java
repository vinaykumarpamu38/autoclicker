package com.techy_pvk.automate.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.StepType;
import com.techy_pvk.automate.service.FloatingPickerService;
import com.techy_pvk.automate.ui.adapter.StepAdapter;
import com.techy_pvk.automate.ui.dialog.StepDialogs;
import com.techy_pvk.automate.ui.dialog.TriggerDialogs;
import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.AppPickerRequest;
import com.techy_pvk.automate.viewmodel.SequenceBuilderViewModel;

/**
 * Builds or edits one sequence. This class only draws {@link SequenceBuilderViewModel}'s state,
 * forwards input to it, and opens the dialogs it asks for. Loading, validation, saving and the
 * draft that survives the Tap / Swipe picker all live in the ViewModel.
 */
public class SequenceBuilderActivity extends AppCompatActivity {

    /** Intent extra naming the sequence to edit; leave out to create a new one. */
    public static final String EXTRA_SEQUENCE_ID = SequenceBuilderViewModel.KEY_SEQUENCE_ID;

    private SequenceBuilderViewModel viewModel;
    private final DialogHolder dialogs = new DialogHolder();

    /** Open Location dialog, so an address lookup that finishes later can fill it in. */
    @Nullable
    private TriggerDialogs.LocationDialog locationDialog;

    private EditText nameInput;
    private EditText repeatInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sequence_builder);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));

        // The default factory copies this Intent's extras (EXTRA_SEQUENCE_ID) into the ViewModel's
        // SavedStateHandle, which is how it knows whether to create or edit.
        viewModel = new ViewModelProvider(this).get(SequenceBuilderViewModel.class);

        nameInput = findViewById(R.id.input_sequence_name);
        repeatInput = findViewById(R.id.input_repeat_count);
        View noStepsLabel = findViewById(R.id.text_no_steps);
        TextView triggerSummary = findViewById(R.id.text_trigger_summary);

        RecyclerView stepsRecycler = findViewById(R.id.recycler_steps);
        stepsRecycler.setLayoutManager(new LinearLayoutManager(this));
        StepAdapter stepAdapter = new StepAdapter(new StepAdapter.Listener() {
            @Override public void onRemove(int position) { viewModel.removeStep(position); }
            @Override public void onMove(int position, int direction) { viewModel.moveStep(position, direction); }
        });
        stepsRecycler.setAdapter(stepAdapter);

        findViewById(R.id.button_add_step).setOnClickListener(v ->
                dialogs.track(StepDialogs.showChooser(this, this::onStepTypeChosen)));
        findViewById(R.id.button_set_trigger).setOnClickListener(v ->
                dialogs.track(TriggerDialogs.showChooser(this, this::onTriggerTypeChosen)));
        findViewById(R.id.button_save_sequence).setOnClickListener(v -> viewModel.save());

        nameInput.addTextChangedListener(new SimpleTextWatcher(viewModel::onNameChanged));
        repeatInput.addTextChangedListener(new SimpleTextWatcher(viewModel::onRepeatChanged));

        // ---- State ----
        viewModel.getName().observe(this, text -> setTextIfDifferent(nameInput, text));
        viewModel.getRepeatText().observe(this, text -> setTextIfDifferent(repeatInput, text));
        viewModel.getSteps().observe(this, steps -> {
            stepAdapter.submitList(steps);
            noStepsLabel.setVisibility(steps == null || steps.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getTriggerSummary().observe(this, triggerSummary::setText);

        // ---- One-shot events ----
        viewModel.getMessage().observe(this, new EventObserver<>(message -> ToastUtil.show(this, message)));
        viewModel.getNameError().observe(this, new EventObserver<>(error -> {
            nameInput.setError(error);
            nameInput.requestFocus();
        }));
        viewModel.getMinimizeApp().observe(this, new EventObserver<>(ignored -> moveTaskToBack(true)));
        viewModel.getFinishScreen().observe(this, new EventObserver<>(ignored -> finish()));
        viewModel.getAppPicker().observe(this, new EventObserver<>(this::onAppPickerReady));
        viewModel.getGeocodeResult().observe(this, new EventObserver<>(result -> {
            if (locationDialog != null) locationDialog.showLookupResult(result);
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the floating picker: pick up the point it saved, if any.
        viewModel.consumePendingPickedPoint();
    }

    @Override
    protected void onStop() {
        if (isFinishing()) {
            viewModel.onScreenAbandoned();
        } else {
            // Backgrounded (picker, another app, ...): keep the half-built sequence safe in case
            // Android reclaims this process while we're away.
            viewModel.persistDraftIfDirty();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        dialogs.dismiss();
        locationDialog = null;
        super.onDestroy();
    }

    // =====================================================================================
    // Step chooser. The index order is defined by StepDialogs.STEP_TYPE_LABELS.
    // =====================================================================================

    private void onStepTypeChosen(int index) {
        switch (index) {
            case 0: viewModel.startPicker(FloatingPickerService.MODE_TAP); break;
            case 1: viewModel.startPicker(FloatingPickerService.MODE_LONG_CLICK); break;
            case 2: viewModel.startPicker(FloatingPickerService.MODE_SWIPE); break;
            case 3: dialogs.track(StepDialogs.showScroll(this, viewModel::addStep)); break;
            case 4: dialogs.track(StepDialogs.showWait(this, viewModel::addStep)); break;
            case 5: viewModel.requestAppPicker(AppPickerRequest.Purpose.OPEN_APP_STEP); break;
            case 6:
                dialogs.track(StepDialogs.showClickBy(this, StepType.CLICK_TEXT, "Click by Text",
                        "Text shown on the button/label (e.g. \"Next\")", viewModel::addStep));
                break;
            case 7:
                dialogs.track(StepDialogs.showClickBy(this, StepType.CLICK_ID, "Click by Resource ID",
                        "Resource id, e.g. \"button_ok\" (not sure? use Inspect Screen on the main screen)",
                        viewModel::addStep));
                break;
            case 8:
                dialogs.track(StepDialogs.showClickBy(this, StepType.CLICK_DESC, "Click by Content Description",
                        "Accessibility content-description text", viewModel::addStep));
                break;
            case 9: dialogs.track(StepDialogs.showTypeText(this, viewModel::addStep)); break;
            case 10: viewModel.addStep(StepDialogs.simpleGlobalStep(StepType.BACK)); break;
            case 11: viewModel.addStep(StepDialogs.simpleGlobalStep(StepType.HOME)); break;
            case 12: viewModel.addStep(StepDialogs.simpleGlobalStep(StepType.RECENTS)); break;
            case 13: dialogs.track(StepDialogs.showRingerMode(this, viewModel::addStep)); break;
            case 14: dialogs.track(StepDialogs.showDnd(this, viewModel::addStep)); break;
            case 15: dialogs.track(StepDialogs.showBrightness(this, viewModel::addStep)); break;
            default: break;
        }
    }

    // =====================================================================================
    // Trigger chooser. The index order is defined by TriggerDialogs.TRIGGER_TYPE_LABELS.
    // =====================================================================================

    private void onTriggerTypeChosen(int index) {
        switch (index) {
            case 0: viewModel.setTrigger(TriggerDialogs.manual(), false, null); break;
            case 1:
                dialogs.track(TriggerDialogs.showBluetooth(this, viewModel.getCurrentTrigger(), onTriggerChosen));
                break;
            case 2: viewModel.requestAppPicker(AppPickerRequest.Purpose.NOTIFICATION_TRIGGER); break;
            case 3: {
                locationDialog = TriggerDialogs.showLocation(this, viewModel.getCurrentTrigger(),
                        viewModel.getCurrentAutoRestore(), viewModel::lookupAddress, onTriggerChosen);
                dialogs.track(locationDialog.getDialog());
                break;
            }
            case 4:
                dialogs.track(TriggerDialogs.showWifi(this, viewModel.getCurrentTrigger(),
                        viewModel::readCurrentWifiSsid, onTriggerChosen));
                break;
            case 5:
                dialogs.track(TriggerDialogs.showScheduled(this, viewModel.getCurrentTrigger(), onTriggerChosen));
                break;
            case 6: viewModel.setTrigger(TriggerDialogs.bootCompleted(), false, null); break;
            case 7:
                dialogs.track(TriggerDialogs.showBattery(this, viewModel.getCurrentTrigger(),
                        viewModel.getCurrentAutoRestore(), onTriggerChosen));
                break;
            case 8: viewModel.setTrigger(TriggerDialogs.wiredHeadset(), false, null); break;
            case 9: viewModel.requestAppPicker(AppPickerRequest.Purpose.MUSIC_TRIGGER); break;
            case 10:
                dialogs.track(TriggerDialogs.showPowerSave(this, viewModel.getCurrentTrigger(), onTriggerChosen));
                break;
            default: break;
        }
    }

    private final TriggerDialogs.OnTriggerChosen onTriggerChosen =
            (trigger, autoRestore, suggestedName) -> viewModel.setTrigger(trigger, autoRestore, suggestedName);

    /** The ViewModel loaded the installed-app list; open whichever dialog asked for it. */
    private void onAppPickerReady(AppPickerRequest request) {
        switch (request.purpose) {
            case OPEN_APP_STEP:
                dialogs.track(StepDialogs.showLaunchApp(this, request.apps, viewModel::addStep));
                break;
            case NOTIFICATION_TRIGGER:
                dialogs.track(TriggerDialogs.showNotification(this, request.apps,
                        viewModel.getCurrentTrigger(), onTriggerChosen));
                break;
            case MUSIC_TRIGGER:
                dialogs.track(TriggerDialogs.showMusicPlayback(this, request.apps,
                        viewModel.getCurrentTrigger(), viewModel.getCurrentAutoRestore(), onTriggerChosen));
                break;
        }
    }

    // =====================================================================================
    // Small helpers
    // =====================================================================================

    /** Avoids resetting the cursor (and re-triggering the TextWatcher) when the text already matches. */
    private static void setTextIfDifferent(EditText input, @Nullable String text) {
        String next = text != null ? text : "";
        if (!next.contentEquals(input.getText())) {
            input.setText(next);
            input.setSelection(next.length());
        }
    }

    /** TextWatcher that only cares about the final text. */
    private static final class SimpleTextWatcher implements TextWatcher {
        private final java.util.function.Consumer<String> onChanged;

        SimpleTextWatcher(java.util.function.Consumer<String> onChanged) {
            this.onChanged = onChanged;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
        @Override public void afterTextChanged(Editable s) { onChanged.accept(s.toString()); }
    }
}
