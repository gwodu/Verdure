package com.verdure.services

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.verdure.data.NotificationData
import com.verdure.data.NotificationSummaryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NotificationListenerService that captures all notifications posted to the device.
 * Provides access to notifications via a StateFlow for reactive UI updates.
 */
class VerdureNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "VerdureNotifListener"

        // StateFlow to hold the list of notifications
        private val _notifications = MutableStateFlow<List<NotificationData>>(emptyList())
        val notifications: StateFlow<List<NotificationData>> = _notifications.asStateFlow()

        // Track notification count
        private var nextId = 0
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification Listener Connected")

        // Load existing notifications when service connects
        loadExistingNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification Listener Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val notificationData = extractNotificationData(sbn)
        if (notificationData != null) {
            Log.d(TAG, "New notification: ${notificationData.appName} - ${notificationData.title}")

            // Add to the beginning of the list (most recent first)
            val currentList = _notifications.value.toMutableList()
            currentList.add(0, notificationData)
            _notifications.value = currentList
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        
        val packageName = sbn.packageName
        val postTime = sbn.postTime
        
        // Find the notification ID that matches this removal
        var removedNotifId: Int? = null
        
        // Remove from internal state
        val currentList = _notifications.value.toMutableList()
        val iterator = currentList.iterator()
        while (iterator.hasNext()) {
            val notif = iterator.next()
            if (notif.packageName == packageName && notif.timestamp == postTime) {
                removedNotifId = notif.id
                iterator.remove()
            }
        }
        
        if (removedNotifId != null) {
            _notifications.value = currentList
            Log.d(TAG, "Notification removed from state: $packageName (${currentList.size} remaining)")
            
            // Check if removed notification was in widget summary
            checkAndUpdateWidgetSummary(removedNotifId)
        } else {
            Log.d(TAG, "Notification removed (not in state): $packageName")
        }
    }
    
    /**
     * Check if the removed notification was in the widget summary.
     * If so, clear the summary and update the widget to show fresh state.
     */
    private fun checkAndUpdateWidgetSummary(removedNotifId: Int) {
        try {
            val summaryStore = NotificationSummaryStore(applicationContext)
            val summary = summaryStore.getLatestSummary()
            
            if (summary != null && summary.notificationIds.contains(removedNotifId)) {
                Log.d(TAG, "Removed notification was in widget summary - clearing stale summary")
                summaryStore.clearSummaries()
                
                // Trigger widget update to reflect cleared notifications
                com.verdure.widget.VerdureWidgetProvider.updateAllWidgets(applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget after notification removal", e)
        }
    }

    /**
     * Load existing notifications when the service first connects.
     */
    private fun loadExistingNotifications() {
        try {
            val activeNotifications = activeNotifications ?: return
            val notificationList = mutableListOf<NotificationData>()

            for (sbn in activeNotifications) {
                val data = extractNotificationData(sbn)
                if (data != null) {
                    notificationList.add(data)
                }
            }

            // Sort by timestamp, most recent first
            notificationList.sortByDescending { it.timestamp }
            _notifications.value = notificationList

            Log.d(TAG, "Loaded ${notificationList.size} existing notifications")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading existing notifications", e)
        }
    }

    /**
     * Extract relevant data from a StatusBarNotification.
     */
    private fun extractNotificationData(sbn: StatusBarNotification): NotificationData? {
        try {
            val notification = sbn.notification ?: return null
            val extras = notification.extras ?: return null

            // Get app name from package manager
            val appName = try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                sbn.packageName
            }

            // Extract title and text
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            // Skip if both title and text are null/empty
            if (title.isNullOrBlank() && text.isNullOrBlank()) {
                return null
            }

            // Log contentIntent availability for debugging
            val contentIntent = notification.contentIntent
            if (contentIntent == null) {
                Log.d(TAG, "No contentIntent for notification from $appName")
            } else {
                Log.d(TAG, "ContentIntent available for notification from $appName")
            }

            // Extract metadata for enhanced scoring
            val hasActions = notification.actions?.isNotEmpty() == true
            val hasImage = extras.containsKey(Notification.EXTRA_PICTURE) ||
                          notification.getLargeIcon() != null
            val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            
            // Get notification importance (replaces deprecated priority field)
            val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notification.channelId?.let { channelId ->
                    notificationManager?.getNotificationChannel(channelId)?.importance
                } ?: NotificationManager.IMPORTANCE_DEFAULT
            } else {
                @Suppress("DEPRECATION")
                notification.priority
            }

            return NotificationData(
                id = nextId++,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                timestamp = sbn.postTime,
                category = notification.category,
                priority = importance,
                contentIntent = contentIntent,  // Capture the intent to open the app

                // Metadata for enhanced scoring
                hasActions = hasActions,
                hasImage = hasImage,
                isOngoing = isOngoing
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting notification data", e)
            return null
        }
    }
}
