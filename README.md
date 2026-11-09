# chzzz

로그인 없이 누구나 **녹화방송**을 등록하고 시청하는 서비스입니다. 녹화방송은 제목과 영상 출처(업로드 파일 또는 외부 URL) 하나를 가집니다. 용어는 [GLOSSARY.md](GLOSSARY.md)를 따릅니다.

- 백엔드: Spring Boot 4.1 + Kotlin, JDK 21, Gradle(Kotlin DSL), PostgreSQL 17 + Flyway
- 프론트엔드: React + Vite + TypeScript, React Router
- 실행: Docker Compose(DB, 백엔드, 프론트엔드)

## 단계별 구현 내역

과제는 4단계로 진행합니다.

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1 | 녹화방송 등록·목록·상세·재생 (TDD) | 완료 |
| 2 | 라이브 방송 송출 | 예정 |
| 3 | 인증·인가, 댓글 | 예정 |
| 4 | 도네이션 | 예정 |

## 실행 방법

### 전체 실행 (채점용)

필요한 도구: Docker (Docker Compose v2 포함). JDK와 Node는 필요 없습니다.

```bash
docker compose up --build
```

세 서비스(db, backend, frontend)가 모두 healthy가 되면 브라우저에서 **http://localhost:3000** 에 접속합니다. 백그라운드로 띄우려면 `docker compose up -d --build --wait`를 씁니다.

- 멈추기: `docker compose down` (DB 데이터와 업로드 파일은 볼륨에 남습니다)
- 데이터까지 지우기: `docker compose down -v`
- 3000 포트가 이미 쓰이고 있으면 `.env`에 `APP_PORT=다른포트`를 적습니다.

### 개발 실행

필요한 도구: Docker, JDK 21, Node.js 22 이상.

```bash
docker compose up -d db
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

브라우저에서 http://localhost:5173 에 접속합니다. Vite 개발 서버가 `/api` 요청을 백엔드(기본 http://localhost:8080)로 넘깁니다.

8080 포트를 다른 프로그램이 쓰고 있으면 백엔드 포트와 프록시 대상을 함께 바꿉니다.

```bash
cd backend && SERVER_PORT=8081 ./gradlew bootRun
```

```bash
cd frontend && VITE_API_PROXY_TARGET=http://localhost:8081 npm run dev
```

## 환경변수

### Docker Compose (`.env`)

[.env.example](.env.example)을 `.env`로 복사해서 고칩니다. `.env`가 없으면 아래 기본값으로 뜹니다.

```dotenv
APP_PORT=3000
DB_NAME=chzzz
DB_USERNAME=chzzz
DB_PASSWORD=chzzz
RECORDING_MAX_UPLOAD_SIZE=500MB
```

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `APP_PORT` | `3000` | 브라우저로 접속할 호스트 포트 |
| `DB_NAME` | `chzzz` | PostgreSQL 데이터베이스 이름 |
| `DB_USERNAME` | `chzzz` | DB 계정 |
| `DB_PASSWORD` | `chzzz` | DB 비밀번호 |
| `RECORDING_MAX_UPLOAD_SIZE` | `500MB` | 최대 업로드 크기. 백엔드 multipart 한도와 프론트 nginx 요청 본문 한도에 같은 값이 적용됩니다 |

### 백엔드 (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/chzzz` | DB 접속 주소 |
| `DB_USERNAME` / `DB_PASSWORD` | `chzzz` / `chzzz` | DB 계정 |
| `SERVER_PORT` | `8080` | 백엔드 포트 |
| `RECORDING_STORAGE_DIR` | `./data/uploads` | 업로드 파일 보관 폴더(없으면 시작할 때 만듭니다) |
| `RECORDING_MAX_UPLOAD_SIZE` | `500MB` | 최대 업로드 크기 |

### 프론트엔드 (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | Vite가 `/api`를 넘길 백엔드 주소 |

전체 실행에서는 `DB_URL`, `SERVER_PORT`, `RECORDING_STORAGE_DIR`를 `docker-compose.yml`이 컨테이너 내부 값으로 고정합니다.

## 구현 범위

### 구현한 것 (1단계)

- **등록** (`/recordings/new`): 제목과 영상 출처를 입력합니다. 영상 출처는 업로드 파일과 외부 URL 중 하나를 고릅니다.
  - 제목: 앞뒤 공백을 지우고 1~100자
  - 업로드 파일: mp4, webm. 최대 크기는 `RECORDING_MAX_UPLOAD_SIZE`
  - 외부 URL: `http`/`https` 주소. 브라우저 `<video>`가 바로 재생할 수 있는 영상 파일 주소여야 합니다
  - 화면이 먼저 입력을 검사하고, 서버가 최종 판단합니다. 서버가 거절하면 이유를 입력란 옆에 보여 줍니다
  - 업로드 중에는 등록 버튼이 비활성화되고, 성공하면 상세 화면으로 이동합니다
- **목록** (`/`): 최신 등록순. 제목, 등록일시(브라우저 로컬 시간), 출처 종류(업로드/URL)를 보여 줍니다. 비어 있으면 안내와 등록 버튼을 보여 줍니다.
- **상세** (`/recordings/:id`): 제목, 등록일시, 영상 플레이어. 업로드 파일은 HTTP Range 요청으로 스트리밍해서 원하는 위치로 탐색할 수 있습니다. 재생에 실패하면 안내 문구를, 없는 녹화방송이면 "찾을 수 없음"과 목록 링크를 보여 줍니다.
- **영속 저장**: 녹화방송 메타데이터는 PostgreSQL에, 업로드 파일은 디스크(Compose에서는 볼륨)에 저장되어 재시작 후에도 남습니다. 스키마는 Flyway로 관리합니다.
- **로그인 없음**: 모든 화면과 API를 인증 없이 씁니다.

