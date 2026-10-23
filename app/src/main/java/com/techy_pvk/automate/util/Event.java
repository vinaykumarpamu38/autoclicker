package com.techy_pvk.automate.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot wrapper for things the UI should react to exactly once - a toast, a navigation, a
 * "close this screen" - and NOT again when the screen is rotated or re-observed.
 *
 * A plain LiveData<String> holding a toast message would re-deliver the same message every
 * time the Activity is recreated and observes it again. Wrapping it in an Event means the
 * first observer to read it consumes it; later reads get nothing.
 *
 * Never put a null payload in here - "no content" is how a consumed event is reported.
 */
public final class Event<T> {

    private final T content;
    private final AtomicBoolean handled = new AtomicBoolean(false);

    public Event(@NonNull T content) {
        this.content = content;
    }

    /** Returns the payload the first time it's called, and null every time after that. */
    @Nullable
    public T getContentIfNotHandled() {
        return handled.compareAndSet(false, true) ? content : null;
    }

    public boolean hasBeenHandled() {
        return handled.get();
    }

    /** Reads the payload without consuming it. */
    @NonNull
    public T peekContent() {
        return content;
    }
}
