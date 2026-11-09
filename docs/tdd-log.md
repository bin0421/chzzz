# TDD 기록

red → green(→ refactor) 사이클마다 한 줄씩 기록한다. 4단계까지 이어 쓰며, 최종 발표에서 TDD 과정의 근거로 쓴다.

- **테스트**: 테스트 이름
- **red**: 처음에 실패한 이유(실제 실패 메시지 요지)
- **green**: 통과시킨 최소 변경
- **refactor**: 리팩터 내용(없으면 `-`)

## 1단계: 녹화방송 등록·시청

| # | [티켓] 테스트 | red | green | refactor |
| --- | --- | --- | --- | --- |
| 1 | [01] 백엔드 `외부 URL로 녹화방송을 등록하면 201과 상세를 돌려준다` | `BadSqlGrammarException`: `recording` 테이블 없음 | Flyway `V1` 마이그레이션, 녹화방송 엔티티·영상 출처(종류+값), 등록 서비스, `POST /api/recordings` | - |
| 2 | [01] 백엔드 `녹화방송 목록은 최신 등록순이다` | `405`: 목록 엔드포인트 없음 | 등록일시·id 내림차순 조회, `GET /api/recordings` | - |
| 3 | [01] 백엔드 `등록한 녹화방송의 상세를 조회한다` | `404`: 상세 엔드포인트 없음 | 단건 조회, `GET /api/recordings/{id}` | - |
| 4 | [01] 백엔드 `제목이 비어 있으면 400 Problem Details로 title 오류를 돌려준다` | `201`: 공백 제목도 등록됨 | 녹화방송 생성 시 제목 공백 제거·필수 검사, 필드 오류를 Problem Details `errors`로 응답하는 공통 처리 | - |
| 5 | [01] 프론트 `외부 URL로 등록하면 상세 화면으로 이동해 그 URL을 재생한다` | `제목` 레이블을 찾지 못함: 화면 없음 | API 클라이언트(`fetch`), 등록 화면, 상세 화면(`<video>`), 라우팅 | - |
| 6 | [01] 프론트 `등록된 녹화방송 제목을 보여 주고 누르면 상세로 이동한다` | `listitem`을 찾지 못함: 목록 화면 없음 | 목록 화면과 `/` 라우트 | - |
| 7 | [01] 백엔드 `등록 응답의 등록일시는 이후 조회한 등록일시와 같다` (실제 브라우저 확인 중 발견) | 응답 `…01.176849800Z`, 조회 `…01.176850Z`: PostgreSQL이 마이크로초로 반올림 | 녹화방송 생성 시 등록일시를 마이크로초로 자름 | - |
| 8 | [02] 백엔드 `제목의 앞뒤 공백을 제거해서 저장한다` | red 없음: 01에서 이미 trim 저장을 구현해 처음부터 통과(현재 동작 고정용) | 변경 없음 | - |
| 9 | [02] 백엔드 `공백 제거 후 제목이 100자를 넘으면 400으로 title 오류를 돌려준다` | DB `varchar(100)` 제약 위반으로 500 | `Recording.register`에 100자 상한(`TITLE_MAX_LENGTH`) 추가 | - |
| 10 | [02] 백엔드 `공백 제거 후 제목이 정확히 100자면 등록된다` | red 없음: 경계값 확인용, 직전 green으로 통과 | 변경 없음 | - |
| 11 | [02] 백엔드 `파일도 URL도 없으면 400으로 source 오류를 돌려준다` | `url` 필수 파라미터 누락 400이라 `errors`가 없음 | `url`·`file`을 선택 파라미터로 바꾸고 `VideoSource.requireExactlyOne`(둘 다 없음) 추가 | - |
| 12 | [02] 백엔드 `파일과 URL이 둘 다 있으면 400으로 source 오류를 돌려준다` | 파일을 무시하고 201로 등록됨 | `requireExactlyOne`에 둘 다 있음 규칙 추가 | - |
| 13 | [02] 백엔드 `URL이 http나 https가 아니면 400으로 url 오류를 돌려준다` | ftp 주소가 201로 등록됨 | `VideoSource.externalUrl`에서 `URI` 파싱 후 스킴 검사 | - |
| 14 | [02] 백엔드 `URL에 호스트가 없으면 400으로 url 오류를 돌려준다` | `https:///video.mp4`가 201로 등록됨 | 호스트가 비어 있으면 `url` 오류 | - |
| 15 | [02] 백엔드 `같은 제목과 같은 URL로 다시 등록해도 성공한다` | red 없음: 중복 허용은 기존 동작(현재 동작 고정용) | 변경 없음. 리팩터: 파일만 있는 경우 `url!!` NPE 대신 03 자리 표시 `file` 오류로 정리 | - |
| 16 | [02] 프론트 `제목이 공백뿐이면 서버에 보내지 않고 제목 오류를 보여 준다` | 검증 없이 POST를 보내고 오류 문구 없음 | 등록 화면에 클라이언트 검증(빈 제목)과 `aria-describedby` 오류 표시 추가, `noValidate` | - |
| 17 | [02] 프론트 `제목이 100자를 넘으면 서버에 보내지 않고 제목 오류를 보여 준다` | 101자 제목을 그대로 POST | 100자 상한 검사 추가 | - |
| 18 | [02] 프론트 `URL을 비우면 서버에 보내지 않고 URL 오류를 보여 준다` | 빈 URL을 그대로 POST | URL 필수 검사와 URL 입력란 오류 표시 추가 | - |
| 19 | [02] 프론트 `URL이 ftp://… / https:///… 이면 서버에 보내지 않고 URL 오류를 보여 준다` | 잘못된 주소를 그대로 POST | 스킴·호스트 검사 추가. 브라우저 `URL`은 `https:///a.mp4`를 고쳐 해석하므로 원문 문자열로 검사 | - |
| 20 | [02] 프론트 `서버가 400으로 거절하면 errors를 필드별로 보여 주고 등록 화면에 남는다` | `ApiError`를 잡지 않아 화면에 아무것도 안 나옴 | `ApiError.errors`를 필드별 오류 상태로 반영 | - |
| 21 | [02] 프론트 `서버가 입력란 밖의 오류(출처 개수 등)로 거절하면 그 메시지와 요약을 알림으로 보여 준다` | `source` 오류를 표시할 곳이 없음 | `role="alert"` 영역에 `detail`과 입력란 밖 필드 메시지 표시 | - |
| 22 | [04] 백엔드 `없는 녹화방송을 조회하면 404 Problem Details를 돌려준다` | 404 본문이 비어 Content-Type이 없음 | `NotFoundException` 추가, `ApiExceptionHandler`가 404 Problem Details(`title` "찾을 수 없음")로 변환, 컨트롤러는 없으면 예외를 던짐 | - |
| 23 | [04] 프론트 `등록된 녹화방송이 없으면 안내 문구와 등록 버튼을 보여 준다` | 빈 목록에 안내 문구가 없음 | 목록에 로딩 상태를 두고, 비어 있으면 안내와 "첫 녹화방송 등록하기" 링크 표시 | - |
| 24 | [04] 프론트 `목록 항목마다 제목, 브라우저 로컬 시간의 등록일시, 출처 종류를 보여 준다` | 항목에 등록일시가 없음 | `format.ts`(로컬 시간 표시, 출처 종류 `업로드`/`URL`) 추가, 항목에 `<time>`과 배지 표시 | - |
| 25 | [04] 프론트 `상세 화면에서도 헤더의 링크로 등록 화면과 목록으로 갈 수 있다` | `banner` 역할 요소가 없음 | `App`에 공통 헤더(`목록`, `녹화방송 등록` 링크)와 `main` 추가. 기존 목록 테스트의 listitem 개수에 영향이 없도록 헤더는 `li`를 쓰지 않음 | - |
| 26 | [04] 프론트 `없는 녹화방송이면 찾을 수 없음과 목록 링크를 보여 준다` | 404를 처리하지 않아 "불러오는 중"에 머묾 | 상세 화면 상태를 loading/loaded/notFound/failed로 나누고 404면 "찾을 수 없음"과 "목록으로 돌아가기" 링크 표시 | - |
| 27 | [04] 프론트 `제목과 브라우저 로컬 시간의 등록일시를 보여 준다` | 상세에 등록일시가 없음 | 제목 아래 `등록일시 <time>` 표시 | - |
| 28 | [04] 프론트 `플레이어에서 재생 오류가 나면 안내 문구를 보여 준다` | `alert` 역할 요소가 없음 | `Player` 컴포넌트가 `<video>`의 `error` 이벤트에서 안내 문구 표시(녹화방송마다 `key`로 상태 초기화) | - |
| 29 | [03] 백엔드 `업로드 파일로 등록하면 201이고 파일은 UUID 이름으로 저장되며 DB에는 그 이름만 남는다` | `400`: `url`이 필수 파라미터라 파일만 보낼 수 없음 | `UploadedFileStorage` 인터페이스와 로컬 디스크 구현(`recording.storage-dir` ← `RECORDING_STORAGE_DIR`, 시작 시 폴더 생성), `UPLOADED_FILE` 출처 종류, `file`을 선택 파라미터로 받아 UUID + 확장자 이름으로 저장 | - |
| 30 | [03] 백엔드 `mp4나 webm이 아닌 파일은 400 Problem Details로 file 오류를 돌려주고 저장하지 않는다` | `201`: 확장자 검사 없음 | 확장자(mp4/webm) 검사, 위반 시 `FieldValidationException("file")` | - |
| 31 | [03] 백엔드 `메타데이터 저장이 실패하면 방금 저장한 파일을 지운다` | 저장 폴더에 `.webm` 파일이 남음 | 메타데이터 저장 실패 시 저장한 파일 삭제 후 예외 재발생 | - |
| 32 | [03] 백엔드 `최대 업로드 크기를 넘으면 413 Problem Details를 돌려준다` | `201`: 크기 한도 미설정 | `recording.max-upload-size` ← `RECORDING_MAX_UPLOAD_SIZE`(기본 500MB)를 Spring multipart 파일·요청 한도에 연결. 413 Problem Details는 Spring 기본 처리 | - |
| 33 | [03] 백엔드 `업로드 파일 녹화방송의 상세 videoUrl은 video 엔드포인트다` | `videoUrl`이 저장 이름(`…webm`) 그대로 노출 | 출처 종류가 업로드 파일이면 `/api/recordings/{id}/video` | - |
| 34 | [03] 백엔드 `video는 Range 없이 요청하면 200과 전체 파일을 돌려준다` | `404`: 엔드포인트 없음 | `GET /api/recordings/{id}/video`가 저장 파일을 `Resource`로, 확장자에 맞는 `Content-Type`으로 반환 | - |
| 35 | [03] 백엔드 `video는 Range 요청에 206과 Content-Range, 요청한 바이트만 돌려준다` | 처음부터 green: Spring MVC가 `Resource` 본문의 Range를 처리. 테스트 민감도는 `ByteArray`를 반환하도록 잠시 바꿔 `200`으로 실패하는 것을 확인 | 변경 없음 | - |
| 36 | [03] 백엔드 `video는 파일 크기를 벗어난 Range 요청에 416을 돌려준다` | 위와 같음(`ByteArray` 반환에서 `200`으로 실패 확인) | 변경 없음 | - |
| 37 | [03] 백엔드 `없는 녹화방송의 video는 404다` | `NullPointerException` | 녹화방송이 없으면 404 | - |
| 38 | [03] 백엔드 `외부 URL 녹화방송의 video는 404다` | `InvalidPathException`: URL을 파일 이름으로 해석 | 출처 종류가 업로드 파일이 아니면 404 | - |
| 39 | [03] 백엔드 `업로드 파일이 디스크에 없으면 video는 404다` | `NullPointerException` | 저장소에 파일이 없으면 404 | - |
| 40 | [03] 프론트 `출처 종류를 전환하면 고른 종류의 입력란만 보인다` | `업로드 파일` 라디오를 찾지 못함 | 출처 종류 라디오(기본 외부 URL)와 종류별 입력란 | - |
| 41 | [03] 프론트 `파일을 골라 등록하면 상세 화면으로 이동해 업로드한 영상을 재생한다` | 요청 본문의 `file`이 `null` | 고른 파일을 상태로 두고 업로드 파일 종류면 `file`만, 외부 URL이면 `url`만 보냄 | - |
| 42 | [03] 프론트 `업로드가 끝날 때까지 등록 버튼이 비활성화된다` | `toBeDisabled()` 실패 | 제출 중 상태로 버튼 비활성화(문구 `등록 중…`), 끝나면 해제 | - |
| 43 | [03] 프론트 `서버가 파일을 거절하면 그 이유를 파일 입력란 옆에 보여 준다` (main 병합 후) | 파일 입력란의 접근 가능한 설명이 비어 있음: `file` 오류가 폼 위 알림에만 표시됨 | `INPUT_FIELDS`에 `file` 추가, 파일 입력란 옆 오류 문구와 `aria-describedby` | - |

