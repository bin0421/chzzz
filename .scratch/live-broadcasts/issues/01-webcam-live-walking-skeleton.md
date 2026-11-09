# 01: PC 웹캠으로 송출 → 방송 목록 → 시청 (골격)

**What to build:** 개발 실행 방식(DB와 MediaMTX는 compose, 백엔드 `bootRun`, 프론트 `npm run dev`)으로 PC `localhost`에서 라이브 방송이 처음부터 끝까지 이어진다. 송출자가 송출 화면에서 제목을 입력하고 "방송 시작"을 누르면 PC 웹캠·마이크 영상이 MediaMTX로 들어가 라이브 방송이 **방송 중**이 되고, 방송 목록의 "라이브 방송" 섹션에 나타나며, 다른 탭의 시청 화면에서 실시간으로 재생된다. 스펙: `.scratch/live-broadcasts/spec.md`, 결정: ADR-0003.

02~05가 이 위에서 병행으로 진행되므로 이 티켓에서 공용 기반을 마련한다.
- 라이브 방송 모듈과 상태(대기·방송 중·종료). 이 티켓에서 구현하는 전환은 대기 → 방송 중뿐이다.
- 송출 키 발급과 해시 저장
- MediaMTX 연동 엔드포인트의 뼈대(송출·시청 인증, 영상 들어옴 훅)
- 공통 오류 처리에 403(권한 없음)·409(상태 충돌) 추가
- 프론트 미디어 어댑터: 실제 구현, 앱에 주입하는 방법, 테스트용 가짜
- 송출·시청 화면 골격, 방송 목록 라이브 섹션 골격, 헤더 "방송 시작" 링크

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Flyway `V2` 마이그레이션이 라이브 방송 테이블을 만든다. `V1`은 그대로다
- [x] `POST /api/live-broadcasts`가 `201` + `Location` + `{ id, title, status: PENDING, streamKey, whipUrl, createdAt }`를 돌려주고, DB에는 송출 키의 해시만 남는다
- [x] 제목이 비면 `400`(`errors`에 `title`)이다. 녹화방송의 제목 규칙을 재사용한다
- [x] 내부 송출 인증 엔드포인트가 맞는 송출 키 + 대기인 `live/{id}` 송출을 허용하고, 틀린 키는 거부한다
- [x] 내부 시청 인증 엔드포인트가 방송 중인 라이브 방송의 시청만 허용한다
- [x] 내부 "영상 들어옴" 훅이 대기 → 방송 중으로 바꾸고 시작 시각을 남긴다
- [x] `GET /api/live-broadcasts`가 방송 중인 라이브 방송을 돌려주고 대기는 넣지 않는다. `GET /api/live-broadcasts/{id}`는 `whepUrl`을 주고 송출 키는 주지 않는다
- [x] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다(훅 요청은 테스트가 직접 보낸다)
- [x] 개발 실행용 compose에 `mediamtx` 서비스가 있다(WebRTC만, HTTP 인증과 훅이 호스트의 백엔드를 호출하도록 대상 주소를 환경변수로 지정). `docker compose up -d db mediamtx`로 뜬다
- [x] Vite가 `/media`를 MediaMTX WebRTC HTTP 포트로 프록시한다(`/media` 접두어 제거)
- [x] 송출 화면 `/live/new`: 제목을 넣고 시작하면 생성 API와 어댑터 송출 시작이 호출되고, 방송 중이 되면 "방송 중"이 보인다(앱 단위 테스트, 가짜 어댑터)
- [x] 시청 화면 `/live/:id`: 방송 중이면 어댑터 시청 연결을 하고 플레이어에 원격 스트림을 붙인다(앱 단위 테스트)
- [x] 방송 목록 위쪽 "라이브 방송" 섹션에 방송 중인 라이브 방송이 "방송 중" 배지와 함께 보이고 시청 화면으로 연결된다. 아래 녹화방송 섹션과 1단계 테스트는 그대로 green이다
- [x] 헤더에 "방송 시작" 링크가 있고, 기존 프론트 테스트 질의가 깨지지 않는다
- [x] 실제 브라우저(PC `localhost`)에서 송출 → 다른 탭 시청을 확인했다 (이 PC에 웹캠이 없어 합성 스트림으로 확인, 아래 Comments)
- [x] 모든 사이클이 `docs/tdd-log.md`에 기록되어 있다

## Comments

- 이 PC에는 카메라 장치가 없다. 그래서 실제 브라우저 확인은 `getUserMedia`를 캔버스 + 사인파 합성 스트림으로 바꿔서 했다. 실제 WebRTC 어댑터, WHIP 송출(VP8 + Opus), MediaMTX 인증 훅, runOnAvailable 훅, 목록, WHEP 시청(640×360 재생 5초 진행)까지 모두 실제 경로다. 실제 카메라·마이크 확인은 06에서 휴대폰으로 한다.
- 서버 경로는 ffmpeg WHIP 송출로도 확인했다: 틀린 송출 키는 MediaMTX가 401로 거부, 맞는 키는 방송 중으로 전환.
- MediaMTX 1.21의 스트림 훅 이름은 `runOnAvailable`/`runOnUnavailable`이다(예전 `runOnReady`). `runOnUnavailable`은 설정에 미리 넣어 두었고 백엔드 엔드포인트는 02가 만든다(지금은 404).
- MediaMTX는 셸·wget이 들어 있는 `1.21.3-ffmpeg` 이미지를 쓴다. 설정은 루트 `mediamtx/mediamtx.yml`, 환경별 값은 compose의 `MTX_*` 환경변수로 덮어쓴다.
- 개발 실행에서 MediaMTX가 호스트의 백엔드를 부르려면 `MEDIAMTX_BACKEND_URL=http://host.docker.internal:<백엔드 포트>`를 주고 `docker compose up -d db mediamtx`를 실행한다.
- 공통 오류 처리에 403(`ForbiddenException`)·409(`ConflictException`)를 미리 추가했다. 테스트는 02의 종료 API에서 한다.
- 시작 직후 첫 상태 확인은 바로 하고 이후 3초 간격이다. 카메라 권한 거부 등 미리보기 실패 처리는 04 범위라 지금은 시작 버튼이 비활성으로 남는다.
