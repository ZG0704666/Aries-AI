package com.ai.assistance.operit.data.model

import java.io.Serializable

/**
 * A lightweight, serializable reference to a DocumentChunk, used for storing in the vector index.
 * It only contains the ID of the chunk.
 */
data class ChunkReference(val chunkId: Long) : Serializable 