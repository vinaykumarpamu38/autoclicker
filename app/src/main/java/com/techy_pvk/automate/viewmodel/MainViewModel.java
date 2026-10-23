package com.techy_pvk.automate.viewmodel;

import android.app.Application;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.techy_pvk.automate.model.AppInfo;
import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;
import com.techy_pvk.automate.repository.InstalledAppsRepository;
import com.techy_pvk.automate.repository.PermissionRepository;
import com.techy_pvk.automate.repository.ProfileRepository;
import com.techy_pvk.automate.repository.SequenceRepository;
import com.techy_pvk.automate.service.SequenceRunner;
import com.techy_pvk.automate.util.Event;
import com.techy_pvk.automate.util.LiveDataUtil;
import com.techy_pvk.automate.util.UiMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * State and decisions for the home screen: the sequence list, the drawer greeting, and the
 * "you're missing a permission" prompts. MainActivity only renders what is exposed here and
 * forwards taps - it never touches the database, SharedPreferences or PermissionManager itself.
 */
public class MainViewModel extends AndroidViewModel {

    private final SequenceRepository sequenceRepository;
    private final InstalledAppsRepository installedAppsRepository;
    private final PermissionRepository permissionRepository;

    private final MediatorLiveData<List<AutomationSequence>> sequences = new MediatorLiveData<>();
    private final LiveData<Boolean> showEmptyState;
    private final LiveData<String> greeting;

    private final MutableLiveData<Event<UiMessage>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> promptAccessibility = new MutableLiveData<>();
    /** Payload = whether the "Allow all the time" background step is also needed. */
    private final MutableLiveData<Event<Boolean>> promptLocation = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> minimizeApp = new MutableLiveData<>();
    private final MutableLiveData<Event<Intent>> shareChooser = new MutableLiveData<>();
    private final MutableLiveData<Event<List<AppInfo>>> inspectDialogApps = new MutableLiveData<>();
    private final MutableLiveData<Event<String[]>> requestRuntimePermissions = new MutableLiveData<>();

    private List<AutomationSequence> latestSequences;
    private boolean accessibilityPromptShown;
    private boolean locationPromptShown;
    private boolean accessibilityPromptEmittedThisResume;

    /** Used by the default ViewModelProvider factory. */
    public MainViewModel(@NonNull Application application) {
        this(application,
                SequenceRepository.getInstance(application),
                ProfileRepository.getInstance(application),
                InstalledAppsRepository.getInstance(application),
                new PermissionRepository(application));
    }

    /** Dependency-injecting constructor, for tests. */
    MainViewModel(@NonNull Application application,
                  @NonNull SequenceRepository sequenceRepository,
                  @NonNull ProfileRepository profileRepository,
                  @NonNull InstalledAppsRepository installedAppsRepository,
                  @NonNull PermissionRepository permissionRepository) {
        super(application);
        this.sequenceRepository = sequenceRepository;
        this.installedAppsRepository = installedAppsRepository;
        this.permissionRepository = permissionRepository;

        sequences.addSource(sequenceRepository.observeAll(), list -> {
            latestSequences = list;
            sequences.setValue(list);
            evaluateLocationPrompt();
        });

        // null (still loading) must not show "no sequences yet" - only a loaded, empty list does.
        showEmptyState = LiveDataUtil.map(sequences, list -> list != null && list.isEmpty());

        greeting = LiveDataUtil.map(profileRepository.observe(), profile ->
                profile != null && profile.hasName() ? "Hi, " + profile.displayName.trim() : "Welcome");

        // Asked once per ViewModel, so rotating the phone doesn't re-prompt.
        List<String> missing = permissionRepository.missingRuntimePermissions();
        if (!missing.isEmpty()) {
            requestRuntimePermissions.setValue(new Event<>(missing.toArray(new String[0])));
        }
    }

    // ---- State the screen renders ----

    public LiveData<List<AutomationSequence>> getSequences() { return sequences; }
    public LiveData<Boolean> getShowEmptyState() { return showEmptyState; }
    public LiveData<String> getGreeting() { return greeting; }

    // ---- One-shot events the screen reacts to ----

    public LiveData<Event<UiMessage>> getMessage() { return message; }
    public LiveData<Event<Boolean>> getPromptAccessibility() { return promptAccessibility; }
    public LiveData<Event<Boolean>> getPromptLocation() { return promptLocation; }
    public LiveData<Event<Boolean>> getMinimizeApp() { return minimizeApp; }
    public LiveData<Event<Intent>> getShareChooser() { return shareChooser; }
    public LiveData<Event<List<AppInfo>>> getInspectDialogApps() { return inspectDialogApps; }
    public LiveData<Event<String[]>> getRequestRuntimePermissions() { return requestRuntimePermissions; }

    // ---- Lifecycle ----

