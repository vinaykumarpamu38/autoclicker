package com.techy_pvk.automate.model;

import java.util.ArrayList;
import java.util.List;

public class AutomationSequence {
    public String id;
    public String name;
    public List<GestureStep> steps = new ArrayList<>();

    /** 1 = run once. 0 = loop until manually stopped. N = run N times. */
    public int repeatCount = 1;

    /** Defaults to a MANUAL trigger, so every sequence saved before this field existed keeps
     *  behaving exactly as it did: it only runs when you tap ▶. */
    public SequenceTrigger trigger = new SequenceTrigger();

    /** Opt-in, off by default. Only meaningful for a trigger that models a "start" state with
     *  a natural opposite already in this app (Location ENTER, Music STARTED, Battery
     *  CHARGING_STARTED) - when the opposite fires (EXIT / STOPPED_OR_PAUSED / CHARGING_STOPPED),
     *  whatever restorable setting this sequence's steps changed (currently just Set Ringer
     *  Mode) gets set back to whatever it was right before this sequence ran - see
     *  TriggerMatcher and RestoreStateStore. */
    public boolean autoRestore = false;
}
