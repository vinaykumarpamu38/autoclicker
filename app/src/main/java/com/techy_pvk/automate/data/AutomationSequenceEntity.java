package com.techy_pvk.automate.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Room row for one saved sequence. steps/trigger are stored as Gson JSON blobs in single
 *  columns (not separate related tables) - keeps AutomationSequence/GestureStep/SequenceTrigger
 *  completely unchanged, so only the persistence layer (SequenceMapper) knows about the format. */
@Entity(tableName = "sequences")
public class AutomationSequenceEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String name;
    public int repeatCount;

    /** Gson JSON of List<GestureStep>. */
    public String stepsJson;

    /** Gson JSON of SequenceTrigger. */
    public String triggerJson;

    /** Added in DB version 2. Before this column existed the flag was silently dropped on every
     *  save, so Auto-Restore never survived a round trip through the database. */
    @ColumnInfo(defaultValue = "0")
    public boolean autoRestore;
}
