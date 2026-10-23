package com.techy_pvk.automate.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.techy_pvk.automate.model.AutomationSequence;
import com.techy_pvk.automate.model.GestureStep;
import com.techy_pvk.automate.model.SequenceTrigger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** The one place that knows how an AutomationSequence is flattened into a Room row and back.
 *  Shared by SequenceStore (Services/Receivers) and SequenceRepository (UI), so the two paths
 *  can never disagree about the format. Pure Java - unit-testable without Android. */
public final class SequenceMapper {

    private static final Gson GSON = new Gson();
    private static final Type STEPS_TYPE = new TypeToken<List<GestureStep>>() {}.getType();

    private SequenceMapper() {}

    public static AutomationSequenceEntity toEntity(AutomationSequence sequence) {
        AutomationSequenceEntity entity = new AutomationSequenceEntity();
        entity.id = sequence.id;
        entity.name = sequence.name;
        entity.repeatCount = sequence.repeatCount;
        entity.stepsJson = GSON.toJson(sequence.steps != null ? sequence.steps : new ArrayList<GestureStep>());
        entity.triggerJson = GSON.toJson(sequence.trigger != null ? sequence.trigger : new SequenceTrigger());
        entity.autoRestore = sequence.autoRestore;
        return entity;
    }

    public static AutomationSequence toModel(AutomationSequenceEntity entity) {
        AutomationSequence sequence = new AutomationSequence();
        sequence.id = entity.id;
        sequence.name = entity.name;
        sequence.repeatCount = entity.repeatCount;
        sequence.autoRestore = entity.autoRestore;

        List<GestureStep> steps = entity.stepsJson != null ? GSON.fromJson(entity.stepsJson, STEPS_TYPE) : null;
        sequence.steps = steps != null ? steps : new ArrayList<>();

        SequenceTrigger trigger = entity.triggerJson != null
                ? GSON.fromJson(entity.triggerJson, SequenceTrigger.class) : null;
        sequence.trigger = trigger != null ? trigger : new SequenceTrigger();

        return sequence;
    }

    public static List<AutomationSequence> toModels(List<AutomationSequenceEntity> entities) {
        List<AutomationSequence> result = new ArrayList<>();
        if (entities != null) {
            for (AutomationSequenceEntity entity : entities) {
                result.add(toModel(entity));
            }
        }
        return result;
    }
}
