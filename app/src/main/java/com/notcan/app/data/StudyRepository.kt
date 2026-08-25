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
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class StudyRepository(private val dao: NotCanDao) {
    fun observeCycles(): Flow<List<StudyCycleEntity>> = dao.observeCycles()
    fun observeSubjects(cycleId: String): Flow<List<SubjectEntity>> = dao.observeSubjects(cycleId)
    fun observeClasses(subjectId: String): Flow<List<ClassSessionEntity>> = dao.observeClasses(subjectId)
    fun observeAudioRecordings(classSessionId: String): Flow<List<AudioRecordingEntity>> = dao.observeAudioRecordings(classSessionId)
    fun observeImportantMoments(classSessionId: String): Flow<List<ImportantMomentEntity>> = dao.observeImportantMoments(classSessionId)
    fun observeNotePages(classSessionId: String): Flow<List<NotePageEntity>> = dao.observeNotePages(classSessionId)
    fun observeDocuments(classSessionId: String): Flow<List<DocumentResourceEntity>> = dao.observeDocuments(classSessionId)
    fun observePdfInkStrokes(classSessionId: String): Flow<List<PdfInkStrokeEntity>> = dao.observePdfInkStrokes(classSessionId)

    suspend fun createCycle(name: String, makeActive: Boolean = true): StudyCycleEntity {
        val now = System.currentTimeMillis()
        val cycle = StudyCycleEntity(UUID.randomUUID().toString(), name.trim(), makeActive, now)
        if (makeActive) dao.deactivateAllCycles()
        dao.insertCycle(cycle)
        return cycle
    }

    suspend fun setActiveCycle(cycleId: String) = dao.setActiveCycle(cycleId)

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
    suspend fun saveImportantMoment(moment: ImportantMomentEntity) = dao.insertImportantMoment(moment)
    suspend fun savePdfInkStroke(stroke: PdfInkStrokeEntity) = dao.insertPdfInkStroke(stroke)
    suspend fun deletePdfInkStroke(strokeId: String) = dao.deletePdfInkStroke(strokeId)
    suspend fun clearPdfInkPage(documentId: String, pageIndex: Int) = dao.clearPdfInkPage(documentId, pageIndex)
}
