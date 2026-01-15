#!/usr/bin/env kotlin

/**
 * Standalone Test Script for Verdure Notification Summarization Logic
 * 
 * This script tests the core notification filtering and summarization logic
 * without requiring Android dependencies or device deployment.
 * 
 * Run with: kotlinc -script test_notification_logic.kts
 * Or with: kotlin test_notification_logic.kts
 */

// ===== Mock Data Classes =====
// Simplified versions of the Android data classes for testing

data class MockNotificationData(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val category: String?,
    val priority: Int,
    val hasActions: Boolean = false,
    val hasImage: Boolean = false,
    val isOngoing: Boolean = false
)

data class MockPriorityRules(
    val keywords: List<String> = listOf("urgent", "deadline", "interview", "important", "asap"),
    val highPriorityApps: List<String> = listOf("Gmail", "Outlook", "Calendar", "Messages", "Slack"),
    val financialApps: List<String> = listOf("Bank", "Venmo", "PayPal"),
    val neutralApps: List<String> = listOf("Instagram", "WhatsApp", "Facebook"),
    val domains: List<String> = listOf(".edu", ".gov"),
    val senders: List<String> = emptyList(),
    val contacts: List<String> = emptyList()
)

data class MockUserContext(
    val priorityRules: MockPriorityRules = MockPriorityRules()
)

// ===== Scoring Keywords (simplified from ScoringKeywords.kt) =====

object MockScoringKeywords {
    val communicationTier1 = setOf("WhatsApp", "Signal", "Messages", "Phone", "Telegram")
    val communicationTier2 = setOf("Gmail", "Outlook", "Slack", "Discord", "Teams", "Email", "Mail")
    val communicationTier3 = setOf("Instagram", "Twitter", "X", "Facebook", "LinkedIn", "Reddit")
    val lowPriorityApps = setOf("Games", "News", "Shopping", "YouTube", "Netflix", "Spotify")
    
    val urgencyTier1Keywords = setOf("urgent", "critical", "asap", "emergency", "immediately", "911")
    val urgencyTier2Keywords = setOf("important", "deadline", "due", "tonight", "today", "expires")
    val urgencyTier3Keywords = setOf("tomorrow", "this week", "reminder", "follow up", "upcoming", "soon")
    
    val requestKeywords = setOf("please reply", "need response", "waiting for", "respond by", "confirm", "rsvp")
    val meetingKeywords = setOf("meeting", "call", "zoom", "interview", "appointment", "event")
    val temporalKeywords = setOf("due", "deadline", "expires", "ends", "starts", "schedule", "calendar")
    val financialKeywords = setOf("payment", "invoice", "bill", "charge", "transaction", "bank", "fraud")
    val personalKeywords = setOf(" you ", " your ", "you're", "you've", "you'll")
}

fun String.containsAny(keywords: Set<String>): Boolean {
    val lower = this.lowercase()
    return keywords.any { lower.contains(it.lowercase()) }
}

// ===== Mock Notification Filter (simplified from NotificationFilter.kt) =====

class MockNotificationFilter(private val userContext: MockUserContext) {
    companion object {
        private const val CRITICAL_THRESHOLD = 15
        private const val SCORE_CAP_MAX = 24
        private const val SCORE_CAP_MIN = -5
    }
    
    fun scoreNotification(notification: MockNotificationData): Int {
        var score = 0
        
        score += scoreByApp(notification.appName, userContext.priorityRules)
        score += scoreByUserRules(notification, userContext.priorityRules)
        score += scoreByContent(notification.title, notification.text)
        score += scoreByTime(notification.timestamp)
        score += scoreByMetadata(notification)
        
        return score.coerceIn(SCORE_CAP_MIN, SCORE_CAP_MAX)
    }
    
