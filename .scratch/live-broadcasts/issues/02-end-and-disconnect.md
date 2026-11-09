# 02: 방송 종료와 끊김 처리

**What to build:** 송출자가 "방송 종료"를 누르거나 탭을 닫아 송출이 끊기면 라이브 방송이 **종료**된다. 보고 있던 시청자는 "방송이 종료되었습니다"를 보고, 방송 목록에는 "종료" 배지가 붙는다. 송출 키를 모르는 사람은 종료할 수 없다. 스펙: `.scratch/live-broadcasts/spec.md`.

03(대기 시간 초과·인증 강화), 04(화면 마무리), 05(HTTPS 전체 실행)와 병행한다. 이 티켓은 **종료로 가는 전환 중 송출자 종료와 끊김**, 그리고 서비스 시작 시 정리를 맡는다. 대기 시간 초과 전환과 인증 거부 규칙은 03이 맡는다. 화면에서는 송출 화면의 종료 버튼과 키 보관, 시청 화면의 종료 처리, 목록의 종료 표시만 건드린다.

**Blocked by:** 01

**Status:** resolved

- [x] `POST /api/live-broadcasts/{id}/end`(`Authorization: Bearer <송출 키>`): 성공하면 `200`과 상태 종료, 종료 시각이 채워진 상세를 준다
- [x] 종료 API 실패: 키가 없거나 틀리면 `403`, 이미 종료됐으면 `409`, 없으면 `404`. 모두 Problem Details다
- [x] 내부 "영상 끊김" 훅이 대기·방송 중을 종료로 바꾸고 종료 시각을 남긴다. 이미 종료된 방송에 온 훅은 무시하고 `200`을 준다
- [x] 목록 순서: 방송 중을 시작 시각 내림차순으로 먼저 주고, 이어서 종료 중 최근 10개를 종료 시각 내림차순으로 준다
- [x] 서비스 시작 시 대기·방송 중으로 남은 라이브 방송을 종료로 정리한다. 테스트로 검증할 수 없으면 06의 직접 확인으로 넘기고 그 사실을 Comments에 적는다
- [x] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다
- [x] 송출 화면 "방송 종료": 어댑터 송출 중지, 송출 키를 담은 종료 API 호출, 미리보기·카메라 끄기(앱 단위 테스트)
- [x] 송출 키를 탭 단위 저장소(sessionStorage)에 보관해서, 송출 화면을 새로고침해도 같은 탭에서 종료할 수 있다(앱 단위 테스트)
- [x] 시청 화면이 3초마다 상세를 다시 불러오고, 종료로 바뀌거나 어댑터가 끊김을 알리면 연결을 닫고 "방송이 종료되었습니다"를 보여 준다. 종료된 방송을 처음 열면 같은 안내와 시작·종료 시각을 보여 준다(앱 단위 테스트)
- [x] 방송 목록 라이브 섹션이 "종료" 배지와 종료 시각을 보여 주고 5초마다 다시 불러온다(앱 단위 테스트)
- [x] 실제 브라우저(PC `localhost`)에서 종료 버튼과 탭 닫기 두 경우 모두 시청 화면·목록에 종료가 반영되는 것을 확인했다
- [x] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
|---|---|---|---|
| `LiveBroadcastEndApiTest` 송출 키로 방송 중인 라이브 방송을 종료하면 200과 종료 상태, 종료 시각을 준다 | 종료 엔드포인트가 없어 200이 아님 | `POST /api/live-broadcasts/{id}/end`, `LiveBroadcast.end`, `LiveBroadcastService.end` | - |
| `LiveBroadcastEndApiTest` 송출 키가 없거나 틀리면 403이고 방송은 그대로다 | 키 확인이 없어 200 | 서비스에서 `StreamKey.matches`가 아니면 `ForbiddenException`, 컨트롤러가 `Bearer ` 접두어가 있을 때만 키로 꺼냄 | - |
| `LiveBroadcastEndApiTest` 이미 종료된 라이브 방송을 다시 종료하면 409다 | 두 번째 종료도 200 | `end`가 false면 `ConflictException` | - |
| `LiveBroadcastEndApiTest` 없는 라이브 방송을 종료하면 404다 | red 없음: 01의 `find`가 이미 `NotFoundException`을 던져 처음부터 green | - | - |
| `MediaMtxUnavailableHookApiTest` 끊김 훅 4개(방송 중→종료, 대기→종료, 종료 후 멱등, 이상한 경로 200) | `/internal/mediamtx/unavailable`이 없어 404 | 훅 엔드포인트와 `onStreamUnavailable`(대기·방송 중이면 `end`, 아니면 무시) | - |
| `LiveBroadcastListOrderApiTest` 방송 중을 최근 시작순으로 먼저, 이어서 종료 중 최근 10개를 최근 종료순으로 준다 | 목록에 종료가 없음 | `findTop10ByStatusOrderByEndedAtDescIdDesc`를 방송 중 뒤에 이어 붙임 | - |
| `LiveBroadcastStartupCleanupApiTest` 시작할 때 대기와 방송 중으로 남은 라이브 방송을 종료로 정리한다 | 앱이 떠도 대기가 PENDING 그대로 | `ApplicationReadyEvent`에서 `endAllUnfinished` 호출하는 `LiveBroadcastStartupCleanup` | 실제 재시작에서 Tomcat이 열린 뒤 1초쯤 정리 전 상태가 보여서 `SmartInitializingSingleton`(웹 서버 시작 전)으로 옮김. 테스트는 그대로 green |
| `LiveBroadcastEnd.test.tsx` 방송 종료를 누르면 송출을 멈추고 송출 키로 종료 API를 부르고 카메라를 끈다 | "방송 종료" 버튼 없음 | `api.endLiveBroadcast`(Bearer), 송출 화면 `ended` 단계, 송출 중지 → 종료 API → 미리보기 트랙 stop·`srcObject` 해제. 가짜 스트림에 stop을 기록하는 트랙 추가 | - |
| `LiveBroadcastEnd.test.tsx` 송출 화면을 새로고침해도 같은 탭에서는 송출 키로 방송을 종료할 수 있다 | 다시 그린 화면에 종료 버튼 없음 | 시작 때 `{ id, streamKey }`를 sessionStorage(`chzzz.ownLiveBroadcast`)에 보관, 있으면 `resumed` 단계로 종료 버튼 표시, 종료하면 지움 | - |
| `LiveBroadcastEnd.test.tsx` 끊김으로 이미 종료된 방송을 종료하려 하면(409) 종료된 것으로 보여 준다 | 409가 처리되지 않은 오류로 남음 | 종료 API의 409는 "이미 종료"로 보고 정상 종료와 같이 처리 | - |
| `LiveBroadcastEnd.test.tsx` 3초마다 상세를 다시 불러와 종료로 바뀌면 연결을 닫고 종료 안내를 보여 준다 | 처음 한 번만 불러옴 | 시청 화면 3초 폴링(서버가 종료로 바꾸면 멈춤), 종료면 플레이어 대신 안내 | 가짜 타이머 아래에서 effect 정리가 늦게 돌아 `close` 단언을 `vi.waitFor`로 기다리게 함 |
| `LiveBroadcastEnd.test.tsx` 시청 연결이 끊기면 연결을 닫고 종료 안내를 보여 준다 | 끊김 알림을 듣지 않음 | `viewing.onDisconnect` → `disconnected` 상태로 종료 안내, 시청 연결 닫음. 종료 시각을 받도록 폴링은 서버가 종료로 바꿀 때까지 계속 | - |
| `LiveBroadcastEnd.test.tsx` 종료된 방송을 처음 열면 연결하지 않고 종료 안내와 시작·종료 시각을 보여 준다 | 시작·종료 시각 표시 없음 | 종료 안내 아래 `<dl>`에 시작·종료 `<time>` | - |
| `LiveBroadcastEnd.test.tsx` 종료된 라이브 방송에 종료 배지와 종료 시각을 보여 주고 5초마다 다시 불러온다 | 종료 항목에도 "방송 중" 배지 | 상태별 배지(종료/방송 중)와 시각(종료 시각/시작 시각), 5초 폴링 | - |

