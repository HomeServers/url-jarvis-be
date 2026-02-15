package io.hunknownn.urljarvis.domain.user

import java.time.LocalDateTime

data class User(
    val id: Long = 0,
    val email: String,
    val name: String,
    val provider: OAuthProvider,
    val providerId: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
