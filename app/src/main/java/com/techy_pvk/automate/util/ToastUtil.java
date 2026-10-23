package com.techy_pvk.automate.util;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

/** View-layer helper: shows a ViewModel's UiMessage. */
public final class ToastUtil {

    private ToastUtil() {}

    public static void show(@NonNull Context context, @NonNull UiMessage message) {
        Toast.makeText(context, message.text,
                message.longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }
}
