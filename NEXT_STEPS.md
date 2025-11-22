# Implementation Plan: Double-Pass Intent Recognition with Robust Heuristic

## Current Status

✅ **Single-pass intent detection implemented (Session 11)**
- 4 intents: `update_priorities`, `query_priorities`, `analyze_notifications`, `chat`
- Validation layer for hallucinated changes
- Hybrid notification synthesis (2 LLM calls for notification queries)
- Commit: `74aa459`

⚠️ **Problem Identified:**
- Intent recognition still struggling despite fixes
- Single-pass approach asks too much of model (classify + respond + generate JSON)
- Need cleaner separation of concerns

## New Architecture: Double-Pass System

**Core idea:** Separate intent classification from response generation

### Pass 1: Intent Classification (Always)
- **Input:** User message only (minimal context)
- **Output:** JSON with intent classification
- **Intents:** `<notification_query>`, `<notification_rerank>`, `<chat>`
- **Speed:** Fast (~1 second, minimal prompt)

### Pass 2: Intent-Specific Processing (Conditional)
- **If `<notification_query>`:**
  - Fetch top 8 urgent notifications (from heuristic scoring)
  - Prompt: "Summarize these notifications effectively for the user"
  - Return: Natural language summary

- **If `<notification_rerank>`:**
  - Prompt: "Extract priority update factors from user request"
  - Return: JSON with factors for robust heuristic update
  - Apply changes to heuristic rules

- **If `<chat>`:**
  - No second pass needed
  - Return: Natural conversation response from Pass 1

**Benefits:**
- ✅ Cleaner intent recognition (one job per prompt)
- ✅ More robust JSON generation (separate passes)
- ✅ Smaller prompts = fewer tokens = less confusion
- ✅ Chat responses remain fast (1 pass)
- ✅ Notification operations get quality second pass

**Tradeoffs:**
- ⚠️ 2 LLM calls for notification operations (~3-4 seconds total)
- ⚠️ Chat remains 1 pass, so same speed as before

---

## Implementation Plan

### **Phase 1: Expand Notification Heuristic (Robust Multi-Factor Scoring)** ⭐ FIRST PRIORITY

**Goal:** Create a more robust scoring system that scales with more factors (inspired by scaling laws: more signals = better accuracy)

#### 1.1 Current Heuristic (8 factors, max score ~6-8)

Current `NotificationFilter.calculatePriorityScore()` uses:
1. App-based scoring (+2 for email/calendar, +2 for financial, 0 for social, -1 for unknown)
2. User keywords (+2 each)
3. Domain matching (+2 for .edu/.gov)
4. Sender matching (+2)
5. Urgent keywords (+3 for urgent/critical/asap)
6. Temporal keywords (+2 for due/deadline/today)
7. Personal reference (+1 for you/your)
8. Calendar proximity (+5 for 30min, +3 for 24hr, +1 for 7day)

**Limitations:**
- Binary scoring (+2, +3, -1) doesn't capture importance gradients
- Only 8 factors = limited signal
- No user behavior learning
- No context awareness (time of day, day of week)

#### 1.2 Enhanced Heuristic (20+ factors, granular scoring)

**New scoring dimensions to add:**

**A. User Behavior Signals (Future - requires tracking)**
- Notification open rate per app (0 to +5 scale)
- Average time to open (fast = urgent: +3, slow = can wait: 0)
- Dismissal rate (often dismissed = -2)
- Reply rate (high = important: +2)

**B. Temporal Context**
- Time of day (work hours: email +2, evening: social +1)
- Day of week (weekday: work apps +2, weekend: personal +1)
- Notification frequency (first from app today: +2, 10th: -1)
- Age of notification (fresh: +1, >24hrs: -1)

**C. Content Analysis (Enhanced)**
- Question marks (asking you something: +2)
- Exclamation marks (excitement/urgency: +1)
- ALL CAPS words (+2 for urgency)
- Numbers in text (likely deadline/meeting time: +1)
- Currency symbols ($, €: +2 for financial)
- Call to action verbs (respond, reply, confirm: +2)

