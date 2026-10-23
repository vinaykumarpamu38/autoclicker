package com.techy_pvk.automate.ui;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

/**
 * Remembers the one dialog an Activity currently has open, so it can be closed before the
 * Activity goes away (a dialog that outlives its Activity leaks its window on rotation) and so a
 * new dialog never stacks on top of an older one.
 */
final class DialogHolder {

    @Nullable
    private AlertDialog current;

    /** Registers a dialog that has just been shown; closes the previous one if still open. */
    void track(@Nullable AlertDialog dialog) {
        if (current != null && current != dialog && current.isShowing()) current.dismiss();
        current = dialog;
    }

    void dismiss() {
        if (current != null && current.isShowing()) current.dismiss();
        current = null;
    }
}
