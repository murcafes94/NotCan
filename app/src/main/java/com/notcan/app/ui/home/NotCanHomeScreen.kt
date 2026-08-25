package com.notcan.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGraphite
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class CreateDialog {
    Cycle,
    Subject,
    Class
}

@Composable
fun NotCanHomeScreen(
    recordingState: RecordingState,
    cycles: List<StudyCycleEntity>,
    subjects: List<SubjectEntity>,
    classes: List<ClassSessionEntity>,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    selectedCycleId: String?,
    selectedSubjectId: String?,
    selectedClassId: String?,
    onSelectCycle: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onCreateCycle: (String) -> Unit,
    onCreateSubject: (String) -> Unit,
    onCreateClass: (String) -> Unit,
    onStartRecording: (String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    var createDialog by remember { mutableStateOf<CreateDialog?>(null) }

    val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth >= 900.dp

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    StudySidebar(
                        cycles = cycles,
                        selectedCycleId = selectedCycleId,
                        onSelectCycle = onSelectCycle,
                        onAddCycle = { createDialog = CreateDialog.Cycle }
                    )

                    StudyNavigator(
                        modifier = Modifier.width(292.dp),
                        subjects = subjects,
                        classes = classes,
                        selectedSubjectId = selectedSubjectId,
                        selectedClassId = selectedClassId,
                        onSelectSubject = onSelectSubject,
                        onSelectClass = onSelectClass,
                        onAddSubject = { createDialog = CreateDialog.Subject },
                        onAddClass = { createDialog = CreateDialog.Class }
                    )

                    ClassWorkspace(
                        modifier = Modifier.weight(1f),
                        cycleName = selectedCycle?.name,
                        subject = selectedSubject,
                        classSession = selectedClass,
                        audioRecordings = audioRecordings,
                        importantMoments = importantMoments,
                        recordingState = recordingState,
                        onStartRecording = onStartRecording,
                        onPauseRecording = onPauseRecording,
                        onResumeRecording = onResumeRecording,
                        onStopRecording = onStopRecording,
                        onMarkMoment = onMarkMoment
                    )
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    CompactNavigation(
                        hasCycle = selectedCycleId != null,
                        onAddCycle = { createDialog = CreateDialog.Cycle }
                    )
                    Column(Modifier.weight(1f)) {
                        StudyNavigator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(265.dp),
                            subjects = subjects,
                            classes = classes,
                            selectedSubjectId = selectedSubjectId,
                            selectedClassId = selectedClassId,
                            onSelectSubject = onSelectSubject,
                            onSelectClass = onSelectClass,
                            onAddSubject = { createDialog = CreateDialog.Subject },
                            onAddClass = { createDialog = CreateDialog.Class }
                        )
                        ClassWorkspace(
                            modifier = Modifier.weight(1f),
                            cycleName = selectedCycle?.name,
                            subject = selectedSubject,
                            classSession = selectedClass,
                            audioRecordings = audioRecordings,
                            importantMoments = importantMoments,
                            recordingState = recordingState,
                            onStartRecording = onStartRecording,
                            onPauseRecording = onPauseRecording,
                            onResumeRecording = onResumeRecording,
                            onStopRecording = onStopRecording,
                            onMarkMoment = onMarkMoment
                        )
                    }
                }
            }
        }
    }

    createDialog?.let { dialog ->
        val enabled = when (dialog) {
            CreateDialog.Cycle -> true
            CreateDialog.Subject -> selectedCycleId != null
            CreateDialog.Class -> selectedSubjectId != null
        }

        NameEntryDialog(
            title = when (dialog) {
                CreateDialog.Cycle -> "Nuevo ciclo"
                CreateDialog.Subject -> "Nueva materia"
                CreateDialog.Class -> "Nueva clase"
            },
            label = when (dialog) {
                CreateDialog.Cycle -> "Ej. 2026 · Segundo semestre"
                CreateDialog.Subject -> "Nombre de la materia"
                CreateDialog.Class -> "Título de la clase"
            },
            enabled = enabled,
            onDismiss = { createDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    CreateDialog.Cycle -> onCreateCycle(name)
                    CreateDialog.Subject -> onCreateSubject(name)
                    CreateDialog.Class -> onCreateClass(name)
                }
                createDialog = null
            }
        )
    }
}

