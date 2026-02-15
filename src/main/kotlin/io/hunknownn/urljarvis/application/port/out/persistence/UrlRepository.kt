package io.hunknownn.urljarvis.application.port.out.persistence

import io.hunknownn.urljarvis.domain.url.CrawlStatus
import io.hunknownn.urljarvis.domain.url.Url
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UrlRepository {
    fun save(url: Url): Url
    fun findById(id: Long): Url?
    fun findByUserIdAndUrl(userId: Long, url: String): Url?
    fun findByUserId(userId: Long, pageable: Pageable): Page<Url>
    fun findByIdAndUserId(id: Long, userId: Long): Url?
    fun deleteById(id: Long)
    fun updateStatus(id: Long, status: CrawlStatus, failReason: String? = null)
}
