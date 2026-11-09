# 02: 방송 종료와 끊김 처리

**What to build:** 송출자가 "방송 종료"를 누르거나 탭을 닫아 송출이 끊기면 라이브 방송이 **종료**된다. 보고 있던 시청자는 "방송이 종료되었습니다"를 보고, 방송 목록에는 "종료" 배지가 붙는다. 송출 키를 모르는 사람은 종료할 수 없다. 스펙: `.scratch/live-broadcasts/spec.md`.

03(대기 시간 초과·인증 강화), 04(화면 마무리), 05(HTTPS 전체 실행)와 병행한다. 이 티켓은 **종료로 가는 전환 중 송출자 종료와 끊김**, 그리고 서비스 시작 시 정리를 맡는다. 대기 시간 초과 전환과 인증 거부 규칙은 03이 맡는다. 화면에서는 송출 화면의 종료 버튼과 키 보관, 시청 화면의 종료 처리, 목록의 종료 표시만 건드린다.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] `POST /api/live-broadcasts/{id}/end`(`Authorization: Bearer <송출 키>`): 성공하면 `200`과 상태 종료, 종료 시각이 채워진 상세를 준다
- [ ] 종료 API 실패: 키가 없거나 틀리면 `403`, 이미 종료됐으면 `409`, 없으면 `404`. 모두 Problem Details다
- [ ] 내부 "영상 끊김" 훅이 대기·방송 중을 종료로 바꾸고 종료 시각을 남긴다. 이미 종료된 방송에 온 훅은 무시하고 `200`을 준다
- [ ] 목록 순서: 방송 중을 시작 시각 내림차순으로 먼저 주고, 이어서 종료 중 최근 10개를 종료 시각 내림차순으로 준다
- [ ] 서비스 시작 시 대기·방송 중으로 남은 라이브 방송을 종료로 정리한다. 테스트로 검증할 수 없으면 06의 직접 확인으로 넘기고 그 사실을 Comments에 적는다
- [ ] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다
- [ ] 송출 화면 "방송 종료": 어댑터 송출 중지, 송출 키를 담은 종료 API 호출, 미리보기·카메라 끄기(앱 단위 테스트)
- [ ] 송출 키를 탭 단위 저장소(sessionStorage)에 보관해서, 송출 화면을 새로고침해도 같은 탭에서 종료할 수 있다(앱 단위 테스트)
- [ ] 시청 화면이 3초마다 상세를 다시 불러오고, 종료로 바뀌거나 어댑터가 끊김을 알리면 연결을 닫고 "방송이 종료되었습니다"를 보여 준다. 종료된 방송을 처음 열면 같은 안내와 시작·종료 시각을 보여 준다(앱 단위 테스트)
- [ ] 방송 목록 라이브 섹션이 "종료" 배지와 종료 시각을 보여 주고 5초마다 다시 불러온다(앱 단위 테스트)
- [ ] 실제 브라우저(PC `localhost`)에서 종료 버튼과 탭 닫기 두 경우 모두 시청 화면·목록에 종료가 반영되는 것을 확인했다
- [ ] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
|---|---|---|---|
| `LiveBroadcastEndApiTest` 송출 키로 방송 중인 라이브 방송을 종료하면 200과 종료 상태, 종료 시각을 준다 | 종료 엔드포인트가 없어 200이 아님 | `POST /api/live-broadcasts/{id}/end`, `LiveBroadcast.end`, `LiveBroadcastService.end` | - |
| `LiveBroadcastEndApiTest` 송출 키가 없거나 틀리면 403이고 방송은 그대로다 | 키 확인이 없어 200 | 서비스에서 `StreamKey.matches`가 아니면 `ForbiddenException`, 컨트롤러가 `Bearer ` 접두어가 있을 때만 키로 꺼냄 | - |
| `LiveBroadcastEndApiTest` 이미 종료된 라이브 방송을 다시 종료하면 409다 | 두 번째 종료도 200 | `end`가 false면 `ConflictException` | - |
| `LiveBroadcastEndApiTest` 없는 라이브 방송을 종료하면 404다 | red 없음: 01의 `find`가 이미 `NotFoundException`을 던져 처음부터 green | - | - |
| `MediaMtxUnavailableHookApiTest` 끊김 훅 4개(방송 중→종료, 대기→종료, 종료 후 멱등, 이상한 경로 200) | `/internal/mediamtx/unavailable`이 없어 404 | 훅 엔드포인트와 `onStreamUnavailable`(대기·방송 중이면 `end`, 아니면 무시) | - |
| `LiveBroadcastListOrderApiTest` 방송 중을 최근 시작순으로 먼저, 이어서 종료 중 최근 10개를 최근 종료순으로 준다 | 목록에 종료가 없음 | `findTop10ByStatusOrderByEndedAtDescIdDesc`를 방송 중 뒤에 이어 붙임 | - |
| `LiveBroadcastStartupCleanupApiTest` 시작할 때 대기와 방송 중으로 남은 라이브 방송을 종료로 정리한다 | 앱이 떠도 대기가 PENDING 그대로 | `ApplicationReadyEvent`에서 `endAllUnfinished` 호출하는 `LiveBroadcastStartupCleanup` | - |
