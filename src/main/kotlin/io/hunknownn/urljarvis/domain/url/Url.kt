package io.hunknownn.urljarvis.domain.url

import java.time.LocalDateTime

data class Url(
    val id: Long = 0,
    val userId: Long,
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val domain: String,
    val status: CrawlStatus = CrawlStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
