package com.techy_pvk.automate.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.ui.MainActivity;
import com.techy_pvk.automate.ui.SequenceBuilderActivity;

/**
 * Floating overlay used to visually capture screen coordinates - for a TAP, LONG_CLICK, or
 * SWIPE step (returns to SequenceBuilderActivity), or for an Inspect Screen point pick
 * (hands off to AutomationService instead, to hit-test whatever's under the marker). Shows
 * one draggable marker (tap / long click / inspect) or two (swipe start "A" / end "B"), plus
 * a small Save-or-Inspect/Cancel control panel.
 */
public class FloatingPickerService extends Service {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_TAP = "TAP";
    public static final String MODE_LONG_CLICK = "LONG_CLICK";
    public static final String MODE_SWIPE = "SWIPE";
    public static final String MODE_INSPECT = "INSPECT";

    public static final String PREFS = "AutoClickerPicker";
    public static final String KEY_PENDING_TYPE = "pending_type"; // MODE_TAP, MODE_LONG_CLICK, or MODE_SWIPE once saved
    public static final String KEY_X1 = "pending_x1";
    public static final String KEY_Y1 = "pending_y1";
    public static final String KEY_X2 = "pending_x2";
    public static final String KEY_Y2 = "pending_y2";

    private WindowManager windowManager;
    private View markerA;
    private View markerB;
    private View controlsView;
    private String mode = MODE_TAP;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String requested = intent != null ? intent.getStringExtra(EXTRA_MODE) : null;
        mode = MODE_SWIPE.equals(requested) ? MODE_SWIPE
                : MODE_LONG_CLICK.equals(requested) ? MODE_LONG_CLICK
                : MODE_INSPECT.equals(requested) ? MODE_INSPECT
                : MODE_TAP;
        setupOverlay();
        return START_NOT_STICKY;
    }

    @SuppressLint("InflateParams")
    private void setupOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        boolean isLongClick = MODE_LONG_CLICK.equals(mode);
        boolean isInspect = MODE_INSPECT.equals(mode);
        int markerColor = isInspect ? 0xFF9C27B0 : isLongClick ? 0xFFFF9800 : 0xFF4CAF50; // purple / orange / green
        String markerLabel = isInspect ? "I" : isLongClick ? "H" : "A";
        markerA = inflateMarker(markerLabel, markerColor);
        WindowManager.LayoutParams paramsA = markerLayoutParams(200, 400);
        makeDraggable(markerA, paramsA);
        windowManager.addView(markerA, paramsA);

        if (MODE_SWIPE.equals(mode)) {
            markerB = inflateMarker("B", 0xFF2196F3); // blue
            WindowManager.LayoutParams paramsB = markerLayoutParams(500, 900);
            makeDraggable(markerB, paramsB);
            windowManager.addView(markerB, paramsB);
        }

        WindowManager.LayoutParams paramsControls = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        paramsControls.gravity = Gravity.TOP | Gravity.START;
        paramsControls.x = 100;
        paramsControls.y = 100;

        controlsView = LayoutInflater.from(this).inflate(R.layout.layout_floating_controls, null);
        TextView hint = controlsView.findViewById(R.id.text_hint);
        hint.setText(MODE_SWIPE.equals(mode)
                ? "Drag A (start) and B (end), then Save"
                : isInspect
                        ? "Drag the marker over what you want to inspect, then tap Inspect"
                        : isLongClick
                                ? "Drag the marker to where it should be long-pressed, then Save"
                                : "Drag the marker to where it should tap, then Save");

        makeDraggable(controlsView.findViewById(R.id.controls_root), paramsControls);
        Button saveButton = controlsView.findViewById(R.id.btn_save_point);
        saveButton.setText(isInspect ? "Inspect" : "Save");
        saveButton.setOnClickListener(v -> saveAndFinish());
        controlsView.findViewById(R.id.btn_cancel_point).setOnClickListener(v -> finishWithoutSaving());

        windowManager.addView(controlsView, paramsControls);
    }

    private WindowManager.LayoutParams markerLayoutParams(int x, int y) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    @SuppressLint("InflateParams")
    private View inflateMarker(String label, int color) {
        View view = LayoutInflater.from(this).inflate(R.layout.layout_floating_marker, null);
        ((TextView) view.findViewById(R.id.marker_label)).setText(label);
        Drawable background = view.findViewById(R.id.marker_root).getBackground().mutate();
        background.setTint(color);
        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(v, params);
                        return true;
                }
                return false;
            }
        });
    }

    private float[] centerOf(View markerView) {
        int[] location = new int[2];
        markerView.getLocationOnScreen(location);
        float centerX = location[0] + markerView.getWidth() / 2f;
        float centerY = location[1] + markerView.getHeight() / 2f;
        return new float[]{centerX, centerY};
    }

    private void saveAndFinish() {
        float[] a = centerOf(markerA);

        if (MODE_INSPECT.equals(mode)) {
            // No sequence builder involved here - just tell AutomationService what point
            // was picked, and let it hit-test the accessibility tree and show the result.
            Intent inspectIntent = new Intent(getPackageName() + AutomationService.ACTION_INSPECT_POINT_SUFFIX);
            inspectIntent.setPackage(getPackageName());
            inspectIntent.putExtra(AutomationService.EXTRA_INSPECT_X, a[0]);
            inspectIntent.putExtra(AutomationService.EXTRA_INSPECT_Y, a[1]);
            sendBroadcast(inspectIntent);
            stopSelf();
            return;
        }

        SharedPreferences.Editor editor = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putFloat(KEY_X1, a[0]);
        editor.putFloat(KEY_Y1, a[1]);

        if (MODE_SWIPE.equals(mode) && markerB != null) {
            float[] b = centerOf(markerB);
            editor.putFloat(KEY_X2, b[0]);
            editor.putFloat(KEY_Y2, b[1]);
            editor.putString(KEY_PENDING_TYPE, MODE_SWIPE);
        } else {
            editor.putString(KEY_PENDING_TYPE, mode); // TAP or LONG_CLICK, both single-marker
        }
        editor.apply();

        Toast.makeText(this, "Point saved", Toast.LENGTH_SHORT).show();
        returnToBuilder();
        stopSelf();
    }

    private void finishWithoutSaving() {
        if (MODE_INSPECT.equals(mode)) {
            returnToMain();
        } else {
            returnToBuilder();
        }
        stopSelf();
    }

    private void returnToBuilder() {
        Intent backIntent = new Intent(this, SequenceBuilderActivity.class);
        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(backIntent);
    }

    private void returnToMain() {
        Intent backIntent = new Intent(this, MainActivity.class);
        backIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(backIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (windowManager != null) {
            if (markerA != null) windowManager.removeView(markerA);
            if (markerB != null) windowManager.removeView(markerB);
            if (controlsView != null) windowManager.removeView(controlsView);
        }
    }
}