## Comments

- 백엔드 46개, 프론트 30개 테스트 green. `npm run build`, `npm run lint` 통과.
- 시작 정리는 테스트로 검증했다(`LiveBroadcastStartupCleanupApiTest`): 앱 컨텍스트가 뜨기 전에 전용 컨테이너 DB에 Flyway를 돌리고 "지난 실행에서 남은" 대기·방송 중 행을 넣은 뒤, 앱이 뜨면 API로 종료를 확인한다. 06으로 넘기지 않아도 된다.
- 시작 정리는 `ApplicationReadyEvent`로 처음 만들었는데, 실제 재시작에서 Tomcat이 요청을 받기 시작한 뒤 약 1초 동안 정리 전 상태가 보였다. `SmartInitializingSingleton`으로 옮겨 웹 서버가 열리기 전에 정리하고, 재시작 직후 첫 응답부터 종료인 것을 확인했다.
- 실제 브라우저 확인(PC `localhost`): 다른 세션과 겹치지 않게 전용 Postgres 컨테이너(127.0.0.1:55432) + 백엔드 8082 + Vite 5182로 했다(확인 뒤 모두 내림). 이 PC에 웹캠이 없고 공용 MediaMTX 훅은 다른 백엔드(8081)를 부르므로, 송출 대신 MediaMTX가 보낼 훅을 직접 보냈다.
  - 탭 닫기(끊김): `available` 훅으로 방송 중 → 시청 화면·목록을 열어 둔 채 `unavailable` 훅 → 3초 안에 시청 화면 "방송이 종료되었습니다"와 시작·종료 시각, 5초 안에 목록 "종료" 배지와 종료 시각.
  - 종료 버튼: 같은 탭 sessionStorage에 방송의 송출 키를 넣고 `/live/new`를 열어(새로고침 경로) 실제 "방송 종료" 버튼을 클릭 → 서버 상태 종료, 송출 화면 "방송이 종료되었습니다", 다른 탭 시청 화면도 종료 안내.
  - 실제 카메라 송출 → 종료, 실제 탭 닫기 → MediaMTX `runOnUnavailable` → 백엔드 호출 흐름은 06(휴대폰 확인)에서 본다.
- 04에 넘길 것: 종료한 뒤 같은 화면에서 새 방송을 바로 준비하는 버튼(지금은 페이지를 다시 열어야 함), 새로고침 뒤(`resumed`) 미리보기·안내 문구 다듬기, 종료 API가 409 외 오류(네트워크 등)일 때 안내.
- 시청 화면은 어댑터의 끊김 알림만으로도 종료 안내를 보여 준다(티켓 문구대로). 그래서 송출이 아닌 시청자 쪽 네트워크가 잠깐 끊겨도 종료로 보인다. 폴링은 서버가 종료로 바꿀 때까지 계속해 종료 시각을 채운다.
- 공용 파일 변경: `api.ts`(`endLiveBroadcast` 추가), `test/fakeMediaAdapter.ts`(가짜 스트림에 stop을 기록하는 트랙 2개). 기존 테스트 파일은 건드리지 않았다.
