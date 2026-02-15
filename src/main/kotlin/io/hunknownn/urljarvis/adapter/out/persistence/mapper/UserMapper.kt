package io.hunknownn.urljarvis.adapter.out.persistence.mapper

import io.hunknownn.urljarvis.adapter.out.persistence.entity.UserJpaEntity
import io.hunknownn.urljarvis.domain.user.User

object UserMapper {
    fun toDomain(entity: UserJpaEntity): User = User(
        id = entity.id,
        email = entity.email,
        name = entity.name,
        provider = entity.provider,
        providerId = entity.providerId,
        createdAt = entity.createdAt
    )

    fun toEntity(domain: User): UserJpaEntity = UserJpaEntity(
        id = domain.id,
        email = domain.email,
        name = domain.name,
        provider = domain.provider,
        providerId = domain.providerId,
        createdAt = domain.createdAt
    )
}
