package io.hunknownn.urljarvis.adapter.out.crawling

import io.hunknownn.urljarvis.application.port.out.crawling.CrawlResult
import io.hunknownn.urljarvis.application.port.out.crawling.WebCrawler
import io.hunknownn.urljarvis.infrastructure.config.FirecrawlProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class FirecrawlAdapter(
    private val webClient: WebClient,
    private val firecrawlProperties: FirecrawlProperties
) : WebCrawler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    override fun crawl(url: String): CrawlResult {
        log.info("Firecrawl 요청 시작: {}", url)

        val response = webClient.post()
            .uri("${firecrawlProperties.baseUrl}/scrape")
            .header("Authorization", "Bearer ${firecrawlProperties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "url" to url,
                    "formats" to listOf("markdown"),
                    "onlyMainContent" to true,
                    "waitFor" to 3000,
                    "actions" to listOf(
                        mapOf("type" to "scroll", "direction" to "down", "amount" to 5),
                        mapOf("type" to "wait", "milliseconds" to 2000)
                    )
                )
            )
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw RuntimeException("Firecrawl returned empty response for: $url")

        val success = response["success"] as? Boolean ?: false
        if (!success) {
            log.error("Firecrawl 실패: {} - response: {}", url, response)
            throw RuntimeException("Firecrawl failed for URL: $url")
        }

        val data = response["data"] as? Map<String, Any>
            ?: throw RuntimeException("No data in Firecrawl response for: $url")

        val metadata = data["metadata"] as? Map<String, Any> ?: emptyMap()
        val markdown = data["markdown"] as? String ?: ""

        val ogImage = metadata["og:image"] as? String ?: metadata["ogImage"] as? String
        log.info("Firecrawl 완료: {} (title={}, ogImage={}, markdown={}자)", url, metadata["title"], ogImage != null, markdown.length)

        return CrawlResult(
            markdown = markdown,
            title = metadata["title"] as? String,
            description = metadata["description"] as? String,
            ogImage = ogImage
        )
    }
}
