package com.verdure.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Manages reading and writing user context to/from JSON file
 * Thread-safe singleton for app-wide access
 */
class UserContextManager private constructor(private val context: Context) {

    private val mutex = Mutex()
    private val contextFile: File = File(context.filesDir, CONTEXT_FILENAME)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Load user context from file, creating default if doesn't exist
     */
    suspend fun loadContext(): UserContext = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!contextFile.exists()) {
                // Initialize with default context
                val defaultContext = UserContext()
                saveContextInternal(defaultContext)
                return@withContext defaultContext
            }

            try {
                val jsonString = contextFile.readText()
                json.decodeFromString<UserContext>(jsonString)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse context file, using default", e)
                UserContext() // Return default on parse error
            }
        }
    }

    /**
     * Save user context to file
     */
    suspend fun saveContext(userContext: UserContext) = withContext(Dispatchers.IO) {
        mutex.withLock {
            saveContextInternal(userContext)
        }
    }

    /**
     * Update context with a transformation function
     * Example: updateContext { it.copy(userProfile = it.userProfile.copy(name = "Alice")) }
     */
    suspend fun updateContext(transform: (UserContext) -> UserContext): UserContext {
        return mutex.withLock {
            val current = loadContextInternal()
            val updated = transform(current).let {
                // Auto-update lastUpdated timestamp
                it.copy(
                    conversationMemory = it.conversationMemory.copy(
                        lastUpdated = Instant.now().toString()
                    )
                )
            }
            saveContextInternal(updated)
            updated
        }
    }

    /**
     * Get context as JSON string for Gemma consumption
     */
    suspend fun getContextAsJson(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            json.encodeToString(UserContext.serializer(), loadContextInternal())
        }
    }

    /**
     * Update context from JSON string (used when Gemma modifies context)
     */
    suspend fun updateFromJson(jsonString: String): Result<UserContext> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val newContext = json.decodeFromString<UserContext>(jsonString)
                saveContextInternal(newContext)
                Result.success(newContext)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse updated context from JSON", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Apply priority changes (add/remove items from lists)
     *
     * This is the delta-based approach for updating priorities.
     * Instead of replacing the entire context, we apply specific changes.
     *
     * @param changes The delta changes to apply
     * @return The updated UserContext
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
                    domains = (rules.domains + changes.add_domains - changes.remove_domains.toSet()).distinct(),
                    contacts = (rules.contacts + changes.add_contacts - changes.remove_contacts.toSet()).distinct()
                )
            )
        }
    }

    // Internal helpers (must be called within mutex lock)

    private fun loadContextInternal(): UserContext {
        return if (contextFile.exists()) {
            try {
                val jsonString = contextFile.readText()
                json.decodeFromString<UserContext>(jsonString)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to parse context file", e)
                UserContext()
            }
        } else {
            UserContext()
        }
    }

    private fun saveContextInternal(userContext: UserContext) {
        try {
            val jsonString = json.encodeToString(UserContext.serializer(), userContext)
            contextFile.writeText(jsonString)
            android.util.Log.d(TAG, "Context saved: ${contextFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save context file", e)
        }
    }

    companion object {
        private const val TAG = "UserContextManager"
        private const val CONTEXT_FILENAME = "user_context.json"

        @Volatile
        private var instance: UserContextManager? = null

        /**
         * Get singleton instance
         */
        fun getInstance(context: Context): UserContextManager {
            return instance ?: synchronized(this) {
                instance ?: UserContextManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
