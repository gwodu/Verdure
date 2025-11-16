package com.verdure.data

import java.util.concurrent.TimeUnit

/**
 * Data class representing a calendar event.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean,
    val calendarName: String
) {
    /**
     * Get a human-readable time range string.
     */
    fun getTimeRange(): String {
        if (allDay) {
            return "All day"
        }

        val startHour = android.text.format.DateFormat.format("h:mm a", startTime).toString()
        val endHour = android.text.format.DateFormat.format("h:mm a", endTime).toString()
        return "$startHour - $endHour"
    }

    /**
     * Get time until event starts in minutes.
     * Negative value means event already started.
     */
    fun getMinutesUntilStart(): Long {
        val now = System.currentTimeMillis()
        val diff = startTime - now
        return TimeUnit.MILLISECONDS.toMinutes(diff)
    }

    /**
     * Check if event is currently happening.
     */
    fun isHappeningNow(): Boolean {
        val now = System.currentTimeMillis()
        return now >= startTime && now <= endTime
    }

    /**
     * Check if event is upcoming (starts within next N minutes).
     */
    fun isUpcoming(minutesThreshold: Long = 60): Boolean {
        val minutesUntil = getMinutesUntilStart()
        return minutesUntil in 0..minutesThreshold
    }

    /**
     * Get urgency label based on time until event.
     */
    fun getUrgencyLabel(): String {
        val minutes = getMinutesUntilStart()
        return when {
            isHappeningNow() -> "NOW"
            minutes < 0 -> "PAST"
            minutes < 15 -> "URGENT (${minutes}m)"
            minutes < 60 -> "SOON (${minutes}m)"
            minutes < 120 -> "1-2 hours"
            else -> {
                val hours = TimeUnit.MINUTES.toHours(minutes)
                "${hours}h away"
            }
        }
    }
}
