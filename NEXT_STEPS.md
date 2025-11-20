# Implementation Plan: Single-Pass Intent Detection with Priority Updates

## Current Status

✅ **Multi-factor scoring system implemented**
- 8-factor heuristic scoring (app priority, keywords, temporal, etc.)
- Priority threshold: score >= 2
- Notifications sorted by score before LLM synthesis
- Max realistic score without boosts: ~6-8
- Commit: `1e4db71`

✅ **LLM integration complete**
- MediaPipe Gemma 3 1B (4-bit quantized)
- On-device inference working
- Current routing: Simple keyword matching

## Goal

Enable **conversational priority learning** through single-pass LLM intent detection:
- User: "prioritize Discord and emails from james deck"
- System: Detects intent → Updates rules → Persists to disk → Future notifications scored higher
- **No extra LLM calls** - intent detection + response generation in one pass

## Architecture Decision: Single-Pass Structured Output

**Approach:** Gemma outputs JSON with intent + changes + natural response in ONE call

**Benefits:**
- ✅ No performance penalty (1 LLM call, not 2)
- ✅ Handles any phrasing (not just keyword matching)
- ✅ Supports multiple intents in one message
- ✅ Easy to swap LLM backends (just JSON parsing)

**Tradeoffs:**
- ⚠️ More complex prompting (must train Gemma to output valid JSON)
- ⚠️ Need robust JSON parsing with fallback

---

## Implementation Plan

### **Phase 1: Data Structures** (30 mins)

#### 1.1 Create `IntentResponse.kt`
Define data classes for structured output:

```kotlin
package com.verdure.data

import kotlinx.serialization.Serializable

/**
 * Structured response from LLM including intent and actions
 */
@Serializable
data class IntentResponse(
    val intent: String,  // "update_priorities", "analyze_notifications", "chat"
    val changes: PriorityChanges? = null,  // Only for update_priorities
    val message: String  // Natural language response to user
)

/**
 * Delta changes to apply to PriorityRules
 */
@Serializable
data class PriorityChanges(
    val add_keywords: List<String> = emptyList(),
    val add_high_priority_apps: List<String> = emptyList(),
    val add_financial_apps: List<String> = emptyList(),
    val add_neutral_apps: List<String> = emptyList(),
    val add_senders: List<String> = emptyList(),
    val add_domains: List<String> = emptyList(),

    val remove_keywords: List<String> = emptyList(),
    val remove_high_priority_apps: List<String> = emptyList(),
    val remove_senders: List<String> = emptyList(),
    val remove_domains: List<String> = emptyList()
)
```

**Files to create:**
- `VerdureApp/app/src/main/java/com/verdure/data/IntentResponse.kt`

---

### **Phase 2: Priority Changes Application** (45 mins)

#### 2.1 Add `applyChanges()` to UserContextManager

Add method to apply delta changes to existing context:

```kotlin
/**
 * Apply priority changes (add/remove items from lists)
 */
suspend fun applyPriorityChanges(changes: PriorityChanges): UserContext {
    return updateContext { current ->
        val rules = current.priorityRules

        current.copy(
            priorityRules = rules.copy(
                keywords = (rules.keywords + changes.add_keywords - changes.remove_keywords.toSet()).distinct(),
                highPriorityApps = (rules.highPriorityApps + changes.add_high_priority_apps - changes.remove_high_priority_apps.toSet()).distinct(),
                financialApps = (rules.financialApps + changes.add_financial_apps).distinct(),
                neutralApps = (rules.neutralApps + changes.add_neutral_apps).distinct(),
                senders = (rules.senders + changes.add_senders - changes.remove_senders.toSet()).distinct(),
                domains = (rules.domains + changes.add_domains - changes.remove_domains.toSet()).distinct()
            )
        )
    }
}
```

**Files to modify:**
- `VerdureApp/app/src/main/java/com/verdure/data/UserContextManager.kt`

---

### **Phase 3: VerdureAI Refactor** (60 mins)

#### 3.1 Replace keyword routing with single-pass intent detection

**Current flow:**
```
User message → Keyword matching → Route to Mode A/B/Default → LLM call
```

**New flow:**
```
User message → LLM call (outputs intent + response) → Parse JSON → Route based on intent
```

#### 3.2 Update `processRequest()` method

```kotlin
suspend fun processRequest(userMessage: String): String {
    val contextJson = contextManager.getContextAsJson()

    // Single LLM call with structured output
    val prompt = buildStructuredOutputPrompt(userMessage, contextJson)
    val llmOutput = llmEngine.generateContent(prompt)

    // Parse JSON response
    val intentResponse = parseIntentResponse(llmOutput)

    // Route based on intent
    return when (intentResponse.intent) {
        "update_priorities" -> {
            intentResponse.changes?.let { changes ->
                contextManager.applyPriorityChanges(changes)
            }
            intentResponse.message
        }
        "analyze_notifications" -> {
            // Future: Optionally enhance with actual notification data
            intentResponse.message
        }
        else -> {
            // General conversation
            intentResponse.message
        }
    }
}
```

#### 3.3 Create structured output prompt

