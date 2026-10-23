package com.techy_pvk.automate.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.AppInfo;
import com.techy_pvk.automate.model.BatteryConditionType;
import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.MusicPlaybackCondition;
import com.techy_pvk.automate.model.PowerSaveCondition;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;
import com.techy_pvk.automate.repository.GeocodeResult;
import com.techy_pvk.automate.util.InputParsers;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The "When should this sequence run?" dialogs. Each takes the trigger currently set (to
 * pre-fill itself when editing), collects the user's choices, and reports a brand-new trigger
 * through OnTriggerChosen. No state, no storage - the ViewModel decides what to do with the result.
 */
public final class TriggerDialogs {

    /** Labels for the trigger-type chooser. Order must match SequenceBuilderActivity.onTriggerTypeChosen(). */
    public static final String[] TRIGGER_TYPE_LABELS = {
            "Manually", "Bluetooth headset connects", "Notification received",
            "Location (enter/exit a place)", "Wi-Fi network connected", "Scheduled time",
            "Device boot completed", "Battery level / charging", "Wired headset connects",
            "Music playback", "Power Saving Mode changes",
    };

    /** Reports the trigger the user chose. */
    public interface OnTriggerChosen {
        /**
         * @param autoRestore   whether the "restore when it ends" box was ticked (the ViewModel
         *                      makes the final call on whether this trigger supports it)
         * @param suggestedName a plainly-worded name to use if the sequence has none yet, or null
         */
        void onChosen(@NonNull SequenceTrigger trigger, boolean autoRestore, @Nullable String suggestedName);
    }

    private TriggerDialogs() {}

    // ---- Triggers that need no dialog ----

    @NonNull
    public static SequenceTrigger manual() {
        return new SequenceTrigger(); // defaults to MANUAL
    }

    @NonNull
    public static SequenceTrigger bootCompleted() {
        SequenceTrigger trigger = new SequenceTrigger();
        trigger.type = TriggerType.BOOT_COMPLETED;
        return trigger;
    }

    @NonNull
    public static SequenceTrigger wiredHeadset() {
        SequenceTrigger trigger = new SequenceTrigger();
        trigger.type = TriggerType.WIRED_HEADSET_CONNECTED;
        return trigger;
    }

    public static AlertDialog showChooser(@NonNull Context context, @NonNull Consumer<Integer> onChosen) {
        return new AlertDialog.Builder(context)
                .setTitle("When should this sequence run?")
                .setItems(TRIGGER_TYPE_LABELS, (dialog, which) -> onChosen.accept(which))
                .show();
    }

    // ---- Bluetooth ----

    public static AlertDialog showBluetooth(@NonNull Context context, @NonNull SequenceTrigger current,
                                     @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_bluetooth, null);
        EditText filterInput = view.findViewById(R.id.input_bluetooth_name_filter);
        if (current.type == TriggerType.BLUETOOTH_CONNECTED && current.bluetoothNameFilter != null) {
            filterInput.setText(current.bluetoothNameFilter);
        }