### API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/recordings` | `multipart/form-data`: `title` + `file` 또는 `url`. 성공 `201` + `Location` |
| `GET` | `/api/recordings` | 목록(최신순) |
| `GET` | `/api/recordings/{id}` | 상세. 없으면 `404` |
| `GET` | `/api/recordings/{id}/video` | 업로드 파일 스트리밍. `Range` 요청에 `206`, 범위 밖 `416`, 업로드 파일이 아니거나 없으면 `404` |

오류는 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식입니다. 입력 검증 실패(`400`)는 `errors` 배열에 `{ field, message }`를 담습니다. 업로드 크기를 넘으면 `413`입니다.

### 구현하지 않은 것

- 녹화방송 수정, 삭제, 검색, 페이지네이션, 썸네일
- 외부 URL의 재생 가능 여부를 서버가 확인하는 기능: 서버가 사용자가 준 주소로 요청하면 내부망 접근(SSRF) 위험이 있어 형식만 검사합니다([ADR-0002](docs/adr/0002-no-server-check-for-external-url.md)). 재생할 수 없는 주소는 상세 화면의 안내 문구로 알립니다.
- 유튜브 같은 플랫폼 링크, HLS/DASH 스트리밍 주소
- mp4/webm 이외 형식, 서버 쪽 트랜스코딩
- 업로드 이어 올리기, 진행률 표시
- 로그인, 등록자, 권한(3단계), 라이브 방송(2단계), 댓글(3단계), 도네이션(4단계)
- E2E 자동화 테스트(대신 아래 직접 확인 절차)

## 직접 확인 절차

전체 실행 기준입니다. 아래 순서대로 확인했습니다.

**준비**: 깨끗한 상태로 시작합니다. 업로드용 mp4 파일(예: 휴대폰으로 찍은 짧은 영상)을 하나 준비합니다.

```bash
docker compose down -v
```

```bash
docker compose up --build
```

1. **빈 목록**: http://localhost:3000 에 접속합니다. "아직 등록된 녹화방송이 없습니다."와 "첫 녹화방송 등록하기" 버튼이 보이면 통과입니다.
2. **외부 URL 등록 → 재생**: "첫 녹화방송 등록하기"를 누르고, 제목을 입력하고, 영상 출처는 "외부 URL" 그대로 두고 아래 주소를 넣은 뒤 "등록"을 누릅니다. 상세 화면으로 이동하고 재생 버튼을 눌러 영상이 재생되면 통과입니다.
   ```text
   https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4
   ```
3. **업로드 파일 등록 → 재생·탐색**: 헤더의 "녹화방송 등록"에서 제목을 입력하고, 영상 출처를 "업로드 파일"로 바꾸고, mp4 파일을 고른 뒤 "등록"을 누릅니다. 상세 화면으로 이동해 재생되고, 재생 막대를 눌러 중간 위치로 이동한 뒤에도 그 위치부터 재생되면 통과입니다.
4. **목록**: 헤더의 "목록"을 누릅니다. 두 녹화방송이 최신 등록순(업로드한 것이 위)으로, 등록일시와 출처 종류 배지("업로드", "URL")와 함께 보이면 통과입니다.
5. **잘못된 입력**: 등록 화면에서 다음을 각각 시도하고, 등록되지 않고 안내가 보이면 통과입니다.
   - 아무것도 입력하지 않고 "등록": "제목을 입력해 주세요.", "영상 URL을 입력해 주세요."
   - 외부 URL에 `ftp://example.com/video.mp4`: "http 또는 https로 시작하는 영상 주소를 입력해 주세요."
   - 업로드 파일에 `.avi` 같은 다른 형식의 파일: "mp4 또는 webm 파일만 올릴 수 있습니다."
6. **없는 녹화방송**: http://localhost:3000/recordings/9999 에 접속합니다. "찾을 수 없음"과 "목록으로 돌아가기" 링크가 보이면 통과입니다.
7. **재시작 후 유지**: 서비스를 멈췄다가 다시 띄웁니다(`-v` 없이). 목록에 두 녹화방송이 그대로 있고, 둘 다 상세 화면에서 재생되면 통과입니다.
   ```bash
   docker compose down
   ```
   ```bash
   docker compose up
   ```

화면 예시는 [docs/screenshots/stage-1](docs/screenshots/stage-1)에 있습니다.

## 테스트

테스트는 TDD(red → green)로 작성했고, 사이클 기록은 [docs/tdd-log.md](docs/tdd-log.md)에 있습니다.

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm test
```

- 백엔드: Spring Boot 앱 전체를 띄우고 REST API로만 검증합니다. DB는 Testcontainers로 실제 PostgreSQL을 띄우므로 **Docker가 실행 중이어야** 합니다.
- 프론트엔드: Vitest + React Testing Library로 라우터를 포함한 앱 전체를 렌더링하고, 네트워크 경계(`fetch`)만 스텁합니다.

## 문서

- [GLOSSARY.md](GLOSSARY.md): 도메인 용어
- [docs/adr/](docs/adr/): 설계 결정 기록
- [docs/tdd-log.md](docs/tdd-log.md): TDD 사이클 기록
- [AGENTS.md](AGENTS.md): 작업 지침
