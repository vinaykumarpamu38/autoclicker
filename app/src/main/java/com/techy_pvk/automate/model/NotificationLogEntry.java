package com.techy_pvk.automate.model;

/** One saved row in the Notification Log - written when a NOTIFICATION_RECEIVED trigger has
 *  storeNotificationData turned on and a matching notification arrives. Viewable/clearable
 *  from NotificationLogActivity. */
public class NotificationLogEntry {
    public String sequenceId;
    public String sequenceName;
    public String packageName;
    public String appLabel;
    public String title;
    public String text;
    public long timestampMillis;
}
