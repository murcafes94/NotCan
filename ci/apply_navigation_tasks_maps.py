from pathlib import Path
import re

ROOT = Path('.')

def read(p): return (ROOT/p).read_text()
def write(p,s): (ROOT/p).write_text(s)
def must_replace(text, old, new, label):
    if old not in text: raise SystemExit(f'missing {label}')
    return text.replace(old,new,1)

# 1) Tasks entity
p=Path('app/src/main/java/com/notcan/app/data/local/AcademicEntities.kt')
s=read(p)
if 'data class TaskItemEntity' not in s:
    s += '''\n\n@Entity(\n    tableName = "task_items",\n    foreignKeys = [\n        ForeignKey(entity = StudyCycleEntity::class, parentColumns = ["id"], childColumns = ["cycleId"], onDelete = ForeignKey.CASCADE),\n        ForeignKey(entity = SubjectEntity::class, parentColumns = ["id"], childColumns = ["subjectId"], onDelete = ForeignKey.SET_NULL)\n    ],\n    indices = [Index("cycleId"), Index("subjectId"), Index("dueAtEpochMs"), Index("isCompleted")]\n)\ndata class TaskItemEntity(\n    @PrimaryKey val id: String,\n    val cycleId: String,\n    val subjectId: String? = null,\n    val title: String,\n    val type: String = "Tarea",\n    val dueAtEpochMs: Long? = null,\n    val priority: String = "Normal",\n    val notes: String = "",\n    val isCompleted: Boolean = false,\n    val createdAtEpochMs: Long,\n    val updatedAtEpochMs: Long\n)\n'''
write(p,s)

# 2) Database migration
p=Path('app/src/main/java/com/notcan/app/data/local/NotCanDatabase.kt')
s=read(p)
s=s.replace('AcademicVocabularyTermEntity::class\n    ],\n    version = 6,','AcademicVocabularyTermEntity::class,\n        TaskItemEntity::class\n    ],\n    version = 7,')
if 'MIGRATION_6_7' not in s:
    marker='''        fun getInstance(context: Context): NotCanDatabase ='''
    migration='''        private val MIGRATION_6_7 = object : Migration(6, 7) {\n            override fun migrate(db: SupportSQLiteDatabase) {\n                db.execSQL("""\n                    CREATE TABLE IF NOT EXISTS `task_items` (\n                        `id` TEXT NOT NULL,\n                        `cycleId` TEXT NOT NULL,\n                        `subjectId` TEXT,\n                        `title` TEXT NOT NULL,\n                        `type` TEXT NOT NULL,\n                        `dueAtEpochMs` INTEGER,\n                        `priority` TEXT NOT NULL,\n                        `notes` TEXT NOT NULL,\n                        `isCompleted` INTEGER NOT NULL,\n                        `createdAtEpochMs` INTEGER NOT NULL,\n                        `updatedAtEpochMs` INTEGER NOT NULL,\n                        PRIMARY KEY(`id`),\n                        FOREIGN KEY(`cycleId`) REFERENCES `study_cycles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,\n                        FOREIGN KEY(`subjectId`) REFERENCES `subjects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL\n                    )\n                """.trimIndent())\n                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_cycleId` ON `task_items` (`cycleId`)")\n                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_subjectId` ON `task_items` (`subjectId`)")\n                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_dueAtEpochMs` ON `task_items` (`dueAtEpochMs`)")\n                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_items_isCompleted` ON `task_items` (`isCompleted`)")\n            }\n        }\n\n'''
    s=s.replace(marker,migration+marker)
s=s.replace('.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)', '.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)')
write(p,s)

