package io.hunknownn.urljarvis.adapter.out.embedding

import io.hunknownn.urljarvis.application.port.out.embedding.EmbeddingClient
import io.hunknownn.urljarvis.infrastructure.config.EmbeddingProperties
import io.hunknownn.urljarvis.infrastructure.config.OpenAiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import kotlin.system.measureTimeMillis

/**
 * OpenAI text-embedding-3-small API를 호출하는 임베딩 어댑터.
 */
@Component
class EmbeddingRestAdapter(
    private val webClient: WebClient,
    private val embeddingProperties: EmbeddingProperties,
    private val openAiProperties: OpenAiProperties
) : EmbeddingClient {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun embed(text: String): FloatArray =
        embedBatch(listOf(text)).first()

    @Suppress("UNCHECKED_CAST")
    override fun embedBatch(texts: List<String>, batchIndex: Int, totalBatches: Int): List<FloatArray> {
        val batchLabel = if (totalBatches > 0) "[배치 $batchIndex/$totalBatches] " else ""
        log.info("임베딩 요청: {}{}건 (model: {})", batchLabel, texts.size, embeddingProperties.model)

        val response: Map<*, *>
        val elapsed = measureTimeMillis {
            response = webClient.post()
                .uri("https://api.openai.com/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${openAiProperties.apiKey}")
                .bodyValue(
                    mapOf(
                        "input" to texts,
                        "model" to embeddingProperties.model
                    )
                )
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() ?: throw RuntimeException("OpenAI embedding API returned empty response")
        }

        val data = response["data"] as? List<Map<String, Any>>
            ?: throw RuntimeException("No data in OpenAI embedding response")

        val embeddings = data.map { item ->
            val vector = item["embedding"] as List<Number>
            FloatArray(vector.size) { i -> vector[i].toFloat() }
        }

        log.info("임베딩 완료: {}{}건 ({}차원) - {}ms (건당 {}ms)", batchLabel, embeddings.size, embeddings.firstOrNull()?.size ?: 0, elapsed, elapsed / texts.size)

        return embeddings
    }
}
