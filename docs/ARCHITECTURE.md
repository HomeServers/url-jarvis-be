# URL Jarvis - 아키텍처 설계서

> Hexagonal Architecture (Ports & Adapters)
> 생성일: 2026-02-15

---

## 1. 아키텍처 개요

```
                    ┌─────────────────────────────────┐
                    │          Driving Adapters         │
                    │  (REST Controllers, Event Listeners) │
                    └──────────────┬──────────────────┘
                                   │ Input Ports
                    ┌──────────────▼──────────────────┐
                    │        Application Layer          │
                    │         (Use Cases)               │
                    │                                   │
                    │  ┌─────────────────────────────┐ │
                    │  │       Domain Layer            │ │
                    │  │  (Entities, Value Objects)    │ │
                    │  └─────────────────────────────┘ │
                    └──────────────┬──────────────────┘
                                   │ Output Ports
                    ┌──────────────▼──────────────────┐
                    │          Driven Adapters          │
                    │  (JPA, Firecrawl, Embedding,     │
                    │   OAuth, Event Publisher)         │
                    └─────────────────────────────────┘
```

핵심 원칙:
- **Domain Layer**는 외부 의존성 없음 (순수 Kotlin)
- **Application Layer**는 Port 인터페이스만 의존
- **Adapter**는 구체적인 기술 구현 (교체 가능)

---

## 2. 패키지 구조

```
io.hunknownn.urljarvis/
│
├── domain/                          # 도메인 계층 (순수 Kotlin, 프레임워크 무의존)
│   ├── user/
│   │   └── User.kt                  # User 엔티티
│   ├── url/
│   │   ├── Url.kt                   # Url 엔티티
│   │   ├── UrlChunk.kt              # UrlChunk 엔티티
│   │   └── CrawlStatus.kt           # PENDING, CRAWLING, CRAWLED, FAILED
│   └── search/
│       └── SearchResult.kt          # 검색 결과 도메인 모델
│
├── application/                     # 애플리케이션 계층 (유스케이스)
│   ├── port/
│   │   ├── in/                      # Input Ports (Driving)
│   │   │   ├── RegisterUrlUseCase.kt
│   │   │   ├── ManageUrlUseCase.kt
│   │   │   ├── SearchUseCase.kt
│   │   │   └── AuthUseCase.kt
│   │   └── out/                     # Output Ports (Driven)
│   │       ├── persistence/
│   │       │   ├── UserRepository.kt
│   │       │   ├── UrlRepository.kt
│   │       │   └── UrlChunkRepository.kt
│   │       ├── crawling/
│   │       │   └── WebCrawler.kt
│   │       ├── embedding/
│   │       │   └── EmbeddingClient.kt
│   │       └── event/
│   │           └── CrawlEventPublisher.kt
│   └── service/                     # 유스케이스 구현
│       ├── UrlService.kt
│       ├── SearchService.kt
│       ├── CrawlPipelineService.kt
│       └── AuthService.kt
│
├── adapter/                         # 어댑터 계층 (기술 구현)
│   ├── in/                          # Driving Adapters
│   │   └── web/
│   │       ├── UrlController.kt
│   │       ├── SearchController.kt
│   │       ├── AuthController.kt
│   │       ├── dto/
│   │       │   ├── request/
│   │       │   │   ├── RegisterUrlRequest.kt
│   │       │   │   └── SearchRequest.kt
│   │       │   └── response/
│   │       │       ├── UrlResponse.kt
│   │       │       ├── SearchResultResponse.kt
│   │       │       └── ApiResponse.kt
│   │       └── exception/
│   │           └── GlobalExceptionHandler.kt
│   │
│   └── out/                         # Driven Adapters
│       ├── persistence/
│       │   ├── entity/
│       │   │   ├── UserJpaEntity.kt
│       │   │   ├── UrlJpaEntity.kt
│       │   │   └── UrlChunkJpaEntity.kt
│       │   ├── repository/
│       │   │   ├── UserJpaRepository.kt
│       │   │   ├── UrlJpaRepository.kt
│       │   │   └── UrlChunkJpaRepository.kt
│       │   ├── mapper/
│       │   │   ├── UserMapper.kt
│       │   │   ├── UrlMapper.kt
│       │   │   └── UrlChunkMapper.kt
│       │   └── adapter/
│       │       ├── UserPersistenceAdapter.kt
│       │       ├── UrlPersistenceAdapter.kt
│       │       └── UrlChunkPersistenceAdapter.kt
│       ├── crawling/
│       │   └── FirecrawlAdapter.kt
│       ├── embedding/
│       │   └── EmbeddingRestAdapter.kt
│       ├── auth/
│       │   └── OAuth2Adapter.kt
│       └── event/
│           └── SpringEventAdapter.kt
│
├── infrastructure/                  # 인프라 설정
│   ├── config/
│   │   ├── SecurityConfig.kt
│   │   ├── AsyncConfig.kt
│   │   ├── JpaConfig.kt
│   │   └── WebClientConfig.kt
│   ├── security/
│   │   ├── JwtTokenProvider.kt
│   │   └── JwtAuthenticationFilter.kt
│   └── async/
│       └── CrawlEventListener.kt
│
└── UrlJarvisApplication.kt
```