## 2단계: 라이브 방송 송출·시청

| # | [티켓] 테스트 | red | green | refactor |
| --- | --- | --- | --- | --- |
| 44 | [01] 백엔드 `라이브 방송을 시작하면 201과 대기 상태, 송출 키, WHIP 주소를 돌려준다` | `BadSqlGrammarException`: `live_broadcast` 테이블 없음 | Flyway `V2`, 라이브 방송 엔티티(대기·방송 중·종료), 송출 키 발급, `POST /api/live-broadcasts` | 제목 규칙을 공통 `Title`로 추출해 녹화방송과 함께 씀 |
| 45 | [01] 백엔드 `송출 키는 DB에 원문이 아닌 해시로만 저장된다` | 위와 같은 이유(테이블 없음) | 송출 키 SHA-256 해시 저장, 상수 시간 비교 | - |
| 46 | [01] 백엔드 `제목이 비어 있으면 400으로 title 오류를 돌려주고 만들지 않는다` | red 없음: 공통 제목 규칙 재사용으로 처음부터 통과(동작 고정용) | 변경 없음 | - |
| 47 | [01] 백엔드 `대기 중인 라이브 방송에 맞는 송출 키로 송출하면 허용한다` / `틀린 송출 키로 송출하면 거부한다` | `404`: 내부 인증 엔드포인트 없음 | `POST /internal/mediamtx/auth`(MediaMTX HTTP 인증), publish는 대기 + 맞는 키만 허용 | - |
| 48 | [01] 백엔드 `상세는 상태와 WHEP 주소를 주고 송출 키는 주지 않는다` | `404`: 상세 엔드포인트 없음 | `GET /api/live-broadcasts/{id}`, 없으면 404 Problem Details | - |
| 49 | [01] 백엔드 `영상이 들어오면 방송 중이 되고 시작 시각이 남는다` / `방송 중인 라이브 방송만 시청을 허용한다` | 훅 `404`, read 인증 `401` | `POST /internal/mediamtx/available`(runOnAvailable 훅)로 대기 → 방송 중, read는 방송 중만 허용 | - |
| 50 | [01] 백엔드 `목록에는 방송 중인 라이브 방송만 나오고 대기는 나오지 않는다` | `405`: 목록 엔드포인트 없음 | `GET /api/live-broadcasts`(방송 중, 최근 시작순) | - |
| 51 | [01] 프론트 송출 `제목을 넣고 방송을 시작하면 미리보기를 WHIP으로 송출하고 방송 중이 되면 알려 준다` | `제목` 레이블 없음: 송출 화면 없음 | 미디어 어댑터 경계(인터페이스·주입·가짜), 송출 화면 `/live/new` | - |
| 52 | [01] 프론트 시청 `방송 중이면 WHEP으로 연결해 원격 영상을 플레이어에 붙인다` | 제목 heading 없음: 시청 화면 없음 | 시청 화면 `/live/:id` | - |
| 53 | [01] 프론트 목록 `방송 중인 라이브 방송을 배지와 함께 보여 주고 시청 화면으로 연결한다` / `헤더의 방송 시작 링크로 송출 화면에 간다` | `라이브 방송` region, `방송 시작` 링크 없음 | 방송 목록 위 라이브 섹션, 헤더 링크 | - |
| 54 | [02] 백엔드 `LiveBroadcastEndApiTest` 송출 키로 방송 중인 라이브 방송을 종료하면 200과 종료 상태, 종료 시각을 준다 | 종료 엔드포인트가 없어 200이 아님 | `POST /api/live-broadcasts/{id}/end`, `LiveBroadcast.end`, `LiveBroadcastService.end` | - |
| 55 | [02] 백엔드 `LiveBroadcastEndApiTest` 송출 키가 없거나 틀리면 403이고 방송은 그대로다 | 키 확인이 없어 200 | 서비스에서 `StreamKey.matches`가 아니면 `ForbiddenException`, 컨트롤러가 `Bearer ` 접두어가 있을 때만 키로 꺼냄 | - |
| 56 | [02] 백엔드 `LiveBroadcastEndApiTest` 이미 종료된 라이브 방송을 다시 종료하면 409다 | 두 번째 종료도 200 | `end`가 false면 `ConflictException` | - |
| 57 | [02] 백엔드 `LiveBroadcastEndApiTest` 없는 라이브 방송을 종료하면 404다 | red 없음: 01의 `find`가 이미 `NotFoundException`을 던져 처음부터 green | - | - |
| 58 | [02] 백엔드 `MediaMtxUnavailableHookApiTest` 끊김 훅 4개(방송 중→종료, 대기→종료, 종료 후 멱등, 이상한 경로 200) | `/internal/mediamtx/unavailable`이 없어 404 | 훅 엔드포인트와 `onStreamUnavailable`(대기·방송 중이면 `end`, 아니면 무시) | - |
| 59 | [02] 백엔드 `LiveBroadcastListOrderApiTest` 방송 중을 최근 시작순으로 먼저, 이어서 종료 중 최근 10개를 최근 종료순으로 준다 | 목록에 종료가 없음 | `findTop10ByStatusOrderByEndedAtDescIdDesc`를 방송 중 뒤에 이어 붙임 | - |
| 60 | [02] 백엔드 `LiveBroadcastStartupCleanupApiTest` 시작할 때 대기와 방송 중으로 남은 라이브 방송을 종료로 정리한다 | 앱이 떠도 대기가 PENDING 그대로 | `ApplicationReadyEvent`에서 `endAllUnfinished` 호출하는 `LiveBroadcastStartupCleanup` | 실제 재시작에서 Tomcat이 열린 뒤 1초쯤 정리 전 상태가 보여서 `SmartInitializingSingleton`(웹 서버 시작 전)으로 옮김. 테스트는 그대로 green |
| 61 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 방송 종료를 누르면 송출을 멈추고 송출 키로 종료 API를 부르고 카메라를 끈다 | "방송 종료" 버튼 없음 | `api.endLiveBroadcast`(Bearer), 송출 화면 `ended` 단계, 송출 중지 → 종료 API → 미리보기 트랙 stop·`srcObject` 해제. 가짜 스트림에 stop을 기록하는 트랙 추가 | - |
| 62 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 송출 화면을 새로고침해도 같은 탭에서는 송출 키로 방송을 종료할 수 있다 | 다시 그린 화면에 종료 버튼 없음 | 시작 때 `{ id, streamKey }`를 sessionStorage(`chzzz.ownLiveBroadcast`)에 보관, 있으면 `resumed` 단계로 종료 버튼 표시, 종료하면 지움 | - |
| 63 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 끊김으로 이미 종료된 방송을 종료하려 하면(409) 종료된 것으로 보여 준다 | 409가 처리되지 않은 오류로 남음 | 종료 API의 409는 "이미 종료"로 보고 정상 종료와 같이 처리 | - |
| 64 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 3초마다 상세를 다시 불러와 종료로 바뀌면 연결을 닫고 종료 안내를 보여 준다 | 처음 한 번만 불러옴 | 시청 화면 3초 폴링(서버가 종료로 바꾸면 멈춤), 종료면 플레이어 대신 안내 | 가짜 타이머 아래에서 effect 정리가 늦게 돌아 `close` 단언을 `vi.waitFor`로 기다리게 함 |
| 65 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 시청 연결이 끊기면 연결을 닫고 종료 안내를 보여 준다 | 끊김 알림을 듣지 않음 | `viewing.onDisconnect` → `disconnected` 상태로 종료 안내, 시청 연결 닫음. 종료 시각을 받도록 폴링은 서버가 종료로 바꿀 때까지 계속 | - |
| 66 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 종료된 방송을 처음 열면 연결하지 않고 종료 안내와 시작·종료 시각을 보여 준다 | 시작·종료 시각 표시 없음 | 종료 안내 아래 `<dl>`에 시작·종료 `<time>` | - |
| 67 | [02] 프론트 `LiveBroadcastEnd.test.tsx` 종료된 라이브 방송에 종료 배지와 종료 시각을 보여 주고 5초마다 다시 불러온다 | 종료 항목에도 "방송 중" 배지 | 상태별 배지(종료/방송 중)와 시각(종료 시각/시작 시각), 5초 폴링 | - |
| 68 | [03] 백엔드 `LivePendingTimeoutApiTest` 대기가 60초를 넘으면 상세 조회 때 종료가 되고 종료 시각이 남는다 | 시계를 61초 옮겨도 `status`가 `PENDING` | `LiveBroadcast.expireIfPendingTooLong`(종료 시각 = 생성 + 시간 초과), `live.pending-timeout=${LIVE_PENDING_TIMEOUT:60s}`, `find`에서 판정 | 테스트용 `MutableClock`(@Primary)을 `MutableClockConfiguration`으로 분리 |
| 69 | [03] 백엔드 `LivePendingTimeoutApiTest` 대기가 60초를 넘으면 목록 조회 때 종료로 저장된다 | 목록 조회 뒤 DB가 `PENDING`/`ended_at` null | `expireOverduePending()`(생성 시각 기준 조회 후 일괄 판정)을 `list`에서 먼저 호출 | 없음. 종료 항목이 목록에 보이는 것은 02 범위라 저장 상태로 확인 |
| 70 | [03] 백엔드 `LivePendingTimeoutApiTest` 시간 초과된 대기에는 맞는 송출 키로도 송출할 수 없다 | 인증 훅이 `200` | `authorize`가 판정 후 조회(`findCurrent`)를 쓰게 함 | `find`도 `findCurrent`로 합침 |
| 71 | [03] 백엔드 `LivePendingTimeoutApiTest` 시간 초과 뒤에 영상 들어옴 훅이 와도 방송 중이 되지 않는다 | 상세가 `LIVE` | `onStreamAvailable`도 `findCurrent` 사용 | 없음 |
| 72 | [03] 백엔드 `LivePendingSweepTest` 조회가 없어도 주기 정리 작업이 시간 초과된 대기를 종료로 바꾼다 | 5초 기다려도 `PENDING` | `SchedulingConfig`(@EnableScheduling), `LivePendingSweeper`(`live.pending-sweep-interval`, 기본 10s)가 `expireOverduePending` 호출 | 없음 |
| 73 | [03] 백엔드 `LivePendingTimeoutApiTest` 60초가 되기 전에는 대기로 남고 송출할 수 있다 | 처음부터 green(경계 확인용으로 추가) | 없음 | 없음 |
| 74 | [03] 백엔드 `MediaMtxAuthRulesApiTest` live 다음 숫자 식별자 형식이 아닌 경로는 맞는 키로도 거부한다 (`live/01`, `live/1\n`) | 두 경로가 `200` 허용. `find`+`$`라서 앞자리 0과 끝 개행이 같은 방송으로 풀렸다(같은 방송을 다른 MediaMTX 경로로 송출 가능) | `MediaPaths.idOf`를 `matchEntire` + `[1-9]\d*`로 바꿔 정규형만 인정 | 없음 |
| 75 | [03] 백엔드 `MediaMtxAuthRulesApiTest` 나머지(키 없음·빈 키, 다른 방송의 키, 방송 중에 재송출, 없는 방송 송출·시청, 그 밖의 경로 8종, 경로 없음, 행동 6종, 대기 시청) | 처음부터 green(티켓 01 구현이 이미 거부). 거부 규칙 고정용 | 없음 | 없음 |
| 76 | [03] 백엔드 `StreamKeyExposureApiTest` 대기 상세 / 방송 중 목록·상세 / 404·400·인증 거부 응답에 송출 키와 해시가 없다 | 처음부터 green. 회귀 방지용(본문·헤더 모두 검사) | 없음 | 없음 |
| 77 | [03] 프론트 `LiveBroadcastPending.test.tsx` 대기면 "곧 방송이 시작됩니다"를 보여 주고, 다시 불러와 방송 중이 되면 시청 연결을 시작한다 | 문구가 없음(상세를 한 번만 불러오고 폴링도 없음) | 시청 화면이 상세를 3초마다 다시 불러오고(종료면 멈춤) 대기면 `role="status"` 문구 표시. 기존 "방송 중이면 시청 연결" 효과가 상태 변화에 반응 | 없음 |
| 78 | [04] 프론트 제목이 비어 있으면 서버에 보내지 않고 입력란 옆에 안내한다 | 검증 없이 `POST` 요청을 보냄 | `validateTitle` 공용 함수로 클라이언트 검증, `aria-describedby` 안내 | 녹화방송 등록 화면도 `validateTitle` 사용 |
| 79 | [04] 프론트 서버가 400으로 제목을 거절하면 그 메시지를 입력란 옆에 보여 주고 다시 시작할 수 있다 | `ApiError`를 잡지 않아 처리되지 않은 오류 | 시작 API 실패 시 `title` 필드 오류 표시, 단계를 `ready`로 되돌림 | - |
| 80 | [04] 프론트 전면 카메라로 미리보기를 열고, 후면을 고르면 이전 카메라를 끄고 후면으로 다시 연다 | 카메라 선택 라디오가 없음 | `facing` 상태, 바뀌면 이전 트랙을 끄고 `openPreview(facing)` 다시 호출 | - |
| 81 | [04] 프론트 방송을 시작한 뒤에는 카메라를 바꿀 수 없다 | (fieldset `disabled`를 빼고 확인) 연결 중에 라디오가 활성 | 단계가 `ready`가 아니면 카메라 fieldset 비활성 | - |
| 82 | [04] 프론트 미리보기가 permission-denied/no-device/insecure-context 오류로 실패하면 이유와 해결 방법을 보여 준다 | 안내(`role="alert"`)가 없음 | `MediaAccessError`와 이유별 안내 문구, 실제 어댑터의 DOMException 매핑 | - |
| 83 | [04] 프론트 방송 시작 후 미디어 서버에 연결하지 못하면 네트워크·방화벽을 확인하라고 안내하고 다시 시작할 수 있다 | `publish` 실패가 처리되지 않음 | `publish` 실패 시 연결 실패 안내, 단계를 `ready`로 | - |
| 84 | [04] 프론트 영상이 서버에 닿지 못해 방송 중이 되기 전에 종료되면 송출을 멈추고 같은 안내를 보여 준다 | 대기에서 종료로 바뀌어도 계속 상태 확인 | 상태 확인이 `ENDED`면 송출 중지 + 연결 실패 안내 | 안내 두 개를 `Guide` 컴포넌트로 합침 |
| 85 | [04] 프론트 방송 중이 되면 다른 기기로 옮길 수 있게 시청 화면 주소를 링크로 보여 준다 | 링크가 없음 | 방송 중 화면에 `origin + /live/{id}` 링크 | - |
| 86 | [04] 프론트 자동재생으로 소리가 막히면 음소거로 재생하고, 소리 켜기를 누르면 소리가 켜진다 / 소리와 함께 재생되면 소리 켜기 버튼을 보여 주지 않는다 | `startPlayback`이 호출되지 않음 | 어댑터 `startPlayback` 추가, 결과가 `muted`면 "소리 켜기" 버튼 | - |
| 87 | [04] 프론트 라이브 영상에 연결하지 못하면 안내를 보여 준다 | `watch` 실패가 처리되지 않음 | `watch` 실패 시 연결 실패 안내 | - |
| 88 | [04] 프론트 없는 라이브 방송이면 찾을 수 없음과 목록 링크를 보여 준다 | 404가 처리되지 않아 계속 "불러오는 중" | 404면 "찾을 수 없음"과 목록 링크, 그 밖의 실패는 불러오기 실패 안내 | - |

참고: #51~#53의 프론트 테스트 네 개는 화면이 하나도 없는 상태에서 함께 작성해 동시에 red를 확인했다(골격 티켓이라 화면 셋을 한 번에 세움).

참고: [05]는 설정(compose·Caddy·nginx)만 바꿔 TDD 사이클이 없다. 병합할 때 [04]의 송출 화면 테스트가 [02]의 탭 저장소(송출 키)를 이어받아 깨져서, 테스트 공통 정리에 `sessionStorage.clear()`를 넣었다.
