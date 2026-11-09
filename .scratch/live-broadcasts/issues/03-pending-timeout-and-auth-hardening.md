# 03: 대기 시간 초과와 송출·시청 인증 강화

**What to build:** "방송 시작"을 눌렀지만 영상이 들어오지 않은 라이브 방송(권한 거부, 네트워크 막힘)은 정해진 시간이 지나면 자동으로 **종료**되어 목록에 유령 방송이 생기지 않는다. 대기 중인 라이브 방송의 링크를 받은 시청자는 "곧 방송이 시작됩니다"를 보며 기다리고, 방송 중이 되면 저절로 재생된다. 송출 키를 모르거나 끝난 방송으로는 누구도 송출할 수 없고, 송출 키는 어떤 응답에도 드러나지 않는다. 스펙: `.scratch/live-broadcasts/spec.md`.

02(종료·끊김), 04(화면 마무리), 05(HTTPS 전체 실행)와 병행한다. 이 티켓은 **대기 시간 초과 전환**과 **인증 거부 규칙**을 맡는다. 송출자 종료·끊김 전환은 02가 맡는다. 화면에서는 시청 화면의 대기 상태 표시만 건드린다.

**Blocked by:** 01

**Status:** resolved

- [x] 시간은 주입된 시계로 얻고, 테스트 설정에서 시계를 바꿔 끼울 수 있다
- [x] 대기가 `LIVE_PENDING_TIMEOUT`(기본 `60s`)을 넘으면 상세·목록 조회 때 종료로 판정해 저장하고 종료 시각을 남긴다. 같은 규칙의 주기 정리 작업이 있다
- [x] 송출 인증 거부: 틀린 키, 키 없음, 종료된 방송, 시간 초과된 대기, 없는 방송, `live/{숫자}`가 아닌 경로, publish·read가 아닌 행동
- [x] 시청 인증 거부: 대기·종료·없는 방송. 방송 중만 허용한다
- [x] 시간 초과 뒤에 온 "영상 들어옴" 훅은 방송 중으로 바꾸지 않는다
- [x] 생성 응답 외의 모든 응답(목록, 상세, 종료, 오류)에 송출 키와 해시가 없다
- [x] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다
- [x] 시청 화면에서 대기 상태면 "곧 방송이 시작됩니다"를 보여 주고, 폴링으로 방송 중이 되면 시청 연결을 시작한다(앱 단위 테스트)
- [x] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| `LivePendingTimeoutApiTest` 대기가 60초를 넘으면 상세 조회 때 종료가 되고 종료 시각이 남는다 | 시계를 61초 옮겨도 `status`가 `PENDING` | `LiveBroadcast.expireIfPendingTooLong`(종료 시각 = 생성 + 시간 초과), `live.pending-timeout=${LIVE_PENDING_TIMEOUT:60s}`, `find`에서 판정 | 테스트용 `MutableClock`(@Primary)을 `MutableClockConfiguration`으로 분리 |
| `LivePendingTimeoutApiTest` 대기가 60초를 넘으면 목록 조회 때 종료로 저장된다 | 목록 조회 뒤 DB가 `PENDING`/`ended_at` null | `expireOverduePending()`(생성 시각 기준 조회 후 일괄 판정)을 `list`에서 먼저 호출 | 없음. 종료 항목이 목록에 보이는 것은 02 범위라 저장 상태로 확인 |
| `LivePendingTimeoutApiTest` 시간 초과된 대기에는 맞는 송출 키로도 송출할 수 없다 | 인증 훅이 `200` | `authorize`가 판정 후 조회(`findCurrent`)를 쓰게 함 | `find`도 `findCurrent`로 합침 |
| `LivePendingTimeoutApiTest` 시간 초과 뒤에 영상 들어옴 훅이 와도 방송 중이 되지 않는다 | 상세가 `LIVE` | `onStreamAvailable`도 `findCurrent` 사용 | 없음 |
| `LivePendingSweepTest` 조회가 없어도 주기 정리 작업이 시간 초과된 대기를 종료로 바꾼다 | 5초 기다려도 `PENDING` | `SchedulingConfig`(@EnableScheduling), `LivePendingSweeper`(`live.pending-sweep-interval`, 기본 10s)가 `expireOverduePending` 호출 | 없음 |
| `LivePendingTimeoutApiTest` 60초가 되기 전에는 대기로 남고 송출할 수 있다 | 처음부터 green(경계 확인용으로 추가) | 없음 | 없음 |
| `MediaMtxAuthRulesApiTest` live 다음 숫자 식별자 형식이 아닌 경로는 맞는 키로도 거부한다 (`live/01`, `live/1\n`) | 두 경로가 `200` 허용. `find`+`$`라서 앞자리 0과 끝 개행이 같은 방송으로 풀렸다(같은 방송을 다른 MediaMTX 경로로 송출 가능) | `MediaPaths.idOf`를 `matchEntire` + `[1-9]\d*`로 바꿔 정규형만 인정 | 없음 |
| `MediaMtxAuthRulesApiTest` 나머지(키 없음·빈 키, 다른 방송의 키, 방송 중에 재송출, 없는 방송 송출·시청, 그 밖의 경로 8종, 경로 없음, 행동 6종, 대기 시청) | 처음부터 green(티켓 01 구현이 이미 거부). 거부 규칙 고정용 | 없음 | 없음 |
| `StreamKeyExposureApiTest` 대기 상세 / 방송 중 목록·상세 / 404·400·인증 거부 응답에 송출 키와 해시가 없다 | 처음부터 green. 회귀 방지용(본문·헤더 모두 검사) | 없음 | 없음 |
| `LiveBroadcastPending.test.tsx` 대기면 "곧 방송이 시작됩니다"를 보여 주고, 다시 불러와 방송 중이 되면 시청 연결을 시작한다 | 문구가 없음(상세를 한 번만 불러오고 폴링도 없음) | 시청 화면이 상세를 3초마다 다시 불러오고(종료면 멈춤) 대기면 `role="status"` 문구 표시. 기존 "방송 중이면 시청 연결" 효과가 상태 변화에 반응 | 없음 |

