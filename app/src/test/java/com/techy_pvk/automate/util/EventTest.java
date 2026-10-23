package com.techy_pvk.automate.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EventTest {

    @Test
    public void contentIsDeliveredExactlyOnce() {
        Event<String> event = new Event<>("hello");
        assertFalse(event.hasBeenHandled());
        assertEquals("hello", event.getContentIfNotHandled());
        assertTrue(event.hasBeenHandled());
        assertNull(event.getContentIfNotHandled());
    }

    @Test
    public void peekDoesNotConsume() {
        Event<Integer> event = new Event<>(7);
        assertEquals(Integer.valueOf(7), event.peekContent());
        assertFalse(event.hasBeenHandled());
        assertEquals(Integer.valueOf(7), event.getContentIfNotHandled());
    }
}
