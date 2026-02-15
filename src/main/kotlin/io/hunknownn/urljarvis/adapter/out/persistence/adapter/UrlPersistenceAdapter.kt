package io.hunknownn.urljarvis.adapter.out.persistence.adapter

import io.hunknownn.urljarvis.adapter.out.persistence.mapper.UrlMapper
import io.hunknownn.urljarvis.adapter.out.persistence.repository.UrlJpaRepository
import io.hunknownn.urljarvis.application.port.out.persistence.UrlRepository
import io.hunknownn.urljarvis.domain.url.CrawlStatus
import io.hunknownn.urljarvis.domain.url.Url
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UrlPersistenceAdapter(
    private val urlJpaRepository: UrlJpaRepository
) : UrlRepository {

    override fun save(url: Url): Url {
        val entity = UrlMapper.toEntity(url)
        val saved = urlJpaRepository.save(entity)
        return UrlMapper.toDomain(saved)
    }

    override fun findById(id: Long): Url? =
        urlJpaRepository.findById(id)
            .map { UrlMapper.toDomain(it) }
            .orElse(null)

    override fun findByUserId(userId: Long, pageable: Pageable): Page<Url> =
        urlJpaRepository.findByUserId(userId, pageable)
            .map { UrlMapper.toDomain(it) }

    override fun findByIdAndUserId(id: Long, userId: Long): Url? =
        urlJpaRepository.findByIdAndUserId(id, userId)
            ?.let { UrlMapper.toDomain(it) }

    override fun deleteById(id: Long) =
        urlJpaRepository.deleteById(id)

    @Transactional
    override fun updateStatus(id: Long, status: CrawlStatus) =
        urlJpaRepository.updateStatus(id, status)
}
