package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.library.ProblemLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * ONNX-based embedding service using paraphrase-multilingual-MiniLM-L12-v2
 * This model provides much better multilingual support, especially for Chinese.
 * 
 * Model: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
 * - Supports 50+ languages including Chinese
 * - Output dimension: 384
 * - Optimized for semantic similarity tasks
 */
object OnnxEmbeddingService {
    
    private const val MODEL_PATH = "models/model_qint8_arm64.onnx"
    private const val TOKENIZER_PATH = "tokenizer.json"
    private const val TAG = "OnnxEmbeddingService"
    private const val MAX_SEQUENCE_LENGTH = 128
    
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: BertTokenizer? = null
    
    @Volatile
    private var isInitialized = false
    
    @Volatile
    private var isInitializing = false
    
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Initialize the service asynchronously in a background thread
     * This prevents blocking the UI thread during tokenizer loading
     */
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialized) {
            AppLogger.d(TAG, "OnnxEmbeddingService is already initialized.")
            return@withContext
        }
        
        if (isInitializing) {
            AppLogger.d(TAG, "OnnxEmbeddingService is already initializing, waiting...")
            // Wait for initialization to complete
            while (isInitializing && !isInitialized) {
                delay(100)
            }
            return@withContext
        }
        
        isInitializing = true
        
        try {
            AppLogger.d(TAG, "Starting OnnxEmbeddingService initialization in background thread...")
            
            // 1. Initialize ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // 2. Copy model file to cache
            val modelFile = copyAssetToCache(context, MODEL_PATH)
            
            // 3. Create ONNX session
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setInterOpNumThreads(4)
            sessionOptions.setIntraOpNumThreads(4)
            
            ortSession = ortEnvironment!!.createSession(
                modelFile.absolutePath,
                sessionOptions
            )
            
            // 4. Initialize tokenizer (this is the heavy part - loads large JSON)
            AppLogger.d(TAG, "Loading tokenizer from JSON...")
            val tokenizerFile = copyAssetToCache(context, TOKENIZER_PATH)
            tokenizer = BertTokenizer(tokenizerFile.absolutePath)
            
            isInitialized = true
            AppLogger.d(TAG, "OnnxEmbeddingService initialized successfully with multilingual support.")
            
            // Log model info
            ortSession?.let { session ->
                AppLogger.d(TAG, "Model inputs: ${session.inputNames}")
                AppLogger.d(TAG, "Model outputs: ${session.outputNames}")
            }
            
            // 触发自动分类（使用 ProblemLibrary）
            AppLogger.d(TAG, "准备触发记忆库未分类记忆的自动分类...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppLogger.d(TAG, "开始获取 EnhancedAIService 实例...")
                    val enhancedAIService = EnhancedAIService.getInstance(context)
                    AppLogger.d(TAG, "开始获取 PROBLEM_LIBRARY 功能的 AIService...")
                    val aiService = enhancedAIService.getAIServiceForFunction(com.ai.assistance.operit.data.model.FunctionType.PROBLEM_LIBRARY)
                    AppLogger.d(TAG, "开始调用 ProblemLibrary.autoCategorizeMemoriesAsync...")
                    ProblemLibrary.autoCategorizeMemoriesAsync(context, aiService)
                    AppLogger.d(TAG, "已触发自动分类任务")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "触发自动分类失败", e)
                }
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error initializing OnnxEmbeddingService", e)
            isInitialized = false
        } finally {
            isInitializing = false
        }
    }
    
    private fun copyAssetToCache(context: Context, assetPath: String): File {
        val cacheFile = File(context.cacheDir, assetPath)
        if (!cacheFile.parentFile!!.exists()) {
            cacheFile.parentFile!!.mkdirs()
        }
        
        if (!cacheFile.exists()) {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        
        return cacheFile
    }
    
    fun generateEmbedding(text: String): Embedding? {
        if (!isInitialized || ortSession == null || tokenizer == null) {
            AppLogger.w(TAG, "OnnxEmbeddingService is not initialized")
            return null
        }
        
        if (text.isBlank()) {
            return null
        }
        
        try {
            // 1. Tokenize input text
            val tokens = tokenizer!!.tokenize(text, MAX_SEQUENCE_LENGTH)
            
            // 2. Create ONNX tensors
            val env = ortEnvironment!!
            val inputIds = OnnxTensor.createTensor(env, arrayOf(tokens.inputIds))
            val attentionMask = OnnxTensor.createTensor(env, arrayOf(tokens.attentionMask))
            val tokenTypeIds = OnnxTensor.createTensor(env, arrayOf(tokens.tokenTypeIds))
            
            // 3. Run inference
            val inputs = mapOf(
                "input_ids" to inputIds,
                "attention_mask" to attentionMask,
                "token_type_ids" to tokenTypeIds
            )
            
            val outputs = ortSession!!.run(inputs)
            
            // 4. Extract embedding from output
            // For sentence-transformers models, we need to do mean pooling
            val outputTensor = outputs[0].value as Array<Array<FloatArray>>
            val embedding = meanPooling(
                outputTensor[0],
                tokens.attentionMask
            )
            
            // 5. L2 normalize
            val normalizedEmbedding = l2Normalize(embedding)
            
            // Log preview
            val preview = normalizedEmbedding.take(5).joinToString(", ") { "%.4f".format(it) }
            AppLogger.d(TAG, "Generated embedding for \"$text\": [$preview ...] (dim: ${normalizedEmbedding.size})")
            
            return Embedding(normalizedEmbedding)
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to generate embedding for text: $text", e)
            return null
        }
    }
    
    /**
     * Mean pooling over token embeddings, taking attention mask into account
     */
    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        val embeddingDim = tokenEmbeddings[0].size
        val sumEmbedding = FloatArray(embeddingDim)
        var sumMask = 0f
        
        for (i in tokenEmbeddings.indices) {
            val mask = attentionMask[i].toFloat()
            sumMask += mask
            for (j in 0 until embeddingDim) {
                sumEmbedding[j] += tokenEmbeddings[i][j] * mask
            }
        }
        
        // Average
        for (j in 0 until embeddingDim) {
            sumEmbedding[j] /= sumMask
        }
        
        return sumEmbedding
    }
    
    /**
     * L2 normalize the embedding vector
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = sqrt(norm)
        
        return FloatArray(embedding.size) { i ->
            embedding[i] / norm
        }
    }
    
    /**
     * Calculates the cosine similarity between two embedding objects.
     * Since embeddings are already L2 normalized, this is just the dot product.
     */
    fun cosineSimilarity(emb1: Embedding, emb2: Embedding): Float {
        val vec1 = emb1.vector
        val vec2 = emb2.vector
        
        if (vec1.size != vec2.size) {
            AppLogger.w(TAG, "Embedding dimension mismatch: ${vec1.size} vs ${vec2.size}")
            return 0f
        }
        
        var dotProduct = 0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
        }
        
        return dotProduct
    }
    
    fun cleanup() {
        try {
            // ONNX Runtime objects are managed by the runtime and don't need explicit cleanup
            // Just clear our references
            ortSession = null
            ortEnvironment = null
            tokenizer = null
            isInitialized = false
            AppLogger.d(TAG, "OnnxEmbeddingService cleaned up successfully")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error cleaning up OnnxEmbeddingService", e)
        }
    }
}