    private fun scoreByApp(appName: String, rules: MockPriorityRules): Int {
        val lowerAppName = appName.lowercase()
        
        if (rules.highPriorityApps.any { lowerAppName.contains(it.lowercase()) }) return 4
        if (rules.financialApps.any { lowerAppName.contains(it.lowercase()) }) return 3
        if (MockScoringKeywords.communicationTier1.any { lowerAppName.contains(it.lowercase()) }) return 3
        if (MockScoringKeywords.communicationTier2.any { lowerAppName.contains(it.lowercase()) }) return 2
        if (MockScoringKeywords.communicationTier3.any { lowerAppName.contains(it.lowercase()) }) return 1
        if (rules.neutralApps.any { lowerAppName.contains(it.lowercase()) }) return 0
        if (MockScoringKeywords.lowPriorityApps.any { lowerAppName.contains(it.lowercase()) }) return -2
        
        return 0
    }
    
    private fun scoreByUserRules(notification: MockNotificationData, rules: MockPriorityRules): Int {
        var score = 0
        val content = "${notification.title} ${notification.text}".lowercase()
        
        val keywordMatches = rules.keywords.count { keyword ->
            content.contains(keyword.lowercase())
        }
        score += (keywordMatches * 2).coerceAtMost(6)
        
        rules.domains.forEach { domain ->
            if (content.contains(domain.lowercase())) {
                score += 2
            }
        }
        
        return score
    }
    
    private fun scoreByContent(title: String?, text: String?): Int {
        var score = 0
        val combined = "${title ?: ""} ${text ?: ""}".lowercase()
        
        if (combined.isBlank()) return 0
        
        if (combined.containsAny(MockScoringKeywords.urgencyTier1Keywords)) {
            score += 5
        } else if (combined.containsAny(MockScoringKeywords.urgencyTier2Keywords)) {
            score += 3
        } else if (combined.containsAny(MockScoringKeywords.urgencyTier3Keywords)) {
            score += 2
        }
        
        if (combined.containsAny(MockScoringKeywords.requestKeywords)) score += 3
        if (combined.containsAny(MockScoringKeywords.meetingKeywords)) score += 3
        if (combined.containsAny(MockScoringKeywords.temporalKeywords)) score += 2
        if (combined.containsAny(MockScoringKeywords.financialKeywords)) score += 2
        
        if (combined.contains('?')) score += 2
        if (combined.count { it == '!' } >= 2) score += 1
        if (combined.containsAny(MockScoringKeywords.personalKeywords)) score += 1
        
        return score
    }
    
    private fun scoreByTime(timestamp: Long): Int {
        var score = 0
        val age = System.currentTimeMillis() - timestamp
        
        when {
            age < 5 * 60 * 1000 -> score += 2    // < 5 minutes
            age < 30 * 60 * 1000 -> score += 1   // < 30 minutes
            age > 24 * 60 * 60 * 1000 -> score -= 1  // > 24 hours
        }
        
        return score
    }
    
    private fun scoreByMetadata(notification: MockNotificationData): Int {
        var score = 0
        
        // Android priority: HIGH=1, MAX=2, LOW=-1, MIN=-2, DEFAULT=0
        when (notification.priority) {
            1, 2 -> score += 3
            -1, -2 -> score -= 1
        }
        
        if (notification.hasActions) score += 1
        if (notification.hasImage) score += 1
        if (notification.isOngoing) score -= 3
        
        return score
    }
    
    fun isCritical(notification: MockNotificationData): Boolean {
        return scoreNotification(notification) >= CRITICAL_THRESHOLD
    }
}

// ===== Mock Summary Generator =====

class MockSummaryGenerator {
    fun generateSummary(notifications: List<MockNotificationData>): String {
        if (notifications.isEmpty()) return "No critical notifications"
        
        return notifications.joinToString("\n") { notif ->
            val text = notif.text ?: notif.title ?: ""
            val truncated = if (text.length > 100) {
                text.substring(0, 100) + "..."
            } else {
                text
            }
            "${notif.appName}: $truncated"
        }
    }
}

// ===== Test Data Generator =====

