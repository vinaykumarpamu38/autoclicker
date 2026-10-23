package com.techy_pvk.automate.util;

import androidx.annotation.NonNull;

/** A toast-sized message from a ViewModel. The ViewModel decides WHAT to say and how long it
 *  deserves to stay up; the Activity decides how to show it (see ToastUtil). */
public final class UiMessage {

    public final String text;
    public final boolean longDuration;

    private UiMessage(String text, boolean longDuration) {
        this.text = text;
        this.longDuration = longDuration;
    }

    @NonNull
    public static UiMessage shortMessage(@NonNull String text) {
        return new UiMessage(text, false);
    }

    @NonNull
    public static UiMessage longMessage(@NonNull String text) {
        return new UiMessage(text, true);
    }
}
