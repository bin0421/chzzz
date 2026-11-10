# 03: 라이브 방송 소유자와 송출 제어

**What to build:** 로그인한 회원만 라이브 방송을 시작하고, 시작한 회원이 소유자가 된다. 소유자는 송출하던 탭이 아니어도(다른 탭·기기) 로그인만 되어 있으면 방송을 종료하고, 제목을 수정하며, 종료된 방송을 삭제한다. 다른 회원·비로그인은 API를 직접 불러도 거절된다. 송출 키는 MediaMTX 송출 허가에만 쓴다. 스펙: `.scratch/members-and-comments/spec.md`, 결정: ADR-0006.

02(녹화방송 수정·삭제)·04(댓글)와 병행한다. 소유자 칸·이벤트·소유자 검사 규칙은 01에 있다.

**Blocked by:** 01

**Status:** resolved

- [x] `POST /api/live-broadcasts`: 비로그인 `401`. 로그인하면 소유자가 기록되고 응답·목록·상세에 `owner: { id, nickname }`. 송출 키는 이 응답에만 나온다
- [x] `POST /api/live-broadcasts/{id}/end`: 송출 키 헤더 대신 세션으로 검사. 소유자면 `200`, 비로그인 `401`, 없음 `404`, 다른 회원·소유자 없는 방송 `403`, 이미 종료 `409`. 송출 키를 헤더에 실어 보내도 소유자가 아니면 거절된다
- [x] `PATCH /api/live-broadcasts/{id}` JSON `{ title }`: 소유자면 대기·방송 중·종료 모두 `200`. 같은 거절 규칙
- [x] `DELETE /api/live-broadcasts/{id}`: 소유자이고 종료면 `204`와 "방송 삭제됨"(라이브 방송, 식별자) 이벤트. 대기·방송 중이면 `409`. 같은 거절 규칙
- [x] MediaMTX 송출 허가(송출 키), 영상 들어옴·끊김 훅, 대기 시간 초과, 재시작 정리는 2단계와 같이 동작한다(기존 테스트 green)
- [x] 백엔드 HTTP API 경계 테스트가 비로그인·소유자·다른 회원 세 경우와 거절 뒤 상태가 그대로임을 확인한다
- [x] 프론트 송출 화면: 비로그인이면 `/login?next=/live/new`로 간다. 탭 저장소(sessionStorage) 송출 키 보관과 "이 탭에서 시작한 라이브 방송" 재개 표시를 없애고, "방송 종료"는 세션으로 종료한다(2단계 관련 테스트를 새 동작에 맞게 바꾼다)
- [x] 프론트 시청 화면: 소유자에게만 "제목 수정", "방송 종료"(대기·방송 중), "삭제"(종료 후, 확인 후 목록으로)가 보인다. 소유자 닉네임 표시. 서버 `401`·`403`·`409`에 맞는 안내
- [x] 프론트 방송 목록 라이브 섹션에 소유자 닉네임
- [x] 새 테스트는 새 파일에 둔다. 1·2단계·01 테스트는 모두 green(바꾼 2단계 테스트는 이유를 Comments에 적는다)
- [x] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| `LiveBroadcastOwnerApiTest` 로그인하지 않고 라이브 방송을 시작하면 401이고 만들어지지 않는다 | 비로그인 시작이 `201`로 만들어짐 | 시작 컨트롤러에 `LoginMember` 파라미터. 2단계 라이브 테스트 9개의 시작 요청에 가입 세션(`signUpSomeone`)을 실음 | 시작 도우미 `startLiveAs`·`signUpSomeone`을 `LiveTestSupport`로 |
| `LiveBroadcastOwnerApiTest` 시작한 회원이 소유자가 되고 시작·목록·상세에 소유자 닉네임 / 회원 기능 이전 방송은 소유자 없음 | 응답에 `owner` 없음 | `LiveBroadcast.owner`(`owner_id`) 매핑, 시작 시 회원 기록, 세 응답에 `owner` | 목록 조회에 `@EntityGraph(owner)` |
| `LiveBroadcastControlApiTest` 종료: 다른 탭의 소유자 200, 비로그인 401(송출 키 있어도), 다른 회원 403(송출 키 있어도), 소유자 없음 403, 404·409, 401이 404보다 먼저 | 송출 키 없는 소유자 `403`, 키 가진 비로그인·다른 회원 `200`, 비로그인 없는 방송 `404` | 종료를 `LoginMember` + `Ownership.requireOwner`로. 송출 키 헤더 처리 삭제. `LiveBroadcastEndApiTest`(2단계)를 소유자 세션 기준으로 바꿈 | - |
| `LiveBroadcastControlApiTest` 제목 수정: 대기·방송 중·종료 모두 200, 401·403·403(소유자 없음)·404 뒤 그대로, 빈 제목 400 | `PATCH` 없음(`405`) | `PATCH /api/live-broadcasts/{id}`, `LiveBroadcast.rename`(공통 `Title` 규칙) | - |
| `LiveBroadcastControlApiTest` 삭제: 종료면 204·사라짐·"방송 삭제됨" 이벤트, 대기·방송 중 409, 401·403·403·404 뒤 그대로 | `DELETE` 없음(`405`) | `DELETE /api/live-broadcasts/{id}`, 종료 아니면 `409`, `BroadcastDeletedEvent(LIVE_BROADCAST, id)` 발행 | - |
| `LiveBroadcastOwner.test` 비로그인으로 송출 화면에 들어가면 로그인 화면으로 가고, 로그인하면 돌아온다 | 송출 화면이 그대로 그려짐 | `/live/new`를 `RequireLogin`으로 감쌈 | - |
| `LiveBroadcastEnd.test`(2단계 3개를 새 동작으로) 송출 키 없이 세션으로 종료 / 탭 저장소에 키를 남기지 않고 새로고침하면 재개 표시 없음 / 409면 종료로 보여 줌 | 종료 요청에 `Authorization: Bearer`가 실림, sessionStorage에 송출 키가 남음 | 송출 화면의 sessionStorage 보관·`resumed` 단계 삭제, `api.endLiveBroadcast(id)`에서 송출 키 헤더 삭제 | - |
| `LiveBroadcastControl.test` 소유자 닉네임과 소유자에게만 제목 수정·방송 종료(대기·방송 중)·삭제(종료) / 다른 회원·비로그인은 버튼 없음 | 소유자 닉네임·버튼 없음 | 시청 화면에 `.owner`와 `owner-actions`(로그인 회원 id == `owner.id`) | - |
| `LiveBroadcastControl.test` 시청 화면에서 방송 종료 → 세션으로 종료, 삭제 버튼 | 버튼에 동작 없음 | `handleEnd` → 응답으로 상세 갱신 | - |
| `LiveBroadcastControl.test` 제목 수정 저장 → PATCH·제목 갱신 / 400이면 입력란 옆 이유 | 입력란 없음 | `TitleEditor`(공통 `validateTitle`), `api.renameLiveBroadcast` | 400은 "취소" 후 원래 제목을 확인하도록 테스트를 맞춤(편집 중에는 머리글 대신 입력란) |
| `LiveBroadcastControl.test` 삭제: 확인 취소면 요청 없음, 확인하면 DELETE 후 목록 | 버튼에 동작 없음 | `window.confirm` 후 `api.deleteLiveBroadcast`, `navigate('/')` | - |
| `LiveBroadcastControl.test` 403 권한 없음 안내 / 401 로그인 안내와 `next` 로그인 링크 / 409 서버 이유 | 거절이 처리되지 않은 오류로 남음 | `control()`로 감싸 `ControlErrorNotice` 표시 | - |
| `LiveBroadcastControl.test` 제목 수정 403도 같은 안내 | 400이 아닌 거절을 입력란에서 처리 | `TitleEditor`가 400 외 거절은 `onRejected`로 넘김 | - |
| `LiveBroadcastOwner.test` 라이브 섹션에 소유자 닉네임(소유자 없으면 표시 안 함) | 목록에 닉네임 없음 | 라이브 섹션 항목에 `.owner` | 관리 버튼·제목 편집 폼 CSS |

