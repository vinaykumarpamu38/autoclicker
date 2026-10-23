package com.techy_pvk.automate.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.techy_pvk.automate.data.InspectorResultStore;
import com.techy_pvk.automate.util.AppExecutors;

import java.util.function.Consumer;

/** Loads the latest Inspect Screen scan that AutomationService stored. */
public class InspectorRepository {

    private static volatile InspectorRepository instance;

    private final Context appContext;
    private final AppExecutors executors;

    public static InspectorRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (InspectorRepository.class) {
                if (instance == null) {
                    instance = new InspectorRepository(context.getApplicationContext(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    InspectorRepository(@NonNull Context appContext, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    public void loadLatest(@NonNull Consumer<InspectorResultStore.Result> callback) {
        executors.diskIO().execute(() -> {
            InspectorResultStore.Result result = InspectorResultStore.load(appContext);
            executors.mainThread().execute(() -> callback.accept(result));
        });
    }
}
