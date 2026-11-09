# 01: 외부 URL 녹화방송 등록 → 목록 → 상세 재생 (골격)

**What to build:** 개발 실행 방식으로 서비스를 띄운다(DB만 Docker Compose, 백엔드 `bootRun`, 프론트 `npm run dev` + `/api` 프록시). 사용자가 등록 화면에서 제목과 외부 URL을 입력해 녹화방송을 등록하면 목록 화면에 나타나고, 항목을 눌러 상세 화면으로 가면 플레이어로 재생된다. 이 티켓이 백엔드·프론트·DB·테스트의 뼈대를 만들고, 02~05가 모두 이 위에서 병행으로 진행된다. 스펙: `.scratch/recordings/spec.md`.

병행 작업을 위해 이 티켓에서 공용 기반을 먼저 마련한다:
- 녹화방송 모듈과 영상 출처("종류 + 값", 이번에는 `EXTERNAL_URL`만 구현)
- 오류 응답 형식: RFC 9457 Problem Details와 필드별 `errors` 배열의 공통 처리(02, 03이 규칙만 추가하면 되도록)
- 프론트의 API 클라이언트, 라우팅(목록/등록/상세), 등록 폼 골격, 테스트 기반(`fetch` 스텁)
- 백엔드 테스트 기반(Testcontainers PostgreSQL). 임시 업로드 폴더는 업로드 저장소가 생기는 03에서 추가한다

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Gradle wrapper(Kotlin DSL), Spring Boot 4.1.x, Kotlin, JDK 21 백엔드가 빌드되고 테스트가 돈다
- [x] Flyway 첫 마이그레이션이 녹화방송 테이블을 만들고, JPA는 `validate` 모드다
- [x] `POST /api/recordings`에 `title`과 `url`을 보내면 `201` + `Location` + 상세 본문을 돌려준다
- [x] `GET /api/recordings`가 등록일시 내림차순 목록(`id`, `title`, `sourceType`, `createdAt`)을 돌려준다
- [x] `GET /api/recordings/{id}`가 `videoUrl` = 외부 URL인 상세를 돌려준다
- [x] 제목이 비면 `400` Problem Details(`errors`에 `title`)로 응답한다. 나머지 검증 규칙은 02에서 추가한다
- [x] 위 동작이 백엔드 HTTP API 경계 테스트(Testcontainers)로 검증된다
- [x] 프론트에서 외부 URL로 등록 → 상세로 이동 → `<video>`의 `src`가 외부 URL인 흐름이 앱 단위 테스트로 검증된다
- [x] 목록 화면이 등록된 녹화방송의 제목을 보여 주고 상세로 연결된다(앱 단위 테스트)
- [x] DB만 띄우는 compose 서비스와 개발 실행 방식으로 실제 브라우저에서 등록·목록·재생을 확인했다 (등록은 Vite 프록시를 거친 실제 API 호출로, 목록 → 상세 이동과 재생은 브라우저에서 확인. 등록 폼 클릭 흐름은 앱 단위 테스트로 검증)
- [x] 모든 사이클이 `docs/tdd-log.md`에 기록되어 있다

## Comments

- 8080 포트를 다른 로컬 앱이 쓰고 있어 백엔드 포트를 `SERVER_PORT`(기본 8080), Vite 프록시 대상을 `VITE_API_PROXY_TARGET`(기본 http://localhost:8080)으로 바꿀 수 있게 했다.
- 브라우저 확인 중 등록 응답과 조회의 등록일시 정밀도가 다른 버그를 발견해 TDD로 고쳤다(tdd-log #7).
