# URL Jarvis

URL을 저장하면 자동으로 크롤링하고, 벡터 임베딩 기반으로 저장된 URL 내용을 검색할 수 있는 RAG 백엔드 서비스.

Chrome Extension 단축키(`Ctrl+Shift+L` / `Cmd+Shift+L`)로 현재 탭 URL을 바로 저장할 수 있다.

## Tech Stack

- **Language**: Kotlin 2.2 / Java 21
- **Framework**: Spring Boot 4.0
- **Database**: PostgreSQL 16 + pgvector
- **Embedding**: OpenAI text-embedding-3-small (1536차원)
- **LLM**: GPT-4o-mini
- **Crawling**: Firecrawl API (CAPTCHA/보안 페이지 자동 감지)
- **Auth**: OAuth 2.0 (Google / Kakao / Naver) + JWT
- **Client**: Chrome Extension (Manifest V3)

## Architecture

Hexagonal Architecture (Ports & Adapters)

```
adapter/
  in/web/          # Controller, DTO
  out/
    persistence/   # JPA Entity, Repository
    crawling/      # Firecrawl Client
    embedding/     # OpenAI Embedding Client
    llm/           # OpenAI Chat Client
    auth/          # OAuth Client
application/
  service/         # Use Case 구현
  port/            # Input/Output Port 인터페이스
domain/            # 도메인 모델
infrastructure/    # Config, Security
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/{provider}` | OAuth 로그인 |
| POST | `/api/auth/refresh` | 토큰 갱신 |
| POST | `/api/urls` | URL 저장 + 크롤링 |
| GET | `/api/urls` | 저장된 URL 목록 |
| DELETE | `/api/urls/{id}` | URL 삭제 |
| POST | `/api/search` | RAG 검색 |

## Pipeline

```
URL 저장 → Firecrawl 크롤링 → CAPTCHA 감지 → 마크다운 전처리 → 텍스트 청킹 → 임베딩 → pgvector 저장
검색 쿼리 → 쿼리 임베딩 → 하이브리드 검색(벡터+키워드 RRF) → top-K 컨텍스트 → LLM 답변 생성
```

## Local Development

```bash
# 요구사항: Java 21, PostgreSQL (pgvector 확장)

# 환경변수 설정 (src/main/resources/application-dev.yaml)
# FIRECRAWL_API_KEY, OPENAI_API_KEY 필요

# 빌드 및 실행
./gradlew bootRun
```

## Deployment (K8s)

```bash
# Docker 이미지 빌드 (amd64)
docker buildx build --platform linux/amd64 -t nuhgnod/url-jarvis-api:latest --push .

# K8s 배포 (jarvis 네임스페이스)
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Secret은 별도 생성 필요 (DB, JWT, API 키 등)
```

## Chrome Extension

`chrome-extension/` 디렉토리에 Manifest V3 기반 Chrome Extension이 포함되어 있다.

- **단축키**: `Ctrl+Shift+L` (Mac: `Cmd+Shift+L`)로 현재 탭 URL 저장
- **인증**: Google OAuth → JWT 자동 갱신
- **설정**: 옵션 페이지에서 서버 URL 변경 가능

```bash
# 설치: chrome://extensions → 개발자 모드 → 압축해제된 확장 프로그램 로드 → chrome-extension/ 선택
```

## Project Structure

```
url-jarvis/
├── src/main/kotlin/io/hunknownn/urljarvis/
├── src/main/resources/
│   ├── application.yaml
│   ├── application-prod.yaml
│   └── db/migration/          # SQL 스키마
├── chrome-extension/          # Chrome Extension (Manifest V3)
├── k8s/                       # K8s 매니페스트
├── Dockerfile
└── build.gradle.kts
```
