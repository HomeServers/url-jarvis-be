package io.hunknownn.urljarvis.application.port.out.embedding

interface EmbeddingClient {
    fun embed(text: String): FloatArray
    fun embedBatch(texts: List<String>): List<FloatArray>
}
