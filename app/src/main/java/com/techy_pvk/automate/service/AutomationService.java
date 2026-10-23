package com.techy_pvk.automate.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


import com.techy_pvk.automate.data.InspectorResultStore;
import com.techy_pvk.automate.data.PaymentGuardStore;
import com.techy_pvk.automate.data.PaymentReminderScheduler;
import com.techy_pvk.automate.data.SequenceStore;
import com.techy_pvk.automate.data.TriggerMatcher;
import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.InspectedElement;
import com.techy_pvk.automate.model.DndMode;
import com.techy_pvk.automate.model.RingerModeOption;
import com.techy_pvk.automate.model.ScrollDirection;
import com.techy_pvk.automate.ui.InspectorResultActivity;
import com.techy_pvk.automate.ui.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a saved AutomationSequence step by step:
 *  - TAP, LONG_CLICK, SWIPE, SCROLL become real gestures via dispatchGesture()
 *  - CLICK_TEXT, CLICK_ID, CLICK_DESC search the current screen (polling for up to each
 *    step's matchTimeoutMs) for a matching element and click it, falling back to a plain
 *    coordinate tap on its bounds if no clickable ancestor is found
 *  - TYPE_TEXT enters text into a found or focused field via ACTION_SET_TEXT; the text can
 *    reference {notification_title}/{notification_text}/{notification_app}, filled in from
 *    whatever notification triggered this run (blank if it was started manually or by Bluetooth)
 *  - WAIT is a plain delay
 *  - LAUNCH_APP opens another app
 *  - BACK / HOME / RECENTS fire the matching global system action
 * Only one sequence runs at a time - starting a new one supersedes whatever was already
 * running, and a persistent notification offers a Stop action for the whole time it's active.
 *
 * Also handles two inspection actions used by the "Inspect Screen" flow, so you can find a
 * step's exact resource ID/text/description without a separate tool:
 *  - ACTION_INSPECT_SUFFIX: scans the whole current screen and lists every element that has
 *    an id, text, or content description.
 *  - ACTION_INSPECT_POINT_SUFFIX: hit-tests a single screen point (dropped via
 *    FloatingPickerService's MODE_INSPECT marker, the same way a Tap step's point is picked)
 *    and returns that element plus its ancestor chain, so if the exact element you tapped has
 *    nothing identifying, you can see which parent view does.
 */
public class AutomationService extends AccessibilityService {

    public static final String ACTION_RUN_SUFFIX = ".RUN_SEQUENCE";
    public static final String ACTION_STOP_SUFFIX = ".STOP_SEQUENCE";
    public static final String ACTION_INSPECT_SUFFIX = ".INSPECT_SCREEN";
    public static final String ACTION_INSPECT_POINT_SUFFIX = ".INSPECT_POINT";
    public static final String EXTRA_SEQUENCE_ID = "sequence_id";
    public static final String EXTRA_INSPECT_X = "inspect_x";
    public static final String EXTRA_INSPECT_Y = "inspect_y";

    /** Set by TriggerMatcher when a run was started by a notification, so a TYPE_TEXT step
     *  can reference what triggered it. Unused (and left blank) for manual/Bluetooth runs. */
    public static final String EXTRA_NOTIFICATION_TITLE = "notification_title";
    public static final String EXTRA_NOTIFICATION_TEXT = "notification_text";
    public static final String EXTRA_NOTIFICATION_APP = "notification_app";

    private static final String CHANNEL_ID = "autoclicker_running";
    private static final int NOTIFICATION_ID = 42;
    private static final String TYPE_TEXT_LOG_TAG = "AutoClickerType";
    private static final String RUN_LOG_TAG = "AutoMateRun";
    // Some older/OEM accessibility stacks don't reliably register gestures shorter than this
    // as real touches - configured step durations (80ms+ for a real tap) are already well
    // above it; this is just a safety floor against a degenerate near-zero value.
    private static final long MIN_GESTURE_DURATION_MS = 20;

    private static final int MAX_INSPECTED_ELEMENTS = 200;
    private static final int MAX_ANCESTOR_CHAIN_DEPTH = 15;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Bumped every time a run starts or is stopped; a scheduled step only fires if the
    // token it was scheduled under still matches, so overlapping runs never race.
    private int runToken = 0;

    // Context captured from whatever triggered the current run, if it was a notification -
    // available to TYPE_TEXT steps via {notification_title} / {notification_text} / {notification_app}.
    private String triggerNotificationTitle;
    private String triggerNotificationText;
    private String triggerNotificationApp;

    // Wi-Fi Connected trigger: tracks the last-seen SSID so onCapabilitiesChanged's repeated
    // firing for the same connection doesn't re-trigger the same sequence over and over.
    private ConnectivityManager.NetworkCallback wifiNetworkCallback;
    private String lastWifiSsid;

    // Battery State trigger: tracks the last-seen level so ACTION_BATTERY_CHANGED's frequent
    // firing (any battery stat changing, not just level) doesn't re-check/re-fire needlessly.
    private int lastBatteryLevel = -1;

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (action.endsWith(ACTION_RUN_SUFFIX)) {
                String sequenceId = intent.getStringExtra(EXTRA_SEQUENCE_ID);
                AutomationSequence sequence = sequenceId != null
                        ? SequenceStore.getById(AutomationService.this, sequenceId) : null;
                if (sequence != null && !sequence.steps.isEmpty()) {
                    triggerNotificationTitle = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE);
                    triggerNotificationText = intent.getStringExtra(EXTRA_NOTIFICATION_TEXT);
                    triggerNotificationApp = intent.getStringExtra(EXTRA_NOTIFICATION_APP);
                    startRun(sequence);
                } else {
                    Toast.makeText(AutomationService.this, "Sequence not found", Toast.LENGTH_SHORT).show();
                }
            } else if (action.endsWith(ACTION_STOP_SUFFIX)) {
                stopRun();
            } else if (action.endsWith(ACTION_INSPECT_SUFFIX)) {
                inspectCurrentScreen();
            } else if (action.endsWith(ACTION_INSPECT_POINT_SUFFIX)) {
                float x = intent.getFloatExtra(EXTRA_INSPECT_X, -1);
                float y = intent.getFloatExtra(EXTRA_INSPECT_Y, -1);
                inspectAtPoint(Math.round(x), Math.round(y));
            }
        }
    };

    // For the Battery State trigger. ACTION_BATTERY_CHANGED can *only* be received by a
    // dynamically registered receiver (it cannot be manifest-declared at all, regardless of
    // the background-broadcast exemption list) - this service is the one long-lived
    // component available to register it against.
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    int percent = Math.round(level * 100f / scale);
                    if (percent != lastBatteryLevel) {
                        lastBatteryLevel = percent;
                        TriggerMatcher.handleBatteryLevelDropped(AutomationService.this, percent);
                    }
                }
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                TriggerMatcher.handleChargingStarted(AutomationService.this);
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                TriggerMatcher.handleChargingStopped(AutomationService.this);
            }
        }
    };

    // For the Wired Headset Connected trigger. Like ACTION_BATTERY_CHANGED, this one can only
    // be received by a dynamically registered receiver - manifest-declared receivers never get
    // it at all, on any Android version, since the system sends it with
    // FLAG_RECEIVER_REGISTERED_ONLY (this predates the newer background-broadcast limits).
    private final BroadcastReceiver wiredHeadsetReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_HEADSET_PLUG.equals(intent.getAction())) return;
            int state = intent.getIntExtra("state", -1);
            if (state == 1) { // 1 = plugged in, 0 = unplugged
                TriggerMatcher.handleWiredHeadsetConnected(AutomationService.this);
            }
        }
    };

    // For the Power Saving Mode trigger. Only detects the change - there's no public API for
    // an app to actually turn Battery Saver on/off itself (the only "set" APIs that exist are
    // locked to OEM-designated or enterprise/MDM apps via signature-level permissions), so
    // this is a trigger only, no matching action step.
    private final BroadcastReceiver powerSaveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager == null) return;
            if (powerManager.isPowerSaveMode()) {
                TriggerMatcher.handlePowerSaveEnabled(AutomationService.this);
            } else {
                TriggerMatcher.handlePowerSaveDisabled(AutomationService.this);
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter();
        filter.addAction(getPackageName() + ACTION_RUN_SUFFIX);
        filter.addAction(getPackageName() + ACTION_STOP_SUFFIX);
        filter.addAction(getPackageName() + ACTION_INSPECT_SUFFIX);
        filter.addAction(getPackageName() + ACTION_INSPECT_POINT_SUFFIX);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, filter);
        }

        IntentFilter batteryFilter = new IntentFilter();
        batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        batteryFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, batteryFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(batteryReceiver, batteryFilter);
        }

        IntentFilter headsetFilter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wiredHeadsetReceiver, headsetFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(wiredHeadsetReceiver, headsetFilter);
        }

        IntentFilter powerSaveFilter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerSaveReceiver, powerSaveFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(powerSaveReceiver, powerSaveFilter);
        }

        registerWifiCallback();
        ensureChannel();

        // If a payment-guard reminder cycle was in progress, the service reconnecting here
        // means the user just turned Accessibility back on themselves - stop reminding them.
        if (PaymentGuardStore.isAwaitingReenable(this)) {
            PaymentReminderScheduler.cancelAll(this);
        }
    }

    // Tracks the last foreground package so we only react to a fresh entry into a watched app,
    // not every internal navigation event within it (TYPE_WINDOW_STATE_CHANGED fires on those
    // too). Not persisted - a service restart starting fresh is fine here.
    private String lastForegroundPackage;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        String packageName = pkg != null ? pkg.toString() : null;
        if (packageName == null || packageName.equals(lastForegroundPackage)) return;
        lastForegroundPackage = packageName;

        if (!PaymentGuardStore.isEnabled(this)) return;
        if (PaymentGuardStore.isAwaitingReenable(this)) return; // already mid reminder-cycle
        if (!PaymentGuardStore.getWatchedPackages(this).contains(packageName)) return;

        startService(new Intent(this, PaymentGuardOverlayService.class));
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(commandReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(wiredHeadsetReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(powerSaveReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (wifiNetworkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if (cm != null) cm.unregisterNetworkCallback(wifiNetworkCallback);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // ---- Wi-Fi Connected trigger ----

    private void registerWifiCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        wifiNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                super.onCapabilitiesChanged(network, capabilities);
                String ssid = extractWifiSsid(capabilities);
                if (ssid != null && !ssid.equals(lastWifiSsid)) {
                    lastWifiSsid = ssid;
                    TriggerMatcher.handleWifiConnected(AutomationService.this, ssid);
                }
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                lastWifiSsid = null; // reconnecting to the same network later counts as fresh again
            }
        };

        try {
            connectivityManager.registerNetworkCallback(request, wifiNetworkCallback);
        } catch (SecurityException e) {
            Log.d(TYPE_TEXT_LOG_TAG, "Wi-Fi network callback registration failed: " + e.getMessage());
        }
    }

    private String extractWifiSsid(NetworkCapabilities capabilities) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TransportInfo transportInfo = capabilities.getTransportInfo();
            return (transportInfo instanceof WifiInfo) ? normalizeSsid(((WifiInfo) transportInfo).getSSID()) : null;
        }
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null) return null;
        WifiInfo info = wifiManager.getConnectionInfo();
        return info != null ? normalizeSsid(info.getSSID()) : null;
    }

    /** WifiInfo.getSSID() wraps the name in quotes and returns a literal "<unknown ssid>"
     *  string when the real name can't be read (usually a missing location permission). */
    private String normalizeSsid(String ssid) {
        if (ssid == null || ssid.contains("<unknown ssid>")) return null;
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    // ---- Run control ----

    private void startRun(AutomationSequence sequence) {
        runToken++; // supersede any run already in progress
        showRunningNotification(sequence.name);
        runStep(sequence, 0, 0, runToken);
    }

    private void stopRun() {
        runToken++; // invalidates any step still scheduled
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show();
    }

    private void runStep(AutomationSequence sequence, int stepIndex, int iteration, int token) {
        if (token != runToken) {
            Log.d(RUN_LOG_TAG, "Run token stale (" + token + " vs current " + runToken
                    + ") - a newer trigger/run superseded this one at step " + stepIndex);
            return;
        }

        if (stepIndex >= sequence.steps.size()) {
            int nextIteration = iteration + 1;
            boolean shouldRepeat = sequence.repeatCount == 0 || nextIteration < sequence.repeatCount;
            if (shouldRepeat) {
                runStep(sequence, 0, nextIteration, token);
            } else {
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
            }
            return;
        }

        GestureStep step = sequence.steps.get(stepIndex);
        Log.d(RUN_LOG_TAG, "Step " + stepIndex + " (" + step.type + "): scheduling, delayBeforeMs=" + step.delayBeforeMs);
        long scheduledAt = SystemClock.elapsedRealtime();
        handler.postDelayed(() -> {
            long actualDelay = SystemClock.elapsedRealtime() - scheduledAt;
            if (token != runToken) {
                Log.d(RUN_LOG_TAG, "Step " + stepIndex + " CANCELLED before firing, " + actualDelay
                        + "ms after being scheduled (requested " + step.delayBeforeMs + "ms) - token changed");
                return;
            }
            Log.d(RUN_LOG_TAG, "Step " + stepIndex + " (" + step.type + ") firing now - actual delay was "
                    + actualDelay + "ms (requested " + step.delayBeforeMs + "ms)");
            performStep(step, token, () -> {
                if (token != runToken) {
                    Log.d(RUN_LOG_TAG, "Step " + stepIndex + " finished but run was cancelled before advancing");
                    return;
                }
                runStep(sequence, stepIndex + 1, iteration, token);
            });
        }, Math.max(0, step.delayBeforeMs));
    }

    private void performStep(GestureStep step, int token, Runnable onDone) {
        switch (step.type) {
            case TAP:
            case LONG_CLICK:
                // A long-click is just a tap-shaped gesture held long enough (durationMs) to
                // clear the platform's long-press timeout - dispatchTap already handles that.
                dispatchTap(step.x1, step.y1, step.durationMs, onDone);
                break;
            case SWIPE:
                dispatchSwipe(step.x1, step.y1, step.x2, step.y2, step.durationMs, onDone);
                break;
            case SCROLL:
                dispatchScroll(step, onDone);
                break;
            case LAUNCH_APP:
                launchApp(step.packageName);
                onDone.run();
                break;
            case CLICK_TEXT:
                clickByMatcher(step, this::findNodeByText, token, onDone);
                break;
            case CLICK_ID:
                clickByMatcher(step, this::findNodeById, token, onDone);
                break;
            case CLICK_DESC:
                clickByMatcher(step, this::findNodeByDesc, token, onDone);
                break;
            case TYPE_TEXT:
                performTypeText(step, token, onDone);
                break;
            case BACK:
                performGlobalAction(GLOBAL_ACTION_BACK);
                onDone.run();
                break;
            case HOME:
                performGlobalAction(GLOBAL_ACTION_HOME);
                onDone.run();
                break;
            case RECENTS:
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                onDone.run();
                break;
            case SET_RINGER_MODE:
                applyRingerMode(step.ringerMode);
                onDone.run();
                break;
            case SET_DND:
                applyDndMode(step.dndMode);
                onDone.run();
                break;
            case SET_BRIGHTNESS:
                applyBrightness(step.brightnessPercent);
                onDone.run();
                break;
            case WAIT:
            default:
                onDone.run();
                break;
        }
    }

    /** VIBRATE and NORMAL don't need any special permission. SILENT specifically requires
     *  Do Not Disturb access (Notification Policy Access) - without it, setRingerMode()
     *  silently no-ops on API 23+, so this checks first and just skips with a toast rather
     *  than pretending it worked. */
    private void applyRingerMode(RingerModeOption mode) {
        if (mode == null) mode = RingerModeOption.NORMAL;
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) return;

        if (mode == RingerModeOption.SILENT) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            boolean hasDndAccess = notificationManager != null && notificationManager.isNotificationPolicyAccessGranted();
            if (!hasDndAccess) {
                Toast.makeText(this, "Silent needs Do Not Disturb access (grant it in Settings) - skipped",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        int ringerMode = mode == RingerModeOption.SILENT ? AudioManager.RINGER_MODE_SILENT
                : mode == RingerModeOption.VIBRATE ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_NORMAL;
        audioManager.setRingerMode(ringerMode);
    }

    /** All four modes need Do Not Disturb access, not just the "on" ones - Android treats
     *  setInterruptionFilter(NONE) (turning DND OFF) as requiring the same permission as
     *  turning it on, since either direction is "changing DND policy". */
    private void applyDndMode(DndMode mode) {
        if (mode == null) mode = DndMode.OFF;
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) return;
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            Toast.makeText(this, "Set Do Not Disturb needs Do Not Disturb access (grant it in Settings) - skipped",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int filter;
        switch (mode) {
            case TOTAL_SILENCE:
                filter = NotificationManager.INTERRUPTION_FILTER_NONE;
                break;
            case ALARMS_ONLY:
                filter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
                break;
            case PRIORITY_ONLY:
                filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
                break;
            case OFF:
            default:
                filter = NotificationManager.INTERRUPTION_FILTER_ALL;
                break;
        }
        notificationManager.setInterruptionFilter(filter);
    }

    /** Needs WRITE_SETTINGS - a special permission with its own consent screen, same pattern
     *  as Overlay/Accessibility. Also switches brightness mode to manual first: adaptive
     *  brightness would just override a fixed value continuously, defeating the step. */
    private void applyBrightness(int percent) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Set Brightness needs \"Modify system settings\" access (grant it in Settings) - skipped",
                    Toast.LENGTH_LONG).show();
            return;
        }
        int clamped = Math.max(1, Math.min(100, percent));
        int rawValue = Math.round(clamped * 255f / 100f);
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, rawValue);
    }

    private void dispatchTap(float x, float y, long durationMs, Runnable onDone) {
        Path path = new Path();
        path.moveTo(x, y);
        // A perfectly stationary (moveTo-only) path is a true zero-length stroke - some older
        // OEM accessibility stacks (seen on Android 7-9 Samsung/MIUI builds) don't reliably
        // recognize that as a tap. A sub-pixel move is visually/functionally identical to a
        // real tap but registers consistently across versions.
        path.lineTo(x + 1, y + 1);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(MIN_GESTURE_DURATION_MS, durationMs));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                onDone.run();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                onDone.run();
            }
        }, handler); // explicit main-thread handler, not null - more predictable across OEM builds
    }

    private void dispatchSwipe(float x1, float y1, float x2, float y2, long durationMs, Runnable onDone) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(MIN_GESTURE_DURATION_MS, durationMs));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                onDone.run();
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                onDone.run();
            }
        }, handler); // explicit main-thread handler, not null - more predictable across OEM builds
    }

    private void dispatchScroll(GestureStep step, Runnable onDone) {
        // Simulated as a full-screen swipe, same mechanism as the SWIPE step - no window
        // content access is needed for this one. Named from the content's point of view
        // (e.g. DOWN reveals what's below), which is the opposite finger direction for the
        // vertical case: scrolling down means the finger swipes up.
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int w = metrics.widthPixels;
        int h = metrics.heightPixels;
        ScrollDirection direction = step.scrollDirection != null ? step.scrollDirection : ScrollDirection.DOWN;
        float x1, y1, x2, y2;
        switch (direction) {
            case UP:
                x1 = w / 2f; y1 = h * 0.3f; x2 = w / 2f; y2 = h * 0.7f;
                break;
            case LEFT:
                x1 = w * 0.3f; y1 = h / 2f; x2 = w * 0.7f; y2 = h / 2f;
                break;
            case RIGHT:
                x1 = w * 0.7f; y1 = h / 2f; x2 = w * 0.3f; y2 = h / 2f;
                break;
            case DOWN:
            default:
                x1 = w / 2f; y1 = h * 0.7f; x2 = w / 2f; y2 = h * 0.3f;
                break;
        }
        dispatchSwipe(x1, y1, x2, y2, Math.max(1, step.durationMs), onDone);
    }

    // ---- Finding elements on screen (CLICK_TEXT / CLICK_ID / CLICK_DESC / TYPE_TEXT) ----
    //
    // These need canRetrieveWindowContent (set in accessibility_service_config.xml). The
    // target screen may still be loading right when a step fires (e.g. right after a
    // LAUNCH_APP step), so matches are polled for every 250ms up to the step's
    // matchTimeoutMs rather than checked just once.

    private interface NodeFinder {
        AccessibilityNodeInfo find(AccessibilityNodeInfo root, String value);
    }

    private interface NodeCallback {
        void onFound(AccessibilityNodeInfo node);
    }

    private void clickByMatcher(GestureStep step, NodeFinder finder, int token, Runnable onDone) {
        findNodeWithRetry(step.targetValue, step.matchTimeoutMs, finder, token, node -> {
            if (token != runToken) return;
            if (node != null) {
                performClickOnNode(node, onDone);
            } else {
                Toast.makeText(this, "Couldn't find a match: " + step.targetValue, Toast.LENGTH_SHORT).show();
                onDone.run();
            }
        });
    }

    private void findNodeWithRetry(String value, long timeoutMs, NodeFinder finder, int token, NodeCallback callback) {
        long deadline = SystemClock.elapsedRealtime() + Math.max(0, timeoutMs);
        attemptFind(value, finder, token, deadline, callback);
    }

    private void attemptFind(String value, NodeFinder finder, int token, long deadline, NodeCallback callback) {
        if (token != runToken) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo match = root != null ? finder.find(root, value) : null;
        if (match != null || SystemClock.elapsedRealtime() >= deadline) {
            callback.onFound(match);
            return;
        }
        handler.postDelayed(() -> attemptFind(value, finder, token, deadline, callback), 250);
    }

    /** Clicks `node` if it's clickable, else walks up to the nearest clickable ancestor
     *  (text/icons are often inside a clickable parent layout); if nothing in the chain is
     *  clickable, falls back to a plain coordinate tap on the node's on-screen bounds. */
    private void performClickOnNode(AccessibilityNodeInfo node, Runnable onDone) {
        AccessibilityNodeInfo clickable = node;
        while (clickable != null && !clickable.isClickable()) {
            clickable = clickable.getParent();
        }
        if (clickable != null) {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            onDone.run();
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.isEmpty()) {
            dispatchTap(bounds.centerX(), bounds.centerY(), 80, onDone);
        } else {
            onDone.run();
        }
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String value) {
        if (root == null || value == null || value.trim().isEmpty()) return null;
        String needle = value.trim();
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(needle);
        if (matches == null || matches.isEmpty()) return null;
        for (AccessibilityNodeInfo node : matches) {
            CharSequence text = node.getText();
            if (text != null && text.toString().equalsIgnoreCase(needle)) return node;
        }
        return matches.get(0); // fall back to the first partial (contains) match
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String value) {
        if (root == null || value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        CharSequence pkg = root.getPackageName();
        // Accept either a bare id ("button_ok") or a fully-qualified one ("pkg:id/button_ok").
        String fullId = v.contains(":id/") ? v : (pkg != null ? pkg + ":id/" + v : v);
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(fullId);
        return (matches != null && !matches.isEmpty()) ? matches.get(0) : null;
    }

    private AccessibilityNodeInfo findNodeByDesc(AccessibilityNodeInfo root, String value) {
        if (root == null || value == null || value.trim().isEmpty()) return null;
        String needle = value.trim();
        AccessibilityNodeInfo exact = searchByDesc(root, needle, true);
        return exact != null ? exact : searchByDesc(root, needle, false);
    }

    /** There's no findAccessibilityNodeInfosByContentDescription(), so this walks the tree
     *  by hand; exactOnly=true is tried first so an exact match always wins over a longer
     *  node that merely contains the search text. */
    private AccessibilityNodeInfo searchByDesc(AccessibilityNodeInfo node, String needle, boolean exactOnly) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            String d = desc.toString();
            boolean matches = exactOnly ? d.equalsIgnoreCase(needle)
                    : d.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
            if (matches) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = searchByDesc(node.getChild(i), needle, exactOnly);
            if (result != null) return result;
        }
        return null;
    }

    // ---- Type text ----
    //
    // The step's target (if any) can resolve to two very different kinds of node:
    //  - the actual editable field itself (most apps' simple search/username boxes) -
    //    type into it directly, no click needed.
    //  - an entry point that merely opens the real field on click (common for apps with a
    //    "Search" button that navigates to a dedicated search screen, or a field that isn't
    //    visible/attached until tapped) - click through it (reusing the same
    //    clickable-ancestor/coordinate-tap logic the Click-by-* steps use), then poll for
    //    whatever editable node shows up, since it may take a moment to appear and may not
    //    register as "input-focused" the instant it does (custom/Compose text fields in
    //    particular are inconsistent about this).
    //
    // Once a candidate node is in hand, ACTION_SET_TEXT is tried first; if the target
    // rejects it (returns false - some custom widgets don't implement it), a clipboard
    // select-all-and-paste is tried as a fallback.

    private void performTypeText(GestureStep step, int token, Runnable onDone) {
        String target = step.targetValue;
        if (target == null || target.trim().isEmpty()) {
            resolveEditableNode(token, step.matchTimeoutMs, node -> {
                if (token != runToken) return;
                enterText(node, step.textToType, onDone);
            });
            return;
        }

        findNodeWithRetry(target, step.matchTimeoutMs, this::resolveTypeTarget, token, candidate -> {
            if (token != runToken) return;
            if (candidate != null && candidate.isEditable()) {
                // Already the field itself - no click-through needed.
                enterText(candidate, step.textToType, onDone);
                return;
            }
            if (candidate != null) {
                Log.d(TYPE_TEXT_LOG_TAG, "Target isn't directly editable (" + describe(candidate)
                        + ") - clicking through to reveal the real field");
                performClickOnNode(candidate, () -> {
                    if (token != runToken) return;
                    resolveEditableNode(token, 2000, node -> {
                        if (token != runToken) return;
                        enterText(node, step.textToType, onDone);
                    });
                });
                return;
            }
            // No id/text match at all - fall back to whatever's currently focused/editable.
            resolveEditableNode(token, step.matchTimeoutMs, node -> {
                if (token != runToken) return;
                enterText(node, step.textToType, onDone);
            });
        });
    }

    private AccessibilityNodeInfo resolveTypeTarget(AccessibilityNodeInfo root, String value) {
        AccessibilityNodeInfo byId = findNodeById(root, value);
        return byId != null ? byId : findNodeByText(root, value);
    }

    /** Polls for the best available editable node: whatever currently has input focus (if it
     *  IS actually editable - some apps report focus on a non-editable wrapper), else the
     *  first editable node found anywhere in the tree. Keeps trying every 250ms up to
     *  timeoutMs, since a freshly-opened field can take a moment to attach or gain focus. */
    private void resolveEditableNode(int token, long timeoutMs, NodeCallback callback) {
        long deadline = SystemClock.elapsedRealtime() + Math.max(0, timeoutMs);
        attemptResolveEditableNode(token, deadline, callback);
    }

    private void attemptResolveEditableNode(int token, long deadline, NodeCallback callback) {
        if (token != runToken) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo node = null;
        if (root != null) {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            node = (focused != null && focused.isEditable()) ? focused : findFirstEditableNode(root);
        }
        if (node != null || SystemClock.elapsedRealtime() >= deadline) {
            callback.onFound(node);
            return;
        }
        handler.postDelayed(() -> attemptResolveEditableNode(token, deadline, callback), 250);
    }

    private AccessibilityNodeInfo findFirstEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findFirstEditableNode(node.getChild(i));
            if (result != null) return result;
        }
        return null;
    }

    private void enterText(AccessibilityNodeInfo node, String rawText, Runnable onDone) {
        String text = resolvePlaceholders(rawText);

        if (node == null) {
            Log.d(TYPE_TEXT_LOG_TAG, "No editable field found to type into");
            Toast.makeText(this, "No text field found to type into", Toast.LENGTH_SHORT).show();
            onDone.run();
            return;
        }

        if (!node.isFocused()) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean setTextOk = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        Log.d(TYPE_TEXT_LOG_TAG, "ACTION_SET_TEXT on " + describe(node) + " = " + setTextOk);

        boolean pasteOk = false;
        if (!setTextOk) {
            pasteOk = pasteUsingClipboard(node, text);
            Log.d(TYPE_TEXT_LOG_TAG, "Clipboard paste fallback = " + pasteOk);
        }

        if (!setTextOk && !pasteOk) {
            Toast.makeText(this, "Couldn't type into that field - it may not support text entry", Toast.LENGTH_SHORT).show();
        }
        onDone.run();
    }

    /** Fallback for fields that reject ACTION_SET_TEXT outright (some custom widgets don't
     *  implement it) - selects any existing content so the paste replaces rather than just
     *  inserting, puts the text on the clipboard, then asks the field to paste it. */
    private boolean pasteUsingClipboard(AccessibilityNodeInfo node, String text) {
        CharSequence existing = node.getText();
        int length = existing != null ? existing.length() : 0;
        if (length > 0) {
            Bundle selectArgs = new Bundle();
            selectArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            selectArgs.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length);
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs);
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("automation", text));
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }

    /** Substitutes {notification_title} / {notification_text} / {notification_app} with
     *  whatever notification triggered this run - blank for manual/Bluetooth-triggered runs. */
    private String resolvePlaceholders(String text) {
        if (text == null) return "";
        return text
                .replace("{notification_title}", nullToEmpty(triggerNotificationTitle))
                .replace("{notification_text}", nullToEmpty(triggerNotificationText))
                .replace("{notification_app}", nullToEmpty(triggerNotificationApp));
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private String describe(AccessibilityNodeInfo node) {
        if (node == null) return "null";
        String cls = node.getClassName() != null ? node.getClassName().toString() : "?";
        String id = node.getViewIdResourceName();
        return cls + (id != null ? " [" + id + "]" : "") + " editable=" + node.isEditable()
                + " focused=" + node.isFocused();
    }

    private void launchApp(String packageName) {
        if (packageName == null) return;
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        }
    }

    // ---- Inspect Screen: finds every ID/text/description on screen so you can copy the exact
    // value a Click-by-* or Type Text step needs, without a separate tool like Appium. ----

    private void inspectCurrentScreen() {
        // MainActivity just called moveTaskToBack() right before sending this - give that
        // window transition a moment to finish before reading "the active window" (the same
        // reason every step defaults to a 500ms delayBeforeMs).
        handler.postDelayed(() -> {
            List<InspectedElement> elements = collectInspectedElements();
            saveInspectResults(elements, false);
        }, 500);
    }

    private void inspectAtPoint(int x, int y) {
        // No artificial delay here - dragging the marker into place already gives the target
        // app (and, if MainActivity just launched it, its cold start) plenty of time to settle.
        List<InspectedElement> chain = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo target = findNodeAtPoint(root, x, y);
            AccessibilityNodeInfo current = target;
            int depth = 0;
            while (current != null && depth < MAX_ANCESTOR_CHAIN_DEPTH) {
                chain.add(toInspectedElement(current, depth));
                current = current.getParent();
                depth++;
            }
        }
        saveInspectResults(chain, true);
    }

    private void saveInspectResults(List<InspectedElement> elements, boolean isPointMode) {
        // Hand the result to InspectorResultStore (shared with InspectorViewModel) rather than
        // writing prefs directly, so the file/key names live in exactly one place.
        InspectorResultStore.save(this, elements, resolveForegroundAppLabel(), isPointMode);

        Intent resultIntent = new Intent(this, InspectorResultActivity.class);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(resultIntent);
    }

    private List<InspectedElement> collectInspectedElements() {
        List<InspectedElement> results = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) collectElements(root, results);
        return results;
    }

    /** Same idea as CLICK_TEXT/CLICK_ID/CLICK_DESC's matching - just collecting every
     *  candidate instead of stopping at the first match. Skips pure layout containers that
     *  have no id/text/description of their own, since those aren't useful match targets. */
    private void collectElements(AccessibilityNodeInfo node, List<InspectedElement> out) {
        if (node == null || out.size() >= MAX_INSPECTED_ELEMENTS) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        boolean hasIdentity = node.getViewIdResourceName() != null
                || (text != null && text.length() > 0)
                || (desc != null && desc.length() > 0);
        if (hasIdentity) {
            out.add(toInspectedElement(node, -1));
        }

        for (int i = 0; i < node.getChildCount() && out.size() < MAX_INSPECTED_ELEMENTS; i++) {
            collectElements(node.getChild(i), out);
        }
    }

    /** Hit-tests the accessibility tree for the most specific (deepest) node whose on-screen
     *  bounds contain (x, y) - the same point FloatingPickerService's marker was dropped on. */
    private AccessibilityNodeInfo findNodeAtPoint(AccessibilityNodeInfo node, int x, int y) {
        if (node == null) return null;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) return null;

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = findNodeAtPoint(node.getChild(i), x, y);
            if (match != null) return match;
        }
        return node; // no child is a more specific match - this is the deepest one containing the point
    }

    private InspectedElement toInspectedElement(AccessibilityNodeInfo node, int depth) {
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String viewId = node.getViewIdResourceName(); // e.g. "com.app:id/button_ok", or null

        InspectedElement element = new InspectedElement();
        element.resourceId = viewId != null && viewId.contains(":id/")
                ? viewId.substring(viewId.indexOf(":id/") + 4) : viewId;
        element.text = text != null ? text.toString() : "";
        element.contentDescription = desc != null ? desc.toString() : "";
        element.className = node.getClassName() != null ? node.getClassName().toString() : "";
        element.clickable = node.isClickable();
        element.depth = depth;
        return element;
    }

    private String resolveForegroundAppLabel() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        CharSequence pkg = root != null ? root.getPackageName() : null;
        if (pkg == null) return "the current screen";
        try {
            return getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(pkg.toString(), 0))
                    .toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg.toString();
        }
    }

    // ---- Notification with a Stop action, shown for as long as a run is active ----

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Sequence running", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }

    private void showRunningNotification(String sequenceName) {
        Intent stopIntent = new Intent(getPackageName() + ACTION_STOP_SUFFIX);
        stopIntent.setPackage(getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Running \"" + sequenceName + "\"")
                .setContentText("Tap Stop to cancel")
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent);

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Notification permission not granted - the sequence still runs, just without the
            // Stop notification. Re-run after granting it under Settings if you need to cancel.
        }
    }
}