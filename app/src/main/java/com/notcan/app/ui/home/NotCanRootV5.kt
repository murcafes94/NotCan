package com.notcan.app.ui.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private data class RootDestination(
    val label: String,
    val icon: ImageVector
)

private val rootDestinations = listOf(
    RootDestination("Clase", Icons.Default.Mic),
    RootDestination("Calendario", Icons.Default.Schedule),
    RootDestination("IA", Icons.Default.CenterFocusStrong),
    RootDestination("Calificaciones", Icons.Default.Grade),
    RootDestination("Ajustes", Icons.Default.Settings)
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
    classContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit,
    gradesContent: @Composable () -> Unit = {},
    settingsContent: @Composable () -> Unit = {}
) {
    var page by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var focusMode by remember { mutableStateOf(false) }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val tabletLayout = maxWidth >= 720.dp

            if (tabletLayout && !focusMode) {
                Row(Modifier.fillMaxSize()) {
                    NotCanNavigationRail(
                        selectedIndex = page,
                        onSelected = { page = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                    )
                    RootContent(
                        modifier = Modifier.weight(1f),
                        page = page,
                        plannedNow = plannedNow,
                        onOpenPlannedClass = onOpenPlannedClass,
                        onRecordPlannedClass = onRecordPlannedClass,
                        classContent = classContent,
                        calendarContent = calendarContent,
                        aiContent = aiContent,
                        gradesContent = gradesContent,
                        settingsContent = settingsContent
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    if (focusMode) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { focusMode = false }) {
                                Icon(Icons.Default.CenterFocusStrong, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Salir de concentración")
                            }
                        }
                    }

                    Box(Modifier.weight(1f)) {
                        RootContent(
                            modifier = Modifier.fillMaxSize(),
                            page = if (focusMode) 0 else page,
                            plannedNow = if (focusMode) null else plannedNow,
                            onOpenPlannedClass = onOpenPlannedClass,
                            onRecordPlannedClass = onRecordPlannedClass,
                            classContent = classContent,
                            calendarContent = calendarContent,
                            aiContent = aiContent,
                            gradesContent = gradesContent,
                            settingsContent = settingsContent
                        )
                    }

                    if (!focusMode) {
                        NotCanBottomNavigation(
                            selectedIndex = page,
                            onSelected = { page = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotCanNavigationRail(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    NavigationRail(
        modifier = Modifier.width(112.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "✦ NotCan",
                color = NotCanBlue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            rootDestinations.forEachIndexed { index, destination ->
                NavigationRailItem(
                    selected = selectedIndex == index,
                    onClick = { onSelected(index) },
                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                    label = { Text(destination.label) }
                )
            }
        }
    }
}

@Composable
private fun NotCanBottomNavigation(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        rootDestinations.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
        }
    }
}

@Composable
private fun RootContent(
    modifier: Modifier,
    page: Int,
    plannedNow: PlannedClassOccurrence?,
    onOpenPlannedClass: (PlannedClassOccurrence) -> Unit,
    onRecordPlannedClass: (PlannedClassOccurrence) -> Unit,
    classContent: @Composable () -> Unit,
    calendarContent: @Composable () -> Unit,
    aiContent: @Composable () -> Unit,
    gradesContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    Column(modifier) {
        if (page == 0 && plannedNow != null) {
            PlannedClassBanner(
                occurrence = plannedNow,
                onOpen = { onOpenPlannedClass(plannedNow) },
                onRecord = { onRecordPlannedClass(plannedNow) }
            )
        }

        Box(Modifier.weight(1f)) {
            when (page) {
                0 -> classContent()
                1 -> calendarContent()
                2 -> aiContent()
                3 -> gradesContent()
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = NotCanSurface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = NotCanBlue,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    occurrence.subject.name,
                    color = NotCanOffWhite,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Próxima clase · ${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}",
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
