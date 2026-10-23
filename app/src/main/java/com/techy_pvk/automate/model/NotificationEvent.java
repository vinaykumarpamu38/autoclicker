package com.techy_pvk.automate.model;

/** One notification as seen by NotificationTriggerService, before it's matched against any
 *  sequence's trigger filters. Not persisted directly - see NotificationLogEntry for the
 *  version that gets saved to the on-device log. */
public class NotificationEvent {
    public String packageName;
    public String appLabel;
    public String title;
    public String text;
    public long postedAtMillis;
}
