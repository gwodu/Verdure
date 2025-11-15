package com.verdure.core

import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wrapper around Google's Gemini Nano for on-device AI via AICore
 * 
 * This engine handles:
 * 1. Initialization of Gemini Nano model through AICore
 * 2. Content generation requests
 * 3. Error handling and graceful fallbacks
 * 
 * All processing happens ON-DEVICE - no cloud, no API calls, privacy-first!
 */
class GeminiNanoEngine(private val context: Context) {
    
    private var model: GenerativeModel? = null
    private var isInitialized = false
    
    /**
     * Initialize the Gemini Nano model via AICore
     * Should be called once at app startup
     * 
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Configure the model parameters
                val config = generationConfig {
                    context = this@GeminiNanoEngine.context
                    temperature = 0.7f      // Balance between creativity and consistency
                    topK = 16               // Consider top 16 tokens for diversity
                    maxOutputTokens = 512   // Max response length
                }
                
                // Create the model instance (AICore manages the actual model)
                model = GenerativeModel(
                    generationConfig = config
                )
                
                isInitialized = true
                println("✅ Gemini Nano initialized successfully (on-device)")
                true
            } catch (e: Exception) {
                println("❌ Failed to initialize Gemini Nano: ${e.message}")
                isInitialized = false
                false
            }
        }
    }
    
    /**
     * Generate content using Gemini Nano
     * All processing happens on-device - no network required!
     * 
     * @param prompt The prompt to send to the model
     * @return Generated text response
     */
    suspend fun generateContent(prompt: String): String {
        if (!isInitialized) {
            return "Error: Gemini Nano not initialized. Please enable AICore in Developer Options."
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val response = model?.generateContent(prompt)
                response?.text ?: "No response from model"
            } catch (e: Exception) {
                "Error generating content: ${e.message}"
            }
        }
    }
    
    /**
     * Check if the engine is ready to use
     */
    fun isReady(): Boolean = isInitialized
}
