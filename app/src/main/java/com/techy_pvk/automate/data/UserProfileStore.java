package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class UserProfileStore {

    public static final String PREFS = "AutoMateProfile";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EMAIL = "email"; // unused until Firebase Auth is added
    private static final String KEY_CREATED_AT = "created_at";

    private UserProfileStore() {}

    public static String getDisplayName(Context context) {
        return prefs(context).getString(KEY_DISPLAY_NAME, "");
    }

    public static void setDisplayName(Context context, String name) {
        prefs(context).edit().putString(KEY_DISPLAY_NAME, name).apply();
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static void setEmail(Context context, String email) {
        prefs(context).edit().putString(KEY_EMAIL, email).apply();
    }

    public static long getOrInitCreatedAt(Context context) {
        SharedPreferences p = prefs(context);
        long existing = p.getLong(KEY_CREATED_AT, -1);
        if (existing > 0) return existing;
        long now = System.currentTimeMillis();
        p.edit().putLong(KEY_CREATED_AT, now).apply();
        return now;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}