@Composable
private fun StudySidebar(
    cycles: List<StudyCycleEntity>,
    selectedCycleId: String?,
    onSelectCycle: (String) -> Unit,
    onAddCycle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(224.dp)
            .fillMaxHeight(),
        color = NotCanGraphite
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "NotCan",
                color = NotCanOffWhite,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Tu espacio académico", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(26.dp))

            SidebarItem(Icons.Default.Book, "Materias", selected = true)
            SidebarItem(Icons.Default.School, "Clases")
            SidebarItem(Icons.Default.LibraryBooks, "Biblioteca")
            SidebarItem(Icons.Default.Description, "Apuntes")
            SidebarItem(Icons.Default.Star, "Estudio final")

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CICLOS", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                FilledTonalIconButton(onClick = onAddCycle, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Crear ciclo")
                }
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cycles, key = { it.id }) { cycle ->
                    val selected = cycle.id == selectedCycleId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCycle(cycle.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) NotCanBlue.copy(alpha = 0.17f) else Color.Transparent
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cycle.isActive) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .background(NotCanBlue, CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                cycle.name,
                                color = if (selected) NotCanOffWhite else NotCanGray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(icon: ImageVector, label: String, selected: Boolean = false) {
    val background = if (selected) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent
    val foreground = if (selected) NotCanOffWhite else NotCanGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) NotCanBlue else foreground)
        Spacer(Modifier.width(12.dp))
        Text(label, color = foreground)
    }
}

@Composable
private fun CompactNavigation(
    hasCycle: Boolean,
    onAddCycle: () -> Unit
) {
    NavigationRail(containerColor = NotCanGraphite) {
        NavigationRailItem(
            selected = true,
            onClick = { },
            icon = { Icon(Icons.Default.Book, contentDescription = "Materias") },
            label = { Text("Materias") }
        )
        NavigationRailItem(
            selected = false,
            onClick = { },
            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Biblioteca") },
            label = { Text("Biblioteca") }
        )
        NavigationRailItem(
            selected = false,
            onClick = onAddCycle,
            icon = { Icon(Icons.Default.Add, contentDescription = "Nuevo ciclo") },
            label = { Text(if (hasCycle) "Ciclo" else "Crear") }
        )
    }
}

