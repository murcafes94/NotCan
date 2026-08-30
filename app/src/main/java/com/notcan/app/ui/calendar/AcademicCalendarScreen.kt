package com.notcan.app.ui.calendar

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    var startDate by remember(cycle?.id, cycle?.startEpochDay) { mutableStateOf(cycle?.startEpochDay?.takeIf { it > 0 }?.let(LocalDate::ofEpochDay)) }
    var endDate by remember(cycle?.id, cycle?.endEpochDay) { mutableStateOf(cycle?.endEpochDay?.takeIf { it > 0 }?.let(LocalDate::ofEpochDay)) }
    var startPickerOpen by remember { mutableStateOf(false) }
    var endPickerOpen by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    var subjectId by remember(subjects, selectedSubjectId) { mutableStateOf(selectedSubjectId ?: subjects.firstOrNull()?.id) }
    var weekday by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var startMinute by remember { mutableIntStateOf(8 * 60) }
    var endMinute by remember { mutableIntStateOf(9 * 60 + 30) }
    var startTimeOpen by remember { mutableStateOf(false) }
    var endTimeOpen by remember { mutableStateOf(false) }
    var scheduleError by remember { mutableStateOf<String?>(null) }

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
                    Modifier.weight(1.65f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { CalendarHero(cycle?.name, schedules.size, todayOccurrences.size, onAdd = { editorOpen = !editorOpen }) }
                    item { WeeklyScheduleBoard(subjects, schedules, true) }
                    item { TodaySection(todayOccurrences, onOpenOccurrence, onRecordOccurrence) }
                    item { UpcomingSection(upcoming) }
                }
                LazyColumn(
                    Modifier.weight(0.85f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { CycleCard(cycle, startDate, endDate, dateError, { startPickerOpen = true }, { endPickerOpen = true }) { start, end ->
                        if (end.isBefore(start)) dateError = "La fecha final debe ser igual o posterior al inicio."
                        else { onSaveCycleDates(start.toEpochDay(), end.toEpochDay()); dateError = null }
                    } }
                    item { ScheduleEditor(
                        visible = true,
                        subjects = subjects,
                        cycle = cycle,
                        subjectId = subjectId,
                        weekday = weekday,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        error = scheduleError,
                        onSubject = { subjectId = it },
                        onWeekday = { weekday = it },
                        onStart = { startTimeOpen = true },
                        onEnd = { endTimeOpen = true },
                        onSave = {
                            if (endMinute <= startMinute) scheduleError = "La hora final debe ser posterior a la inicial."
                            else if (subjectId != null) {
                                onAddSchedule(subjectId!!, weekday, startMinute, endMinute, RecordingService.AUTO_STOP_ASK, 5)
                                scheduleError = null
                            }
                        }
                    ) }
                    item { SavedSchedules(subjects, schedules, onDeleteSchedule, onSyncScheduleToCalendar) }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { CalendarHero(cycle?.name, schedules.size, todayOccurrences.size, onAdd = { editorOpen = !editorOpen }) }
                item { WeeklyScheduleBoard(subjects, schedules, false) }
                item { TodaySection(todayOccurrences, onOpenOccurrence, onRecordOccurrence) }
                item { UpcomingSection(upcoming) }
                if (editorOpen) item { ScheduleEditor(
                    visible = true,
                    subjects = subjects,
                    cycle = cycle,
                    subjectId = subjectId,
                    weekday = weekday,
                    startMinute = startMinute,
                    endMinute = endMinute,
                    error = scheduleError,
                    onSubject = { subjectId = it },
                    onWeekday = { weekday = it },
                    onStart = { startTimeOpen = true },
                    onEnd = { endTimeOpen = true },
                    onSave = {
                        if (endMinute <= startMinute) scheduleError = "La hora final debe ser posterior a la inicial."
                        else if (subjectId != null) {
                            onAddSchedule(subjectId!!, weekday, startMinute, endMinute, RecordingService.AUTO_STOP_ASK, 5)
                            scheduleError = null
                            editorOpen = false
                        }
                    }
                ) }
                item { SavedSchedules(subjects, schedules, onDeleteSchedule, onSyncScheduleToCalendar) }
                item { CycleCard(cycle, startDate, endDate, dateError, { startPickerOpen = true }, { endPickerOpen = true }) { start, end ->
                    if (end.isBefore(start)) dateError = "La fecha final debe ser igual o posterior al inicio."
                    else { onSaveCycleDates(start.toEpochDay(), end.toEpochDay()); dateError = null }
                } }
            }
        }
    }

    if (startPickerOpen) DateSelectionDialog(startDate, { startPickerOpen = false }, { startDate = it; startPickerOpen = false; dateError = null })
    if (endPickerOpen) DateSelectionDialog(endDate, { endPickerOpen = false }, { endDate = it; endPickerOpen = false; dateError = null })
    if (startTimeOpen) TimeSelectionDialog(startMinute, { startTimeOpen = false }, { startMinute = it; startTimeOpen = false; scheduleError = null })
    if (endTimeOpen) TimeSelectionDialog(endMinute, { endTimeOpen = false }, { endMinute = it; endTimeOpen = false; scheduleError = null })
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
                Text("$todayCount clase(s) hoy · $schedules bloque(s) semanales", color = NotCanGray)
            }
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Horario")
            }
        }
    }
}

