package com.techy_pvk.automate.viewmodel;

/** Where tapping a Settings row should take the user. The ViewModel decides WHICH; the
 *  Activity owns HOW (building the Intent), because Intents and Settings screens are View-layer. */
public enum SettingsAction {
    OPEN_OVERLAY_SETTINGS,
    OPEN_ACCESSIBILITY_DISCLOSURE,
    OPEN_NOTIFICATION_LISTENER_SETTINGS,
    OPEN_DND_SETTINGS,
    OPEN_WRITE_SETTINGS,
    /** Foreground location only (Wi-Fi network name, and the first half of geofencing). */
    OPEN_LOCATION_DISCLOSURE,
    /** Foreground + "Allow all the time" background location (geofence triggers). */
    OPEN_BACKGROUND_LOCATION_DISCLOSURE,
    OPEN_EXACT_ALARM_SETTINGS
}
