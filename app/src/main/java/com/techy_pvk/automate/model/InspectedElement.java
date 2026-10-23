package com.techy_pvk.automate.model;

/**
 * One on-screen element as found by AutomationService's "Inspect Screen" scan - the same
 * accessibility-tree walk used by CLICK_ID/CLICK_TEXT/CLICK_DESC at run time, surfaced back to
 * you so you can see (and copy) the exact value to type into those steps, without a separate
 * tool like Appium or Android Studio's Layout Inspector.
 */
public class InspectedElement {
    /** Short id with no package prefix (e.g. "button_ok"), ready to paste into Click by Resource ID. */
    public String resourceId;
    public String text;
    public String contentDescription;
    public String className;
    public boolean clickable;

    /** -1 for a whole-screen scan entry (order doesn't mean anything special). For a
     *  point-based scan, 0 = the exact element under the dropped marker, 1 = its parent,
     *  2 = its grandparent, and so on - lets the UI show "Tapped element" / "1 level up" /
     *  etc., since the tapped element itself often has no id/text but an ancestor does. */
    public int depth = -1;
}
