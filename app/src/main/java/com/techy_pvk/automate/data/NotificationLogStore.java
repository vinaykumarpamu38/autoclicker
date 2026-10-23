package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.techy_pvk.automate.model.NotificationLogEntry;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Stores notifications captured by a NOTIFICATION_RECEIVED trigger with "store notification
 *  data" turned on. Newest first, capped at MAX_ENTRIES so it can't grow unbounded. */
public class NotificationLogStore {

    public static final String PREFS = "AutoClickerNotificationLog";
    private static final String KEY_LOG = "log_json";
    private static final int MAX_ENTRIES = 100;

    public static List<NotificationLogEntry> loadAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_LOG, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<NotificationLogEntry>>() {}.getType();
        List<NotificationLogEntry> list = new Gson().fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public static void append(Context context, NotificationLogEntry entry) {
        List<NotificationLogEntry> all = loadAll(context);
        all.add(0, entry); // newest first
        while (all.size() > MAX_ENTRIES) {
            all.remove(all.size() - 1);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOG, new Gson().toJson(all)).apply();
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply();
    }
}
