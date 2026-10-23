package com.techy_pvk.automate.service;

import android.content.Context;
import android.content.Intent;

/**
 * The one place that builds the "run this sequence" and "inspect the screen" broadcasts that
 * AutomationService listens for. The same three lines used to be copy-pasted into MainActivity,
 * RunSequenceShortcutActivity and TriggerMatcher.
 */
public final class SequenceRunner {

    private SequenceRunner() {}

    /** Asks AutomationService to start running the sequence with this id. */
    public static void run(Context context, String sequenceId) {
        context.sendBroadcast(runIntent(context, sequenceId));
    }

    /** Same broadcast, returned un-sent so a caller (TriggerMatcher) can attach extras first. */
    public static Intent runIntent(Context context, String sequenceId) {
        Intent intent = new Intent(context.getPackageName() + AutomationService.ACTION_RUN_SUFFIX);
        intent.setPackage(context.getPackageName());
        intent.putExtra(AutomationService.EXTRA_SEQUENCE_ID, sequenceId);
        return intent;
    }

    /** Asks AutomationService to scan and list every element currently on screen. */
    public static void inspectWholeScreen(Context context) {
        Intent intent = new Intent(context.getPackageName() + AutomationService.ACTION_INSPECT_SUFFIX);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    /** Shows the draggable marker used to point at one element to inspect. */
    public static void startPointInspection(Context context) {
        Intent intent = new Intent(context, FloatingPickerService.class);
        intent.putExtra(FloatingPickerService.EXTRA_MODE, FloatingPickerService.MODE_INSPECT);
        context.startService(intent);
    }

    /** Shows the draggable marker(s) used to capture a Tap / Long Click / Swipe point. */
    public static void startPicker(Context context, String mode) {
        Intent intent = new Intent(context, FloatingPickerService.class);
        intent.putExtra(FloatingPickerService.EXTRA_MODE, mode);
        context.startService(intent);
    }
}
