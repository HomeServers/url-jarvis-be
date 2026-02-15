package io.hunknownn.urljarvis.application.port.out.crawling

data class CrawlResult(
    val markdown: String,
    val title: String?,
    val description: String?
)

interface WebCrawler {
    fun crawl(url: String): CrawlResult
}
