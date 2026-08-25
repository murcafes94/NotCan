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

    @Query("SELECT * FROM class_sessions WHERE subjectId = :subjectId ORDER BY startedAtEpochMs DESC")
    fun observeClasses(subjectId: String): Flow<List<ClassSessionEntity>>

    @Query("SELECT * FROM audio_recordings WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs DESC")
    fun observeAudioRecordings(classSessionId: String): Flow<List<AudioRecordingEntity>>

    @Query("SELECT * FROM important_moments WHERE classSessionId = :classSessionId ORDER BY offsetMs ASC")
    fun observeImportantMoments(classSessionId: String): Flow<List<ImportantMomentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCycle(cycle: StudyCycleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassSession(classSession: ClassSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioRecording(audioRecording: AudioRecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportantMoment(moment: ImportantMomentEntity)

    @Query("UPDATE study_cycles SET isActive = 0")
    suspend fun deactivateAllCycles()

    @Query("UPDATE study_cycles SET isActive = 1 WHERE id = :cycleId")
    suspend fun activateCycle(cycleId: String)

    @Transaction
    suspend fun setActiveCycle(cycleId: String) {
        deactivateAllCycles()
        activateCycle(cycleId)
    }

    @Query("DELETE FROM audio_recordings WHERE id = :audioId")
    suspend fun deleteAudioRecording(audioId: String)
}
