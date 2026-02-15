package io.hunknownn.urljarvis.application.service

import io.hunknownn.urljarvis.application.port.`in`.AuthUseCase
import io.hunknownn.urljarvis.application.port.`in`.TokenPair
import io.hunknownn.urljarvis.application.port.out.auth.OAuthClient
import io.hunknownn.urljarvis.application.port.out.persistence.UserRepository
import io.hunknownn.urljarvis.domain.user.OAuthProvider
import io.hunknownn.urljarvis.domain.user.User
import io.hunknownn.urljarvis.infrastructure.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val oAuthClient: OAuthClient,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider
) : AuthUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun loginWithOAuth(provider: OAuthProvider, code: String, redirectUri: String): TokenPair {
        log.info("OAuth 로그인 시도: provider={}", provider)
        val oAuthUserInfo = oAuthClient.getUserInfo(provider, code, redirectUri)

        val user = userRepository.findByProviderAndProviderId(oAuthUserInfo.provider, oAuthUserInfo.providerId)
            ?: userRepository.save(
                User(
                    email = oAuthUserInfo.email,
                    name = oAuthUserInfo.name,
                    provider = oAuthUserInfo.provider,
                    providerId = oAuthUserInfo.providerId
                )
            ).also { log.info("신규 회원 생성: id={}, email={}", it.id, it.email) }

        log.info("OAuth 로그인 완료: userId={}, provider={}", user.id, provider)
        return generateTokenPair(user.id)
    }

    override fun refreshToken(refreshToken: String): TokenPair {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("유효하지 않은 refresh token")
            throw IllegalArgumentException("Invalid refresh token")
        }
        val userId = jwtTokenProvider.getUserId(refreshToken)
        userRepository.findById(userId)
            ?: throw IllegalArgumentException("User not found")

        log.info("토큰 갱신: userId={}", userId)
        return generateTokenPair(userId)
    }

    private fun generateTokenPair(userId: Long): TokenPair = TokenPair(
        accessToken = jwtTokenProvider.generateAccessToken(userId),
        refreshToken = jwtTokenProvider.generateRefreshToken(userId)
    )
}
