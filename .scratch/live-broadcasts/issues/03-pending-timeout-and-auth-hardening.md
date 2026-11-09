# 03: 대기 시간 초과와 송출·시청 인증 강화

**What to build:** "방송 시작"을 눌렀지만 영상이 들어오지 않은 라이브 방송(권한 거부, 네트워크 막힘)은 정해진 시간이 지나면 자동으로 **종료**되어 목록에 유령 방송이 생기지 않는다. 대기 중인 라이브 방송의 링크를 받은 시청자는 "곧 방송이 시작됩니다"를 보며 기다리고, 방송 중이 되면 저절로 재생된다. 송출 키를 모르거나 끝난 방송으로는 누구도 송출할 수 없고, 송출 키는 어떤 응답에도 드러나지 않는다. 스펙: `.scratch/live-broadcasts/spec.md`.

02(종료·끊김), 04(화면 마무리), 05(HTTPS 전체 실행)와 병행한다. 이 티켓은 **대기 시간 초과 전환**과 **인증 거부 규칙**을 맡는다. 송출자 종료·끊김 전환은 02가 맡는다. 화면에서는 시청 화면의 대기 상태 표시만 건드린다.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] 시간은 주입된 시계로 얻고, 테스트 설정에서 시계를 바꿔 끼울 수 있다
- [ ] 대기가 `LIVE_PENDING_TIMEOUT`(기본 `60s`)을 넘으면 상세·목록 조회 때 종료로 판정해 저장하고 종료 시각을 남긴다. 같은 규칙의 주기 정리 작업이 있다
- [ ] 송출 인증 거부: 틀린 키, 키 없음, 종료된 방송, 시간 초과된 대기, 없는 방송, `live/{숫자}`가 아닌 경로, publish·read가 아닌 행동
- [ ] 시청 인증 거부: 대기·종료·없는 방송. 방송 중만 허용한다
- [ ] 시간 초과 뒤에 온 "영상 들어옴" 훅은 방송 중으로 바꾸지 않는다
- [ ] 생성 응답 외의 모든 응답(목록, 상세, 종료, 오류)에 송출 키와 해시가 없다
- [ ] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다
- [ ] 시청 화면에서 대기 상태면 "곧 방송이 시작됩니다"를 보여 주고, 폴링으로 방송 중이 되면 시청 연결을 시작한다(앱 단위 테스트)
- [ ] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| `LivePendingTimeoutApiTest` 대기가 60초를 넘으면 상세 조회 때 종료가 되고 종료 시각이 남는다 | 시계를 61초 옮겨도 `status`가 `PENDING` | `LiveBroadcast.expireIfPendingTooLong`(종료 시각 = 생성 + 시간 초과), `live.pending-timeout=${LIVE_PENDING_TIMEOUT:60s}`, `find`에서 판정 | 테스트용 `MutableClock`(@Primary)을 `MutableClockConfiguration`으로 분리 |
| `LivePendingTimeoutApiTest` 대기가 60초를 넘으면 목록 조회 때 종료로 저장된다 | 목록 조회 뒤 DB가 `PENDING`/`ended_at` null | `expireOverduePending()`(생성 시각 기준 조회 후 일괄 판정)을 `list`에서 먼저 호출 | 없음. 종료 항목이 목록에 보이는 것은 02 범위라 저장 상태로 확인 |
| `LivePendingTimeoutApiTest` 시간 초과된 대기에는 맞는 송출 키로도 송출할 수 없다 | 인증 훅이 `200` | `authorize`가 판정 후 조회(`findCurrent`)를 쓰게 함 | `find`도 `findCurrent`로 합침 |
| `LivePendingTimeoutApiTest` 시간 초과 뒤에 영상 들어옴 훅이 와도 방송 중이 되지 않는다 | 상세가 `LIVE` | `onStreamAvailable`도 `findCurrent` 사용 | 없음 |
| `LivePendingSweepTest` 조회가 없어도 주기 정리 작업이 시간 초과된 대기를 종료로 바꾼다 | 5초 기다려도 `PENDING` | `SchedulingConfig`(@EnableScheduling), `LivePendingSweeper`(`live.pending-sweep-interval`, 기본 10s)가 `expireOverduePending` 호출 | 없음 |
| `LivePendingTimeoutApiTest` 60초가 되기 전에는 대기로 남고 송출할 수 있다 | 처음부터 green(경계 확인용으로 추가) | 없음 | 없음 |
