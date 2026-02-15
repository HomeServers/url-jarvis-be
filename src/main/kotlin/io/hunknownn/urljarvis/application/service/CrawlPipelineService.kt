package io.hunknownn.urljarvis.application.service

import io.hunknownn.urljarvis.application.port.out.crawling.WebCrawler
import io.hunknownn.urljarvis.application.port.out.embedding.EmbeddingClient
import io.hunknownn.urljarvis.application.port.out.persistence.UrlChunkRepository
import io.hunknownn.urljarvis.application.port.out.persistence.UrlRepository
import io.hunknownn.urljarvis.domain.url.CrawlStatus
import io.hunknownn.urljarvis.domain.url.UrlChunk
import io.hunknownn.urljarvis.infrastructure.config.CrawlDebugProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.measureTimeMillis

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
    private val embeddingClient: EmbeddingClient,
    private val crawlDebugProperties: CrawlDebugProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executePipeline(urlId: Long) {
        val url = urlRepository.findById(urlId) ?: run {
            log.warn("URL not found for crawl pipeline: {}", urlId)
            return
        }

        val totalTime = measureTimeMillis {
            try {
                urlRepository.updateStatus(urlId, CrawlStatus.CRAWLING)

                // 1. 크롤링
                val crawlResult: io.hunknownn.urljarvis.application.port.out.crawling.CrawlResult
                val crawlTime = measureTimeMillis {
                    crawlResult = webCrawler.crawl(url.url)
                }
                log.info("[크롤링] {}ms - {} (markdown={}자)", crawlTime, url.url, crawlResult.markdown.length)

                // 1-1. 디버그: 크롤링 마크다운 파일 저장
                if (crawlDebugProperties.saveMarkdown) {
                    saveMarkdownForDebug(urlId, url.url, crawlResult.markdown)
                }

                // 2. 메타데이터 업데이트
                urlRepository.save(
                    url.copy(
                        title = crawlResult.title,
                        description = crawlResult.description,
                        thumbnail = crawlResult.ogImage,
                        status = CrawlStatus.CRAWLING
                    )
                )

                // 3. 텍스트 청킹
                val textChunks: List<String>
                val chunkTime = measureTimeMillis {
                    textChunks = textChunkingService.chunk(crawlResult.markdown)
                }
                log.info("[청킹] {}ms - {}건", chunkTime, textChunks.size)

                if (textChunks.isEmpty()) {
                    log.warn("No content chunks for URL: {}", url.url)
                    urlRepository.updateStatus(urlId, CrawlStatus.CRAWLED)
                    return
                }

                // 4. 임베딩 (10개씩 배치 처리하여 타임아웃 방지)
                val embeddings: List<FloatArray>
                val embedTime = measureTimeMillis {
                    val batches = textChunks.chunked(10)
                    val totalBatches = batches.size
                    embeddings = batches.flatMapIndexed { index, batch ->
                        embeddingClient.embedBatch(batch, batchIndex = index + 1, totalBatches = totalBatches)
                    }
                }
                log.info("[임베딩] {}ms - {}건 ({}차원)", embedTime, embeddings.size, embeddings.firstOrNull()?.size ?: 0)

                // 5. 벡터 저장
                val saveTime = measureTimeMillis {
                    val urlChunks = textChunks.mapIndexed { index, content ->
                        UrlChunk(
                            urlId = urlId,
                            content = content,
                            chunkIndex = index,
                            embedding = embeddings[index]
                        )
                    }
                    urlChunkRepository.saveAll(urlChunks)
                }
                log.info("[DB 저장] {}ms - {}건", saveTime, textChunks.size)

                urlRepository.updateStatus(urlId, CrawlStatus.CRAWLED)

            } catch (e: Exception) {
                log.error("Crawl pipeline failed for URL: {}", url.url, e)
                urlRepository.updateStatus(urlId, CrawlStatus.FAILED)
            }
        }
        log.info("[파이프라인 총합] {}ms - {}", totalTime, url.url)
    }

    private fun saveMarkdownForDebug(urlId: Long, originalUrl: String, markdown: String) {
        try {
            val dir = Path.of(crawlDebugProperties.outputDir)
            Files.createDirectories(dir)
            val safeName = originalUrl.replace(Regex("[^a-zA-Z0-9가-힣]"), "_").take(80)
            val file = dir.resolve("${urlId}_${safeName}.md")
            Files.writeString(file, markdown)
            log.info("[디버그] 마크다운 저장: {}", file)
        } catch (e: Exception) {
            log.warn("마크다운 디버그 저장 실패: {}", e.message)
        }
    }
}
