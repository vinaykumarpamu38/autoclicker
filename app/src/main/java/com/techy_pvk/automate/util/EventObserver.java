package com.techy_pvk.automate.util;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import java.util.function.Consumer;

/**
 * Observer for LiveData<Event<T>> that only calls back for an event nobody has handled yet:
 * <pre>
 *   viewModel.toast.observe(this, new EventObserver<>(message -> Toast.makeText(...).show()));
 * </pre>
 */
public final class EventObserver<T> implements Observer<Event<T>> {

    private final Consumer<T> onUnhandled;

    public EventObserver(@NonNull Consumer<T> onUnhandled) {
        this.onUnhandled = onUnhandled;
    }

    @Override
    public void onChanged(Event<T> event) {
        if (event == null) return;
        T content = event.getContentIfNotHandled();
        if (content != null) onUnhandled.accept(content);
    }
}