# 3) DAO
p=Path('app/src/main/java/com/notcan/app/data/local/NotCanDao.kt')
s=read(p)
if 'fun observeTasks' not in s:
    s=s.replace('''    @Query("SELECT * FROM detected_cues WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs ASC")\n    fun observeDetectedCues(classSessionId: String): Flow<List<DetectedCueEntity>>\n''','''    @Query("SELECT * FROM detected_cues WHERE classSessionId = :classSessionId ORDER BY createdAtEpochMs ASC")\n    fun observeDetectedCues(classSessionId: String): Flow<List<DetectedCueEntity>>\n\n    @Query("SELECT * FROM task_items WHERE cycleId = :cycleId ORDER BY isCompleted ASC, dueAtEpochMs IS NULL ASC, dueAtEpochMs ASC, createdAtEpochMs DESC")\n    fun observeTasks(cycleId: String): Flow<List<TaskItemEntity>>\n''')
    s=s.replace('''    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun insertDetectedCue(cue: DetectedCueEntity)\n''','''    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun insertDetectedCue(cue: DetectedCueEntity)\n    @Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun insertTaskItem(item: TaskItemEntity)\n''')
    s=s.replace('''    @Query("DELETE FROM grade_items WHERE id = :itemId")\n    suspend fun deleteGradeItem(itemId: String)\n''','''    @Query("DELETE FROM grade_items WHERE id = :itemId")\n    suspend fun deleteGradeItem(itemId: String)\n    @Query("UPDATE task_items SET isCompleted = :completed, updatedAtEpochMs = :updatedAt WHERE id = :taskId")\n    suspend fun setTaskCompleted(taskId: String, completed: Boolean, updatedAt: Long)\n    @Query("DELETE FROM task_items WHERE id = :taskId")\n    suspend fun deleteTask(taskId: String)\n''')
write(p,s)

# 4) Repository
p=Path('app/src/main/java/com/notcan/app/data/StudyRepository.kt')
s=read(p)
if 'TaskItemEntity' not in s:
    s=s.replace('import com.notcan.app.data.local.SubjectScheduleEntity\n','import com.notcan.app.data.local.SubjectScheduleEntity\nimport com.notcan.app.data.local.TaskItemEntity\n')
if 'fun observeTasks' not in s:
    s=s.replace('''    fun observeDetectedCues(classSessionId: String): Flow<List<DetectedCueEntity>> = dao.observeDetectedCues(classSessionId)\n''','''    fun observeDetectedCues(classSessionId: String): Flow<List<DetectedCueEntity>> = dao.observeDetectedCues(classSessionId)\n    fun observeTasks(cycleId: String): Flow<List<TaskItemEntity>> = dao.observeTasks(cycleId)\n''')
    insert='''\n    suspend fun addTask(\n        cycleId: String,\n        subjectId: String?,\n        title: String,\n        type: String,\n        dueAtEpochMs: Long?,\n        priority: String,\n        notes: String\n    ): TaskItemEntity {\n        val now = System.currentTimeMillis()\n        val item = TaskItemEntity(\n            id = UUID.randomUUID().toString(), cycleId = cycleId, subjectId = subjectId,\n            title = title.trim().ifBlank { "Pendiente" }, type = type.trim().ifBlank { "Tarea" },\n            dueAtEpochMs = dueAtEpochMs, priority = priority.trim().ifBlank { "Normal" },\n            notes = notes.trim(), createdAtEpochMs = now, updatedAtEpochMs = now\n        )\n        dao.insertTaskItem(item)\n        return item\n    }\n\n    suspend fun setTaskCompleted(taskId: String, completed: Boolean) = dao.setTaskCompleted(taskId, completed, System.currentTimeMillis())\n    suspend fun deleteTask(taskId: String) = dao.deleteTask(taskId)\n'''
    s=s.replace('''    suspend fun addVocabularyTerm(term: AcademicVocabularyTermEntity) = dao.insertVocabularyTerm(term)\n''',insert+'\n    suspend fun addVocabularyTerm(term: AcademicVocabularyTermEntity) = dao.insertVocabularyTerm(term)\n')
write(p,s)

