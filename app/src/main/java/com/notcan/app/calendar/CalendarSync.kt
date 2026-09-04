package com.notcan.app.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

object CalendarSync {
    data class CalendarTarget(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean
    ) {
        val isGoogle: Boolean get() = accountType.equals("com.google", ignoreCase = true)
        val label: String
            get() = when {
                isGoogle && accountName.isNotBlank() -> "Google · $accountName"
                accountName.isNotBlank() && displayName != accountName -> "$displayName · $accountName"
                displayName.isNotBlank() -> displayName
                else -> "Calendario del dispositivo"
            }
    }

    data class SyncResult(val eventId: Long, val calendar: CalendarTarget)

    fun listWritableCalendars(context: Context): List<CalendarTarget> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val result = mutableListOf<CalendarTarget>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val primaryCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                result += CalendarTarget(
                    id = cursor.getLong(idCol),
                    displayName = cursor.getString(nameCol).orEmpty(),
                    accountName = cursor.getString(accountCol).orEmpty(),
                    accountType = cursor.getString(typeCol).orEmpty(),
                    isPrimary = cursor.getInt(primaryCol) == 1
                )
            }
        }
        return result.sortedWith(
            compareByDescending<CalendarTarget> { it.isGoogle }
                .thenByDescending { it.isPrimary }
                .thenBy { it.accountName.lowercase() }
                .thenBy { it.displayName.lowercase() }
        )
    }

    fun preferredTarget(context: Context, preferredId: Long? = null): CalendarTarget? {
        val calendars = listWritableCalendars(context)
        val requested = preferredId?.takeIf { it > 0L }?.let { id -> calendars.firstOrNull { it.id == id } }
        return requested
            ?: calendars.firstOrNull { it.isGoogle && it.isPrimary }
            ?: calendars.firstOrNull { it.isGoogle }
            ?: calendars.firstOrNull { it.isPrimary }
            ?: calendars.firstOrNull()
    }

    fun syncSchedule(
        context: Context,
        cycle: StudyCycleEntity,
        subject: SubjectEntity,
        schedule: SubjectScheduleEntity,
        calendarId: Long? = null
    ): SyncResult? {
        if (cycle.startEpochDay <= 0 || cycle.endEpochDay < cycle.startEpochDay) return null
        val target = preferredTarget(context, calendarId) ?: return null
        val first = AcademicSchedule.allOccurrences(cycle, listOf(subject), listOf(schedule)).firstOrNull() ?: return null
        schedule.calendarEventId?.let { removeEvent(context, it) }

        val zone = ZoneId.systemDefault()
        val until = LocalDate.ofEpochDay(cycle.endEpochDay)
            .plusDays(1)
            .atStartOfDay(zone)
            .minusSeconds(1)
            .withZoneSameInstant(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val durationMinutes = schedule.endMinuteOfDay - schedule.startMinuteOfDay

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, target.id)
            put(CalendarContract.Events.TITLE, subject.name)
            put(CalendarContract.Events.DESCRIPTION, "Horario académico sincronizado por NotCan")
            put(CalendarContract.Events.DTSTART, first.startEpochMs)
            put(CalendarContract.Events.DURATION, "PT${durationMinutes}M")
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;UNTIL=$until")
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(eventUri)
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, schedule.reminderMinutesBefore.coerceAtLeast(0))
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
        return SyncResult(eventId, target)
    }

    fun removeEvent(context: Context, eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null)
    }
}
