package io.hunknownn.urljarvis.adapter.`in`.web

import io.hunknownn.urljarvis.adapter.`in`.web.dto.response.ApiResponse
import io.hunknownn.urljarvis.adapter.`in`.web.dto.response.TokenResponse
import io.hunknownn.urljarvis.application.port.out.persistence.UserRepository
import io.hunknownn.urljarvis.domain.user.OAuthProvider
import io.hunknownn.urljarvis.domain.user.User
import io.hunknownn.urljarvis.infrastructure.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** dev 프로필에서만 활성화되는 테스트 토큰 발급 엔드포인트 */
@Profile("dev")
@RestController
@RequestMapping("/api/auth")
class DevAuthController(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/dev-token")
    fun issueDevToken(): ResponseEntity<ApiResponse<TokenResponse>> {
        log.info("Dev 토큰 발급 요청")
        val user = userRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "dev-user-001")
            ?: userRepository.save(
                User(
                    email = "dev@urljarvis.local",
                    name = "Dev User",
                    provider = OAuthProvider.GOOGLE,
                    providerId = "dev-user-001"
                )
            )

        val tokenPair = TokenResponse(
            accessToken = jwtTokenProvider.generateAccessToken(user.id),
            refreshToken = jwtTokenProvider.generateRefreshToken(user.id)
        )

        log.info("Dev 토큰 발급 완료: userId={}", user.id)
        return ResponseEntity.ok(ApiResponse.ok(tokenPair))
    }
}
