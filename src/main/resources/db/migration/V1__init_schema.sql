-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 사용자
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    provider        VARCHAR(20) NOT NULL,
    provider_id     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (provider, provider_id)
);

CREATE INDEX idx_users_email ON users(email);

-- URL
CREATE TABLE urls (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    url             TEXT NOT NULL,
    title           VARCHAR(500),
    description     TEXT,
    domain          VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_urls_user_id ON urls(user_id);
CREATE INDEX idx_urls_status ON urls(status);

-- URL 청크 (벡터 포함)
CREATE TABLE url_chunks (
    id              BIGSERIAL PRIMARY KEY,
    url_id          BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    chunk_index     INT NOT NULL,
    embedding       vector(384) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_url_chunks_url_id ON url_chunks(url_id);

-- HNSW 인덱스 (코사인 유사도 검색 최적화)
CREATE INDEX idx_url_chunks_embedding ON url_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
