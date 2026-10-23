package com.techy_pvk.automate.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.techy_pvk.automate.data.InstalledApps;
import com.techy_pvk.automate.model.AppInfo;
import com.techy_pvk.automate.util.AppExecutors;

import java.util.List;
import java.util.function.Consumer;

/** Lists launchable apps off the main thread. Querying PackageManager for every launcher
 *  activity can take a few hundred milliseconds on a phone with many apps - long enough to
 *  visibly freeze the screen when done inline while opening a dialog. Deliberately not cached:
 *  the user can install an app between two dialogs, and a fresh read is cheap enough. */
public class InstalledAppsRepository {

    private static volatile InstalledAppsRepository instance;

    private final Context appContext;
    private final AppExecutors executors;

    public static InstalledAppsRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (InstalledAppsRepository.class) {
                if (instance == null) {
                    instance = new InstalledAppsRepository(context.getApplicationContext(), AppExecutors.getInstance());
                }
            }
        }
        return instance;
    }

    InstalledAppsRepository(@NonNull Context appContext, @NonNull AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    public void load(@NonNull Consumer<List<AppInfo>> callback) {
        executors.diskIO().execute(() -> {
            List<AppInfo> apps = InstalledApps.getLaunchableApps(appContext);
            executors.mainThread().execute(() -> callback.accept(apps));
        });
    }
}
