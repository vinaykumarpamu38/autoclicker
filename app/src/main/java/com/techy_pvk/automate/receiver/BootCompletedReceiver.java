package com.techy_pvk.automate.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.techy_pvk.automate.data.AlarmScheduler;
import com.techy_pvk.automate.data.GeofenceHelper;
import com.techy_pvk.automate.data.SequenceStore;
import com.techy_pvk.automate.data.TriggerMatcher;
import com.techy_pvk.automate.model.AutomationSequence;

import java.util.List;

/**
 * Handles device restart in three ways:
 *  - re-registers every Location trigger's geofence (Android clears all geofences on reboot)
 *  - reschedules every Scheduled Time trigger's alarm (also cleared on reboot)
 *  - runs any sequence whose trigger is "when the device boots"
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        List<AutomationSequence> sequences = SequenceStore.loadAll(context);
        GeofenceHelper.registerAll(context, sequences);
        AlarmScheduler.scheduleAll(context, sequences);
        TriggerMatcher.handleBootCompleted(context, sequences);
    }
}
