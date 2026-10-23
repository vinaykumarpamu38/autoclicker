package com.techy_pvk.automate.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.techy_pvk.automate.R;
import com.techy_pvk.automate.repository.PermissionState;
import com.techy_pvk.automate.util.EventObserver;
import com.techy_pvk.automate.util.ToastUtil;
import com.techy_pvk.automate.viewmodel.LocationDisclosureViewModel;
import com.techy_pvk.automate.viewmodel.SettingsAction;
import com.techy_pvk.automate.viewmodel.SettingsViewModel;

/**
 * Permissions and toggles. Each row's button reports a tap to {@link SettingsViewModel}, which
 * decides what it means; this class builds the Intent for the system screen the ViewModel picks
 * and shows each permission's current state on its button.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String GRANTED = "Granted \u2713";

    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        EdgeToEdgeUtils.applyRootInsets(findViewById(android.R.id.content));
        setTitle("Settings");

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        findViewById(R.id.btn_overlay_permission).setOnClickListener(v -> viewModel.onOverlayClicked());
        findViewById(R.id.btn_accessibility_permission).setOnClickListener(v -> viewModel.onAccessibilityClicked());
        findViewById(R.id.btn_notification_access).setOnClickListener(v -> viewModel.onNotificationAccessClicked());
        findViewById(R.id.btn_dnd_access).setOnClickListener(v -> viewModel.onDoNotDisturbClicked());
        findViewById(R.id.btn_write_settings_access).setOnClickListener(v -> viewModel.onWriteSettingsClicked());
        findViewById(R.id.btn_grant_location).setOnClickListener(v -> viewModel.onLocationClicked());
        findViewById(R.id.btn_grant_background_location).setOnClickListener(v -> viewModel.onBackgroundLocationClicked());
        findViewById(R.id.btn_grant_exact_alarm).setOnClickListener(v -> viewModel.onExactAlarmClicked());

        MaterialSwitch paymentGuardSwitch = findViewById(R.id.switch_payment_guard);
        paymentGuardSwitch.setOnCheckedChangeListener((button, isChecked) ->
                viewModel.onPaymentGuardToggled(isChecked));
        viewModel.getPaymentGuardEnabled().observe(this, enabled -> {
            boolean checked = Boolean.TRUE.equals(enabled);
            if (paymentGuardSwitch.isChecked() != checked) paymentGuardSwitch.setChecked(checked);
        });

        viewModel.getPermissions().observe(this, this::renderPermissions);
        viewModel.getMessage().observe(this, new EventObserver<>(message -> ToastUtil.show(this, message)));
        viewModel.getAction().observe(this, new EventObserver<>(this::perform));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user changes these in system screens we send them to - re-read on the way back.
        viewModel.refresh();
    }

    private void renderPermissions(PermissionState state) {
        setLabel(R.id.btn_overlay_permission, state.overlay, "Grant");
        setLabel(R.id.btn_notification_access, state.notificationListener, "Grant");
        setLabel(R.id.btn_dnd_access, state.doNotDisturb, "Grant");
        setLabel(R.id.btn_write_settings_access, state.writeSettings, "Grant");
        setLabel(R.id.btn_grant_location, state.foregroundLocation, "Open Settings");
        setLabel(R.id.btn_grant_background_location,
                !state.backgroundLocationApplies || state.backgroundLocation, "Grant");
        setLabel(R.id.btn_grant_exact_alarm, !state.exactAlarmApplies || state.exactAlarm, "Grant");

        // Always tappable: it's also how the user gets back to the system screen to turn it off.
        ((Button) findViewById(R.id.btn_accessibility_permission))
                .setText(state.accessibilityEnabled ? "Enabled \u2713 \u00b7 Manage" : "Open Settings");
    }

    private void setLabel(int buttonId, boolean granted, @NonNull String notGrantedLabel) {
        ((Button) findViewById(buttonId)).setText(granted ? GRANTED : notGrantedLabel);
    }

    /** The ViewModel chose where to go; this is just how to get there. */
    private void perform(SettingsAction action) {
        switch (action) {
            case OPEN_OVERLAY_SETTINGS:
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri()));
                break;
            case OPEN_ACCESSIBILITY_DISCLOSURE:
                startActivity(new Intent(this, AccessibilityDisclosureActivity.class));
                break;
            case OPEN_NOTIFICATION_LISTENER_SETTINGS:
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                break;
            case OPEN_DND_SETTINGS:
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                break;
            case OPEN_WRITE_SETTINGS:
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri()));
                break;
            case OPEN_LOCATION_DISCLOSURE:
                startActivity(locationDisclosure(false));
                break;
            case OPEN_BACKGROUND_LOCATION_DISCLOSURE:
                startActivity(locationDisclosure(true));
                break;
            case OPEN_EXACT_ALARM_SETTINGS:
                openExactAlarmSettings();
                break;
        }
    }

    private Intent locationDisclosure(boolean includeBackground) {
        Intent intent = new Intent(this, LocationDisclosureActivity.class);
        intent.putExtra(LocationDisclosureViewModel.KEY_INCLUDE_BACKGROUND, includeBackground);
        return intent;
    }

    /** ACTION_REQUEST_SCHEDULE_EXACT_ALARM exists only from Android 12; the ViewModel only emits
     *  this action there, but the check also keeps Lint (and any stray call) honest. */
    private void openExactAlarmSettings() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return;
        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri()));
    }

    private Uri packageUri() {
        return Uri.parse("package:" + getPackageName());
    }
}
