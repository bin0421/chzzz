# 05: HTTPS 전체 실행 (휴대폰 접속 준비)

**What to build:** 채점자가 `PUBLIC_HOST`에 PC의 LAN IP만 넣고 `docker compose up --build`를 실행하면, 같은 네트워크의 다른 기기가 `https://{PUBLIC_HOST}:{HTTPS_PORT}`로 서비스에 접속해 카메라 송출과 시청을 할 수 있다. PC에서는 기존 `http://localhost:3000`으로도 송출·시청이 된다. 스펙: `.scratch/live-broadcasts/spec.md`, 결정: ADR-0003, ADR-0004.

02, 03, 04와 병행한다. 이 티켓은 Docker·프록시 설정만 맡는다(compose, Caddy 설정, MediaMTX 설정, nginx 설정, `.env.example`). 애플리케이션 코드를 바꿔야 하면 최소로 하고 이유를 Comments에 적는다. 휴대폰 실기 확인은 06에서 사용자와 함께 한다.

**Blocked by:** 01

**Status:** resolved

- [x] 전체 실행 compose에 `mediamtx`와 `caddy`가 있고, `docker compose up --build` 하나로 모든 서비스가 healthy로 뜬다. 1단계의 `db`·`backend`·`frontend` 구성과 볼륨은 그대로다
- [x] Caddy가 `https://{PUBLIC_HOST}:{HTTPS_PORT}`(기본 8443)에서 로컬 CA 인증서로 TLS를 제공한다. `/media/*`는 접두어를 떼서 MediaMTX WebRTC HTTP 포트로, 나머지는 프론트 nginx로 보낸다. 업로드 한도는 1단계와 같다
- [x] MediaMTX는 WebRTC만 켜고(RTSP·RTMP·HLS·SRT 끔), 미디어 포트는 `WEBRTC_PORT`(기본 8189)의 UDP·TCP 모두다. ICE 후보로 `PUBLIC_HOST`를 알린다. HTTP 인증과 상태 훅은 compose 내부의 백엔드를 호출한다
- [x] 프론트 nginx도 `/media/`를 MediaMTX로 프록시해서 `http://localhost:3000`에서 송출·시청이 된다
- [x] `/internal/`은 Caddy·nginx 어디서도 밖으로 전달되지 않는다. 직접 요청해서 404인 것을 확인했다
- [x] `.env.example`에 `PUBLIC_HOST`, `HTTPS_PORT`, `WEBRTC_PORT`, `LIVE_PENDING_TIMEOUT`이 설명과 함께 있다. `.env`가 없으면 `PUBLIC_HOST=localhost` 기본값으로 뜬다
- [x] 개발 실행(`docker compose up -d db mediamtx`)이 그대로 동작한다
- [x] PC에서 확인: 깨끗한 상태에서 전체 실행 → PC 브라우저로 `https://{PUBLIC_HOST}:8443` 접속(인증서 경고 넘김) → 웹캠 송출 → 다른 탭(`http://localhost:3000`) 시청을 확인했다 (웹캠이 없어 Chrome 가짜 카메라로 확인, 아래 Comments)
- [x] 06의 README에 넣을 방화벽 허용 명령 초안(`HTTPS_PORT`/TCP, `WEBRTC_PORT`/UDP·TCP 인바운드, 관리자 PowerShell)을 Comments에 적었다. 실제 방화벽 변경은 하지 않는다
- [x] 확인에 쓴 컨테이너와 볼륨을 정리했다. 개발 DB는 원래 상태로 둔다

## Comments

### 바꾼 것