**D. Relationship Signals**
- Sender in contacts (known person: +3)
- Sender frequency (first message from this person today: +2)
- Sender in high-priority contacts list (+4)
- Group message vs direct message (direct: +2)

**E. App Category Intelligence**
- Communication apps priority tiers:
  - Tier 1 (immediate): WhatsApp, Signal, Messages (+3)
  - Tier 2 (important): Email, Slack, Discord (+2)
  - Tier 3 (social): Instagram, Twitter, Reddit (+1)
  - Tier 4 (low): Games, news, promotions (0)
- User can boost/lower entire categories

**F. Semantic Keywords (Expanded)**
- Urgency tier 1 (+5): "urgent", "critical", "asap", "emergency", "immediately"
- Urgency tier 2 (+3): "important", "deadline", "due", "tonight", "today"
- Urgency tier 3 (+2): "tomorrow", "this week", "reminder", "follow up"
- Request keywords (+3): "please reply", "need response", "waiting for you"
- Meeting keywords (+3): "meeting", "call", "zoom", "interview"

**G. Notification Metadata**
- Priority flag from Android (if HIGH: +3, if LOW: -1)
- Has actions (reply/archive buttons: +1)
- Has inline image (+1 for visual importance)
- Is ongoing notification (music/timer: -2 from priority queue)

#### 1.3 Scoring Implementation

**New `NotificationFilter.calculatePriorityScore()` structure:**

```kotlin
fun calculatePriorityScore(notification: NotificationData, context: UserContext): Int {
    var score = 0

    // A. App-based scoring (granular tiers)
    score += scoreByApp(notification.app, context)

    // B. User-learned priorities (from LLM updates)
    score += scoreByUserRules(notification, context.priorityRules)

    // C. Content analysis (semantic + structural)
    score += scoreByContent(notification.title, notification.text)

    // D. Temporal factors
    score += scoreByTime(notification.timestamp)

    // E. Sender/relationship signals
    score += scoreBySender(notification.sender, context)

    // F. Metadata signals
    score += scoreByMetadata(notification)

    // G. Calendar proximity (existing)
    score += scoreByCalendarProximity(notification)

    // H. User behavior (future - when tracking added)
    // score += scoreByUserBehavior(notification.app, userStats)

    // Cap at 24 to prevent over-prioritization
    return score.coerceIn(-5, 24)
}
```

**Scoring breakdown helpers:**

