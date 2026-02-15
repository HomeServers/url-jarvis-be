package io.hunknownn.urljarvis.application.port.out.persistence

import io.hunknownn.urljarvis.domain.user.OAuthProvider
import io.hunknownn.urljarvis.domain.user.User

interface UserRepository {
    fun save(user: User): User
    fun findById(id: Long): User?
    fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): User?
}
