package com.verdure.data

import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * Enhanced multi-factor notification scoring system.
 *
 * Architecture: Scaling laws approach - more signals = better accuracy
 * Uses 15+ factors across multiple dimensions:
 * - App-based scoring (granular tiers)
 * - User-learned priorities (context)
 * - Content analysis (semantic + structural)
 * - Temporal factors (freshness, time-of-day)
 * - Sender/relationship signals
 * - Metadata signals (actions, images, priority flags)
 * - Calendar proximity
 *
 * Score range: -5 to +24
 * Threshold: >= 2 considered priority
 */
class NotificationFilter(private val userContext: UserContext) {

    companion object {
        private const val TAG = "NotificationFilter"

        // Threshold for what counts as "priority"
        private const val PRIORITY_THRESHOLD = 2

        // Score cap to prevent over-prioritization
        private const val SCORE_CAP_MAX = 24
        private const val SCORE_CAP_MIN = -5
    }

    /**
     * Score a single notification using multi-factor analysis.
     * Returns numerical score (higher = more important).
     */
    fun scoreNotification(notification: NotificationData): Int {
        var score = 0

        // A. App-based scoring (granular tiers)
        score += scoreByApp(notification.packageName, notification.appName, userContext.priorityRules)

        // B. User-learned priorities (from LLM updates)
        score += scoreByUserRules(notification, userContext.priorityRules)

        // C. Content analysis (semantic + structural)
        score += scoreByContent(notification.title, notification.text)

        // D. Temporal factors
        score += scoreByTime(notification.timestamp)

        // E. Sender/relationship signals
        score += scoreBySender(notification.title, notification.text, userContext.priorityRules)

        // F. Metadata signals
        score += scoreByMetadata(notification)

        // G. Calendar proximity (existing)
        score += scoreByCalendarProximity(notification)

        // H. User behavior (future - when tracking added)
        // score += scoreByUserBehavior(notification.appName, userStats)

        // Cap score to prevent extreme values
        val cappedScore = score.coerceIn(SCORE_CAP_MIN, SCORE_CAP_MAX)

        if (score != cappedScore) {
            Log.d(TAG, "Score ${cappedScore} (capped from $score): ${notification.appName} - ${notification.title}")
        } else {
            Log.d(TAG, "Score ${cappedScore}: ${notification.appName} - ${notification.title}")
        }

        return cappedScore
    }

    // ----- Scoring Helper Methods -----

    /**
     * A. App-based scoring with granular tiers
     * 
     * Priority: customAppOrder (user drag-and-drop) > tier-based defaults
     */
    private fun scoreByApp(packageName: String, appName: String, rules: PriorityRules): Int {
        val lowerAppName = appName.lowercase()

        // **HIGHEST PRIORITY: User's custom drag-and-drop ordering**
        // Uses exact package name matching for precision
        if (rules.customAppOrder.isNotEmpty()) {
            val position = rules.customAppOrder.indexOf(packageName)

            if (position != -1) {
                // Position 0 (first) = +10, position 1 = +8, position 2 = +6
                // Then decay: 3 = +5, 4 = +4, 5 = +3, 6+ = +2
                return when (position) {
                    0 -> 10
                    1 -> 8
                    2 -> 6
                    3 -> 5
                    4 -> 4
                    5 -> 3
                    else -> 2
                }
            }
            // If app not in customAppOrder, fall through to default scoring
        }

        // **DEFAULT TIER-BASED SCORING** (used if no custom order set)

        // User-specified high priority apps (from chat)
        if (rules.highPriorityApps.any { lowerAppName.contains(it.lowercase()) }) {
            return 4
        }

        // User-specified financial apps
        if (rules.financialApps.any { lowerAppName.contains(it.lowercase()) }) {
            return 3
        }

        // Communication Tier 1 (immediate: WhatsApp, Signal, Messages)
        if (ScoringKeywords.communicationTier1.any { lowerAppName.contains(it.lowercase()) }) {
            return 3
        }

        // Communication Tier 2 (important: Gmail, Slack, Discord)
        if (ScoringKeywords.communicationTier2.any { lowerAppName.contains(it.lowercase()) }) {
            return 2
        }

        // Communication Tier 3 (social: Instagram, Twitter)
        if (ScoringKeywords.communicationTier3.any { lowerAppName.contains(it.lowercase()) }) {
            return 1
        }

        // User-specified neutral apps
        if (rules.neutralApps.any { lowerAppName.contains(it.lowercase()) }) {
            return 0
        }

        // Low priority apps (games, news, shopping)
        if (ScoringKeywords.lowPriorityApps.any { lowerAppName.contains(it.lowercase()) }) {
            return -2
        }

        // Unknown apps default to neutral
        return 0
    }

