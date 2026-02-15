package io.hunknownn.urljarvis.adapter.out.persistence.repository

import io.hunknownn.urljarvis.adapter.out.persistence.entity.UrlJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UrlJpaRepository : JpaRepository<UrlJpaEntity, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<UrlJpaEntity>
    fun findByIdAndUserId(id: Long, userId: Long): UrlJpaEntity?

    @Modifying
    @Query("UPDATE UrlJpaEntity u SET u.status = :status, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    fun updateStatus(id: Long, status: io.hunknownn.urljarvis.domain.url.CrawlStatus)
}
