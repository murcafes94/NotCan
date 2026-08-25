package com.notcan.app.data

import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotCanDao
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class StudyRepository(
    private val dao: NotCanDao
) {
    fun observeCycles(): Flow<List<StudyCycleEntity>> = dao.observeCycles()

    fun observeSubjects(cycleId: String): Flow<List<SubjectEntity>> = dao.observeSubjects(cycleId)

    fun observeClasses(subjectId: String): Flow<List<ClassSessionEntity>> = dao.observeClasses(subjectId)

    fun observeAudioRecordings(classSessionId: String): Flow<List<AudioRecordingEntity>> =
        dao.observeAudioRecordings(classSessionId)

    fun observeImportantMoments(classSessionId: String): Flow<List<ImportantMomentEntity>> =
        dao.observeImportantMoments(classSessionId)

    suspend fun createCycle(name: String, makeActive: Boolean = true): StudyCycleEntity {
        val now = System.currentTimeMillis()
        val cycle = StudyCycleEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            isActive = makeActive,
            createdAtEpochMs = now
        )
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

    suspend fun saveAudioRecording(audioRecording: AudioRecordingEntity) =
        dao.insertAudioRecording(audioRecording)

    suspend fun saveImportantMoment(moment: ImportantMomentEntity) =
        dao.insertImportantMoment(moment)
}
