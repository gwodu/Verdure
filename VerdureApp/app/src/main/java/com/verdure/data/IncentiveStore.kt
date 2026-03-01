package com.verdure.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Persistent storage for incentives and their matched notifications
 */
class IncentiveStore(context: Context) {
    private val prefs = context.getSharedPreferences("incentives", Context.MODE_PRIVATE)
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    companion object {
        private const val TAG = "IncentiveStore"
        private const val KEY_INCENTIVES = "incentives_list"
        private const val KEY_MATCHES_PREFIX = "matches_"
    }
    
    /**
     * Save a new incentive
     */
    fun saveIncentive(incentive: Incentive) {
        try {
            val incentives = getAllIncentives().toMutableList()
            // Remove old version if exists
            incentives.removeAll { it.id == incentive.id }
            incentives.add(incentive)
            
            val jsonString = json.encodeToString(incentives)
            prefs.edit()
                .putString(KEY_INCENTIVES, jsonString)
                .apply()
            
            Log.d(TAG, "Saved incentive: ${incentive.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save incentive", e)
        }
    }
    
    /**
     * Get all incentives
     */
    fun getAllIncentives(): List<Incentive> {
        return try {
            val jsonString = prefs.getString(KEY_INCENTIVES, null) ?: return emptyList()
            json.decodeFromString<List<Incentive>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load incentives", e)
            emptyList()
        }
    }
    
    /**
     * Get active incentives only
     */
    fun getActiveIncentives(): List<Incentive> {
        return getAllIncentives().filter { it.isActive }
    }
    
    /**
     * Get a specific incentive by ID
     */
    fun getIncentive(id: String): Incentive? {
        return getAllIncentives().find { it.id == id }
    }
    
    /**
     * Delete an incentive and all its matches
     */
    fun deleteIncentive(id: String) {
        try {
            val incentives = getAllIncentives().toMutableList()
            incentives.removeAll { it.id == id }
            
            val jsonString = json.encodeToString(incentives)
            prefs.edit()
                .putString(KEY_INCENTIVES, jsonString)
                .remove("$KEY_MATCHES_PREFIX$id")
                .apply()
            
            Log.d(TAG, "Deleted incentive: $id")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete incentive", e)
        }
    }
    
    /**
     * Toggle incentive active state
     */
    fun toggleIncentiveActive(id: String) {
        try {
            val incentive = getIncentive(id) ?: return
            saveIncentive(incentive.copy(isActive = !incentive.isActive))
            Log.d(TAG, "Toggled incentive active state: $id -> ${!incentive.isActive}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle incentive", e)
        }
    }
    
    /**
     * Save a notification match to an incentive
     */
    fun saveMatch(match: IncentiveMatch) {
        try {
            val matches = getMatches(match.incentiveId).toMutableList()
            // Remove duplicate if exists (same notification)
            matches.removeAll { it.notificationId == match.notificationId }
            matches.add(0, match) // Add to front (most recent first)
            
            val jsonString = json.encodeToString(matches)
            prefs.edit()
                .putString("$KEY_MATCHES_PREFIX${match.incentiveId}", jsonString)
                .apply()
            
            Log.d(TAG, "Saved match for incentive ${match.incentiveId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save match", e)
        }
    }
    
    /**
     * Get all matches for an incentive
     */
    fun getMatches(incentiveId: String): List<IncentiveMatch> {
        return try {
            val jsonString = prefs.getString("$KEY_MATCHES_PREFIX$incentiveId", null) 
                ?: return emptyList()
            json.decodeFromString<List<IncentiveMatch>>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load matches for incentive $incentiveId", e)
            emptyList()
        }
    }
    
    /**
     * Get recent matches across all incentives (for quick overview)
     */
    fun getAllRecentMatches(limit: Int = 20): List<Pair<Incentive, IncentiveMatch>> {
        val results = mutableListOf<Pair<Incentive, IncentiveMatch>>()
        
        getAllIncentives().forEach { incentive ->
            val matches = getMatches(incentive.id)
            matches.forEach { match ->
                results.add(incentive to match)
            }
        }
        
        return results
            .sortedByDescending { it.second.matchedAt }
            .take(limit)
    }
    
    /**
     * Create a new incentive with a generated ID
     */
    fun createIncentive(
        name: String,
        userDescription: String,
        aiSummary: String,
        keywords: List<String>
    ): Incentive {
        val incentive = Incentive(
            id = UUID.randomUUID().toString(),
            name = name,
            userDescription = userDescription,
            aiSummary = aiSummary,
            keywords = keywords,
            createdAt = System.currentTimeMillis(),
            isActive = true
        )
        saveIncentive(incentive)
        return incentive
    }
}
