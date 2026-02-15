package io.hunknownn.urljarvis.adapter.out.persistence.entity

import io.hunknownn.urljarvis.domain.url.CrawlStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "urls")
class UrlJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, columnDefinition = "TEXT")
    val url: String,

    @Column(length = 500)
    val title: String? = null,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(nullable = false, length = 255)
    val domain: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: CrawlStatus = CrawlStatus.PENDING,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UrlJpaEntity) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int = javaClass.hashCode()
}
