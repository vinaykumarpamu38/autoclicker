package com.techy_pvk.automate.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.techy_pvk.automate.data.PaymentGuardStore;
import com.techy_pvk.automate.util.AppExecutors;

import java.util.function.Consumer;

/** App-level toggles shown on the Settings screen (currently just Payment Guard). */
public class SettingsRepository {

    private static volatile SettingsRepository instance;

    private final Context appContext;
    private final AppExecutors executors;

    public static SettingsRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SettingsRepository.class) {
                if (instance == null) {
                    instance = new SettingsRepository(context.getApplicationContext(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    SettingsRepository(@NonNull Context appContext, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    public void loadPaymentGuardEnabled(@NonNull Consumer<Boolean> callback) {
        executors.diskIO().execute(() -> {
            boolean enabled = PaymentGuardStore.isEnabled(appContext);
            executors.mainThread().execute(() -> callback.accept(enabled));
        });
    }

    public void setPaymentGuardEnabled(boolean enabled) {
        executors.diskIO().execute(() -> PaymentGuardStore.setEnabled(appContext, enabled));
    }
}
