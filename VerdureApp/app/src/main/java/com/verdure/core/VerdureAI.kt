package com.verdure.core

import android.util.Log
import com.verdure.data.UserContextManager
import com.verdure.data.NotificationData
import com.verdure.data.PriorityChanges
import com.verdure.tools.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Central AI orchestrator for Verdure
 *
 * Architecture: Two-pass intent detection system
 *
 * PASS 1: Intent Classification
 * - Minimal prompt: Just classify the intent
 * - Returns: {"intent": "...", "confidence": "..."}
 * - Fast, focused, reliable
 *
 * PASS 2: Intent-Specific Processing
 * - notification_query → Fetch notifications + synthesize
 * - notification_rerank → Extract priority changes + apply
 * - chat → Generate natural response
 *
 * Benefits:
 * - Cleaner separation of concerns
 * - Smaller JSON payloads (more reliable)
 * - Intent classification separate from extraction
 * - Easier to debug and improve
 */
class VerdureAI(
    private val llmEngine: LLMEngine,
    private val contextManager: UserContextManager
) {

    companion object {
        private const val TAG = "VerdureAI"
    }

    // Registry of all available tools
    private val tools = mutableMapOf<String, Tool>()

    // JSON parser for structured output
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Register a tool to be available for use
     */
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name} - ${tool.description}")
    }

    /**
     * Process a user request with two-pass system
     *
     * Pass 1: Classify intent (notification_query, notification_rerank, chat)
     * Pass 2: Handle based on intent with appropriate prompt
     */
    suspend fun processRequest(userMessage: String): String {
        // PASS 1: Intent Classification
        val intentPrompt = buildIntentClassificationPrompt(userMessage)
        val intentJson = llmEngine.generateContent(intentPrompt)
        val intent = parseIntent(intentJson)

        Log.d(TAG, "Detected intent: $intent")

        // PASS 2: Intent-Specific Processing
        return when (intent) {
            "notification_query" -> handleNotificationQuery(userMessage)
            "notification_rerank" -> handleNotificationRerank(userMessage)
            "chat" -> handleChat(userMessage)
            else -> {
                Log.w(TAG, "Unknown intent: $intent, falling back to chat")
                handleChat(userMessage)
            }
        }
    }

    // ----- PASS 1: Intent Classification -----

    /**
     * Build minimal prompt for intent classification only
     */
    private fun buildIntentClassificationPrompt(userMessage: String): String {
        return """
You are V, a personal AI assistant for notification management.

Your job: Identify the user's intention and respond with ONLY a JSON object.

User message: "$userMessage"

Classify into ONE of these intents:
1. notification_query - User asks about their notifications
   - Examples: "what's urgent?", "show me important messages", "what do I need to know?"

2. notification_rerank - User wants to make some notifications more/less important
   - Examples: "prioritize Discord", "make work emails more important", "ignore Instagram", "focus on messages from Sarah"

3. chat - User is having a conversation, asking follow-up questions, or chatting casually
   - Examples: "hi", "thanks", "what can you do?", "how does this work?"

IMPORTANT:
- If user greets you (hi, hello, hey) → chat
- If user asks about their current priorities ("what are my priorities?") → chat
- If user asks about notifications → notification_query
- If user wants to change what's important → notification_rerank

Respond with ONLY this JSON format (no extra text):
{
  "intent": "notification_query OR notification_rerank OR chat",
  "confidence": "high OR medium OR low"
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

    /**
     * Parse intent from Pass 1 JSON response
     */
    private fun parseIntent(json: String): String {
        return try {
            val jsonStart = json.indexOf('{')
            val jsonEnd = json.lastIndexOf('}') + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = json.substring(jsonStart, jsonEnd)
                val jsonObj = this.json.parseToJsonElement(jsonString).jsonObject
                jsonObj["intent"]?.jsonPrimitive?.content ?: "chat"
            } else {
                Log.w(TAG, "No JSON found in intent response, defaulting to chat")
                "chat"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse intent, defaulting to chat", e)
            "chat"
        }
    }

    // ----- PASS 2A: Notification Query -----

    /**
     * Handle notification_query intent
     * Fetches top priority notifications and synthesizes summary
     */
    private suspend fun handleNotificationQuery(userMessage: String): String {
        val notificationTool = tools["notification_filter"]

        if (notificationTool == null) {
            return "I don't have access to your notifications right now."
        }

        // Get top 8 priority notifications from heuristic scoring
        val notificationsResult = notificationTool.execute(
            mapOf("action" to "get_priority", "limit" to 8)
        )

        val context = contextManager.loadContext()

        // Second LLM call to synthesize
        val summaryPrompt = buildNotificationSummaryPrompt(
            userMessage,
            notificationsResult,
            context
        )

        Log.d(TAG, "Synthesizing notification query with second LLM call")
        return llmEngine.generateContent(summaryPrompt)
    }

    /**
     * Build prompt for notification summary (Pass 2A)
     */
    private fun buildNotificationSummaryPrompt(
        userMessage: String,
        notificationList: String,
        context: com.verdure.data.UserContext
    ): String {
        return """
You are V, the user's personal AI assistant.

User's priorities: ${context.priorityRules.keywords.joinToString(", ").ifEmpty { "None set yet" }}
High priority apps: ${context.priorityRules.highPriorityApps.joinToString(", ").ifEmpty { "None set yet" }}

Recent urgent notifications (sorted by importance):
$notificationList

User asked: "$userMessage"

Provide a helpful, concise summary of the notifications that answers their question.
Focus on what's most urgent and important based on their priorities.

Response:
        """.trimIndent()
    }

    // ----- PASS 2B: Notification Rerank -----

    /**
     * Handle notification_rerank intent
     * Extracts priority changes and applies them
     */
    private suspend fun handleNotificationRerank(userMessage: String): String {
        val context = contextManager.loadContext()

        // Second LLM call to extract priority changes
        val updatePrompt = buildPriorityUpdatePrompt(userMessage, context)
        val updateJson = llmEngine.generateContent(updatePrompt)

        Log.d(TAG, "Extracting priority changes with second LLM call")

        val result = parsePriorityUpdateResponse(updateJson)

        if (result != null) {
            // Validate and apply changes
            val validatedChanges = validatePriorityChanges(result.changes)
            contextManager.applyPriorityChanges(validatedChanges)
            return result.message
        } else {
            return "I couldn't understand which notifications to prioritize. Can you be more specific?"
        }
    }

    /**
     * Build prompt for priority update extraction (Pass 2B)
     */
    private fun buildPriorityUpdatePrompt(
        userMessage: String,
        context: com.verdure.data.UserContext
    ): String {
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
    "remove_keywords": [],
    "remove_senders": [],
    "remove_contacts": [],
    "remove_domains": []
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

    @Serializable
    private data class PriorityUpdateResponse(
        val changes: PriorityChanges,
        val message: String
    )

    /**
     * Parse priority update response from Pass 2B
     */
    private fun parsePriorityUpdateResponse(json: String): PriorityUpdateResponse? {
        return try {
            val jsonStart = json.indexOf('{')
            val jsonEnd = json.lastIndexOf('}') + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = json.substring(jsonStart, jsonEnd)
                this.json.decodeFromString<PriorityUpdateResponse>(jsonString)
            } else {
                Log.w(TAG, "No JSON found in priority update response")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse priority update response", e)
            null
        }
    }

    /**
     * Validate priority changes to prevent invalid data
     */
    private fun validatePriorityChanges(changes: PriorityChanges): PriorityChanges {
        return changes.copy(
            // Domains must start with "."
            add_domains = changes.add_domains.filter { it.startsWith(".") },
            remove_domains = changes.remove_domains.filter { it.startsWith(".") },

            // Filter out empty strings
            add_keywords = changes.add_keywords.filter { it.isNotBlank() },
            add_high_priority_apps = changes.add_high_priority_apps.filter { it.isNotBlank() },
            add_contacts = changes.add_contacts.filter { it.isNotBlank() },
            add_senders = changes.add_senders.filter { it.isNotBlank() }
        )
    }

    // ----- PASS 2C: Chat -----

    /**
     * Handle chat intent
     * Simple conversational response
     */
    private suspend fun handleChat(userMessage: String): String {
        val context = contextManager.loadContext()
        val chatPrompt = buildChatPrompt(userMessage, context)

        Log.d(TAG, "Generating chat response with LLM")
        return llmEngine.generateContent(chatPrompt)
    }

    /**
     * Build prompt for chat response (Pass 2C)
     */
    private fun buildChatPrompt(
        userMessage: String,
        context: com.verdure.data.UserContext
    ): String {
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

    // ----- Public API -----

    /**
     * List all available tools (useful for debugging)
     */
    fun getAvailableTools(): List<Tool> = tools.values.toList()

    /**
     * Check if a specific tool is registered
     */
    fun hasTool(toolName: String): Boolean = tools.containsKey(toolName)
}
