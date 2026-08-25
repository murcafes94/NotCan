package com.notcan.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StudyCycleEntity::class,
        SubjectEntity::class,
        ClassSessionEntity::class,
        AudioRecordingEntity::class,
        ImportantMomentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NotCanDatabase : RoomDatabase() {
    abstract fun dao(): NotCanDao

    companion object {
        @Volatile
        private var instance: NotCanDatabase? = null

        fun getInstance(context: Context): NotCanDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotCanDatabase::class.java,
                    "notcan.db"
                ).build().also { instance = it }
            }
        }
    }
}
