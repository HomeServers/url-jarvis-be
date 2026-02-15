package io.hunknownn.urljarvis.adapter.`in`.web.dto.response

import io.hunknownn.urljarvis.domain.url.CrawlStatus
import io.hunknownn.urljarvis.domain.url.Url
import java.time.LocalDateTime

data class UrlResponse(
    val id: Long,
    val url: String,
    val title: String?,
    val description: String?,
    val domain: String,
    val status: CrawlStatus,
    val failReason: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(url: Url): UrlResponse = UrlResponse(
            id = url.id,
            url = url.url,
            title = url.title,
            description = url.description,
            domain = url.domain,
            status = url.status,
            failReason = url.failReason,
            createdAt = url.createdAt,
            updatedAt = url.updatedAt
        )
    }
}
