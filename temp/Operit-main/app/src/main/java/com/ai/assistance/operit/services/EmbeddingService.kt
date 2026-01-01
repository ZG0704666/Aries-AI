package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.Embedding
import com.google.mediapipe.tasks.components.containers.Embedding as MediaPipeEmbedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * A singleton service for generating text embeddings using a TFLite model.
 * This service needs to be initialized once with the application context.
 */
object EmbeddingService {

    private const val MODEL_PATH = "models/universal_sentence_encoder.tflite"
    private const val TAG = "EmbeddingService"

    private var textEmbedder: TextEmbedder? = null
    private var isInitialized = false

    fun isInitialized(): Boolean = isInitialized

    fun initialize(context: Context) {
        if (isInitialized) {
            AppLogger.d(TAG, "EmbeddingService is already initialized.")
            return
        }

        try {
            val modelFile = File(context.cacheDir, MODEL_PATH)
            if (!modelFile.parentFile!!.exists()) {
                modelFile.parentFile!!.mkdirs()
            }
            context.assets.open(MODEL_PATH).use { inputStream ->
                FileOutputStream(modelFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            val baseOptions = BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build()

            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .setL2Normalize(true)
                .build()

            textEmbedder = TextEmbedder.createFromOptions(context, options)
            isInitialized = true
            AppLogger.d(TAG, "EmbeddingService initialized successfully with L2 normalization.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error initializing EmbeddingService", e)
        }
    }

    fun generateEmbedding(text: String): Embedding? {
        if (!isInitialized || textEmbedder == null) {
            AppLogger.w(TAG, "EmbeddingService is not initialized, cannot generate embedding.")
            return null
        }
        if (text.isBlank()) {
            return null
        }
        try {
            val embeddingResult = textEmbedder?.embed(text)
            val floatArray = embeddingResult?.embeddingResult()?.embeddings()?.firstOrNull()?.floatEmbedding()
            
            // Log the first few dimensions of the generated embedding for debugging
            floatArray?.let {
                val preview = it.take(5).joinToString(", ") { "%.4f".format(it) }
                AppLogger.d(TAG, "Generated embedding for \"$text\": [$preview ...]")
            }
            
            return floatArray?.let { Embedding(it) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to generate embedding for text: $text", e)
            return null
        }
    }

    /**
     * Calculates the cosine similarity between two embedding objects.
     * @return A value between -1 and 1, where 1 means identical.
     */
    fun cosineSimilarity(emb1: Embedding, emb2: Embedding): Float {
        val vec1 = emb1.vector
        val vec2 = emb2.vector
        if (vec1.size != vec2.size) {
            return 0f
        }
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            normA += vec1[i] * vec1[i]
            normB += vec2[i] * vec2[i]
        }
        if (normA == 0f || normB == 0f) {
            return 0f
        }
        return (dotProduct / (sqrt(normA) * sqrt(normB)))
    }
} 