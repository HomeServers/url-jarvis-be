package io.hunknownn.urljarvis.domain.search

data class SearchResult(
    val urlId: Long,
    val url: String,
    val title: String?,
    val thumbnail: String?,
    val domain: String,
    val matchedChunkContent: String,
    val score: Double
)
