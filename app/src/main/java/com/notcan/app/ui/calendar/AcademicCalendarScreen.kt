package com.notcan.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.notcan.app.recording.RecordingService
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanBorder
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import com.notcan.app.ui.theme.NotCanSurfaceHigh
import com.notcan.app.ui.theme.NotCanSurfaceSoft
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AcademicCalendarScreen(
    cycle: StudyCycleEntity?,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    selectedSubjectId: String?,
    onSaveCycleDates: (Long, Long) -> Unit,
    onAddSchedule: (subjectId: String, weekdayIso: Int, startMinute: Int, endMinute: Int, autoStopMode: String, graceMinutes: Int) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    onSyncScheduleToCalendar: (String) -> Unit,
    onOpenOccurrence: (PlannedClassOccurrence) -> Unit,
    onRecordOccurrence: (PlannedClassOccurrence) -> Unit
) {
    // El período académico se administra en Configuración. Se conserva el callback
    // para compatibilidad con la navegación actual mientras se completa la migración.
    @Suppress("UNUSED_VARIABLE")
    val periodCallback = onSaveCycleDates

    var editorOpen by remember { mutableStateOf(false) }
    var subjectId by remember(subjects, selectedSubjectId) { mutableStateOf(selectedSubjectId ?: subjects.firstOrNull()?.id) }
    var weekday by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value.coerceIn(1, 7)) }
    var startMinute by remember { mutableIntStateOf(AcademicSchedule.institutionalTimeSlots.first().startMinuteOfDay) }
    var scheduleError by remember { mutableStateOf<String?>(null) }

    val endMinute = AcademicSchedule.calculatedEndMinute(startMinute)
    val today = LocalDate.now()
    val todayOccurrences = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules)
    val upcoming = cycle?.let {
        AcademicSchedule.allOccurrences(it, subjects, schedules)
            .filter { occurrence -> !occurrence.date.isBefore(today) }
            .take(12)
    }.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    Modifier.weight(if (editorOpen) 1.55f else 1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { CalendarHero(cycle?.name, schedules.size, todayOccurrences.size, onAdd = { editorOpen = !editorOpen }) }
                    item { WeeklyScheduleBoard(subjects, schedules, true, onDeleteSchedule, onSyncScheduleToCalendar) }
                    item { TodaySection(todayOccurrences, onOpenOccurrence, onRecordOccurrence) }
                    item { UpcomingSection(upcoming) }
                }
                if (editorOpen) {
                    Column(Modifier.weight(0.75f).fillMaxHeight()) {
                        ScheduleEditor(
                            subjects = subjects,
                            cycle = cycle,
                            subjectId = subjectId,
                            weekday = weekday,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            error = scheduleError,
                            onSubject = { subjectId = it },
                            onWeekday = { weekday = it },
                            onStart = { startMinute = it; scheduleError = null },
                            onSave = {
                                if (subjectId == null) scheduleError = "Selecciona una materia."
                                else {
                                    onAddSchedule(subjectId!!, weekday, startMinute, endMinute, RecordingService.AUTO_STOP_ASK, 5)
                                    scheduleError = null
                                }
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { CalendarHero(cycle?.name, schedules.size, todayOccurrences.size, onAdd = { editorOpen = !editorOpen }) }
                item { WeeklyScheduleBoard(subjects, schedules, false, onDeleteSchedule, onSyncScheduleToCalendar) }
                item { TodaySection(todayOccurrences, onOpenOccurrence, onRecordOccurrence) }
                item { UpcomingSection(upcoming) }
                if (editorOpen) {
                    item {
                        ScheduleEditor(
                            subjects = subjects,
                            cycle = cycle,
                            subjectId = subjectId,
                            weekday = weekday,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            error = scheduleError,
                            onSubject = { subjectId = it },
                            onWeekday = { weekday = it },
                            onStart = { startMinute = it; scheduleError = null },
                            onSave = {
                                if (subjectId == null) scheduleError = "Selecciona una materia."
                                else {
                                    onAddSchedule(subjectId!!, weekday, startMinute, endMinute, RecordingService.AUTO_STOP_ASK, 5)
                                    scheduleError = null
                                    editorOpen = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHero(cycleName: String?, schedules: Int, todayCount: Int, onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh), shape = RoundedCornerShape(22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = NotCanBlue.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.CalendarMonth, null, tint = NotCanBlue, modifier = Modifier.padding(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(cycleName ?: "Calendario académico", color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge)
                Text("$todayCount clase(s) hoy · $schedules clase(s) semanales", color = NotCanGray)
            }
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Añadir clase")
            }
        }
    }
}

@Composable
private fun WeeklyScheduleBoard(
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    wide: Boolean,
    onDelete: (String) -> Unit,
    onSync: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Horario semanal", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("45 min por clase", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (1..7).forEach { day ->
                    DayColumn(day, subjects, schedules.filter { it.weekdayIso == day }, Modifier.weight(1f), onDelete, onSync)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items((1..7).toList()) { day ->
                    DayColumn(day, subjects, schedules.filter { it.weekdayIso == day }, Modifier.width(190.dp), onDelete, onSync)
                }
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: Int,
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    modifier: Modifier,
    onDelete: (String) -> Unit,
    onSync: (String) -> Unit
) {
    val today = LocalDate.now().dayOfWeek.value == day
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = if (today) NotCanBlue.copy(alpha = 0.10f) else NotCanSurfaceSoft),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (today) NotCanBlue.copy(alpha = 0.45f) else NotCanBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(AcademicSchedule.weekdayLabel(day), color = if (today) NotCanBlue else NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            if (schedules.isEmpty()) {
                Text("Libre", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(36.dp))
            } else {
                schedules.sortedBy { it.startMinuteOfDay }.forEach { schedule ->
                    val subject = subjects.firstOrNull { it.id == schedule.subjectId }
                    Surface(color = NotCanSurfaceHigh, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(9.dp)) {
                            Text(subject?.name ?: "Materia", color = NotCanOffWhite, fontWeight = FontWeight.Medium, maxLines = 2)
                            Text(
                                "${AcademicSchedule.formatMinutes(schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(schedule.endMinuteOfDay)}",
                                color = NotCanBlue,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                IconButton(onClick = { onSync(schedule.id) }) {
                                    Icon(Icons.Default.Sync, "Sincronizar con calendario", tint = NotCanBlue)
                                }
                                IconButton(onClick = { onDelete(schedule.id) }) {
                                    Icon(Icons.Default.DeleteOutline, "Eliminar clase del horario", tint = NotCanGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditor(
    subjects: List<SubjectEntity>,
    cycle: StudyCycleEntity?,
    subjectId: String?,
    weekday: Int,
    startMinute: Int,
    endMinute: Int,
    error: String?,
    onSubject: (String) -> Unit,
    onWeekday: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onSave: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Añadir clase al horario", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Text(
                "Elige la hora de inicio. NotCan calcula automáticamente los 45 minutos y respeta el receso de 10:40 a 10:55.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(subjects, key = { it.id }) { subject ->
                    FilterChip(selected = subjectId == subject.id, onClick = { onSubject(subject.id) }, label = { Text(subject.name) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..7).toList()) { day ->
                    FilterChip(selected = weekday == day, onClick = { onWeekday(day) }, label = { Text(AcademicSchedule.weekdayLabel(day).take(3)) })
                }
            }
            Text("Hora", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(AcademicSchedule.institutionalTimeSlots, key = { it.startMinuteOfDay }) { slot ->
                    FilterChip(
                        selected = startMinute == slot.startMinuteOfDay,
                        onClick = { onStart(slot.startMinuteOfDay) },
                        label = { Text(slot.label) }
                    )
                }
            }
            Surface(color = NotCanBlue.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Clase: ${AcademicSchedule.formatMinutes(startMinute)}–${AcademicSchedule.formatMinutes(endMinute)}",
                    color = NotCanBlue,
                    modifier = Modifier.fillMaxWidth().padding(11.dp),
                    fontWeight = FontWeight.Medium
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = cycle != null && subjectId != null, onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar en horario")
            }
        }
    }
}

@Composable
private fun TodaySection(
    occurrences: List<PlannedClassOccurrence>,
    onOpen: (PlannedClassOccurrence) -> Unit,
    onRecord: (PlannedClassOccurrence) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hoy", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium)
        if (occurrences.isEmpty()) Text("No hay clases programadas para hoy.", color = NotCanGray)
        occurrences.forEach { occurrence -> OccurrenceRow(occurrence, onOpen, onRecord) }
    }
}

@Composable
private fun UpcomingSection(upcoming: List<PlannedClassOccurrence>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Próximas clases", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium)
        if (upcoming.isEmpty()) Text("No hay próximas clases dentro del ciclo.", color = NotCanGray)
        upcoming.take(6).forEach { occurrence ->
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurfaceSoft), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = NotCanBlue)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(occurrence.subject.name, color = NotCanOffWhite)
                        Text(
                            "${formatDate(occurrence.date)} · ${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}",
                            color = NotCanGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OccurrenceRow(
    occurrence: PlannedClassOccurrence,
    onOpen: (PlannedClassOccurrence) -> Unit,
    onRecord: (PlannedClassOccurrence) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(occurrence.subject.name, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                Text(
                    "${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}",
                    color = NotCanBlue
                )
            }
            OutlinedButton(onClick = { onOpen(occurrence) }) { Text("Abrir") }
            Button(onClick = { onRecord(occurrence) }) {
                Icon(Icons.Default.Mic, null)
                Spacer(Modifier.width(5.dp))
                Text("Grabar")
            }
        }
    }
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