```kotlin
private fun scoreByApp(app: String, context: UserContext): Int {
    val rules = context.priorityRules

    return when {
        app in rules.highPriorityApps -> 4
        app in rules.financialApps -> 3
        app in communicationTier1 -> 3
        app in communicationTier2 -> 2
        app in rules.neutralApps -> 0
        app in lowPriorityApps -> -2
        else -> 0  // Unknown apps are neutral
    }
}

private fun scoreByContent(title: String, text: String): Int {
    var score = 0
    val combined = "$title $text".lowercase()

    // Urgency keywords (tiered)
    if (combined.containsAny(urgencyTier1Keywords)) score += 5
    else if (combined.containsAny(urgencyTier2Keywords)) score += 3
    else if (combined.containsAny(urgencyTier3Keywords)) score += 2

    // Request/action keywords
    if (combined.containsAny(requestKeywords)) score += 3

    // Meeting keywords
    if (combined.containsAny(meetingKeywords)) score += 3

    // Temporal keywords
    if (combined.containsAny(temporalKeywords)) score += 2

    // Structural signals
    if (combined.contains('?')) score += 2  // Question
    if (combined.count { it == '!' } >= 2) score += 1  // Multiple exclamations
    if (combined.any { it.isUpperCase() } && combined.count { it.isUpperCase() } > 3) score += 2  // ALL CAPS
    if (combined.containsAny(listOf("$", "€", "£"))) score += 2  // Currency

    // Personal reference
    if (combined.containsAny(listOf("you", "your"))) score += 1

    return score
}

private fun scoreByTime(timestamp: Long): Int {
    var score = 0
    val age = System.currentTimeMillis() - timestamp
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    // Freshness bonus
    if (age < 5 * 60 * 1000) score += 2  // < 5 minutes: very fresh
    else if (age < 30 * 60 * 1000) score += 1  // < 30 minutes: fresh
    else if (age > 24 * 60 * 60 * 1000) score -= 1  // > 24 hours: stale

    // Time of day context (can be user-customizable)
    // Example: Work hours (9am-5pm) on weekdays boost work apps
    // This should reference user preferences in context

    return score
}

private fun scoreBySender(sender: String?, context: UserContext): Int {
    if (sender.isNullOrBlank()) return 0

    var score = 0
    val rules = context.priorityRules

    // Exact sender match (email addresses)
    if (rules.senders.any { sender.contains(it, ignoreCase = true) }) {
        score += 4
    }

    // Contact name match
    if (rules.contacts.any { sender.contains(it, ignoreCase = true) }) {
        score += 4
    }

    // Domain match (.edu, .gov, etc.)
    if (rules.domains.any { sender.contains(it, ignoreCase = true) }) {
        score += 2
    }

    return score
}

private fun scoreByMetadata(notification: NotificationData): Int {
    var score = 0

    // Android priority flag
    when (notification.priority) {
        NotificationCompat.PRIORITY_HIGH, NotificationCompat.PRIORITY_MAX -> score += 3
        NotificationCompat.PRIORITY_LOW, NotificationCompat.PRIORITY_MIN -> score -= 1
    }

    // Has action buttons (reply, archive, etc.)
    if (notification.hasActions) score += 1

    // Has inline image/rich content
    if (notification.hasImage) score += 1

    // Ongoing notifications (music, timer) should not dominate priority queue
    if (notification.isOngoing) score -= 3

    return score
}
```

#### 1.4 Keyword Constants

Create `ScoringKeywords.kt`:

```kotlin
package com.verdure.data

object ScoringKeywords {
    // Communication app tiers
    val communicationTier1 = setOf("WhatsApp", "Signal", "Messages", "Phone")
    val communicationTier2 = setOf("Gmail", "Outlook", "Slack", "Discord", "Teams")
    val lowPriorityApps = setOf("Games", "News", "Shopping", "YouTube")

    // Urgency keywords (tiered)
    val urgencyTier1Keywords = setOf("urgent", "critical", "asap", "emergency", "immediately", "911")
    val urgencyTier2Keywords = setOf("important", "deadline", "due", "tonight", "today", "expires")
    val urgencyTier3Keywords = setOf("tomorrow", "this week", "reminder", "follow up", "upcoming")

    // Action/request keywords
    val requestKeywords = setOf(
        "please reply", "need response", "waiting for", "respond by", "confirm",
        "rsvp", "action required", "approval needed"
    )

    // Meeting/event keywords
    val meetingKeywords = setOf(
        "meeting", "call", "zoom", "interview", "appointment", "event",
        "conference", "session"
    )

    // Temporal keywords
    val temporalKeywords = setOf(
        "due", "deadline", "expires", "ends", "starts", "begins",
        "schedule", "calendar"
    )
}

// Helper extension
fun String.containsAny(keywords: Set<String>): Boolean {
    return keywords.any { this.contains(it, ignoreCase = true) }
}
```

#### 1.5 Files to Modify

**Create:**
- `VerdureApp/app/src/main/java/com/verdure/data/ScoringKeywords.kt`

**Modify:**
- `VerdureApp/app/src/main/java/com/verdure/data/NotificationFilter.kt`
  - Replace `calculatePriorityScore()` with new multi-factor implementation
  - Add helper methods: `scoreByApp()`, `scoreByContent()`, `scoreByTime()`, `scoreBySender()`, `scoreByMetadata()`
  - Update score cap from 24 to allow wider range (-5 to 24)

**Update:**
- `VerdureApp/app/src/main/java/com/verdure/data/NotificationData.kt`
  - Add fields: `priority: Int`, `hasActions: Boolean`, `hasImage: Boolean`, `isOngoing: Boolean`

#### 1.6 Success Criteria

