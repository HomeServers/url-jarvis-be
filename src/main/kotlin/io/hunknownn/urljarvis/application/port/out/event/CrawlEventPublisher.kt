package io.hunknownn.urljarvis.application.port.out.event

interface CrawlEventPublisher {
    fun publishCrawlRequested(urlId: Long)
}
