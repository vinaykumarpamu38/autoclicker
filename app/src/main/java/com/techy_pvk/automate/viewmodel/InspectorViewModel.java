package com.techy_pvk.automate.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.techy_pvk.automate.data.InspectorResultStore;
import com.techy_pvk.automate.model.InspectedElement;
import com.techy_pvk.automate.repository.InspectorRepository;

import java.util.List;

/** Inspect Screen results: everything the screen shows, already worded and ready to render. */
public class InspectorViewModel extends AndroidViewModel {

    /** What to draw. */
    public static final class UiState {
        public final String title;
        public final String emptyMessage;

       // public final String empty1;
        public final boolean showEmptyMessage;
        public final List<InspectedElement> elements;

        UiState(String title, String emptyMessage, boolean showEmptyMessage, List<InspectedElement> elements) {
            this.title = title;
            this.emptyMessage = emptyMessage;
            this.showEmptyMessage = showEmptyMessage;
            this.elements = elements;
        }
    }

    private final InspectorRepository repository;
    private final MutableLiveData<UiState> state = new MutableLiveData<>();

    /** Used by the default ViewModelProvider factory. */
    public InspectorViewModel(@NonNull Application application) {
        this(application, InspectorRepository.getInstance(application));
    }

    /** Dependency-injecting constructor, for tests. */
    InspectorViewModel(@NonNull Application application, @NonNull InspectorRepository repository) {
        super(application);
        this.repository = repository;
        reload();
    }

    public LiveData<UiState> getState() { return state; }

    /** Re-reads the latest scan. InspectorResultActivity is single-top, so a second scan while it
     *  is already showing arrives via onNewIntent() - the old code ignored that and kept
     *  displaying the previous scan. */
    public void reload() {
        repository.loadLatest(result -> state.setValue(toUiState(result)));
    }

    static UiState toUiState(InspectorResultStore.Result result) {
        String title = result.pointMode
                ? "Element at your tap on " + result.appLabel
                : "Elements found on " + result.appLabel;
        String empty = result.pointMode
                ? "Nothing found at that exact point - try tapping slightly differently, or use \"Show everything on screen\" instead."
                : "Nothing found - make sure Accessibility is enabled and try inspecting again.";
        return new UiState(title, empty, result.elements.isEmpty(), result.elements);
    }
}
