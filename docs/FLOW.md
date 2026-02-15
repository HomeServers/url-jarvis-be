# url-jarvis 전체 Flow

## 1. 인증 Flow

```
Client                    AuthController              AuthService            OAuthClientAdapter
  │                            │                          │                        │
  │  POST /api/auth/oauth/google                          │                        │
  │  { code, redirectUri }     │                          │                        │
  │ ──────────────────────────>│                          │                        │
  │                            │  loginWithOAuth()        │                        │
  │                            │ ────────────────────────>│                        │
  │                            │                          │  getUserInfo()         │
  │                            │                          │ ──────────────────────>│
  │                            │                          │                        │── code → Google Token API
  │                            │                          │                        │<─ access_token
  │                            │                          │                        │── token → Google UserInfo API
  │                            │                          │  OAuthUserInfo         │<─ { sub, email, name }
  │                            │                          │ <──────────────────────│
  │                            │                          │
  │                            │                          │── findByProviderAndProviderId()
  │                            │                          │   없으면 → save() (신규 회원)
  │                            │                          │
  │                            │                          │── JwtTokenProvider.generateAccessToken()
  │                            │                          │── JwtTokenProvider.generateRefreshToken()
  │                            │  TokenPair               │
  │  { accessToken,            │ <────────────────────────│
  │    refreshToken }          │
  │ <──────────────────────────│
```

## 2. URL 등록 + 크롤링 Pipeline

```
Client              UrlController          UrlService          SpringEventAdapter
  │                      │                     │                      │
  │  POST /api/urls      │                     │                      │
  │  { url: "https://..."}                     │                      │
  │ ────────────────────>│                     │                      │
  │  [JWT Bearer Token]  │  register()         │                      │
  │                      │ ───────────────────>│                      │
  │                      │                     │── URL 파싱 (domain 추출)
  │                      │                     │── save(status=PENDING)
  │                      │                     │── publishCrawlRequested(urlId)
  │                      │                     │                      │
  │                      │                     │ ────────────────────>│
  │  202 Accepted        │                     │    ApplicationEvent  │
  │  { url, status:      │ <───────────────────│    발행 (비동기)      │
  │    PENDING }         │                                            │
  │ <────────────────────│                                            │
                                                                      │
                                                                      ▼ @Async
 CrawlEventListener              CrawlPipelineService
       │                               │
       │  onCrawlRequested(urlId)       │
       │ ─────────────────────────────>│
                                       │
       ┌───────────────────────────────┘
       ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │  Step 1: 상태 변경                                               │
  │  updateStatus(urlId, CRAWLING)                                   │
  ├─────────────────────────────────────────────────────────────────┤
  │  Step 2: 크롤링                                                  │
  │  FirecrawlAdapter.crawl(url)                                     │
  │    → POST https://api.firecrawl.dev/v1/scrape                    │
  │    ← { markdown, title, description }                            │
  ├─────────────────────────────────────────────────────────────────┤
  │  Step 3: 텍스트 청킹                                             │
  │  TextChunkingService.chunk(markdown)                             │
  │                                                                  │
  │    "문단1\n\n문단2\n\n문단3..."                                    │
  │         │                                                        │
  │         ▼  \n\n 기준 분할                                         │
  │    ┌──────────┐ ┌──────────┐ ┌──────────┐                       │
  │    │  문단 1   │ │  문단 2   │ │  문단 3   │                       │
  │    │ (300자)   │ │ (800자)   │ │ (200자)   │                       │
  │    └──────────┘ └──────────┘ └──────────┘                       │
  │         │            │            │                              │
  │         ▼            ▼            ▼                              │
  │    500자 이하 →    500자 초과 →   버퍼에 합산                       │
  │    버퍼 누적     문장 단위 재분할   (overlap 100자)                  │
  │                                                                  │
  │    결과: [chunk1(480자), chunk2(490자), chunk3(450자), ...]       │
  ├─────────────────────────────────────────────────────────────────┤
  │  Step 4: 임베딩                                                  │
  │  EmbeddingRestAdapter.embedBatch(                                │
  │    chunks.map { "passage: " + it }    ← e5 prefix               │
  │  )                                                               │
  │    → POST http://localhost:8081/embed                            │
  │    ← [[0.012, -0.034, ...], ...]      ← 384차원 벡터             │
  ├─────────────────────────────────────────────────────────────────┤
  │  Step 5: 벡터 저장                                               │
  │  UrlChunkPersistenceAdapter.saveAll(chunks + embeddings)         │
  │    → INSERT INTO url_chunks (content, embedding::vector, ...)    │
  │    → pgvector HNSW 인덱스 자동 갱신                                │
  ├─────────────────────────────────────────────────────────────────┤
  │  Step 6: 상태 변경                                               │
  │  updateStatus(urlId, CRAWLED)                                    │
  │  (실패 시 → FAILED)                                               │
  └─────────────────────────────────────────────────────────────────┘
```

## 3. 검색 + LLM 답변 Flow

