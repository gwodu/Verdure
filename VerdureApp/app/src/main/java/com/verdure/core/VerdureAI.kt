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
                intentResponse.changes?.let { changes ->
                    Log.d(TAG, "Applying priority changes: $changes")
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

    /**
     * Build structured output prompt for single-pass intent detection
     *
     * Instructs Gemma to output JSON with:
     * - intent: update_priorities, analyze_notifications, or chat
     * - changes: delta changes to apply (if update_priorities)
     * - message: natural language response
     */
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
- For "chat" or "analyze_notifications": Set "changes" to null
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
  "message": "Your current priorities include ${contextJson.take(50)}... I've also added work emails to your priorities."
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
