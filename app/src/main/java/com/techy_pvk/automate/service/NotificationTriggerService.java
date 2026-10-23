package com.techy_pvk.automate.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.techy_pvk.automate.data.TriggerMatcher;
import com.techy_pvk.automate.model.NotificationEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Watches every notification posted system-wide, once you've granted this app Notification
 * access under Settings (a separate grant from the Accessibility service - Android treats
 * reading notification content as its own special permission). Each one is handed to
 * TriggerMatcher, which runs any sequence whose trigger is "when a notification is received"
 * and whose app/text filters match.
 *
 * Also monitors media playback for the Music Playback trigger, reusing this same Notification
 * access grant - MediaSessionManager.getActiveSessions() requires an enabled
 * NotificationListenerService as the caller, so no extra permission is needed here.
 *
 * Playback state changes go through a short debounce (see PAUSE_DEBOUNCE_MS) before being
 * treated as a real pause/stop. Track-to-track transitions briefly report a non-PLAYING
 * state too (SKIPPING_TO_NEXT, then BUFFERING) before settling back to PLAYING within well
 * under a second - without the debounce (and without explicitly ignoring those transient
 * states), a track change looks identical to a genuine pause. That ambiguity is exactly what
 * causes Samsung's Modes and Routines "music playing/paused" condition to misfire on track
 * changes: it appears to react to any non-PLAYING state rather than confirming which one it is.
 *
 * All playback/debounce state is tracked per-package (not as one global flag), since more
 * than one app can hold an active media session at once (e.g. Spotify and a browser tab) -
 * this is what makes the Music Playback trigger's per-app filter (musicPlaybackPackageFilter)
 * behave correctly instead of conflating unrelated apps' playback together.
 */
public class NotificationTriggerService extends NotificationListenerService {

    private static final String TAG = "AutoMateMusic";

    // How long a non-playing state must persist before it's treated as a real pause/stop -
    // generous enough to absorb a track change's brief gap, short enough that a real pause
    // is still detected promptly.
    private static final long PAUSE_DEBOUNCE_MS = 1800;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaController, MediaController.Callback> activeCallbacks = new HashMap<>();
    private final Map<String, Boolean> isConsideredPlayingByPackage = new HashMap<>();
    private final Map<String, Runnable> pendingPauseCheckByPackage = new HashMap<>();

    private MediaSessionManager mediaSessionManager;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String packageName = sbn.getPackageName();
        if (getPackageName().equals(packageName)) return; // ignore our own "sequence running" notification

        Notification notification = sbn.getNotification();
        Bundle extras = notification != null ? notification.extras : null;
        CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;

        NotificationEvent event = new NotificationEvent();
        event.packageName = packageName;
        event.appLabel = resolveAppLabel(packageName);
        event.title = title != null ? title.toString() : "";
        event.text = text != null ? text.toString() : "";
        event.postedAtMillis = sbn.getPostTime();

        TriggerMatcher.handleNotification(this, event);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        setUpMediaSessionMonitoring();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSessionManager != null && sessionsChangedListener != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        }
        for (Map.Entry<MediaController, MediaController.Callback> entry : activeCallbacks.entrySet()) {
            entry.getKey().unregisterCallback(entry.getValue());
        }
        activeCallbacks.clear();
        for (Runnable pending : pendingPauseCheckByPackage.values()) {
            handler.removeCallbacks(pending);
        }
        pendingPauseCheckByPackage.clear();
        isConsideredPlayingByPackage.clear();
    }

    private String resolveAppLabel(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    // ---- Music Playback trigger ----

    private void setUpMediaSessionMonitoring() {
        mediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (mediaSessionManager == null) return;
        ComponentName self = new ComponentName(this, NotificationTriggerService.class);

        sessionsChangedListener = this::onActiveSessionsChanged;
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, self);
            onActiveSessionsChanged(mediaSessionManager.getActiveSessions(self));
        } catch (SecurityException e) {
            Log.w(TAG, "No notification access yet - can't monitor media sessions", e);
        }
    }

    private void onActiveSessionsChanged(List<MediaController> controllers) {
        // Stop watching sessions that are no longer active.
        Iterator<Map.Entry<MediaController, MediaController.Callback>> it = activeCallbacks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<MediaController, MediaController.Callback> entry = it.next();
            if (controllers == null || !controllers.contains(entry.getKey())) {
                entry.getKey().unregisterCallback(entry.getValue());
                it.remove();
            }
        }
        if (controllers == null) return;

        for (MediaController controller : controllers) {
            if (activeCallbacks.containsKey(controller)) continue;
            String packageName = controller.getPackageName();
            MediaController.Callback callback = new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    handlePlaybackState(packageName, state);
                }
            };
            controller.registerCallback(callback, handler);
            activeCallbacks.put(controller, callback);
            handlePlaybackState(packageName, controller.getPlaybackState()); // pick up whatever it's already doing
        }
    }

    private void handlePlaybackState(String packageName, PlaybackState state) {
        if (state == null) return;
        int s = state.getState();

        // Transient states a track change reports on its way from one song to the next -
        // never treated as a pause. This is the crux of the fix: Samsung's Routines
        // apparently doesn't make this distinction.
        if (s == PlaybackState.STATE_SKIPPING_TO_NEXT
                || s == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
                || s == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
                || s == PlaybackState.STATE_BUFFERING
                || s == PlaybackState.STATE_CONNECTING) {
            return;
        }

        boolean wasPlaying = Boolean.TRUE.equals(isConsideredPlayingByPackage.get(packageName));

        if (s == PlaybackState.STATE_PLAYING) {
            Runnable pending = pendingPauseCheckByPackage.remove(packageName);
            if (pending != null) handler.removeCallbacks(pending);

            if (!wasPlaying) {
                isConsideredPlayingByPackage.put(packageName, true);
                TriggerMatcher.handleMusicStarted(this, packageName);
            }
            return;
        }

        if (s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_NONE) {
            if (!wasPlaying) return; // already treated as stopped for this app, nothing new

            Runnable existingPending = pendingPauseCheckByPackage.get(packageName);
            if (existingPending != null) handler.removeCallbacks(existingPending);

            Runnable pauseCheck = () -> {
                pendingPauseCheckByPackage.remove(packageName);
                isConsideredPlayingByPackage.put(packageName, false);
                TriggerMatcher.handleMusicStopped(this, packageName);
            };
            pendingPauseCheckByPackage.put(packageName, pauseCheck);
            handler.postDelayed(pauseCheck, PAUSE_DEBOUNCE_MS);
        }
    }
}