    /**
     * B. User-learned priorities (keywords, domains, senders, contacts)
     */
    private fun scoreByUserRules(notification: NotificationData, rules: PriorityRules): Int {
        var score = 0
        val content = "${notification.title} ${notification.text}".lowercase()

        // User-specified keywords (+2 each, capped at +6 to prevent keyword spam)
        val keywordMatches = rules.keywords.count { keyword ->
            content.contains(keyword.lowercase())
        }
        score += (keywordMatches * 2).coerceAtMost(6)

        // Domain matching (.edu, .gov, etc.)
        rules.domains.forEach { domain ->
            if (content.contains(domain.lowercase())) {
                score += 2
            }
        }

        return score
    }

    /**
     * C. Content analysis (semantic + structural signals)
     */
    private fun scoreByContent(title: String?, text: String?): Int {
        var score = 0
        val combined = "${title ?: ""} ${text ?: ""}".lowercase()

        if (combined.isBlank()) return 0

        // Urgency keywords (tiered)
        if (combined.containsAny(ScoringKeywords.urgencyTier1Keywords)) {
            score += 5
        } else if (combined.containsAny(ScoringKeywords.urgencyTier2Keywords)) {
            score += 3
        } else if (combined.containsAny(ScoringKeywords.urgencyTier3Keywords)) {
            score += 2
        }

        // Request/action keywords
        if (combined.containsAny(ScoringKeywords.requestKeywords)) {
            score += 3
        }

        // Meeting keywords
        if (combined.containsAny(ScoringKeywords.meetingKeywords)) {
            score += 3
        }

        // Temporal keywords
        if (combined.containsAny(ScoringKeywords.temporalKeywords)) {
            score += 2
        }

        // Financial keywords
        if (combined.containsAny(ScoringKeywords.financialKeywords)) {
            score += 2
        }

        // Structural signals
        if (combined.contains('?')) {
            score += 2  // Question asked
        }

        if (combined.count { it == '!' } >= 2) {
            score += 1  // Multiple exclamations = excitement/urgency
        }

        // ALL CAPS detection (at least 4 uppercase letters in a row)
        if (Regex("[A-Z]{4,}").containsMatchIn(title ?: "")) {
            score += 2
        }

        // Currency symbols
        if (combined.containsAny(setOf("$", "€", "£", "¥"))) {
            score += 2
        }

        // Personal reference
        if (combined.containsAny(ScoringKeywords.personalKeywords)) {
            score += 1
        }

        return score
    }

    /**
     * D. Temporal factors (freshness, time-of-day context)
     */
    private fun scoreByTime(timestamp: Long): Int {
        var score = 0
        val age = System.currentTimeMillis() - timestamp

        // Freshness bonus
        when {
            age < 5 * 60 * 1000 -> score += 2    // < 5 minutes: very fresh
            age < 30 * 60 * 1000 -> score += 1   // < 30 minutes: fresh
            age > 24 * 60 * 60 * 1000 -> score -= 1  // > 24 hours: stale
        }

        // Time-of-day context (future enhancement - can be user-customizable)
        // For now, keep it simple
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isWorkHours = hour in 9..17

        // Could boost work-related apps during work hours
        // This would require app categorization, deferred for now

        return score
    }

