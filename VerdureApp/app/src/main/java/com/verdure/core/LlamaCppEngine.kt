package com.verdure.core

import android.content.Context
import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * llama.cpp backend for running Llama 3.2 on-device
 *
 * Model: Llama-3.2-1B-Instruct-Q4_K_M.gguf (quantized)
 * Size: ~700 MB model file
 * RAM: ~1 GB during inference
 * Speed: ~8-12 tokens/sec on Pixel 8A (CPU), 15+ with GPU
 *
 * All inference happens on-device - no cloud, no internet required.
 *
 * Using de.kherud:llama Java bindings for llama.cpp
 */
class LlamaCppEngine(private val context: Context) : LLMEngine {

    private var model: LlamaModel? = null
    private var isInitialized = false

    companion object {
        // Model configuration
        const val MODEL_FILENAME = "Llama-3.2-1B-Instruct-Q4_K_M.gguf"
        const val MODEL_ASSET_PATH = "models/$MODEL_FILENAME"

        // Inference parameters
        const val MAX_TOKENS = 512
        const val TEMPERATURE = 0.7f
        const val TOP_P = 0.9f
        const val TOP_K = 40

        // GPU acceleration (Vulkan/OpenCL)
        const val GPU_LAYERS = 32  // Number of layers to offload to GPU (0 = CPU only)
    }

    override suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("🚀 LlamaCppEngine: Initializing...")

                // Copy model from assets to cache directory
                val modelFile = copyModelFromAssets()
                if (modelFile == null || !modelFile.exists()) {
                    println("❌ Model file not found: $MODEL_ASSET_PATH")
                    println("   Please download Llama-3.2-1B-Instruct-Q4_K_M.gguf")
                    println("   from https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF")
                    println("   and place it in app/src/main/assets/models/")
                    isInitialized = false
                    return@withContext false
                }

                println("📦 Model file found: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024} MB)")

                // Configure model parameters
                val modelParams = ModelParameters()
                    .setNGpuLayers(GPU_LAYERS)  // Enable GPU acceleration
                    .setModelFilePath(modelFile.absolutePath)

                println("🔧 Loading model with $GPU_LAYERS GPU layers...")

                // Load the model
                model = LlamaModel(modelParams)

                isInitialized = true
                println("✅ LlamaCppEngine initialized successfully")
                println("   Model: $MODEL_FILENAME")
                println("   GPU layers: $GPU_LAYERS")
                println("   Max tokens: $MAX_TOKENS")

                true
            } catch (e: Exception) {
                println("❌ Failed to initialize LlamaCppEngine: ${e.message}")
                e.printStackTrace()
                isInitialized = false
                false
            }
        }
    }

    override suspend fun generateContent(prompt: String): String {
        if (!isInitialized || model == null) {
            return "Error: LLM not initialized. Please ensure model file is in assets/models/"
        }

        return withContext(Dispatchers.IO) {
            try {
                println("🤖 LlamaCppEngine: Generating response...")
                println("   Prompt: ${prompt.take(100)}...")

                // Format prompt for Llama 3.2 Instruct
                val formattedPrompt = formatInstructPrompt(prompt)

                // Configure inference parameters
                val inferParams = InferenceParameters(formattedPrompt)
                    .setTemperature(TEMPERATURE)
                    .setTopP(TOP_P)
                    .setTopK(TOP_K)
                    .setNPredict(MAX_TOKENS)

                // Generate text
                val startTime = System.currentTimeMillis()
                val response = StringBuilder()

                for (output in model!!.generate(inferParams)) {
                    response.append(output)
                }

                val elapsedTime = System.currentTimeMillis() - startTime
                val tokensGenerated = response.length / 4  // Rough estimate
                val tokensPerSec = if (elapsedTime > 0) (tokensGenerated * 1000.0 / elapsedTime) else 0.0

                println("✅ Response generated in ${elapsedTime}ms (~${String.format("%.1f", tokensPerSec)} tok/s)")
                println("   Response: ${response.take(100)}...")

                response.toString().trim()

            } catch (e: Exception) {
                println("❌ Error generating content: ${e.message}")
                e.printStackTrace()
                "Error: ${e.message}"
            }
        }
    }

    override fun isReady(): Boolean = isInitialized && model != null

    /**
     * Format prompt for Llama 3.2 Instruct template
     * Format: <|begin_of_text|><|start_header_id|>user<|end_header_id|>\n{prompt}<|eot_id|><|start_header_id|>assistant<|end_header_id|>
     */
    private fun formatInstructPrompt(userMessage: String): String {
        return "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n" +
               "$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
    }

    /**
     * Copy model from assets to cache directory
     * llama.cpp requires a file path, can't load directly from assets
     */
    private fun copyModelFromAssets(): File? {
        try {
            val cacheDir = context.cacheDir
            val modelFile = File(cacheDir, MODEL_FILENAME)

            // If already copied, return it
            if (modelFile.exists()) {
                println("📁 Model already in cache: ${modelFile.absolutePath}")
                return modelFile
            }

            println("📥 Copying model from assets to cache...")

            // Copy from assets to cache
            context.assets.open(MODEL_ASSET_PATH).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }

            println("✅ Model copied to cache (${modelFile.length() / 1024 / 1024} MB)")
            return modelFile

        } catch (e: Exception) {
            println("❌ Failed to copy model from assets: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Clean up resources when done
     */
    fun destroy() {
        model?.close()
        model = null
        isInitialized = false
        println("🔄 LlamaCppEngine destroyed")
    }
}
