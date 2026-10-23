package com.techy_pvk.automate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.techy_pvk.automate.repository.PermissionRepository;
import com.techy_pvk.automate.repository.PermissionState;
import com.techy_pvk.automate.repository.SettingsRepository;
import com.techy_pvk.automate.util.Event;
import com.techy_pvk.automate.util.UiMessage;

/**
 * The Settings screen's brain: which permissions are granted, what each row's button should do
 * ("already granted" toast vs. open the right system screen), and the Payment Guard switch.
 * Every "already granted" message and the "grant location first" ordering rule lives here.
 */
public class SettingsViewModel extends AndroidViewModel {

    private final PermissionRepository permissionRepository;
    private final SettingsRepository settingsRepository;

    private final MutableLiveData<PermissionState> permissions = new MutableLiveData<>();
    private final MutableLiveData<Boolean> paymentGuardEnabled = new MutableLiveData<>();
    private final MutableLiveData<Event<UiMessage>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<SettingsAction>> action = new MutableLiveData<>();

    /** Used by the default ViewModelProvider factory. */
    public SettingsViewModel(@NonNull Application application) {
        this(application, new PermissionRepository(application), SettingsRepository.getInstance(application));
    }

    /** Dependency-injecting constructor, for tests. */
    SettingsViewModel(@NonNull Application application,
                      @NonNull PermissionRepository permissionRepository,
                      @NonNull SettingsRepository settingsRepository) {
        super(application);
        this.permissionRepository = permissionRepository;
        this.settingsRepository = settingsRepository;

        refresh();
        // If the user already flipped the switch before the stored value arrived, theirs wins.
        settingsRepository.loadPaymentGuardEnabled(stored -> {
            if (paymentGuardEnabled.getValue() == null) paymentGuardEnabled.setValue(stored);
        });
    }

    public LiveData<PermissionState> getPermissions() { return permissions; }
    public LiveData<Boolean> getPaymentGuardEnabled() { return paymentGuardEnabled; }
    public LiveData<Event<UiMessage>> getMessage() { return message; }
    public LiveData<Event<SettingsAction>> getAction() { return action; }

    /** Re-reads every grant. Call from onResume: the user changes them in system screens we launched. */
    public void refresh() {
        permissions.setValue(permissionRepository.snapshot());
    }

    // ---- Row taps ----

    public void onOverlayClicked() {
        if (permissionRepository.hasOverlay()) {
            tell("Overlay permission already granted");
        } else {
            action.setValue(new Event<>(SettingsAction.OPEN_OVERLAY_SETTINGS));
        }
    }

    public void onAccessibilityClicked() {
        // Always goes through the disclosure screen, granted or not - it's also the way back to
        // the system screen where the user can switch the service off.
        action.setValue(new Event<>(SettingsAction.OPEN_ACCESSIBILITY_DISCLOSURE));
    }

    public void onNotificationAccessClicked() {
        if (permissionRepository.snapshot().notificationListener) {
            tell("Notification access already granted");
        } else {
            action.setValue(new Event<>(SettingsAction.OPEN_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    public void onDoNotDisturbClicked() {
        if (permissionRepository.snapshot().doNotDisturb) {
            tell("Do Not Disturb access already granted");
        } else {
            action.setValue(new Event<>(SettingsAction.OPEN_DND_SETTINGS));
        }
    }

    public void onWriteSettingsClicked() {
        if (permissionRepository.snapshot().writeSettings) {
            tell("Modify System Settings already granted");
        } else {
            action.setValue(new Event<>(SettingsAction.OPEN_WRITE_SETTINGS));
        }
    }

    public void onLocationClicked() {
        if (permissionRepository.hasForegroundLocation()) {
            tell("Location access already granted");
        } else {
            // Never request location straight from a button: Google Play requires a prominent
            // disclosure + explicit consent first, so this always goes via the disclosure screen.
            action.setValue(new Event<>(SettingsAction.OPEN_LOCATION_DISCLOSURE));
        }
    }

    public void onBackgroundLocationClicked() {
        PermissionState state = permissionRepository.snapshot();
        if (!state.backgroundLocationApplies) {
            tell("Not needed on this Android version");
        } else if (state.backgroundLocation) {
            tell("Background location already granted");
        } else if (!state.foregroundLocation) {
            tell("Grant Location Access first (step 1)");
        } else {
            action.setValue(new Event<>(SettingsAction.OPEN_BACKGROUND_LOCATION_DISCLOSURE));
        }
    }

    public void onExactAlarmClicked() {
        PermissionState state = permissionRepository.snapshot();
        if (!state.exactAlarmApplies) {
            tell("Not needed on this Android version");
        } else if (state.exactAlarm) {
            tell("Exact alarm permission already granted");
        } else {
            action.setValue(new Event<>(SettingsAction.OPEN_EXACT_ALARM_SETTINGS));
        }
    }

    // ---- Payment Guard ----

    public void onPaymentGuardToggled(boolean enabled) {
        if (Boolean.valueOf(enabled).equals(paymentGuardEnabled.getValue())) return;
        paymentGuardEnabled.setValue(enabled);
        settingsRepository.setPaymentGuardEnabled(enabled);
    }

    private void tell(String text) {
        message.setValue(new Event<>(UiMessage.shortMessage(text)));
    }
}