fun createTestNotifications(): List<MockNotificationData> {
    val now = System.currentTimeMillis()
    
    return listOf(
        // CRITICAL notifications (should score >= 15)
        MockNotificationData(
            id = 1,
            packageName = "com.google.android.gm",
            appName = "Gmail",
            title = "URGENT: Interview tomorrow at 9am",
            text = "Please confirm your availability ASAP",
            timestamp = now - 2 * 60 * 1000, // 2 minutes ago
            category = "email",
            priority = 1, // HIGH
            hasActions = true
        ),
        
        MockNotificationData(
            id = 2,
            packageName = "com.slack",
            appName = "Slack",
            title = "Meeting in 15 minutes",
            text = "Zoom link: https://zoom.us/j/123 - Please join ASAP",
            timestamp = now - 1 * 60 * 1000, // 1 minute ago
            category = "msg",
            priority = 1,
            hasActions = true
        ),
        
        MockNotificationData(
            id = 3,
            packageName = "com.chase.bank",
            appName = "Chase Bank",
            title = "Security Alert: Unusual activity detected",
            text = "Urgent: Please verify transaction of $500. Respond immediately.",
            timestamp = now - 5 * 60 * 1000, // 5 minutes ago
            category = "msg",
            priority = 2, // MAX
            hasActions = true
        ),
        
        // Medium priority notifications (score 5-14)
        MockNotificationData(
            id = 4,
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Mom",
            text = "Can you call me when you get a chance?",
            timestamp = now - 10 * 60 * 1000, // 10 minutes ago
            category = "msg",
            priority = 0,
            hasActions = true
        ),
        
        MockNotificationData(
            id = 5,
            packageName = "com.google.android.calendar",
            appName = "Calendar",
            title = "Event tomorrow: Team standup",
            text = "9:00 AM - 9:30 AM",
            timestamp = now - 30 * 60 * 1000, // 30 minutes ago
            category = "event",
            priority = 0
        ),
        
        // Low priority notifications (score < 5)
        MockNotificationData(
            id = 6,
            packageName = "com.instagram",
            appName = "Instagram",
            title = "New follower",
            text = "john_doe started following you",
            timestamp = now - 60 * 60 * 1000, // 1 hour ago
            category = "social",
            priority = 0
        ),
        
        MockNotificationData(
            id = 7,
            packageName = "com.youtube",
            appName = "YouTube",
            title = "New video from TechChannel",
            text = "Check out our latest tech review!",
            timestamp = now - 2 * 60 * 60 * 1000, // 2 hours ago
            category = "recommendation",
            priority = -1 // LOW
        ),
        
        // Edge cases
        MockNotificationData(
            id = 8,
            packageName = "com.spotify",
            appName = "Spotify",
            title = "Now Playing",
            text = "Song Name - Artist",
            timestamp = now,
            category = "transport",
            priority = 0,
            isOngoing = true // Should get -3 penalty
        ),
        
        MockNotificationData(
            id = 9,
            packageName = "com.unknown.app",
            appName = "Unknown App",
            title = null,
            text = null,
            timestamp = now - 48 * 60 * 60 * 1000, // 2 days ago (stale)
            category = null,
            priority = 0
        )
    )
}

// ===== Test Runner =====

