package com.techy_pvk.automate.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonParseException;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.repository.SequenceFileReader;
import com.techy_pvk.automate.repository.SequenceRepository;
import com.techy_pvk.automate.util.AppExecutors;
import com.techy_pvk.automate.util.Event;

/**
 * Importing a shared sequence file: read it (size-capped), parse and sanitize it, describe it
 * for the confirmation dialog, and save it only if the user confirms.
 */
public class ImportSequenceViewModel extends AndroidViewModel {

    /** What the import screen should be showing. */
    public static final class UiState {
        public enum Status { LOADING, READY, ERROR }

        public final Status status;
        public final String sequenceName;
        public final int stepCount;
        /** Non-null when the sequence starts by itself rather than only when tapped. */
        @Nullable public final String automaticTriggerSummary;
        @Nullable public final String errorMessage;

        private UiState(Status status, String sequenceName, int stepCount,
                        @Nullable String automaticTriggerSummary, @Nullable String errorMessage) {
            this.status = status;
            this.sequenceName = sequenceName;
            this.stepCount = stepCount;
            this.automaticTriggerSummary = automaticTriggerSummary;
            this.errorMessage = errorMessage;
        }

        static UiState loading() { return new UiState(Status.LOADING, "", 0, null, null); }
        static UiState error(String message) { return new UiState(Status.ERROR, "", 0, null, message); }
        static UiState ready(String name, int steps, @Nullable String automaticTriggerSummary) {
            return new UiState(Status.READY, name, steps, automaticTriggerSummary, null);
        }
    }

    private final SequenceRepository repository;
    private final AppExecutors executors;

    private final MutableLiveData<UiState> state = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<Event<String>> imported = new MutableLiveData<>();

    private boolean started;
    // Written on the background thread, read on the main thread.
    private volatile AutomationSequence pending;

    /** Used by the default ViewModelProvider factory. */
    public ImportSequenceViewModel(@NonNull Application application) {
        this(application, SequenceRepository.getInstance(application), AppExecutors.getInstance());
    }

    /** Dependency-injecting constructor, for tests. */
    ImportSequenceViewModel(@NonNull Application application, @NonNull SequenceRepository repository,
                            @NonNull AppExecutors executors) {
        super(application);
        this.repository = repository;
        this.executors = executors;
    }

    public LiveData<UiState> getState() { return state; }

    /** Payload = the imported sequence's name. */
    public LiveData<Event<String>> getImported() { return imported; }

    /** Starts reading the file. Idempotent, so a rotation doesn't re-read it. */
    public void load(@Nullable Uri uri) {
        if (started) return;
        started = true;

        if (uri == null) {
            state.setValue(UiState.error("No sequence file found"));
            return;
        }

        executors.diskIO().execute(() -> {
            UiState result;
            try {
                String json = SequenceFileReader.readUtf8(getApplication(), uri);
                AutomationSequence parsed = SequenceFileReader.parse(json);
                if (parsed == null) {
                    result = UiState.error("That file doesn't contain a valid sequence");
                } else {
                    pending = parsed;
                    result = UiState.ready(parsed.name, parsed.steps.size(),
                            TriggerSummaryFormatter.runsAutomatically(parsed.trigger)
                                    ? TriggerSummaryFormatter.format(parsed.trigger, parsed.autoRestore) : null);
                }
            } catch (SequenceFileReader.TooLargeException e) {
                result = UiState.error("That file is too large to be a sequence");
            } catch (Exception e) { // IOException, JsonParseException, anything a hostile file can provoke
                result = UiState.error("Couldn't read that file - it may not be a valid sequence");
            }
            UiState finalResult = result;
            executors.mainThread().execute(() -> state.setValue(finalResult));
        });
    }

    /** The user confirmed the import dialog. */
    public void confirmImport() {
        AutomationSequence toImport = pending;
        if (toImport == null) return;
        pending = null; // a double-tap must not import twice
        state.setValue(UiState.loading()); // hides the dialog; also stops a rotation re-showing it

        repository.importSequence(toImport, saved -> {
            if (saved == null) {
                state.setValue(UiState.error("Couldn't save that sequence"));
            } else {
                imported.setValue(new Event<>(saved.name));
            }
        });
    }
}
