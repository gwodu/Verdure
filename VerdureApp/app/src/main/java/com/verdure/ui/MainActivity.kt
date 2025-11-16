package com.verdure.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.verdure.R
import com.verdure.services.CalendarReader
import com.verdure.services.SystemStateMonitor
import com.verdure.services.VerdureNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var requestPermissionButton: Button
    private lateinit var systemContextText: TextView
    private lateinit var calendarEventsText: TextView
    private lateinit var notificationListText: TextView

    private lateinit var calendarReader: CalendarReader
    private lateinit var systemStateMonitor: SystemStateMonitor

    companion object {
        private const val CALENDAR_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        requestPermissionButton = findViewById(R.id.requestPermissionButton)
        systemContextText = findViewById(R.id.systemContextText)
        calendarEventsText = findViewById(R.id.calendarEventsText)
        notificationListText = findViewById(R.id.notificationListText)

        calendarReader = CalendarReader(applicationContext)
        systemStateMonitor = SystemStateMonitor(applicationContext)

        requestPermissionButton.setOnClickListener {
            requestAllPermissions()
        }

        checkPermissionsAndSetup()
        updateSystemContext()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions and refresh data when returning to the app
        checkPermissionsAndSetup()
        updateSystemContext()
        if (hasCalendarPermission()) {
            loadCalendarEvents()
        }
    }

    /**
     * Check if calendar permission is granted.
     */
    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification listener permission is granted.
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        val componentName = ComponentName(this, VerdureNotificationListener::class.java)
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    /**
     * Request all necessary permissions.
     */
    private fun requestAllPermissions() {
        // Request calendar permission
        if (!hasCalendarPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CALENDAR),
                CALENDAR_PERMISSION_REQUEST
            )
        }

        // Open notification listener settings
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    /**
     * Handle permission request results.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CALENDAR_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCalendarEvents()
            }
            checkPermissionsAndSetup()
        }
    }

    /**
     * Check all permissions and set up observers if granted.
     */
    private fun checkPermissionsAndSetup() {
        val hasCalendar = hasCalendarPermission()
        val hasNotifications = isNotificationListenerEnabled()

        when {
            hasCalendar && hasNotifications -> {
                statusText.text = "✅ All permissions granted"
                requestPermissionButton.isEnabled = false
                requestPermissionButton.text = "Permissions Granted"
                observeNotifications()
                loadCalendarEvents()
            }
            hasCalendar && !hasNotifications -> {
                statusText.text = "⚠️ Notification access required"
                requestPermissionButton.isEnabled = true
                loadCalendarEvents()
            }
            !hasCalendar && hasNotifications -> {
                statusText.text = "⚠️ Calendar access required"
                requestPermissionButton.isEnabled = true
                observeNotifications()
            }
            else -> {
                statusText.text = "⚠️ Permissions required"
                requestPermissionButton.isEnabled = true
            }
        }
    }

    /**
     * Update system context display (time of day, DND status).
     */
    private fun updateSystemContext() {
        val timeDesc = systemStateMonitor.getTimeDescription()
        val isWorkHours = systemStateMonitor.isWorkHours()
        val workHoursText = if (isWorkHours) " | Work Hours" else ""
        systemContextText.text = "🕐 $timeDesc$workHoursText"
    }

    /**
     * Load and display calendar events.
     */
    private fun loadCalendarEvents() {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                calendarReader.getUpcomingEvents()
            }

            if (events.isEmpty()) {
                calendarEventsText.text = "No upcoming events found.\n\nAdd events to your calendar to see them here."
            } else {
                val displayText = events.joinToString("\n\n---\n\n") { event ->
                    buildString {
                        append("${event.getUrgencyLabel()}\n")
                        append("📅 ${event.title}\n")
                        append("⏰ ${event.getTimeRange()}\n")
                        if (!event.location.isNullOrBlank()) {
                            append("📍 ${event.location}\n")
                        }
                        if (!event.description.isNullOrBlank()) {
                            append("📝 ${event.description}\n")
                        }
                        append("📆 ${event.calendarName}")
                    }
                }
                calendarEventsText.text = displayText
            }
        }
    }

    /**
     * Observe the notifications StateFlow and update UI with temporal prioritization.
     */
    private fun observeNotifications() {
        lifecycleScope.launch {
            VerdureNotificationListener.notifications.collect { notifications ->
                if (notifications.isEmpty()) {
                    notificationListText.text = "Waiting for notifications...\n\nOnce you receive notifications on your phone, they will appear here."
                } else {
                    // Apply temporal prioritization
                    val prioritized = withContext(Dispatchers.Default) {
                        prioritizeNotifications(notifications)
                    }

                    // Format notifications for display
                    val displayText = prioritized.joinToString("\n\n---\n\n") { (notif, score) ->
                        buildString {
                            // Show priority indicator
                            val indicator = when {
                                score >= 1.5 -> "🔴 HIGH"
                                score >= 1.0 -> "🟡 MEDIUM"
                                else -> "🟢 LOW"
                            }
                            append("$indicator Priority (score: %.2f)\n".format(score))
                            append("📱 ${notif.appName}\n")
                            append("⏰ ${notif.getFormattedTime()}\n")
                            if (!notif.title.isNullOrBlank()) {
                                append("📌 ${notif.title}\n")
                            }
                            if (!notif.text.isNullOrBlank()) {
                                append("💬 ${notif.text}\n")
                            }
                            append("⚡ Base Priority: ${notif.priority}")
                        }
                    }

                    notificationListText.text = displayText
                }
            }
        }
    }

    /**
     * Prioritize notifications based on temporal context and urgency.
     * Returns list of (notification, priority score) pairs, sorted by priority.
     */
    private fun prioritizeNotifications(notifications: List<com.verdure.data.NotificationData>):
            List<Pair<com.verdure.data.NotificationData, Double>> {

        // Get time-based priority multiplier
        val timeMultiplier = systemStateMonitor.getTimePriorityMultiplier()

        // Calculate priority score for each notification
        val scored = notifications.map { notif ->
            var score = 1.0

            // Base score from Android priority (-2 to 2, normalize to 0.5 to 1.5)
            score += (notif.priority * 0.25)

            // Recency boost (newer = higher priority)
            val ageMinutes = (System.currentTimeMillis() - notif.timestamp) / 60000
            val recencyBoost = when {
                ageMinutes < 5 -> 0.5
                ageMinutes < 15 -> 0.3
                ageMinutes < 60 -> 0.1
                else -> 0.0
            }
            score += recencyBoost

            // Apply time of day multiplier
            score *= timeMultiplier

            // DND mode reduces all priorities except high-priority
            if (systemStateMonitor.isDndEnabled() && notif.priority < 1) {
                score *= 0.5
            }

            Pair(notif, score)
        }

        // Sort by priority score (highest first)
        return scored.sortedByDescending { it.second }
    }
}
