package com.verdure.services

import android.app.NotificationManager
import android.content.Context
import java.util.Calendar

/**
 * Monitor system state like time of day and Do Not Disturb mode.
 */
class SystemStateMonitor(private val context: Context) {

    /**
     * Time of day categories.
     */
    enum class TimeOfDay {
        EARLY_MORNING,  // 5am-8am
        MORNING,        // 8am-12pm
        AFTERNOON,      // 12pm-5pm
        EVENING,        // 5pm-9pm
        NIGHT,          // 9pm-12am
        LATE_NIGHT      // 12am-5am
    }

    /**
     * Get current time of day category.
     */
    fun getTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..7 -> TimeOfDay.EARLY_MORNING
            in 8..11 -> TimeOfDay.MORNING
            in 12..16 -> TimeOfDay.AFTERNOON
            in 17..20 -> TimeOfDay.EVENING
            in 21..23 -> TimeOfDay.NIGHT
            else -> TimeOfDay.LATE_NIGHT
        }
    }

    /**
     * Check if Do Not Disturb mode is enabled.
     */
    fun isDndEnabled(): Boolean {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = notificationManager.currentInterruptionFilter
            filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get a human-readable description of current time.
     */
    fun getTimeDescription(): String {
        val timeOfDay = getTimeOfDay()
        val dnd = if (isDndEnabled()) " (DND)" else ""

        return when (timeOfDay) {
            TimeOfDay.EARLY_MORNING -> "Early Morning$dnd"
            TimeOfDay.MORNING -> "Morning$dnd"
            TimeOfDay.AFTERNOON -> "Afternoon$dnd"
            TimeOfDay.EVENING -> "Evening$dnd"
            TimeOfDay.NIGHT -> "Night$dnd"
            TimeOfDay.LATE_NIGHT -> "Late Night$dnd"
        }
    }

    /**
     * Get priority adjustment based on time of day.
     * Returns a multiplier (0.5 = lower priority, 1.5 = higher priority).
     */
    fun getTimePriorityMultiplier(): Double {
        val timeOfDay = getTimeOfDay()
        return when (timeOfDay) {
            TimeOfDay.MORNING, TimeOfDay.AFTERNOON -> 1.0  // Normal priority
            TimeOfDay.EARLY_MORNING, TimeOfDay.EVENING -> 0.9
            TimeOfDay.NIGHT -> 0.7  // Lower priority at night
            TimeOfDay.LATE_NIGHT -> 0.5  // Much lower priority late night
        }
    }

    /**
     * Check if we're in "work hours" (8am-5pm weekday).
     */
    fun isWorkHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isDuringWorkHours = hour in 8..16

        return isWeekday && isDuringWorkHours
    }
}
