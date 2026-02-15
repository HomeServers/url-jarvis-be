# URL Jarvis - 요구사항 명세서

> URL 기반 시맨틱 검색 서비스
> 생성일: 2026-02-15

---

## 1. 프로젝트 개요

**url-jarvis**는 사용자가 등록한 URL의 콘텐츠를 크롤링하여 벡터화하고,
시맨틱 검색을 통해 원하는 URL을 빠르게 찾을 수 있게 해주는 서비스이다.

### 핵심 가치
- URL을 등록하면 콘텐츠를 자동으로 수집/색인
- 자연어 질의로 저장된 URL 검색 (시맨틱 검색)
- 특정 URL 내 콘텐츠 검색 + 전체 URL 대상 검색 지원

---

## 2. 기술 스택

| 구분 | 기술 |
|------|------|
| Backend | Spring Boot 4.0.2 + Kotlin 2.2.21 + Java 21 |
| Database | PostgreSQL + pgvector |
| Crawling | Firecrawl Cloud API (SaaS) |
| Embedding | intfloat/multilingual-e5-small (외부 서버) |
| Auth | OAuth 2.0 (Google, Kakao, Naver) |
| API | REST API only (Frontend 별도 구축) |

---

## 3. 기능 요구사항

### FR-1: URL 등록

- 사용자가 URL을 입력하면 시스템에 등록
- Firecrawl Cloud API를 호출하여 해당 URL의 콘텐츠를 Markdown으로 추출
- 추출된 Markdown을 청크(chunk) 단위로 분할
- 각 청크를 외부 임베딩 서버(multilingual-e5-small)로 벡터화
- 벡터 데이터를 pgvector에 저장
- 원본 URL 메타데이터(제목, 설명, 도메인 등)를 PostgreSQL에 저장

### FR-2: 특정 URL 대상 질의

- 사용자가 특정 URL(또는 URL의 PK)을 지정하여 질의
- 해당 URL의 청크들만을 대상으로 벡터 유사도 검색 수행
- 유사도 높은 청크와 해당 URL 정보를 반환
- 예시: "이 상품 가격 얼마야?", "이 URL과 연관된 URL 찾아줘"

### FR-3: 전체 URL 대상 질의

- 사용자의 모든 저장된 URL을 대상으로 벡터 유사도 검색 수행
- 유사도 높은 URL 목록(PK, URL, 메타데이터)을 반환
- 예시: "Kotlin 관련 URL 찾아줘", "라멘 맛집 찾아둔 URL 뭐였지?"

### FR-4: URL 관리

- URL 목록 조회 (페이지네이션)
- URL 삭제 (관련 벡터 데이터 함께 삭제)
- URL 재크롤링 (콘텐츠 갱신)

### FR-5: 사용자 인증

- OAuth 2.0 소셜 로그인: Google, Kakao, Naver
- 사용자별 독립된 URL 저장소
- JWT 기반 세션 관리

---

## 4. 비기능 요구사항

### NFR-1: 응답 형식
- LLM 텍스트 생성 없이, 벡터 검색 결과(URL/PK)를 JSON으로 반환
- 향후 LLM 응답 생성 기능 추가 가능성 고려한 구조 설계

### NFR-2: 확장성
- 임베딩 모델 교체 가능한 추상화 레이어
- LLM 통합을 위한 확장 포인트 확보

### NFR-3: 보안
- OAuth 토큰 안전한 관리
- 사용자 간 데이터 격리 (URL, 벡터 데이터)

### NFR-4: 성능
- 크롤링은 비동기 처리 (URL 등록 시 즉시 응답, 백그라운드에서 크롤링/임베딩)
- 벡터 검색 응답 시간 목표: < 500ms

---

## 5. 핵심 API 엔드포인트 (예상)

```
# 인증
POST   /api/auth/login/{provider}     - OAuth 로그인
POST   /api/auth/refresh               - 토큰 갱신

# URL 관리
POST   /api/urls                       - URL 등록
GET    /api/urls                       - URL 목록 조회
GET    /api/urls/{id}                  - URL 상세 조회
DELETE /api/urls/{id}                  - URL 삭제
POST   /api/urls/{id}/recrawl          - URL 재크롤링

# 검색
POST   /api/search                     - 전체 URL 대상 시맨틱 검색
POST   /api/search/url/{id}            - 특정 URL 대상 시맨틱 검색
```

---

## 6. 데이터 모델 (개념)

### User
- id, email, name, provider, providerId, createdAt

### Url
- id, userId, url, title, description, domain, status(PENDING/CRAWLED/FAILED), createdAt, updatedAt

### UrlChunk
- id, urlId, content, chunkIndex, embedding(vector), createdAt

---

## 7. 시스템 흐름

```
[사용자] → URL 등록 요청
    ↓
[API Server] → URL 메타데이터 저장 (status: PENDING)
    ↓ (비동기)
[Firecrawl API] → URL 크롤링 → Markdown 반환
    ↓
[Chunking] → Markdown을 청크 단위로 분할
    ↓
[Embedding Server] → 각 청크 벡터화 (multilingual-e5-small)
    ↓
[pgvector] → 벡터 + 청크 저장 (status: CRAWLED)
```

```
[사용자] → 검색 질의
    ↓
[Embedding Server] → 질의 텍스트 벡터화
    ↓
[pgvector] → 코사인 유사도 검색
    ↓
[API Server] → 매칭된 URL/PK 목록 반환
```

---

## 8. 미결 사항 (Open Questions)

1. **청크 전략**: 텍스트 분할 크기 및 오버랩 설정은?
2. **검색 결과 개수**: 기본 반환 개수(top-k)는?
3. **유사도 임계값**: 최소 유사도 점수 기준은?
4. **크롤링 제한**: URL당 최대 콘텐츠 크기 제한은?
5. **임베딩 서버 API 형식**: 외부 서버의 API 스펙은? (REST? gRPC?)
6. **OAuth 콜백 URL**: 프론트엔드 도메인/URL 구조는?
7. **에러 핸들링**: 크롤링 실패 시 재시도 정책은?

---

## 9. 다음 단계

이 요구사항을 바탕으로:
1. `/sc:design` → 아키텍처 및 패키지 구조 설계
2. `/sc:implement` → 기능별 구현
3. `/sc:test` → 테스트 작성 및 검증