✅ Scoring uses 15+ factors (up from 8)
✅ Granular scoring (not just +2/-1, but 0-5 scales)
✅ Score range expanded to -5 to +24
✅ Keyword sets organized and extensible
✅ More accurate prioritization (test with real notifications)

---

### **Phase 2: Double-Pass Intent Classification System**

**Goal:** Separate intent detection from response generation for cleaner, more accurate classification

#### 2.1 Define New Intent Structure

**3 core intents:**
1. `<notification_query>` - User asks about their notifications
   - Examples: "what's urgent?", "what do I need to know?", "any important messages?"

2. `<notification_rerank>` - User wants to change notification importance
   - Examples: "prioritize Discord", "make emails from John more important", "ignore Instagram"

3. `<chat>` - General conversation, follow-ups, questions
   - Examples: "hi", "how are you?", "what can you do?", "thanks"

#### 2.2 Pass 1: Intent Classification Prompt

**Minimal, focused prompt:**

```kotlin
private fun buildIntentClassificationPrompt(userMessage: String): String {
    return """
You are V, a personal AI assistant for notification management.

Your job: Identify the user's intention and respond with ONLY a JSON object.

User message: "$userMessage"

Classify into ONE of these intents:
1. <notification_query> - User asks about their notifications
   - Examples: "what's urgent?", "show me important messages", "what do I need to know?"

2. <notification_rerank> - User wants to make some notifications more/less important
   - Examples: "prioritize Discord", "make work emails more important", "ignore Instagram", "focus on messages from Sarah"

3. <chat> - User is having a conversation, asking follow-up questions, or chatting casually
   - Examples: "hi", "thanks", "what can you do?", "how does this work?"

IMPORTANT:
- If user greets you (hi, hello, hey) → <chat>
- If user asks about their current priorities ("what are my priorities?") → <chat>
- If user asks about notifications → <notification_query>
- If user wants to change what's important → <notification_rerank>

Respond with ONLY this JSON format (no extra text):
{
  "intent": "<notification_query OR notification_rerank OR chat>",
  "confidence": "<high OR medium OR low>"
}

Examples:

Input: "what's urgent today?"
Output: {"intent": "notification_query", "confidence": "high"}

Input: "prioritize emails from work"
Output: {"intent": "notification_rerank", "confidence": "high"}

Input: "hi there"
Output: {"intent": "chat", "confidence": "high"}

Input: "what are my current priorities?"
Output: {"intent": "chat", "confidence": "high"}

Now classify the user's message:
    """.trimIndent()
}
```

#### 2.3 Pass 2A: Notification Query Synthesis

**When intent = `<notification_query>`:**

```kotlin
private fun buildNotificationSummaryPrompt(
    userMessage: String,
    notifications: List<NotificationData>,
    context: UserContext
): String {
    val notificationList = notifications.take(8).joinToString("\n") { notif ->
        "${notif.app}: ${notif.title} - ${notif.text}"
    }

    return """
You are V, the user's personal AI assistant.

User's priorities: ${context.priorityRules.keywords.joinToString(", ")}
High priority apps: ${context.priorityRules.highPriorityApps.joinToString(", ")}

Recent urgent notifications (sorted by importance):
$notificationList

User asked: "$userMessage"

Provide a helpful, concise summary of the notifications that answers their question.
Focus on what's most urgent and important based on their priorities.

Response:
    """.trimIndent()
}
```

#### 2.4 Pass 2B: Notification Reranking JSON Extraction

**When intent = `<notification_rerank>`:**

