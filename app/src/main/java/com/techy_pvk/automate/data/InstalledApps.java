package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.techy_pvk.automate.model.AppInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists installed, launchable apps for the various app pickers in this app (Open App step,
 * notification trigger's app filter, Inspect Screen's target app) - scoped via the
 * LAUNCHER-category `<queries>` declaration in the manifest, not the broader (and
 * Play-Store-restricted) QUERY_ALL_PACKAGES permission.
 */
public final class InstalledApps {

    private InstalledApps() {}

    public static List<AppInfo> getLaunchableApps(Context context) {
        List<AppInfo> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo info : resolveInfos) {
            result.add(new AppInfo(info.loadLabel(pm).toString(), info.activityInfo.packageName));
        }
        result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }
}
