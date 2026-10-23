package com.techy_pvk.automate.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.techy_pvk.automate.data.PaymentReminderScheduler;

public class PaymentReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        int stage = intent.getIntExtra(PaymentReminderScheduler.EXTRA_STAGE, 0);

        if (PaymentReminderScheduler.ACTION_SHOW_REMINDER.equals(intent.getAction())) {
            PaymentReminderScheduler.showReminder(context, stage);
        } else if (PaymentReminderScheduler.ACTION_REMINDER_DISMISSED.equals(intent.getAction())) {
            PaymentReminderScheduler.scheduleNextStage(context, stage);
        }
    }
}
