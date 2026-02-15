package io.hunknownn.urljarvis.application.port.out.persistence

import io.hunknownn.urljarvis.domain.search.SearchResult
import io.hunknownn.urljarvis.domain.url.UrlChunk

interface UrlChunkRepository {
    fun saveAll(chunks: List<UrlChunk>)
    fun deleteByUrlId(urlId: Long)
    fun searchByUserId(userId: Long, queryEmbedding: FloatArray, topK: Int): List<SearchResult>
    fun searchByUrlId(urlId: Long, queryEmbedding: FloatArray, topK: Int): List<SearchResult>
}
