package com.verdure.core

import com.verdure.data.UserContextManager
import com.verdure.data.NotificationData
import com.verdure.tools.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Central AI orchestrator for Verdure
 *
 * This is the "brain" that:
 * 1. Manages all available tools (NotificationTool, DayPlannerTool, etc.)
 * 2. Routes user requests to appropriate tools
 * 3. Synthesizes responses from multiple tools
 * 4. Falls back to direct LLM conversation when no tool matches
 * 5. Maintains user context (goals, priorities, rules)
 * 6. Updates heuristics based on user preferences (Mode A)
 * 7. Analyzes notifications with context (Mode B)
 *
 * For prototype: Simple keyword-based routing
 * Future: Use LLM to intelligently select tools based on user intent
 */
class VerdureAI(
    private val llmEngine: LLMEngine,
    private val contextManager: UserContextManager
) {

    // Registry of all available tools
    private val tools = mutableMapOf<String, Tool>()
    
    /**
     * Register a tool to be available for use
     * Call this during app initialization for each tool you want to enable
     * 
     * Example: verdureAI.registerTool(NotificationTool())
     */
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        println("✅ Registered tool: ${tool.name} - ${tool.description}")
    }
    
    /**
     * Process a user request and return a response
     * Always loads user context before processing
     *
     * Flow:
     * 1. Load user context
     * 2. Analyze user message
     * 3. Determine which tool(s) to use (or none)
     * 4. Execute tool(s)
     * 5. Return synthesized response
     *
     * @param userMessage The user's input/question
     * @return AI-generated response
     */
    suspend fun processRequest(userMessage: String): String {
        // Load current user context
        val context = contextManager.loadContext()
        val contextJson = contextManager.getContextAsJson()

        // Simple keyword-based routing for prototype
        // TODO: Later, use Gemini to intelligently select tools
        val lowerMessage = userMessage.lowercase()

        return when {
            // Mode A: Update priorities/heuristics
            "prioritize" in lowerMessage ||
            "priority" in lowerMessage ||
            "important" in lowerMessage && ("make" in lowerMessage || "set" in lowerMessage) -> {
                println("🔧 Mode A: Updating heuristics")
                updatePriorityRules(userMessage, contextJson)
            }

            // Mode B: Analyze notifications with context
            "notification" in lowerMessage ||
            "urgent" in lowerMessage ||
            "what's" in lowerMessage && ("important" in lowerMessage || "urgent" in lowerMessage) -> {
                val notificationTool = tools["notification_filter"]
                if (notificationTool != null) {
                    println("🔧 Mode B: Analyzing notifications with context")
                    analyzeNotificationsWithContext(userMessage, contextJson, notificationTool)
                } else {
                    "Notification filtering tool not available. Please check setup."
                }
            }

            // Default: Direct conversation with LLM (context included)
            else -> {
                println("💬 Direct LLM conversation with context")
                val prompt = buildPromptWithContext(userMessage, contextJson)
                llmEngine.generateContent(prompt)
            }
        }
    }

    /**
     * Mode A: Update priority rules based on user request
     * Example: "Prioritize emails from professors about grad school"
     * → Gemma updates keywords, domains, and goals in context file
     */
    private suspend fun updatePriorityRules(userMessage: String, contextJson: String): String {
        val prompt = """
You are Verdure, a personal AI assistant that helps prioritize notifications.

The user wants to update their priority preferences. Here is their current context:

$contextJson

User request: "$userMessage"

Analyze their request and update the context JSON to reflect their new priorities.
- Add relevant keywords to priorityRules.keywords
- Add relevant domains to priorityRules.domains (e.g., .edu for universities)
- Add relevant apps to priorityRules.apps
- Update userProfile.currentGoals if they mention a goal
- Update userProfile.activePriorities if relevant

Return ONLY the updated JSON, nothing else. Ensure valid JSON format.
        """.trimIndent()

        val updatedJson = llmEngine.generateContent(prompt)

        // Try to parse and save the updated context
        val result = contextManager.updateFromJson(updatedJson)

        return if (result.isSuccess) {
            "✅ Updated your priority preferences! New rules are active for future notifications."
        } else {
            "I understood your request, but had trouble updating the settings. Please try again."
        }
    }

    /**
     * Mode B: Analyze notifications with user context
     * Example: "What's urgent about grad school?"
     * → Gemma loads context (knows user is applying to grad school)
     * → Searches notifications for relevant + urgent items
     */
    private suspend fun analyzeNotificationsWithContext(
        userMessage: String,
        contextJson: String,
        notificationTool: Tool
    ): String {
        // Get recent notifications from tool
        val notificationsResult = notificationTool.execute(mapOf("action" to "get_all"))

        val prompt = """
You are Verdure, a personal AI assistant.

Here is what you know about the user:

$contextJson

Recent notifications:
$notificationsResult

User question: "$userMessage"

Based on the user's current goals and priorities, analyze the notifications and provide a helpful response.
Focus on what's relevant to their question and current context.
        """.trimIndent()

        return llmEngine.generateContent(prompt)
    }

    /**
     * Build a prompt that includes user context as system knowledge
     */
    private fun buildPromptWithContext(userMessage: String, contextJson: String): String {
        return """
You are Verdure, a personal AI assistant.

Here is what you know about the user:

$contextJson

User: $userMessage

Respond helpfully and naturally.
        """.trimIndent()
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
