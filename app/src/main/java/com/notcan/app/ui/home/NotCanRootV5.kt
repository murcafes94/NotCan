package com.notcan.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
private val phoneDestinations = railDestinations.filter { it.page in listOf(0, 1, 2, 3, 5) }

@Composable
fun NotCanRootV5(
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    tasks: List<TaskItemEntity>,
    recordingActive: Boolean = false,
    autoFocusOnRecording: () -> Boolean = { true },
    onOpenPlannedClass: (PlannedClassOccurrence) -> Unit,
    onRecordPlannedClass: (PlannedClassOccurrence) -> Unit,
    assistantContextTitle: String = "NotCan",
    assistantOfflineEntries: List<TuNotOfflineEntry> = emptyList(),
    assistantOnlineConfigured: Boolean = false,
    assistantBusy: Boolean = false,
    assistantResult: String = "",
    onAssistantAsk: (String) -> Unit = {},
    subjectsContent: @Composable () -> Unit,
    tasksContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit,
    gradesContent: @Composable () -> Unit = {},
    settingsContent: @Composable () -> Unit = {}
) {
    var page by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }
    var navExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = focusMode || page != 0) {
        if (focusMode) focusMode = false
        else {
            page = 0
            navExpanded = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(recordingActive) {
        if (recordingActive && autoFocusOnRecording()) {
            focusMode = true
            page = 1
        } else if (!recordingActive && focusMode) {
            focusMode = false
        }
    }

    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val plannedNow = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules, zone)
        .firstOrNull { it.isPreviewVisible(now) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp

        if (focusMode) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { focusMode = false }) {
                        Icon(NotCanIcons.Focus, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Salir de concentración")
                    }
                }
                Box(Modifier.weight(1f)) { subjectsContent() }
            }
            return@BoxWithConstraints
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = page == 0 || navExpanded) {
                    Row {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Surface(
                                color = NotCanBlue.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.padding(vertical = 14.dp)
                            ) {
                                Text(
                                    "N",
                                    color = NotCanBlue,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp)
                                )
                            }
                            railDestinations.forEach { d ->
                                NavigationRailItem(
                                    selected = page == d.page,
                                    onClick = { page = d.page; navExpanded = false },
                                    icon = { Icon(d.icon, d.label) },
                                    label = { Text(d.label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            NavigationRailItem(
                                selected = page == 6,
                                onClick = { page = 6; navExpanded = false },
                                icon = { Icon(NotCanIcons.Settings, "Configuración") },
                                label = { Text("Ajustes") }
                            )
                        }
                        HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                    }
                }

                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (page != 0) {
                        NotCanTopBar(
                            page,
                            true,
                            { navExpanded = !navExpanded },
                            menuExpanded,
                            { menuExpanded = it },
                            { page = 4 },
                            { page = 1; focusMode = true },
                            { page = 6 }
                        )
                    }
                    if (page == 0 && plannedNow != null) {
                        PlannedClassBanner(plannedNow, { onOpenPlannedClass(plannedNow) }, { onRecordPlannedClass(plannedNow) })
                    }
                    RootPage(
                        page,
                        subjectsContent,
                        tasksContent,
                        calendarContent,
                        aiContent,
                        gradesContent,
                        settingsContent,
                        tasks,
                        cycle,
                        subjects,
                        schedules,
                        recordingActive,
                        now,
                        { page = it },
                        Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (page != 0) {
                    NotCanTopBar(
                        page,
                        false,
                        {},
                        menuExpanded,
                        { menuExpanded = it },
                        { page = 4 },
                        { page = 1; focusMode = true },
                        { page = 6 }
                    )
                }
                if (page == 0 && plannedNow != null) {
                    PlannedClassBanner(plannedNow, { onOpenPlannedClass(plannedNow) }, { onRecordPlannedClass(plannedNow) })
                }
                RootPage(
                    page,
                    subjectsContent,
                    tasksContent,
                    calendarContent,
                    aiContent,
                    gradesContent,
                    settingsContent,
                        tasks,
                        cycle,
                        subjects,
                        schedules,
                        recordingActive,
                        now,
                        { page = it },
                    Modifier.weight(1f)
                )
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    phoneDestinations.forEach { d ->
                        NavigationBarItem(
                            selected = page == d.page,
                            onClick = { page = d.page },
                            icon = { Icon(d.icon, d.label) },
                            label = { Text(d.label) }
                        )
                    }
                }
            }
        }

        if (page != 5) {
            TuNotQuickAssistant(
                contextTitle = assistantContextForPage(page, assistantContextTitle),
                offlineEntries = assistantOfflineEntries,
                onlineConfigured = assistantOnlineConfigured,
                onlineBusy = assistantBusy,
                onlineResult = assistantResult,
                suggestions = assistantSuggestions(page),
                onAskOnline = onAssistantAsk,
                onOpenFullChat = { page = 5 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (wide) 22.dp else 16.dp, bottom = if (wide) 20.dp else 82.dp)
            )
        }
    }
}

