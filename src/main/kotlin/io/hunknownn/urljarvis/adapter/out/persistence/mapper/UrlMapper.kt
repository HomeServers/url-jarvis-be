package io.hunknownn.urljarvis.adapter.out.persistence.mapper

import io.hunknownn.urljarvis.adapter.out.persistence.entity.UrlJpaEntity
import io.hunknownn.urljarvis.domain.url.Url

object UrlMapper {
    fun toDomain(entity: UrlJpaEntity): Url = Url(
        id = entity.id,
        userId = entity.userId,
        url = entity.url,
        title = entity.title,
        description = entity.description,
        thumbnail = entity.thumbnail,
        domain = entity.domain,
        status = entity.status,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(domain: Url): UrlJpaEntity = UrlJpaEntity(
        id = domain.id,
        userId = domain.userId,
        url = domain.url,
        title = domain.title,
        description = domain.description,
        thumbnail = domain.thumbnail,
        domain = domain.domain,
        status = domain.status,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )
}
