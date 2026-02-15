package io.hunknownn.urljarvis.infrastructure.async

import io.hunknownn.urljarvis.application.service.CrawlPipelineService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class CrawlEventListener(
    private val crawlPipelineService: CrawlPipelineService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("crawlExecutor")
    @EventListener
    fun handleCrawlRequested(event: CrawlRequestedEvent) {
        log.info("Received crawl event for URL ID: {}", event.urlId)
        crawlPipelineService.executePipeline(event.urlId)
    }
}
