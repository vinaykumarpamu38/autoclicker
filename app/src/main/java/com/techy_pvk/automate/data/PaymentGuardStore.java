package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * All state here is read/written by three independent things that do NOT run inside
 * AutomationService: PaymentGuardOverlayService (shows the popup), PaymentReminderReceiver
 * (the 10min/1hr/3hr escalation), and AutomationService itself only to check whether the
 * feature is on, and to clear this state once the service reconnects (i.e. the user turned
 * Accessibility back on). Everything here must keep working with Accessibility OFF.
 */
public final class PaymentGuardStore {

    private static final String PREFS = "AutoMatePaymentGuard";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PACKAGES = "watched_packages";
    private static final String KEY_AWAITING_REENABLE = "awaiting_reenable";
    private static final String KEY_REMINDER_STAGE = "reminder_stage";

    /** Delay before each escalation, in the order the user asked for: 10 min, 1 hr, 3 hr.
     *  Index 0 is the delay before the SECOND notification (the first fires immediately when
     *  the user taps "Go to Settings"), and so on. After the last stage, reminders stop. */
    public static final long[] STAGE_DELAYS_MS = {
            10 * 60 * 1000L,       // stage 0 -> 1
            60 * 60 * 1000L,       // stage 1 -> 2
            3 * 60 * 60 * 1000L,   // stage 2 -> 3
    };

    private static final String[] DEFAULT_PACKAGES = {
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",                        // PhonePe
            "net.one97.paytm",                        // Paytm
            "in.org.npci.upiapp",                      // BHIM
            "com.dreamplug.androidapp",                 // CRED
            "com.amazon.mShop.android.shopping",        // Amazon (Amazon Pay lives inside it)
    };

    private PaymentGuardStore() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false); // opt-in, off by default
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static Set<String> getWatchedPackages(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(KEY_PACKAGES)) {
            Set<String> defaults = new HashSet<>();
            for (String pkg : DEFAULT_PACKAGES) defaults.add(pkg);
            p.edit().putStringSet(KEY_PACKAGES, defaults).apply();
            return defaults;
        }
        return p.getStringSet(KEY_PACKAGES, new HashSet<>());
    }

    public static void addWatchedPackage(Context context, String packageName) {
        Set<String> current = new HashSet<>(getWatchedPackages(context));
        current.add(packageName);
        prefs(context).edit().putStringSet(KEY_PACKAGES, current).apply();
    }

    public static boolean isAwaitingReenable(Context context) {
        return prefs(context).getBoolean(KEY_AWAITING_REENABLE, false);
    }

    /** Called when the user taps "Go to Settings" on the popup - starts the reminder chain. */
    public static void startReminderCycle(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_AWAITING_REENABLE, true)
                .putInt(KEY_REMINDER_STAGE, 0)
                .apply();
    }

    /** Called once AutomationService reconnects - the user turned Accessibility back on. */
    public static void clearReminderCycle(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_AWAITING_REENABLE, false)
                .remove(KEY_REMINDER_STAGE)
                .apply();
    }

    public static int getReminderStage(Context context) {
        return prefs(context).getInt(KEY_REMINDER_STAGE, 0);
    }

    public static void setReminderStage(Context context, int stage) {
        prefs(context).edit().putInt(KEY_REMINDER_STAGE, stage).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
