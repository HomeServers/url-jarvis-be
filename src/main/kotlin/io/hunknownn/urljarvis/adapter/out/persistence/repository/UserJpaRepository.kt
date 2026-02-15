package io.hunknownn.urljarvis.adapter.out.persistence.repository

import io.hunknownn.urljarvis.adapter.out.persistence.entity.UserJpaEntity
import io.hunknownn.urljarvis.domain.user.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): UserJpaEntity?
}
