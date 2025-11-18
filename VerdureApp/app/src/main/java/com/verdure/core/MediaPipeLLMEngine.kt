package com.verdure.core

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaPipe LLM backend for running Gemma 3 1B on-device
 *
 * Model: Gemma 3 1B 4-bit quantized (.task format)
 * Size: ~600-800 MB model file
 * RAM: ~1.5 GB during inference
 * Speed: ~2-4 tokens/sec on Pixel 8A (optimized for efficiency)
 *
 * All inference happens on-device - no cloud, no internet required.
 *
 * Using Google MediaPipe Tasks GenAI library (official Android solution)
 */
class MediaPipeLLMEngine(private val context: Context) : LLMEngine {

    private var llmInference: LlmInference? = null
    private var isInitialized = false

    companion object {
        // Model configuration
        const val MODEL_FILENAME = "gemma-3-1b-q4.task"
        const val MODEL_DEV_PATH = "/data/local/tmp/llm/$MODEL_FILENAME"

        // Inference parameters (MediaPipe defaults)
        const val MAX_TOKENS = 512
        const val TEMPERATURE = 0.8f
        const val TOP_K = 64
        const val RANDOM_SEED = 0
    }

    override suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("🚀 MediaPipeLLMEngine: Initializing...")

                // Check if model exists
                val modelPath = getModelPath()
                if (modelPath == null) {
                    println("❌ Model file not found at: $MODEL_DEV_PATH")
                    println("   Please push the model to your device using:")
                    println("   adb shell mkdir -p /data/local/tmp/llm/")
                    println("   adb push $MODEL_FILENAME /data/local/tmp/llm/")
                    println("")
                    println("   Download Gemma 3 1B 4-bit from:")
                    println("   https://huggingface.co/litert-community/gemma-3-1b-4bit")
                    isInitialized = false
                    return@withContext false
                }

                println("📦 Model file found: $modelPath")

                // Configure LLM inference options
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .setTopK(TOP_K)
                    .setTemperature(TEMPERATURE)
                    .setRandomSeed(RANDOM_SEED)
                    .build()

                println("🔧 Loading Gemma 3 1B model...")

                // Create LlmInference instance
                llmInference = LlmInference.createFromOptions(context, options)

                isInitialized = true
                println("✅ MediaPipeLLMEngine initialized successfully")
                println("   Model: Gemma 3 1B (4-bit quantized)")
                println("   Max tokens: $MAX_TOKENS")
                println("   Temperature: $TEMPERATURE")
                println("   Top-K: $TOP_K")

                true
            } catch (e: Exception) {
                println("❌ Failed to initialize MediaPipeLLMEngine: ${e.message}")
                e.printStackTrace()
                isInitialized = false
                false
            }
        }
    }

    override suspend fun generateContent(prompt: String): String {
        if (!isInitialized || llmInference == null) {
            return "Error: LLM not initialized. Please ensure model file is pushed to device via adb."
        }

        return withContext(Dispatchers.IO) {
            try {
                println("🤖 MediaPipeLLMEngine: Generating response...")
                println("   Prompt: ${prompt.take(100)}...")

                val startTime = System.currentTimeMillis()

                // Generate response synchronously
                val response = llmInference!!.generateResponse(prompt)

                val elapsedTime = System.currentTimeMillis() - startTime
                val tokensGenerated = response.length / 4  // Rough estimate
                val tokensPerSec = if (elapsedTime > 0) (tokensGenerated * 1000.0 / elapsedTime) else 0.0

                println("✅ Response generated in ${elapsedTime}ms (~${String.format("%.1f", tokensPerSec)} tok/s)")
                println("   Response: ${response.take(100)}...")

                response.trim()

            } catch (e: Exception) {
                println("❌ Error generating content: ${e.message}")
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }

    override fun isReady(): Boolean = isInitialized && llmInference != null

    /**
     * Get the model path, checking both development and production locations
     * Development: /data/local/tmp/llm/ (pushed via adb)
     * Production: app cache directory (downloaded at runtime - future implementation)
     */
    private fun getModelPath(): String? {
        // Check development location first (adb push)
        val devFile = File(MODEL_DEV_PATH)
        if (devFile.exists()) {
            return MODEL_DEV_PATH
        }

        // Check app cache (future: runtime download)
        val cacheFile = File(context.cacheDir, MODEL_FILENAME)
        if (cacheFile.exists()) {
            return cacheFile.absolutePath
        }

        // Model not found
        return null
    }

    /**
     * Clean up resources when done
     */
    fun destroy() {
        llmInference?.close()
        llmInference = null
        isInitialized = false
        println("🔄 MediaPipeLLMEngine destroyed")
    }
}