- `caddy/Caddyfile`(새 파일, 위치는 사용자 결정): `https://{$PUBLIC_HOST}:{$HTTPS_PORT}` + `tls internal`. `/internal/*`는 404, `/media/*`는 `handle_path`로 접두어를 떼서 `mediamtx:8889`, 나머지는 `frontend:80`. 이미지는 `caddy:2.11.7-alpine`. LF 유지용 `caddy/.gitattributes`.
  - `default_sni {$PUBLIC_HOST}`: IP 주소로 접속하면 브라우저가 SNI를 보내지 않아 인증서를 못 고를 수 있어서 넣었다.
  - `auto_https disable_redirects`(80 포트 안 씀), `skip_install_trust`(컨테이너 신뢰 저장소에 CA 설치 시도 안 함).
  - 로컬 CA·인증서는 `caddy-data` 볼륨에 남아 재시작해도 같은 인증서를 쓴다. `down -v`를 하면 CA가 새로 만들어져 휴대폰에서 경고를 다시 넘겨야 한다(06 README에 적을 것).
  - 업로드 한도: Caddy는 본문을 제한하지 않고 그대로 흘려보내며, 뒤의 nginx(`client_max_body_size`)와 백엔드가 1단계 한도를 지킨다. HTTPS로 501MB 업로드 → 413, 2MB → 201 확인.
- `docker-compose.yml`: `caddy` 서비스와 `caddy-data`·`caddy-config` 볼륨 추가. `mediamtx`에 healthcheck(`nc -z 127.0.0.1 8889`) 추가, 개발 실행이 단독으로 뜨도록 backend 의존은 두지 않았다. 1단계 서비스·볼륨은 그대로이고 다음만 더했다.
  - `backend`: `LIVE_PENDING_TIMEOUT` 환경변수 전달(기본 `60s`). 실제로 읽는 설정은 03이 만든다.
  - `frontend`: `MEDIAMTX_URL`, `depends_on: mediamtx(healthy)`(nginx가 시작할 때 프록시 대상 이름을 찾으므로), healthcheck(`wget http://127.0.0.1/`). "모든 서비스 healthy"를 위해 넣었다.
- `frontend/docker/default.conf.template`: `location /internal/ { return 404; }`(없으면 SPA `try_files`가 index.html을 200으로 준다), `location /media/`를 `${MEDIAMTX_URL}/`로 프록시. `frontend/Dockerfile`에 `MEDIAMTX_URL` 기본값.
- MediaMTX 세션 주소 보정: MediaMTX는 WHIP/WHEP 응답의 `Location`을 `/live/1/whip/<세션>`처럼 자기 기준 절대 경로로 준다. 그대로면 송출 종료 때 어댑터의 `DELETE`가 `/media` 없이 SPA로 간다. Caddy는 `header_down Location`, nginx는 `proxy_redirect` + `absolute_redirect off`로 `/media/...`를 붙였다(`absolute_redirect`를 안 끄면 `http://localhost/media/...`로 호스트 포트가 빠진다). Caddy·nginx 둘 다 `DELETE` 200 확인.
- `mediamtx/mediamtx.yml`은 바꾸지 않았다. 01에서 이미 WebRTC만 켜고 ICE 후보·인증·훅 주소를 compose 환경변수로 덮어쓰게 되어 있다.
- `.env.example`: `PUBLIC_HOST`, `HTTPS_PORT`, `WEBRTC_PORT`, `LIVE_PENDING_TIMEOUT`을 설명과 함께 추가.
- 애플리케이션 코드는 바꾸지 않았다.

### 확인한 것 (PC, 2026-11-09)

