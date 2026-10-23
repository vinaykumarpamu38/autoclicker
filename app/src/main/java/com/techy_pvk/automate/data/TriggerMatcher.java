package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import com.google.android.gms.location.Geofence;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.BatteryConditionType;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.MusicPlaybackCondition;
import com.techy_pvk.automate.model.NotificationEvent;
import com.techy_pvk.automate.model.NotificationLogEntry;
import com.techy_pvk.automate.model.PowerSaveCondition;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.StepType;
import com.techy_pvk.automate.model.TriggerType;
import com.techy_pvk.automate.service.AutomationService;
import com.techy_pvk.automate.service.SequenceRunner;

import java.util.List;
import java.util.Locale;

/**
 * Checks an incoming event (Bluetooth connect, notification posted, geofence crossed, Wi-Fi
 * connected, a scheduled alarm firing, boot completed, a battery condition, or music playback)
 * against every saved sequence's trigger config, and starts a run for each match by
 * broadcasting to AutomationService - the exact same broadcast MainActivity's ▶ button sends.
 *
 * Also handles Auto-Restore: for the three trigger conditions that have a natural opposite
 * already modeled here (Location ENTER/EXIT, Music STARTED/STOPPED_OR_PAUSED, Battery
 * CHARGING_STARTED/CHARGING_STOPPED), a sequence with autoRestore=true gets its restorable
 * setting snapshotted right before it runs, and restored directly - not by re-running the
 * sequence's steps - the moment the opposite condition fires. Restoring is a direct system
 * call (AudioManager.setRingerMode), not a gesture-dispatch run, so it doesn't depend on
 * AutomationService/Accessibility being alive at restore time.
 */
public final class TriggerMatcher {

    private TriggerMatcher() {}

