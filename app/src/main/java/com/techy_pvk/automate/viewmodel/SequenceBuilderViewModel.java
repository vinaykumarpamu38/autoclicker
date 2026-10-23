package com.techy_pvk.automate.viewmodel;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.repository.DraftPolicy;
import com.techy_pvk.automate.repository.DraftRepository;
import com.techy_pvk.automate.repository.GeocodeResult;
import com.techy_pvk.automate.repository.GeocodingRepository;
import com.techy_pvk.automate.repository.InstalledAppsRepository;
import com.techy_pvk.automate.repository.PickerRepository;
import com.techy_pvk.automate.repository.SequenceRepository;
import com.techy_pvk.automate.repository.WifiRepository;
import com.techy_pvk.automate.service.FloatingPickerService;
import com.techy_pvk.automate.service.SequenceRunner;
import com.techy_pvk.automate.util.AppExecutors;
import com.techy_pvk.automate.util.Event;
import com.techy_pvk.automate.util.InputParsers;
import com.techy_pvk.automate.util.UiMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything the Sequence Builder screen knows and decides: the form fields, the step list, the
 * trigger, validation, saving, the draft that survives the picker round-trip, and the async
 * lookups its dialogs need. SequenceBuilderActivity just renders this and forwards input.
 *
 * Threading rule: every LiveData here is read and written on the main thread only. Background
 * work (draft I/O, database) is handed to AppExecutors.diskIO(), and its results hop back to the
 * main thread before touching state.
 */
public class SequenceBuilderViewModel extends AndroidViewModel {

    /** Intent extra naming the sequence to edit; absent for "new sequence". The default
     *  SavedStateViewModelFactory copies the launching Intent's extras into the SavedStateHandle. */
    public static final String KEY_SEQUENCE_ID = "sequence_id";

    private final SequenceRepository sequenceRepository;
    private final DraftRepository draftRepository;
    private final PickerRepository pickerRepository;
    private final InstalledAppsRepository installedAppsRepository;
    private final GeocodingRepository geocodingRepository;
    private final WifiRepository wifiRepository;
    private final AppExecutors executors;

    // ---- Form state ----
    private final MutableLiveData<String> name = new MutableLiveData<>("");
    private final MutableLiveData<String> repeatText = new MutableLiveData<>("1");
    private final MutableLiveData<List<GestureStep>> steps = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<SequenceTrigger> trigger = new MutableLiveData<>(new SequenceTrigger());
    private final MutableLiveData<Boolean> autoRestore = new MutableLiveData<>(false);
    private final MediatorLiveData<String> triggerSummary = new MediatorLiveData<>();

    // ---- One-shot events ----
    private final MutableLiveData<Event<UiMessage>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> nameError = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> finishScreen = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> minimizeApp = new MutableLiveData<>();
    private final MutableLiveData<Event<AppPickerRequest>> appPicker = new MutableLiveData<>();
    private final MutableLiveData<Event<GeocodeResult>> geocodeResult = new MutableLiveData<>();

    // ---- Plain state ----
    private String sequenceId;
    private boolean loaded;
    private boolean consumePickedPointWhenLoaded;
    private boolean dirty;
    private boolean saving;
    private boolean saved;
    private boolean abandoned;

    /** Used by the default ViewModelProvider factory. */
    public SequenceBuilderViewModel(@NonNull Application application, @NonNull SavedStateHandle savedState) {
        this(application, savedState,
                SequenceRepository.getInstance(application),
                new DraftRepository(application),
                new PickerRepository(application),
                InstalledAppsRepository.getInstance(application),
                GeocodingRepository.getInstance(application),
                new WifiRepository(application),
                AppExecutors.getInstance());
    }

