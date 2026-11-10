# chzzz

로그인 없이 누구나 **녹화방송**을 등록해 시청하고, 휴대폰 카메라·마이크로 **라이브 방송**을 송출해 다른 화면에서 시청하는 서비스입니다. 용어는 [GLOSSARY.md](GLOSSARY.md)를 따릅니다.

- 백엔드: Spring Boot 4.1 + Kotlin, JDK 21, Gradle(Kotlin DSL), PostgreSQL 17 + Flyway
- 프론트엔드: React + Vite + TypeScript, React Router
- 라이브 송출: WebRTC WHIP(송출)·WHEP(시청) + [MediaMTX](https://github.com/bluenviron/mediamtx) ([ADR-0003](docs/adr/0003-webrtc-whip-whep-with-mediamtx.md))
- 휴대폰 HTTPS: Caddy 로컬 인증서 ([ADR-0004](docs/adr/0004-https-with-caddy-local-ca.md))
- 실행: Docker Compose(db, backend, frontend, mediamtx, caddy)

## 단계별 구현 내역

과제는 4단계로 진행합니다.

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1 | 녹화방송 등록·목록·상세·재생 (TDD) | 완료 |
| 2 | 라이브 방송 송출·시청, 방송 중·종료 표시 (TDD) | 완료 |
| 3 | 인증·인가, 댓글 | 예정 |
| 4 | 도네이션 | 예정 |

## 실행 방법

### 전체 실행 (채점용)

필요한 도구: Docker (Docker Compose v2 포함). JDK와 Node는 필요 없습니다.

```bash
docker compose up --build
```

다섯 서비스(db, mediamtx, backend, frontend, caddy)가 모두 healthy가 되면 접속합니다. 백그라운드로 띄우려면 `docker compose up -d --build --wait`를 씁니다.

- PC: **http://localhost:3000**
- 휴대폰(같은 Wi-Fi): **https://`PUBLIC_HOST`:8443** (아래 [휴대폰 연결](#휴대폰-연결))
- 멈추기: `docker compose down` (DB 데이터, 업로드 파일, HTTPS 인증서는 볼륨에 남습니다)
- 데이터까지 지우기: `docker compose down -v` (인증서도 새로 만들어지므로 휴대폰에서 경고를 다시 넘겨야 합니다)
- 3000 포트가 이미 쓰이고 있으면 `.env`에 `APP_PORT=다른포트`를 적습니다.

### 개발 실행

필요한 도구: Docker, JDK 21, Node.js 22 이상. PC `localhost`에서만 확인합니다(휴대폰 확인은 전체 실행으로 합니다).

MediaMTX가 호스트에서 도는 백엔드를 부를 수 있게 주소를 함께 줍니다.

```bash
MEDIAMTX_BACKEND_URL=http://host.docker.internal:8080 docker compose up -d db mediamtx
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

브라우저에서 http://localhost:5173 에 접속합니다. Vite 개발 서버가 `/api`를 백엔드(기본 http://localhost:8080)로, `/media`를 MediaMTX(기본 http://localhost:8889)로 넘깁니다. PC 웹캠이 있으면 `/live/new`에서 송출할 수 있습니다(`localhost`는 HTTPS가 아니어도 카메라를 쓸 수 있습니다).

8080 포트를 다른 프로그램이 쓰고 있으면 백엔드 포트, 프록시 대상, MediaMTX 훅 주소를 함께 바꿉니다.

```bash
MEDIAMTX_BACKEND_URL=http://host.docker.internal:8081 docker compose up -d db mediamtx
```

```bash
cd backend && SERVER_PORT=8081 ./gradlew bootRun
```

```bash
cd frontend && VITE_API_PROXY_TARGET=http://localhost:8081 npm run dev
```

## 휴대폰 연결

휴대폰 브라우저는 HTTPS 주소에서만 카메라·마이크를 허용합니다. 그래서 전체 실행에는 Caddy가 PC의 LAN IP로 HTTPS 인증서를 직접 만들어 8443 포트로 서비스합니다. 영상은 MediaMTX의 8189 포트(UDP, TCP)로 오갑니다.

1. **같은 Wi-Fi**: 휴대폰과 PC를 같은 공유기(같은 Wi-Fi)에 연결합니다. 모바일 데이터(LTE·5G)로는 접속할 수 없습니다.
2. **PC의 LAN IP 확인**: Windows는 `ipconfig`에서 Wi-Fi 또는 이더넷 어댑터의 `IPv4 주소`(예: `192.168.0.5`)를 찾습니다. VPN·WSL·Docker 가상 어댑터의 주소는 쓰지 않습니다. macOS는 `ipconfig getifaddr en0`입니다.
3. **`.env`에 `PUBLIC_HOST` 지정**: [.env.example](.env.example)을 `.env`로 복사하고 `PUBLIC_HOST`를 위 IP로 바꾼 뒤 전체 실행을 띄웁니다(이미 떠 있으면 다시 `docker compose up -d --build --wait`). 이 값이 HTTPS 인증서 주소와 영상이 오갈 주소(ICE 후보)에 쓰입니다.
   ```dotenv
   PUBLIC_HOST=192.168.0.5
   ```
4. **방화벽 허용 (Windows)**: 관리자 PowerShell에서 실행합니다. 같은 LAN(로컬 서브넷)의 기기만 들어오도록 제한합니다. 포트를 바꿨으면 숫자를 `.env` 값으로 고칩니다.
   ```powershell
   New-NetFirewallRule -DisplayName "chzzz HTTPS 8443/TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443 -RemoteAddress LocalSubnet -Profile Any
   New-NetFirewallRule -DisplayName "chzzz WebRTC 8189/UDP" -Direction Inbound -Action Allow -Protocol UDP -LocalPort 8189 -RemoteAddress LocalSubnet -Profile Any
   New-NetFirewallRule -DisplayName "chzzz WebRTC 8189/TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8189 -RemoteAddress LocalSubnet -Profile Any
   ```
   확인이 끝나면 지웁니다.
   ```powershell
   Remove-NetFirewallRule -DisplayName "chzzz *"
   ```
5. **접속과 인증서 경고 넘기기**: 휴대폰에서 `https://<PUBLIC_HOST>:8443`에 접속합니다. 공인 인증서가 아니라서 경고가 나옵니다.
   - Android Chrome: "연결이 비공개로 설정되어 있지 않습니다" → **고급** → **`<PUBLIC_HOST>`(안전하지 않음)(으)로 이동**
   - iOS Safari: "이 연결은 비공개 연결이 아닙니다" → **세부사항 보기** → **이 웹 사이트 방문** → 한 번 더 **웹 사이트 방문** (직접 확인하지는 않았습니다)

## 카메라·마이크 권한

- **허용**: 송출 화면(`/live/new`)을 열면 브라우저가 카메라와 마이크 권한을 묻습니다. 둘 다 **허용**하면 미리보기가 보입니다. 전면·후면 카메라는 방송을 시작하기 전에 고릅니다.
- **거부했을 때 다시 켜기**: 송출 화면에 이유와 해결 방법이 안내됩니다.
  - Android Chrome: 주소창 왼쪽 아이콘(사이트 설정) → **권한** → 카메라·마이크를 **허용**으로 바꾸고 새로고침합니다.
  - iOS Safari: 주소창의 **aA** → **웹 사이트 설정** → 카메라·마이크를 **허용**으로 바꾸고 새로고침합니다. 기기 전체에서 막았다면 **설정 → Safari → 카메라/마이크**도 확인합니다. (직접 확인하지는 않았습니다)
- HTTPS가 아닌 주소(예: `http://192.168.0.5:3000`)로 들어오면 브라우저가 카메라를 막습니다. 송출 화면이 `https://` 주소로 다시 접속하라고 안내합니다.

## 로그인 없는 송출과 송출 키

로그인이 없으므로 "이 방송을 종료할 수 있는 사람"은 **송출 키**로 구분합니다.

1. 송출자가 방송 시작을 누르면 서버가 라이브 방송을 **대기** 상태로 만들고, 그 방송 전용 송출 키를 **한 번만** 돌려줍니다. 서버에는 키의 SHA-256 해시만 저장합니다.
2. 송출 화면은 그 키로 MediaMTX에 WHIP 송출을 시작합니다. MediaMTX는 송출·시청 요청마다 백엔드 내부 엔드포인트에 허가를 묻고, 백엔드는 대기인 방송에 맞는 키일 때만 송출을, 방송 중일 때만 시청을 허가합니다.
3. 영상이 들어오면 **방송 중**, 송출자가 종료하거나 연결이 끊기면 **종료**가 됩니다. 영상이 60초(`LIVE_PENDING_TIMEOUT`) 안에 들어오지 않은 대기도 종료로 정리됩니다.
4. 키는 그 브라우저 탭의 저장소(sessionStorage)에만 남아서, 같은 탭에서 새로고침해도 방송을 종료할 수 있습니다. 목록·상세 응답에는 키가 나오지 않습니다.
5. 시청자는 키 없이 목록에서 방송을 골라 봅니다. 백엔드의 `/internal/*` 엔드포인트는 Caddy·nginx가 바깥에서 404로 막습니다.

## 환경변수

### Docker Compose (`.env`)

[.env.example](.env.example)을 `.env`로 복사해서 고칩니다. `.env`가 없으면 아래 기본값으로 뜹니다.

```dotenv
APP_PORT=3000
DB_NAME=chzzz
DB_USERNAME=chzzz
DB_PASSWORD=chzzz
RECORDING_MAX_UPLOAD_SIZE=500MB
PUBLIC_HOST=localhost
HTTPS_PORT=8443
WEBRTC_PORT=8189
LIVE_PENDING_TIMEOUT=60s
```

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `APP_PORT` | `3000` | PC 브라우저로 접속할 HTTP 포트 |
| `DB_NAME` | `chzzz` | PostgreSQL 데이터베이스 이름 |
| `DB_USERNAME` | `chzzz` | DB 계정 |
| `DB_PASSWORD` | `chzzz` | DB 비밀번호 |
| `RECORDING_MAX_UPLOAD_SIZE` | `500MB` | 최대 업로드 크기. 백엔드 multipart 한도와 프론트 nginx 요청 본문 한도에 같은 값이 적용됩니다 |
| `PUBLIC_HOST` | `localhost` | 휴대폰이 접속할 PC의 LAN IP. HTTPS 인증서와 MediaMTX ICE 후보에 쓰입니다. 휴대폰으로 확인하려면 반드시 바꿉니다 |
| `HTTPS_PORT` | `8443` | 휴대폰 등 다른 기기가 접속할 HTTPS 포트(Caddy) |
| `WEBRTC_PORT` | `8189` | MediaMTX WebRTC 미디어 포트(UDP·TCP 모두) |
| `LIVE_PENDING_TIMEOUT` | `60s` | 영상이 들어오지 않은 대기를 종료로 정리할 때까지의 시간 |

### 백엔드 (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/chzzz` | DB 접속 주소 |
| `DB_USERNAME` / `DB_PASSWORD` | `chzzz` / `chzzz` | DB 계정 |
| `SERVER_PORT` | `8080` | 백엔드 포트 |
| `RECORDING_STORAGE_DIR` | `./data/uploads` | 업로드 파일 보관 폴더(없으면 시작할 때 만듭니다) |
| `RECORDING_MAX_UPLOAD_SIZE` | `500MB` | 최대 업로드 크기 |
| `LIVE_PENDING_TIMEOUT` | `60s` | 대기 시간 초과 |

### 프론트엔드·MediaMTX (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | Vite가 `/api`를 넘길 백엔드 주소 |
| `VITE_MEDIA_PROXY_TARGET` | `http://localhost:8889` | Vite가 `/media`를 넘길 MediaMTX 주소 |
| `MEDIAMTX_BACKEND_URL` | `http://backend:8080` | MediaMTX가 인증·훅을 보낼 백엔드 주소. 개발 실행에서는 `http://host.docker.internal:<백엔드 포트>` |

전체 실행에서는 `DB_URL`, `SERVER_PORT`, `RECORDING_STORAGE_DIR`, `MEDIAMTX_BACKEND_URL`을 `docker-compose.yml`이 컨테이너 내부 값으로 고정합니다.

## 구현 범위

### 구현한 것 (1단계: 녹화방송)

- **등록** (`/recordings/new`): 제목과 영상 출처를 입력합니다. 영상 출처는 업로드 파일과 외부 URL 중 하나를 고릅니다.
  - 제목: 앞뒤 공백을 지우고 1~100자
  - 업로드 파일: mp4, webm. 최대 크기는 `RECORDING_MAX_UPLOAD_SIZE`
  - 외부 URL: `http`/`https` 주소. 브라우저 `<video>`가 바로 재생할 수 있는 영상 파일 주소여야 합니다
  - 화면이 먼저 입력을 검사하고, 서버가 최종 판단합니다. 서버가 거절하면 이유를 입력란 옆에 보여 줍니다
  - 업로드 중에는 등록 버튼이 비활성화되고, 성공하면 상세 화면으로 이동합니다
- **목록** (`/`의 녹화방송 섹션): 최신 등록순. 제목, 등록일시(브라우저 로컬 시간), 출처 종류(업로드/URL)를 보여 줍니다. 비어 있으면 안내와 등록 버튼을 보여 줍니다.
- **상세** (`/recordings/:id`): 제목, 등록일시, 영상 플레이어. 업로드 파일은 HTTP Range 요청으로 스트리밍해서 원하는 위치로 탐색할 수 있습니다. 재생에 실패하면 안내 문구를, 없는 녹화방송이면 "찾을 수 없음"과 목록 링크를 보여 줍니다.
- **영속 저장**: 녹화방송 메타데이터는 PostgreSQL에, 업로드 파일은 디스크(Compose에서는 볼륨)에 저장되어 재시작 후에도 남습니다. 스키마는 Flyway로 관리합니다.
- **로그인 없음**: 모든 화면과 API를 인증 없이 씁니다.

### 구현한 것 (2단계: 라이브 방송)

- **송출 화면** (`/live/new`, 헤더 "방송 시작"): 카메라·마이크 미리보기(전면은 거울처럼), 전면·후면 카메라 선택, 제목 입력(녹화방송과 같은 규칙) → **방송 시작** → "방송 중" 배지와 시청 화면 주소 → **방송 종료**. 권한 거부·카메라 없음·HTTPS 아님·송출 연결 실패는 이유와 해결 방법을 안내합니다.
- **상태**: 대기(시작했지만 영상이 아직 오지 않음) → 방송 중 → 종료. 종료되는 경우는 송출자의 종료, 연결 끊김(탭 닫기·네트워크 끊김, MediaMTX가 알아채는 데 수십 초 걸릴 수 있음), 대기 시간 초과, 서비스 재시작(남아 있던 대기·방송 중을 시작할 때 종료로 정리)입니다.
- **방송 목록** (`/`의 라이브 방송 섹션): 방송 중 전부(최근 시작순)와 최근 종료 10개(최근 종료순)를 "방송 중"(빨강)·"종료" 배지와 시각으로 보여 줍니다. 5초마다 다시 불러옵니다. 대기는 보이지 않습니다.
- **시청 화면** (`/live/:id`): 대기면 "곧 방송이 시작됩니다", 방송 중이면 WHEP으로 실시간 재생, 종료면 "방송이 종료되었습니다"와 시작·종료 시각. 3초마다 상태를 다시 불러와 바뀌면 화면도 바뀝니다. 브라우저가 소리 자동재생을 막으면 음소거로 재생하고 **소리 켜기** 버튼을 보여 줍니다. 연결 실패와 없는 방송("찾을 수 없음")도 안내합니다.
- **영속 저장**: 라이브 방송 기록(제목, 상태, 생성·시작·종료 시각, 송출 키 해시)은 PostgreSQL에 남습니다(Flyway `V2`). 영상 자체는 저장하지 않습니다.
- **로그인 없음**: 송출과 시청 모두 로그인 없이 합니다. 종료 권한은 송출 키로 확인합니다([로그인 없는 송출과 송출 키](#로그인-없는-송출과-송출-키)).

### API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/recordings` | `multipart/form-data`: `title` + `file` 또는 `url`. 성공 `201` + `Location` |
| `GET` | `/api/recordings` | 녹화방송 목록(최신순) |
| `GET` | `/api/recordings/{id}` | 녹화방송 상세. 없으면 `404` |
| `GET` | `/api/recordings/{id}/video` | 업로드 파일 스트리밍. `Range` 요청에 `206`, 범위 밖 `416`, 업로드 파일이 아니거나 없으면 `404` |
| `POST` | `/api/live-broadcasts` | JSON `{ title }`. 대기 상태로 만들고 `201` + `Location` + `{ id, title, status, streamKey, whipUrl, createdAt }`. 송출 키는 이 응답에만 나옵니다 |
| `GET` | `/api/live-broadcasts` | 라이브 방송 목록: 방송 중(최근 시작순) + 최근 종료 10개(최근 종료순) |
| `GET` | `/api/live-broadcasts/{id}` | 상세 `{ id, title, status, createdAt, startedAt, endedAt, whepUrl }`. 없으면 `404` |
| `POST` | `/api/live-broadcasts/{id}/end` | `Authorization: Bearer <송출 키>`. 종료하고 상세를 돌려줍니다. 키가 없거나 틀리면 `403`, 이미 종료면 `409`, 없으면 `404` |
| `POST` | `/media/live/{id}/whip`, `/media/live/{id}/whep` | MediaMTX WebRTC 송출·시청(SDP). 바깥에서는 이 경로로만 MediaMTX에 닿습니다 |

`/internal/mediamtx/{auth,available,unavailable}`은 MediaMTX만 부르는 내부 엔드포인트(송출·시청 허가, 영상 들어옴·끊김 훅)이고 바깥에는 열지 않습니다.

오류는 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식입니다. 입력 검증 실패(`400`)는 `errors` 배열에 `{ field, message }`를 담습니다. 업로드 크기를 넘으면 `413`입니다.

### 구현하지 않은 것

- 녹화방송 수정, 삭제, 검색, 페이지네이션, 썸네일
- 외부 URL의 재생 가능 여부를 서버가 확인하는 기능: 서버가 사용자가 준 주소로 요청하면 내부망 접근(SSRF) 위험이 있어 형식만 검사합니다([ADR-0002](docs/adr/0002-no-server-check-for-external-url.md)). 재생할 수 없는 주소는 상세 화면의 안내 문구로 알립니다.
- 유튜브 같은 플랫폼 링크, HLS/DASH 스트리밍 주소
- mp4/webm 이외 형식, 서버 쪽 트랜스코딩
- 업로드 이어 올리기, 진행률 표시
- 라이브 방송을 녹화방송으로 저장(다시보기)
- 시청자 수, 채팅, 좋아요
- 방송 중 음소거·카메라 전환·화면 필터·화질 조절(카메라는 시작 전에만 고릅니다)
- 같은 네트워크 밖(LTE 등)에서의 송출·시청: TURN 서버가 없어서 휴대폰·시청 기기와 서버가 같은 LAN에 있어야 합니다([ADR-0003](docs/adr/0003-webrtc-whip-whep-with-mediamtx.md))
- 공인 인증서(경고 없는 HTTPS), 도메인
- 라이브 방송 수정·삭제·검색·페이지네이션
- 로그인, 송출자 프로필, 권한(3단계), 댓글(3단계), 도네이션(4단계)
- E2E 자동화 테스트(대신 아래 직접 확인 절차)

## 직접 확인 절차

전체 실행 기준입니다. 아래 순서대로 확인했습니다(2026-11-10, Windows PC + Android Chrome).

**준비**: 깨끗한 상태로 시작합니다. 업로드용 mp4 파일(예: 휴대폰으로 찍은 짧은 영상)을 하나 준비합니다. 휴대폰 확인을 하려면 먼저 [휴대폰 연결](#휴대폰-연결)의 1~4를 합니다.

```bash
docker compose down -v
```

```bash
docker compose up --build
```

### 녹화방송 (1단계)

1. **빈 목록**: http://localhost:3000 에 접속합니다. 녹화방송 섹션에 "아직 등록된 녹화방송이 없습니다."와 "첫 녹화방송 등록하기" 버튼이 보이면 통과입니다.
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

### 라이브 방송 (2단계, 휴대폰 송출 + PC 시청)

7. **접속·권한·미리보기 (휴대폰)**: `https://<PUBLIC_HOST>:8443/live/new`에 접속해 인증서 경고를 넘기고 카메라·마이크를 허용합니다. 전면 카메라 미리보기가 보이고, "후면 카메라"를 고르면 후면 화면으로 바뀌면 통과입니다.
8. **송출 시작 (휴대폰)**: 제목을 넣고 **방송 시작**을 누릅니다. "연결 중…" 뒤에 빨간 **방송 중** 배지와 시청 화면 주소가 보이면 통과입니다.
9. **목록·시청 (PC)**: http://localhost:3000 의 라이브 방송 섹션에 그 방송이 **방송 중** 배지와 함께 보입니다. 눌러서 시청 화면으로 들어가 휴대폰 영상과 소리가 나오면 통과입니다(**소리 켜기**가 보이면 누릅니다). 확인 때 지연은 체감상 거의 없었습니다.
10. **송출 종료 (휴대폰 → PC)**: 휴대폰에서 **방송 종료**를 누릅니다. 휴대폰에 "방송이 종료되었습니다"가 보이고 카메라가 꺼집니다. 몇 초 안에 PC 시청 화면이 "방송이 종료되었습니다"와 시작·종료 시각으로 바뀌고, 목록에서 **종료** 배지로 보이면 통과입니다.
11. **연결 끊김 자동 종료**: 새 방송을 시작하고 **방송 중**이 되면 종료 버튼을 누르지 않고 휴대폰 Chrome 탭을 닫습니다. PC 재생이 멈추고, 약 30초 안에(MediaMTX가 끊김을 알아채는 시간) 시청 화면과 목록이 종료로 바뀌면 통과입니다.
12. **재시작 후 유지·정리**: 휴대폰에서 방송을 시작해 둔 채(또는 시작 직후) 서비스를 멈췄다가 다시 띄웁니다(`-v` 없이). 녹화방송 두 개와 라이브 방송 기록이 그대로 있고, 녹화방송은 상세 화면에서 재생되며, 남아 있던 대기·방송 중은 **종료**로 정리되어 있으면 통과입니다.
    ```bash
    docker compose down
    ```
    ```bash
    docker compose up
    ```

화면 예시는 [docs/screenshots/stage-1](docs/screenshots/stage-1), [docs/screenshots/stage-2](docs/screenshots/stage-2)에 있습니다.

## 테스트

테스트는 TDD(red → green)로 작성했고, 사이클 기록은 [docs/tdd-log.md](docs/tdd-log.md)에 있습니다.

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm test
```

- 백엔드: Spring Boot 앱 전체를 띄우고 REST API로만 검증합니다. DB는 Testcontainers로 실제 PostgreSQL을 띄우므로 **Docker가 실행 중이어야** 합니다. MediaMTX의 인증·훅 요청은 테스트가 내부 엔드포인트를 직접 불러 흉내 내고, 시간(대기 시간 초과)은 테스트용 시계로 바꿔 끼웁니다.
- 프론트엔드: Vitest + React Testing Library로 라우터를 포함한 앱 전체를 렌더링하고, 네트워크 경계(`fetch`)와 미디어 어댑터(카메라·WebRTC)만 가짜로 바꿉니다.

## 문서

- [GLOSSARY.md](GLOSSARY.md): 도메인 용어
- [docs/adr/](docs/adr/): 설계 결정 기록
- [docs/tdd-log.md](docs/tdd-log.md): TDD 사이클 기록
- [AGENTS.md](AGENTS.md): 작업 지침