---

## 3. 도메인 모델 상세

### 3.1 User

```kotlin
// domain/user/User.kt
data class User(
    val id: Long = 0,
    val email: String,
    val name: String,
    val provider: OAuthProvider,
    val providerId: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class OAuthProvider { GOOGLE, KAKAO, NAVER }
```

### 3.2 Url

```kotlin
// domain/url/Url.kt
data class Url(
    val id: Long = 0,
    val userId: Long,
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val domain: String,
    val status: CrawlStatus = CrawlStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class CrawlStatus { PENDING, CRAWLING, CRAWLED, FAILED }
```

### 3.3 UrlChunk

```kotlin
// domain/url/UrlChunk.kt
data class UrlChunk(
    val id: Long = 0,
    val urlId: Long,
    val content: String,
    val chunkIndex: Int,
    val embedding: FloatArray,  // 384 dimensions (e5-small)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

### 3.4 SearchResult

```kotlin
// domain/search/SearchResult.kt
data class SearchResult(
    val urlId: Long,
    val url: String,
    val title: String?,
    val domain: String,
    val matchedChunkContent: String,
    val similarity: Double
)
```

---

## 4. 포트 설계

### 4.1 Input Ports (Driving)

```kotlin
// application/port/in/RegisterUrlUseCase.kt
interface RegisterUrlUseCase {
    fun register(userId: Long, url: String): Url
}

// application/port/in/ManageUrlUseCase.kt
interface ManageUrlUseCase {
    fun getUrls(userId: Long, page: Int, size: Int): Page<Url>
    fun getUrl(userId: Long, urlId: Long): Url
    fun deleteUrl(userId: Long, urlId: Long)
    fun recrawlUrl(userId: Long, urlId: Long): Url
}

// application/port/in/SearchUseCase.kt
interface SearchUseCase {
    fun searchAll(userId: Long, query: String, topK: Int = 10): List<SearchResult>
    fun searchByUrl(userId: Long, urlId: Long, query: String, topK: Int = 5): List<SearchResult>
}

// application/port/in/AuthUseCase.kt
interface AuthUseCase {
    fun loginWithOAuth(provider: OAuthProvider, authCode: String): TokenPair
    fun refreshToken(refreshToken: String): TokenPair
}
```

### 4.2 Output Ports (Driven)

```kotlin
// application/port/out/persistence/UrlRepository.kt
interface UrlRepository {
    fun save(url: Url): Url
    fun findById(id: Long): Url?
    fun findByUserId(userId: Long, pageable: Pageable): Page<Url>
    fun deleteById(id: Long)
    fun updateStatus(id: Long, status: CrawlStatus)
}

// application/port/out/persistence/UrlChunkRepository.kt
interface UrlChunkRepository {
    fun saveAll(chunks: List<UrlChunk>)
    fun deleteByUrlId(urlId: Long)
    fun searchByUserId(userId: Long, queryEmbedding: FloatArray, topK: Int): List<SearchResult>
    fun searchByUrlId(urlId: Long, queryEmbedding: FloatArray, topK: Int): List<SearchResult>
}

// application/port/out/crawling/WebCrawler.kt
interface WebCrawler {
    fun crawl(url: String): CrawlResult
}

data class CrawlResult(
    val markdown: String,
    val title: String?,
    val description: String?
)

// application/port/out/embedding/EmbeddingClient.kt
interface EmbeddingClient {
    fun embed(text: String): FloatArray
    fun embedBatch(texts: List<String>): List<FloatArray>
}
```

---

## 5. 데이터베이스 스키마

### 5.1 DDL

```sql
-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 사용자
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    provider        VARCHAR(20) NOT NULL,      -- GOOGLE, KAKAO, NAVER
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
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, CRAWLING, CRAWLED, FAILED
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
    embedding       vector(384) NOT NULL,      -- multilingual-e5-small: 384 dimensions
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_url_chunks_url_id ON url_chunks(url_id);

