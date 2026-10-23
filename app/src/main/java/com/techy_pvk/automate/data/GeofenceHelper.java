package com.techy_pvk.automate.data;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.TriggerType;
import com.techy_pvk.automate.receiver.GeofenceBroadcastReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers one Play Services geofence per LOCATION-trigger sequence (keyed by the
 * sequence's own id, so GeofenceBroadcastReceiver can map a transition straight back to
 * which sequence it belongs to). Both ENTER and EXIT are monitored on every geofence
 * regardless of which one the trigger cares about - the receiver filters by the sequence's
 * configured direction, which is simpler than registering/re-registering per direction.
 *
 * Call registerAll() whenever the saved sequence list changes (SequenceRepository does this
 * after every save/delete, and MainViewModel on each home-screen resume - which also picks up
 * a location permission granted later) and once more on boot (geofences don't survive a
 * reboot on their own).
 */
public final class GeofenceHelper {

    private static final String TAG = "AutoClickerGeofence";

    private GeofenceHelper() {}

    public static void registerAll(Context context, List<AutomationSequence> sequences) {
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "Skipping geofence registration - location permission not granted");
            return;
        }

        GeofencingClient client = LocationServices.getGeofencingClient(context);
        // Clear everything first so a deleted/edited sequence doesn't leave a stale geofence
        // registered under its old id.
        client.removeGeofences(pendingIntent(context));

        List<Geofence> geofences = new ArrayList<>();
        for (AutomationSequence sequence : sequences) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.LOCATION) continue;
            if (sequence.steps.isEmpty()) continue;

            geofences.add(new Geofence.Builder()
                    .setRequestId(sequence.id)
                    .setCircularRegion(trigger.locationLatitude, trigger.locationLongitude, trigger.locationRadiusMeters)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build());
        }

        if (geofences.isEmpty()) return;

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build();

        try {
            client.addGeofences(request, pendingIntent(context))
                    .addOnFailureListener(e -> Log.d(TAG, "addGeofences failed: " + e.getMessage()));
        } catch (SecurityException e) {
            Log.d(TAG, "addGeofences SecurityException - location permission missing at call time");
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0);
        // FLAG_MUTABLE is required here (not FLAG_IMMUTABLE) - Play services needs to attach
        // the transition details to this Intent when it delivers the broadcast.
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    private static boolean hasLocationPermission(Context context) {
        boolean fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!fine) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true; // background location didn't exist as a separate permission yet
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
