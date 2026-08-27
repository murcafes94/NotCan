package com.notcan.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
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

    var subjectId by remember(subjects, selectedSubjectId) { mutableStateOf(selectedSubjectId ?: subjects.firstOrNull()?.id) }
    var weekday by remember { mutableIntStateOf(1) }
    var startMinute by remember { mutableIntStateOf(8 * 60 + 15) }
    var endMinute by remember { mutableIntStateOf(9 * 60 + 45) }
    var startTimeOpen by remember { mutableStateOf(false) }
    var endTimeOpen by remember { mutableStateOf(false) }
    var autoStopMode by remember { mutableStateOf(RecordingService.AUTO_STOP_ASK) }
    var graceMinutes by remember { mutableIntStateOf(5) }
    var scheduleError by remember { mutableStateOf<String?>(null) }

    val today = LocalDate.now()
    val todayOccurrences = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules)
    val upcoming = cycle?.let {
        AcademicSchedule.allOccurrences(it, subjects, schedules)
            .filter { occurrence -> !occurrence.date.isBefore(today) }
            .take(24)
    }.orEmpty()

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Calendario académico", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Clases, horario y recordatorios del ciclo. Una sesión prevista solo se convierte en clase cuando la utilizas.", color = NotCanGray)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = NotCanBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(cycle?.name ?: "Selecciona o crea un ciclo", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateButton("Inicio", startDate, { startPickerOpen = true }, Modifier.weight(1f))
                        DateButton("Fin", endDate, { endPickerOpen = true }, Modifier.weight(1f))
                    }
                    dateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(enabled = cycle != null && startDate != null && endDate != null, onClick = {
                        val start = startDate
                        val end = endDate
                        if (start == null || end == null || end.isBefore(start)) dateError = "La fecha final debe ser igual o posterior al inicio."
                        else { onSaveCycleDates(start.toEpochDay(), end.toEpochDay()); dateError = null }
                    }) { Text("Guardar período del semestre") }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Horario de una materia", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subjects, key = { it.id }) { subject ->
                            FilterChip(selected = subjectId == subject.id, onClick = { subjectId = subject.id }, label = { Text(subject.name) })
                        }
                    }
                    Text("Día de la semana", color = NotCanGray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..7).toList()) { day ->
                            FilterChip(selected = weekday == day, onClick = { weekday = day }, label = { Text(AcademicSchedule.weekdayLabel(day).take(3)) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeButton("Empieza", startMinute, { startTimeOpen = true }, Modifier.weight(1f))
                        TimeButton("Termina", endMinute, { endTimeOpen = true }, Modifier.weight(1f))
                    }
                    Text("Al terminar el horario", color = NotCanGray)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item { FilterChip(selected = autoStopMode == RecordingService.AUTO_STOP_ASK, onClick = { autoStopMode = RecordingService.AUTO_STOP_ASK }, label = { Text("Preguntar") }) }
                        item { FilterChip(selected = autoStopMode == RecordingService.AUTO_STOP_AUTO, onClick = { autoStopMode = RecordingService.AUTO_STOP_AUTO }, label = { Text("Detener automático") }) }
                        item { FilterChip(selected = autoStopMode == RecordingService.AUTO_STOP_CONTINUE, onClick = { autoStopMode = RecordingService.AUTO_STOP_CONTINUE }, label = { Text("Continuar") }) }
                    }
                    if (autoStopMode == RecordingService.AUTO_STOP_AUTO) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Margen:", color = NotCanGray)
                            listOf(0, 5, 10, 15).forEach { value ->
                                FilterChip(selected = graceMinutes == value, onClick = { graceMinutes = value }, label = { Text("+$value min") })
                            }
                        }
                    }
                    scheduleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(enabled = cycle != null && subjectId != null, onClick = {
                        if (endMinute <= startMinute) scheduleError = "La hora final debe ser posterior a la inicial."
                        else {
                            onAddSchedule(subjectId!!, weekday, startMinute, endMinute, autoStopMode, graceMinutes)
                            scheduleError = null
                        }
                    }) { Text("Añadir al horario") }
                }
            }
        }

        item { Text("Horario semanal", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (schedules.isEmpty()) item { Text("Todavía no has añadido horarios.", color = NotCanGray) }
        else items(schedules, key = { it.id }) { schedule ->
            val subject = subjects.firstOrNull { it.id == schedule.subjectId }
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(subject?.name ?: "Materia", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                        Text("${AcademicSchedule.weekdayLabel(schedule.weekdayIso)} · ${AcademicSchedule.formatMinutes(schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(schedule.endMinuteOfDay)} · aviso 1 día antes", color = NotCanGray)
                    }
                    IconButton(onClick = { onSyncScheduleToCalendar(schedule.id) }) { Icon(Icons.Default.Sync, "Sincronizar con calendario", tint = NotCanBlue) }
                    IconButton(onClick = { onDeleteSchedule(schedule.id) }) { Icon(Icons.Default.Delete, "Eliminar horario") }
                }
            }
        }

        item { Text("Hoy", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (todayOccurrences.isEmpty()) item { Text("No hay materias programadas para hoy.", color = NotCanGray) }
        else items(todayOccurrences, key = { it.schedule.id + it.date }) { occurrence -> OccurrenceRow(occurrence, onOpenOccurrence, onRecordOccurrence) }

        item { Text("Próximas clases", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        items(upcoming, key = { it.schedule.id + it.date }) { occurrence ->
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = NotCanBlue)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(occurrence.subject.name, color = NotCanOffWhite)
                        Text("${formatDate(occurrence.date)} · ${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}", color = NotCanGray)
                    }
                }
            }
        }
    }

    if (startPickerOpen) DateSelectionDialog(startDate, { startPickerOpen = false }, { startDate = it; startPickerOpen = false; dateError = null })
    if (endPickerOpen) DateSelectionDialog(endDate, { endPickerOpen = false }, { endDate = it; endPickerOpen = false; dateError = null })
    if (startTimeOpen) TimeSelectionDialog(startMinute, { startTimeOpen = false }, { startMinute = it; startTimeOpen = false; scheduleError = null })
    if (endTimeOpen) TimeSelectionDialog(endMinute, { endTimeOpen = false }, { endMinute = it; endTimeOpen = false; scheduleError = null })
}

@Composable
private fun DateButton(label: String, date: LocalDate?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.CalendarMonth, null)
        Spacer(Modifier.width(7.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(date?.let(::formatDate) ?: "Elegir fecha")
        }
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Default.Schedule, null)
        Spacer(Modifier.width(7.dp))
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
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(occurrence.subject.name, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                Text("${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)} · sesión prevista", color = NotCanGray)
            }
            OutlinedButton(onClick = { onOpen(occurrence) }) { Text("Abrir") }
            Button(onClick = { onRecord(occurrence) }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(5.dp)); Text("Grabar") }
        }
    }
}

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
