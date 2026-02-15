package io.hunknownn.urljarvis.domain.url

import java.time.LocalDateTime

data class UrlChunk(
    val id: Long = 0,
    val urlId: Long,
    val content: String,
    val chunkIndex: Int,
    val embedding: FloatArray,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UrlChunk) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
