package io.hunknownn.urljarvis.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "url-jarvis.embedding")
data class EmbeddingProperties(
    val model: String = "text-embedding-3-small",
    val dimensions: Int = 1536
)
