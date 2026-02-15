package io.hunknownn.urljarvis.adapter.out.persistence.adapter

import io.hunknownn.urljarvis.adapter.out.persistence.mapper.UserMapper
import io.hunknownn.urljarvis.adapter.out.persistence.repository.UserJpaRepository
import io.hunknownn.urljarvis.application.port.out.persistence.UserRepository
import io.hunknownn.urljarvis.domain.user.OAuthProvider
import io.hunknownn.urljarvis.domain.user.User
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserRepository {

    override fun save(user: User): User {
        val entity = UserMapper.toEntity(user)
        val saved = userJpaRepository.save(entity)
        return UserMapper.toDomain(saved)
    }

    override fun findById(id: Long): User? =
        userJpaRepository.findById(id)
            .map { UserMapper.toDomain(it) }
            .orElse(null)

    override fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): User? =
        userJpaRepository.findByProviderAndProviderId(provider, providerId)
            ?.let { UserMapper.toDomain(it) }
}
