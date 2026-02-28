package com.verdure.core

import android.content.Context
import com.cactus.CactusCompletionParams
import com.cactus.CactusContextInitializer
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.ChatMessage
import com.cactus.InferenceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cactus LLM backend for running Qwen 3 0.6B on-device.
 *
 * Uses Cactus SDK for local model download and inference.
 */
class CactusLLMEngine private constructor(private val context: Context) : LLMEngine {

    private var cactusLM: CactusLM? = null
    private var isInitialized = false

    companion object {
        private const val MODEL_SLUG = "qwen3-0.6"
        private const val CONTEXT_SIZE = 2048
        private const val MAX_TOKENS = 2048
        private const val TEMPERATURE = 0.8
        private const val TOP_K = 64

        @Volatile
        private var instance: CactusLLMEngine? = null

        fun getInstance(context: Context): CactusLLMEngine {
            return instance ?: synchronized(this) {
                instance ?: CactusLLMEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    override suspend fun initialize(): Boolean {
        if (isInitialized && cactusLM?.isLoaded() == true) {
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                // Ensure Cactus context is initialized
                CactusContextInitializer.initialize(context)

                val lm = CactusLM()

                // Download and initialize the model (throws exception on failure)
                lm.downloadModel(MODEL_SLUG)
                lm.initializeModel(
                    CactusInitParams(
                        model = MODEL_SLUG,
                        contextSize = CONTEXT_SIZE
                    )
                )

                cactusLM = lm
                isInitialized = true
                true
            } catch (e: Exception) {
                e.printStackTrace()
                isInitialized = false
                false
            }
        }
    }

    override suspend fun generateContent(prompt: String): String {
        val lm = cactusLM
        if (!isInitialized || lm == null || !lm.isLoaded()) {
            return "Error: LLM not initialized. Please allow model download and retry."
        }

        return withContext(Dispatchers.IO) {
            try {
                val result = lm.generateCompletion(
                    messages = listOf(
                        ChatMessage(content = prompt, role = "user")
                    ),
                    params = CactusCompletionParams(
                        maxTokens = MAX_TOKENS,
                        temperature = TEMPERATURE,
                        topK = TOP_K,
                        mode = InferenceMode.LOCAL
                    )
                )

                if (result?.success == true) {
                    result.response?.trim().orEmpty()
                } else {
                    "Error: LLM generation failed"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }

    override fun isReady(): Boolean = isInitialized && cactusLM?.isLoaded() == true

    fun unload() {
        cactusLM?.unload()
        cactusLM = null
        isInitialized = false
    }
}
