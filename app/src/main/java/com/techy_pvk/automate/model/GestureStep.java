package com.techy_pvk.automate.model;

import java.util.Locale;

/**
 * One step in an automation sequence. Which fields matter depends on `type`:
 *  - TAP:        (x1, y1) is the tap point
 *  - LONG_CLICK: (x1, y1) is the point, held for durationMs (long enough to register as a
 *                long-press, not a tap)
 *  - SWIPE:      drags from (x1, y1) to (x2, y2) over durationMs
 *  - SCROLL:     swipes the whole screen in `scrollDirection` over durationMs
 *  - WAIT:       just a pause, no gesture
 *  - LAUNCH_APP: opens `packageName`
 *  - CLICK_TEXT / CLICK_ID / CLICK_DESC: finds an on-screen element whose text / resource-id
 *                / content-description matches `targetValue` (polling for up to
 *                matchTimeoutMs, in case the screen hasn't finished loading yet) and clicks it
 *  - TYPE_TEXT:  enters `textToType` into the field matching `targetValue` (by id or text),
 *                or the currently-focused field if `targetValue` is blank
 *  - BACK / HOME / RECENTS: system navigation, no extra fields
 *
 * `delayBeforeMs` is how long to wait before this step runs - use it to give the
 * previous step's screen time to settle (an app launching, a page transitioning)
 * before the next gesture fires.
 */
public class GestureStep {
    public StepType type;
    public float x1, y1;
    public float x2, y2;
    public long delayBeforeMs = 500;
    public long durationMs = 100;
    public String packageName;
    public String appLabel;

    /** Text / resource-id / content-description to search for (CLICK_* and optionally TYPE_TEXT). */
    public String targetValue;
    /** How long to keep polling for targetValue to appear before giving up, in ms. */
    public long matchTimeoutMs = 3000;
    /** Text to enter for a TYPE_TEXT step. */
    public String textToType;
    /** Direction for a SCROLL step. */
    public ScrollDirection scrollDirection;
    /** Which mode for a SET_RINGER_MODE step. */
    public RingerModeOption ringerMode;
    /** Which mode for a SET_DND step. */
    public DndMode dndMode;
    /** 1-100, for a SET_BRIGHTNESS step. */
    public int brightnessPercent = 50;

    public String summary() {
        String wait = "Wait " + delayBeforeMs + "ms, then ";
        switch (type) {
            case TAP:
                return wait + "tap (" + (int) x1 + ", " + (int) y1 + ")";
            case LONG_CLICK:
                return wait + "long-click (" + (int) x1 + ", " + (int) y1 + ") for " + durationMs + "ms";
            case SWIPE:
                return wait + "swipe (" + (int) x1 + "," + (int) y1 + ") \u2192 ("
                        + (int) x2 + "," + (int) y2 + ") over " + durationMs + "ms";
            case SCROLL:
                return wait + "scroll " + (scrollDirection != null
                        ? scrollDirection.name().toLowerCase(Locale.US) : "down");
            case WAIT:
                return "Wait " + delayBeforeMs + "ms";
            case LAUNCH_APP:
                return wait + "open " + appLabel;
            case CLICK_TEXT:
                return wait + "click text \"" + targetValue + "\"";
            case CLICK_ID:
                return wait + "click id \"" + targetValue + "\"";
            case CLICK_DESC:
                return wait + "click description \"" + targetValue + "\"";
            case TYPE_TEXT:
                return wait + "type \"" + truncate(textToType) + "\"" + (targetValue != null
                        && !targetValue.isEmpty() ? " into \"" + targetValue + "\"" : " into focused field");
            case BACK:
                return wait + "press Back";
            case HOME:
                return wait + "press Home";
            case RECENTS:
                return wait + "open Recent Apps";
            case SET_RINGER_MODE:
                return wait + "set ringer to " + (ringerMode != null
                        ? ringerMode.name().toLowerCase(Locale.US) : "normal");
            case SET_DND:
                return wait + "set Do Not Disturb to " + (dndMode != null
                        ? dndMode.name().toLowerCase(Locale.US).replace('_', ' ') : "off");
            case SET_BRIGHTNESS:
                return wait + "set brightness to " + brightnessPercent + "%";
            default:
                return "Step";
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 24 ? s.substring(0, 24) + "\u2026" : s;
    }
}
