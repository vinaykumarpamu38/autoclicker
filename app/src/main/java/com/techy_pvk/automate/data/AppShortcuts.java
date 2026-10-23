package com.techy_pvk.automate.data;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.techy_pvk.automate.R;
import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.ui.RunSequenceShortcutActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Publishes one Android dynamic app shortcut per saved sequence (long-press the app icon to
 * see them). Each shortcut's intent points at RunSequenceShortcutActivity with that sequence's
 * id, so launching the shortcut runs it immediately - no UI, no extra tap.
 *
 * This is also the practical bridge to Samsung's Modes and Routines: a Routine's "Other apps"
 * action list is (per Samsung/Google's App Actions system) sometimes able to target a specific
 * shortcut rather than just "open the app" - whether that shows up depends on your One UI
 * version, so it's worth checking on your own device. Either way, these shortcuts also work
 * from the regular Android app-icon long-press menu and from Google Assistant-style launchers.
 *
 * Call refresh() whenever the saved sequence list changes (SequenceRepository does this after
 * every save/delete, and MainViewModel does it each time the home screen resumes).
 */
public final class AppShortcuts {

    private AppShortcuts() {}

    public static void refresh(Context context, List<AutomationSequence> sequences) {
        int maxCount = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context);
        if (maxCount <= 0) maxCount = 4; // fallback if a device reports no limit info

        List<ShortcutInfoCompat> shortcuts = new ArrayList<>();
        for (int i = 0; i < sequences.size() && i < maxCount; i++) {
            AutomationSequence sequence = sequences.get(i);
            if (sequence.steps.isEmpty()) continue;

            Intent intent = new Intent(context, RunSequenceShortcutActivity.class);
            intent.setAction(Intent.ACTION_VIEW); // shortcut intents need a non-null action
            intent.putExtra(RunSequenceShortcutActivity.EXTRA_SEQUENCE_ID, sequence.id);

            ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, "sequence_" + sequence.id)
                    .setShortLabel(sequence.name)
                    .setLongLabel("Run \"" + sequence.name + "\"")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build();
            shortcuts.add(shortcut);
        }

        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts);
        } catch (Exception ignored) {
            // Shortcuts are a convenience, not a core feature - never let a publishing hiccup
            // (e.g. a device-specific limit) crash the app.
        }
    }
}
