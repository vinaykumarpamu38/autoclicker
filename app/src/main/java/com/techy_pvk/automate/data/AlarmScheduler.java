package com.techy_pvk.automate.data;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;
import com.techy_pvk.automate.receiver.ScheduledTriggerReceiver;

import java.util.Calendar;
import java.util.List;

/**
 * Schedules the next one-shot alarm for each SCHEDULED_TIME sequence. AlarmManager alarms are
 * one-shot by nature, so ScheduledTriggerReceiver calls scheduleNext() again for that same
 * sequence right after it fires, to queue up its next occurrence.
 *
 * Uses an exact alarm (setExactAndAllowWhileIdle) when SCHEDULE_EXACT_ALARM is granted -
 * denied by default on Android 14+ until the user grants it via Settings - and falls back to
 * an inexact one (setAndAllowWhileIdle, which can drift by several minutes under Doze) rather
 * than silently not scheduling anything.
 */
public final class AlarmScheduler {

    private AlarmScheduler() {}

    public static void scheduleAll(Context context, List<AutomationSequence> sequences) {
        for (AutomationSequence sequence : sequences) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger != null && trigger.type == TriggerType.SCHEDULED_TIME && !sequence.steps.isEmpty()) {
                scheduleNext(context, sequence);
            }
        }
    }

    public static void scheduleNext(Context context, AutomationSequence sequence) {
        long nextFireMillis = computeNextFireTime(sequence.trigger);
        if (nextFireMillis < 0) return; // no day of the week is enabled - nothing to schedule

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = pendingIntentFor(context, sequence.id);

        boolean canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMillis, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            // Exact alarm permission was revoked between the check and the call - fall back.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFireMillis, pendingIntent);
        }
    }

    public static void cancel(Context context, String sequenceId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) alarmManager.cancel(pendingIntentFor(context, sequenceId));
    }

    private static PendingIntent pendingIntentFor(Context context, String sequenceId) {
        Intent intent = new Intent(context, ScheduledTriggerReceiver.class);
        intent.putExtra(ScheduledTriggerReceiver.EXTRA_SEQUENCE_ID, sequenceId);
        return PendingIntent.getBroadcast(context, sequenceId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Walks forward from now, day by day (today through +7, so a "same weekday next week"
     *  case is always found even if today's slot already passed), for the first enabled day
     *  whose scheduled time is still in the future. Returns -1 if no day is enabled at all. */
    private static long computeNextFireTime(SequenceTrigger trigger) {
        Calendar now = Calendar.getInstance();
        for (int daysAhead = 0; daysAhead < 8; daysAhead++) {
            Calendar candidate = (Calendar) now.clone();
            candidate.add(Calendar.DAY_OF_YEAR, daysAhead);
            candidate.set(Calendar.HOUR_OF_DAY, trigger.scheduledHour);
            candidate.set(Calendar.MINUTE, trigger.scheduledMinute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);

            int dayIndex = candidate.get(Calendar.DAY_OF_WEEK) - 1; // Calendar.SUNDAY(1) -> index 0
            boolean dayEnabled = trigger.scheduledDays != null && dayIndex >= 0
                    && dayIndex < trigger.scheduledDays.length && trigger.scheduledDays[dayIndex];
            if (dayEnabled && candidate.getTimeInMillis() > now.getTimeInMillis()) {
                return candidate.getTimeInMillis();
            }
        }
        return -1;
    }
}
