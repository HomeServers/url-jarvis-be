package io.hunknownn.urljarvis.adapter.`in`.web

import io.hunknownn.urljarvis.adapter.`in`.web.dto.request.OAuthLoginRequest
import io.hunknownn.urljarvis.adapter.`in`.web.dto.request.RefreshTokenRequest
import io.hunknownn.urljarvis.adapter.`in`.web.dto.response.ApiResponse
import io.hunknownn.urljarvis.adapter.`in`.web.dto.response.TokenResponse
import io.hunknownn.urljarvis.application.port.`in`.AuthUseCase
import io.hunknownn.urljarvis.domain.user.OAuthProvider
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authUseCase: AuthUseCase
) {
    @PostMapping("/{provider}")
    fun oauthLogin(
        @PathVariable provider: String,
        @RequestBody @Valid request: OAuthLoginRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val oAuthProvider = OAuthProvider.valueOf(provider.uppercase())
        val tokenPair = authUseCase.loginWithOAuth(oAuthProvider, request.code, request.redirectUri)

        return ResponseEntity.ok(
            ApiResponse.ok(TokenResponse(tokenPair.accessToken, tokenPair.refreshToken))
        )
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody @Valid request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val tokenPair = authUseCase.refreshToken(request.refreshToken)

        return ResponseEntity.ok(
            ApiResponse.ok(TokenResponse(tokenPair.accessToken, tokenPair.refreshToken))
        )
    }
}
