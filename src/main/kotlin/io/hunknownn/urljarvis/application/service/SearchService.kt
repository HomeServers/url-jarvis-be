package io.hunknownn.urljarvis.application.service

import io.hunknownn.urljarvis.application.port.`in`.SearchUseCase
import io.hunknownn.urljarvis.application.port.`in`.SearchUseCase.SearchAnswer
import io.hunknownn.urljarvis.application.port.out.embedding.EmbeddingClient
import io.hunknownn.urljarvis.application.port.out.llm.LlmClient
import io.hunknownn.urljarvis.application.port.out.persistence.UrlChunkRepository
import io.hunknownn.urljarvis.domain.search.SearchResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 시맨틱 검색 서비스.
 * 쿼리 임베딩 → pgvector 유사도 검색 → top-K 청크 컨텍스트 구성 → LLM 답변 생성.
 */
@Service
class SearchService(
    private val embeddingClient: EmbeddingClient,
    private val urlChunkRepository: UrlChunkRepository,
    private val llmClient: LlmClient
) : SearchUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchAll(userId: Long, query: String, topK: Int): SearchAnswer {
        log.info("전체 검색 시작: userId={}, query='{}', topK={}", userId, query, topK)
        val queryEmbedding = embeddingClient.embed("query: $query")
        val results = urlChunkRepository.searchByUserId(userId, queryEmbedding, topK)
        log.info("벡터 검색 결과: {}건 (최고 유사도: {})", results.size, results.firstOrNull()?.similarity ?: 0.0)
        return buildAnswer(query, results)
    }

    override fun searchByUrl(userId: Long, urlId: Long, query: String, topK: Int): SearchAnswer {
        log.info("URL 내 검색 시작: userId={}, urlId={}, query='{}', topK={}", userId, urlId, query, topK)
        val queryEmbedding = embeddingClient.embed("query: $query")
        val results = urlChunkRepository.searchByUrlId(urlId, queryEmbedding, topK)
        log.info("벡터 검색 결과: {}건 (최고 유사도: {})", results.size, results.firstOrNull()?.similarity ?: 0.0)
        return buildAnswer(query, results)
    }

    private fun buildAnswer(query: String, results: List<SearchResult>): SearchAnswer {
        if (results.isEmpty()) {
            log.warn("검색 결과 없음: query='{}'", query)
            return SearchAnswer(
                answer = "관련된 정보를 찾을 수 없습니다.",
                sources = emptyList()
            )
        }

        val context = results.mapIndexed { i, r ->
            "[출처 ${i + 1}: ${r.title ?: r.url}]\n${r.matchedChunkContent}"
        }.joinToString("\n\n")

        val answer = llmClient.generate(query, context)
        log.info("검색 완료: query='{}', sources={}건, answer={}자", query, results.size, answer.length)
        return SearchAnswer(answer = answer, sources = results)
    }
}
