package io.hunknownn.urljarvis.application.service

import io.hunknownn.urljarvis.application.port.out.crawling.WebCrawler
import io.hunknownn.urljarvis.application.port.out.embedding.EmbeddingClient
import io.hunknownn.urljarvis.application.port.out.persistence.UrlChunkRepository
import io.hunknownn.urljarvis.application.port.out.persistence.UrlRepository
import io.hunknownn.urljarvis.domain.url.CrawlStatus
import io.hunknownn.urljarvis.domain.url.UrlChunk
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * URL 크롤링 비동기 파이프라인.
 * Firecrawl(크롤링) → TextChunking(분할) → Embedding(벡터화) → pgvector(저장)
 *
 * CrawlEventListener에 의해 @Async로 호출된다.
 * 실패 시 status를 FAILED로 변경하며, 재시도는 recrawl API로 수동 트리거한다.
 */
@Service
class CrawlPipelineService(
    private val urlRepository: UrlRepository,
    private val urlChunkRepository: UrlChunkRepository,
    private val webCrawler: WebCrawler,
    private val textChunkingService: TextChunkingService,
    private val embeddingClient: EmbeddingClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executePipeline(urlId: Long) {
        val url = urlRepository.findById(urlId) ?: run {
            log.warn("URL not found for crawl pipeline: {}", urlId)
            return
        }

        try {
            urlRepository.updateStatus(urlId, CrawlStatus.CRAWLING)

            // 1. 크롤링
            log.info("Crawling URL: {}", url.url)
            val crawlResult = webCrawler.crawl(url.url)

            // 2. 메타데이터 업데이트
            urlRepository.save(
                url.copy(
                    title = crawlResult.title,
                    description = crawlResult.description,
                    status = CrawlStatus.CRAWLING
                )
            )

            // 3. 텍스트 청킹
            val textChunks = textChunkingService.chunk(crawlResult.markdown)
            if (textChunks.isEmpty()) {
                log.warn("No content chunks for URL: {}", url.url)
                urlRepository.updateStatus(urlId, CrawlStatus.CRAWLED)
                return
            }

            // 4. 임베딩 (10개씩 배치 처리하여 타임아웃 방지)
            // e5 모델 규칙: 검색 대상 문서는 "passage: " prefix, 질의는 "query: " prefix
            val prefixedChunks = textChunks.map { "passage: $it" }
            val embeddings = prefixedChunks.chunked(10).flatMap { batch ->
                embeddingClient.embedBatch(batch)
            }

            // 5. 벡터 저장
            val urlChunks = textChunks.mapIndexed { index, content ->
                UrlChunk(
                    urlId = urlId,
                    content = content,
                    chunkIndex = index,
                    embedding = embeddings[index]
                )
            }
            urlChunkRepository.saveAll(urlChunks)

            urlRepository.updateStatus(urlId, CrawlStatus.CRAWLED)
            log.info("Crawl pipeline completed for URL: {} ({} chunks)", url.url, urlChunks.size)

        } catch (e: Exception) {
            log.error("Crawl pipeline failed for URL: {}", url.url, e)
            urlRepository.updateStatus(urlId, CrawlStatus.FAILED)
        }
    }
}
