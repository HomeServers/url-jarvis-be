package io.hunknownn.urljarvis.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient {
        val strategies = ExchangeStrategies.builder()
            .codecs { it.defaultCodecs().maxInMemorySize(512 * 1024) } // 512KB
            .build()
        return WebClient.builder()
            .exchangeStrategies(strategies)
            .build()
    }
}