    /** Dependency-injecting constructor, for tests. */
    SequenceBuilderViewModel(@NonNull Application application,
                             @NonNull SavedStateHandle savedState,
                             @NonNull SequenceRepository sequenceRepository,
                             @NonNull DraftRepository draftRepository,
                             @NonNull PickerRepository pickerRepository,
                             @NonNull InstalledAppsRepository installedAppsRepository,
                             @NonNull GeocodingRepository geocodingRepository,
                             @NonNull WifiRepository wifiRepository,
                             @NonNull AppExecutors executors) {
        super(application);
        this.sequenceRepository = sequenceRepository;
        this.draftRepository = draftRepository;
        this.pickerRepository = pickerRepository;
        this.installedAppsRepository = installedAppsRepository;
        this.geocodingRepository = geocodingRepository;
        this.wifiRepository = wifiRepository;
        this.executors = executors;

        triggerSummary.addSource(trigger, t -> refreshTriggerSummary());
        triggerSummary.addSource(autoRestore, r -> refreshTriggerSummary());
        refreshTriggerSummary();

        String requestedId = savedState.get(KEY_SEQUENCE_ID);
        sequenceId = requestedId != null ? requestedId : UUID.randomUUID().toString();
        loadInitialState(requestedId);
    }

    // =====================================================================================
    // State the screen renders
    // =====================================================================================

    public LiveData<String> getName() { return name; }
    public LiveData<String> getRepeatText() { return repeatText; }
    public LiveData<List<GestureStep>> getSteps() { return steps; }
    public LiveData<String> getTriggerSummary() { return triggerSummary; }

    /** The trigger as it stands right now - used to pre-fill the trigger dialogs when editing. */
    @NonNull
    public SequenceTrigger getCurrentTrigger() {
        SequenceTrigger current = trigger.getValue();
        return current != null ? current : new SequenceTrigger();
    }

    public boolean getCurrentAutoRestore() {
        return Boolean.TRUE.equals(autoRestore.getValue());
    }

    public LiveData<Event<UiMessage>> getMessage() { return message; }
    public LiveData<Event<String>> getNameError() { return nameError; }
    public LiveData<Event<Boolean>> getFinishScreen() { return finishScreen; }
    public LiveData<Event<Boolean>> getMinimizeApp() { return minimizeApp; }
    public LiveData<Event<AppPickerRequest>> getAppPicker() { return appPicker; }
    public LiveData<Event<GeocodeResult>> getGeocodeResult() { return geocodeResult; }

    // =====================================================================================
    // Loading
    // =====================================================================================

    private void loadInitialState(@Nullable String requestedId) {
        executors.diskIO().execute(() -> {
            DraftRepository.Draft draft = draftRepository.load();
            boolean useDraft = draft != null
                    && DraftPolicy.belongsToRequest(draft.id, draft.savedAt, requestedId, System.currentTimeMillis());
            // A draft that isn't for what was asked for is a leftover from an old, never-saved
            // session. Clear it so it can't keep hijacking future edit / new-sequence requests.
            if (draft != null && !useDraft) draftRepository.clear();

            executors.mainThread().execute(() -> {
                if (useDraft) {
                    // Resuming an in-progress builder session (e.g. back from the picker).
                    applyLoadedState(draft.id, draft.name, draft.repeatText,
                            draft.steps, draft.trigger, draft.autoRestore);
                    dirty = true; // it came from unsaved work - keep persisting it
                } else if (requestedId != null) {
                    sequenceRepository.getById(requestedId, existing -> {
                        if (existing != null) {
                            applyLoadedState(existing.id, existing.name, String.valueOf(existing.repeatCount),
                                    existing.steps, existing.trigger, existing.autoRestore);
                        } else {
                            applyLoadedState(UUID.randomUUID().toString(), "", "1", null, null, false);
                        }
                    });
                } else {
                    applyLoadedState(sequenceId, "", "1", null, null, false);
                }
            });
        });
    }

    @MainThread
    private void applyLoadedState(String id, String loadedName, String loadedRepeat,
                                  @Nullable List<GestureStep> loadedSteps,
                                  @Nullable SequenceTrigger loadedTrigger, boolean loadedAutoRestore) {
        sequenceId = id != null ? id : UUID.randomUUID().toString();
        name.setValue(loadedName != null ? loadedName : "");
        repeatText.setValue(loadedRepeat != null ? loadedRepeat : "1");
        steps.setValue(loadedSteps != null ? new ArrayList<>(loadedSteps) : new ArrayList<>());
        SequenceTrigger t = loadedTrigger != null ? loadedTrigger : new SequenceTrigger();
        trigger.setValue(t);
        autoRestore.setValue(loadedAutoRestore && t.supportsAutoRestore());

        loaded = true;
        if (consumePickedPointWhenLoaded) {
            consumePickedPointWhenLoaded = false;
            consumePendingPickedPoint();
        }
    }

