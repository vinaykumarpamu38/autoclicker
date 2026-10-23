package com.techy_pvk.automate.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import com.techy_pvk.automate.data.GeofenceHelper;
import com.techy_pvk.automate.data.TriggerMatcher;

import java.util.List;

/**
 * Fires when a registered geofence (see GeofenceHelper) is entered or exited. Each geofence's
 * requestId is the sequence's own id, so this just needs to check whether the transition
 * direction matches what that sequence's trigger is configured for.
 */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "AutoClickerGeofence";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            if (event != null) Log.d(TAG, "Geofencing error code: " + event.getErrorCode());
            return;
        }

        int transition = event.getGeofenceTransition();
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER && transition != Geofence.GEOFENCE_TRANSITION_EXIT) {
            return; // ignore DWELL and anything else - we only ever register ENTER/EXIT
        }

        List<Geofence> triggeringGeofences = event.getTriggeringGeofences();
        if (triggeringGeofences == null) return;

        for (Geofence geofence : triggeringGeofences) {
            TriggerMatcher.handleLocationEvent(context, geofence.getRequestId(), transition);
        }
    }
}