@Composable
private fun StudyNavigator(
    modifier: Modifier,
    subjects: List<SubjectEntity>,
    classes: List<ClassSessionEntity>,
    selectedSubjectId: String?,
    selectedClassId: String?,
    onSelectSubject: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onAddSubject: () -> Unit,
    onAddClass: () -> Unit
) {
    Surface(modifier = modifier.fillMaxHeight(), color = NotCanSurface.copy(alpha = 0.72f)) {
        Column(Modifier.padding(14.dp)) {
            SectionHeader("Materias", enabled = true, onAdd = onAddSubject)
            Spacer(Modifier.height(8.dp))

            if (subjects.isEmpty()) {
                SmallEmptyMessage("Crea una materia dentro del ciclo seleccionado.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(0.42f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(subjects, key = { it.id }) { subject ->
                        NavigatorItem(
                            title = subject.name,
                            selected = subject.id == selectedSubjectId,
                            onClick = { onSelectSubject(subject.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = NotCanGray.copy(alpha = 0.18f))
            Spacer(Modifier.height(12.dp))

            SectionHeader("Clases", enabled = selectedSubjectId != null, onAdd = onAddClass)
            Spacer(Modifier.height(8.dp))

            if (selectedSubjectId == null) {
                SmallEmptyMessage("Selecciona una materia para ver sus clases.")
            } else if (classes.isEmpty()) {
                SmallEmptyMessage("Aún no hay clases. Crea la primera con +.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(0.58f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(classes, key = { it.id }) { classSession ->
                        NavigatorItem(
                            title = classSession.title,
                            subtitle = formatDate(classSession.startedAtEpochMs),
                            selected = classSession.id == selectedClassId,
                            onClick = { onSelectClass(classSession.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    enabled: Boolean,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onAdd, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Añadir $title")
        }
    }
}

@Composable
private fun NavigatorItem(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                title,
                color = if (selected) NotCanOffWhite else NotCanGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = NotCanGray.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SmallEmptyMessage(message: String) {
    Text(
        message,
        color = NotCanGray,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun ClassWorkspace(
    modifier: Modifier,
    cycleName: String?,
    subject: SubjectEntity?,
    classSession: ClassSessionEntity?,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    recordingState: RecordingState,
    onStartRecording: (String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    Box(modifier.fillMaxSize()) {
        if (classSession == null) {
            EmptyWorkspace(
                cycleName = cycleName,
                hasSubject = subject != null,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    listOfNotNull(cycleName, subject?.name).joinToString(" · "),
                    color = NotCanGray,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    classSession.title,
                    color = NotCanOffWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(18.dp))
                WorkspaceTabs(
                    audioRecordings = audioRecordings,
                    importantMoments = importantMoments
                )
            }
        }

        RecordingControls(
            state = recordingState,
            selectedClassId = classSession?.id,
            onStart = onStartRecording,
            onPause = onPauseRecording,
            onResume = onResumeRecording,
            onStop = onStopRecording,
            onMark = onMarkMoment,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
        )
    }
}

@Composable
private fun EmptyWorkspace(
    cycleName: String?,
    hasSubject: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.School, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                cycleName == null -> "Crea tu primer ciclo"
                !hasSubject -> "Crea o selecciona una materia"
                else -> "Crea o selecciona una clase"
            },
            color = NotCanOffWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Audio, transcripción, apuntes y documentos quedarán interconectados dentro de cada clase.",
            color = NotCanGray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WorkspaceTabs(
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>
) {
    var selected by remember { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Transcripción", "Apuntes", "PDF", "EPUB", "Mapa mental")

    TabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        contentColor = NotCanBlue,
        divider = { }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selected == index,
                onClick = { selected = index },
                text = { Text(title, maxLines = 1) }
            )
        }
    }

    Spacer(Modifier.height(18.dp))

    when (selected) {
        0 -> AudioContent(audioRecordings, importantMoments)
        1 -> ModulePlaceholder("Transcripción", "Aquí quedará la transcripción en vivo y la revisión final con Gemini.")
        2 -> ModulePlaceholder("Apuntes", "El editor enriquecido y Pencil se conectarán a esta clase sin depender de internet.")
        3 -> ModulePlaceholder("PDF", "Los PDF importados y sus anotaciones permanecerán vinculados a esta clase.")
        4 -> ModulePlaceholder("EPUB", "Lectura, subrayados y notas ancladas al texto del EPUB.")
        else -> ModulePlaceholder("Mapa mental", "Los mapas generados o editados quedarán disponibles también sin conexión.")
    }
}

@Composable
private fun AudioContent(
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NotCanSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Grabaciones de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("El audio original permanece guardado localmente durante el ciclo.", color = NotCanGray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (audioRecordings.isEmpty()) {
                    Text("Todavía no hay grabaciones.", color = NotCanGray)
                } else {
                    audioRecordings.take(5).forEach { audio ->
                        AudioRow(audio)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = NotCanGraphite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Momentos importantes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (importantMoments.isEmpty()) {
                    Text("Pulsa ✴ durante la clase para guardar un instante importante.", color = NotCanGray)
                } else {
                    importantMoments.take(12).forEach { moment ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(formatDuration(moment.offsetMs), color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                            moment.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Spacer(Modifier.width(10.dp))
                                Text(note, color = NotCanGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRow(audio: AudioRecordingEntity) {
    Surface(
        color = NotCanGraphite,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir", tint = NotCanBlue)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    File(audio.localPath).name,
                    color = NotCanOffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatDateTime(audio.createdAtEpochMs), color = NotCanGray, style = MaterialTheme.typography.labelSmall)
            }
            Text(formatDuration(audio.durationMs), color = NotCanGray)
        }
    }
}

@Composable
private fun ModulePlaceholder(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = NotCanGray)
        }
    }
}

@Composable
private fun RecordingControls(
    state: RecordingState,
    selectedClassId: String?,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val active = state is RecordingState.Recording || state is RecordingState.Paused

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (active) {
            RoundControl(
                icon = Icons.Default.Star,
                contentDescription = "Marcar momento importante",
                tint = NotCanOffWhite,
                background = NotCanBlue,
                onClick = onMark
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = active && expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is RecordingState.Recording -> RoundControl(
                            icon = Icons.Default.Pause,
                            contentDescription = "Pausar grabación",
                            tint = NotCanOffWhite,
                            background = NotCanSurface,
                            onClick = onPause
                        )
                        is RecordingState.Paused -> RoundControl(
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Reanudar grabación",
                            tint = NotCanOffWhite,
                            background = NotCanSurface,
                            onClick = onResume
                        )
                        else -> Unit
                    }
                    RoundControl(
                        icon = Icons.Default.Stop,
                        contentDescription = "Detener grabación",
                        tint = NotCanOffWhite,
                        background = NotCanSurface,
                        onClick = onStop
                    )
                }
            }

            if (!active) {
                RoundControl(
                    icon = Icons.Default.RadioButtonChecked,
                    contentDescription = if (selectedClassId == null) "Selecciona una clase para grabar" else "Comenzar grabación",
                    tint = if (selectedClassId == null) NotCanGray else NotCanRed,
                    background = NotCanGraphite,
                    enabled = selectedClassId != null,
                    onClick = { selectedClassId?.let(onStart) }
                )
            } else {
                RoundControl(
                    icon = Icons.Default.Circle,
                    contentDescription = "Controles de grabación",
                    tint = NotCanRed,
                    background = NotCanGraphite,
                    onClick = { expanded = !expanded }
                )
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = background,
        shadowElevation = 5.dp
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

@Composable
private fun NameEntryDialog(
    title: String,
    label: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(title) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!enabled) {
                    Spacer(Modifier.height(8.dp))
                    Text("Primero selecciona el nivel anterior.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = enabled && text.isNotBlank()
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochMs))

private fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))
