package com.techy_pvk.automate.model;

/**
 * The "when should this run automatically" half of a sequence - paired with its steps (the
 * "what should it do" half). Defaults to MANUAL so every existing saved sequence keeps
 * behaving exactly as before (only runs when you tap ▶).
 */
public class SequenceTrigger {
    public TriggerType type = TriggerType.MANUAL;

    // ---- BLUETOOTH_CONNECTED ----
    /** Optional case-insensitive "contains" filter on the connecting device's name. Blank = any audio device. */
    public String bluetoothNameFilter;

    // ---- NOTIFICATION_RECEIVED ----
    /** Optional: only match notifications from this package. Blank = any app. */
    public String notificationPackageName;
    /** Display label for notificationPackageName, so the UI can show something readable. */
    public String notificationAppLabel;
    /** Optional case-insensitive "contains" filter checked against the notification's title OR text. Blank = any content. */
    public String notificationTextFilter;
    /** If true, every matching notification is also appended to the on-device Notification Log. */
    public boolean storeNotificationData;

    // ---- LOCATION ----
    public double locationLatitude;
    public double locationLongitude;
    /** Geofence radius in meters. */
    public float locationRadiusMeters = 150f;
    public LocationTransition locationTransition = LocationTransition.ENTER;
    /** Display label (address typed in, or a name you gave it) - purely for the UI summary. */
    public String locationLabel;

    // ---- WIFI_CONNECTED ----
    /** Optional exact SSID to match. Blank = any Wi-Fi network. */
    public String wifiSsidFilter;

    // ---- SCHEDULED_TIME ----
    public int scheduledHour = 8;
    public int scheduledMinute = 0;
    /** Index 0 = Sunday ... 6 = Saturday (matches Calendar.DAY_OF_WEEK - 1). All true = every day. */
    public boolean[] scheduledDays = {true, true, true, true, true, true, true};

    // ---- BATTERY_STATE ----
    public BatteryConditionType batteryConditionType = BatteryConditionType.LEVEL_DROPPED_TO;
    /** Used only when batteryConditionType is LEVEL_DROPPED_TO. */
    public int batteryLevelPercent = 20;

    // ---- MUSIC_PLAYBACK_STATE ----
    public MusicPlaybackCondition musicPlaybackCondition = MusicPlaybackCondition.STARTED;
    /** Blank/null = any app's media session. Set to a specific package (e.g. Spotify) to only
     *  react to that app's playback state. */
    public String musicPlaybackPackageFilter;
    /** Human-readable label for musicPlaybackPackageFilter, stored alongside it so the
     *  builder screen's summary doesn't need a PackageManager lookup to display it. */
    public String musicPlaybackAppLabel;

    // ---- POWER_SAVE_MODE_CHANGED ----
    public PowerSaveCondition powerSaveCondition = PowerSaveCondition.ENABLED;

    /** True only for the three triggers that have a natural "opposite" event already modeled:
     *  Location ENTER (opposite: EXIT), Battery CHARGING_STARTED (opposite: CHARGING_STOPPED) and
     *  Music STARTED (opposite: STOPPED_OR_PAUSED). Auto-Restore is meaningless for any other
     *  trigger, so a sequence's autoRestore flag is only ever kept when this returns true. */
    public boolean supportsAutoRestore() {
        if (type == null) return false;
        switch (type) {
            case LOCATION:
                return locationTransition == LocationTransition.ENTER;
            case BATTERY_STATE:
                return batteryConditionType == BatteryConditionType.CHARGING_STARTED;
            case MUSIC_PLAYBACK_STATE:
                return musicPlaybackCondition == MusicPlaybackCondition.STARTED;
            default:
                return false;
        }
    }
}
