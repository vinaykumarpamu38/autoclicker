package com.techy_pvk.automate.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import androidx.annotation.Nullable;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.data.PaymentGuardStore;
import com.techy_pvk.automate.data.PaymentReminderScheduler;

/**
 * A true overlay popup (not a notification) shown the moment a watched payment app comes to
 * the foreground - see AutomationService's accessibility event handling for the detection
 * side. Tapping "Go to Settings" opens Accessibility Settings and starts the reminder chain
 * (see PaymentReminderScheduler); "Not Now" just dismisses this one instance.
 */
public class PaymentGuardOverlayService extends Service {

    private WindowManager windowManager;
    private View overlayView;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (overlayView != null) return START_NOT_STICKY; // already showing one, don't stack

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_payment_guard, null);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        Button settingsButton = overlayView.findViewById(R.id.btn_payment_guard_settings);
        Button dismissButton = overlayView.findViewById(R.id.btn_payment_guard_dismiss);

        settingsButton.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settingsIntent);

            PaymentGuardStore.startReminderCycle(this);
            PaymentReminderScheduler.showReminder(this, 0);
            stopSelf();
        });

        dismissButton.setOnClickListener(v -> stopSelf());

        windowManager.addView(overlayView, params);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
            }
        }
        overlayView = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
