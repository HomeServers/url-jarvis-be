package io.hunknownn.urljarvis.adapter.out.llm

import io.hunknownn.urljarvis.application.port.out.llm.LlmClient
import io.hunknownn.urljarvis.infrastructure.config.OpenAiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

/**
 * OpenAI Chat Completions API 어댑터.
 * 검색된 청크를 컨텍스트로 제공하고 gpt-4o-mini가 자연어 답변을 생성한다.
 */
@Component
class OpenAiAdapter(
    private val webClient: WebClient,
    private val openAiProperties: OpenAiProperties
) : LlmClient {

    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"

        private const val SYSTEM_PROMPT = """
당신은 사용자가 저장한 URL의 콘텐츠를 바탕으로 질문에 답변하는 어시스턴트입니다.
아래 규칙을 따르세요:
- 제공된 컨텍스트 내의 정보만 사용하세요.
- 컨텍스트에 없는 정보는 "제공된 정보에서 찾을 수 없습니다"라고 답하세요.
- 간결하고 정확하게 답변하세요.
- 한국어로 답변하세요.
"""
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    override fun generate(query: String, context: String): String {
        log.info("OpenAI 요청: model={}, query='{}', context={}자", openAiProperties.model, query, context.length)

        val requestBody = mapOf(
            "model" to openAiProperties.model,
            "max_tokens" to openAiProperties.maxTokens,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT.trimIndent()),
                mapOf("role" to "user", "content" to buildUserMessage(query, context))
            )
        )

        val response = webClient.post()
            .uri(API_URL)
            .header("Authorization", "Bearer ${openAiProperties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block() ?: throw RuntimeException("OpenAI returned empty response")

        val choices = response["choices"] as? List<Map<String, Any>>
            ?: throw RuntimeException("No choices in OpenAI response")
        val message = choices.first()["message"] as? Map<String, Any>
            ?: throw RuntimeException("No message in OpenAI choice")

        val answer = message["content"] as? String ?: ""
        log.info("OpenAI 응답: {}자", answer.length)
        return answer
    }

    private fun buildUserMessage(query: String, context: String): String =
        """
        [컨텍스트]
        $context

        [질문]
        $query
        """.trimIndent()
}
