package com.notcan.app.data

import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.NotCanDao
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import com.notcan.app.data.local.TranscriptEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class StudyRepository(private val dao: NotCanDao) {
    fun observeCycles(): Flow<List<StudyCycleEntity>> = dao.observeCycles()
    fun observeSubjects(cycleId: String): Flow<List<SubjectEntity>> = dao.observeSubjects(cycleId)
    fun observeSchedules(cycleId: String): Flow<List<SubjectScheduleEntity>> = dao.observeSchedules(cycleId)
    fun observeClasses(subjectId: String): Flow<List<ClassSessionEntity>> = dao.observeClasses(subjectId)
    fun observeAudioRecordings(classSessionId: String): Flow<List<AudioRecordingEntity>> = dao.observeAudioRecordings(classSessionId)
    fun observeImportantMoments(classSessionId: String): Flow<List<ImportantMomentEntity>> = dao.observeImportantMoments(classSessionId)
    fun observeNotePages(classSessionId: String): Flow<List<NotePageEntity>> = dao.observeNotePages(classSessionId)
    fun observeDocuments(classSessionId: String): Flow<List<DocumentResourceEntity>> = dao.observeDocuments(classSessionId)
    fun observePdfInkStrokes(classSessionId: String): Flow<List<PdfInkStrokeEntity>> = dao.observePdfInkStrokes(classSessionId)
    fun observeTranscripts(classSessionId: String): Flow<List<TranscriptEntity>> = dao.observeTranscripts(classSessionId)

    suspend fun createCycle(name: String, makeActive: Boolean = true): StudyCycleEntity {
        val now = System.currentTimeMillis()
        val cycle = StudyCycleEntity(UUID.randomUUID().toString(), name.trim(), makeActive, now)
        if (makeActive) dao.deactivateAllCycles()
        dao.insertCycle(cycle)
        return cycle
    }

    suspend fun setActiveCycle(cycleId: String) = dao.setActiveCycle(cycleId)
    suspend fun updateCycleDates(cycleId: String, startEpochDay: Long, endEpochDay: Long) =
        dao.updateCycleDates(cycleId, startEpochDay, endEpochDay)

    suspend fun createSubject(cycleId: String, name: String): SubjectEntity {
        val subject = SubjectEntity(
            id = UUID.randomUUID().toString(),
            cycleId = cycleId,
            name = name.trim(),
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.insertSubject(subject)
        return subject
    }

    suspend fun addSchedule(
        cycleId: String,
        subjectId: String,
        weekdayIso: Int,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        reminderMinutesBefore: Int = 1440,
        previewMinutesBefore: Int = 10,
        autoStopMode: String = "ASK",
        autoStopGraceMinutes: Int = 5
    ): SubjectScheduleEntity {
        require(weekdayIso in 1..7)
        require(startMinuteOfDay in 0..1439)
        require(endMinuteOfDay in 1..1440 && endMinuteOfDay > startMinuteOfDay)
        val schedule = SubjectScheduleEntity(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            cycleId = cycleId,
            weekdayIso = weekdayIso,
            startMinuteOfDay = startMinuteOfDay,
            endMinuteOfDay = endMinuteOfDay,
            reminderMinutesBefore = reminderMinutesBefore,
            previewMinutesBefore = previewMinutesBefore,
            autoStopMode = autoStopMode,
            autoStopGraceMinutes = autoStopGraceMinutes,
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.insertSchedule(schedule)
        return schedule
    }

    suspend fun deleteSchedule(scheduleId: String) = dao.deleteSchedule(scheduleId)
    suspend fun setScheduleCalendarEvent(scheduleId: String, eventId: Long?) = dao.setScheduleCalendarEvent(scheduleId, eventId)

    suspend fun createClassSession(subjectId: String, title: String): ClassSessionEntity {
        val now = System.currentTimeMillis()
        val classSession = ClassSessionEntity(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            title = title.trim(),
            startedAtEpochMs = now,
            createdAtEpochMs = now
        )
        dao.insertClassSession(classSession)
        return classSession
    }

    suspend fun materializeScheduledSession(
        schedule: SubjectScheduleEntity,
        subject: SubjectEntity,
        occurrenceStartEpochMs: Long,
        occurrenceEndEpochMs: Long
    ): ClassSessionEntity {
        dao.findMaterializedSession(subject.id, occurrenceStartEpochMs)?.let { return it }
        val date = Instant.ofEpochMilli(occurrenceStartEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val session = ClassSessionEntity(
            id = UUID.randomUUID().toString(),
            subjectId = subject.id,
            title = "${subject.name} · $date",
            startedAtEpochMs = occurrenceStartEpochMs,
            createdAtEpochMs = System.currentTimeMillis(),
            scheduleId = schedule.id,
            plannedStartEpochMs = occurrenceStartEpochMs,
            plannedEndEpochMs = occurrenceEndEpochMs
        )
        dao.insertClassSession(session)
        return session
    }

    suspend fun createNotePage(classSessionId: String, title: String): NotePageEntity {
        val now = System.currentTimeMillis()
        val note = NotePageEntity(
            id = UUID.randomUUID().toString(),
            classSessionId = classSessionId,
            title = title.trim().ifBlank { "Apuntes" },
            body = "",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.insertNotePage(note)
        return note
    }

    suspend fun saveNotePage(notePage: NotePageEntity) = dao.insertNotePage(notePage)
    suspend fun saveDocument(document: DocumentResourceEntity) = dao.insertDocument(document)
    suspend fun saveAudioRecording(audioRecording: AudioRecordingEntity) = dao.insertAudioRecording(audioRecording)
    suspend fun deleteAudio(audioId: String) = dao.deleteAudioAndMoments(audioId)
    suspend fun saveImportantMoment(moment: ImportantMomentEntity) = dao.insertImportantMoment(moment)
    suspend fun savePdfInkStroke(stroke: PdfInkStrokeEntity) = dao.insertPdfInkStroke(stroke)
    suspend fun deletePdfInkStroke(strokeId: String) = dao.deletePdfInkStroke(strokeId)
    suspend fun clearPdfInkPage(documentId: String, pageIndex: Int) = dao.clearPdfInkPage(documentId, pageIndex)
    suspend fun saveTranscript(transcript: TranscriptEntity) = dao.insertTranscript(transcript)
}
