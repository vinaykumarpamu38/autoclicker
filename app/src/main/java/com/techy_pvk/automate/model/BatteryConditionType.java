package com.techy_pvk.automate.model;

/** Which battery condition a BATTERY_STATE trigger fires on. */
public enum BatteryConditionType {
    /** Fires once when the level drops to (or below) the configured percentage - the common
     *  "do something when battery gets low" case. Won't re-fire again until the level rises
     *  back above the threshold and drops to it again. */
    LEVEL_DROPPED_TO,
    /** Fires when charging begins (cable/wireless charger connected). */
    CHARGING_STARTED,
    /** Fires when charging stops (unplugged). */
    CHARGING_STOPPED
}
