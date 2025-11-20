package com.verdure.data

import android.util.Log

/**
 * Scoring-based notification classifier
 *
 * Architecture: Multi-factor scoring system
 * 1. App-based base score (email/finance = high, social = neutral/low)
 * 2. User-specified keywords from context (learned via Mode A)
 * 3. General high-priority keywords (urgent, due, tomorrow, etc.)
 * 4. Temporal keywords (due, deadline, today, tomorrow, soon)
 * 5. Personal reference detection ("you", sender in contacts)
 * 6. Calendar event temporal scoring (next 30 min = very urgent, 24h = urgent, 7d = normal)
 *
 * Returns notifications sorted by score (highest first)
 */
class NotificationFilter(private val userContext: UserContext) {

    companion object {
        private const val TAG = "NotificationFilter"

        // Scoring weights
        private const val SCORE_HIGH_PRIORITY_APP = 2
        private const val SCORE_FINANCIAL_APP = 2
        private const val SCORE_NEUTRAL_APP = 0
        private const val SCORE_LOW_PRIORITY_APP = -1

        private const val SCORE_USER_KEYWORD = 2
        private const val SCORE_DOMAIN = 2
        private const val SCORE_SENDER = 2

        // General high-priority keywords
        private const val SCORE_URGENT_KEYWORD = 3
        private const val SCORE_TEMPORAL_KEYWORD = 2
        private const val SCORE_PERSONAL_REFERENCE = 1

        // Calendar temporal scoring
        private const val SCORE_CALENDAR_NEXT_30MIN = 5
        private const val SCORE_CALENDAR_NEXT_24H = 3
        private const val SCORE_CALENDAR_NEXT_7D = 1

        // General high-priority keywords (hardcoded)
        private val URGENT_KEYWORDS = listOf("urgent", "critical", "asap", "emergency")
        private val TEMPORAL_KEYWORDS = listOf("due", "deadline", "today", "tonight", "tomorrow", "expiring", "expires")
        private val PERSONAL_KEYWORDS = listOf(" you ", " your ", "you're", "you've")

        // Threshold for what counts as "priority"
        private const val PRIORITY_THRESHOLD = 2
    }

    /**
     * Score a single notification
     * Returns the numerical score (higher = more important)
     * Score is capped at 12 to prevent over-prioritization
     */
    fun scoreNotification(notification: NotificationData): Int {
        var score = 0
        val rules = userContext.priorityRules

        // 1. App-based scoring
        score += getAppScore(notification.appName, rules)

        // 2. User-specified keywords (from context)
        score += getUserKeywordScore(notification, rules)

        // 3. Domain matching (e.g., .edu, .gov)
        score += getDomainScore(notification, rules)

        // 4. Sender matching
        score += getSenderScore(notification, rules)

        // 5. General high-priority keywords (urgent, critical, etc.)
        score += getUrgentKeywordScore(notification)

        // 6. Temporal keywords (due, deadline, today, tomorrow)
        score += getTemporalKeywordScore(notification)

        // 7. Personal reference ("you", "your")
        score += getPersonalReferenceScore(notification)

        // 8. Calendar event temporal scoring (if this is a calendar notification)
        score += getCalendarTemporalScore(notification)

        // Cap score at 12 (roughly 2x typical high-priority score)
        // Prevents over-prioritization when many factors match
        val cappedScore = score.coerceAtMost(12)

        Log.d(TAG, "Score ${cappedScore}${if (score > 12) " (capped from $score)" else ""}: ${notification.appName} - ${notification.title}")

        return cappedScore
    }

    /**
     * Filter and sort notifications by score
     * Returns only notifications above PRIORITY_THRESHOLD, sorted by score (highest first)
     */
    fun filterAndSortByScore(notifications: List<NotificationData>): List<NotificationData> {
        return notifications
            .map { notif -> notif to scoreNotification(notif) }
            .filter { (_, score) -> score >= PRIORITY_THRESHOLD }
            .sortedByDescending { (_, score) -> score }
            .map { (notif, _) -> notif }
    }

    /**
     * Get all notifications with their scores (for debugging/display)
     */
    fun getNotificationsWithScores(notifications: List<NotificationData>): List<Pair<NotificationData, Int>> {
        return notifications
            .map { notif -> notif to scoreNotification(notif) }
            .sortedByDescending { (_, score) -> score }
    }

    // ----- Scoring Functions -----