    public static void handleBluetoothConnected(Context context, String deviceName) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.BLUETOOTH_CONNECTED) continue;
            if (!matchesFilter(trigger.bluetoothNameFilter, deviceName)) continue;
            runSequence(context, sequence, null);
        }
    }

    public static void handleWiredHeadsetConnected(Context context) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger != null && trigger.type == TriggerType.WIRED_HEADSET_CONNECTED) {
                runSequence(context, sequence, null);
            }
        }
    }

    public static void handleNotification(Context context, NotificationEvent event) {
        List<AutomationSequence> sequences = SequenceStore.loadAll(context);
        for (AutomationSequence sequence : sequences) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.NOTIFICATION_RECEIVED) continue;

            boolean packageMatches = trigger.notificationPackageName == null
                    || trigger.notificationPackageName.isEmpty()
                    || trigger.notificationPackageName.equals(event.packageName);
            if (!packageMatches) continue;

            boolean textMatches = matchesFilter(trigger.notificationTextFilter, event.title)
                    || matchesFilter(trigger.notificationTextFilter, event.text);
            if (!textMatches) continue;

            if (trigger.storeNotificationData) {
                NotificationLogEntry entry = new NotificationLogEntry();
                entry.sequenceId = sequence.id;
                entry.sequenceName = sequence.name;
                entry.packageName = event.packageName;
                entry.appLabel = event.appLabel;
                entry.title = event.title;
                entry.text = event.text;
                entry.timestampMillis = event.postedAtMillis;
                NotificationLogStore.append(context, entry);
            }

            runSequence(context, sequence, event);
        }
    }

    /** sequenceId is a geofence's requestId, which GeofenceHelper sets to the owning
     *  sequence's own id - see GeofenceBroadcastReceiver. GeofenceHelper always registers
     *  BOTH transitions on every geofence regardless of the trigger's configured direction
     *  (see its class doc), which is exactly what makes the auto-restore-on-exit path here
     *  possible - the exit event always arrives even for an ENTER-configured sequence. */
    public static void handleLocationEvent(Context context, String sequenceId, int geofenceTransition) {
        AutomationSequence sequence = SequenceStore.getById(context, sequenceId);
        if (sequence == null) return;
        SequenceTrigger trigger = sequence.trigger;
        if (trigger == null || trigger.type != TriggerType.LOCATION) return;

        boolean isEnter = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER;
        boolean wantsEnter = trigger.locationTransition == LocationTransition.ENTER;
        if (isEnter == wantsEnter) {
            snapshotForAutoRestoreIfNeeded(context, sequence);
            runSequence(context, sequence, null);
        } else if (sequence.autoRestore) {
            applyAutoRestore(context, sequence);
        }
    }

    public static void handleWifiConnected(Context context, String ssid) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.WIFI_CONNECTED) continue;
            boolean matches = trigger.wifiSsidFilter == null || trigger.wifiSsidFilter.trim().isEmpty()
                    || trigger.wifiSsidFilter.trim().equalsIgnoreCase(ssid);
            if (matches) runSequence(context, sequence, null);
        }
    }

    /** Called by ScheduledTriggerReceiver, which already knows exactly which sequence its
     *  alarm belongs to - no need to scan every saved sequence for this one. */
    public static void handleScheduledFire(Context context, AutomationSequence sequence) {
        if (sequence.trigger != null && sequence.trigger.type == TriggerType.SCHEDULED_TIME) {
            runSequence(context, sequence, null);
        }
    }

    public static void handleBootCompleted(Context context, List<AutomationSequence> sequences) {
        for (AutomationSequence sequence : sequences) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger != null && trigger.type == TriggerType.BOOT_COMPLETED) {
                runSequence(context, sequence, null);
            }
        }
    }

    public static void handleBatteryLevelDropped(Context context, int level) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.BATTERY_STATE) continue;
            if (trigger.batteryConditionType != BatteryConditionType.LEVEL_DROPPED_TO) continue;
            if (level <= trigger.batteryLevelPercent) runSequence(context, sequence, null);
        }
    }

    public static void handleChargingStarted(Context context) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.BATTERY_STATE) continue;
            if (trigger.batteryConditionType == BatteryConditionType.CHARGING_STARTED) {
                snapshotForAutoRestoreIfNeeded(context, sequence);
                runSequence(context, sequence, null);
            }
        }
    }

    public static void handleChargingStopped(Context context) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.BATTERY_STATE) continue;
            if (trigger.batteryConditionType == BatteryConditionType.CHARGING_STOPPED) {
                runSequence(context, sequence, null);
            } else if (trigger.batteryConditionType == BatteryConditionType.CHARGING_STARTED
                    && sequence.autoRestore) {
                applyAutoRestore(context, sequence);
            }
        }
    }

    /** packageName is whichever app's media session actually changed state - see
     *  NotificationTriggerService, which now tracks playback per-package rather than as one
     *  global flag, specifically so this filtering works correctly when more than one app
     *  has an active session at once (e.g. Spotify and a browser tab). */
    public static void handleMusicStarted(Context context, String packageName) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.MUSIC_PLAYBACK_STATE) continue;
            if (!matchesAppFilter(trigger.musicPlaybackPackageFilter, packageName)) continue;
            if (trigger.musicPlaybackCondition == MusicPlaybackCondition.STARTED) {
                snapshotForAutoRestoreIfNeeded(context, sequence);
                runSequence(context, sequence, null);
            }
        }
    }

    /** Only called after NotificationTriggerService's debounce window confirms a pause is
     *  real, not a momentary gap between two tracks - see that class for why. */
    public static void handleMusicStopped(Context context, String packageName) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger == null || trigger.type != TriggerType.MUSIC_PLAYBACK_STATE) continue;
            if (!matchesAppFilter(trigger.musicPlaybackPackageFilter, packageName)) continue;
            if (trigger.musicPlaybackCondition == MusicPlaybackCondition.STOPPED_OR_PAUSED) {
                runSequence(context, sequence, null);
            } else if (trigger.musicPlaybackCondition == MusicPlaybackCondition.STARTED
                    && sequence.autoRestore) {
                applyAutoRestore(context, sequence);
            }
        }
    }

    public static void handlePowerSaveEnabled(Context context) {
        handlePowerSaveCondition(context, PowerSaveCondition.ENABLED);
    }

    public static void handlePowerSaveDisabled(Context context) {
        handlePowerSaveCondition(context, PowerSaveCondition.DISABLED);
    }

    private static void handlePowerSaveCondition(Context context, PowerSaveCondition condition) {
        for (AutomationSequence sequence : SequenceStore.loadAll(context)) {
            SequenceTrigger trigger = sequence.trigger;
            if (trigger != null && trigger.type == TriggerType.POWER_SAVE_MODE_CHANGED
                    && trigger.powerSaveCondition == condition) {
                runSequence(context, sequence, null);
            }
        }
    }

    /** Blank/null filter matches any app - otherwise an exact package match. */
    private static boolean matchesAppFilter(String filterPackage, String actualPackage) {
        return filterPackage == null || filterPackage.trim().isEmpty() || filterPackage.equals(actualPackage);
    }

    // ---- Auto-Restore ----

    /** Snapshots the current value of whatever restorable setting this sequence's steps are
     *  about to change, so it can be put back later. Currently only Set Ringer Mode is
     *  "restorable" - the only step type that changes a system-wide setting we can read back
     *  cheaply. A sequence with no restorable step just has nothing to snapshot. */
    private static void snapshotForAutoRestoreIfNeeded(Context context, AutomationSequence sequence) {
        if (!sequence.autoRestore) return;
        for (GestureStep step : sequence.steps) {
            if (step.type == StepType.SET_RINGER_MODE) {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    RestoreStateStore.saveRingerModeSnapshot(context, sequence.id, audioManager.getRingerMode());
                }
                return; // one ringer-mode snapshot is all this sequence needs
            }
        }
    }

    /** Applies whatever was snapshotted, directly - not by re-running the sequence's steps.
     *  A plain AudioManager call doesn't need AutomationService/Accessibility to be alive. */
    private static void applyAutoRestore(Context context, AutomationSequence sequence) {
        Integer ringerModeSnapshot = RestoreStateStore.takeRingerModeSnapshot(context, sequence.id);
        if (ringerModeSnapshot != null) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) audioManager.setRingerMode(ringerModeSnapshot);
        }
    }

    /** A blank filter matches everything; otherwise a case-insensitive "contains" check. */
    private static boolean matchesFilter(String filter, String value) {
        if (filter == null || filter.trim().isEmpty()) return true;
        if (value == null) return false;
        return value.toLowerCase(Locale.US).contains(filter.trim().toLowerCase(Locale.US));
    }

    private static void runSequence(Context context, AutomationSequence sequence, NotificationEvent event) {
        if (sequence.steps.isEmpty()) return;
        Intent triggerIntent = SequenceRunner.runIntent(context, sequence.id);
        if (event != null) {
            // Lets a Type Text step reference {notification_title} / {notification_text} /
            // {notification_app} in what it types - see AutomationService.resolvePlaceholders().
            triggerIntent.putExtra(AutomationService.EXTRA_NOTIFICATION_TITLE, event.title);
            triggerIntent.putExtra(AutomationService.EXTRA_NOTIFICATION_TEXT, event.text);
            triggerIntent.putExtra(AutomationService.EXTRA_NOTIFICATION_APP, event.appLabel);
        }
        context.sendBroadcast(triggerIntent);
    }
}
