package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.techy_pvk.automate.model.InspectedElement;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-off point for an Inspect Screen scan: AutomationService writes the result here, then
 * opens InspectorResultActivity, whose ViewModel reads it back. Before this class existed the
 * pref file name and key names were duplicated as private constants in both places - a typo in
 * either would have silently produced an empty results screen.
 */
public final class InspectorResultStore {

    public static final String PREFS = "AutoClickerInspector";
    private static final String KEY_RESULTS = "results_json";
    private static final String KEY_APP_LABEL = "app_label";
    private static final String KEY_IS_POINT_MODE = "is_point_mode";

    private static final Type LIST_TYPE = new TypeToken<List<InspectedElement>>() {}.getType();

    private InspectorResultStore() {}

    /** One finished scan. */
    public static final class Result {
        public final List<InspectedElement> elements;
        public final String appLabel;
        public final boolean pointMode;

        public Result(List<InspectedElement> elements, String appLabel, boolean pointMode) {
            this.elements = elements;
            this.appLabel = appLabel;
            this.pointMode = pointMode;
        }
    }

    public static void save(Context context, List<InspectedElement> elements, String appLabel, boolean pointMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_RESULTS, new Gson().toJson(elements))
                .putString(KEY_APP_LABEL, appLabel)
                .putBoolean(KEY_IS_POINT_MODE, pointMode)
                .apply();
    }

    public static Result load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_RESULTS, null);
        String appLabel = prefs.getString(KEY_APP_LABEL, "the current screen");
        boolean pointMode = prefs.getBoolean(KEY_IS_POINT_MODE, false);

        List<InspectedElement> elements = null;
        if (json != null) {
            try {
                elements = new Gson().fromJson(json, LIST_TYPE);
            } catch (RuntimeException ignored) {
                // Corrupt/foreign JSON just means "nothing to show" - never a crash.
            }
        }
        return new Result(elements != null ? elements : new ArrayList<>(), appLabel, pointMode);
    }
}
