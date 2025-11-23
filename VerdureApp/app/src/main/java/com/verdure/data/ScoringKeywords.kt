package com.verdure.data

/**
 * Centralized keyword sets and app categorization for notification scoring.
 *
 * Architecture: Scaling laws approach - more signals = better accuracy
 * Organizes keywords into tiers for granular scoring (not just binary +2/-1)
 */
object ScoringKeywords {

    // ----- Communication App Tiers -----

    /**
     * Tier 1: Immediate communication (direct messages, calls)
     * Score: +3
     */
    val communicationTier1 = setOf(
        "WhatsApp", "Signal", "Messages", "Phone",
        "Telegram", "WeChat", "iMessage"
    )

    /**
     * Tier 2: Important communication (email, work chat)
     * Score: +2
     */
    val communicationTier2 = setOf(
        "Gmail", "Outlook", "Slack", "Discord", "Teams",
        "Email", "Mail", "ProtonMail"
    )

    /**
     * Tier 3: Social media (lower priority)
     * Score: +1
     */
    val communicationTier3 = setOf(
        "Instagram", "Twitter", "X", "Facebook", "LinkedIn",
        "Reddit", "TikTok", "Snapchat", "Mastodon"
    )

    /**
     * Low priority apps (games, promotions, news)
     * Score: -2
     */
    val lowPriorityApps = setOf(
        "Games", "News", "Shopping", "YouTube", "Netflix",
        "Spotify", "TikTok", "Candy Crush", "ESPN"
    )

    // ----- Urgency Keywords (Tiered) -----

    /**
     * Tier 1 urgency: Critical/emergency
     * Score: +5
     */
    val urgencyTier1Keywords = setOf(
        "urgent", "critical", "asap", "emergency", "immediately",
        "911", "attention required", "action needed", "time sensitive"
    )

    /**
     * Tier 2 urgency: Important/deadline-driven
     * Score: +3
     */
    val urgencyTier2Keywords = setOf(
        "important", "deadline", "due", "tonight", "today",
        "expires", "expiring", "final notice", "last chance",
        "overdue", "past due"
    )

    /**
     * Tier 3 urgency: Soon/upcoming
     * Score: +2
     */
    val urgencyTier3Keywords = setOf(
        "tomorrow", "this week", "reminder", "follow up",
        "upcoming", "soon", "pending", "scheduled"
    )

    // ----- Action/Request Keywords -----

    /**
     * Keywords indicating user action is needed
     * Score: +3
     */
    val requestKeywords = setOf(
        "please reply", "need response", "waiting for", "respond by",
        "confirm", "rsvp", "action required", "approval needed",
        "please respond", "awaiting your", "needs your", "requires your"
    )

    // ----- Meeting/Event Keywords -----

    /**
     * Meeting and event-related keywords
     * Score: +3
     */
    val meetingKeywords = setOf(
        "meeting", "call", "zoom", "interview", "appointment",
        "event", "conference", "session", "webinar", "seminar",
        "standup", "sync", "review"
    )

    // ----- Temporal Keywords -----

    /**
     * Time-related keywords (deadlines, schedules)
     * Score: +2
     */
    val temporalKeywords = setOf(
        "due", "deadline", "expires", "ends", "starts", "begins",
        "schedule", "calendar", "by end of", "before"
    )

    // ----- Question Indicators -----

    /**
     * Keywords indicating a question/request for input
     * These are structural (used alongside '?' detection)
     * Score: +2
     */
    val questionKeywords = setOf(
        "are you", "can you", "could you", "would you", "will you",
        "do you", "did you", "have you", "what", "when", "where",
        "how", "why", "which"
    )

    // ----- Financial Keywords -----

    /**
     * Financial/payment keywords
     * Score: +2
     */
    val financialKeywords = setOf(
        "payment", "invoice", "bill", "charge", "transaction",
        "bank", "account", "credit", "debit", "balance",
        "deposit", "withdrawal", "transfer", "fraud", "security alert"
    )

    // ----- Personal Reference Keywords -----

    /**
     * Keywords indicating personal relevance
     * Score: +1
     */
    val personalKeywords = setOf(
        " you ", " your ", "you're", "you've", "you'll",
        "you are", "your account", "your order"
    )
}

/**
 * Helper extension function to check if a string contains any keyword from a set.
 * Case-insensitive matching.
 */
fun String.containsAny(keywords: Set<String>): Boolean {
    val lower = this.lowercase()
    return keywords.any { lower.contains(it.lowercase()) }
}

/**
 * Helper extension to count keyword occurrences
 */
fun String.countKeywords(keywords: Set<String>): Int {
    val lower = this.lowercase()
    return keywords.count { lower.contains(it.lowercase()) }
}