        return new AlertDialog.Builder(context)
                .setTitle("Bluetooth headset connects")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.BLUETOOTH_CONNECTED;
                    trigger.bluetoothNameFilter = filterInput.getText().toString().trim();
                    onChosen.onChosen(trigger, false, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Notification ----

    public static AlertDialog showNotification(@NonNull Context context, @NonNull List<AppInfo> installedApps,
                                        @NonNull SequenceTrigger current, @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_notification, null);
        Spinner appSpinner = view.findViewById(R.id.spinner_trigger_app);
        EditText textFilterInput = view.findViewById(R.id.input_notification_text_filter);
        CheckBox storeCheckbox = view.findViewById(R.id.checkbox_store_notification);

        List<AppInfo> apps = withLeadingChoice("Any app", installedApps);
        appSpinner.setAdapter(StepDialogs.appAdapter(context, apps));

        if (current.type == TriggerType.NOTIFICATION_RECEIVED) {
            textFilterInput.setText(current.notificationTextFilter != null ? current.notificationTextFilter : "");
            storeCheckbox.setChecked(current.storeNotificationData);
            selectApp(appSpinner, apps, current.notificationPackageName);
        }

        return new AlertDialog.Builder(context)
                .setTitle("Notification received")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.NOTIFICATION_RECEIVED;
                    AppInfo selectedApp = (AppInfo) appSpinner.getSelectedItem();
                    trigger.notificationPackageName = selectedApp != null ? selectedApp.packageName : "";
                    trigger.notificationAppLabel = selectedApp != null ? selectedApp.label : "";
                    trigger.notificationTextFilter = textFilterInput.getText().toString().trim();
                    trigger.storeNotificationData = storeCheckbox.isChecked();
                    onChosen.onChosen(trigger, false, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Location ----

    /** Handle to an open Location dialog, so an address lookup that finishes later can fill it in. */
    public static final class LocationDialog {
        private final AlertDialog dialog;
        private final EditText latitudeInput;
        private final EditText longitudeInput;

        LocationDialog(AlertDialog dialog, EditText latitudeInput, EditText longitudeInput) {
            this.dialog = dialog;
            this.latitudeInput = latitudeInput;
            this.longitudeInput = longitudeInput;
        }

        /** The underlying dialog, so the screen can dismiss it if it is destroyed first. */
        @NonNull
        public AlertDialog getDialog() { return dialog; }

        /** Shows the result of an address lookup. Does nothing if the dialog has been dismissed. */
        public void showLookupResult(@NonNull GeocodeResult result) {
            if (!dialog.isShowing()) return;
            Context context = dialog.getContext();
            if (!result.found) {
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show();
                return;
            }
            latitudeInput.setText(String.valueOf(result.latitude));
            longitudeInput.setText(String.valueOf(result.longitude));
            Toast.makeText(context, "Found it - coordinates filled in below", Toast.LENGTH_SHORT).show();
        }
    }

    /** @param onLookupAddress called with the typed address when the user taps Look Up */
    @NonNull
    public static LocationDialog showLocation(@NonNull Context context, @NonNull SequenceTrigger current,
                                              boolean currentAutoRestore,
                                              @NonNull Consumer<String> onLookupAddress,
                                              @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_location, null);
        EditText addressInput = view.findViewById(R.id.input_location_address);
        Button lookupButton = view.findViewById(R.id.button_lookup_address);
        EditText latitudeInput = view.findViewById(R.id.input_location_latitude);
        EditText longitudeInput = view.findViewById(R.id.input_location_longitude);
        EditText radiusInput = view.findViewById(R.id.input_location_radius);
        RadioGroup transitionGroup = view.findViewById(R.id.radio_group_location_transition);
        CheckBox autoRestoreCheckbox = view.findViewById(R.id.checkbox_location_auto_restore);

        if (current.type == TriggerType.LOCATION) {
            addressInput.setText(current.locationLabel != null ? current.locationLabel : "");
            latitudeInput.setText(String.valueOf(current.locationLatitude));
            longitudeInput.setText(String.valueOf(current.locationLongitude));
            radiusInput.setText(String.valueOf(Math.round(current.locationRadiusMeters)));
            transitionGroup.check(current.locationTransition == LocationTransition.EXIT
                    ? R.id.radio_location_exit : R.id.radio_location_enter);
            autoRestoreCheckbox.setChecked(currentAutoRestore);
        } else {
            radiusInput.setText("150");
        }

        // Auto-restore only means something for "arrive" - there's nothing to restore *from* on "leave".
        Runnable syncAutoRestoreEnabled = () -> autoRestoreCheckbox.setEnabled(
                transitionGroup.getCheckedRadioButtonId() == R.id.radio_location_enter);
        syncAutoRestoreEnabled.run();
        transitionGroup.setOnCheckedChangeListener((group, checkedId) -> syncAutoRestoreEnabled.run());

        lookupButton.setOnClickListener(v -> onLookupAddress.accept(addressInput.getText().toString()));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Location")
                .setView(view)
                .setPositiveButton("Save", (d, which) -> {
                    Double lat = InputParsers.parseDoubleOrNull(latitudeInput.getText().toString());
                    Double lng = InputParsers.parseDoubleOrNull(longitudeInput.getText().toString());
                    if (lat == null || lng == null) {
                        Toast.makeText(context, "Enter a valid latitude and longitude (or use Look Up)",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.LOCATION;
                    trigger.locationLatitude = lat;
                    trigger.locationLongitude = lng;
                    trigger.locationRadiusMeters = InputParsers.parseFloatOrDefault(radiusInput.getText().toString(), 150f);
                    trigger.locationTransition = transitionGroup.getCheckedRadioButtonId() == R.id.radio_location_exit
                            ? LocationTransition.EXIT : LocationTransition.ENTER;
                    trigger.locationLabel = addressInput.getText().toString().trim();
                    boolean restore = autoRestoreCheckbox.isEnabled() && autoRestoreCheckbox.isChecked();
                    onChosen.onChosen(trigger, restore,
                            trigger.locationLabel.isEmpty() ? "Arrive at place" : "Arrive at " + trigger.locationLabel);
                })
                .setNegativeButton("Cancel", null)
                .show();

        return new LocationDialog(dialog, latitudeInput, longitudeInput);
    }

    // ---- Wi-Fi ----

    /** @param currentSsid returns the connected network's name, or null if unavailable */
    public static AlertDialog showWifi(@NonNull Context context, @NonNull SequenceTrigger current,
                                @NonNull Supplier<String> currentSsid, @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_wifi, null);
        EditText ssidInput = view.findViewById(R.id.input_wifi_ssid);
        Button useCurrentButton = view.findViewById(R.id.button_use_current_wifi);

        if (current.type == TriggerType.WIFI_CONNECTED && current.wifiSsidFilter != null) {
            ssidInput.setText(current.wifiSsidFilter);
        }

        useCurrentButton.setOnClickListener(v -> {
            String ssid = currentSsid.get();
            if (ssid == null) {
                Toast.makeText(context, "Not connected to Wi-Fi, or location access isn't granted yet",
                        Toast.LENGTH_LONG).show();
            } else {
                ssidInput.setText(ssid);
            }
        });

        return new AlertDialog.Builder(context)
                .setTitle("Wi-Fi network connected")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.WIFI_CONNECTED;
                    trigger.wifiSsidFilter = ssidInput.getText().toString().trim();
                    onChosen.onChosen(trigger, false, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Scheduled time ----

    public static AlertDialog showScheduled(@NonNull Context context, @NonNull SequenceTrigger current,
                                     @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_scheduled, null);
        EditText hourInput = view.findViewById(R.id.input_scheduled_hour);
        EditText minuteInput = view.findViewById(R.id.input_scheduled_minute);
        CheckBox[] dayCheckboxes = {
                view.findViewById(R.id.checkbox_day_sun), view.findViewById(R.id.checkbox_day_mon),
                view.findViewById(R.id.checkbox_day_tue), view.findViewById(R.id.checkbox_day_wed),
                view.findViewById(R.id.checkbox_day_thu), view.findViewById(R.id.checkbox_day_fri),
                view.findViewById(R.id.checkbox_day_sat),
        };

        if (current.type == TriggerType.SCHEDULED_TIME) {
            hourInput.setText(String.valueOf(current.scheduledHour));
            minuteInput.setText(String.valueOf(current.scheduledMinute));
            if (current.scheduledDays != null) {
                for (int i = 0; i < dayCheckboxes.length && i < current.scheduledDays.length; i++) {
                    dayCheckboxes[i].setChecked(current.scheduledDays[i]);
                }
            }
        } else {
            hourInput.setText("8");
            minuteInput.setText("0");
        }

        return new AlertDialog.Builder(context)
                .setTitle("Scheduled time")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    Integer hour = InputParsers.parseIntInRange(hourInput.getText().toString(), 0, 23);
                    Integer minute = InputParsers.parseIntInRange(minuteInput.getText().toString(), 0, 59);
                    if (hour == null || minute == null) {
                        Toast.makeText(context, "Enter a valid hour (0-23) and minute (0-59)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean[] days = new boolean[7];
                    boolean anyDay = false;
                    for (int i = 0; i < dayCheckboxes.length; i++) {
                        days[i] = dayCheckboxes[i].isChecked();
                        anyDay = anyDay || days[i];
                    }
                    if (!anyDay) {
                        Toast.makeText(context, "Pick at least one day", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.SCHEDULED_TIME;
                    trigger.scheduledHour = hour;
                    trigger.scheduledMinute = minute;
                    trigger.scheduledDays = days;
                    onChosen.onChosen(trigger, false, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Battery ----

    public static AlertDialog showBattery(@NonNull Context context, @NonNull SequenceTrigger current,
                                   boolean currentAutoRestore, @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_battery, null);
        RadioGroup conditionGroup = view.findViewById(R.id.radio_group_battery_condition);
        View levelSection = view.findViewById(R.id.section_battery_level);
        EditText percentInput = view.findViewById(R.id.input_battery_percent);
        View autoRestoreSection = view.findViewById(R.id.section_battery_auto_restore);
        CheckBox autoRestoreCheckbox = view.findViewById(R.id.checkbox_battery_auto_restore);

        int checkedId = R.id.radio_battery_level;
        if (current.type == TriggerType.BATTERY_STATE) {
            percentInput.setText(String.valueOf(current.batteryLevelPercent));
            if (current.batteryConditionType == BatteryConditionType.CHARGING_STARTED) {
                checkedId = R.id.radio_battery_charging_started;
                autoRestoreCheckbox.setChecked(currentAutoRestore);
            } else if (current.batteryConditionType == BatteryConditionType.CHARGING_STOPPED) {
                checkedId = R.id.radio_battery_charging_stopped;
            }
        } else {
            percentInput.setText("20");
        }
        conditionGroup.check(checkedId);
        levelSection.setVisibility(checkedId == R.id.radio_battery_level ? View.VISIBLE : View.GONE);
        autoRestoreSection.setVisibility(checkedId == R.id.radio_battery_charging_started ? View.VISIBLE : View.GONE);
        conditionGroup.setOnCheckedChangeListener((group, id) -> {
            levelSection.setVisibility(id == R.id.radio_battery_level ? View.VISIBLE : View.GONE);
            autoRestoreSection.setVisibility(id == R.id.radio_battery_charging_started ? View.VISIBLE : View.GONE);
        });

        return new AlertDialog.Builder(context)
                .setTitle("Battery level / charging")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    int selected = conditionGroup.getCheckedRadioButtonId();
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.BATTERY_STATE;
                    boolean restore = false;
                    String suggestedName = null;
                    if (selected == R.id.radio_battery_charging_started) {
                        trigger.batteryConditionType = BatteryConditionType.CHARGING_STARTED;
                        restore = autoRestoreCheckbox.isChecked();
                        suggestedName = "Vibrate while charging";
                    } else if (selected == R.id.radio_battery_charging_stopped) {
                        trigger.batteryConditionType = BatteryConditionType.CHARGING_STOPPED;
                    } else {
                        trigger.batteryConditionType = BatteryConditionType.LEVEL_DROPPED_TO;
                        Integer percent = InputParsers.parseIntInRange(percentInput.getText().toString(), 1, 100);
                        trigger.batteryLevelPercent = percent != null ? percent : 20;
                    }
                    onChosen.onChosen(trigger, restore, suggestedName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Music playback ----

    public static AlertDialog showMusicPlayback(@NonNull Context context, @NonNull List<AppInfo> installedApps,
                                         @NonNull SequenceTrigger current, boolean currentAutoRestore,
                                         @NonNull OnTriggerChosen onChosen) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_trigger_music, null);
        RadioGroup conditionGroup = view.findViewById(R.id.radio_group_music_condition);
        View autoRestoreSection = view.findViewById(R.id.section_music_auto_restore);
        CheckBox autoRestoreCheckbox = view.findViewById(R.id.checkbox_music_auto_restore);
        Spinner appSpinner = view.findViewById(R.id.spinner_music_app);

        List<AppInfo> apps = withLeadingChoice("Any app that plays media", installedApps);
        appSpinner.setAdapter(StepDialogs.appAdapter(context, apps));

        int checkedId = R.id.radio_music_started;
        if (current.type == TriggerType.MUSIC_PLAYBACK_STATE) {
            if (current.musicPlaybackCondition == MusicPlaybackCondition.STOPPED_OR_PAUSED) {
                checkedId = R.id.radio_music_stopped;
            } else {
                autoRestoreCheckbox.setChecked(currentAutoRestore);
            }
            selectApp(appSpinner, apps, current.musicPlaybackPackageFilter);
        }
        conditionGroup.check(checkedId);
        autoRestoreSection.setVisibility(checkedId == R.id.radio_music_started ? View.VISIBLE : View.GONE);
        conditionGroup.setOnCheckedChangeListener((group, id) ->
                autoRestoreSection.setVisibility(id == R.id.radio_music_started ? View.VISIBLE : View.GONE));

        return new AlertDialog.Builder(context)
                .setTitle("Music playback")
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.MUSIC_PLAYBACK_STATE;
                    AppInfo selectedApp = (AppInfo) appSpinner.getSelectedItem();
                    trigger.musicPlaybackPackageFilter = selectedApp != null ? selectedApp.packageName : "";
                    trigger.musicPlaybackAppLabel = selectedApp != null ? selectedApp.label : "";
                    boolean restore = false;
                    String suggestedName = null;
                    if (conditionGroup.getCheckedRadioButtonId() == R.id.radio_music_started) {
                        trigger.musicPlaybackCondition = MusicPlaybackCondition.STARTED;
                        restore = autoRestoreCheckbox.isChecked();
                        suggestedName = selectedApp != null && !selectedApp.packageName.isEmpty()
                                ? "Vibrate while " + selectedApp.label + " plays" : "Vibrate while music plays";
                    } else {
                        trigger.musicPlaybackCondition = MusicPlaybackCondition.STOPPED_OR_PAUSED;
                    }
                    onChosen.onChosen(trigger, restore, suggestedName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Power Saving Mode ----

    public static AlertDialog showPowerSave(@NonNull Context context, @NonNull SequenceTrigger current,
                                     @NonNull OnTriggerChosen onChosen) {
        String[] labels = {"When Battery Saver turns on", "When Battery Saver turns off"};
        PowerSaveCondition[] options = {PowerSaveCondition.ENABLED, PowerSaveCondition.DISABLED};

        int checkedIndex = current.type == TriggerType.POWER_SAVE_MODE_CHANGED
                && current.powerSaveCondition == PowerSaveCondition.DISABLED ? 1 : 0;

        return new AlertDialog.Builder(context)
                .setTitle("Power Saving Mode")
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    SequenceTrigger trigger = new SequenceTrigger();
                    trigger.type = TriggerType.POWER_SAVE_MODE_CHANGED;
                    trigger.powerSaveCondition = options[which];
                    onChosen.onChosen(trigger, false, null);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---- Helpers ----

    /** The installed apps with a "no filter" choice (empty package name) in front. */
    @NonNull
    private static List<AppInfo> withLeadingChoice(@NonNull String label, @NonNull List<AppInfo> installed) {
        List<AppInfo> apps = new ArrayList<>();
        apps.add(new AppInfo(label, ""));
        apps.addAll(installed);
        return apps;
    }

    private static void selectApp(@NonNull Spinner spinner, @NonNull List<AppInfo> apps, @Nullable String packageName) {
        String wanted = packageName != null ? packageName : "";
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).packageName.equals(wanted)) {
                spinner.setSelection(i);
                return;
            }
        }
    }
}
