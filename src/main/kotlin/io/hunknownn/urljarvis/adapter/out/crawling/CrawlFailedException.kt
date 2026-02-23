package io.hunknownn.urljarvis.adapter.out.crawling

class CrawlFailedException(
    val statusCode: Int,
    val responseBody: String,
    message: String = "Crawl failed with status $statusCode"
) : RuntimeException(message) {

    fun toUserMessage(): String {
        if ("SCRAPE_ALL_ENGINES_FAILED" in responseBody) {
            return "해당 웹사이트가 자동 수집을 차단하고 있어 콘텐츠를 가져올 수 없습니다"
        }
        if ("BLOCKED_CONTENT_DETECTED" in responseBody) {
            return "해당 웹사이트의 보안 검증(CAPTCHA)으로 인해 콘텐츠를 가져올 수 없습니다"
        }
        return when {
            statusCode in 500..599 -> "외부 서비스 일시 오류로 크롤링에 실패했습니다. 잠시 후 다시 시도해주세요"
            statusCode in 400..499 -> "요청 처리 중 오류가 발생했습니다"
            else -> "크롤링 처리 중 오류가 발생했습니다"
        }
    }
}
