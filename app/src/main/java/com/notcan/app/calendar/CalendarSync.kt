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
    fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            if (cursor.moveToFirst()) return cursor.getLong(idIndex)
        }
        return null
    }

    fun syncSchedule(
        context: Context,
        cycle: StudyCycleEntity,
        subject: SubjectEntity,
        schedule: SubjectScheduleEntity,
        calendarId: Long = findWritableCalendarId(context) ?: return null
    ): Long? {
        if (cycle.startEpochDay <= 0 || cycle.endEpochDay < cycle.startEpochDay) return null
        val first = AcademicSchedule.allOccurrences(cycle, listOf(subject), listOf(schedule)).firstOrNull() ?: return null
        schedule.calendarEventId?.let { removeEvent(context, it) }

        val zone = ZoneId.systemDefault()
        val until = LocalDate.ofEpochDay(cycle.endEpochDay)
            .plusDays(1)
            .atStartOfDay(zone)
            .minusSeconds(1)
            .withZoneSameInstant(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, subject.name)
            put(CalendarContract.Events.DESCRIPTION, "Horario académico sincronizado por NotCan")
            put(CalendarContract.Events.DTSTART, first.startEpochMs)
            put(CalendarContract.Events.DTEND, first.endEpochMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;UNTIL=$until")
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(eventUri)
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, schedule.reminderMinutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
        return eventId
    }

    fun removeEvent(context: Context, eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null)
    }
}
