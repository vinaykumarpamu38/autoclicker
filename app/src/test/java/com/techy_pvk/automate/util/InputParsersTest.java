package com.techy_pvk.automate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class InputParsersTest {

    @Test
    public void parseLongOrDefault() {
        assertEquals(250L, InputParsers.parseLongOrDefault(" 250 ", 1000));
        assertEquals(1000L, InputParsers.parseLongOrDefault("abc", 1000));
        assertEquals(1000L, InputParsers.parseLongOrDefault("", 1000));
        assertEquals(1000L, InputParsers.parseLongOrDefault(null, 1000));
    }

    @Test
    public void parseDoubleOrNull() {
        assertEquals(17.38, InputParsers.parseDoubleOrNull(" 17.38 "), 1e-9);
        assertNull(InputParsers.parseDoubleOrNull("north"));
        assertNull(InputParsers.parseDoubleOrNull(null));
    }

    @Test
    public void parseFloatOrDefault() {
        assertEquals(150f, InputParsers.parseFloatOrDefault("x", 150f), 0f);
        assertEquals(42.5f, InputParsers.parseFloatOrDefault("42.5", 150f), 0f);
    }

    @Test
    public void parseIntInRangeRejectsOutOfRange() {
        assertEquals(Integer.valueOf(50), InputParsers.parseIntInRange("50", 1, 100));
        assertEquals(Integer.valueOf(1), InputParsers.parseIntInRange("1", 1, 100));
        assertEquals(Integer.valueOf(100), InputParsers.parseIntInRange("100", 1, 100));
        assertNull(InputParsers.parseIntInRange("0", 1, 100));
        assertNull(InputParsers.parseIntInRange("101", 1, 100));
        assertNull(InputParsers.parseIntInRange("fifty", 1, 100));
    }

    @Test
    public void parseRepeatCount() {
        assertEquals(1, InputParsers.parseRepeatCount("1"));
        assertEquals(0, InputParsers.parseRepeatCount("0"));     // loop until stopped
        assertEquals(5, InputParsers.parseRepeatCount(" 5 "));
        assertEquals(1, InputParsers.parseRepeatCount("-3"));    // negative means once
        assertEquals(1, InputParsers.parseRepeatCount(""));
        assertEquals(1, InputParsers.parseRepeatCount("many"));
        assertEquals(1, InputParsers.parseRepeatCount(null));
    }
}
