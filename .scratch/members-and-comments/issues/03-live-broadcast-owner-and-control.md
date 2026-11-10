# 03: 라이브 방송 소유자와 송출 제어

**What to build:** 로그인한 회원만 라이브 방송을 시작하고, 시작한 회원이 소유자가 된다. 소유자는 송출하던 탭이 아니어도(다른 탭·기기) 로그인만 되어 있으면 방송을 종료하고, 제목을 수정하며, 종료된 방송을 삭제한다. 다른 회원·비로그인은 API를 직접 불러도 거절된다. 송출 키는 MediaMTX 송출 허가에만 쓴다. 스펙: `.scratch/members-and-comments/spec.md`, 결정: ADR-0006.

02(녹화방송 수정·삭제)·04(댓글)와 병행한다. 소유자 칸·이벤트·소유자 검사 규칙은 01에 있다.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] `POST /api/live-broadcasts`: 비로그인 `401`. 로그인하면 소유자가 기록되고 응답·목록·상세에 `owner: { id, nickname }`. 송출 키는 이 응답에만 나온다
- [ ] `POST /api/live-broadcasts/{id}/end`: 송출 키 헤더 대신 세션으로 검사. 소유자면 `200`, 비로그인 `401`, 없음 `404`, 다른 회원·소유자 없는 방송 `403`, 이미 종료 `409`. 송출 키를 헤더에 실어 보내도 소유자가 아니면 거절된다
- [ ] `PATCH /api/live-broadcasts/{id}` JSON `{ title }`: 소유자면 대기·방송 중·종료 모두 `200`. 같은 거절 규칙
- [ ] `DELETE /api/live-broadcasts/{id}`: 소유자이고 종료면 `204`와 "방송 삭제됨"(라이브 방송, 식별자) 이벤트. 대기·방송 중이면 `409`. 같은 거절 규칙
- [ ] MediaMTX 송출 허가(송출 키), 영상 들어옴·끊김 훅, 대기 시간 초과, 재시작 정리는 2단계와 같이 동작한다(기존 테스트 green)
- [ ] 백엔드 HTTP API 경계 테스트가 비로그인·소유자·다른 회원 세 경우와 거절 뒤 상태가 그대로임을 확인한다
- [ ] 프론트 송출 화면: 비로그인이면 `/login?next=/live/new`로 간다. 탭 저장소(sessionStorage) 송출 키 보관과 "이 탭에서 시작한 라이브 방송" 재개 표시를 없애고, "방송 종료"는 세션으로 종료한다(2단계 관련 테스트를 새 동작에 맞게 바꾼다)
- [ ] 프론트 시청 화면: 소유자에게만 "제목 수정", "방송 종료"(대기·방송 중), "삭제"(종료 후, 확인 후 목록으로)가 보인다. 소유자 닉네임 표시. 서버 `401`·`403`·`409`에 맞는 안내
- [ ] 프론트 방송 목록 라이브 섹션에 소유자 닉네임
- [ ] 새 테스트는 새 파일에 둔다. 1·2단계·01 테스트는 모두 green(바꾼 2단계 테스트는 이유를 Comments에 적는다)
- [ ] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| `LiveBroadcastOwnerApiTest` 로그인하지 않고 라이브 방송을 시작하면 401이고 만들어지지 않는다 | 비로그인 시작이 `201`로 만들어짐 | 시작 컨트롤러에 `LoginMember` 파라미터. 2단계 라이브 테스트 9개의 시작 요청에 가입 세션(`signUpSomeone`)을 실음 | 시작 도우미 `startLiveAs`·`signUpSomeone`을 `LiveTestSupport`로 |
| `LiveBroadcastOwnerApiTest` 시작한 회원이 소유자가 되고 시작·목록·상세에 소유자 닉네임 / 회원 기능 이전 방송은 소유자 없음 | 응답에 `owner` 없음 | `LiveBroadcast.owner`(`owner_id`) 매핑, 시작 시 회원 기록, 세 응답에 `owner` | 목록 조회에 `@EntityGraph(owner)` |
| `LiveBroadcastControlApiTest` 종료: 다른 탭의 소유자 200, 비로그인 401(송출 키 있어도), 다른 회원 403(송출 키 있어도), 소유자 없음 403, 404·409, 401이 404보다 먼저 | 송출 키 없는 소유자 `403`, 키 가진 비로그인·다른 회원 `200`, 비로그인 없는 방송 `404` | 종료를 `LoginMember` + `Ownership.requireOwner`로. 송출 키 헤더 처리 삭제. `LiveBroadcastEndApiTest`(2단계)를 소유자 세션 기준으로 바꿈 | - |
| `LiveBroadcastControlApiTest` 제목 수정: 대기·방송 중·종료 모두 200, 401·403·403(소유자 없음)·404 뒤 그대로, 빈 제목 400 | `PATCH` 없음(`405`) | `PATCH /api/live-broadcasts/{id}`, `LiveBroadcast.rename`(공통 `Title` 규칙) | - |
| `LiveBroadcastControlApiTest` 삭제: 종료면 204·사라짐·"방송 삭제됨" 이벤트, 대기·방송 중 409, 401·403·403·404 뒤 그대로 | `DELETE` 없음(`405`) | `DELETE /api/live-broadcasts/{id}`, 종료 아니면 `409`, `BroadcastDeletedEvent(LIVE_BROADCAST, id)` 발행 | - |