fun runTests() {
    println("=" * 80)
    println("Verdure Notification Summarization Logic Test")
    println("=" * 80)
    println()
    
    val userContext = MockUserContext()
    val filter = MockNotificationFilter(userContext)
    val summaryGenerator = MockSummaryGenerator()
    val notifications = createTestNotifications()
    
    // Test 1: Score all notifications
    println("TEST 1: Notification Scoring")
    println("-" * 80)
    val scoredNotifications = notifications.map { notif ->
        val score = filter.scoreNotification(notif)
        Triple(notif, score, filter.isCritical(notif))
    }.sortedByDescending { it.second }
    
    scoredNotifications.forEach { (notif, score, isCritical) ->
        val criticalMarker = if (isCritical) " [CRITICAL]" else ""
        println("Score: %2d%s | %s | %s".format(score, criticalMarker, notif.appName.padEnd(15), notif.title ?: "(no title)"))
    }
    println()
    
    // Test 2: Filter for CRITICAL notifications (score >= 15)
    println("TEST 2: CRITICAL Notification Detection (score >= 15)")
    println("-" * 80)
    val criticalNotifications = scoredNotifications.filter { it.third }.map { it.first }
    println("Found ${criticalNotifications.size} CRITICAL notifications:")
    criticalNotifications.forEach { notif ->
        println("  - ${notif.appName}: ${notif.title}")
    }
    println()
    
    // Test 3: Generate summary
    println("TEST 3: Summary Generation")
    println("-" * 80)
    val summary = summaryGenerator.generateSummary(criticalNotifications)
    println("Generated Summary:")
    println(summary)
    println()
    
    // Test 4: Edge cases
    println("TEST 4: Edge Case Validation")
    println("-" * 80)
    
    // Empty notification
    val emptyNotif = notifications.find { it.title == null && it.text == null }
    if (emptyNotif != null) {
        val score = filter.scoreNotification(emptyNotif)
        println("✓ Empty notification handled: score = $score")
    }
    
    // Ongoing notification penalty
    val ongoingNotif = notifications.find { it.isOngoing }
    if (ongoingNotif != null) {
        val score = filter.scoreNotification(ongoingNotif)
        println("✓ Ongoing notification penalty applied: score = $score (should be negative)")
    }
    
    // Stale notification
    val staleNotif = notifications.find { 
        System.currentTimeMillis() - it.timestamp > 24 * 60 * 60 * 1000 
    }
    if (staleNotif != null) {
        val score = filter.scoreNotification(staleNotif)
        println("✓ Stale notification (>24h) handled: score = $score")
    }
    
    println()
    
    // Test 5: Summary statistics
    println("TEST 5: Summary Statistics")
    println("-" * 80)
    val totalNotifications = notifications.size
    val criticalCount = criticalNotifications.size
    val criticalPercentage = (criticalCount.toDouble() / totalNotifications * 100).toInt()
    
    println("Total notifications: $totalNotifications")
    println("CRITICAL notifications: $criticalCount ($criticalPercentage%)")
    println("Average score: %.1f".format(scoredNotifications.map { it.second }.average()))
    println("Score range: ${scoredNotifications.last().second} to ${scoredNotifications.first().second}")
    println()
    
    // Test 6: Validation
    println("TEST 6: Validation Results")
    println("-" * 80)
    
    var passedTests = 0
    var totalTests = 0
    
    // Validate: Gmail urgent interview should be CRITICAL
    totalTests++
    val gmailUrgent = notifications.find { it.id == 1 }
    if (gmailUrgent != null && filter.isCritical(gmailUrgent)) {
        println("✓ PASS: Gmail urgent interview is CRITICAL")
        passedTests++
    } else {
        println("✗ FAIL: Gmail urgent interview should be CRITICAL")
    }
    
    // Validate: Slack meeting should be CRITICAL
    totalTests++
    val slackMeeting = notifications.find { it.id == 2 }
    if (slackMeeting != null && filter.isCritical(slackMeeting)) {
        println("✓ PASS: Slack meeting is CRITICAL")
        passedTests++
    } else {
        println("✗ FAIL: Slack meeting should be CRITICAL")
    }
    
    // Validate: Bank security alert should be CRITICAL
    totalTests++
    val bankAlert = notifications.find { it.id == 3 }
    if (bankAlert != null && filter.isCritical(bankAlert)) {
        println("✓ PASS: Bank security alert is CRITICAL")
        passedTests++
    } else {
        println("✗ FAIL: Bank security alert should be CRITICAL")
    }
    
    // Validate: Instagram notification should NOT be CRITICAL
    totalTests++
    val instagram = notifications.find { it.id == 6 }
    if (instagram != null && !filter.isCritical(instagram)) {
        println("✓ PASS: Instagram notification is NOT CRITICAL")
        passedTests++
    } else {
        println("✗ FAIL: Instagram notification should NOT be CRITICAL")
    }
    
    // Validate: YouTube notification should NOT be CRITICAL
    totalTests++
    val youtube = notifications.find { it.id == 7 }
    if (youtube != null && !filter.isCritical(youtube)) {
        println("✓ PASS: YouTube notification is NOT CRITICAL")
        passedTests++
    } else {
        println("✗ FAIL: YouTube notification should NOT be CRITICAL")
    }
    
    println()
    println("=" * 80)
    println("Test Results: $passedTests/$totalTests tests passed")
    println("=" * 80)
    
    if (passedTests == totalTests) {
        println("✓ ALL TESTS PASSED!")
    } else {
        println("✗ SOME TESTS FAILED")
    }
}

// ===== Main Execution =====

operator fun String.times(n: Int): String = this.repeat(n)

runTests()