## Comments

- 백엔드: 라이브 방송 시작은 `LoginMember`로 로그인을 요구하고, `LiveBroadcast.owner`(`V3`의 `owner_id`, 녹화방송과 같은 `@ManyToOne`)에 시작한 회원을 기록한다. 시작·목록·상세 응답에 `owner: { id, nickname } | null`. 목록 조회 두 개에 `@EntityGraph(owner)`. 새 마이그레이션은 만들지 않았다.
- 종료·제목 수정·삭제는 모두 `LiveBroadcastService.find`(404) → `Ownership.requireOwner`(403) → 상태 검사(409) 순서이고, 401은 컨트롤러의 `LoginMember`가 먼저 낸다. 종료에서 송출 키 헤더 처리를 지웠으므로 송출 키를 실어 보내도 비로그인은 401, 다른 회원은 403이다. 송출 키는 MediaMTX 송출 허가(`authorize("publish")`)에만 쓰인다.
- 제목 수정은 모든 상태에서 되고 공통 `Title` 규칙(400 `errors[title]`)을 쓴다. 검증은 소유자 검사 뒤라 다른 회원은 내용과 상관없이 403이다.
- 삭제는 종료된 방송만 지우고 `BroadcastDeletedEvent(LIVE_BROADCAST, id)`를 같은 트랜잭션 안에서 발행한다(04의 댓글 정리가 받는다). 테스트는 `@RecordApplicationEvents`로 이벤트를 확인했다.
- 바꾼 2단계 테스트와 이유:
  - 백엔드 라이브 테스트 9개(`LiveBroadcastApiTest`, `LiveBroadcastListOrderApiTest`, `LivePendingSweepTest`, `LivePendingTimeoutApiTest`, `MediaMtxAuthRulesApiTest`, `MediaMtxHookApiTest`, `MediaMtxUnavailableHookApiTest`, `StreamKeyExposureApiTest`, `LiveBroadcastEndApiTest`): 시작에 로그인이 필요해져서 시작 요청에 가입 세션(`signUpSomeone`, 겹치지 않는 아이디)을 실었다. 단언은 그대로다.
  - `LiveBroadcastEndApiTest`: 종료 근거가 송출 키에서 소유자 세션으로 바뀌었다(ADR-0006). 성공·409·404는 소유자 세션으로 보내고, "송출 키가 없거나 틀리면 403"은 "송출 키만으로는 종료할 수 없다(비로그인 401, 상태 그대로)"로 바꿨다.
  - 프론트 `LiveBroadcastEnd.test`의 송출 화면 3개: 종료 요청에 송출 키 헤더가 실리는지, 새로고침 뒤 탭 저장소로 재개하는지 확인하던 것을 "헤더 없음", "탭 저장소에 키 없음·재개 표시 없음", "방송 중 화면에서 409면 종료로 보여 줌"으로 바꿨다. 새로고침한 송출자는 시청 화면에서 소유자로 종료한다.