## Comments

- 확인한 것: 백엔드 `./gradlew test` 67개 green, 프론트 `npm test` 24개 green, `npm run build`, `npm run lint` 통과.
- 시간 초과 판정은 `LiveBroadcast.expireIfPendingTooLong` 한 곳이다. 상세(`find`), 목록(`list` → `expireOverduePending`), 송출 인증(`authorize`), 영상 들어옴 훅(`onStreamAvailable`), 주기 정리(`LivePendingSweeper`, `live.pending-sweep-interval` 기본 10s)가 모두 이 규칙을 쓴다. 종료 시각은 판정한 때가 아니라 "생성 시각 + 시간 초과"로 남겨, 어느 경로로 판정해도 같은 값이다.
- 설정: `live.pending-timeout=${LIVE_PENDING_TIMEOUT:60s}`. `.env.example`·compose·README에 `LIVE_PENDING_TIMEOUT`을 넣는 일은 이 브랜치에서 하지 않았다(05·README 마무리에서 함께 정리).
- 테스트 시계: `MutableClockConfiguration`(`@Primary MutableClock`)을 `@Import`하면 `ClockConfig`의 시계 대신 쓰인다. 02도 시간 관련 테스트에 쓸 수 있다.
- 인증 강화에서 실제로 막힌 틈은 경로 형식 하나였다: `live/01`, `live/1\n`이 방송 1로 풀려 같은 키로 다른 MediaMTX 경로에 송출할 수 있었다. `MediaPaths.idOf`가 정규형(`matchEntire`, 앞자리 0 없음)만 인정한다. 나머지 거부 규칙은 01 구현이 이미 지키고 있어 테스트로 고정만 했다.
- "종료된 방송" 송출 거부는 이 브랜치에서 시간 초과로 종료된 경우로 검증했다. 송출자 종료·끊김으로 종료된 경우와 종료 API 응답의 키 비노출은 02의 종료 API가 생긴 뒤 02 쪽 테스트(또는 병합 후)로 확인한다. 상태가 `PENDING`이 아니면 송출을 거부하는 같은 분기를 탄다.
- 목록 조회 때 저장되는지는 02가 목록에 종료 항목을 넣기 전이라 DB 상태로 확인했다. 02 병합 뒤에는 목록 응답으로 확인하도록 바꿀 수 있다.
- 병합 시 02와 겹칠 곳: `LiveBroadcastService`(02가 종료·끊김 메서드 추가 — 둘 다 `findCurrent`를 거치면 시간 초과가 먼저 반영된다), `LiveBroadcast`(02의 종료 전환 메서드), `LiveBroadcastWatchPage.tsx`(이 브랜치가 3초 폴링을 넣었고 종료되면 폴링을 멈춘다. 02의 종료 표시·연결 해제는 같은 폴링 결과의 `status`를 보면 된다), `application.properties` 끝 두 줄.
- 남은 것: 실제 MediaMTX와의 직접 확인(권한 거부 후 60초 뒤 목록·상세가 종료로 바뀌는지)은 하지 않았다. README 직접 확인 절차에서 다룬다.