    // =====================================================================================
    // Form input
    // =====================================================================================

    @MainThread
    public void onNameChanged(@Nullable String text) {
        String value = text != null ? text : "";
        if (value.equals(name.getValue())) return; // also stops the observer<->TextWatcher echo
        name.setValue(value);
        dirty = true;
    }

    @MainThread
    public void onRepeatChanged(@Nullable String text) {
        String value = text != null ? text : "";
        if (value.equals(repeatText.getValue())) return;
        repeatText.setValue(value);
        dirty = true;
    }

    // =====================================================================================
    // Steps
    // =====================================================================================

    @MainThread
    public void addStep(@NonNull GestureStep step) {
        List<GestureStep> next = new ArrayList<>(currentSteps());
        next.add(step);
        steps.setValue(next);
        dirty = true;
        persistDraft(false);
    }

    @MainThread
    public void removeStep(int position) {
        List<GestureStep> next = new ArrayList<>(currentSteps());
        if (position < 0 || position >= next.size()) return;
        next.remove(position);
        steps.setValue(next);
        dirty = true;
        persistDraft(false);
    }

    /** @param direction -1 to move up, +1 to move down. */
    @MainThread
    public void moveStep(int position, int direction) {
        List<GestureStep> next = new ArrayList<>(currentSteps());
        int target = position + direction;
        if (position < 0 || position >= next.size() || target < 0 || target >= next.size()) return;
        GestureStep moved = next.get(position);
        next.set(position, next.get(target));
        next.set(target, moved);
        steps.setValue(next);
        dirty = true;
        persistDraft(false);
    }

    @NonNull
    private List<GestureStep> currentSteps() {
        List<GestureStep> current = steps.getValue();
        return current != null ? current : new ArrayList<>();
    }

    // =====================================================================================
    // Trigger
    // =====================================================================================

    /**
     * @param wantsAutoRestore what the user ticked; only honoured if the trigger can actually
     *                         auto-restore (the single rule lives in SequenceTrigger).
     * @param suggestedName    a plainly-worded name to use if the name field is still blank, or
     *                         null. Auto-restore sequences are easy to lose track of later
     *                         ("what was this vibrate-mode thing for?").
     */
    @MainThread
    public void setTrigger(@NonNull SequenceTrigger newTrigger, boolean wantsAutoRestore,
                           @Nullable String suggestedName) {
        trigger.setValue(newTrigger);
        autoRestore.setValue(wantsAutoRestore && newTrigger.supportsAutoRestore());
        if (suggestedName != null && isBlank(name.getValue())) {
            name.setValue(suggestedName);
        }
        dirty = true;
        persistDraft(false);
    }

    private void refreshTriggerSummary() {
        triggerSummary.setValue(TriggerSummaryFormatter.format(getCurrentTrigger(), getCurrentAutoRestore()));
    }

    // =====================================================================================
    // Picker round-trip (Tap / Long Click / Swipe)
    // =====================================================================================

    /** Hands off to the floating picker overlay, which needs this app out of the way. */
    @MainThread
    public void startPicker(@NonNull String mode) {
        dirty = true;
        persistDraft(true); // the app is about to go to the background and may be reclaimed there
        SequenceRunner.startPicker(getApplication(), mode);
        message.setValue(new Event<>(UiMessage.longMessage(
                "Position the marker" + (FloatingPickerService.MODE_SWIPE.equals(mode) ? "s" : "")
                        + ", then tap Save on the floating panel")));
        minimizeApp.setValue(new Event<>(Boolean.TRUE));
    }

