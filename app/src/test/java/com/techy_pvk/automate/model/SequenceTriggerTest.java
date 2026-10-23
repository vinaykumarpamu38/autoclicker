package com.techy_pvk.automate.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SequenceTriggerTest {

    private static SequenceTrigger of(TriggerType type) {
        SequenceTrigger trigger = new SequenceTrigger();
        trigger.type = type;
        return trigger;
    }

    @Test
    public void onlyTriggersWithANaturalOppositeSupportAutoRestore() {
        SequenceTrigger location = of(TriggerType.LOCATION);
        location.locationTransition = LocationTransition.ENTER;
        assertTrue(location.supportsAutoRestore());
        location.locationTransition = LocationTransition.EXIT;
        assertFalse(location.supportsAutoRestore());

        SequenceTrigger battery = of(TriggerType.BATTERY_STATE);
        battery.batteryConditionType = BatteryConditionType.CHARGING_STARTED;
        assertTrue(battery.supportsAutoRestore());
        battery.batteryConditionType = BatteryConditionType.LEVEL_DROPPED_TO;
        assertFalse(battery.supportsAutoRestore());

        SequenceTrigger music = of(TriggerType.MUSIC_PLAYBACK_STATE);
        music.musicPlaybackCondition = MusicPlaybackCondition.STARTED;
        assertTrue(music.supportsAutoRestore());
        music.musicPlaybackCondition = MusicPlaybackCondition.STOPPED_OR_PAUSED;
        assertFalse(music.supportsAutoRestore());
    }

    @Test
    public void everythingElseDoesNot() {
        assertFalse(new SequenceTrigger().supportsAutoRestore());
        assertFalse(of(TriggerType.WIFI_CONNECTED).supportsAutoRestore());
        assertFalse(of(TriggerType.SCHEDULED_TIME).supportsAutoRestore());
        assertFalse(of(null).supportsAutoRestore());
    }
}