# 5) Extras VM
p=Path('app/src/main/java/com/notcan/app/ui/AcademicExtrasViewModel.kt')
s=read(p)
s=s.replace('import com.notcan.app.data.local.NotCanDatabase\n','import com.notcan.app.data.local.NotCanDatabase\nimport com.notcan.app.data.local.TaskItemEntity\n')
s=s.replace('''    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao(), application)\n    private val subjectId = MutableStateFlow<String?>(null)\n''','''    private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao(), application)\n    private val cycleId = MutableStateFlow<String?>(null)\n    private val subjectId = MutableStateFlow<String?>(null)\n''')
if 'val taskItems' not in s:
    s=s.replace('''    val detectedCues: StateFlow<List<DetectedCueEntity>> = classId.flatMapLatest { id ->\n        if (id == null) flowOf(emptyList()) else repository.observeDetectedCues(id)\n    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())\n''','''    val detectedCues: StateFlow<List<DetectedCueEntity>> = classId.flatMapLatest { id ->\n        if (id == null) flowOf(emptyList()) else repository.observeDetectedCues(id)\n    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())\n\n    val taskItems: StateFlow<List<TaskItemEntity>> = cycleId.flatMapLatest { id ->\n        if (id == null) flowOf(emptyList()) else repository.observeTasks(id)\n    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())\n''')
    s=s.replace('''    fun setContext(selectedSubjectId: String?, selectedClassId: String?) {\n        if (subjectId.value != selectedSubjectId) subjectId.value = selectedSubjectId\n''','''    fun setContext(selectedCycleId: String?, selectedSubjectId: String?, selectedClassId: String?) {\n        if (cycleId.value != selectedCycleId) cycleId.value = selectedCycleId\n        if (subjectId.value != selectedSubjectId) subjectId.value = selectedSubjectId\n''')
    s=s.replace('''    fun deleteGrade(id: String) {\n        viewModelScope.launch { repository.deleteGradeItem(id) }\n    }\n''','''    fun deleteGrade(id: String) {\n        viewModelScope.launch { repository.deleteGradeItem(id) }\n    }\n\n    fun addTask(subjectId: String?, title: String, type: String, dueAtEpochMs: Long?, priority: String, notes: String) {\n        val cycle = cycleId.value ?: return\n        viewModelScope.launch { repository.addTask(cycle, subjectId, title, type, dueAtEpochMs, priority, notes) }\n    }\n\n    fun setTaskCompleted(id: String, completed: Boolean) {\n        viewModelScope.launch { repository.setTaskCompleted(id, completed) }\n    }\n\n    fun deleteTask(id: String) {\n        viewModelScope.launch { repository.deleteTask(id) }\n    }\n''')
write(p,s)

# 6) Tasks screen
p=Path('app/src/main/java/com/notcan/app/ui/tasks/TasksScreen.kt')
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''package com.notcan.app.ui.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.TaskItemEntity
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TasksScreen(
    subjects: List<SubjectEntity>,
    items: List<TaskItemEntity>,
    onAdd: (String?, String, String, Long?, String, String) -> Unit,
    onCompleted: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var adding by remember { mutableStateOf(false) }
    val pending = items.count { !it.isCompleted }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, null, tint = NotCanBlue)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tareas", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("$pending pendiente(s) · ${items.count { it.isCompleted }} completada(s)", color = NotCanGray)
                }
                Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Añadir") }
            }
        }
        if (items.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("Tu checklist académico está vacío", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Añade tareas, controles de lectura, lecciones, exposiciones, ensayos o exámenes.", color = NotCanGray)
                }
            }
        }
        items(items, key = { it.id }) { task ->
            val subject = subjects.firstOrNull { it.id == task.subjectId }?.name
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { onCompleted(task.id, it) })
                    Column(Modifier.weight(1f)) {
                        Text(task.title, color = if (task.isCompleted) NotCanGray else NotCanOffWhite, fontWeight = FontWeight.Medium, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
                        val metadata = buildList {
                            add(task.type)
                            subject?.let(::add)
                            task.dueAtEpochMs?.let { add("Entrega ${formatDate(it)}") }
                            if (task.priority != "Normal") add(task.priority)
                        }.joinToString(" · ")
                        if (metadata.isNotBlank()) Text(metadata, color = if (task.priority == "Alta" && !task.isCompleted) MaterialTheme.colorScheme.error else NotCanGray, style = MaterialTheme.typography.bodySmall)
                        if (task.notes.isNotBlank()) Text(task.notes, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onDelete(task.id) }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            }
        }
    }

    if (adding) AddTaskDialog(subjects, onDismiss = { adding = false }) { subjectId, title, type, due, priority, notes ->
        onAdd(subjectId, title, type, due, priority, notes)
        adding = false
    }
}