```
Client              SearchController           SearchService
  │                       │                         │
  │  POST /api/search     │                         │
  │  { query: "가격?",    │                         │
  │    topK: 5 }          │                         │
  │ ─────────────────────>│  searchAll()            │
  │                       │ ───────────────────────>│
  │                       │                         │
  │                       │    ┌────────────────────┘
  │                       │    ▼
  │                       │  ┌──────────────────────────────────────┐
  │                       │  │ Step 1: 쿼리 임베딩                    │
  │                       │  │ EmbeddingClient.embed("query: 가격?") │
  │                       │  │   → [0.023, -0.051, ...]  (384차원)   │
  │                       │  ├──────────────────────────────────────┤
  │                       │  │ Step 2: pgvector 유사도 검색            │
  │                       │  │ UrlChunkRepository.searchByUserId(    │
  │                       │  │   userId, queryEmbedding, topK=5      │
  │                       │  │ )                                     │
  │                       │  │                                       │
  │                       │  │ SQL:                                  │
  │                       │  │ SELECT c.*, u.url, u.title,           │
  │                       │  │   1 - (c.embedding <=> ?::vector)     │
  │                       │  │   AS similarity                       │
  │                       │  │ FROM url_chunks c JOIN urls u ...      │
  │                       │  │ WHERE u.user_id = ?                   │
  │                       │  │ ORDER BY c.embedding <=> ?::vector    │
  │                       │  │ LIMIT 5                               │
  │                       │  │                                       │
  │                       │  │ 결과:                                  │
  │                       │  │ ┌─────────────────────────────────┐   │
  │                       │  │ │ chunk1: "상품A 가격 29,000원..."  │   │
  │                       │  │ │ chunk2: "배송비 포함 총 가격..."   │   │
  │                       │  │ │ chunk3: "할인가 적용 시..."       │   │
  │                       │  │ │ chunk4: "대량 구매 가격표..."     │   │
  │                       │  │ │ chunk5: "가격 비교 결과..."       │   │
  │                       │  │ └─────────────────────────────────┘   │
  │                       │  ├──────────────────────────────────────┤
  │                       │  │ Step 3: 컨텍스트 구성                  │
  │                       │  │                                       │
  │                       │  │ "[출처 1: 상품 소개 페이지]             │
  │                       │  │  상품A 가격 29,000원...                │
  │                       │  │                                       │
  │                       │  │  [출처 2: 배송 안내]                   │
  │                       │  │  배송비 포함 총 가격...                 │
  │                       │  │  ..."                                 │
  │                       │  ├──────────────────────────────────────┤
  │                       │  │ Step 4: LLM 답변 생성                  │
  │                       │  │ OpenAiAdapter.generate(query, context) │
  │                       │  │                                       │
  │                       │  │ → POST https://api.openai.com/v1/     │
  │                       │  │   chat/completions                    │
  │                       │  │   {                                   │
  │                       │  │     model: "gpt-4o-mini",             │
  │                       │  │     messages: [                       │
  │                       │  │       { system: "컨텍스트 기반 답변..." },│
  │                       │  │       { user: "[컨텍스트]...[질문]..." } │
  │                       │  │     ]                                 │
  │                       │  │   }                                   │
  │                       │  │                                       │
  │                       │  │ ← "상품A의 가격은 29,000원입니다.       │
  │                       │  │    배송비 포함 시 32,000원이며..."       │
  │                       │  └──────────────────────────────────────┘
  │                       │                         │
  │  200 OK               │ <───────────────────────│
  │  {                    │
  │    answer: "상품A의 가격은 29,000원입니다...",
  │    sources: [
  │      { urlId: 1, url: "https://...", title: "상품 소개",
  │        matchedContent: "상품A 가격 29,000원...", similarity: 0.92 },
  │      { urlId: 1, url: "https://...", title: "배송 안내",
  │        matchedContent: "배송비 포함...", similarity: 0.87 },
  │      ...
  │    ]
  │  }
  │ <─────────────────────│
```

## 4. 전체 아키텍처 요약

```
┌─ Client (SPA) ────────────────────────────────────────────────────────┐
│   Authorization: Bearer <JWT>                                         │
└───────┬──────────────┬──────────────┬─────────────────────────────────┘
        │              │              │
        ▼              ▼              ▼
┌─ Driving Adapters ───────────────────────────────────────────────────┐
│  AuthController    UrlController    SearchController                  │
│  /api/auth/**      /api/urls/**     /api/search, /api/urls/*/search  │
└───────┬──────────────┬──────────────┬────────────────────────────────┘
        │              │              │
        ▼              ▼              ▼
┌─ Application Layer (Use Cases) ──────────────────────────────────────┐
│  AuthService       UrlService       SearchService                     │
│                    CrawlPipelineService                               │
│                    TextChunkingService                                │
└───┬──────┬──────┬──────┬──────┬──────┬───────────────────────────────┘
    │      │      │      │      │      │
    ▼      ▼      ▼      ▼      ▼      ▼
┌─ Output Ports ───────────────────────────────────────────────────────┐
│ UserRepo  UrlRepo  UrlChunkRepo  OAuthClient  EmbeddingClient        │
│ WebCrawler  CrawlEventPublisher  LlmClient                          │
└───┬──────┬──────┬──────┬──────┬──────┬──────┬────────────────────────┘
    │      │      │      │      │      │      │
    ▼      ▼      ▼      ▼      ▼      ▼      ▼
┌─ Driven Adapters ────────────────────────────────────────────────────┐
│ UserPersistence   OAuthClient    Firecrawl    EmbeddingRest  OpenAi  │
│ UrlPersistence    Adapter        Adapter      Adapter        Adapter │
│ UrlChunkPersistence              SpringEvent                         │
│ (JdbcTemplate+pgvector)          Adapter                             │
└───┬────────────────┬─────────────┬────────────┬──────────────┬───────┘
    │                │             │            │              │
    ▼                ▼             ▼            ▼              ▼
 PostgreSQL      Google/Kakao   Firecrawl   Embedding      OpenAI
 + pgvector      /Naver OAuth   Cloud API   Server(e5)     API
 (port 5433)                                (port 8081)    (gpt-4o-mini)
```
