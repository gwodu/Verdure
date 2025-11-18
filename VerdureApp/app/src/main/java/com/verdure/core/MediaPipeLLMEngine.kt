package com.verdure.core

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
        const val MODEL_ASSET_PATH = "models/$MODEL_FILENAME"

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
                    println("❌ Model file not found")
                    println("")
                    println("📥 To use Verdure AI, download and push the model:")
                    println("")
                    println("1. Download Gemma 3 1B (555 MB):")
                    println("   wget https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task")
                    println("")
                    println("2. Push to device:")
                    println("   adb shell mkdir -p /data/local/tmp/llm/")
                    println("   adb push Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task /data/local/tmp/llm/gemma-3-1b-q4.task")
                    println("")
                    println("3. Restart Verdure app")
                    println("")
                    isInitialized = false
                    return@withContext false
                }

                println("📦 Model file found: $modelPath")

                // Configure LLM inference options
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
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
     * Get the model path, checking multiple locations:
     * 1. Development: /data/local/tmp/llm/ (pushed via adb)
     * 2. Bundled: APK assets/models/ (single-install convenience)
     * 3. Production: app cache (downloaded at runtime - future)
     */
    private fun getModelPath(): String? {
        // 1. Check development location first (adb push)
        val devFile = File(MODEL_DEV_PATH)
        if (devFile.exists()) {
            println("📱 Using model from adb location: $MODEL_DEV_PATH")
            return MODEL_DEV_PATH
        }

        // 2. Check if model is bundled in APK assets
        try {
            context.assets.open(MODEL_ASSET_PATH).use {
                println("📦 Model found in APK assets, copying to cache...")
                return copyModelFromAssets()
            }
        } catch (e: Exception) {
            // Model not in assets, continue to next option
        }

        // 3. Check app cache (future: runtime download)
        val cacheFile = File(context.cacheDir, MODEL_FILENAME)
        if (cacheFile.exists()) {
            println("💾 Using cached model: ${cacheFile.absolutePath}")
            return cacheFile.absolutePath
        }

        // Model not found anywhere
        return null
    }

    /**
     * Copy model from APK assets to cache directory
     * MediaPipe requires a file path, can't load directly from assets
     */
    private fun copyModelFromAssets(): String {
        val cacheFile = File(context.cacheDir, MODEL_FILENAME)

        // If already copied, return it
        if (cacheFile.exists()) {
            println("✅ Model already in cache: ${cacheFile.absolutePath}")
            return cacheFile.absolutePath
        }

        println("📥 Copying model from assets to cache...")
        val startTime = System.currentTimeMillis()

        // Copy from assets to cache
        context.assets.open(MODEL_ASSET_PATH).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        println("✅ Model copied to cache in ${elapsed}ms (${cacheFile.length() / 1024 / 1024} MB)")
        return cacheFile.absolutePath
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