@Composable
private fun AddTaskDialog(
    subjects: List<SubjectEntity>,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, Long?, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Tarea") }
    var subjectId by remember { mutableStateOf<String?>(null) }
    var due by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Normal") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val types = listOf("Tarea", "Control de lectura", "Lección", "Exposición", "Ensayo", "Examen", "Otro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo pendiente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Tipo", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { value -> FilterChip(selected = type == value, onClick = { type = value }, label = { Text(value) }) }
                }
                Text("Materia (opcional)", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = subjectId == null, onClick = { subjectId = null }, label = { Text("General") })
                    subjects.forEach { subject -> FilterChip(selected = subjectId == subject.id, onClick = { subjectId = subject.id }, label = { Text(subject.name) }) }
                }
                OutlinedTextField(due, { due = it }, label = { Text("Entrega opcional · AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Prioridad", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Baja", "Normal", "Alta").forEach { value -> FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(value) }) }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas opcionales") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { error = "Escribe un título."; return@TextButton }
                val dueEpoch = if (due.isBlank()) null else runCatching {
                    LocalDate.parse(due.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
                if (due.isNotBlank() && dueEpoch == null) { error = "Usa la fecha AAAA-MM-DD."; return@TextButton }
                onSave(subjectId, title, type, dueEpoch, priority, notes)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatDate(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
''')

# 7) Iconography home
p=Path('app/src/main/java/com/notcan/app/ui/theme/NotCanIconography.kt')
s=read(p)
if 'icons.filled.Home' not in s:
    s=s.replace('import androidx.compose.material.icons.filled.Grade\n','import androidx.compose.material.icons.filled.Grade\nimport androidx.compose.material.icons.filled.Home\n')
    s=s.replace('object NotCanIcons {\n','object NotCanIcons {\n    val Home: ImageVector = Icons.Default.Home\n')
write(p,s)

# 8) Root rewrite
p=Path('app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt')
p.write_text(r'''package com.notcan.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.calendar.AcademicSchedule
import com.notcan.app.calendar.PlannedClassOccurrence
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import com.notcan.app.data.local.TaskItemEntity
import com.notcan.app.ui.ai.TuNotOfflineEntry
import com.notcan.app.ui.ai.TuNotQuickAssistant
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanIcons
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import com.notcan.app.ui.theme.NotCanSurfaceHigh
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class RootDestination(val page: Int, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val railDestinations = listOf(
    RootDestination(0, "Inicio", NotCanIcons.Home),
    RootDestination(1, "Materias", NotCanIcons.Subjects),
    RootDestination(2, "Tareas", NotCanIcons.Tasks),
    RootDestination(3, "Calendario", NotCanIcons.Calendar),
    RootDestination(4, "Calificaciones", NotCanIcons.Grades),
    RootDestination(5, "TuNot", NotCanIcons.TuNot)
)
private val phoneDestinations = railDestinations.filter { it.page in listOf(0,1,2,3,5) }

@Composable
fun NotCanRootV5(
    cycle: StudyCycleEntity?, subjects: List<SubjectEntity>, schedules: List<SubjectScheduleEntity>, tasks: List<TaskItemEntity>,
    recordingActive: Boolean = false, autoFocusOnRecording: () -> Boolean = { true },
    onOpenPlannedClass: (PlannedClassOccurrence) -> Unit, onRecordPlannedClass: (PlannedClassOccurrence) -> Unit,
    assistantContextTitle: String = "NotCan", assistantOfflineEntries: List<TuNotOfflineEntry> = emptyList(),
    assistantOnlineConfigured: Boolean = false, assistantBusy: Boolean = false, assistantResult: String = "",
    onAssistantAsk: (String) -> Unit = {},
    subjectsContent: @Composable () -> Unit, tasksContent: @Composable () -> Unit, calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit, gradesContent: @Composable () -> Unit = {}, settingsContent: @Composable () -> Unit = {}
) {
    var page by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }
    var navExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = focusMode || page != 0) { if (focusMode) focusMode = false else { page = 0; navExpanded = false } }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    LaunchedEffect(recordingActive) { if (recordingActive && autoFocusOnRecording()) { focusMode = true; page = 1 } else if (!recordingActive && focusMode) focusMode = false }

    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val plannedNow = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules, zone).firstOrNull { it.isPreviewVisible(now) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp
        if (focusMode) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { focusMode = false }) { Icon(NotCanIcons.Focus, null); Spacer(Modifier.width(6.dp)); Text("Salir de concentración") }
                }
                Box(Modifier.weight(1f)) { subjectsContent() }
            }
            return@BoxWithConstraints
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = page == 0 || navExpanded) {
                    Row {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxHeight()) {
                            Surface(color = NotCanBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 14.dp)) {
                                Text("N", color = NotCanBlue, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp))
                            }
                            railDestinations.forEach { d -> NavigationRailItem(selected = page == d.page, onClick = { page = d.page; navExpanded = false }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) }) }
                            Spacer(Modifier.weight(1f))
                            NavigationRailItem(selected = page == 6, onClick = { page = 6; navExpanded = false }, icon = { Icon(NotCanIcons.Settings, "Configuración") }, label = { Text("Ajustes") })
                        }
                        HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (page != 0) NotCanTopBar(page, true, { navExpanded = !navExpanded }, menuExpanded, { menuExpanded = it }, { page = 4 }, { page = 1; focusMode = true }, { page = 6 })
                    if (page == 0 && plannedNow != null) PlannedClassBanner(plannedNow, { onOpenPlannedClass(plannedNow) }, { onRecordPlannedClass(plannedNow) })
                    RootPage(page, subjectsContent, tasksContent, calendarContent, aiContent, gradesContent, settingsContent, tasks, cycle, subjects, { page = it }, Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (page != 0) NotCanTopBar(page, false, {}, menuExpanded, { menuExpanded = it }, { page = 4 }, { page = 1; focusMode = true }, { page = 6 })
                if (page == 0 && plannedNow != null) PlannedClassBanner(plannedNow, { onOpenPlannedClass(plannedNow) }, { onRecordPlannedClass(plannedNow) })
                RootPage(page, subjectsContent, tasksContent, calendarContent, aiContent, gradesContent, settingsContent, tasks, cycle, subjects, { page = it }, Modifier.weight(1f))
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    phoneDestinations.forEach { d -> NavigationBarItem(selected = page == d.page, onClick = { page = d.page }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) }) }
                }
            }
        }

        if (page != 5) TuNotQuickAssistant(
            contextTitle = assistantContextForPage(page, assistantContextTitle), offlineEntries = assistantOfflineEntries,
            onlineConfigured = assistantOnlineConfigured, onlineBusy = assistantBusy, onlineResult = assistantResult,
            suggestions = assistantSuggestions(page), onAskOnline = onAssistantAsk, onOpenFullChat = { page = 5 },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = if (wide) 22.dp else 16.dp, bottom = if (wide) 20.dp else 82.dp)
        )
    }
}

