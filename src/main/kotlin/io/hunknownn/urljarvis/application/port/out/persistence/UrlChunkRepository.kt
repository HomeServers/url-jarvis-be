package io.hunknownn.urljarvis.application.port.out.persistence

import io.hunknownn.urljarvis.domain.search.SearchResult
import io.hunknownn.urljarvis.domain.url.UrlChunk

/**
 * URL 청크 + 벡터 저장/검색 Output Port.
 * 벡터 유사도 + 키워드 매칭을 RRF로 결합한 하이브리드 검색을 수행한다.
 */
interface UrlChunkRepository {
    fun saveAll(chunks: List<UrlChunk>)
    fun deleteByUrlId(urlId: Long)
    /** 사용자의 전체 URL을 대상으로 하이브리드 검색 */
    fun searchByUserId(userId: Long, queryEmbedding: FloatArray, query: String, topK: Int): List<SearchResult>
    /** 특정 URL 내에서 하이브리드 검색 */
    fun searchByUrlId(urlId: Long, queryEmbedding: FloatArray, query: String, topK: Int): List<SearchResult>
}