- main 체크아웃의 개발 컨테이너와 겹치지 않게 별도 프로젝트 `-p chzzz-t05`, `PUBLIC_HOST=192.168.0.5`, `WEBRTC_PORT=8190`, db·mediamtx 호스트 포트를 빼는 임시 override 파일(저장소 밖)로 띄웠다.
- 깨끗한 상태(`down -v` 후) `up --build --wait`: db·mediamtx·backend·frontend·caddy 다섯 개 모두 healthy.
- 인증서: 발급자 `Caddy Local Authority - ECC Intermediate`, SAN `IP Address:192.168.0.5`. `PUBLIC_HOST` 기본값(localhost)으로도 다섯 개 healthy, SAN `DNS:localhost`.
- `/internal/mediamtx/auth`(GET·POST), `/api/../internal/...`, `/%69nternal/...`, `/api/%2e%2e/internal/...`: Caddy·nginx 모두 404.
- WHEP 응답의 ICE 후보는 `192.168.0.5:8190` UDP·TCP만 나간다.
- 송출: 이 PC에는 웹캠이 없고, 앱 브라우저 패널은 로컬 CA 경고를 넘길 수 없다. 그래서 별도 임시 프로필의 헤드리스 Chrome에 `--use-fake-device-for-media-stream`(Chrome 가짜 카메라·마이크)과 이 Caddy 인증서 체인만 받아들이는 `--ignore-certificate-errors-spki-list`(경고를 넘기는 것과 같은 효과)를 주고 `https://192.168.0.5:8443/live/new`에서 송출했다. `isSecureContext` true, `POST /api/live-broadcasts` 201 → `POST /media/live/1/whip` 201 → "방송 중" 배지.
- 시청: 앱 브라우저 패널의 `http://localhost:3000` 목록에 "방송 중"으로 보이고, `/live/1`에서 640×360 영상이 재생 시간이 늘어나며 재생, 오디오·비디오 트랙 수신.
- 개발 실행: 별도 프로젝트로 `up -d --wait db mediamtx` → 두 서비스만 뜨고 healthy.
- 정리: `chzzz-t05`·`chzzz-t05dev` 프로젝트의 컨테이너·볼륨·네트워크를 모두 내렸다. main 체크아웃의 `chzzz-db-1`·`chzzz-mediamtx-1`은 건드리지 않았다.
- 백엔드 `./gradlew test`, 프론트 `npm test`(23개)·`npm run build`·`npm run lint` 통과.

### 남은 것

- 휴대폰 실기 확인(실제 카메라, 인증서 경고 넘기기, LAN 접속)은 06에서 사용자와 한다.
- 개발 실행의 Vite 프록시(`/media`)는 `Location`을 고치지 않는다. 개발 실행에서 송출 종료 `DELETE`가 MediaMTX에 닿지 않을 수 있다(연결을 닫으면 결국 끊기긴 한다). 02(송출 종료)에서 필요하면 `vite.config.ts`의 `/media` 프록시에 같은 보정을 넣는다.
- main 체크아웃에서 실행 중인 `chzzz-mediamtx-1`은 이 브랜치 이전 compose로 만들어져 healthcheck가 없다. 병합 후 `docker compose up -d db mediamtx`를 다시 하면 새 설정으로 재생성된다.

### 06 README용 방화벽 허용 명령 초안 (관리자 PowerShell, 실행하지 않았다)

PC 네트워크 프로필이 "공용"이어도 같은 LAN(로컬 서브넷)의 기기만 들어오도록 `-RemoteAddress LocalSubnet`을 붙인다. 포트를 바꿨으면 숫자를 `.env` 값으로 고친다.

```powershell
New-NetFirewallRule -DisplayName "chzzz HTTPS 8443/TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443 -RemoteAddress LocalSubnet -Profile Any
New-NetFirewallRule -DisplayName "chzzz WebRTC 8189/UDP" -Direction Inbound -Action Allow -Protocol UDP -LocalPort 8189 -RemoteAddress LocalSubnet -Profile Any
New-NetFirewallRule -DisplayName "chzzz WebRTC 8189/TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8189 -RemoteAddress LocalSubnet -Profile Any
```

확인이 끝난 뒤 지우기:

```powershell
Remove-NetFirewallRule -DisplayName "chzzz *"
```

## TDD 기록

해당 없음. 이 티켓은 compose·Caddy·nginx 설정과 `.env.example`만 바꿨고 애플리케이션 코드(테스트 대상 코드)는 바꾸지 않았다. 설정은 위 "확인한 것"처럼 전체 실행으로 직접 확인했다.
