package com.techy_pvk.automate.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.techy_pvk.automate.model.BatteryConditionType;
import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.MusicPlaybackCondition;
import com.techy_pvk.automate.model.PowerSaveCondition;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;

import org.junit.Test;

public class TriggerSummaryFormatterTest {

    private static SequenceTrigger of(TriggerType type) {
        SequenceTrigger trigger = new SequenceTrigger();
        trigger.type = type;
        return trigger;
    }

    @Test
    public void nullAndManualDescribeManualRuns() {
        String manual = "Runs: manually (tap ▶ on the main screen)";
        assertEquals(manual, TriggerSummaryFormatter.format(null, false));
        assertEquals(manual, TriggerSummaryFormatter.format(new SequenceTrigger(), false));
    }

    @Test
    public void bluetoothMentionsTheNameFilterOnlyWhenSet() {
        SequenceTrigger trigger = of(TriggerType.BLUETOOTH_CONNECTED);
        assertEquals("Runs: when a Bluetooth headset connects", TriggerSummaryFormatter.format(trigger, false));
        trigger.bluetoothNameFilter = "Buds";
        assertEquals("Runs: when a Bluetooth headset connects (\"Buds\")", TriggerSummaryFormatter.format(trigger, false));
    }

    @Test
    public void notificationSummaryCoversAppFilterAndLog() {
        SequenceTrigger trigger = of(TriggerType.NOTIFICATION_RECEIVED);
        assertEquals("Runs: when a notification from any app arrives", TriggerSummaryFormatter.format(trigger, false));
        trigger.notificationAppLabel = "WhatsApp";
        trigger.notificationTextFilter = "OTP";
        trigger.storeNotificationData = true;
        assertEquals("Runs: when a notification from WhatsApp arrives containing \"OTP\" (saved to log)",
                TriggerSummaryFormatter.format(trigger, false));
    }

    @Test
    public void locationUsesLabelOrCoordinatesAndOnlyEnterCanAutoRestore() {
        SequenceTrigger trigger = of(TriggerType.LOCATION);
        trigger.locationLatitude = 17.385;
        trigger.locationLongitude = 78.4867;
        trigger.locationRadiusMeters = 150f;
        assertEquals("Runs: when you arrive at 17.38500, 78.48670 (150m radius) • auto-restores on leave",
                TriggerSummaryFormatter.format(trigger, true));

        trigger.locationLabel = "Office";
        assertEquals("Runs: when you arrive at Office (150m radius)", TriggerSummaryFormatter.format(trigger, false));

        trigger.locationTransition = LocationTransition.EXIT;
        assertEquals("Runs: when you leave Office (150m radius)", TriggerSummaryFormatter.format(trigger, true));
    }

    @Test
    public void wifiAnyOrNamed() {
        SequenceTrigger trigger = of(TriggerType.WIFI_CONNECTED);
        assertEquals("Runs: when connected to any Wi-Fi network", TriggerSummaryFormatter.format(trigger, false));
        trigger.wifiSsidFilter = "Home";
        assertEquals("Runs: when connected to \"Home\"", TriggerSummaryFormatter.format(trigger, false));
    }

    @Test
    public void scheduledTimeAndDays() {
        SequenceTrigger trigger = of(TriggerType.SCHEDULED_TIME);
        trigger.scheduledHour = 8;
        trigger.scheduledMinute = 5;
        assertEquals("Runs: at 8:05 AM on every day", TriggerSummaryFormatter.format(trigger, false));
        trigger.scheduledDays = new boolean[]{false, true, true, false, false, false, true};
        trigger.scheduledHour = 18;
        assertEquals("Runs: at 6:05 PM on Mon, Tue, Sat", TriggerSummaryFormatter.format(trigger, false));
    }

    @Test
    public void formatTimeHandlesMidnightAndNoon() {
        assertEquals("12:00 AM", TriggerSummaryFormatter.formatTime(0, 0));
        assertEquals("12:30 PM", TriggerSummaryFormatter.formatTime(12, 30));
        assertEquals("11:59 PM", TriggerSummaryFormatter.formatTime(23, 59));
    }

    @Test
    public void describeDaysEdgeCases() {
        assertEquals("every day", TriggerSummaryFormatter.describeDays(null));
        assertEquals("no days selected", TriggerSummaryFormatter.describeDays(new boolean[7]));
        assertEquals("every day", TriggerSummaryFormatter.describeDays(
                new boolean[]{true, true, true, true, true, true, true}));
    }

    @Test
    public void batteryVariants() {
        SequenceTrigger trigger = of(TriggerType.BATTERY_STATE);
        trigger.batteryConditionType = BatteryConditionType.CHARGING_STARTED;
        assertEquals("Runs: when charging starts", TriggerSummaryFormatter.format(trigger, false));
        assertEquals("Runs: when charging starts • auto-restores when charging stops",
                TriggerSummaryFormatter.format(trigger, true));
        trigger.batteryConditionType = BatteryConditionType.CHARGING_STOPPED;
        assertEquals("Runs: when charging stops", TriggerSummaryFormatter.format(trigger, true));
        trigger.batteryConditionType = BatteryConditionType.LEVEL_DROPPED_TO;
        trigger.batteryLevelPercent = 15;
        assertEquals("Runs: when battery drops to 15% or below", TriggerSummaryFormatter.format(trigger, false));
    }

    @Test
    public void musicAndPowerSave() {
        SequenceTrigger music = of(TriggerType.MUSIC_PLAYBACK_STATE);
        music.musicPlaybackAppLabel = "Spotify";
        music.musicPlaybackCondition = MusicPlaybackCondition.STARTED;
        assertEquals("Runs: when music from Spotify starts playing • auto-restores when it stops",
                TriggerSummaryFormatter.format(music, true));
        music.musicPlaybackCondition = MusicPlaybackCondition.STOPPED_OR_PAUSED;
        assertEquals("Runs: when music from Spotify genuinely stops or pauses",
                TriggerSummaryFormatter.format(music, true));

        SequenceTrigger saver = of(TriggerType.POWER_SAVE_MODE_CHANGED);
        assertEquals("Runs: when Battery Saver turns on", TriggerSummaryFormatter.format(saver, false));
        saver.powerSaveCondition = PowerSaveCondition.DISABLED;
        assertEquals("Runs: when Battery Saver turns off", TriggerSummaryFormatter.format(saver, false));
    }

    @Test
    public void runsAutomaticallyOnlyForNonManual() {
        assertFalse(TriggerSummaryFormatter.runsAutomatically(null));
        assertFalse(TriggerSummaryFormatter.runsAutomatically(new SequenceTrigger()));
        assertTrue(TriggerSummaryFormatter.runsAutomatically(of(TriggerType.BOOT_COMPLETED)));
    }
}
