package com.verdure.core

/**
 * Interface for any LLM backend (MLC, Gemini, etc.)
 *
 * This abstraction allows Verdure to swap LLM implementations without changing
 * the VerdureAI orchestrator or tools.
 *
 * Current implementation: MLCLLMEngine (Llama 3.2 via MLC LLM)
 * Future: Could support other models (Gemini, Phi-3, etc.)
 */
interface LLMEngine {
    /**
     * Initialize the LLM model
     * Should be called once at app startup
     *
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean

    /**
     * Generate content using the LLM
     * All processing happens on-device - no network required
     *
     * @param prompt The prompt to send to the model
     * @return Generated text response
     */
    suspend fun generateContent(prompt: String): String

    /**
     * Check if the engine is ready to use
     *
     * @return true if initialized and ready, false otherwise
     */
    fun isReady(): Boolean
}
