package com.techy_pvk.automate.viewmodel;

import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.MusicPlaybackCondition;
import com.techy_pvk.automate.model.PowerSaveCondition;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;

import java.util.Locale;

/**
 * Turns a trigger into the one-line "Runs: ..." description shown under the trigger button in
 * the builder, and on the import confirmation. This used to be a 70-line switch inside
 * SequenceBuilderActivity; as a pure function it needs no Activity to test.
 */
public final class TriggerSummaryFormatter {

    private static final String[] DAY_LABELS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private TriggerSummaryFormatter() {}

    public static String format(SequenceTrigger trigger, boolean autoRestore) {
        if (trigger == null || trigger.type == null) trigger = new SequenceTrigger();

        switch (trigger.type) {
            case BLUETOOTH_CONNECTED:
                return "Runs: when a Bluetooth headset connects"
                        + (notBlank(trigger.bluetoothNameFilter)
                        ? " (\"" + trigger.bluetoothNameFilter + "\")" : "");

            case NOTIFICATION_RECEIVED:
                String appPart = notBlank(trigger.notificationAppLabel) ? trigger.notificationAppLabel : "any app";
                return "Runs: when a notification from " + appPart + " arrives"
                        + (notBlank(trigger.notificationTextFilter)
                        ? " containing \"" + trigger.notificationTextFilter + "\"" : "")
                        + (trigger.storeNotificationData ? " (saved to log)" : "");

            case LOCATION:
                String place = notBlank(trigger.locationLabel)
                        ? trigger.locationLabel
                        : String.format(Locale.US, "%.5f, %.5f", trigger.locationLatitude, trigger.locationLongitude);
                boolean entering = trigger.locationTransition != LocationTransition.EXIT;
                return "Runs: when you " + (entering ? "arrive at " : "leave ")
                        + place + " (" + Math.round(trigger.locationRadiusMeters) + "m radius)"
                        + (entering && autoRestore ? " \u2022 auto-restores on leave" : "");

            case WIFI_CONNECTED:
                return "Runs: when connected to "
                        + (notBlank(trigger.wifiSsidFilter) ? "\"" + trigger.wifiSsidFilter + "\"" : "any Wi-Fi network");

            case SCHEDULED_TIME:
                return "Runs: at " + formatTime(trigger.scheduledHour, trigger.scheduledMinute)
                        + " on " + describeDays(trigger.scheduledDays);

            case BOOT_COMPLETED:
                return "Runs: whenever the device finishes booting";

            case BATTERY_STATE:
                if (trigger.batteryConditionType == null) {
                    return "Runs: when battery drops to " + trigger.batteryLevelPercent + "% or below";
                }
                switch (trigger.batteryConditionType) {
                    case CHARGING_STARTED:
                        return "Runs: when charging starts" + (autoRestore ? " \u2022 auto-restores when charging stops" : "");
                    case CHARGING_STOPPED:
                        return "Runs: when charging stops";
                    case LEVEL_DROPPED_TO:
                    default:
                        return "Runs: when battery drops to " + trigger.batteryLevelPercent + "% or below";
                }

            case WIRED_HEADSET_CONNECTED:
                return "Runs: when a wired headset is plugged in";

            case MUSIC_PLAYBACK_STATE:
                String musicAppPart = notBlank(trigger.musicPlaybackAppLabel)
                        ? " from " + trigger.musicPlaybackAppLabel : "";
                return "Runs: when music" + musicAppPart + " "
                        + (trigger.musicPlaybackCondition != MusicPlaybackCondition.STOPPED_OR_PAUSED
                        ? "starts playing" + (autoRestore ? " \u2022 auto-restores when it stops" : "")
                        : "genuinely stops or pauses");

            case POWER_SAVE_MODE_CHANGED:
                return "Runs: when Battery Saver turns "
                        + (trigger.powerSaveCondition == PowerSaveCondition.DISABLED ? "off" : "on");

            case MANUAL:
            default:
                return "Runs: manually (tap \u25b6 on the main screen)";
        }
    }

    /** True for anything other than the default "only when I tap play". Used by the import
     *  confirmation to warn that the shared sequence will start running by itself. */
    public static boolean runsAutomatically(SequenceTrigger trigger) {
        return trigger != null && trigger.type != null && trigger.type != TriggerType.MANUAL;
    }

    static String formatTime(int hour, int minute) {
        String period = hour < 12 ? "AM" : "PM";
        int hour12 = hour % 12 == 0 ? 12 : hour % 12;
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, period);
    }

    static String describeDays(boolean[] days) {
        if (days == null) return "every day";
        int count = 0;
        for (boolean day : days) if (day) count++;
        if (count == 7) return "every day";
        if (count == 0) return "no days selected";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < days.length && i < DAY_LABELS.length; i++) {
            if (days[i]) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(DAY_LABELS[i]);
            }
        }
        return builder.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isEmpty();
    }
}
