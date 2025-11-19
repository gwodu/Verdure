package com.verdure.data

import android.util.Log

/**
 * Fast heuristic-based notification classifier
 * Uses user context rules to determine if a notification is priority
 */
class NotificationFilter(private val userContext: UserContext) {

    /**
     * Classify a notification as PRIORITY or NOT_PRIORITY
     * Uses keyword, app, domain, and sender matching from user context
     */
    fun isPriority(notification: NotificationData): Boolean {
        val rules = userContext.priorityRules

        // Check app name match
        val appMatch = rules.apps.any { app ->
            notification.appName.contains(app, ignoreCase = true)
        }

        // Check keyword match in title or text
        val keywordMatch = rules.keywords.any { keyword ->
            notification.title.contains(keyword, ignoreCase = true) ||
                    notification.text.contains(keyword, ignoreCase = true)
        }

        // Check domain match (for email notifications)
        val domainMatch = rules.domains.any { domain ->
            notification.title.contains(domain, ignoreCase = true) ||
                    notification.text.contains(domain, ignoreCase = true)
        }

        // Check sender match
        val senderMatch = rules.senders.any { sender ->
            notification.title.contains(sender, ignoreCase = true)
        }

        val isPriority = appMatch || keywordMatch || domainMatch || senderMatch

        if (isPriority) {
            Log.d(TAG, "Priority: ${notification.appName} - ${notification.title} " +
                    "(app=$appMatch, keyword=$keywordMatch, domain=$domainMatch, sender=$senderMatch)")
        }

        return isPriority
    }

    /**
     * Filter a list of notifications, returning only priority ones
     */
    fun filterPriority(notifications: List<NotificationData>): List<NotificationData> {
        return notifications.filter { isPriority(it) }
    }

    /**
     * Classify notifications into priority and non-priority groups
     */
    fun classify(notifications: List<NotificationData>): ClassificationResult {
        val priority = mutableListOf<NotificationData>()
        val nonPriority = mutableListOf<NotificationData>()

        notifications.forEach { notification ->
            if (isPriority(notification)) {
                priority.add(notification)
            } else {
                nonPriority.add(notification)
            }
        }

        return ClassificationResult(priority, nonPriority)
    }

    data class ClassificationResult(
        val priority: List<NotificationData>,
        val nonPriority: List<NotificationData>
    )

    companion object {
        private const val TAG = "NotificationFilter"
    }
}
