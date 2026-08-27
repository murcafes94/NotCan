package com.notcan.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NotCanDao {
    @Query("SELECT * FROM study_cycles ORDER BY isActive DESC, createdAtEpochMs DESC")
    fun observeCycles(): Flow<List<StudyCycleEntity>>

    @Query("SELECT * FROM subjects WHERE cycleId = :cycleId ORDER BY name COLLATE NOCASE ASC")
    fun observeSubjects(cycleId: String): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subject_schedules WHERE cycleId = :cycleId ORDER BY weekdayIso, startMinuteOfDay")
    fun observeSchedules(cycleId: String): Flow<List<SubjectScheduleEntity>>

    @Query("SELECT * FROM class_sessions WHERE subjectId = :subjectId ORDER BY startedAtEpochMs DESC")
    fun observeClasses(subjectId: String): Flow<List<ClassSessionEntity>>

    @Query("SELECT * FROM audio_recordings WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs DESC")
    fun observeAudioRecordings(classSessionId: String): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM important_moments WHERE classSessionId = :classSessionId ORDER BY offsetMs ASC")
    fun observeImportantMoments(classSessionId: String): Flow<List<ImportantMomentEntity>>

    @Query("SELECT * FROM note_pages WHERE classSessionId = :classSessionId ORDER BY updatedAtEpochMs DESC")
    fun observeNotePages(classSessionId: String): Flow<List<NotePageEntity>>

    @Query("SELECT * FROM document_resources WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs DESC")
    fun observeDocuments(classSessionId: String): Flow<List<DocumentResourceEntity>>

    @Query("SELECT * FROM pdf_ink_strokes WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs ASC")
    fun observePdfInkStrokes(classSessionId: String): Flow<List<PdfInkStrokeEntity>>

    @Query("SELECT * FROM transcripts WHERE classSessionId = :classSessionId ORDER BY updatedAtEpochMs DESC")
    fun observeTranscripts(classSessionId: String): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM grade_items WHERE subjectId = :subjectId ORDER BY createdAtEpochMs ASC")
    fun observeGradeItems(subjectId: String): Flow<List<GradeItemEntity>>

    @Query("SELECT * FROM detected_cues WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs ASC")
    fun observeDetectedCues(classSessionId: String): Flow<List<DetectedCueEntity>>

    @Query("SELECT * FROM class_sessions WHERE subjectId = :subjectId AND plannedStartEpochMs = :plannedStart LIMIT 1")
    suspend fun findMaterializedSession(subjectId: String, plannedStart: Long): ClassSessionEntity?

    @Query("SELECT * FROM study_cycles WHERE id = :cycleId LIMIT 1")
    suspend fun getCycle(cycleId: String): StudyCycleEntity?

    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    suspend fun getSubject(subjectId: String): SubjectEntity?

    @Query("SELECT * FROM class_sessions WHERE id = :classId LIMIT 1")
    suspend fun getClassSession(classId: String): ClassSessionEntity?

    @Query("SELECT COUNT(*) FROM class_sessions WHERE subjectId = :subjectId")
    suspend fun countClassesForSubject(subjectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: StudyCycleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: SubjectScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassSession(classSession: ClassSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioRecording(audioRecording: AudioRecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportantMoment(moment: ImportantMomentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotePage(notePage: NotePageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentResourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfInkStroke(stroke: PdfInkStrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGradeItem(item: GradeItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedCue(cue: DetectedCueEntity)

    @Query("UPDATE study_cycles SET isActive = 0")
    suspend fun deactivateAllCycles()

    @Query("UPDATE study_cycles SET isActive = 1 WHERE id = :cycleId")
    suspend fun activateCycle(cycleId: String)

    @Query("UPDATE study_cycles SET startEpochDay = :startEpochDay, endEpochDay = :endEpochDay WHERE id = :cycleId")
    suspend fun updateCycleDates(cycleId: String, startEpochDay: Long, endEpochDay: Long)

    @Query("UPDATE subject_schedules SET calendarEventId = :eventId WHERE id = :scheduleId")
    suspend fun setScheduleCalendarEvent(scheduleId: String, eventId: Long?)

    @Transaction
    suspend fun setActiveCycle(cycleId: String) {
        deactivateAllCycles()
        activateCycle(cycleId)
    }

    @Query("DELETE FROM subject_schedules WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)

    @Query("DELETE FROM audio_recordings WHERE id = :audioId")
    suspend fun deleteAudioRecording(audioId: String)

    @Query("DELETE FROM important_moments WHERE audioId = :audioId")
    suspend fun deleteImportantMomentsForAudio(audioId: String)

    @Transaction
    suspend fun deleteAudioAndMoments(audioId: String) {
        deleteImportantMomentsForAudio(audioId)
        deleteAudioRecording(audioId)
    }

    @Query("DELETE FROM note_pages WHERE id = :noteId")
    suspend fun deleteNotePage(noteId: String)

    @Query("DELETE FROM document_resources WHERE id = :documentId")
    suspend fun deleteDocument(documentId: String)

    @Query("DELETE FROM pdf_ink_strokes WHERE id = :strokeId")
    suspend fun deletePdfInkStroke(strokeId: String)

    @Query("DELETE FROM pdf_ink_strokes WHERE documentId = :documentId AND pageIndex = :pageIndex")
    suspend fun clearPdfInkPage(documentId: String, pageIndex: Int)

    @Query("DELETE FROM grade_items WHERE id = :itemId")
    suspend fun deleteGradeItem(itemId: String)

    @Query("DELETE FROM detected_cues WHERE transcriptId = :transcriptId")
    suspend fun deleteDetectedCuesForTranscript(transcriptId: String)
}
