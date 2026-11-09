# 05: HTTPS 전체 실행 (휴대폰 접속 준비)

**What to build:** 채점자가 `PUBLIC_HOST`에 PC의 LAN IP만 넣고 `docker compose up --build`를 실행하면, 같은 네트워크의 다른 기기가 `https://{PUBLIC_HOST}:{HTTPS_PORT}`로 서비스에 접속해 카메라 송출과 시청을 할 수 있다. PC에서는 기존 `http://localhost:3000`으로도 송출·시청이 된다. 스펙: `.scratch/live-broadcasts/spec.md`, 결정: ADR-0003, ADR-0004.

02, 03, 04와 병행한다. 이 티켓은 Docker·프록시 설정만 맡는다(compose, Caddy 설정, MediaMTX 설정, nginx 설정, `.env.example`). 애플리케이션 코드를 바꿔야 하면 최소로 하고 이유를 Comments에 적는다. 휴대폰 실기 확인은 06에서 사용자와 함께 한다.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] 전체 실행 compose에 `mediamtx`와 `caddy`가 있고, `docker compose up --build` 하나로 모든 서비스가 healthy로 뜬다. 1단계의 `db`·`backend`·`frontend` 구성과 볼륨은 그대로다
- [ ] Caddy가 `https://{PUBLIC_HOST}:{HTTPS_PORT}`(기본 8443)에서 로컬 CA 인증서로 TLS를 제공한다. `/media/*`는 접두어를 떼서 MediaMTX WebRTC HTTP 포트로, 나머지는 프론트 nginx로 보낸다. 업로드 한도는 1단계와 같다
- [ ] MediaMTX는 WebRTC만 켜고(RTSP·RTMP·HLS·SRT 끔), 미디어 포트는 `WEBRTC_PORT`(기본 8189)의 UDP·TCP 모두다. ICE 후보로 `PUBLIC_HOST`를 알린다. HTTP 인증과 상태 훅은 compose 내부의 백엔드를 호출한다
- [ ] 프론트 nginx도 `/media/`를 MediaMTX로 프록시해서 `http://localhost:3000`에서 송출·시청이 된다
- [ ] `/internal/`은 Caddy·nginx 어디서도 밖으로 전달되지 않는다. 직접 요청해서 404인 것을 확인했다
- [ ] `.env.example`에 `PUBLIC_HOST`, `HTTPS_PORT`, `WEBRTC_PORT`, `LIVE_PENDING_TIMEOUT`이 설명과 함께 있다. `.env`가 없으면 `PUBLIC_HOST=localhost` 기본값으로 뜬다
- [ ] 개발 실행(`docker compose up -d db mediamtx`)이 그대로 동작한다
- [ ] PC에서 확인: 깨끗한 상태에서 전체 실행 → PC 브라우저로 `https://{PUBLIC_HOST}:8443` 접속(인증서 경고 넘김) → 웹캠 송출 → 다른 탭(`http://localhost:3000`) 시청을 확인했다
- [ ] 06의 README에 넣을 방화벽 허용 명령 초안(`HTTPS_PORT`/TCP, `WEBRTC_PORT`/UDP·TCP 인바운드, 관리자 PowerShell)을 Comments에 적었다. 실제 방화벽 변경은 하지 않는다
- [ ] 확인에 쓴 컨테이너와 볼륨을 정리했다. 개발 DB는 원래 상태로 둔다
