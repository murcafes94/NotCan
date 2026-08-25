package com.notcan.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        StudyCycleEntity::class,
        SubjectEntity::class,
        ClassSessionEntity::class,
        AudioRecordingEntity::class,
        ImportantMomentEntity::class,
        NotePageEntity::class,
        DocumentResourceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NotCanDatabase : RoomDatabase() {
    abstract fun dao(): NotCanDao

    companion object {
        @Volatile
        private var instance: NotCanDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_pages` (
                        `id` TEXT NOT NULL,
                        `classSessionId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_pages_classSessionId` ON `note_pages` (`classSessionId`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `document_resources` (
                        `id` TEXT NOT NULL,
                        `classSessionId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `documentType` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_document_resources_classSessionId` ON `document_resources` (`classSessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_document_resources_documentType` ON `document_resources` (`documentType`)"
                )
            }
        }

        fun getInstance(context: Context): NotCanDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotCanDatabase::class.java,
                    "notcan.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
