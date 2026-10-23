package com.techy_pvk.automate.model;

/** Which ringer mode a SET_RINGER_MODE step applies. SILENT needs Do Not Disturb access
 *  (a separate special grant) - NORMAL and VIBRATE don't need any extra permission. */
public enum RingerModeOption {
    NORMAL, VIBRATE, SILENT
}