    /** Called every time the home screen comes to the foreground (including returning from
     *  system Settings after granting something). */
    public void onScreenResumed() {
        accessibilityPromptEmittedThisResume = false;

        // Re-sync shortcuts / geofences / alarms. Beyond keeping them current, this is what picks
        // up a location permission the user only just granted: geofences can't register without it.
        sequenceRepository.syncSystemRegistrations();

        // Without the Accessibility service nothing runs at all, so that is the more important
        // prompt: say so once per launch (not on every return from Settings). Dismissing the
        // dialog is never treated as consent - "Enable" only opens the disclosure screen, which
        // is where the real Allow / Don't Allow choice lives.
        if (!accessibilityPromptShown && !permissionRepository.isAccessibilityServiceEnabled()) {
            accessibilityPromptShown = true;
            accessibilityPromptEmittedThisResume = true;
            promptAccessibility.setValue(new Event<>(Boolean.TRUE));
        }

        evaluateLocationPrompt();
    }

    // ---- Location prompt ----

    private void evaluateLocationPrompt() {
        if (locationPromptShown || latestSequences == null) return;
        // Never stack two dialogs: if the Accessibility prompt just went up, the location one
        // waits for the next resume (e.g. coming back from Accessibility Settings).
        if (accessibilityPromptEmittedThisResume) return;

        boolean needsBackground = false;
        boolean needsAnyLocation = false;
        for (AutomationSequence sequence : latestSequences) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type == null) continue;
            if (trigger.type == TriggerType.LOCATION) {
                needsAnyLocation = true;
                needsBackground = true; // a geofence has to keep working while the app is closed
            } else if (trigger.type == TriggerType.WIFI_CONNECTED
                    && trigger.wifiSsidFilter != null && !trigger.wifiSsidFilter.isEmpty()) {
                needsAnyLocation = true; // Android hides the network name from apps without location access
            }
        }
        if (!needsAnyLocation) return;

        boolean foregroundOk = permissionRepository.hasForegroundLocation();
        boolean backgroundOk = !needsBackground || permissionRepository.hasBackgroundLocation();
        if (foregroundOk && backgroundOk) return;

        locationPromptShown = true;
        promptLocation.setValue(new Event<>(needsBackground));
    }

    // ---- User actions ----

    public void onRunClicked(@NonNull AutomationSequence sequence) {
        // No overlay-permission check here on purpose: running a sequence is entirely the
        // Accessibility service's job. The overlay is only needed to *build* tap/swipe steps.
        SequenceRunner.run(getApplication(), sequence.id);
        message.setValue(new Event<>(UiMessage.shortMessage("Running \"" + sequence.name + "\"\u2026")));
        // Minimize immediately: the accessibility service dispatches taps/swipes onto whatever
        // app is underneath, so open your target app before tapping Run.
        minimizeApp.setValue(new Event<>(Boolean.TRUE));
    }

    public void onShareClicked(@NonNull AutomationSequence sequence) {
        sequenceRepository.createShareIntent(sequence, intent -> {
            if (intent == null) {
                message.setValue(new Event<>(UiMessage.shortMessage("Couldn't create the share file")));
            } else {
                shareChooser.setValue(new Event<>(intent));
            }
        });
    }

    public void deleteSequence(@NonNull AutomationSequence sequence) {
        sequenceRepository.delete(sequence.id, null);
    }

    // ---- Inspect Screen ----

    /** Step 1: fetch the app list, then tell the screen to show the picker dialog. */
    public void onInspectScreenRequested() {
        installedAppsRepository.load(installed -> {
            List<AppInfo> apps = new ArrayList<>(installed.size() + 1);
            // Empty package name = "whatever is already in the foreground".
            apps.add(new AppInfo("Currently foreground app", ""));
            apps.addAll(installed);
            inspectDialogApps.setValue(new Event<>(apps));
        });
    }

    /** Step 2: the user picked an app + mode. */
    public void startInspection(AppInfo app, boolean pointMode) {
        if (pointMode && !permissionRepository.hasOverlay()) {
            message.setValue(new Event<>(UiMessage.shortMessage("Grant overlay permission first (Settings)")));
            return;
        }

        if (app != null && app.packageName != null && !app.packageName.isEmpty()) {
            Intent launchIntent = getApplication().getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launchIntent == null) {
                message.setValue(new Event<>(UiMessage.shortMessage("Couldn't open " + app.label)));
                return;
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getApplication().startActivity(launchIntent);
        }

        if (pointMode) {
            SequenceRunner.startPointInspection(getApplication());
            message.setValue(new Event<>(UiMessage.longMessage(
                    "Drag the marker over what you want to inspect, then tap Inspect")));
        } else {
            SequenceRunner.inspectWholeScreen(getApplication());
            message.setValue(new Event<>(UiMessage.shortMessage("Reading the screen\u2026")));
        }

        // Minimize so whatever app should be inspected - freshly launched above, or whatever was
        // already open - is what's in the foreground for the picker / AutomationService.
        minimizeApp.setValue(new Event<>(Boolean.TRUE));
    }
}
