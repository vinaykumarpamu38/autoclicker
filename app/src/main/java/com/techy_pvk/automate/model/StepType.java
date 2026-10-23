package com.techy_pvk.automate.model;

/**
 * The kinds of steps a sequence can be built from.
 *
 *  - TAP, LONG_CLICK, SWIPE, SCROLL: coordinate/gesture-based, dispatched via the
 *    Accessibility gesture API.
 *  - WAIT: a plain pause.
 *  - LAUNCH_APP: opens another app by package name.
 *  - CLICK_TEXT, CLICK_ID, CLICK_DESC: find an on-screen element by its visible text,
 *    Android resource-id, or accessibility content-description, and click it.
 *  - TYPE_TEXT: enters text into a field (found by id/text, or whatever is focused).
 *  - BACK, HOME, RECENTS: system-level navigation actions.
 *  - SET_RINGER_MODE: switches the device between Normal, Vibrate, and Silent.
 *  - SET_DND: switches Do Not Disturb between Off, Priority Only, Alarms Only, Total Silence.
 *  - SET_BRIGHTNESS: sets screen brightness to a fixed percentage (disables adaptive
 *    brightness for that - the two aren't compatible, adaptive brightness overrides a fixed
 *    value continuously).
 */
public enum StepType {
    TAP, LONG_CLICK, SWIPE, SCROLL, WAIT, LAUNCH_APP,
    CLICK_TEXT, CLICK_ID, CLICK_DESC, TYPE_TEXT,
    BACK, HOME, RECENTS, SET_RINGER_MODE, SET_DND, SET_BRIGHTNESS
}
