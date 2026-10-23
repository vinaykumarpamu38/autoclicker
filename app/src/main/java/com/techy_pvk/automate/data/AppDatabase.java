package com.techy_pvk.automate.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * allowMainThreadQueries() stays on deliberately, but it is no longer what the UI relies on:
 * every screen now reads through SequenceRepository (LiveData + a background executor). The
 * synchronous path exists for Services and BroadcastReceivers (TriggerMatcher, AutomationService,
 * the boot/geofence/alarm receivers) which run outside any UI lifecycle, need an answer
 * immediately, and read a handful of tiny rows. If those ever move to goAsync()/WorkManager,
 * this flag can be dropped.
 *
 * Version history:
 *   1 - initial Room schema.
 *   2 - adds sequences.autoRestore (it was never persisted before).
 */
@Database(entities = {AutomationSequenceEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract SequenceDao sequenceDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sequences ADD COLUMN autoRestore INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "automate.db")
                            .addMigrations(MIGRATION_1_2)
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return instance;
    }
}
