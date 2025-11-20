package com.verdure.data

import kotlinx.serialization.Serializable

/**
 * Structured response from LLM including intent and actions
 *
 * This enables single-pass intent detection where Gemma outputs:
 * 1. The detected intent (update_priorities, analyze_notifications, chat)
 * 2. Any priority changes to apply (if update_priorities)
 * 3. A natural language response to the user
 *
 * Example:
 * ```json
 * {
 *   "intent": "update_priorities",
 *   "changes": {
 *     "add_high_priority_apps": ["Discord"],
 *     "add_senders": ["james.deck@example.com"]
 *   },
 *   "message": "I've prioritized Discord and emails from James Deck."
 * }
 * ```
 */
@Serializable
data class IntentResponse(
    /**
     * The detected user intent
     * - "update_priorities": User wants to change what's important
     * - "analyze_notifications": User asks about their notifications
     * - "chat": General conversation
     */
    val intent: String,

    /**
     * Priority changes to apply (only for update_priorities intent)
     * Null for other intents
     */
    val changes: PriorityChanges? = null,

    /**
     * Natural language response to display to the user
     */
    val message: String
)

/**
 * Delta changes to apply to PriorityRules
 *
 * Uses add/remove lists to make explicit changes without replacing entire context.
 * This is more token-efficient and safer than outputting the full context.
 *
 * Example usage:
 * ```kotlin
 * val changes = PriorityChanges(
 *     add_high_priority_apps = listOf("Discord"),
 *     add_senders = listOf("john@example.com"),
 *     remove_keywords = listOf("old keyword")
 * )
 * ```
 */
@Serializable
data class PriorityChanges(
    /**
     * Keywords to add to priority list
     * Example: ["work", "grad school", "thesis"]
     */
    val add_keywords: List<String> = emptyList(),

    /**
     * Apps to add to high priority category
     * Example: ["Discord", "Slack"]
     */
    val add_high_priority_apps: List<String> = emptyList(),

    /**
     * Apps to add to financial category
     * Example: ["Venmo", "Cash App"]
     */
    val add_financial_apps: List<String> = emptyList(),

    /**
     * Apps to add to neutral category (social media, entertainment)
     * Example: ["Instagram", "TikTok"]
     */
    val add_neutral_apps: List<String> = emptyList(),

    /**
     * Email senders to prioritize
     * Example: ["professor@university.edu", "james deck"]
     */
    val add_senders: List<String> = emptyList(),

    /**
     * Email domains to prioritize
     * Example: [".edu", ".gov"]
     */
    val add_domains: List<String> = emptyList(),

    /**
     * Keywords to remove from priority list
     * Example: ["old keyword"]
     */
    val remove_keywords: List<String> = emptyList(),

    /**
     * Apps to remove from high priority category
     * Example: ["Snapchat"] (user said "ignore Snapchat")
     */
    val remove_high_priority_apps: List<String> = emptyList(),

    /**
     * Senders to remove from priority list
     * Example: ["spam@example.com"]
     */
    val remove_senders: List<String> = emptyList(),

    /**
     * Domains to remove from priority list
     * Example: [".marketing"]
     */
    val remove_domains: List<String> = emptyList()
)