@Composable
private fun RootPage(page:Int, subjectsContent:@Composable()->Unit, tasksContent:@Composable()->Unit, calendarContent:@Composable()->Unit, aiContent:@Composable()->Unit, gradesContent:@Composable()->Unit, settingsContent:@Composable()->Unit, tasks:List<TaskItemEntity>, cycle:StudyCycleEntity?, subjects:List<SubjectEntity>, onNavigate:(Int)->Unit, modifier:Modifier=Modifier) {
    Box(modifier) { when(page) {
        0 -> HomeDashboard(cycle, subjects, tasks, onNavigate)
        1 -> subjectsContent(); 2 -> tasksContent(); 3 -> calendarContent(); 4 -> gradesContent(); 5 -> aiContent(); else -> settingsContent()
    } }
}

@Composable
private fun HomeDashboard(cycle: StudyCycleEntity?, subjects: List<SubjectEntity>, tasks: List<TaskItemEntity>, onNavigate: (Int)->Unit) {
    val pending = tasks.filterNot { it.isCompleted }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column { Text("Inicio", color=NotCanOffWhite, style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.SemiBold); Text(cycle?.name ?: "Organiza tu estudio en NotCan", color=NotCanGray) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                DashboardCard("Materias", subjects.size.toString(), NotCanIcons.Subjects, Modifier.weight(1f)) { onNavigate(1) }
                DashboardCard("Pendientes", pending.size.toString(), NotCanIcons.Tasks, Modifier.weight(1f)) { onNavigate(2) }
                DashboardCard("Calendario", "Ver", NotCanIcons.Calendar, Modifier.weight(1f)) { onNavigate(3) }
            }
        }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={onNavigate(1)}){Text("Materias")}; Button(onClick={onNavigate(2)}){Text("Tareas")}; TextButton(onClick={onNavigate(4)}){Text("Calificaciones")} } }
        item { Text("Próximos pendientes", color=NotCanOffWhite, fontWeight=FontWeight.SemiBold) }
        if (pending.isEmpty()) item { Text("No tienes tareas pendientes registradas.", color=NotCanGray) }
        else items(pending.take(5), key={it.id}) { task ->
            Card(colors=CardDefaults.cardColors(containerColor=NotCanSurface), modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp)) { Text(task.title,color=NotCanOffWhite,fontWeight=FontWeight.Medium); val due=task.dueAtEpochMs?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))}; Text(listOfNotNull(subjects.firstOrNull{it.id==task.subjectId}?.name, task.type, due?.let{"Entrega $it"}).joinToString(" · "), color=NotCanGray, style=MaterialTheme.typography.bodySmall) } }
        }
    }
}

