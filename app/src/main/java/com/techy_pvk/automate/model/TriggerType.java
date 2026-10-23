package com.techy_pvk.automate.model;

/**
 * What causes a sequence to run.
 *  - MANUAL: only runs when you tap ▶ on the main screen (the original behavior).
 *  - BLUETOOTH_CONNECTED: runs when a Bluetooth audio device (headset, earbuds, speaker)
 *    connects.
 *  - NOTIFICATION_RECEIVED: runs when a notification is posted that matches the trigger's
 *    app/text filters.
 *  - LOCATION: runs when you enter or leave a radius around a saved lat/lng (a geofence).
 *  - WIFI_CONNECTED: runs when the device connects to Wi-Fi (optionally a specific network).
 *  - SCHEDULED_TIME: runs at a specific time of day, on whichever days of the week you pick.
 *  - BOOT_COMPLETED: runs once whenever the device finishes booting.
 *  - BATTERY_STATE: runs when charging starts/stops, or when the battery level drops to a
 *    configured percentage.
 *  - WIRED_HEADSET_CONNECTED: runs when a wired headset (3.5mm jack or a USB-C adapter that
 *    reports itself the same way) is plugged in.
 *  - MUSIC_PLAYBACK_STATE: runs when media playback starts, or genuinely stops/pauses (not
 *    just skips to the next track - see MusicPlaybackCondition for how that's told apart).
 *  - POWER_SAVE_MODE_CHANGED: runs when Battery Saver is turned on or off. Detection only -
 *    there's no matching action step, since no public API lets a regular app toggle Battery
 *    Saver itself.
 */
public enum TriggerType {
    MANUAL, BLUETOOTH_CONNECTED, NOTIFICATION_RECEIVED,
    LOCATION, WIFI_CONNECTED, SCHEDULED_TIME, BOOT_COMPLETED, BATTERY_STATE,
    WIRED_HEADSET_CONNECTED, MUSIC_PLAYBACK_STATE, POWER_SAVE_MODE_CHANGED
}
