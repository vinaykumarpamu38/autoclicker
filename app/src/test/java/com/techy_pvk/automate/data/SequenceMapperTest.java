package com.techy_pvk.automate.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.LocationTransition;
import com.techy_pvk.automate.model.StepType;
import com.techy_pvk.automate.model.TriggerType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SequenceMapperTest {

    private static AutomationSequence sample() {
        AutomationSequence sequence = new AutomationSequence();
        sequence.id = "id-1";
        sequence.name = "Office mode";
        sequence.repeatCount = 3;
        GestureStep tap = new GestureStep();
        tap.type = StepType.TAP;
        tap.x1 = 12.5f;
        tap.y1 = 40f;
        GestureStep click = new GestureStep();
        click.type = StepType.CLICK_TEXT;
        click.targetValue = "Next";
        sequence.steps = new ArrayList<>(Arrays.asList(tap, click));
        sequence.trigger.type = TriggerType.LOCATION;
        sequence.trigger.locationLatitude = 17.385;
        sequence.trigger.locationLongitude = 78.4867;
        sequence.trigger.locationTransition = LocationTransition.ENTER;
        sequence.autoRestore = true;
        return sequence;
    }

    @Test
    public void roundTripKeepsEverything_includingAutoRestore() {
        AutomationSequence original = sample();
        AutomationSequence back = SequenceMapper.toModel(SequenceMapper.toEntity(original));

        assertEquals("id-1", back.id);
        assertEquals("Office mode", back.name);
        assertEquals(3, back.repeatCount);
        assertEquals(2, back.steps.size());
        assertEquals(StepType.TAP, back.steps.get(0).type);
        assertEquals(12.5f, back.steps.get(0).x1, 0.0001f);
        assertEquals("Next", back.steps.get(1).targetValue);
        assertEquals(TriggerType.LOCATION, back.trigger.type);
        assertEquals(17.385, back.trigger.locationLatitude, 1e-9);
        // The regression this guards: autoRestore used to be dropped on every save.
        assertTrue(back.autoRestore);
    }

    @Test
    public void autoRestoreFalseStaysFalse() {
        AutomationSequence original = sample();
        original.autoRestore = false;
        assertFalse(SequenceMapper.toModel(SequenceMapper.toEntity(original)).autoRestore);
    }

    @Test
    public void nullStepsAndTriggerBecomeEmptyListAndManual() {
        AutomationSequence sparse = new AutomationSequence();
        sparse.id = "s";
        sparse.name = "n";
        sparse.steps = null;
        sparse.trigger = null;
        AutomationSequence back = SequenceMapper.toModel(SequenceMapper.toEntity(sparse));
        assertNotNull(back.steps);
        assertTrue(back.steps.isEmpty());
        assertEquals(TriggerType.MANUAL, back.trigger.type);
    }

    @Test
    public void rowsWithMissingJsonStillLoad() {
        AutomationSequenceEntity entity = new AutomationSequenceEntity();
        entity.id = "e";
        entity.name = "broken row";
        entity.stepsJson = null;
        entity.triggerJson = null;
        AutomationSequence model = SequenceMapper.toModel(entity);
        assertTrue(model.steps.isEmpty());
        assertEquals(TriggerType.MANUAL, model.trigger.type);
    }

    @Test
    public void toModelsHandlesNullAndPreservesOrder() {
        assertTrue(SequenceMapper.toModels(null).isEmpty());

        AutomationSequence a = sample();
        a.id = "a";
        AutomationSequence b = sample();
        b.id = "b";
        List<AutomationSequenceEntity> rows = Arrays.asList(SequenceMapper.toEntity(a), SequenceMapper.toEntity(b));
        List<AutomationSequence> models = SequenceMapper.toModels(rows);
        assertEquals("a", models.get(0).id);
        assertEquals("b", models.get(1).id);
    }
}