/**
 * Tokenization result containing input IDs, attention mask, and token type IDs
 */
data class TokenizationResult(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
)

/**
 * Tokenizer for the multilingual sentence-transformers model
 * Loads vocabulary from tokenizer.json format
 */
class BertTokenizer(tokenizerPath: String) {
    private val vocab: Map<String, Int>
    private val unkTokenId: Int
    private val padTokenId: Int
    private val clsTokenId: Int
    private val sepTokenId: Int
    
    init {
        // Load tokenizer.json and parse vocabulary
        val jsonContent = File(tokenizerPath).readText()
        val jsonObject = JSONObject(jsonContent)
        
        // Parse added_tokens for special tokens
        val addedTokens = jsonObject.getJSONArray("added_tokens")
        val specialTokens = mutableMapOf<String, Int>()
        
        for (i in 0 until addedTokens.length()) {
            val token = addedTokens.getJSONObject(i)
            val content = token.getString("content")
            val id = token.getInt("id")
            specialTokens[content] = id
        }
        
        // Parse model.vocab for the main vocabulary
        val model = jsonObject.getJSONObject("model")
        val vocabArray = model.getJSONArray("vocab")
        
        val vocabMap = mutableMapOf<String, Int>()
        for (i in 0 until vocabArray.length()) {
            val entry = vocabArray.getJSONArray(i)
            val token = entry.getString(0)
            vocabMap[token] = i
        }
        
        // Merge special tokens with main vocabulary
        vocab = vocabMap + specialTokens
        
        // Set special token IDs
        clsTokenId = specialTokens["<s>"] ?: 0
        sepTokenId = specialTokens["</s>"] ?: 2
        padTokenId = specialTokens["<pad>"] ?: 1
        unkTokenId = specialTokens["<unk>"] ?: 3
        
        AppLogger.d("BertTokenizer", "Loaded vocabulary with ${vocab.size} tokens")
    }
    
    fun tokenize(text: String, maxLength: Int): TokenizationResult {
        // Basic preprocessing - split by whitespace
        val words = text.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        // Tokenize using simple greedy approach with ▁ prefix for word boundaries
        val tokenIds = mutableListOf<Int>()
        tokenIds.add(clsTokenId)
        
        for ((index, word) in words.withIndex()) {
            // Add word boundary marker (▁) for first word or after space
            val prefix = if (index == 0 || tokenIds.size > 1) "▁" else ""
            
            // Try to find the word with prefix in vocab
            var currentWord = prefix + word
            var matched = false
            
            // Try exact match first
            if (vocab.containsKey(currentWord)) {
                tokenIds.add(vocab[currentWord]!!)
                matched = true
            } else {
                // Fall back to character-level tokenization for unknown words
                // Add prefix token if exists
                if (prefix.isNotEmpty() && vocab.containsKey(prefix)) {
                    tokenIds.add(vocab[prefix]!!)
                }
                
                // Add each character
                for (char in word) {
                    val charStr = char.toString()
                    tokenIds.add(vocab[charStr] ?: unkTokenId)
                }
            }
            
            if (tokenIds.size >= maxLength - 1) break
        }
        
        tokenIds.add(sepTokenId)
        
        // Convert to arrays with padding
        val inputIds = LongArray(maxLength) { i ->
            if (i < tokenIds.size) tokenIds[i].toLong() else padTokenId.toLong()
        }
        
        val attentionMask = LongArray(maxLength) { i ->
            if (i < tokenIds.size) 1L else 0L
        }
        
        val tokenTypeIds = LongArray(maxLength) { 0L }
        
        return TokenizationResult(inputIds, attentionMask, tokenTypeIds)
    }
}