```kotlin
private fun buildStructuredOutputPrompt(userMessage: String, contextJson: String): String {
    return """
You are V, an AI assistant for Verdure.

User context (current priorities and settings):
$contextJson

User message: "$userMessage"

Analyze the user's intent and respond with ONLY valid JSON in this format:

{
  "intent": "<one of: update_priorities, analyze_notifications, chat>",
  "changes": {
    "add_keywords": ["keyword1", "keyword2"],
    "add_high_priority_apps": ["App1"],
    "add_senders": ["sender@example.com"],
    "add_domains": [".edu"],
    "remove_keywords": [],
    "remove_high_priority_apps": []
  },
  "message": "Your natural language response to the user"
}

Intent detection rules:
- "update_priorities": User wants to change what's important (prioritize, focus on, ignore, boost, deprioritize)
- "analyze_notifications": User asks about their notifications (what's urgent, what's important, show notifications)
- "chat": Everything else (questions, conversation, asking about current priorities)

Important:
- For "update_priorities": Include ALL changes in the "changes" object
- For "chat" or "analyze_notifications": Set "changes" to null or empty object
- ALWAYS include a helpful "message" field with your natural response
- Output ONLY the JSON object, no extra text before or after

Examples:

User: "prioritize Discord and emails from james deck"
{
  "intent": "update_priorities",
  "changes": {
    "add_high_priority_apps": ["Discord"],
    "add_senders": ["james deck", "james.deck@"]
  },
  "message": "Got it! I've prioritized Discord and emails from James Deck. Future notifications from these sources will be scored higher."
}

User: "what are my priorities today, I should focus more on work emails"
{
  "intent": "update_priorities",
  "changes": {
    "add_keywords": ["work"]
  },
  "message": "Your current priorities include Discord and emails from James Deck. I've also added work emails to your priorities."
}

User: "what's urgent?"
{
  "intent": "analyze_notifications",
  "changes": null,
  "message": "Let me check your urgent notifications..."
}

User: "how are you?"
{
  "intent": "chat",
  "changes": null,
  "message": "I'm doing well, thanks for asking! How can I help you today?"
}

Now respond to the user's message with valid JSON:
    """.trimIndent()
}
```

#### 3.4 Add JSON parser with fallback

```kotlin
private fun parseIntentResponse(llmOutput: String): IntentResponse {
    try {
        // Try to extract JSON from response (handle cases where LLM adds extra text)
        val jsonStart = llmOutput.indexOf('{')
        val jsonEnd = llmOutput.lastIndexOf('}') + 1

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            val jsonString = llmOutput.substring(jsonStart, jsonEnd)
            val json = Json { ignoreUnknownKeys = true }
            return json.decodeFromString<IntentResponse>(jsonString)
        }

        // Fallback: treat as chat
        return IntentResponse(
            intent = "chat",
            changes = null,
            message = llmOutput
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse intent response, treating as chat", e)
        return IntentResponse(
            intent = "chat",
            changes = null,
            message = llmOutput
        )
    }
}
```

**Files to modify:**
- `VerdureApp/app/src/main/java/com/verdure/core/VerdureAI.kt`

---

### **Phase 4: Score Cap Implementation** (15 mins)

#### 4.1 Add score cap to NotificationFilter

Cap maximum score at **12** (double the typical high-priority score of ~6):

```kotlin
fun scoreNotification(notification: NotificationData): Int {
    var score = 0
    val rules = userContext.priorityRules

    // ... existing scoring logic ...

    // Cap score at 12 (prevents over-prioritization)
    return score.coerceAtMost(12)
}
```

**Files to modify:**
- `VerdureApp/app/src/main/java/com/verdure/data/NotificationFilter.kt`

---

### **Phase 5: Testing & Validation** (30 mins)

#### 5.1 Test cases

1. **Update priorities (single intent)**
   - User: "prioritize Discord"
   - Expected: Discord added to highPriorityApps, confirmation message

2. **Update priorities (multiple items)**
   - User: "focus on emails from john@example.com and Slack messages"
   - Expected: Both sender and app added, confirmation message

3. **Multiple intents in one message**
   - User: "what are my priorities? also prioritize Instagram"
   - Expected: Current priorities listed + Instagram added

4. **Remove priorities**
   - User: "stop showing me Snapchat"
   - Expected: Snapchat added to remove list or moved to neutral/low priority

5. **Chat fallback**
   - User: "how are you?"
   - Expected: Natural conversation, no priority changes

6. **Malformed JSON handling**
   - Simulate Gemma outputting invalid JSON
   - Expected: Graceful fallback to treating as chat

#### 5.2 Verification

- ✅ Changes persist across app restarts
- ✅ Future notifications scored correctly with new rules
- ✅ context.json file updated on disk
- ✅ No crashes on malformed JSON
- ✅ Response time acceptable (~2-5 seconds, not doubled)

---

## Success Criteria

✅ User can say "prioritize X" and system learns
✅ Changes persist across app restarts
✅ Works with any phrasing (not just keywords)
✅ Handles multiple intents in one message
✅ No performance penalty (single LLM call)
✅ Graceful fallback on JSON parsing errors
✅ Score capped at 12

---

## Implementation Order

1. ✅ Create `IntentResponse.kt` data classes
2. ✅ Add `applyPriorityChanges()` to UserContextManager
3. ✅ Update VerdureAI.processRequest() with structured output
4. ✅ Add JSON parser with fallback
5. ✅ Add score cap to NotificationFilter
6. ✅ Test all flows
7. ✅ Commit and push to trigger CI/CD build
8. ✅ Test on Pixel 8A device

---

## Future Enhancements (Next Iteration)

- **Custom boost amounts**: Track per-item weights (e.g., Discord = +4, Slack = +2)
- **Undo/history**: Allow users to undo priority changes
- **UI for priority management**: Settings screen to view/edit learned rules
- **Smart notification batching**: Analyze more than 3 notifications per query
- **Notification limit increase**: Test higher MAX_TOKENS to fit more context