```kotlin
private fun buildPriorityUpdatePrompt(userMessage: String, context: UserContext): String {
    return """
You are V, a personal AI assistant.

Current user priorities:
${context.toJson()}

User message: "$userMessage"

Extract what the user wants to prioritize or deprioritize. Respond with ONLY valid JSON:

{
  "changes": {
    "add_high_priority_apps": ["App1", "App2"],
    "add_keywords": ["keyword1", "keyword2"],
    "add_senders": ["email@example.com"],
    "add_contacts": ["Person Name"],
    "add_domains": [".edu", ".gov"],
    "remove_high_priority_apps": [],
    "remove_keywords": []
  },
  "message": "[Your confirmation message to user]"
}

Guidelines:
- App names: Exact app names (Discord, Gmail, Instagram, WhatsApp, etc.)
- Keywords: Important words/phrases user cares about
- Senders: Email addresses (user@example.com)
- Contacts: Person names (John Smith, Sarah)
- Domains: Email domains (.edu, .gov, @company.com)
- If user says "ignore" or "deprioritize", use remove_ fields

Examples:

Input: "prioritize Discord and emails from james deck"
Output: {
  "changes": {
    "add_high_priority_apps": ["Discord"],
    "add_contacts": ["james deck"]
  },
  "message": "Got it! I've prioritized Discord and messages from James Deck."
}

Input: "focus on work emails"
Output: {
  "changes": {
    "add_keywords": ["work"]
  },
  "message": "I'll prioritize work-related emails for you."
}

Input: "stop showing Instagram"
Output: {
  "changes": {
    "remove_high_priority_apps": ["Instagram"]
  },
  "message": "Instagram notifications will be deprioritized."
}

Now extract from the user's message:
    """.trimIndent()
}
```

#### 2.5 Pass 2C: Chat Response

**When intent = `<chat>`:**

```kotlin
private fun buildChatPrompt(userMessage: String, context: UserContext): String {
    return """
You are V, a personal AI assistant for notification management.

What you can do:
- Summarize urgent/important notifications
- Help users prioritize specific apps, people, or topics
- Answer questions about their priorities

Current user priorities:
- Apps: ${context.priorityRules.highPriorityApps.joinToString(", ").ifEmpty { "None set yet" }}
- Keywords: ${context.priorityRules.keywords.joinToString(", ").ifEmpty { "None set yet" }}
- Important contacts: ${context.priorityRules.contacts.joinToString(", ").ifEmpty { "None set yet" }}

User: "$userMessage"

Respond naturally and helpfully:
    """.trimIndent()
}
```

#### 2.6 VerdureAI Process Flow Refactor

**New `processRequest()` method:**

```kotlin
suspend fun processRequest(userMessage: String): String {
    // PASS 1: Classify intent
    val intentPrompt = buildIntentClassificationPrompt(userMessage)
    val intentJson = llmEngine.generateContent(intentPrompt)
    val intent = parseIntent(intentJson)

    // PASS 2: Handle based on intent
    return when (intent) {
        "notification_query" -> {
            // Fetch top 8 priority notifications from heuristic
            val urgentNotifications = notificationTool.getTopPriorityNotifications(limit = 8)

            // Generate summary with LLM
            val summaryPrompt = buildNotificationSummaryPrompt(
                userMessage,
                urgentNotifications,
                contextManager.getContext()
            )
            llmEngine.generateContent(summaryPrompt)
        }

        "notification_rerank" -> {
            // Extract priority changes with LLM
            val updatePrompt = buildPriorityUpdatePrompt(
                userMessage,
                contextManager.getContext()
            )
            val updateJson = llmEngine.generateContent(updatePrompt)
            val changes = parseUpdateChanges(updateJson)

            // Validate and apply changes
            val validChanges = validatePriorityChanges(changes)
            if (validChanges != null) {
                contextManager.applyPriorityChanges(validChanges)
                changes.message
            } else {
                "I couldn't understand which notifications to prioritize. Can you be more specific?"
            }
        }

        "chat" -> {
            // Simple chat response (could use Pass 1 response directly or make new call)
            val chatPrompt = buildChatPrompt(userMessage, contextManager.getContext())
            llmEngine.generateContent(chatPrompt)
        }

        else -> {
            // Fallback
            llmEngine.generateContent("User: $userMessage\n\nV:")
        }
    }
}
```

#### 2.7 JSON Parsing Helpers

