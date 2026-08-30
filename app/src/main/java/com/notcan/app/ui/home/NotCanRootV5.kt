package com.notcan.app.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.notcan.app.ui.ai.TuNotOfflineEntry
import com.notcan.app.ui.ai.TuNotQuickAssistant
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanIcons
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurfaceHigh
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private data class RootDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val primaryDestinations = listOf(
    RootDestination("Materias", NotCanIcons.Subjects),
    RootDestination("Calendario", NotCanIcons.Calendar),
    RootDestination("TuNot", NotCanIcons.TuNot)
)

@Composable
fun NotCanRootV5(
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
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
    classContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit,
    gradesContent: @Composable () -> Unit = {},
    settingsContent: @Composable () -> Unit = {}
) {
    var page by remember { mutableIntStateOf(0) }
    var previousPage by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menuExpanded by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }

    BackHandler(enabled = focusMode || page != 0) {
        when {
            focusMode -> focusMode = false
            page == 3 || page == 4 -> page = previousPage.coerceIn(0, 2)
            else -> page = 0
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
            page = 0
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
                Box(Modifier.weight(1f)) { classContent() }
            }
            return@BoxWithConstraints
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
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
                    primaryDestinations.forEachIndexed { index, destination ->
                        NavigationRailItem(
                            selected = page == index,
                            onClick = {
                                if (page in 0..2) previousPage = page
                                page = index
                            },
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = page == 3,
                        onClick = { previousPage = page.coerceIn(0, 2); page = 3 },
                        icon = { Icon(NotCanIcons.Grades, "Calificaciones") },
                        label = { Text("Notas") }
                    )
                    NavigationRailItem(
                        selected = page == 4,
                        onClick = { previousPage = page.coerceIn(0, 2); page = 4 },
                        icon = { Icon(NotCanIcons.Settings, "Configuración") },
                        label = { Text("Ajustes") }
                    )
                }
                HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (page != 0) {
                        NotCanTopBar(
                            page = page,
                            menuExpanded = menuExpanded,
                            onMenuExpanded = { menuExpanded = it },
                            onGrades = { previousPage = page.coerceIn(0, 2); page = 3 },
                            onFocus = { page = 0; focusMode = true },
                            onSettings = { previousPage = page.coerceIn(0, 2); page = 4 }
                        )
                    }
                    if (page == 0 && plannedNow != null) {
                        PlannedClassBanner(plannedNow, { onOpenPlannedClass(plannedNow) }, { onRecordPlannedClass(plannedNow) })
                    }
                    RootPage(page, classContent, calendarContent, aiContent, gradesContent, settingsContent, Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (page != 0) {
                    NotCanTopBar(
                        page = page,
                        menuExpanded = menuExpanded,
                        onMenuExpanded = { menuExpanded = it },
                        onGrades = { previousPage = page.coerceIn(0, 2); page = 3 },
                        onFocus = { page = 0; focusMode = true },
                        onSettings = { previousPage = page.coerceIn(0, 2); page = 4 }
                    )
                }
                if (page == 0 && plannedNow != null) {
                    PlannedClassBanner(plannedNow, { onOpenPlannedClass(plannedNow) }, { onRecordPlannedClass(plannedNow) })
                }
                RootPage(page, classContent, calendarContent, aiContent, gradesContent, settingsContent, Modifier.weight(1f))
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    primaryDestinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = page == index,
                            onClick = {
                                if (page in 0..2) previousPage = page
                                page = index
                            },
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }

        if (page != 2) {
            val assistantBottomPadding = if (wide) 20.dp else 82.dp
            TuNotQuickAssistant(
                contextTitle = assistantContextForPage(page, assistantContextTitle),
                offlineEntries = assistantOfflineEntries,
                onlineConfigured = assistantOnlineConfigured,
                onlineBusy = assistantBusy,
                onlineResult = assistantResult,
                suggestions = assistantSuggestions(page),
                onAskOnline = onAssistantAsk,
                onOpenFullChat = {
                    if (page in 0..2) previousPage = page
                    page = 2
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (wide) 22.dp else 16.dp, bottom = assistantBottomPadding)
            )
        }
    }
}

private fun assistantContextForPage(page: Int, base: String): String = when (page) {
    0 -> base
    1 -> "Calendario académico · $base"
    3 -> "Calificaciones · $base"
    4 -> "Ajustes de NotCan"
    else -> base
}

private fun assistantSuggestions(page: Int): List<String> = when (page) {
    0 -> listOf("Buscar tema", "Resumir", "Explicar", "Crear preguntas")
    1 -> listOf("¿Qué tengo mañana?", "Organizar estudio", "Próxima clase")
    3 -> listOf("Analizar rendimiento", "¿Qué nota necesito?", "Priorizar materias")
    4 -> listOf("¿Cómo funciona NotCan?", "Ayuda con TuNot")
    else -> listOf("Preguntar", "Buscar tema")
}

@Composable
private fun NotCanTopBar(
    page: Int,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    onGrades: () -> Unit,
    onFocus: () -> Unit,
    onSettings: () -> Unit
) {
    val title = when (page) {
        0 -> "NotCan"
        1 -> "Calendario académico"
        2 -> "TuNot"
        3 -> "Calificaciones"
        else -> "Configuración"
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge)
                if (page == 2) Text("Tutor académico católico", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
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
private fun RootPage(
    page: Int,
    classContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit,
    gradesContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when (page) {
            0 -> classContent()
            1 -> calendarContent()
            2 -> aiContent()
            3 -> gradesContent()
            else -> settingsContent()
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(color = NotCanBlue.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                Icon(NotCanIcons.Schedule, contentDescription = null, tint = NotCanBlue, modifier = Modifier.padding(10.dp))
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
                Icon(NotCanIcons.Audio, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grabar")
            }
        }
    }
}
