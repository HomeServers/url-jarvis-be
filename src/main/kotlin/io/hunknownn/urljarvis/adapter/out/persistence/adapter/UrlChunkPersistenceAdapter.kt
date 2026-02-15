package io.hunknownn.urljarvis.adapter.out.persistence.adapter

import com.pgvector.PGvector
import io.hunknownn.urljarvis.adapter.out.persistence.repository.UrlChunkJpaRepository
import io.hunknownn.urljarvis.application.port.out.persistence.UrlChunkRepository
import io.hunknownn.urljarvis.domain.search.SearchResult
import io.hunknownn.urljarvis.domain.url.UrlChunk
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * pgvector 벡터 연산을 위해 JdbcTemplate + native SQL을 사용하는 어댑터.
 *
 * JPA는 vector 타입을 직접 매핑할 수 없으므로,
 * 임베딩 저장/검색은 PGvector 라이브러리 + native query로 처리한다.
 * <=> 연산자: pgvector의 코사인 거리 (1 - cosine_similarity)
 */
@Component
class UrlChunkPersistenceAdapter(
    private val urlChunkJpaRepository: UrlChunkJpaRepository,
    private val jdbcTemplate: JdbcTemplate
) : UrlChunkRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun saveAll(chunks: List<UrlChunk>) {
        log.info("청크 저장: {}건 (urlId={})", chunks.size, chunks.firstOrNull()?.urlId)
        val sql = """
            INSERT INTO url_chunks (url_id, content, chunk_index, embedding, created_at)
            VALUES (?, ?, ?, ?::vector, NOW())
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, chunks, chunks.size) { ps, chunk ->
            ps.setLong(1, chunk.urlId)
            ps.setString(2, chunk.content)
            ps.setInt(3, chunk.chunkIndex)
            ps.setObject(4, PGvector(chunk.embedding))
        }
    }

    @Transactional
    override fun deleteByUrlId(urlId: Long) {
        log.info("청크 삭제: urlId={}", urlId)
        urlChunkJpaRepository.deleteByUrlId(urlId)
    }

    override fun searchByUserId(userId: Long, queryEmbedding: FloatArray, topK: Int): List<SearchResult> {
        val sql = """
            SELECT u.id AS url_id, u.url, u.title, u.domain, c.content,
                   1 - (c.embedding <=> ?::vector) AS similarity
            FROM url_chunks c
            JOIN urls u ON c.url_id = u.id
            WHERE u.user_id = ?
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
        """.trimIndent()

        val vector = PGvector(queryEmbedding)
        return jdbcTemplate.query(sql, { rs, _ ->
            SearchResult(
                urlId = rs.getLong("url_id"),
                url = rs.getString("url"),
                title = rs.getString("title"),
                domain = rs.getString("domain"),
                matchedChunkContent = rs.getString("content"),
                similarity = rs.getDouble("similarity")
            )
        }, vector, userId, vector, topK)
    }

    override fun searchByUrlId(urlId: Long, queryEmbedding: FloatArray, topK: Int): List<SearchResult> {
        val sql = """
            SELECT u.id AS url_id, u.url, u.title, u.domain, c.content,
                   1 - (c.embedding <=> ?::vector) AS similarity
            FROM url_chunks c
            JOIN urls u ON c.url_id = u.id
            WHERE c.url_id = ?
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
        """.trimIndent()

        val vector = PGvector(queryEmbedding)
        return jdbcTemplate.query(sql, { rs, _ ->
            SearchResult(
                urlId = rs.getLong("url_id"),
                url = rs.getString("url"),
                title = rs.getString("title"),
                domain = rs.getString("domain"),
                matchedChunkContent = rs.getString("content"),
                similarity = rs.getDouble("similarity")
            )
        }, vector, urlId, vector, topK)
    }
}