    /** Call when the screen returns to the foreground: collects a point the picker saved. */
    @MainThread
    public void consumePendingPickedPoint() {
        if (!loaded) {
            // The initial load (which may replace the step list) hasn't finished - applying a
            // picked point now would just be overwritten by it. Do it as soon as loading completes.
            consumePickedPointWhenLoaded = true;
            return;
        }
        executors.diskIO().execute(() -> {
            GestureStep picked = pickerRepository.consumePendingStep();
            if (picked != null) executors.mainThread().execute(() -> addStep(picked));
        });
    }

    // =====================================================================================
    // Dialog support
    // =====================================================================================

    /** Loads installed apps off the main thread, then asks the screen to open the dialog that needs them. */
    @MainThread
    public void requestAppPicker(@NonNull AppPickerRequest.Purpose purpose) {
        installedAppsRepository.load(apps ->
                appPicker.setValue(new Event<>(new AppPickerRequest(purpose, apps))));
    }

    @MainThread
    public void lookupAddress(@Nullable String address) {
        String trimmed = address == null ? "" : address.trim();
        if (trimmed.isEmpty()) {
            message.setValue(new Event<>(UiMessage.shortMessage("Enter an address first")));
            return;
        }
        geocodingRepository.lookup(trimmed, result -> geocodeResult.setValue(new Event<>(result)));
    }

    /** Name of the Wi-Fi network the phone is on right now, or null. */
    @Nullable
    public String readCurrentWifiSsid() {
        return wifiRepository.currentSsid();
    }

    // =====================================================================================
    // Save / draft
    // =====================================================================================

    @MainThread
    public void save() {
        if (!loaded || saving) return;

        String trimmedName = name.getValue() == null ? "" : name.getValue().trim();
        if (trimmedName.isEmpty()) {
            nameError.setValue(new Event<>("Name this sequence"));
            return;
        }
        List<GestureStep> currentSteps = currentSteps();
        if (currentSteps.isEmpty()) {
            message.setValue(new Event<>(UiMessage.shortMessage("Add at least one step")));
            return;
        }

        SequenceTrigger currentTrigger = getCurrentTrigger();
        AutomationSequence sequence = new AutomationSequence();
        sequence.id = sequenceId;
        sequence.name = trimmedName;
        sequence.steps = new ArrayList<>(currentSteps);
        sequence.repeatCount = InputParsers.parseRepeatCount(repeatText.getValue());
        sequence.trigger = currentTrigger;
        sequence.autoRestore = getCurrentAutoRestore() && currentTrigger.supportsAutoRestore();

        saving = true;
        sequenceRepository.save(sequence, () -> {
            saved = true;
            saving = false;
            // Anything queued before this ran first (single-threaded executor), and persistDraft()
            // refuses to run once saved == true, so nothing can resurrect the draft after this.
            executors.diskIO().execute(draftRepository::clear);
            message.setValue(new Event<>(UiMessage.shortMessage("Saved \"" + trimmedName + "\"")));
            finishScreen.setValue(new Event<>(Boolean.TRUE));
        });
    }

    /** Call when the screen stops, so a half-built sequence (including a typed name) survives
     *  the app being killed in the background. Does nothing for an untouched screen. */
    @MainThread
    public void persistDraftIfDirty() {
        persistDraft(false);
    }

    /** The user left the screen for good (Back / Up) without saving. Drop the draft so it can't
     *  reappear the next time they tap "+" - drafts exist only to survive the picker round-trip. */
    @MainThread
    public void onScreenAbandoned() {
        if (saved) return;
        abandoned = true;
        executors.diskIO().execute(draftRepository::clear);
    }

    private void persistDraft(boolean force) {
        if (!loaded || saved || abandoned) return;
        if (!force && !dirty) return;

        DraftRepository.Draft draft = new DraftRepository.Draft(
                sequenceId,
                name.getValue() != null ? name.getValue() : "",
                repeatText.getValue() != null ? repeatText.getValue() : "1",
                new ArrayList<>(currentSteps()),
                getCurrentTrigger(),
                getCurrentAutoRestore(),
                System.currentTimeMillis());
        executors.diskIO().execute(() -> draftRepository.save(draft));
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
