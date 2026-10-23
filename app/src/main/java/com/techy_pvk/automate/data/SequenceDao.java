package com.techy_pvk.automate.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface SequenceDao {

    /** Synchronous read - for Services / BroadcastReceivers, which have no lifecycle to observe. */
    @Query("SELECT * FROM sequences")
    List<AutomationSequenceEntity> getAll();

    /** Live read - for the UI. Re-emits automatically whenever the table changes, no matter who
     *  changed it (the builder, an import, a delete, or a background write). */
    @Query("SELECT * FROM sequences")
    LiveData<List<AutomationSequenceEntity>> observeAll();

    @Query("SELECT * FROM sequences WHERE id = :id LIMIT 1")
    AutomationSequenceEntity getById(String id);

    /** A true upsert (insert, or update in place). The previous @Insert(REPLACE) deleted and
     *  re-inserted the row, which moved every edited sequence to the bottom of the list. */
    @Upsert
    void upsert(AutomationSequenceEntity entity);

    @Delete
    void delete(AutomationSequenceEntity entity);

    @Query("DELETE FROM sequences WHERE id = :id")
    void deleteById(String id);
}
