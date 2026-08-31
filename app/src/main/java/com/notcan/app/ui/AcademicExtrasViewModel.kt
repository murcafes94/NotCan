package com.notcan.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.GradeItemEntity
import com.notcan.app.data.local.NotCanDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AcademicExtrasViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao(), application)
    private val subjectId = MutableStateFlow<String?>(null)
    private val classId = MutableStateFlow<String?>(null)

    val gradeItems: StateFlow<List<GradeItemEntity>> = subjectId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeGradeItems(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val detectedCues: StateFlow<List<DetectedCueEntity>> = classId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeDetectedCues(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setContext(selectedSubjectId: String?, selectedClassId: String?) {
        if (subjectId.value != selectedSubjectId) subjectId.value = selectedSubjectId
        if (classId.value != selectedClassId) classId.value = selectedClassId
    }

    fun addGrade(title: String, score: Double, maxScore: Double, weightPercent: Double) {
        val id = subjectId.value ?: return
        viewModelScope.launch { repository.addGradeItem(id, title, score, maxScore, weightPercent) }
    }

    fun deleteGrade(id: String) {
        viewModelScope.launch { repository.deleteGradeItem(id) }
    }
}