@Composable
private fun WeeklyScheduleBoard(subjects: List<SubjectEntity>, schedules: List<SubjectScheduleEntity>, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Horario semanal", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("Vista de clases", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (1..7).forEach { day ->
                    DayColumn(day, subjects, schedules.filter { it.weekdayIso == day }, Modifier.weight(1f))
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                items((1..7).toList()) { day ->
                    DayColumn(day, subjects, schedules.filter { it.weekdayIso == day }, Modifier.width(178.dp))
                }
            }
        }
    }
}

@Composable
private fun DayColumn(day: Int, subjects: List<SubjectEntity>, schedules: List<SubjectScheduleEntity>, modifier: Modifier) {
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
                        }
                    }
                }
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
                        Text("${formatDate(occurrence.date)} · ${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleCard(
    cycle: StudyCycleEntity?,
    startDate: LocalDate?,
    endDate: LocalDate?,
    error: String?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onSave: (LocalDate, LocalDate) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Período académico", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("Inicio", startDate, onStart, Modifier.weight(1f))
                DateButton("Fin", endDate, onEnd, Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = cycle != null && startDate != null && endDate != null, onClick = {
                if (startDate != null && endDate != null) onSave(startDate, endDate)
            }) { Text("Guardar período") }
        }
    }
}

@Composable
private fun ScheduleEditor(
    visible: Boolean,
    subjects: List<SubjectEntity>,
    cycle: StudyCycleEntity?,
    subjectId: String?,
    weekday: Int,
    startMinute: Int,
    endMinute: Int,
    error: String?,
    onSubject: (String) -> Unit,
    onWeekday: (Int) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onSave: () -> Unit
) {
    if (!visible) return
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Añadir bloque de clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeButton("Inicio", startMinute, onStart, Modifier.weight(1f))
                TimeButton("Fin", endMinute, onEnd, Modifier.weight(1f))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = cycle != null && subjectId != null, onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar en horario") }
        }
    }
}

@Composable
private fun SavedSchedules(
    subjects: List<SubjectEntity>,
    schedules: List<SubjectScheduleEntity>,
    onDelete: (String) -> Unit,
    onSync: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bloques guardados", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            if (schedules.isEmpty()) Text("Todavía no hay horarios guardados.", color = NotCanGray)
            schedules.sortedWith(compareBy<SubjectScheduleEntity> { it.weekdayIso }.thenBy { it.startMinuteOfDay }).forEachIndexed { index, schedule ->
                val subject = subjects.firstOrNull { it.id == schedule.subjectId }
                if (index > 0) HorizontalDivider(color = NotCanBorder)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(subject?.name ?: "Materia", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                        Text("${AcademicSchedule.weekdayLabel(schedule.weekdayIso)} · ${AcademicSchedule.formatMinutes(schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(schedule.endMinuteOfDay)}", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onSync(schedule.id) }) { Icon(Icons.Default.Sync, "Sincronizar", tint = NotCanBlue) }
                    IconButton(onClick = { onDelete(schedule.id) }) { Icon(Icons.Default.Delete, "Eliminar") }
                }
            }
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date?.let(::formatDate) ?: "Elegir")
        }
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(AcademicSchedule.formatMinutes(minute))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionDialog(initial: LocalDate?, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val initialMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis -> onSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectionDialog(initialMinute: Int, onDismiss: () -> Unit, onSelected: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir hora") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onSelected(state.hour * 60 + state.minute) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun OccurrenceRow(occurrence: PlannedClassOccurrence, onOpen: (PlannedClassOccurrence) -> Unit, onRecord: (PlannedClassOccurrence) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurfaceHigh), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(occurrence.subject.name, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                Text("${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}", color = NotCanBlue)
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

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
