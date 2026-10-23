package com.techy_pvk.automate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.techy_pvk.automate.model.NotificationLogEntry;
import com.techy_pvk.automate.repository.NotificationLogRepository;
import com.techy_pvk.automate.util.Event;
import com.techy_pvk.automate.util.LiveDataUtil;
import com.techy_pvk.automate.util.UiMessage;

import java.util.List;

/** Notification Log screen: the captured notifications (live), and the clear-with-confirmation flow. */
public class NotificationLogViewModel extends AndroidViewModel {

    private final NotificationLogRepository repository;

    private final LiveData<List<NotificationLogEntry>> entries;
    private final LiveData<Boolean> showEmptyState;
    private final MutableLiveData<Event<UiMessage>> message = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> confirmClear = new MutableLiveData<>();

    /** Used by the default ViewModelProvider factory. */
    public NotificationLogViewModel(@NonNull Application application) {
        this(application, NotificationLogRepository.getInstance(application));
    }

    /** Dependency-injecting constructor, for tests. */
    NotificationLogViewModel(@NonNull Application application, @NonNull NotificationLogRepository repository) {
        super(application);
        this.repository = repository;
        this.entries = repository.observe();
        // null = still loading; only a loaded, empty list shows the empty message.
        this.showEmptyState = LiveDataUtil.map(entries, list -> list != null && list.isEmpty());
    }

    public LiveData<List<NotificationLogEntry>> getEntries() { return entries; }
    public LiveData<Boolean> getShowEmptyState() { return showEmptyState; }
    public LiveData<Event<UiMessage>> getMessage() { return message; }
    public LiveData<Event<Boolean>> getConfirmClear() { return confirmClear; }

    public void onClearClicked() {
        List<NotificationLogEntry> current = entries.getValue();
        if (current == null || current.isEmpty()) {
            message.setValue(new Event<>(UiMessage.shortMessage("Nothing to clear")));
        } else {
            confirmClear.setValue(new Event<>(Boolean.TRUE));
        }
    }

    public void onClearConfirmed() {
        repository.clear(null);
    }
}
