package com.verdure.tools

import com.verdure.core.LLMEngine
import com.verdure.data.NotificationData
import com.verdure.data.NotificationFilter
import com.verdure.data.UserContextManager
import com.verdure.services.VerdureNotificationListener

/**
 * Tool for analyzing and prioritizing notifications
 *
 * Architecture:
 * 1. Heuristic filter (NotificationFilter) marks notifications as PRIORITY using user context rules
 * 2. LLM analyzes ONLY priority-flagged notifications (10-15 max)
 * 3. Returns intelligent summary based on user's goals/priorities
 *
 * This validates the hybrid system: heuristic does heavy lifting, LLM provides synthesis
 */
class NotificationTool(
    private val llmEngine: LLMEngine,
    private val contextManager: UserContextManager
) : Tool {

    override val name: String = "notification_filter"
    override val description: String = "Analyzes and prioritizes notifications based on importance and urgency"

    override suspend fun execute(params: Map<String, Any>): String {
        val action = params["action"] as? String

        // If action is "get_all", just return formatted notification list (no LLM)
        if (action == "get_all") {
            // Get priority-filtered notifications (heuristic does the heavy lifting)
            // Limit to 15 priority notifications (fits within token budget: ~1600 tokens total)
            val notifications = getPriorityNotifications(limit = 15)
            if (notifications.isEmpty()) {
                return "No priority notifications right now."
            }
            return formatNotificationsForContext(notifications)
        }

        // Otherwise, use LLM to analyze (original behavior)
        val userQuery = params["query"] as? String ?: "What's important?"

        // Get priority-filtered notifications (heuristic pre-filtering)
        val notifications = getPriorityNotifications()

        if (notifications.isEmpty()) {
            return "You have no priority notifications right now."
        }

        // Format notifications for LLM analysis
        val notificationList = formatNotificationsForContext(notifications)

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
     * Format notifications for context/analysis
     */
    private fun formatNotificationsForContext(notifications: List<NotificationData>): String {
        return notifications.mapIndexed { index, notif ->
            // Sanitize text to avoid special characters that might crash LLM
            val title = sanitizeText(notif.title ?: "(no title)")
            val text = sanitizeText(notif.text ?: "(no text)")
            val timeDesc = notif.getFormattedTime()
            "${index + 1}. ${notif.appName}: $title - $text ($timeDesc)"
        }.joinToString("\n")
    }

    /**
     * Sanitize text to remove potentially problematic characters
     */
    private fun sanitizeText(text: String): String {
        return text
            .replace("\u0000", "") // Remove null bytes
            .replace(Regex("[\\p{C}&&[^\\n\\r\\t]]"), "") // Remove other control characters except newlines/tabs
            .take(200) // Limit length per field
    }

    /**
     * Get priority notifications using heuristic filter
     *
     * Flow:
     * 1. Get all notifications from VerdureNotificationListener (Service)
     * 2. Load user context to get priority rules
     * 3. Use NotificationFilter to classify (heuristic matching)
     * 4. Return only PRIORITY-flagged notifications (up to limit)
     *
     * This is the key to the hybrid architecture: heuristic filters, LLM synthesizes
     */
    private fun getPriorityNotifications(limit: Int = 15): List<NotificationData> {
        val allNotifications = VerdureNotificationListener.notifications.value

        // Load user context to get priority rules (keywords, apps, domains, senders)
        val context = contextManager.loadContext()
        val filter = NotificationFilter(context)

        // Filter using heuristic (fast, no LLM needed)
        val priorityNotifications = filter.filterPriority(allNotifications)

        // Return top N priority notifications (not all notifications)
        return priorityNotifications.take(limit)
    }
}
