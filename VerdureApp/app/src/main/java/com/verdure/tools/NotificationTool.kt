package com.verdure.tools

import com.verdure.core.LLMEngine
import com.verdure.data.NotificationData
import com.verdure.services.VerdureNotificationListener

/**
 * Tool for analyzing and prioritizing notifications
 *
 * Reads notifications from VerdureNotificationListener (Service)
 * Uses LLM to categorize by importance/urgency
 * Returns human-readable summary
 */
class NotificationTool(
    private val llmEngine: LLMEngine
) : Tool {

    override val name: String = "notification_filter"
    override val description: String = "Analyzes and prioritizes notifications based on importance and urgency"

    override suspend fun execute(params: Map<String, Any>): String {
        val userQuery = params["query"] as? String ?: "What's important?"

        // Read notifications from the Service's StateFlow
        val notifications = getRecentNotifications()

        if (notifications.isEmpty()) {
            return "You have no notifications right now."
        }

        // Format notifications for LLM analysis
        val notificationList = notifications.mapIndexed { index, notif ->
            val title = notif.title ?: "(no title)"
            val text = notif.text ?: "(no text)"
            "${index + 1}. ${notif.appName}: $title - $text"
        }.joinToString("\n")

        val prompt = """
You are a notification prioritization assistant. Analyze these notifications and categorize them:

Notifications:
$notificationList

User asked: "$userQuery"

Categorize into:
🔴 URGENT (needs immediate attention)
🟡 IMPORTANT (check soon)
⚪ CAN WAIT (low priority)
🚫 IGNORE (spam/unimportant)

Provide a brief, clear summary.
        """.trimIndent()

        return llmEngine.generateContent(prompt)
    }

    /**
     * Get recent notifications from VerdureNotificationListener
     * This connects the Tool (processor) to the Service (collector)
     */
    private fun getRecentNotifications(limit: Int = 10): List<NotificationData> {
        return VerdureNotificationListener.notifications.value.take(limit)
    }
}
