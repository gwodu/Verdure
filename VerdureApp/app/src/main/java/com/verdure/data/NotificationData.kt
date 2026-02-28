package com.verdure.data

import android.app.PendingIntent

/**
 * Data class representing a notification captured from the device.
 */
data class NotificationData(
    val id: Int,
    val systemKey: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val isClearable: Boolean,
    val category: String?,
    val priority: Int,
    val contentIntent: PendingIntent?,  // Intent to open the notification's app

    // Metadata for enhanced scoring
    val hasActions: Boolean = false,      // Has action buttons (reply, archive, etc.)
    val hasImage: Boolean = false,        // Has inline image/rich media
    val isOngoing: Boolean = false        // Ongoing notification (music, timer, etc.)
) {
    /**
     * Get a human-readable timestamp string.
     */
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    }
}