@Composable
private fun RootPage(
    page: Int,
    subjectsContent: @Composable () -> Unit,
    tasksContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit,
    gradesContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    tasks: List<TaskItemEntity>,
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    recordingActive: Boolean,
    now: Long,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when (page) {
            0 -> HomeDashboard(cycle, subjects, schedules, tasks, recordingActive, now, onNavigate)
            1 -> subjectsContent()
            2 -> tasksContent()
            3 -> calendarContent()
            4 -> gradesContent()
            5 -> aiContent()
            else -> settingsContent()
        }
    }
}

@Composable
private fun HomeDashboard(
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    tasks: List<TaskItemEntity>,
    recordingActive: Boolean,
    now: Long,
    onNavigate: (Int) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val todayClasses = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules, zone)
    val nextClass = AcademicSchedule.nextOccurrence(now, cycle, subjects, schedules, zone, horizonDays = 8)
    val pending = tasks.filterNot { it.isCompleted }.sortedWith(
        compareBy<TaskItemEntity> {
            when (it.priority.lowercase()) {
                "alta" -> 0
                "normal" -> 1
                else -> 2
            }
        }.thenBy { it.dueAtEpochMs ?: Long.MAX_VALUE }
    )
    val completed = tasks.count { it.isCompleted }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Inicio",
                        color = NotCanOffWhite,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(cycle?.name ?: "Tu espacio académico en NotCan", color = NotCanGray)
                }
                Surface(
                    color = if (recordingActive) NotCanBlue.copy(alpha = 0.18f) else NotCanSurface,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        if (recordingActive) "Clase en curso" else "Todo listo",
                        color = if (recordingActive) NotCanBlue else NotCanGray,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard("Materias", subjects.size.toString(), NotCanIcons.Subjects, Modifier.weight(1f)) { onNavigate(1) }
                DashboardCard("Pendientes", pending.size.toString(), NotCanIcons.Tasks, Modifier.weight(1f)) { onNavigate(2) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard("Clases hoy", todayClasses.size.toString(), NotCanIcons.Calendar, Modifier.weight(1f)) { onNavigate(3) }
                DashboardCard("Horarios", schedules.size.toString(), NotCanIcons.Schedule, Modifier.weight(1f)) { onNavigate(3) }
            }
        }

        item {
            Card(
                onClick = { onNavigate(3) },
                colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(NotCanIcons.Calendar, null, tint = NotCanBlue)
                        Spacer(Modifier.width(9.dp))
                        Text("Próxima clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    }
                    if (nextClass == null) {
                        Text("No hay otra clase programada en los próximos días.", color = NotCanGray)
                    } else {
                        Text(nextClass.subject.name, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium)
                        val dateLabel = if (nextClass.date == today) "Hoy" else nextClass.date.format(DateTimeFormatter.ofPattern("EEE dd/MM"))
                        Text(
                            "$dateLabel · ${AcademicSchedule.formatMinutes(nextClass.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(nextClass.schedule.endMinuteOfDay)}",
                            color = NotCanGray
                        )
                    }
                }
            }
        }

        item {
            Text("Resumen académico", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardCard("Calificaciones", "Revisar", NotCanIcons.Grades, Modifier.weight(1f)) { onNavigate(4) }
                DashboardCard("TuNot", "Abrir", NotCanIcons.TuNot, Modifier.weight(1f)) { onNavigate(5) }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tareas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("${pending.size} pendientes · $completed completadas", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onNavigate(2) }) { Text("Ver todas") }
                }
            }
        }

        item { Text("Pendientes prioritarios", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold) }
        if (pending.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                    Text("No tienes tareas pendientes registradas.", color = NotCanGray, modifier = Modifier.padding(15.dp))
                }
            }
        } else {
            items(pending.take(5), key = { it.id }) { task ->
                Card(
                    onClick = { onNavigate(2) },
                    colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(task.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (task.priority.equals("Alta", ignoreCase = true)) {
                                Text("Alta", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val due = task.dueAtEpochMs?.let {
                            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"))
                        }
                        Text(
                            listOfNotNull(
                                subjects.firstOrNull { it.id == task.subjectId }?.name,
                                task.type,
                                due?.let { "Entrega $it" }
                            ).joinToString(" · "),
                            color = NotCanGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Text("Accesos rápidos", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { onNavigate(1) }) { Text("Materias") }
                Button(onClick = { onNavigate(2) }) { Text("Tareas") }
                TextButton(onClick = { onNavigate(3) }) { Text("Calendario") }
                TextButton(onClick = { onNavigate(4) }) { Text("Calificaciones") }
                TextButton(onClick = { onNavigate(5) }) { Text("TuNot") }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = NotCanBlue)
            Text(value, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun assistantContextForPage(page: Int, base: String): String = when (page) {
    0 -> "Inicio · $base"
    1 -> base
    2 -> "Tareas · $base"
    3 -> "Calendario académico · $base"
    4 -> "Calificaciones · $base"
    6 -> "Ajustes de NotCan"
    else -> base
}

private fun assistantSuggestions(page: Int): List<String> = when (page) {
    0 -> listOf("¿Qué estudio hoy?", "Organizar pendientes", "Próxima clase")
    1 -> listOf("Buscar tema", "Resumir", "Explicar", "Crear preguntas")
    2 -> listOf("Priorizar tareas", "Plan de hoy", "Preparar examen")
    3 -> listOf("¿Qué tengo mañana?", "Organizar estudio", "Próxima clase")
    4 -> listOf("Analizar rendimiento", "¿Qué nota necesito?", "Priorizar materias")
    else -> listOf("Preguntar", "Buscar tema")
}

@Composable
private fun NotCanTopBar(
    page: Int,
    showNavigation: Boolean,
    onNavigation: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    onGrades: () -> Unit,
    onFocus: () -> Unit,
    onSettings: () -> Unit
) {
    val title = when (page) {
        1 -> "Materias"
        2 -> "Tareas"
        3 -> "Calendario académico"
        4 -> "Calificaciones"
        5 -> "TuNot"
        else -> "Configuración"
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showNavigation) {
                IconButton(onClick = onNavigation) {
                    Icon(Icons.Default.Menu, "Navegación", tint = NotCanOffWhite)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge)
                if (page == 5) Text("Tutor académico", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { onMenuExpanded(true) }) {
                    Icon(NotCanIcons.More, "Más opciones", tint = NotCanOffWhite)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpanded(false) }) {
                    DropdownMenuItem(
                        text = { Text("Calificaciones") },
                        leadingIcon = { Icon(NotCanIcons.Grades, null) },
                        onClick = { onGrades(); onMenuExpanded(false) }
                    )
                    DropdownMenuItem(
                        text = { Text("Modo concentración") },
                        leadingIcon = { Icon(NotCanIcons.Focus, null) },
                        onClick = { onFocus(); onMenuExpanded(false) }
                    )
                    DropdownMenuItem(
                        text = { Text("Configuración") },
                        leadingIcon = { Icon(NotCanIcons.Settings, null) },
                        onClick = { onSettings(); onMenuExpanded(false) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannedClassBanner(
    occurrence: PlannedClassOccurrence,
    onOpen: () -> Unit,
    onRecord: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(color = NotCanBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                Icon(NotCanIcons.Schedule, null, tint = NotCanBlue, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(occurrence.subject.name, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text(
                    "${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)} · próxima clase",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onOpen) { Text("Abrir") }
            Button(onClick = onRecord) {
                Icon(NotCanIcons.Audio, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grabar")
            }
        }
    }
}
