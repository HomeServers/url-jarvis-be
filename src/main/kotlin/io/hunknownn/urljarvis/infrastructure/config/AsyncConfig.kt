package io.hunknownn.urljarvis.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("crawlExecutor")
    fun crawlExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 3          // 기본 스레드 수
        maxPoolSize = 10          // 최대 스레드 수
        queueCapacity = 50        // 대기 큐 크기
        setThreadNamePrefix("crawl-")
        initialize()
    }
}
