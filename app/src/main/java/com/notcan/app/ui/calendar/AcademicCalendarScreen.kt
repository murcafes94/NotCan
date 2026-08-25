package com.notcan.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val formatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    var startText by remember(cycle?.id, cycle?.startEpochDay) {
        mutableStateOf(cycle?.startEpochDay?.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(it).format(formatter) }.orEmpty())
    }
    var endText by remember(cycle?.id, cycle?.endEpochDay) {
        mutableStateOf(cycle?.endEpochDay?.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(it).format(formatter) }.orEmpty())
    }
    var dateError by remember { mutableStateOf<String?>(null) }

    var subjectId by remember(subjects, selectedSubjectId) {
        mutableStateOf(selectedSubjectId ?: subjects.firstOrNull()?.id)
    }
    var weekday by remember { mutableIntStateOf(1) }
    var startTime by remember { mutableStateOf("08:15") }
    var endTime by remember { mutableStateOf("09:45") }
    var autoStopMode by remember { mutableStateOf(RecordingService.AUTO_STOP_ASK) }
    var graceText by remember { mutableStateOf("5") }
    var scheduleError by remember { mutableStateOf<String?>(null) }

    val today = LocalDate.now()
    val todayOccurrences = AcademicSchedule.occurrencesForDate(today, cycle, subjects, schedules)
    val upcoming = cycle?.let {
        AcademicSchedule.allOccurrences(it, subjects, schedules)
            .filter { occurrence -> !occurrence.date.isBefore(today) }
            .take(20)
    }.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Calendario académico", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "El horario genera sesiones previstas. Si no grabas, escribes, importas o abres esa sesión, no se crea ninguna clase vacía.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NotCanBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(cycle?.name ?: "Selecciona o crea un ciclo", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startText,
                            onValueChange = { startText = it; dateError = null },
                            label = { Text("Inicio · AAAA-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endText,
                            onValueChange = { endText = it; dateError = null },
                            label = { Text("Fin · AAAA-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    dateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = cycle != null,
                        onClick = {
                            try {
                                val start = LocalDate.parse(startText, formatter)
                                val end = LocalDate.parse(endText, formatter)
                                require(!end.isBefore(start)) { "La fecha final debe ser posterior al inicio" }
                                onSaveCycleDates(start.toEpochDay(), end.toEpochDay())
                                dateError = null
                            } catch (t: Throwable) {
                                dateError = t.message ?: "Revisa las fechas"
                            }
                        }
                    ) { Text("Guardar período del semestre") }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Añadir horario", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Materia", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(subjects, key = { it.id }) { subject ->
                            FilterChip(
                                selected = subjectId == subject.id,
                                onClick = { subjectId = subject.id },
                                label = { Text(subject.name) }
                            )
                        }
                    }
                    Text("Día", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..7).toList()) { day ->
                            FilterChip(
                                selected = weekday == day,
                                onClick = { weekday = day },
                                label = { Text(AcademicSchedule.weekdayLabel(day).take(3)) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it; scheduleError = null },
                            label = { Text("Inicio · HH:mm") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it; scheduleError = null },
                            label = { Text("Fin · HH:mm") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text("Al terminar el horario", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item {
                            FilterChip(
                                selected = autoStopMode == RecordingService.AUTO_STOP_ASK,
                                onClick = { autoStopMode = RecordingService.AUTO_STOP_ASK },
                                label = { Text("Preguntar") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = autoStopMode == RecordingService.AUTO_STOP_AUTO,
                                onClick = { autoStopMode = RecordingService.AUTO_STOP_AUTO },
                                label = { Text("Detener automático") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = autoStopMode == RecordingService.AUTO_STOP_CONTINUE,
                                onClick = { autoStopMode = RecordingService.AUTO_STOP_CONTINUE },
                                label = { Text("Continuar") }
                            )
                        }
                    }
                    if (autoStopMode == RecordingService.AUTO_STOP_AUTO) {
                        OutlinedTextField(
                            value = graceText,
                            onValueChange = { graceText = it.filter(Char::isDigit).take(2) },
                            label = { Text("Margen después de clase · minutos") },
                            singleLine = true
                        )
                    }
                    scheduleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = cycle != null && subjectId != null,
                        onClick = {
                            try {
                                val start = parseTime(startTime)
                                val end = parseTime(endTime)
                                require(end > start) { "La hora final debe ser posterior" }
                                onAddSchedule(
                                    subjectId!!,
                                    weekday,
                                    start,
                                    end,
                                    autoStopMode,
                                    graceText.toIntOrNull()?.coerceIn(0, 60) ?: 5
                                )
                                scheduleError = null
                            } catch (t: Throwable) {
                                scheduleError = t.message ?: "Revisa el horario"
                            }
                        }
                    ) { Text("Añadir al horario") }
                }
            }
        }

        item {
            Text("Horario semanal", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (schedules.isEmpty()) {
            item { Text("Todavía no has añadido horarios.", color = NotCanGray) }
        } else {
            items(schedules, key = { it.id }) { schedule ->
                val subject = subjects.firstOrNull { it.id == schedule.subjectId }
                Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(subject?.name ?: "Materia", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                            Text(
                                "${AcademicSchedule.weekdayLabel(schedule.weekdayIso)} · ${AcademicSchedule.formatMinutes(schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(schedule.endMinuteOfDay)} · aviso 1 día antes",
                                color = NotCanGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { onSyncScheduleToCalendar(schedule.id) }) {
                            Icon(Icons.Default.Sync, contentDescription = "Sincronizar con calendario", tint = NotCanBlue)
                        }
                        IconButton(onClick = { onDeleteSchedule(schedule.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar horario")
                        }
                    }
                }
            }
        }

        item {
            Text("Clases previstas hoy", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (todayOccurrences.isEmpty()) {
            item { Text("No hay materias programadas para hoy.", color = NotCanGray) }
        } else {
            items(todayOccurrences, key = { it.schedule.id + it.date }) { occurrence ->
                OccurrenceRow(occurrence, onOpenOccurrence, onRecordOccurrence)
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Próximas clases", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        items(upcoming, key = { it.schedule.id + it.date }) { occurrence ->
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(occurrence.subject.name, color = NotCanOffWhite)
                        Text(
                            "${occurrence.date} · ${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)}",
                            color = NotCanGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OccurrenceRow(
    occurrence: PlannedClassOccurrence,
    onOpen: (PlannedClassOccurrence) -> Unit,
    onRecord: (PlannedClassOccurrence) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(occurrence.subject.name, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                Text(
                    "${AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(occurrence.schedule.endMinuteOfDay)} · sesión prevista",
                    color = NotCanGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = { onOpen(occurrence) }) { Text("Abrir") }
            Button(onClick = { onRecord(occurrence) }) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Grabar")
            }
        }
    }
}

private fun parseTime(value: String): Int {
    val parts = value.trim().split(':')
    require(parts.size == 2) { "Usa el formato HH:mm" }
    val hour = parts[0].toIntOrNull() ?: error("Hora inválida")
    val minute = parts[1].toIntOrNull() ?: error("Minuto inválido")
    require(hour in 0..23 && minute in 0..59) { "Hora inválida" }
    return hour * 60 + minute
}