- 프론트: 시청 화면 관리 버튼은 `useAuth`의 회원 id와 `owner.id`가 같을 때만 보인다(보조 수단). 서버 거절은 401이면 "로그인이 필요합니다"와 `/login?next=/live/<id>` 링크, 403이면 "이 방송을 관리할 권한이 없습니다.", 그 밖(409)은 서버의 `detail`을 보여 준다. 공용 `fetch`가 401에 로그인 상태를 비우므로 401 뒤에는 관리 버튼도 사라진다. 삭제 확인은 `window.confirm`.
- 확인한 것: 백엔드 `./gradlew test` 120개, 프론트 `npm test` 67개 green, `npm run build`·`npm run lint` 통과.
- 남은 것: 실제 브라우저·휴대폰 확인(송출 → 다른 기기에서 소유자 종료, 다른 회원 거절)은 05의 직접 확인 절차에서 한다. 이 티켓에서는 브라우저 수동 확인을 하지 않았다. 녹화방송 상세(02)와 라이브 시청 화면이 각자 제목 수정·삭제 UI를 가지므로, 병합 뒤 02와 겹치는 모양(인라인 편집 폼, 확인 방식, 거절 안내)을 맞출지 05에서 본다. 이 파일의 `## TDD 기록`은 병합할 때 `docs/tdd-log.md`로 옮긴다.
