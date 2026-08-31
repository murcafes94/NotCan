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
        TranscriptEntity::class,
        GradeItemEntity::class,
        DetectedCueEntity::class,
        AcademicVocabularyTermEntity::class,
        TaskItemEntity::class
    ],
    version = 7,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `grade_items` (
                        `id` TEXT NOT NULL,
                        `subjectId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `score` REAL NOT NULL,
                        `maxScore` REAL NOT NULL,
                        `weightPercent` REAL NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grade_items_subjectId` ON `grade_items` (`subjectId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `detected_cues` (
                        `id` TEXT NOT NULL,
                        `classSessionId` TEXT NOT NULL,
                        `transcriptId` TEXT,
                        `audioId` TEXT,
                        `label` TEXT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `excerpt` TEXT NOT NULL,
                        `offsetMs` INTEGER,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`classSessionId`) REFERENCES `class_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_cues_classSessionId` ON `detected_cues` (`classSessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_cues_transcriptId` ON `detected_cues` (`transcriptId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_cues_label` ON `detected_cues` (`label`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `academic_vocabulary` (
                        `id` TEXT NOT NULL,
                        `term` TEXT NOT NULL,
                        `normalizedTerm` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `area` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `cycleId` TEXT,
                        `subjectId` TEXT,
                        `source` TEXT NOT NULL,
                        `weight` REAL NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_vocabulary_scope` ON `academic_vocabulary` (`scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_vocabulary_cycleId` ON `academic_vocabulary` (`cycleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_vocabulary_subjectId` ON `academic_vocabulary` (`subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_vocabulary_normalizedTerm_language_area` ON `academic_vocabulary` (`normalizedTerm`, `language`, `area`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_items` (
                        `id` TEXT NOT NULL,
                        `cycleId` TEXT NOT NULL,
                        `subjectId` TEXT,
                        `title` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `dueAtEpochMs` INTEGER,
                        `priority` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `createdAtEpochMs` INTEGER NOT NULL,
                        `updatedAtEpochMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`cycleId`) REFERENCES `study_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_cycleId` ON `task_items` (`cycleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_subjectId` ON `task_items` (`subjectId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_dueAtEpochMs` ON `task_items` (`dueAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_isCompleted` ON `task_items` (`isCompleted`)")
            }
        }

        fun getInstance(context: Context): NotCanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NotCanDatabase::class.java,
                    "notcan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { instance = it }
            }
    }
}
