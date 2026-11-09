# 05: 전체 Docker Compose 실행(채점용)

**What to build:** 채점자가 JDK와 Node 없이 `docker compose up --build` 하나로 DB, 백엔드, 프론트를 띄우고 서비스를 쓸 수 있다. `docker compose down` 후 다시 올려도 등록한 녹화방송이 남는다. 스펙: `.scratch/recordings/spec.md`.

02, 03, 04와 병행한다. 환경변수 이름(`DB_*`, `RECORDING_STORAGE_DIR`, `RECORDING_MAX_UPLOAD_SIZE`)은 스펙에서 확정되어 있으므로 03을 기다리지 않고 구성한다. 업로드 파일의 재시작 후 재생 확인은 03이 끝난 뒤 06에서 한다.

**Blocked by:** 01

**Status:** resolved

- [x] 백엔드 이미지가 빌드된다(멀티 스테이지, 이미지 안에서 Gradle 빌드)
- [x] 프론트 이미지가 정적 빌드를 서빙하고 `/api`를 백엔드로 프록시한다. 요청 본문 한도가 `RECORDING_MAX_UPLOAD_SIZE`와 같은 값이다
- [x] DB 데이터와 업로드 폴더가 이름 있는 볼륨이다
- [x] 백엔드는 DB가 준비된 뒤 시작한다(헬스체크 기반)
- [x] 루트에 `.env.example`이 있고, `.env`가 없어도 기본값으로 뜬다
- [x] 깨끗한 상태에서 `docker compose up --build` → 브라우저에서 외부 URL 녹화방송 등록 → `docker compose down` → `up` → 목록과 재생이 유지됨을 확인했다
- [x] DB만 띄우는 개발용 실행 방식도 그대로 동작한다

## Comments

### 구현 메모 (2026-11-09)

- **개발용 DB만 실행하는 방식:** compose profile을 쓰지 않는다. `docker compose up -d db`처럼 서비스 이름을 지정하면 그 서비스(와 의존 서비스)만 뜨는 compose 기본 동작을 쓴다. 그래서 채점용은 `docker compose up --build` 그대로, 개발용은 기존 `docker compose up -d db` 그대로다. profile로 나누면 채점용 명령에 `--profile`을 붙여야 해서 택하지 않았다. `db` 서비스 정의는 바꾸지 않았다.
- **이미지:** 백엔드는 `eclipse-temurin:21-jdk`에서 `./gradlew bootJar -x test`로 빌드하고 `eclipse-temurin:21-jre`에서 비루트 사용자로 실행한다(테스트는 Testcontainers가 Docker를 요구하므로 이미지 빌드에서 뺀다). 프론트는 `node:22-alpine`에서 `npm run build` 후 `nginx:1.29-alpine`이 정적 파일을 서빙하고, `/api/`를 `backend:8080`으로 프록시하며, 나머지 경로는 `try_files ... /index.html`로 SPA 라우팅에 넘긴다.
- **업로드 한도:** nginx `client_max_body_size`는 `500MB` 같은 Spring DataSize 형식을 받지 않는다. 그래서 `frontend/docker/15-upload-size.envsh`가 시작 시 `RECORDING_MAX_UPLOAD_SIZE`를 nginx 형식(`500m`, `1g`, 바이트 수)으로 바꾸고 nginx 이미지의 템플릿 envsubst가 채운다. 알 수 없는 형식이면 컨테이너가 시작을 거부한다. compose는 같은 `RECORDING_MAX_UPLOAD_SIZE`를 백엔드와 프론트 양쪽에 넘긴다. 백엔드 multipart 설정에 이 값을 연결하는 것은 03 범위다.
- **환경변수:** `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME`, `RECORDING_MAX_UPLOAD_SIZE`, `APP_PORT`(호스트 포트, 기본 3000; 8080은 다른 앱과 겹치기 쉬워 피함)를 `.env`로 바꿀 수 있다. 백엔드 컨테이너의 `DB_URL`(`db` 호스트), `SERVER_PORT`(8080), `RECORDING_STORAGE_DIR`(`/app/data/uploads`, `uploads` 볼륨 마운트 경로)는 컨테이너 내부 값이라 compose에서 고정한다. `.env`에 개발용 값(`localhost` DB 등)을 넣어도 컨테이너 구성이 깨지지 않게 하기 위해서다.
- **기동 순서:** 백엔드는 `db`의 `service_healthy` 뒤에, 프론트는 백엔드의 `service_healthy`(`/actuator/health`를 curl로 확인) 뒤에 시작한다. 프론트가 먼저 떠서 502가 나는 구간을 없앴다.
- **줄바꿈:** `core.autocrlf=true`인 Windows에서 체크아웃해도 컨테이너에서 sh로 도는 스크립트가 깨지지 않게 `frontend/docker/.gitattributes`로 LF를 고정했다.
- 애플리케이션 코드는 바꾸지 않았다.

### 확인 결과 (2026-11-09)

개발 DB(`chzzz-db-1`, 5432)가 떠 있어서 확인은 별도 프로젝트 이름(`-p chzzz-t05`)과 db 호스트 포트만 빼는 임시 override 파일로 했다. 그 외 구성은 그대로다. `.env` 없이 기본값으로 실행했다.

- `down -v`로 볼륨까지 지운 상태에서 `up -d --build --wait` → db, backend, frontend 모두 healthy. `http://localhost:3000/recordings/123` 직접 접근이 `index.html`을 받는다(SPA 폴백).
- 브라우저에서 `/recordings/new`로 외부 URL(MDN `flower.mp4`) 등록 → 상세 `/recordings/1`로 이동, `<video>` 재생 진행 확인.
- `down`(볼륨 유지) → `up -d --wait` → 목록에 같은 녹화방송이 있고 상세에서 재생이 진행됨을 확인.
- 업로드 한도: `RECORDING_MAX_UPLOAD_SIZE=1KB`로 띄운 프론트 컨테이너에 2KB 본문 POST → nginx `413`, 512B는 통과. `1GB`→`1g`, `2048KB`→`2048k`, `1048576`→`1048576`, `10mb`→`10m`, `abc`→시작 거부.
- 개발용: `docker compose up -d db`로 db 컨테이너만 뜬다. 실제 개발 프로젝트(`chzzz`)에 `--dry-run`으로 돌리면 `chzzz-db-1 Running`으로 기존 컨테이너·볼륨을 건드리지 않는다.
- 확인에 쓴 컨테이너, 볼륨, 이미지는 모두 지웠다. 업로드 파일의 재시작 후 재생 확인은 03 병합 뒤 06에서 한다.
