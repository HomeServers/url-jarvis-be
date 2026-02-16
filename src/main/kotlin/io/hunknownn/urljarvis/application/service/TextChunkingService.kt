package io.hunknownn.urljarvis.application.service

import io.hunknownn.urljarvis.infrastructure.config.ChunkingProperties
import org.springframework.stereotype.Service

/**
 * Markdown 텍스트를 벡터 임베딩에 적합한 크기의 청크로 분할한다.
 *
 * 전략: 문단(\n\n) 우선 분할 → 초과 시 문장 단위 재분할.
 * 청크 간 overlap을 적용하여 문맥 단절을 방지한다.
 */
@Service
class TextChunkingService(
    private val chunkingProperties: ChunkingProperties
) {
    fun chunk(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val cleaned = cleanMarkdown(text)
        if (cleaned.isBlank()) return emptyList()

        // 1차: 문단 단위 병합 (chunkSize 이내로 문단들을 합침)
        val paragraphs = cleaned.split("\n\n").filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()

        val buffer = StringBuilder()
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (buffer.length + trimmed.length > chunkingProperties.chunkSize && buffer.isNotEmpty()) {
                chunks.add(buffer.toString().trim())
                // overlap: 이전 청크의 끝부분을 다음 청크 시작에 포함 → 문맥 유지
                val overlap = buffer.toString().takeLast(chunkingProperties.chunkOverlap)
                buffer.clear()
                buffer.append(overlap)
            }
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(trimmed)
        }

        if (buffer.isNotBlank()) {
            chunks.add(buffer.toString().trim())
        }

        // 2차: chunkSize * 1.5 초과 청크는 문장 단위로 재분할
        return chunks.flatMap { splitLargeChunk(it) }
            .filter { it.isNotBlank() }
    }

    /** 임베딩에 불필요한 노이즈 제거: 이미지, 링크 URL, 광고, 마크다운 서식 등 */
    private fun cleanMarkdown(text: String): String {
        return text
            .replace(Regex("!\\[.*?]\\(.*?\\)"), "")                // 이미지 태그: ![alt](url)
            .replace(Regex("\\[\\s*]\\(.*?\\)"), "")                 // 빈 링크: [](url)
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")        // 마크다운 링크 → 텍스트만: [text](url) → text
            .replace(Regex("https?://\\S+"), "")                     // 모든 URL 제거
            .replace(Regex("\\\\{2,}"), "")                          // 이스케이프 백슬래시 제거
            .replace(Regex("(?m)^.*광고.*제휴업체.*판매상품.*$"), "")   // 광고 문구 제거
            .replace(Regex("(?m)^.*함께 비교하면 좋을 상품.*$"), "")    // 프로모션 문구 제거
            .replace(Regex("(?m)^.*추천.*이런건 어때요.*$"), "")        // 추천 문구 제거
            .replace(Regex("(?m)^.*오늘의 판매자 특가.*$"), "")         // 특가 문구 제거
            .replace(Regex("\\*{2,}"), "")                           // 볼드 마크다운: **text** → text
            .replace(Regex("(?<=\\S)_(?=\\S)"), "")                  // 이탤릭 마크다운: _text_ 중간 언더스코어 제거
            .replace(Regex("\\n{3,}"), "\n\n")                       // 연속 줄바꿈 정리
            .replace(Regex(" {2,}"), " ")                            // 연속 공백 정리
    }

    private fun splitLargeChunk(chunk: String): List<String> {
        if (chunk.length <= chunkingProperties.chunkSize * 1.5) return listOf(chunk)

        val sentences = chunk.split(Regex("(?<=[.!?。])\\.?\\s+"))
        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        for (sentence in sentences) {
            if (buffer.length + sentence.length > chunkingProperties.chunkSize && buffer.isNotEmpty()) {
                result.add(buffer.toString().trim())
                val overlap = buffer.toString().takeLast(chunkingProperties.chunkOverlap)
                buffer.clear()
                buffer.append(overlap)
            }
            if (buffer.isNotEmpty()) buffer.append(" ")
            buffer.append(sentence)
        }

        if (buffer.isNotBlank()) {
            result.add(buffer.toString().trim())
        }

        return result
    }
}
