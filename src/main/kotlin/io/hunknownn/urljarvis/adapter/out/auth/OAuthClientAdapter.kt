package io.hunknownn.urljarvis.adapter.out.auth

import io.hunknownn.urljarvis.application.port.out.auth.OAuthClient
import io.hunknownn.urljarvis.application.port.out.auth.OAuthUserInfo
import io.hunknownn.urljarvis.domain.user.OAuthProvider
import io.hunknownn.urljarvis.infrastructure.config.OAuthProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * OAuth2 Authorization Code Flow를 WebClient로 직접 구현한 어댑터.
 *
 * 흐름: FE가 전달한 auth code → Provider에 토큰 교환 → 사용자 정보 조회
 * 각 Provider(Google, Kakao, Naver)별 응답 JSON 구조가 다르므로 개별 파서를 사용한다.
 */
@Component
class OAuthClientAdapter(
    private val webClient: WebClient,
    private val oAuthProperties: OAuthProperties
) : OAuthClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getUserInfo(provider: OAuthProvider, code: String, redirectUri: String): OAuthUserInfo {
        val props = getProviderProperties(provider)
        val accessToken = exchangeCodeForToken(props, code, redirectUri, provider)
        return fetchUserInfo(provider, props, accessToken)
    }

    /** auth code를 Provider의 token endpoint에 보내 access_token을 받는다 */
    private fun exchangeCodeForToken(
        props: OAuthProperties.ProviderProperties,
        code: String,
        redirectUri: String,
        provider: OAuthProvider
    ): String {
        val formData = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", props.clientId)
            add("client_secret", props.clientSecret)
            add("code", code)
            add("redirect_uri", redirectUri)
        }

        val response = try {
            webClient.post()
                .uri(props.tokenUri)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: throw RuntimeException("Failed to exchange token with ${provider.name}")
        } catch (e: WebClientResponseException) {
            log.error("Token exchange failed for ${provider.name}: ${e.statusCode} - ${e.responseBodyAsString}")
            throw e
        }

        return response["access_token"] as? String
            ?: throw RuntimeException("No access_token in response from ${provider.name}")
    }

    private fun fetchUserInfo(
        provider: OAuthProvider,
        props: OAuthProperties.ProviderProperties,
        accessToken: String
    ): OAuthUserInfo {
        val response = webClient.get()
            .uri(props.userInfoUri)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw RuntimeException("Failed to fetch user info from ${provider.name}")

        return when (provider) {
            OAuthProvider.GOOGLE -> parseGoogleUser(response)
            OAuthProvider.KAKAO -> parseKakaoUser(response)
            OAuthProvider.NAVER -> parseNaverUser(response)
        }
    }

    private fun parseGoogleUser(response: Map<*, *>): OAuthUserInfo = OAuthUserInfo(
        provider = OAuthProvider.GOOGLE,
        providerId = response["sub"] as String,
        email = response["email"] as String,
        name = response["name"] as? String ?: "Unknown"
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseKakaoUser(response: Map<*, *>): OAuthUserInfo {
        val kakaoAccount = response["kakao_account"] as? Map<String, Any> ?: emptyMap()
        val profile = kakaoAccount["profile"] as? Map<String, Any> ?: emptyMap()
        return OAuthUserInfo(
            provider = OAuthProvider.KAKAO,
            providerId = response["id"].toString(),
            email = kakaoAccount["email"] as? String ?: "",
            name = profile["nickname"] as? String ?: "Unknown"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseNaverUser(response: Map<*, *>): OAuthUserInfo {
        val naverResponse = response["response"] as? Map<String, Any> ?: emptyMap()
        return OAuthUserInfo(
            provider = OAuthProvider.NAVER,
            providerId = naverResponse["id"] as String,
            email = naverResponse["email"] as? String ?: "",
            name = naverResponse["name"] as? String ?: "Unknown"
        )
    }

    private fun getProviderProperties(provider: OAuthProvider): OAuthProperties.ProviderProperties =
        when (provider) {
            OAuthProvider.GOOGLE -> oAuthProperties.google
            OAuthProvider.KAKAO -> oAuthProperties.kakao
            OAuthProvider.NAVER -> oAuthProperties.naver
        }
}
