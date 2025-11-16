package com.verdure.services

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.verdure.data.CalendarEvent
import java.util.Calendar

/**
 * Utility class to read calendar events from the device.
 */
class CalendarReader(private val context: Context) {

    companion object {
        private const val TAG = "CalendarReader"
    }

    /**
     * Get events for today and tomorrow.
     */
    fun getUpcomingEvents(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()

        try {
            // Define time range: from now until end of tomorrow
            val now = System.currentTimeMillis()
            val endOfTomorrow = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 2)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Query calendar events
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME
            )

            val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?)"
            val selectionArgs = arrayOf(now.toString(), endOfTomorrow.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val event = extractEventFromCursor(it)
                    if (event != null) {
                        events.add(event)
                    }
                }
            }

            Log.d(TAG, "Loaded ${events.size} upcoming events")
        } catch (e: SecurityException) {
            Log.e(TAG, "Calendar permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calendar events", e)
        }

        return events
    }

    /**
     * Extract a CalendarEvent from a cursor row.
     */
    private fun extractEventFromCursor(cursor: Cursor): CalendarEvent? {
        return try {
            val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val descIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val allDayIndex = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val calendarNameIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)

            if (idIndex == -1 || titleIndex == -1 || startIndex == -1) {
                return null
            }

            val id = cursor.getLong(idIndex)
            val title = cursor.getString(titleIndex) ?: "Untitled Event"
            val description = if (descIndex != -1) cursor.getString(descIndex) else null
            val location = if (locationIndex != -1) cursor.getString(locationIndex) else null
            val startTime = cursor.getLong(startIndex)
            val endTime = if (endIndex != -1) cursor.getLong(endIndex) else startTime + 3600000 // Default 1 hour
            val allDay = if (allDayIndex != -1) cursor.getInt(allDayIndex) == 1 else false
            val calendarName = if (calendarNameIndex != -1) cursor.getString(calendarNameIndex) ?: "Calendar" else "Calendar"

            CalendarEvent(
                id = id,
                title = title,
                description = description,
                location = location,
                startTime = startTime,
                endTime = endTime,
                allDay = allDay,
                calendarName = calendarName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting event from cursor", e)
            null
        }
    }
}
