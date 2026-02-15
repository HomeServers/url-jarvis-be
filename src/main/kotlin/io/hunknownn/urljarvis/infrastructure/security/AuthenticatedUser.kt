package io.hunknownn.urljarvis.infrastructure.security

import org.springframework.security.core.context.SecurityContextHolder

object AuthenticatedUser {
    fun getId(): Long =
        SecurityContextHolder.getContext().authentication?.principal as? Long
            ?: throw IllegalStateException("No authenticated user found")
}
