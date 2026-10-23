package com.techy_pvk.automate.data;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.techy_pvk.automate.receiver.PaymentReminderReceiver;

/**
 * The reminder chain: an immediate notification when the user taps "Go to Settings" on the
 * payment-guard popup, then - only if each one is swiped away rather than acted on - another
 * at +10 min, +1 hr, and +3 hr, matching PaymentGuardStore.STAGE_DELAYS_MS. Uses inexact alarms
 * (setAndAllowWhileIdle) since a reminder firing a little late under Doze isn't a functional
 * problem the way a missed automation trigger would be, and it avoids needing the exact-alarm
 * permission for this feature at all.
 */
public final class PaymentReminderScheduler {

    private static final String CHANNEL_ID = "payment_guard_reminders";
    private static final int NOTIFICATION_ID = 9001;
    public static final String ACTION_SHOW_REMINDER = "com.techy_pvk.automate.action.SHOW_PAYMENT_REMINDER";
    public static final String ACTION_REMINDER_DISMISSED = "com.techy_pvk.automate.action.PAYMENT_REMINDER_DISMISSED";
    public static final String EXTRA_STAGE = "stage";

    private static final String[] STAGE_MESSAGES = {
            "You turned off Accessibility for a payment - don't forget to turn AutoMate back on.",
            "Still off: AutoMate's Accessibility Service hasn't been re-enabled yet.",
            "Reminder: your automations won't run until Accessibility is turned back on.",
            "Last reminder: AutoMate's Accessibility Service is still off.",
    };

    private PaymentReminderScheduler() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Re-enable Accessibility reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminds you to turn AutoMate's Accessibility Service back on after a payment.");
            manager.createNotificationChannel(channel);
        }
    }

    /** Stage 0 fires immediately (called directly, not via alarm) when the user taps
     *  "Go to Settings" on the popup. Stages 1-3 arrive here via the alarm receiver. */
    public static void showReminder(Context context, int stage) {
        ensureChannel(context);
        if (!PaymentGuardStore.isAwaitingReenable(context)) return; // user already re-enabled it

        Intent tapIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent tapPendingIntent = PendingIntent.getActivity(context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(context, PaymentReminderReceiver.class);
        dismissIntent.setAction(ACTION_REMINDER_DISMISSED);
        dismissIntent.putExtra(EXTRA_STAGE, stage);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(context, stage, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String message = STAGE_MESSAGES[Math.min(stage, STAGE_MESSAGES.length - 1)];
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("Turn AutoMate back on")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(tapPendingIntent)
                .setDeleteIntent(dismissPendingIntent)
                .build();

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
    }

    /** Called by PaymentReminderReceiver when a notification is swiped away - schedules the
     *  next stage, or does nothing if this was the last one. */
    public static void scheduleNextStage(Context context, int dismissedStage) {
        if (!PaymentGuardStore.isAwaitingReenable(context)) return;
        int nextStage = dismissedStage + 1;
        if (nextStage - 1 >= PaymentGuardStore.STAGE_DELAYS_MS.length) return; // that was the last one

        long delay = PaymentGuardStore.STAGE_DELAYS_MS[nextStage - 1];
        PaymentGuardStore.setReminderStage(context, nextStage);

        Intent alarmIntent = new Intent(context, PaymentReminderReceiver.class);
        alarmIntent.setAction(ACTION_SHOW_REMINDER);
        alarmIntent.putExtra(EXTRA_STAGE, nextStage);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, nextStage, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delay, pendingIntent);
        }
    }

    /** Called from AutomationService.onServiceConnected() - the user turned Accessibility back
     *  on, so cancel whatever's pending and clear the notification. */
    public static void cancelAll(Context context) {
        for (int stage = 0; stage <= PaymentGuardStore.STAGE_DELAYS_MS.length; stage++) {
            Intent alarmIntent = new Intent(context, PaymentReminderReceiver.class);
            alarmIntent.setAction(ACTION_SHOW_REMINDER);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, stage, alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
        }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
        PaymentGuardStore.clearReminderCycle(context);
    }
}
