package com.notcan.app.calendar

import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class PlannedClassOccurrence(
    val schedule: SubjectScheduleEntity,
    val subject: SubjectEntity,
    val date: LocalDate,
    val startEpochMs: Long,
    val endEpochMs: Long
) {
    fun isPreviewVisible(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val previewStart = startEpochMs - schedule.previewMinutesBefore * 60_000L
        return nowEpochMs in previewStart..endEpochMs
    }
}

object AcademicSchedule {
    fun occurrencesForDate(
        date: LocalDate,
        cycle: StudyCycleEntity?,
        subjects: List<SubjectEntity>,
        schedules: List<SubjectScheduleEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PlannedClassOccurrence> {
        if (cycle == null) return emptyList()
        if (cycle.startEpochDay > 0 && date.toEpochDay() < cycle.startEpochDay) return emptyList()
        if (cycle.endEpochDay > 0 && date.toEpochDay() > cycle.endEpochDay) return emptyList()
        val subjectMap = subjects.associateBy { it.id }
        val weekday = date.dayOfWeek.value
        return schedules.asSequence()
            .filter { it.weekdayIso == weekday }
            .mapNotNull { schedule ->
                val subject = subjectMap[schedule.subjectId] ?: return@mapNotNull null
                occurrence(date, schedule, subject, zone)
            }
            .sortedBy { it.startEpochMs }
            .toList()
    }

    fun allOccurrences(
        cycle: StudyCycleEntity,
        subjects: List<SubjectEntity>,
        schedules: List<SubjectScheduleEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PlannedClassOccurrence> {
        if (cycle.startEpochDay <= 0 || cycle.endEpochDay < cycle.startEpochDay) return emptyList()
        val start = LocalDate.ofEpochDay(cycle.startEpochDay)
        val end = LocalDate.ofEpochDay(cycle.endEpochDay)
        val result = mutableListOf<PlannedClassOccurrence>()
        var date = start
        while (!date.isAfter(end)) {
            result += occurrencesForDate(date, cycle, subjects, schedules, zone)
            date = date.plusDays(1)
        }
        return result
    }

    fun nextOccurrence(
        nowEpochMs: Long,
        cycle: StudyCycleEntity?,
        subjects: List<SubjectEntity>,
        schedules: List<SubjectScheduleEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
        horizonDays: Long = 8
    ): PlannedClassOccurrence? {
        if (cycle == null) return null
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        var date = today
        repeat(horizonDays.toInt() + 1) {
            occurrencesForDate(date, cycle, subjects, schedules, zone)
                .firstOrNull { it.endEpochMs >= nowEpochMs }
                ?.let { return it }
            date = date.plusDays(1)
        }
        return null
    }

    private fun occurrence(
        date: LocalDate,
        schedule: SubjectScheduleEntity,
        subject: SubjectEntity,
        zone: ZoneId
    ): PlannedClassOccurrence {
        val start = date.atStartOfDay(zone).plusMinutes(schedule.startMinuteOfDay.toLong())
        val end = date.atStartOfDay(zone).plusMinutes(schedule.endMinuteOfDay.toLong())
        return PlannedClassOccurrence(
            schedule = schedule,
            subject = subject,
            date = date,
            startEpochMs = start.toInstant().toEpochMilli(),
            endEpochMs = end.toInstant().toEpochMilli()
        )
    }

    fun minuteOfDay(hour: Int, minute: Int): Int = hour * 60 + minute
    fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    fun weekdayLabel(iso: Int): String = when (DayOfWeek.of(iso)) {
        DayOfWeek.MONDAY -> "Lunes"
        DayOfWeek.TUESDAY -> "Martes"
        DayOfWeek.WEDNESDAY -> "Miércoles"
        DayOfWeek.THURSDAY -> "Jueves"
        DayOfWeek.FRIDAY -> "Viernes"
        DayOfWeek.SATURDAY -> "Sábado"
        DayOfWeek.SUNDAY -> "Domingo"
    }
}
