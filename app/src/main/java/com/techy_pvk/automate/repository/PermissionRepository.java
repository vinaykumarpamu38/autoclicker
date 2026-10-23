package com.techy_pvk.automate.repository;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Reads the current state of every permission/access grant from the system. Cheap, synchronous
 *  system-service calls - safe on the main thread. */
public class PermissionRepository {

    private final Context context;

    public PermissionRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public PermissionState snapshot() {
        boolean bgApplies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        boolean exactApplies = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        return new PermissionState(
                hasOverlay(),
                isAccessibilityServiceEnabled(),
                hasNotificationListenerAccess(),
                hasDoNotDisturbAccess(),
                Settings.System.canWrite(context),
                hasForegroundLocation(),
                bgApplies && has(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                exactApplies && canScheduleExactAlarms(),
                bgApplies,
                exactApplies);
    }

    /** Dangerous runtime permissions the app wants up front that haven't been granted yet.
     *  Missing BLUETOOTH_CONNECT specifically is why the Bluetooth trigger silently never fires on
     *  Android 12+: it's a runtime permission, not something granted just by declaring it.
     *  Location is deliberately NOT in this list - it goes through its own disclosure screen. */
    @NonNull
    public List<String> missingRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !has(Manifest.permission.POST_NOTIFICATIONS)) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        return missing;
    }

    public boolean hasOverlay() {
        return Settings.canDrawOverlays(context);
    }

    public boolean hasForegroundLocation() {
        return has(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public boolean hasBackgroundLocation() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || has(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    /** True if AutoMate's AccessibilityService is currently enabled in system settings. Reads the
     *  system's own list rather than tracking state ourselves, since the user can turn it off at
     *  any time from outside the app (and Android can auto-revoke it for apps that haven't been
     *  opened in a while). */
    public boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> enabled =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabled == null) return false;
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getId() != null && info.getId().startsWith(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNotificationListenerAccess() {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    private boolean hasDoNotDisturbAccess() {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && manager.isNotificationPolicyAccessGranted();
    }

    private boolean canScheduleExactAlarms() {
        // The SDK check lives inside this method (not only at the call site) so Lint can see
        // that canScheduleExactAlarms() - API 31 - is never reached on older devices.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    private boolean has(String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
