package com.verdure.tools

import android.util.Log
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
    companion object {
        private const val TAG = "NotificationTool"
        private const val DEFAULT_CLEAR_AFTER_VIEW = true
    }

    override val name: String = "notification_filter"
    override val description: String = "Analyzes and prioritizes notifications based on importance and urgency"

    override suspend fun execute(params: Map<String, Any>): String {
        val action = params["action"] as? String
        val clearAfterView = params["clear_after_view"] as? Boolean ?: DEFAULT_CLEAR_AFTER_VIEW

        // If action is "get_all", just return formatted notification list (no LLM)
        if (action == "get_all") {
            // Get priority-filtered notifications (heuristic does the heavy lifting)
            // Limit to 8 priority notifications (safe token budget: ~1200 tokens total)
            val notifications = getPriorityNotifications(limit = 8)
            if (notifications.isEmpty()) {
                return "No priority notifications right now."
            }
            val formatted = formatNotificationsForContext(notifications)
            maybeDismissViewedNotifications(notifications, clearAfterView)
            return formatted
        }

        // If action is "search", filter by keywords
        if (action == "search") {
            val keywords = params["keywords"] as? List<*>
            val keywordStrings = keywords?.mapNotNull { it as? String } ?: emptyList()
            val limit = params["limit"] as? Int ?: 10

            val notifications = searchNotifications(keywordStrings, limit)
            if (notifications.isEmpty()) {
                return "No notifications found matching: ${keywordStrings.joinToString(", ")}"
            }
            val formatted = formatNotificationsForContext(notifications)
            maybeDismissViewedNotifications(notifications, clearAfterView)
            return formatted
        }

        // Handle "get_priority" action (for compatibility with VerdureAI)
        if (action == "get_priority") {
            val limit = params["limit"] as? Int ?: 8
            val notifications = getPriorityNotifications(limit)
            if (notifications.isEmpty()) {
                return "No priority notifications right now."
            }
            val formatted = formatNotificationsForContext(notifications)
            maybeDismissViewedNotifications(notifications, clearAfterView)
            return formatted
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

        val response = llmEngine.generateContent(prompt)
        maybeDismissViewedNotifications(notifications, clearAfterView)
        return response
    }

    private fun maybeDismissViewedNotifications(
        notifications: List<NotificationData>,
        clearAfterView: Boolean
    ) {
        if (!clearAfterView || notifications.isEmpty()) {
            return
        }

        val dismissedCount = VerdureNotificationListener.dismissViewedNotifications(notifications)
        if (dismissedCount > 0) {
            Log.d(TAG, "Dismissed $dismissedCount viewed notifications after processing")
        }
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
    private suspend fun getPriorityNotifications(limit: Int = 8): List<NotificationData> {
        val allNotifications = VerdureNotificationListener.notifications.value

        // Load user context to get priority rules (keywords, apps, domains, senders)
        val context = contextManager.loadContext()
        val filter = NotificationFilter(context)

        // Filter using heuristic (fast, no LLM needed)
        val priorityNotifications = filter.filterPriority(allNotifications)

        // Return top N priority notifications (not all notifications)
        return priorityNotifications.take(limit)
    }

    /**
     * Search notifications by keywords (manual filtering, no LLM)
     *
     * Flow:
     * 1. Get ALL notifications from service
     * 2. Filter by keyword matching in app name, title, or text
     * 3. Return matching notifications (up to limit)
     *
     * This enables "find notifications about X" queries
     */
    private fun searchNotifications(keywords: List<String>, limit: Int = 10): List<NotificationData> {
        val allNotifications = VerdureNotificationListener.notifications.value

        if (keywords.isEmpty()) {
            // No keywords? Just return recent notifications
            return allNotifications.take(limit)
        }

        // Filter notifications that contain ANY of the keywords (case-insensitive)
        val matchingNotifications = allNotifications.filter { notif ->
            keywords.any { keyword ->
                val lowerKeyword = keyword.lowercase()
                notif.appName.lowercase().contains(lowerKeyword) ||
                (notif.title?.lowercase()?.contains(lowerKeyword) == true) ||
                (notif.text?.lowercase()?.contains(lowerKeyword) == true)
            }
        }

        return matchingNotifications.take(limit)
    }
}
