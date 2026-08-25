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
        SubjectScheduleEntity::class,
        ClassSessionEntity::class,
        AudioRecordingEntity::class,
        ImportantMomentEntity::class,
        NotePageEntity::class,
        DocumentResourceEntity::class,
        PdfInkStrokeEntity::class,
        TranscriptEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NotCanDatabase : RoomDatabase() {
    abstract fun dao(): NotCanDao

    companion object {
        @Volatile private var instance: NotCanDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_pages` (
                        `id` TEXT NOT NULL, `classSessionId` TEXT NOT NULL,
                        `title` TEXT NOT NULL, `body` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_pages_classSessionId` ON `note_pages` (`classSessionId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `document_resources` (
                        `id` TEXT NOT NULL, `classSessionId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL, `localPath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL, `documentType` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_resources_classSessionId` ON `document_resources` (`classSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_resources_documentType` ON `document_resources` (`documentType`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pdf_ink_strokes` (
                        `id` TEXT NOT NULL, `classSessionId` TEXT NOT NULL,
                        `documentId` TEXT NOT NULL, `pageIndex` INTEGER NOT NULL,
                        `tool` TEXT NOT NULL, `colorArgb` INTEGER NOT NULL,
                        `baseWidth` REAL NOT NULL, `pointsData` TEXT NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`documentId`) REFERENCES `document_resources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_ink_strokes_classSessionId` ON `pdf_ink_strokes` (`classSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_ink_strokes_documentId` ON `pdf_ink_strokes` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_ink_strokes_documentId_pageIndex` ON `pdf_ink_strokes` (`documentId`, `pageIndex`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_cycles` ADD COLUMN `startEpochDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `study_cycles` ADD COLUMN `endEpochDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `class_sessions` ADD COLUMN `scheduleId` TEXT")
                db.execSQL("ALTER TABLE `class_sessions` ADD COLUMN `plannedStartEpochMs` INTEGER")
                db.execSQL("ALTER TABLE `class_sessions` ADD COLUMN `plannedEndEpochMs` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_class_sessions_scheduleId` ON `class_sessions` (`scheduleId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `subject_schedules` (
                        `id` TEXT NOT NULL,
                        `subjectId` TEXT NOT NULL,
                        `cycleId` TEXT NOT NULL,
                        `weekdayIso` INTEGER NOT NULL,
                        `startMinuteOfDay` INTEGER NOT NULL,
                        `endMinuteOfDay` INTEGER NOT NULL,
                        `reminderMinutesBefore` INTEGER NOT NULL,
                        `previewMinutesBefore` INTEGER NOT NULL,
                        `autoStopMode` TEXT NOT NULL,
                        `autoStopGraceMinutes` INTEGER NOT NULL,
                        `calendarEventId` INTEGER,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`cycleId`) REFERENCES `study_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subject_schedules_subjectId` ON `subject_schedules` (`subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subject_schedules_cycleId` ON `subject_schedules` (`cycleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subject_schedules_weekdayIso_startMinuteOfDay` ON `subject_schedules` (`weekdayIso`, `startMinuteOfDay`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transcripts` (
                        `id` TEXT NOT NULL,
                        `classSessionId` TEXT NOT NULL,
                        `audioId` TEXT,
                        `body` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `modelName` TEXT,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_classSessionId` ON `transcripts` (`classSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcripts_audioId` ON `transcripts` (`audioId`)")
            }
        }

        fun getInstance(context: Context): NotCanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotCanDatabase::class.java,
                    "notcan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
