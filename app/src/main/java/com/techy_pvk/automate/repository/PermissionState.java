package com.techy_pvk.automate.repository;

/**
 * Immutable snapshot of every special permission / access grant the app cares about. A value
 * object on purpose: ViewModels hold and compare snapshots instead of asking the system
 * repeatedly, and tests can build one directly.
 */
public final class PermissionState {

    public final boolean overlay;
    public final boolean accessibilityEnabled;
    public final boolean notificationListener;
    public final boolean doNotDisturb;
    public final boolean writeSettings;
    public final boolean foregroundLocation;
    public final boolean backgroundLocation;
    public final boolean exactAlarm;

    /** Background location is its own permission only from Android 10 (API 29). */
    public final boolean backgroundLocationApplies;
    /** Exact alarms need a user grant only from Android 12 (API 31). */
    public final boolean exactAlarmApplies;

    public PermissionState(boolean overlay, boolean accessibilityEnabled, boolean notificationListener,
                           boolean doNotDisturb, boolean writeSettings, boolean foregroundLocation,
                           boolean backgroundLocation, boolean exactAlarm,
                           boolean backgroundLocationApplies, boolean exactAlarmApplies) {
        this.overlay = overlay;
        this.accessibilityEnabled = accessibilityEnabled;
        this.notificationListener = notificationListener;
        this.doNotDisturb = doNotDisturb;
        this.writeSettings = writeSettings;
        this.foregroundLocation = foregroundLocation;
        this.backgroundLocation = backgroundLocation;
        this.exactAlarm = exactAlarm;
        this.backgroundLocationApplies = backgroundLocationApplies;
        this.exactAlarmApplies = exactAlarmApplies;
    }

    /** Everything a location-trigger sequence needs to actually fire while the app is closed. */
    public boolean locationTriggersCanRun() {
        return foregroundLocation && (!backgroundLocationApplies || backgroundLocation);
    }
}