-- HNSW 인덱스 (코사인 유사도 검색 최적화)
CREATE INDEX idx_url_chunks_embedding ON url_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

### 5.2 핵심 쿼리

```sql
-- 전체 URL 대상 시맨틱 검색
SELECT u.id, u.url, u.title, u.domain, c.content,
       1 - (c.embedding <=> :query_embedding) AS similarity
FROM url_chunks c
JOIN urls u ON c.url_id = u.id
WHERE u.user_id = :user_id
ORDER BY c.embedding <=> :query_embedding
LIMIT :top_k;

-- 특정 URL 대상 시맨틱 검색
SELECT u.id, u.url, u.title, u.domain, c.content,
       1 - (c.embedding <=> :query_embedding) AS similarity
FROM url_chunks c
JOIN urls u ON c.url_id = u.id
WHERE c.url_id = :url_id
ORDER BY c.embedding <=> :query_embedding
LIMIT :top_k;
```

---

## 6. 외부 연동 설계

### 6.1 Firecrawl Cloud API

```
POST https://api.firecrawl.dev/v1/scrape
Headers:
  Authorization: Bearer {FIRECRAWL_API_KEY}
Body:
  {
    "url": "https://example.com",
    "formats": ["markdown"]
  }
Response:
  {
    "success": true,
    "data": {
      "markdown": "# Page Title\n...",
      "metadata": {
        "title": "...",
        "description": "..."
      }
    }
  }
```

### 6.2 Embedding Server (multilingual-e5-small)

```
POST http://{EMBEDDING_SERVER_HOST}/embed
Headers:
  Content-Type: application/json
Body:
  {
    "texts": ["query: 검색할 텍스트"],     // e5 모델은 prefix 필요
    "model": "intfloat/multilingual-e5-small"
  }
Response:
  {
    "embeddings": [[0.012, -0.034, ...]]   // 384 dimensions
  }
```

> **참고**: e5 모델은 검색 질의에 `"query: "` prefix, 문서에 `"passage: "` prefix를 붙여야 성능이 최적화됨

---

## 7. 비동기 크롤링 파이프라인

```
                         Spring Event
[UrlService.register()] ──────────────►  [CrawlEventListener]
  └─ URL 저장 (PENDING)                     │
  └─ CrawlRequestedEvent 발행               ▼
                                    [CrawlPipelineService]
                                         │
                                    ┌────▼────┐
                                    │ Firecrawl │  ← 크롤링
                                    └────┬────┘
                                         │ Markdown
                                    ┌────▼────┐
                                    │ Chunking │  ← 텍스트 분할
                                    └────┬────┘
                                         │ List<String>
                                    ┌────▼────┐
                                    │ Embedding│  ← 벡터화
                                    └────┬────┘
                                         │ List<FloatArray>
                                    ┌────▼────┐
                                    │ pgvector │  ← 저장
                                    └─────────┘
                                    status → CRAWLED
```

- `@Async` + Spring ApplicationEvent 사용
- 실패 시 status → FAILED, 재시도는 recrawl API로 수동 트리거

---

## 8. API 상세 설계

### 8.1 인증

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| POST | `/api/auth/oauth/{provider}` | OAuth 로그인 (auth code → JWT) | - |
| POST | `/api/auth/refresh` | Access Token 갱신 | - |

**POST /api/auth/oauth/{provider}**
```json
// Request
{ "code": "oauth_authorization_code", "redirectUri": "https://..." }

// Response 200
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresIn": 3600 }
```

### 8.2 URL 관리

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| POST | `/api/urls` | URL 등록 | Bearer |
| GET | `/api/urls` | URL 목록 조회 | Bearer |
| GET | `/api/urls/{id}` | URL 상세 조회 | Bearer |
| DELETE | `/api/urls/{id}` | URL 삭제 | Bearer |
| POST | `/api/urls/{id}/recrawl` | 재크롤링 | Bearer |

**POST /api/urls**
```json
// Request
{ "url": "https://example.com/article" }

// Response 202 Accepted
{
  "id": 1,
  "url": "https://example.com/article",
  "domain": "example.com",
  "status": "PENDING",
  "createdAt": "2026-02-15T10:00:00"
}
```

**GET /api/urls?page=0&size=20**
```json
// Response 200
{
  "content": [
    {
      "id": 1,
      "url": "https://example.com/article",
      "title": "Example Article",
      "domain": "example.com",
      "status": "CRAWLED",
      "createdAt": "2026-02-15T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3
}
```

