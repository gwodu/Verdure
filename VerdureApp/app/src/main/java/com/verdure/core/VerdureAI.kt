package com.verdure.core

import android.util.Log
import com.verdure.data.UserContextManager
import com.verdure.data.NotificationData
import com.verdure.data.IntentResponse
import com.verdure.data.PriorityChanges
import com.verdure.tools.Tool
import kotlinx.serialization.json.Json

/**
 * Central AI orchestrator for Verdure
 *
 * This is the "brain" that:
 * 1. Manages all available tools (NotificationTool, etc.)
 * 2. Uses single-pass LLM intent detection (structured JSON output)
 * 3. Routes based on detected intent (update_priorities, analyze_notifications, chat)
 * 4. Applies priority changes when user teaches the system
 * 5. Maintains user context (goals, priorities, rules)
 *
 * Architecture: Single-pass structured output
 * - One LLM call outputs: intent + changes + message
 * - No performance penalty vs keyword routing
 * - Handles any phrasing, not just specific keywords
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
     * Call this during app initialization for each tool you want to enable
     *
     * Example: verdureAI.registerTool(NotificationTool())
     */
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name} - ${tool.description}")
    }

    /**
     * Process a user request and return a response
     *
     * New architecture: Single-pass intent detection
     * 1. Load user context
     * 2. Build structured output prompt
     * 3. LLM generates JSON with intent + changes + message
     * 4. Parse JSON response
     * 5. Route based on intent and apply changes
     * 6. Return natural language response
     *
     * @param userMessage The user's input/question
     * @return AI-generated response
     */
    suspend fun processRequest(userMessage: String): String {
        val contextJson = contextManager.getContextAsJson()

        // Single LLM call with structured output
        val prompt = buildStructuredOutputPrompt(userMessage, contextJson)
        val llmOutput = llmEngine.generateContent(prompt)

        Log.d(TAG, "LLM output: ${llmOutput.take(200)}...")

        // Parse JSON response
        val intentResponse = parseIntentResponse(llmOutput)

        Log.d(TAG, "Detected intent: ${intentResponse.intent}")

        // Route based on intent
        return when (intentResponse.intent) {
            "update_priorities" -> {
                intentResponse.changes?.let { rawChanges ->
                    // Validate changes before applying
                    val validatedChanges = validatePriorityChanges(rawChanges)
                    Log.d(TAG, "Applying validated priority changes: $validatedChanges")
                    contextManager.applyPriorityChanges(validatedChanges)
                }
                intentResponse.message
            }
            "query_priorities", "analyze_notifications" -> {
                // Hybrid approach: Fetch actual notifications and synthesize
                synthesizeNotifications(userMessage, contextJson)
            }
            else -> {
                // General conversation (chat)
                intentResponse.message
            }
        }
    }

    /**
     * Synthesize notifications for query_priorities and analyze_notifications intents
     *
     * Makes a second LLM call with actual notification data.
     * Only called when user asks about their priorities or notifications.
     */
    private suspend fun synthesizeNotifications(userMessage: String, contextJson: String): String {
        val notificationTool = tools["notification_filter"]

        if (notificationTool == null) {
            return "I don't have access to your notifications right now."
        }

        // Get top priority notifications (limit to 8 to stay within token budget)
        val notificationsResult = notificationTool.execute(mapOf("action" to "get_priority", "limit" to 8))

        // Second LLM call to synthesize
        val synthesisPrompt = """
You are V, a helpful personal assistant.

User context:
$contextJson

Priority notifications:
$notificationsResult

User: "$userMessage"

Provide a helpful summary of their priority notifications. Be concise (1-2 sentences per notification).
        """.trimIndent()

        Log.d(TAG, "Synthesizing notifications with second LLM call")
        return llmEngine.generateContent(synthesisPrompt)
    }

    /**
     * Build structured output prompt for single-pass intent detection
     *
     * Simplified prompt design:
     * - Minimal instructions (simpler = less confusion)
     * - Emphasizes context-awareness
     * - Uses brackets [placeholder] to avoid literal copying
     * - No verbose examples that Gemma might copy
     */
    private fun buildStructuredOutputPrompt(userMessage: String, contextJson: String): String {
        return """
You are V, a helpful context-aware personal assistant with access to your user's information and priorities.

Context:
$contextJson

User: "$userMessage"

Respond with JSON:
{
  "intent": "update_priorities" | "query_priorities" | "analyze_notifications" | "chat",
  "changes": { "add_keywords": [], "add_high_priority_apps": [], "add_senders": [], "add_contacts": [], "add_domains": [], "remove_keywords": [], "remove_high_priority_apps": [], "remove_senders": [], "remove_contacts": [], "remove_domains": [] },
  "message": "[your helpful response]"
}

Intents:
- update_priorities: User wants to ADD/REMOVE priorities (prioritize, focus, ignore, deprioritize)
- query_priorities: User asks WHAT their current priorities are
- analyze_notifications: User asks about THEIR NOTIFICATIONS
- chat: Everything else (greetings, questions, conversation)

Rules:
- add_senders: Email addresses only (user@example.com)
- add_contacts: Person names only (John, Sarah, Mom)
- add_domains: Must start with . (like .edu, .gov)
- NOT update_priorities: "Hi", "How are you", "What are my priorities"

Output only valid JSON, no extra text.
        """.trimIndent()
    }

    /**
     * Validate and sanitize priority changes
     * Rejects nonsensical changes like "Whatsapp" as a domain
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

    /**
     * Parse LLM output into IntentResponse
     *
     * Handles cases where LLM adds extra text around JSON.
     * Falls back to treating response as chat if JSON parsing fails.
     */
    private fun parseIntentResponse(llmOutput: String): IntentResponse {
        try {
            // Try to extract JSON from response (handle cases where LLM adds extra text)
            val jsonStart = llmOutput.indexOf('{')
            val jsonEnd = llmOutput.lastIndexOf('}') + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = llmOutput.substring(jsonStart, jsonEnd)
                return json.decodeFromString<IntentResponse>(jsonString)
            }

            // Fallback: treat as chat
            Log.w(TAG, "No JSON found in LLM output, treating as chat")
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

    /**
     * List all available tools (useful for debugging)
     */
    fun getAvailableTools(): List<Tool> = tools.values.toList()

    /**
     * Check if a specific tool is registered
     */
    fun hasTool(toolName: String): Boolean = tools.containsKey(toolName)
}
