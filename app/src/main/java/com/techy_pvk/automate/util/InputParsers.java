package com.techy_pvk.automate.util;

import androidx.annotation.Nullable;

/** Forgiving parsers for what people type into dialog fields. Pure Java, unit-tested. */
public final class InputParsers {

    private InputParsers() {}

    public static long parseLongOrDefault(@Nullable String text, long fallback) {
        try {
            return Long.parseLong(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    public static Double parseDoubleOrNull(@Nullable String text) {
        try {
            return Double.valueOf(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static float parseFloatOrDefault(@Nullable String text, float fallback) {
        try {
            return Float.parseFloat(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** How many times a sequence should run: 1 = once, 0 = loop until stopped, N = N times.
     *  Anything blank, unparseable or negative means "once" - the safe default. */
    public static int parseRepeatCount(@Nullable String text) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return value < 0 ? 1 : value;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** The integer if it parses AND lies within [min, max]; otherwise null. */
    @Nullable
    public static Integer parseIntInRange(@Nullable String text, int min, int max) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return (value >= min && value <= max) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
