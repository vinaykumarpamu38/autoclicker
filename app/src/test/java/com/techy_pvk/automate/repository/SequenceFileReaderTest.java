package com.techy_pvk.automate.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.gson.JsonParseException;
import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.StepType;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SequenceFileReaderTest {

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsUtf8Text() throws IOException {
        assertEquals("héllo ✓", SequenceFileReader.readUtf8(bytes("héllo ✓"), 1024));
    }

    @Test
    public void refusesInputOverTheLimitInsteadOfTruncating() throws IOException {
        try {
            SequenceFileReader.readUtf8(new ByteArrayInputStream(new byte[2000]), 1000);
            fail("expected TooLargeException");
        } catch (SequenceFileReader.TooLargeException expected) {
            // ok
        }
    }

    @Test
    public void exactlyAtTheLimitIsAccepted() throws IOException {
        assertEquals(1000, SequenceFileReader.readUtf8(new ByteArrayInputStream(new byte[1000]), 1000).length());
    }

    @Test(expected = IOException.class)
    public void nullStreamIsAnIoError() throws IOException {
        SequenceFileReader.readUtf8(null, 10);
    }

    @Test
    public void parsesAValidSequence() {
        String json = "{\"id\":\"1\",\"name\":\"  Hello \",\"repeatCount\":2,"
                + "\"steps\":[{\"type\":\"TAP\",\"x1\":10,\"y1\":20}]}";
        AutomationSequence sequence = SequenceFileReader.parse(json);
        assertNotNull(sequence);
        assertEquals("Hello", sequence.name);
        assertEquals(StepType.TAP, sequence.steps.get(0).type);
    }

    @Test
    public void unknownStepTypeIsDroppedNotCrashed() {
        String json = "{\"name\":\"x\",\"steps\":[{\"type\":\"NOT_A_REAL_TYPE\"},{\"type\":\"HOME\"}]}";
        AutomationSequence sequence = SequenceFileReader.parse(json);
        assertNotNull(sequence);
        assertEquals(1, sequence.steps.size());
        assertEquals(StepType.HOME, sequence.steps.get(0).type);
    }

    @Test
    public void validJsonWithNoUsableStepsGivesNull() {
        assertNull(SequenceFileReader.parse("{\"name\":\"empty\",\"steps\":[]}"));
        assertNull(SequenceFileReader.parse("null"));
    }

    @Test(expected = JsonParseException.class)
    public void garbageIsAParseError() {
        SequenceFileReader.parse("this is not json at all {{{");
    }
}
