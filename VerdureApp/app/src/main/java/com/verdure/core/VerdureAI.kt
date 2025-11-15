package com.verdure.core

import com.verdure.tools.Tool

/**
 * Central AI orchestrator for Verdure
 * 
 * This is the "brain" that:
 * 1. Manages all available tools (NotificationTool, DayPlannerTool, etc.)
 * 2. Routes user requests to appropriate tools
 * 3. Synthesizes responses from multiple tools
 * 4. Falls back to direct Gemini Nano conversation when no tool matches
 * 
 * For prototype: Simple keyword-based routing
 * Future: Use Gemini to intelligently select tools based on user intent
 */
class VerdureAI(private val geminiEngine: GeminiNanoEngine) {
    
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
     * 
     * Flow:
     * 1. Analyze user message
     * 2. Determine which tool(s) to use (or none)
     * 3. Execute tool(s)
     * 4. Return synthesized response
     * 
     * @param userMessage The user's input/question
     * @return AI-generated response
     */
    suspend fun processRequest(userMessage: String): String {
        // Simple keyword-based routing for prototype
        // TODO: Later, use Gemini to intelligently select tools
        val lowerMessage = userMessage.lowercase()
        
        return when {
            // Route to notification tool
            "notification" in lowerMessage || 
            "important" in lowerMessage || 
            "urgent" in lowerMessage -> {
                val notificationTool = tools["notification_filter"]
                if (notificationTool != null) {
                    println("🔧 Using NotificationTool")
                    notificationTool.execute(mapOf("query" to userMessage))
                } else {
                    "Notification filtering tool not available. Please check setup."
                }
            }
            
            // Route to day planner (future)
            "plan" in lowerMessage && "day" in lowerMessage -> {
                val plannerTool = tools["day_planner"]
                if (plannerTool != null) {
                    println("🔧 Using DayPlannerTool")
                    plannerTool.execute(mapOf("query" to userMessage))
                } else {
                    "Day planner coming soon!"
                }
            }
            
            // Default: Direct conversation with Gemini Nano
            else -> {
                println("💬 Direct Gemini Nano conversation")
                geminiEngine.generateContent(userMessage)
            }
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