### 8.3 검색

| Method | Path | 설명 | Auth |
|--------|------|------|------|
| POST | `/api/search` | 전체 URL 시맨틱 검색 | Bearer |
| POST | `/api/urls/{id}/search` | 특정 URL 시맨틱 검색 | Bearer |

**POST /api/search**
```json
// Request
{ "query": "Kotlin 관련 URL 찾아줘", "topK": 10 }

// Response 200
{
  "results": [
    {
      "urlId": 5,
      "url": "https://kotlinlang.org/docs",
      "title": "Kotlin Documentation",
      "domain": "kotlinlang.org",
      "matchedContent": "Kotlin is a modern programming language...",
      "similarity": 0.87
    }
  ]
}
```

**POST /api/urls/{id}/search**
```json
// Request
{ "query": "가격 얼마야?", "topK": 5 }

// Response 200
{
  "results": [
    {
      "urlId": 3,
      "url": "https://shop.example.com/product/123",
      "title": "Product Name",
      "domain": "shop.example.com",
      "matchedContent": "가격: 29,900원 (할인가 19,900원)...",
      "similarity": 0.92
    }
  ]
}
```

---

## 9. 보안 설계

### JWT 구조
```
Access Token  (만료: 1시간)  → API 요청 인증
Refresh Token (만료: 14일)   → Access Token 갱신
```

### 인증 흐름
```
[FE] → OAuth Provider → auth code 획득
  ↓
[FE] → POST /api/auth/oauth/{provider} { code, redirectUri }
  ↓
[BE] → OAuth Provider에 code로 사용자 정보 조회
  ↓
[BE] → User 조회 or 생성 → JWT 발급 → 응답
```

### Security Filter Chain
```
Request → JwtAuthenticationFilter → Controller
           │
           ├─ /api/auth/** → permitAll
           └─ /api/** → authenticated (JWT 검증)
```

---

## 10. 텍스트 청킹 전략

```
기본 설정 (application.yaml에서 조정 가능):
- chunk-size: 500 characters
- chunk-overlap: 100 characters
- separator: 문단(\n\n) 우선, 문장(. ) 차선
```

분할 로직:
1. Markdown → 문단 단위 분리
2. 문단이 chunk-size 초과 시 문장 단위로 재분할
3. 각 청크 간 overlap 적용 (문맥 유지)
4. 빈 청크 또는 의미 없는 청크 필터링

---

## 11. 설정 구조 (application.yaml)

```yaml
spring:
  application:
    name: url-jarvis
  datasource:
    url: jdbc:postgresql://localhost:5432/urljarvis
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
          kakao:
            client-id: ${KAKAO_CLIENT_ID}
            client-secret: ${KAKAO_CLIENT_SECRET}
          naver:
            client-id: ${NAVER_CLIENT_ID}
            client-secret: ${NAVER_CLIENT_SECRET}

url-jarvis:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiry: 3600        # 1 hour
    refresh-token-expiry: 1209600    # 14 days
  firecrawl:
    api-key: ${FIRECRAWL_API_KEY}
    base-url: https://api.firecrawl.dev/v1
  embedding:
    base-url: ${EMBEDDING_SERVER_URL}
    model: intfloat/multilingual-e5-small
    dimensions: 384
  chunking:
    chunk-size: 500
    chunk-overlap: 100
```

---

## 12. 의존성 (build.gradle.kts에 추가 필요)

```kotlin
dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Security + OAuth2
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // HTTP Client (Firecrawl, Embedding)
    implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
```

---

## 13. 구현 우선순위

### Phase 1: 기반 구축
1. 프로젝트 구조 생성 (패키지, 설정)
2. DB 스키마 + JPA 엔티티
3. JWT 인증 기반

### Phase 2: 핵심 기능
4. OAuth 소셜 로그인 (Google → Kakao → Naver)
5. URL 등록 + Firecrawl 연동
6. 텍스트 청킹 + 임베딩 연동
7. pgvector 저장

### Phase 3: 검색
8. 전체 URL 시맨틱 검색
9. 특정 URL 시맨틱 검색
10. URL 관리 (CRUD)

### Phase 4: 안정화
11. 에러 핸들링 + 재시도
12. 테스트 작성
13. API 문서화

---

## 14. 다음 단계

이 설계를 승인하면 `/sc:implement`로 Phase 1부터 구현을 시작합니다.
