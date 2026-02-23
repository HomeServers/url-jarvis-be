package io.hunknownn.urljarvis.adapter.out.crawling

import io.hunknownn.urljarvis.application.port.out.crawling.CrawlResult
import io.hunknownn.urljarvis.application.port.out.crawling.WebCrawler
import io.hunknownn.urljarvis.infrastructure.config.FirecrawlProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

@Component
class FirecrawlAdapter(
    private val webClient: WebClient,
    private val firecrawlProperties: FirecrawlProperties
) : WebCrawler {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** CAPTCHA/보안 검증 페이지에서 자주 등장하는 패턴 */
        private val BLOCKED_CONTENT_PATTERNS = listOf(
            // 영문 CAPTCHA/봇 검증
            "security verification",
            "please verify you are a human",
            "verify that you are a real user",
            "are you a robot",
            "captcha",
            "i'm not a robot",
            "access denied",
            "please complete the security",
            "checking your browser",
            "enable javascript and cookies",
            "ray id",
            // 한국어 CAPTCHA/봇 검증
            "보안 검증",
            "자동 등록 방지",
            "로봇이 아닙니다",
            "실제 사용자인지 확인",
            "음성으로 안내되고 있습니다",
            "접근이 거부되었습니다",
            "스팸 방지를 위해",
            "보안문자",
        )

        /** 패턴 중 2개 이상 매칭되면 차단 페이지로 판단하는 임계값 */
        private const val BLOCKED_PATTERN_THRESHOLD = 2
    }

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
            .exchangeToMono { clientResponse ->
                val statusCode = clientResponse.statusCode().value()
                if (clientResponse.statusCode().is2xxSuccessful) {
                    clientResponse.bodyToMono(Map::class.java)
                } else {
                    clientResponse.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { body ->
                            log.error("Firecrawl HTTP 에러: url={}, status={}, body={}", url, statusCode, body)
                            Mono.error(CrawlFailedException(statusCode, body))
                        }
                }
            }
            .retryWhen(
                Retry.backoff(
                    firecrawlProperties.maxRetries.toLong(),
                    Duration.ofSeconds(firecrawlProperties.retryBackoffSeconds)
                ).filter { it is CrawlFailedException && (it as CrawlFailedException).statusCode in 500..599 }
                    .doBeforeRetry { signal ->
                        log.warn("Firecrawl 재시도 #{}: url={}, cause={}", signal.totalRetries() + 1, url, signal.failure().message)
                    }
            )
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

        // CAPTCHA/보안 검증 페이지 감지
        detectBlockedContent(markdown, url)

        val ogImage = metadata["og:image"] as? String ?: metadata["ogImage"] as? String
        log.info("Firecrawl 완료: {} (title={}, ogImage={}, markdown={}자)", url, metadata["title"], ogImage != null, markdown.length)

        return CrawlResult(
            markdown = markdown,
            title = metadata["title"] as? String,
            description = metadata["description"] as? String,
            ogImage = ogImage
        )
    }

    /**
     * 크롤링 결과가 CAPTCHA/보안 검증 페이지인지 감지한다.
     * 차단 패턴이 임계값 이상 매칭되면 CrawlFailedException을 발생시킨다.
     */
    private fun detectBlockedContent(markdown: String, url: String) {
        val lowerContent = markdown.lowercase()
        val matchedPatterns = BLOCKED_CONTENT_PATTERNS.filter { it in lowerContent }

        if (matchedPatterns.size >= BLOCKED_PATTERN_THRESHOLD) {
            log.warn(
                "CAPTCHA/보안 페이지 감지: url={}, 매칭 패턴={}, markdown 길이={}",
                url, matchedPatterns, markdown.length
            )
            throw CrawlFailedException(
                statusCode = 200,
                responseBody = "BLOCKED_CONTENT_DETECTED: ${matchedPatterns.joinToString(", ")}",
                message = "Blocked content detected for URL: $url"
            )
        }
    }
}