@Composable
private fun DashboardCard(title:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(onClick=onClick, modifier=modifier, colors=CardDefaults.cardColors(containerColor=NotCanSurface), shape=RoundedCornerShape(16.dp)) { Column(Modifier.padding(14.dp)) { Icon(icon,null,tint=NotCanBlue); Text(value,color=NotCanOffWhite,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Text(title,color=NotCanGray,style=MaterialTheme.typography.bodySmall) } }
}

private fun assistantContextForPage(page:Int,base:String)=when(page){0->"Inicio · $base";1->base;2->"Tareas · $base";3->"Calendario académico · $base";4->"Calificaciones · $base";6->"Ajustes de NotCan";else->base}
private fun assistantSuggestions(page:Int)=when(page){0->listOf("¿Qué estudio hoy?","Organizar pendientes","Próxima clase");1->listOf("Buscar tema","Resumir","Explicar","Crear preguntas");2->listOf("Priorizar tareas","Plan de hoy","Preparar examen");3->listOf("¿Qué tengo mañana?","Organizar estudio","Próxima clase");4->listOf("Analizar rendimiento","¿Qué nota necesito?","Priorizar materias");else->listOf("Preguntar","Buscar tema")}

@Composable
private fun NotCanTopBar(page:Int,showNavigation:Boolean,onNavigation:()->Unit,menuExpanded:Boolean,onMenuExpanded:(Boolean)->Unit,onGrades:()->Unit,onFocus:()->Unit,onSettings:()->Unit) {
    val title=when(page){1->"Materias";2->"Tareas";3->"Calendario académico";4->"Calificaciones";5->"TuNot";else->"Configuración"}
    Surface(color=MaterialTheme.colorScheme.surface){Row(Modifier.fillMaxWidth().padding(start=10.dp,end=8.dp,top=7.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically){if(showNavigation)IconButton(onClick=onNavigation){Icon(Icons.Default.Menu,"Navegación",tint=NotCanOffWhite)};Column(Modifier.weight(1f)){Text(title,color=NotCanOffWhite,style=MaterialTheme.typography.titleLarge);if(page==5)Text("Tutor académico",color=NotCanGray,style=MaterialTheme.typography.bodySmall)};Box{IconButton(onClick={onMenuExpanded(true)}){Icon(NotCanIcons.More,"Más opciones",tint=NotCanOffWhite)};DropdownMenu(expanded=menuExpanded,onDismissRequest={onMenuExpanded(false)}){DropdownMenuItem(text={Text("Calificaciones")},leadingIcon={Icon(NotCanIcons.Grades,null)},onClick={onGrades();onMenuExpanded(false)});DropdownMenuItem(text={Text("Modo concentración")},leadingIcon={Icon(NotCanIcons.Focus,null)},onClick={onFocus();onMenuExpanded(false)});DropdownMenuItem(text={Text("Configuración")},leadingIcon={Icon(NotCanIcons.Settings,null)},onClick={onSettings();onMenuExpanded(false)})}}}}
}

@Composable
private fun PlannedClassBanner(occurrence: PlannedClassOccurrence,onOpen:()->Unit,onRecord:()->Unit){Card(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),colors=CardDefaults.cardColors(containerColor=NotCanSurfaceHigh),shape=RoundedCornerShape(18.dp)){Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Surface(color=NotCanBlue.copy(alpha=.15f),shape=RoundedCornerShape(12.dp)){Icon(NotCanIcons.Schedule,null,tint=NotCanBlue,modifier=Modifier.padding(10.dp))};Column(Modifier.weight(1f)){Text(occurrence.subject.name,color=NotCanOffWhite,fontWeight=FontWeight.SemiBold);Text("${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)} · próxima clase",color=NotCanGray,style=MaterialTheme.typography.bodySmall)};TextButton(onClick=onOpen){Text("Abrir")};Button(onClick=onRecord){Icon(NotCanIcons.Audio,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("Grabar")}}}}
''')

# 9) MainActivity integration
p=Path('app/src/main/java/com/notcan/app/MainActivity.kt')
s=read(p)
if 'ui.tasks.TasksScreen' not in s:
    s=s.replace('import com.notcan.app.ui.settings.SettingsScreen\n','import com.notcan.app.ui.settings.SettingsScreen\nimport com.notcan.app.ui.tasks.TasksScreen\n')
s=s.replace('''                val gradeItems = extrasViewModel.gradeItems.collectAsStateWithLifecycle().value\n                val detectedCues = extrasViewModel.detectedCues.collectAsStateWithLifecycle().value\n\n                LaunchedEffect(selectedSubjectId, selectedClassId) {\n                    extrasViewModel.setContext(selectedSubjectId, selectedClassId)\n                }\n''','''                val gradeItems = extrasViewModel.gradeItems.collectAsStateWithLifecycle().value\n                val detectedCues = extrasViewModel.detectedCues.collectAsStateWithLifecycle().value\n                val taskItems = extrasViewModel.taskItems.collectAsStateWithLifecycle().value\n\n                LaunchedEffect(selectedCycleId, selectedSubjectId, selectedClassId) {\n                    extrasViewModel.setContext(selectedCycleId, selectedSubjectId, selectedClassId)\n                }\n''')
s=s.replace('''                    schedules = schedules,\n                    recordingActive = recordingActive,''','''                    schedules = schedules,\n                    tasks = taskItems,\n                    recordingActive = recordingActive,''')
s=s.replace('''                    classContent = {''','''                    subjectsContent = {''')
needle='''                    calendarContent = {\n                        AcademicCalendarScreen('''
if 'tasksContent = {' not in s:
    block='''                    tasksContent = {\n                        TasksScreen(\n                            subjects = subjects,\n                            items = taskItems,\n                            onAdd = { subjectId, title, type, dueAt, priority, notes -> extrasViewModel.addTask(subjectId, title, type, dueAt, priority, notes) },\n                            onCompleted = extrasViewModel::setTaskCompleted,\n                            onDelete = extrasViewModel::deleteTask\n                        )\n                    },\n'''
    s=s.replace(needle,block+needle)
write(p,s)

# 10) Map controls below map + text fit
p=Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapScreen.kt')
s=read(p)
# remove toolbar call at top of StudyMapScreen
start='''        StudyMapToolbar(\n            map = map,\n            style = layoutStyle,\n            zoom = zoom,\n            onLayoutChange = {\n                layoutStyle = it\n                collapsedNodes = emptySet()\n                fitRequest++\n            },\n            onZoomOut = { zoom = (zoom / 1.18f).coerceIn(0.35f, 3.5f) },\n            onZoomIn = { zoom = (zoom * 1.18f).coerceIn(0.35f, 3.5f) },\n            onFit = { fitRequest++ },\n            onCenter = { centerRequest++ },\n            onExport = ::exportAndShare\n        )\n\n'''
if start in s:
    s=s.replace(start,'',1)
    s=s.replace('BoxWithConstraints(Modifier.fillMaxSize()) {','BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {',1)
    marker='''            }\n        }\n    }\n}\n\n@Composable\nprivate fun StudyMapToolbar'''
    toolbar='''            }\n        }\n        StudyMapToolbar(\n            map = map,\n            style = layoutStyle,\n            zoom = zoom,\n            onLayoutChange = { layoutStyle = it; collapsedNodes = emptySet(); fitRequest++ },\n            onZoomOut = { zoom = (zoom / 1.18f).coerceIn(0.35f, 3.5f) },\n            onZoomIn = { zoom = (zoom * 1.18f).coerceIn(0.35f, 3.5f) },\n            onFit = { fitRequest++ },\n            onCenter = { centerRequest++ },\n            onExport = ::exportAndShare\n        )\n    }\n}\n\n@Composable\nprivate fun StudyMapToolbar'''
    if marker not in s: raise SystemExit('map end marker missing')
    s=s.replace(marker,toolbar,1)
# no ellipsis inside nodes
s=s.replace('''                    maxLines = if (visualCard) 4 else 3,\n                    overflow = TextOverflow.Ellipsis,\n                    modifier = Modifier.weight(1f)''','''                    modifier = Modifier.weight(1f)''')
s=s.replace('''                    maxLines = if (visualCard) 5 else 3,\n                    overflow = TextOverflow.Ellipsis\n''','''                    softWrap = true\n''')
s=s.replace('''                    maxLines = 1,\n                    overflow = TextOverflow.Ellipsis\n''','''                    softWrap = true\n''',1)
write(p,s)

# 11) Layout estimated heights
p=Path('app/src/main/java/com/notcan/app/ui/maps/StudyMapLayoutEngine.kt')
s=read(p)
if 'estimatedNodeHeight' not in s:
    s=s.replace('''object StudyMapLayoutEngine {\n''','''object StudyMapLayoutEngine {\n    private fun estimatedNodeHeight(node: StudyMapNode, base: Float): Float {\n        val chars = node.title.length + (node.description?.length ?: 0)\n        val extra = (chars / 34).coerceAtMost(7) * 17f\n        val sourceExtra = if (node.sourceRefs.isNotEmpty()) 18f else 0f\n        return (base + extra + sourceExtra).coerceAtMost(220f)\n    }\n''')
    s=s.replace('val rootHeight = 96f','val rootHeight = estimatedNodeHeight(root, 96f)',1)
    s=s.replace('''                val cardHeight = when {\n                    depth <= 1 -> 92f\n                    depth == 2 -> 82f\n                    else -> 74f\n                }''','''                val cardHeight = estimatedNodeHeight(node, when {\n                    depth <= 1 -> 92f\n                    depth == 2 -> 82f\n                    else -> 74f\n                })''',1)
    s=s.replace('PositionedStudyMapNode(root, centerX - 115f, centerY - 48f, 230f, 96f)','PositionedStudyMapNode(root, centerX - 115f, centerY - estimatedNodeHeight(root, 96f)/2f, 230f, estimatedNodeHeight(root, 96f))')
    s=s.replace('val cardHeight = if (depth == 1) 90f else 78f','val cardHeight = estimatedNodeHeight(node, if (depth == 1) 90f else 78f)')
    s=s.replace('val rootHeight = 108f','val rootHeight = estimatedNodeHeight(root, 108f)',1)
    s=s.replace('val cardHeight = 124f','val cardHeight = estimatedNodeHeight(node, 124f)',1)
    s=s.replace('PositionedStudyMapNode(child, childCenterX - 90f, childCenterY - 40f, 180f, 80f)','PositionedStudyMapNode(child, childCenterX - 90f, childCenterY - estimatedNodeHeight(child, 80f)/2f, 180f, estimatedNodeHeight(child, 80f))')
    s=s.replace('PositionedStudyMapNode(root, centerX - 125f, centerY - 65f, 250f, 130f)','PositionedStudyMapNode(root, centerX - 125f, centerY - estimatedNodeHeight(root, 130f)/2f, 250f, estimatedNodeHeight(root, 130f))')
    s=s.replace('PositionedStudyMapNode(node, cx - 120f, cy - 72f, 240f, 144f)','PositionedStudyMapNode(node, cx - 120f, cy - estimatedNodeHeight(node, 144f)/2f, 240f, estimatedNodeHeight(node, 144f))')
    s=s.replace('PositionedStudyMapNode(detail, detailCx - 90f, detailCy - 40f, 180f, 80f)','PositionedStudyMapNode(detail, detailCx - 90f, detailCy - estimatedNodeHeight(detail, 80f)/2f, 180f, estimatedNodeHeight(detail, 80f))')
    s=s.replace('val cardHeight = if (depth == 0) 96f else 84f','val cardHeight = estimatedNodeHeight(node, if (depth == 0) 96f else 84f)')
write(p,s)

# 12) bump version
p=Path('app/build.gradle.kts')
s=read(p).replace('versionCode = 19','versionCode = 20').replace('versionName = "0.8.2"','versionName = "0.8.3"')
write(p,s)
