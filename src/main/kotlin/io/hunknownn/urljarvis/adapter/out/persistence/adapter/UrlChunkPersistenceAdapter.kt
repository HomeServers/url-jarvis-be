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

    override fun searchByUserId(userId: Long, queryEmbedding: FloatArray, query: String, topK: Int): List<SearchResult> {
        val sql = """
            WITH vector_search AS (
                SELECT c.id, c.content, c.url_id,
                       ROW_NUMBER() OVER (ORDER BY c.embedding <=> ?::vector) AS rank_v
                FROM url_chunks c JOIN urls u ON c.url_id = u.id
                WHERE u.user_id = ?
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
            ),
            keyword_search AS (
                SELECT c.id, c.content, c.url_id,
                       ROW_NUMBER() OVER (ORDER BY ts_rank(c.content_tsv, query) DESC) AS rank_k
                FROM url_chunks c JOIN urls u ON c.url_id = u.id,
                     plainto_tsquery('simple', ?) AS query
                WHERE u.user_id = ? AND c.content_tsv @@ query
                LIMIT ?
            )
            SELECT u.id AS url_id, u.url, u.title, u.thumbnail, u.domain,
                   COALESCE(v.content, k.content) AS content,
                   COALESCE(1.0/(60+v.rank_v), 0) + COALESCE(1.0/(60+k.rank_k), 0) AS score
            FROM vector_search v
            FULL OUTER JOIN keyword_search k ON v.id = k.id
            JOIN url_chunks c ON c.id = COALESCE(v.id, k.id)
            JOIN urls u ON c.url_id = u.id
            ORDER BY score DESC
            LIMIT ?
        """.trimIndent()

        val vector = PGvector(queryEmbedding)
        return jdbcTemplate.query(sql, { rs, _ ->
            SearchResult(
                urlId = rs.getLong("url_id"),
                url = rs.getString("url"),
                title = rs.getString("title"),
                thumbnail = rs.getString("thumbnail"),
                domain = rs.getString("domain"),
                matchedChunkContent = rs.getString("content"),
                score = rs.getDouble("score")
            )
        }, vector, userId, vector, topK, query, userId, topK, topK)
    }

    override fun searchByUrlId(urlId: Long, queryEmbedding: FloatArray, query: String, topK: Int): List<SearchResult> {
        val sql = """
            WITH vector_search AS (
                SELECT c.id, c.content, c.url_id,
                       ROW_NUMBER() OVER (ORDER BY c.embedding <=> ?::vector) AS rank_v
                FROM url_chunks c
                WHERE c.url_id = ?
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
            ),
            keyword_search AS (
                SELECT c.id, c.content, c.url_id,
                       ROW_NUMBER() OVER (ORDER BY ts_rank(c.content_tsv, query) DESC) AS rank_k
                FROM url_chunks c,
                     plainto_tsquery('simple', ?) AS query
                WHERE c.url_id = ? AND c.content_tsv @@ query
                LIMIT ?
            )
            SELECT u.id AS url_id, u.url, u.title, u.thumbnail, u.domain,
                   COALESCE(v.content, k.content) AS content,
                   COALESCE(1.0/(60+v.rank_v), 0) + COALESCE(1.0/(60+k.rank_k), 0) AS score
            FROM vector_search v
            FULL OUTER JOIN keyword_search k ON v.id = k.id
            JOIN url_chunks c ON c.id = COALESCE(v.id, k.id)
            JOIN urls u ON c.url_id = u.id
            ORDER BY score DESC
            LIMIT ?
        """.trimIndent()

        val vector = PGvector(queryEmbedding)
        return jdbcTemplate.query(sql, { rs, _ ->
            SearchResult(
                urlId = rs.getLong("url_id"),
                url = rs.getString("url"),
                title = rs.getString("title"),
                thumbnail = rs.getString("thumbnail"),
                domain = rs.getString("domain"),
                matchedChunkContent = rs.getString("content"),
                score = rs.getDouble("score")
            )
        }, vector, urlId, vector, topK, query, urlId, topK, topK)
    }
}