    private fun getAppScore(appName: String, rules: PriorityRules): Int {
        val lowerAppName = appName.lowercase()

        // Check high-priority apps (email, messaging, calendar)
        if (rules.highPriorityApps.any { lowerAppName.contains(it.lowercase()) }) {
            return SCORE_HIGH_PRIORITY_APP
        }

        // Check financial apps
        if (rules.financialApps.any { lowerAppName.contains(it.lowercase()) }) {
            return SCORE_FINANCIAL_APP
        }

        // Check neutral apps (social media)
        if (rules.neutralApps.any { lowerAppName.contains(it.lowercase()) }) {
            return SCORE_NEUTRAL_APP
        }

        // Default: slightly below neutral (unknown apps)
        return SCORE_LOW_PRIORITY_APP
    }

    private fun getUserKeywordScore(notification: NotificationData, rules: PriorityRules): Int {
        val content = "${notification.title} ${notification.text}".lowercase()
        var score = 0

        rules.keywords.forEach { keyword ->
            if (content.contains(keyword.lowercase())) {
                score += SCORE_USER_KEYWORD
            }
        }

        return score
    }

    private fun getDomainScore(notification: NotificationData, rules: PriorityRules): Int {
        val content = "${notification.title} ${notification.text}".lowercase()
        var score = 0

        rules.domains.forEach { domain ->
            if (content.contains(domain.lowercase())) {
                score += SCORE_DOMAIN
            }
        }

        return score
    }

    private fun getSenderScore(notification: NotificationData, rules: PriorityRules): Int {
        val title = notification.title?.lowercase() ?: ""
        var score = 0

        rules.senders.forEach { sender ->
            if (title.contains(sender.lowercase())) {
                score += SCORE_SENDER
            }
        }

        return score
    }

    private fun getUrgentKeywordScore(notification: NotificationData): Int {
        val content = "${notification.title} ${notification.text}".lowercase()
        var score = 0

        URGENT_KEYWORDS.forEach { keyword ->
            if (content.contains(keyword)) {
                score += SCORE_URGENT_KEYWORD
            }
        }

        return score
    }

    private fun getTemporalKeywordScore(notification: NotificationData): Int {
        val content = "${notification.title} ${notification.text}".lowercase()
        var score = 0

        TEMPORAL_KEYWORDS.forEach { keyword ->
            if (content.contains(keyword)) {
                score += SCORE_TEMPORAL_KEYWORD
            }
        }

        return score
    }

    private fun getPersonalReferenceScore(notification: NotificationData): Int {
        val content = "${notification.title} ${notification.text}".lowercase()
        var score = 0

        PERSONAL_KEYWORDS.forEach { keyword ->
            if (content.contains(keyword)) {
                score += SCORE_PERSONAL_REFERENCE
                return score // Only count once
            }
        }

        return score
    }

    private fun getCalendarTemporalScore(notification: NotificationData): Int {
        // Check if this is a calendar notification
        val isCalendar = notification.appName.lowercase().contains("calendar") ||
                notification.category == "event"

        if (!isCalendar) return 0

        // Try to extract time information from the notification
        // For now, use Android's priority field as a proxy (high priority = soon)
        // TODO: More sophisticated time extraction from notification text

        val now = System.currentTimeMillis()
        val notificationAge = now - notification.timestamp

        // If notification is very recent (< 30 min old), assume event is soon
        if (notificationAge < 30 * 60 * 1000) {
            return SCORE_CALENDAR_NEXT_30MIN
        }

        // If notification is recent (< 24h old), assume event is today/tomorrow
        if (notificationAge < 24 * 60 * 60 * 1000) {
            return SCORE_CALENDAR_NEXT_24H
        }

        // If notification is within a week, give normal boost
        if (notificationAge < 7 * 24 * 60 * 60 * 1000) {
            return SCORE_CALENDAR_NEXT_7D
        }

        return 0
    }

    // ----- Legacy API (for backward compatibility) -----

    /**
     * Binary classification (for backward compatibility)
     * Returns true if score >= PRIORITY_THRESHOLD
     */
    fun isPriority(notification: NotificationData): Boolean {
        return scoreNotification(notification) >= PRIORITY_THRESHOLD
    }

    /**
     * Filter only priority notifications (for backward compatibility)
     */
    fun filterPriority(notifications: List<NotificationData>): List<NotificationData> {
        return filterAndSortByScore(notifications)
    }

    /**
     * Classify notifications into priority and non-priority groups
     */
    data class ClassificationResult(
        val priority: List<NotificationData>,
        val nonPriority: List<NotificationData>
    )

    fun classify(notifications: List<NotificationData>): ClassificationResult {
        val scored = notifications.map { it to scoreNotification(it) }
        val priority = scored.filter { (_, score) -> score >= PRIORITY_THRESHOLD }.map { (notif, _) -> notif }
        val nonPriority = scored.filter { (_, score) -> score < PRIORITY_THRESHOLD }.map { (notif, _) -> notif }

        return ClassificationResult(priority, nonPriority)
    }
}
