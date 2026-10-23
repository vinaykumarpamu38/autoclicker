package com.techy_pvk.automate.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.SequenceTrigger;
import com.techy_pvk.automate.model.StepType;
import com.techy_pvk.automate.model.TriggerType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class SequenceSanitizerTest {

    private static GestureStep step(StepType type) {
        GestureStep step = new GestureStep();
        step.type = type;
        return step;
    }

    private static AutomationSequence sequenceWith(GestureStep... steps) {
        AutomationSequence sequence = new AutomationSequence();
        sequence.id = "x";
        sequence.name = "Name";
        sequence.steps = new ArrayList<>(Arrays.asList(steps));
        return sequence;
    }

    @Test
    public void nullInputGivesNull() {
        assertNull(SequenceSanitizer.sanitize(null));
    }

    @Test
    public void dropsStepsWithNoTypeSoTheHomeListCannotCrashOnThem() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP), step(null), null, step(StepType.HOME));
        AutomationSequence clean = SequenceSanitizer.sanitize(raw);
        assertNotNull(clean);
        assertEquals(2, clean.steps.size());
        assertEquals(StepType.TAP, clean.steps.get(0).type);
        assertEquals(StepType.HOME, clean.steps.get(1).type);
    }

    @Test
    public void nothingUsableLeftGivesNull() {
        assertNull(SequenceSanitizer.sanitize(sequenceWith(step(null), null)));
        AutomationSequence noSteps = sequenceWith();
        noSteps.steps = null;
        assertNull(SequenceSanitizer.sanitize(noSteps));
    }

    @Test
    public void capsTheNumberOfSteps() {
        GestureStep[] many = new GestureStep[SequenceSanitizer.MAX_STEPS + 50];
        for (int i = 0; i < many.length; i++) many[i] = step(StepType.TAP);
        assertEquals(SequenceSanitizer.MAX_STEPS, SequenceSanitizer.sanitize(sequenceWith(many)).steps.size());
    }

    @Test
    public void namesAreTrimmedDefaultedAndCapped() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP));

        raw.name = null;
        assertEquals(SequenceSanitizer.DEFAULT_NAME, SequenceSanitizer.sanitize(raw).name);

        raw.name = "   ";
        assertEquals(SequenceSanitizer.DEFAULT_NAME, SequenceSanitizer.sanitize(raw).name);

        raw.name = "  Morning  ";
        assertEquals("Morning", SequenceSanitizer.sanitize(raw).name);

        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 500; i++) longName.append('a');
        raw.name = longName.toString();
        assertEquals(SequenceSanitizer.MAX_NAME_LENGTH, SequenceSanitizer.sanitize(raw).name.length());
    }

    @Test
    public void negativeRepeatCountBecomesOnce_zeroStaysLoopForever() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP));
        raw.repeatCount = -5;
        assertEquals(1, SequenceSanitizer.sanitize(raw).repeatCount);
        raw.repeatCount = 0;
        assertEquals(0, SequenceSanitizer.sanitize(raw).repeatCount);
    }

    @Test
    public void clampsAbsurdTimingsAndBrightness() {
        GestureStep wild = step(StepType.WAIT);
        wild.delayBeforeMs = Long.MAX_VALUE;
        wild.durationMs = -10;
        wild.matchTimeoutMs = Long.MAX_VALUE;
        wild.brightnessPercent = 9999;
        GestureStep clean = SequenceSanitizer.sanitize(sequenceWith(wild)).steps.get(0);
        assertTrue(clean.delayBeforeMs <= 10 * 60 * 1000L);
        assertEquals(0, clean.durationMs);
        assertTrue(clean.matchTimeoutMs <= 10 * 60 * 1000L);
        assertEquals(100, clean.brightnessPercent);
    }

    @Test
    public void missingOrBrokenTriggerFallsBackToManual() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP));
        raw.trigger = null;
        assertEquals(TriggerType.MANUAL, SequenceSanitizer.sanitize(raw).trigger.type);

        raw.trigger = new SequenceTrigger();
        raw.trigger.type = null;
        assertEquals(TriggerType.MANUAL, SequenceSanitizer.sanitize(raw).trigger.type);
    }

    @Test
    public void locationTriggerWithImpossibleCoordinatesBecomesManual() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP));
        raw.trigger = new SequenceTrigger();
        raw.trigger.type = TriggerType.LOCATION;
        raw.trigger.locationLatitude = 123.0;
        raw.trigger.locationLongitude = 10.0;
        assertEquals(TriggerType.MANUAL, SequenceSanitizer.sanitize(raw).trigger.type);

        raw.trigger.locationLatitude = Double.NaN;
        assertEquals(TriggerType.MANUAL, SequenceSanitizer.sanitize(raw).trigger.type);
    }

    @Test
    public void validLocationTriggerSurvivesAndNullEnumsGetDefaults() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP));
        raw.trigger = new SequenceTrigger();
        raw.trigger.type = TriggerType.LOCATION;
        raw.trigger.locationLatitude = 17.38;
        raw.trigger.locationLongitude = 78.48;
        raw.trigger.locationTransition = null;     // e.g. an unknown enum name in the file
        raw.trigger.locationRadiusMeters = 0f;
        raw.trigger.scheduledDays = new boolean[3]; // wrong length
        raw.trigger.scheduledHour = 99;
        raw.trigger.scheduledMinute = -4;

        SequenceTrigger clean = SequenceSanitizer.sanitize(raw).trigger;
        assertEquals(TriggerType.LOCATION, clean.type);
        assertEquals(LocationTransition.ENTER, clean.locationTransition);
        assertEquals(150f, clean.locationRadiusMeters, 0.001f);
        assertEquals(7, clean.scheduledDays.length);
        assertEquals(23, clean.scheduledHour);
        assertEquals(0, clean.scheduledMinute);
    }

    @Test
    public void autoRestoreIsOnlyKeptWhereTheTriggerSupportsIt() {
        AutomationSequence raw = sequenceWith(step(StepType.TAP));
        raw.autoRestore = true;

        raw.trigger = new SequenceTrigger(); // MANUAL cannot auto-restore
        assertFalse(SequenceSanitizer.sanitize(raw).autoRestore);

        raw.trigger = new SequenceTrigger();
        raw.trigger.type = TriggerType.LOCATION;
        raw.trigger.locationLatitude = 1;
        raw.trigger.locationLongitude = 1;
        raw.trigger.locationTransition = LocationTransition.ENTER;
        assertTrue(SequenceSanitizer.sanitize(raw).autoRestore);

        raw.trigger.locationTransition = LocationTransition.EXIT; // nothing to restore from on "leave"
        assertFalse(SequenceSanitizer.sanitize(raw).autoRestore);
    }
}
