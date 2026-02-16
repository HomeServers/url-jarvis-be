package io.hunknownn.urljarvis.adapter.`in`.web.dto.response

import io.hunknownn.urljarvis.application.port.`in`.SearchUseCase.SearchAnswer

data class SearchResponse(
    val answer: String,
    val sources: List<SourceInfo>
) {
    data class SourceInfo(
        val urlId: Long,
        val url: String,
        val title: String?,
        val thumbnail: String?,
        val domain: String,
        val matchedContent: String,
        val score: Double
    )

    companion object {
        fun from(searchAnswer: SearchAnswer): SearchResponse = SearchResponse(
            answer = searchAnswer.answer,
            sources = searchAnswer.sources.map {
                SourceInfo(
                    urlId = it.urlId,
                    url = it.url,
                    title = it.title,
                    thumbnail = it.thumbnail,
                    domain = it.domain,
                    matchedContent = it.matchedChunkContent,
                    score = it.score
                )
            }
        )
    }
}
