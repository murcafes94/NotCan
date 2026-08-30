package com.notcan.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

@Composable
fun NotCanRootV5(
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    recordingActive: Boolean = false,
    autoFocusOnRecording: () -> Boolean = { true },
    onOpenPlannedClass: (PlannedClassOccurrence) -> Unit,
    onRecordPlannedClass: (PlannedClassOccurrence) -> Unit,
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

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (!focusMode) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TabRow(
                    selectedTabIndex = page.coerceIn(0, 2),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = NotCanBlue,
                    modifier = Modifier.weight(1f)
                ) {
                    listOf("Clase", "Calendario", "IA").forEachIndexed { index, label ->
                        Tab(
                            selected = page == index,
                            onClick = {
                                if (page in 0..2) previousPage = page
                                page = index
                            },
                            text = { Text(label, fontWeight = if (page == index) FontWeight.SemiBold else FontWeight.Normal) }
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Opciones", tint = NotCanOffWhite) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Calificaciones") },
                            leadingIcon = { Icon(Icons.Default.Grade, null) },
                            onClick = {
                                previousPage = page.coerceIn(0, 2)
                                page = 3
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Modo concentración") },
                            leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null) },
                            onClick = { page = 0; focusMode = true; menuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Configuración") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = {
                                previousPage = page.coerceIn(0, 2)
                                page = 4
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { focusMode = false }) {
                    Icon(Icons.Default.CenterFocusStrong, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Salir de concentración")
                }
            }
        }

        if (!focusMode && page == 0 && plannedNow != null) {
            PlannedClassBanner(
                occurrence = plannedNow,
                onOpen = { onOpenPlannedClass(plannedNow) },
                onRecord = { onRecordPlannedClass(plannedNow) }
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                focusMode -> classContent()
                page == 0 -> classContent()
                page == 1 -> calendarContent()
                page == 2 -> aiContent()
                page == 3 -> gradesContent()
                else -> settingsContent()
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = NotCanSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = NotCanBlue)
            Column(Modifier.weight(1f)) {
                Text(occurrence.subject.name, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text(
                    "Prevista ${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)} · aún no se ha creado",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onOpen) { Text("Abrir") }
            Button(onClick = onRecord) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Grabar")
            }
        }
    }
}
