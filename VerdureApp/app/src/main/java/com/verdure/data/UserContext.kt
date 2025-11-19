package com.verdure.data

import kotlinx.serialization.Serializable

/**
 * User profile containing goals, priorities, and notes
 */
@Serializable
data class UserProfile(
    val name: String = "User",
    val currentGoals: List<String> = emptyList(),
    val activePriorities: List<String> = emptyList(),
    val notes: String = ""
)

/**
 * Priority rules for heuristic notification filtering
 */
@Serializable
data class PriorityRules(
    val keywords: List<String> = listOf(
        "urgent", "deadline", "interview", "important", "asap", "response needed"
    ),
    val apps: List<String> = listOf(
        "Gmail", "Outlook", "Calendar", "Messages"
    ),
    val domains: List<String> = listOf(
        ".edu", ".gov"
    ),
    val senders: List<String> = emptyList()
)

/**
 * Conversation memory for tracking recent interactions
 */
@Serializable
data class ConversationMemory(
    val lastUpdated: String = "",
    val recentRequests: List<String> = emptyList()
)

/**
 * Complete user context including profile, rules, and memory
 * This is loaded by Gemma on every request and can be updated conversationally
 */
@Serializable
data class UserContext(
    val userProfile: UserProfile = UserProfile(),
    val priorityRules: PriorityRules = PriorityRules(),
    val conversationMemory: ConversationMemory = ConversationMemory()
) {
    /**
     * Convert to JSON string for Gemma consumption
     */
    fun toJson(): String {
        return kotlinx.serialization.json.Json.encodeToString(serializer(), this)
    }

    companion object {
        /**
         * Parse from JSON string (used when Gemma updates context)
         */
        fun fromJson(json: String): UserContext {
            return kotlinx.serialization.json.Json.decodeFromString(serializer(), json)
        }
    }
}
