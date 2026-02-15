package io.hunknownn.urljarvis.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "url_chunks")
class UrlChunkJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "url_id", nullable = false)
    val urlId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "chunk_index", nullable = false)
    val chunkIndex: Int,

    // embedding 컬럼은 JPA로 매핑하지 않음 (pgvector 타입 → JdbcTemplate으로 처리)

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UrlChunkJpaEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
