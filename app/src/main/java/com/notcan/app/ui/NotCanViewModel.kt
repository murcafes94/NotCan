package com.notcan.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notcan.app.data.StudyRepository
import com.notcan.app.data.local.NotCanDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotCanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao())

    private val _selectedCycleId = MutableStateFlow<String?>(null)
    val selectedCycleId: StateFlow<String?> = _selectedCycleId.asStateFlow()

    private val _selectedSubjectId = MutableStateFlow<String?>(null)
    val selectedSubjectId: StateFlow<String?> = _selectedSubjectId.asStateFlow()

    private val _selectedClassId = MutableStateFlow<String?>(null)
    val selectedClassId: StateFlow<String?> = _selectedClassId.asStateFlow()

    val cycles = repository.observeCycles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subjects = _selectedCycleId
        .flatMapLatest { cycleId ->
            if (cycleId == null) flowOf(emptyList()) else repository.observeSubjects(cycleId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val classes = _selectedSubjectId
        .flatMapLatest { subjectId ->
            if (subjectId == null) flowOf(emptyList()) else repository.observeClasses(subjectId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val audioRecordings = _selectedClassId
        .flatMapLatest { classId ->
            if (classId == null) flowOf(emptyList()) else repository.observeAudioRecordings(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importantMoments = _selectedClassId
        .flatMapLatest { classId ->
            if (classId == null) flowOf(emptyList()) else repository.observeImportantMoments(classId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            cycles.collect { items ->
                val current = _selectedCycleId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedCycleId.value = items.firstOrNull { it.isActive }?.id ?: items.firstOrNull()?.id
                }
            }
        }

        viewModelScope.launch {
            subjects.collect { items ->
                val current = _selectedSubjectId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedSubjectId.value = items.firstOrNull()?.id
                }
            }
        }

        viewModelScope.launch {
            classes.collect { items ->
                val current = _selectedClassId.value
                if (current == null || items.none { it.id == current }) {
                    _selectedClassId.value = items.firstOrNull()?.id
                }
            }
        }
    }

    fun selectCycle(cycleId: String) {
        _selectedCycleId.value = cycleId
        _selectedSubjectId.value = null
        _selectedClassId.value = null
        viewModelScope.launch { repository.setActiveCycle(cycleId) }
    }

    fun selectSubject(subjectId: String) {
        _selectedSubjectId.value = subjectId
        _selectedClassId.value = null
    }

    fun selectClass(classId: String) {
        _selectedClassId.value = classId
    }

    fun createCycle(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val cycle = repository.createCycle(name)
            _selectedCycleId.value = cycle.id
            _selectedSubjectId.value = null
            _selectedClassId.value = null
        }
    }

    fun createSubject(name: String) {
        val cycleId = _selectedCycleId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val subject = repository.createSubject(cycleId, name)
            _selectedSubjectId.value = subject.id
            _selectedClassId.value = null
        }
    }

    fun createClass(title: String) {
        val subjectId = _selectedSubjectId.value ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            val classSession = repository.createClassSession(subjectId, title)
            _selectedClassId.value = classSession.id
        }
    }
}
