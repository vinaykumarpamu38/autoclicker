package com.techy_pvk.automate.model;

/** Maps directly to NotificationManager.INTERRUPTION_FILTER_* constants. Needs Do Not
 *  Disturb access (Notification Policy Access) - same permission already used by Set Ringer
 *  Mode's Silent option. */
public enum DndMode {
    OFF, PRIORITY_ONLY, ALARMS_ONLY, TOTAL_SILENCE
}
