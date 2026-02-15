package io.hunknownn.urljarvis.adapter.out.persistence.repository

import io.hunknownn.urljarvis.adapter.out.persistence.entity.UrlChunkJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UrlChunkJpaRepository : JpaRepository<UrlChunkJpaEntity, Long> {
    fun deleteByUrlId(urlId: Long)
}