```kotlin
private fun parseIntent(json: String): String {
    return try {
        val jsonObj = Json.parseToJsonElement(json).jsonObject
        jsonObj["intent"]?.jsonPrimitive?.content ?: "chat"
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse intent, defaulting to chat", e)
        "chat"
    }
}

private data class UpdateChanges(
    val changes: PriorityChanges,
    val message: String
)

private fun parseUpdateChanges(json: String): UpdateChanges? {
    return try {
        val jsonStart = json.indexOf('{')
        val jsonEnd = json.lastIndexOf('}') + 1
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val jsonString = json.substring(jsonStart, jsonEnd)
            Json.decodeFromString<UpdateChanges>(jsonString)
        } else null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse update changes", e)
        null
    }
}

private fun validatePriorityChanges(changes: UpdateChanges?): PriorityChanges? {
    if (changes == null) return null

    val c = changes.changes

    // Filter out invalid entries
    val validChanges = PriorityChanges(
        add_keywords = c.add_keywords.filter { it.isNotBlank() },
        add_high_priority_apps = c.add_high_priority_apps.filter { it.isNotBlank() },
        add_financial_apps = c.add_financial_apps.filter { it.isNotBlank() },
        add_neutral_apps = c.add_neutral_apps.filter { it.isNotBlank() },
        add_senders = c.add_senders.filter { it.isNotBlank() && it.contains("@") },
        add_contacts = c.add_contacts.filter { it.isNotBlank() },
        add_domains = c.add_domains.filter { it.contains(".") },

        remove_keywords = c.remove_keywords.filter { it.isNotBlank() },
        remove_high_priority_apps = c.remove_high_priority_apps.filter { it.isNotBlank() },
        remove_senders = c.remove_senders.filter { it.isNotBlank() },
        remove_contacts = c.remove_contacts.filter { it.isNotBlank() },
        remove_domains = c.remove_domains.filter { it.contains(".") }
    )

    return validChanges
}
```

#### 2.8 Files to Modify

**Modify:**
- `VerdureApp/app/src/main/java/com/verdure/core/VerdureAI.kt`
  - Refactor `processRequest()` with double-pass logic
  - Add prompt builders: `buildIntentClassificationPrompt()`, `buildNotificationSummaryPrompt()`, `buildPriorityUpdatePrompt()`, `buildChatPrompt()`
  - Add parsers: `parseIntent()`, `parseUpdateChanges()`, `validatePriorityChanges()`

**Modify:**
- `VerdureApp/app/src/main/java/com/verdure/tools/NotificationTool.kt`
  - Add method: `getTopPriorityNotifications(limit: Int): List<NotificationData>`

**Update:**
- `VerdureApp/app/src/main/java/com/verdure/data/PriorityChanges.kt`
  - Add `add_contacts` and `remove_contacts` fields

---

### **Phase 3: Testing & Validation**

#### 3.1 Test Intent Classification

**Test cases:**

1. **Notification Query Detection**
   - "what's urgent?" → `<notification_query>`
   - "show me important messages" → `<notification_query>`
   - "anything I need to know?" → `<notification_query>`

2. **Notification Rerank Detection**
   - "prioritize Discord" → `<notification_rerank>`
   - "make work emails more important" → `<notification_rerank>`
   - "ignore Instagram" → `<notification_rerank>`
   - "focus on messages from Sarah" → `<notification_rerank>`

3. **Chat Detection**
   - "hi" → `<chat>`
   - "hello" → `<chat>`
   - "what can you do?" → `<chat>`
   - "what are my current priorities?" → `<chat>`
   - "thanks" → `<chat>`

4. **Edge Cases**
   - "what are my priorities and also prioritize Discord" → Should detect `<notification_rerank>` (dual intent, but rerank takes precedence)
   - "" (empty message) → `<chat>` (graceful fallback)

#### 3.2 Test Notification Summary

**Test with real notifications:**
- Fetch top 8 from heuristic scoring
- Verify summary is concise and relevant
- Check that user priorities are reflected

#### 3.3 Test Priority Updates

**Test extraction accuracy:**
- "prioritize Discord and Gmail" → Both apps added
- "focus on emails from john@example.com" → Sender added
- "make .edu emails important" → Domain added
- "ignore Snapchat" → App removed from high priority