    /**
     * E. Sender/relationship signals
     */
    private fun scoreBySender(title: String?, text: String?, rules: PriorityRules): Int {
        var score = 0
        val combined = "${title ?: ""} ${text ?: ""}".lowercase()

        if (combined.isBlank()) return 0

        // Exact sender match (email addresses)
        rules.senders.forEach { sender ->
            if (combined.contains(sender.lowercase())) {
                score += 4
                return@forEach  // Only count once per sender
            }
        }

        // Contact name match
        rules.contacts.forEach { contact ->
            if (combined.contains(contact.lowercase())) {
                score += 4
                return@forEach  // Only count once per contact
            }
        }

        return score
    }

    /**
     * F. Metadata signals (Android notification properties)
     */
    private fun scoreByMetadata(notification: NotificationData): Int {
        var score = 0

        // Android priority flag
        when (notification.priority) {
            NotificationCompat.PRIORITY_HIGH, NotificationCompat.PRIORITY_MAX -> score += 3
            NotificationCompat.PRIORITY_LOW, NotificationCompat.PRIORITY_MIN -> score -= 1
            // PRIORITY_DEFAULT = 0, no change
        }

        // Has action buttons (reply, archive, etc.) = actionable
        if (notification.hasActions) {
            score += 1
        }

        // Has inline image/rich content = visually important
        if (notification.hasImage) {
            score += 1
        }

        // Ongoing notifications (music, timer) should not dominate priority queue
        if (notification.isOngoing) {
            score -= 3
        }

        return score
    }

    /**
     * G. Calendar event temporal scoring
     */
    private fun scoreByCalendarProximity(notification: NotificationData): Int {
        // Check if this is a calendar notification
        val isCalendar = notification.appName.lowercase().contains("calendar") ||
                notification.category == "event"

        if (!isCalendar) return 0

        // Use notification recency as proxy for event proximity
        // Fresh calendar notification = event is soon
        val now = System.currentTimeMillis()
        val notificationAge = now - notification.timestamp

        return when {
            notificationAge < 30 * 60 * 1000 -> 5    // < 30 min: event very soon
            notificationAge < 24 * 60 * 60 * 1000 -> 3  // < 24h: event today/tomorrow
            notificationAge < 7 * 24 * 60 * 60 * 1000 -> 1  // < 7d: event this week
            else -> 0
        }
    }

    // ----- Public API -----

    /**
     * Filter and sort notifications by score.
     * Returns only notifications above PRIORITY_THRESHOLD, sorted by score (highest first).
     */
    fun filterAndSortByScore(notifications: List<NotificationData>): List<NotificationData> {
        return notifications
            .map { notif -> notif to scoreNotification(notif) }
            .filter { (_, score) -> score >= PRIORITY_THRESHOLD }
            .sortedByDescending { (_, score) -> score }
            .map { (notif, _) -> notif }
    }

    /**
     * Get all notifications with their scores (for debugging/display).
     */
    fun getNotificationsWithScores(notifications: List<NotificationData>): List<Pair<NotificationData, Int>> {
        return notifications
            .map { notif -> notif to scoreNotification(notif) }
            .sortedByDescending { (_, score) -> score }
    }

    /**
     * Binary classification (for backward compatibility).
     * Returns true if score >= PRIORITY_THRESHOLD.
     */
    fun isPriority(notification: NotificationData): Boolean {
        return scoreNotification(notification) >= PRIORITY_THRESHOLD
    }

    /**
     * Filter only priority notifications (for backward compatibility).
     */
    fun filterPriority(notifications: List<NotificationData>): List<NotificationData> {
        return filterAndSortByScore(notifications)
    }

    /**
     * Classify notifications into priority and non-priority groups.
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
