package io.hunknownn.urljarvis.application.service

import io.hunknownn.urljarvis.application.port.`in`.SearchUseCase
import io.hunknownn.urljarvis.application.port.`in`.SearchUseCase.SearchAnswer
import io.hunknownn.urljarvis.application.port.out.embedding.EmbeddingClient
import io.hunknownn.urljarvis.application.port.out.llm.LlmClient
import io.hunknownn.urljarvis.application.port.out.persistence.UrlChunkRepository
import io.hunknownn.urljarvis.domain.search.SearchResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

/**
 * 하이브리드 검색 서비스.
 * 쿼리 임베딩 → 벡터 + 키워드 RRF 하이브리드 검색 → top-K 청크 컨텍스트 구성 → LLM 답변 생성.
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

        val queryEmbedding: FloatArray
        val embedTime = measureTimeMillis {
            queryEmbedding = embeddingClient.embed(query)
        }
        log.info("[임베딩] {}ms", embedTime)

        val results: List<SearchResult>
        val searchTime = measureTimeMillis {
            results = urlChunkRepository.searchByUserId(userId, queryEmbedding, query, topK)
        }
        log.info("[하이브리드 검색] {}ms - {}건 (최고 점수: {})", searchTime, results.size, results.firstOrNull()?.score ?: 0.0)

        return buildAnswer(query, results)
    }

    override fun searchByUrl(userId: Long, urlId: Long, query: String, topK: Int): SearchAnswer {
        log.info("URL 내 검색 시작: userId={}, urlId={}, query='{}', topK={}", userId, urlId, query, topK)

        val queryEmbedding: FloatArray
        val embedTime = measureTimeMillis {
            queryEmbedding = embeddingClient.embed(query)
        }
        log.info("[임베딩] {}ms", embedTime)

        val results: List<SearchResult>
        val searchTime = measureTimeMillis {
            results = urlChunkRepository.searchByUrlId(urlId, queryEmbedding, query, topK)
        }
        log.info("[하이브리드 검색] {}ms - {}건 (최고 점수: {})", searchTime, results.size, results.firstOrNull()?.score ?: 0.0)

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

        val maxScore = results.maxOf { it.score }
        val normalizedResults = if (maxScore > 0) {
            results.map { it.copy(score = it.score / maxScore) }
        } else {
            results
        }

        val context = normalizedResults.groupBy { it.urlId }.entries.mapIndexed { i, (_, chunks) ->
            val first = chunks.first()
            val contents = chunks.mapIndexed { j, c ->
                "${j + 1}. ${c.matchedChunkContent}"
            }.joinToString("\n")
            "[출처 ${i + 1}]\nURL: ${first.url}\n제목: ${first.title ?: "없음"}\n\n$contents"
        }.joinToString("\n\n")

        val answer: String
        val llmTime = measureTimeMillis {
            answer = llmClient.generate(query, context)
        }
        log.info("[LLM] {}ms - answer={}자", llmTime, answer.length)
        log.info("[검색 완료] query='{}', sources={}건", query, normalizedResults.size)

        return SearchAnswer(answer = answer, sources = normalizedResults)
    }
}
