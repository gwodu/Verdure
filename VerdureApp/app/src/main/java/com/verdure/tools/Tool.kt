package com.verdure.tools

/**
 * Base interface for all Verdure tools
 * Tools are specialized AI capabilities that can be invoked by VerdureAI
 * 
 * Example tools:
 * - NotificationTool: Filters and prioritizes notifications
 * - DayPlannerTool: Plans your day based on calendar/tasks
 * - EmailTool: Drafts or summarizes emails
 */
interface Tool {
    /** Unique identifier for this tool */
    val name: String
    
    /** Description of what this tool does (helps AI decide when to use it) */
    val description: String
    
    /**
     * Execute this tool with given parameters
     * @param params Map of parameter name to value
     * @return Result as a string (can be JSON, plain text, etc.)
     */
    suspend fun execute(params: Map<String, Any>): String
}
