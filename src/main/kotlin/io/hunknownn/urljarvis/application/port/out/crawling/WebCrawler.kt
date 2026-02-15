package io.hunknownn.urljarvis.application.port.out.crawling

data class CrawlResult(
    val markdown: String,
    val title: String?,
    val description: String?,
    val ogImage: String? = null
)

/** URL 크롤링 Output Port. 구현체: FirecrawlAdapter */
interface WebCrawler {
    fun crawl(url: String): CrawlResult
}