**Test validation:**
- Empty strings filtered out ✅
- Domains without "." rejected ✅
- Senders without "@" rejected ✅
- Duplicate entries removed ✅

#### 3.4 Performance Testing

**Measure latency:**
- Pass 1 (intent classification): ~1-1.5 seconds
- Pass 2 (notification query): ~2-3 seconds
- Pass 2 (rerank): ~1.5-2 seconds
- Chat (1 pass): ~1-2 seconds

**Total latency:**
- Notification operations: ~3-4.5 seconds (acceptable for quality)
- Chat: ~1-2 seconds (same as before)

---

## Success Criteria

### Phase 1: Robust Heuristic
✅ 15+ scoring factors implemented
✅ Granular scoring (0-5 scales, not binary +2/-1)
✅ Score range: -5 to +24
✅ Keyword sets organized in `ScoringKeywords.kt`
✅ More accurate prioritization verified with device testing

### Phase 2: Double-Pass System
✅ Intent classification separated from response generation
✅ 3 intents clearly distinguished: `<notification_query>`, `<notification_rerank>`, `<chat>`
✅ Greetings correctly classified as `<chat>`
✅ Priority updates correctly classified as `<notification_rerank>`
✅ Notification questions correctly classified as `<notification_query>`

### Phase 3: Integration
✅ Notification summaries use top 8 from heuristic
✅ Priority updates validated and applied correctly
✅ Chat responses remain fast (1 pass)
✅ No crashes on malformed JSON
✅ Changes persist across app restarts

---

## Implementation Order

1. **Phase 1: Robust Heuristic (High Priority)**
   - [ ] Create `ScoringKeywords.kt` with expanded keyword sets
   - [ ] Update `NotificationData.kt` with metadata fields
   - [ ] Refactor `NotificationFilter.calculatePriorityScore()` with 15+ factors
   - [ ] Add scoring helper methods: `scoreByApp()`, `scoreByContent()`, `scoreByTime()`, `scoreBySender()`, `scoreByMetadata()`
   - [ ] Test scoring accuracy with real notifications
   - [ ] Commit and verify build

2. **Phase 2A: Intent Classification (Pass 1)**
   - [ ] Update `VerdureAI.kt` with `buildIntentClassificationPrompt()`
   - [ ] Add `parseIntent()` helper
   - [ ] Test intent classification in isolation
   - [ ] Verify greetings → `<chat>`, queries → `<notification_query>`, updates → `<notification_rerank>`

3. **Phase 2B: Conditional Second Pass**
   - [ ] Add `buildNotificationSummaryPrompt()` for notification queries
   - [ ] Add `buildPriorityUpdatePrompt()` for reranking
   - [ ] Add `buildChatPrompt()` for chat
   - [ ] Implement `processRequest()` with double-pass logic
   - [ ] Add `getTopPriorityNotifications()` to `NotificationTool.kt`

4. **Phase 2C: Validation & Parsing**
   - [ ] Add `parseUpdateChanges()` helper
   - [ ] Add `validatePriorityChanges()` with filtering
   - [ ] Update `PriorityChanges.kt` with `add_contacts`/`remove_contacts`

5. **Phase 3: Testing & Refinement**
   - [ ] Test all 3 intent paths on device
   - [ ] Verify latency is acceptable
   - [ ] Test edge cases and malformed inputs
   - [ ] Document DEVLOG entry with decisions and tradeoffs

6. **Commit & Deploy**
   - [ ] Commit all changes
   - [ ] Push to trigger GitHub Actions build
   - [ ] Download APK and test on Pixel 8A
   - [ ] Update DEVLOG.md with Session 12 entry

---

## Future Enhancements (Next Iteration)

- **User behavior tracking:** Log which notifications user opens, track app importance over time
- **Installed app validation:** Use PackageManager to validate priority updates
- **Adaptive scoring weights:** Let users adjust factor weights (e.g., "time of day matters less to me")
- **Batch notification processing:** Handle more than 8 notifications with batching
- **Context window expansion:** Test higher MAX_TOKENS (4096, 8192) for more notifications per query
- **Conversation memory:** Track recent requests for follow-up context
