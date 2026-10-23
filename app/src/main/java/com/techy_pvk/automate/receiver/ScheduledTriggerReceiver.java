package com.techy_pvk.automate.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.techy_pvk.automate.data.AlarmScheduler;
import com.techy_pvk.automate.data.SequenceStore;
import com.techy_pvk.automate.data.TriggerMatcher;
import com.techy_pvk.automate.model.AutomationSequence;

/** Fires once for a specific sequence's Scheduled Time trigger, then immediately reschedules
 *  the next occurrence for that same sequence - AlarmManager alarms are one-shot. */
public class ScheduledTriggerReceiver extends BroadcastReceiver {

    public static final String EXTRA_SEQUENCE_ID = "sequence_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String sequenceId = intent.getStringExtra(EXTRA_SEQUENCE_ID);
        if (sequenceId == null) return;

        AutomationSequence sequence = SequenceStore.getById(context, sequenceId);
        if (sequence == null) return; // deleted since this alarm was scheduled

        TriggerMatcher.handleScheduledFire(context, sequence);
        AlarmScheduler.scheduleNext(context, sequence);
    }
}
