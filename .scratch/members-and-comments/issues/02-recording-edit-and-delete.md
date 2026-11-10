# 02: 녹화방송 제목 수정·삭제 (소유자만)

**What to build:** 소유자가 자기 녹화방송 상세 화면에서 제목을 수정하고, 녹화방송을 삭제한다. 삭제하면 업로드 파일도 지워지고 목록으로 돌아가며, 댓글 정리를 위해 "방송 삭제됨" 이벤트가 발행된다. 다른 회원에게는 버튼이 보이지 않고 API를 직접 불러도 `403`, 비로그인은 `401`이다. 소유자 없는 기존 녹화방송은 아무도 수정·삭제할 수 없다. 스펙: `.scratch/members-and-comments/spec.md`.

03(라이브 방송 소유자)·04(댓글)와 병행한다. 댓글 테이블과 이벤트, 소유자 검사 규칙은 01에 있다.

**Blocked by:** 01

**Status:** resolved

- [x] `PATCH /api/recordings/{id}` JSON `{ title }`: 소유자면 `200` 상세(제목 규칙은 등록과 같음, 위반 `400`). 비로그인 `401`, 없음 `404`, 다른 회원·소유자 없는 방송 `403`. 거절 뒤 제목이 그대로임을 조회로 확인
- [x] `DELETE /api/recordings/{id}`: 소유자면 `204`, 이후 상세 `404`. 업로드 파일이면 디스크의 파일도 지워진다. 같은 거절 규칙(`401`/`404`/`403`), 거절 뒤 그대로 남음
- [x] 삭제하면 "방송 삭제됨"(녹화방송, 식별자) 이벤트가 발행된다(댓글 정리는 04가 받는다)
- [x] CSRF 토큰 없이 수정·삭제하면 거절된다
- [x] 백엔드 HTTP API 경계 테스트가 비로그인·소유자·다른 회원 세 경우를 모두 확인한다
- [x] 프론트: 소유자에게만 상세 화면의 "수정"(제목 인라인 수정, 저장·취소, 오류 안내)과 "삭제"(확인 후 삭제, 목록으로 이동)가 보인다. 다른 회원·비로그인에게는 보이지 않는다
- [x] 프론트: 서버가 `401`·`403`을 주면 로그인 안내·권한 없음 안내를 보여 준다
- [x] 새 테스트는 새 파일에 둔다(병합 충돌 줄이기). 1·2단계·01 테스트는 모두 green
- [x] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## Comments

- 백엔드: `PATCH`·`DELETE /api/recordings/{id}`는 01 기반(`LoginMember` 파라미터 → 401, 서비스에서 조회 → 404, `Ownership.requireOwner` → 403)을 그대로 쓴다. 제목 규칙은 등록과 같은 `Title.normalize`(`Recording.rename`).
- 삭제: 한 트랜잭션에서 행 삭제 → `BroadcastDeletedEvent(RECORDING, id)` 발행. 04가 `@EventListener`(동기)로 받으면 댓글 삭제도 같은 트랜잭션에 들어간다. 업로드 파일은 `afterCommit`에서 지운다. 롤백되면 파일이 남아 계속 재생된다. 반대로 커밋 뒤 파일 삭제가 실패하면 디스크에 고아 파일이 남는다(재생 경로가 없어 사용자에게는 보이지 않음).
- 이벤트는 테스트에서 `@RecordApplicationEvents`로 확인한다. 거절(401·403)에서는 이벤트가 없음도 확인했다.
- 거절 테스트(401·403·404·CSRF)는 01 기반 덕에 처음부터 green이었다. 헛도는 테스트가 아님을 보려고 서비스의 `Ownership.requireOwner` 호출을 잠시 빼서 다른 회원·소유자 없는 방송 테스트가 red(200/204)가 되는 것을 확인하고 되돌렸다.
- 프론트: 소유자 판단은 `auth.member.id === recording.owner?.id`(보조). 서버 거절 안내는 `ActionErrorNotice`(401 로그인 안내 + `/login?next=현재 경로` 링크, 403 "권한이 없습니다." + 서버 메시지)로 두어 03(라이브 제목 수정·종료·삭제)도 쓸 수 있게 했다. 삭제 확인은 `window.confirm`. 401이면 공용 `fetch`가 로그인 상태를 비워 버튼·수정란이 사라지고 안내만 남는다.
- 공용 파일 수정: `api.ts`(`updateRecordingTitle`, `deleteRecording`), `index.css`(`.owner-actions`, `.title-editor` 끝에 추가).
- 결과: 백엔드 119개(새 14개), 프론트 66개(새 12개) 테스트 green, 빌드·린트 통과.
- 남은 것: 실제 브라우저 직접 확인(소유자·다른 회원·비로그인 화면과 API 직접 호출)은 하지 않았다. 05의 README 직접 확인 절차에서 함께 확인한다. 녹화방송 삭제 시 댓글이 지워지는지는 04 병합 뒤 확인한다.

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| `RecordingEditApiTest` 소유자가 제목을 수정하면 200 상세가 오고 이후 조회에도 새 제목이 나온다 | `PATCH` 경로 없음(405) | `PATCH /api/recordings/{id}`, `RecordingService.rename`, `Recording.rename`(제목 규칙 재사용) | 없음 |
| `RecordingEditApiTest` 400·401·403(다른 회원·소유자 없음)·404·CSRF 거절 뒤 제목 그대로 | 처음부터 green(01 기반). 소유자 검사를 빼면 403 두 테스트가 200으로 red | 변경 없음 | 없음 |
| `RecordingDeleteApiTest` 소유자가 삭제하면 204, 상세 404, 목록에서 빠지고 방송 삭제됨 이벤트 발행 | `DELETE` 경로 없음(405) | `DELETE /api/recordings/{id}`, `RecordingService.delete`(삭제 + 이벤트 발행) | 없음 |
| `RecordingDeleteApiTest` 업로드 파일 녹화방송을 삭제하면 디스크의 파일도 지워지고 영상 주소는 404 | 파일이 디스크에 남음 | 커밋 뒤(`afterCommit`) 업로드 파일 삭제 | 없음 |
| `RecordingDeleteApiTest` 다른 회원·소유자 없는 방송 삭제 403(파일·이벤트 그대로), 401, 404, CSRF | 소유자 검사 전: 다른 회원·소유자 없는 방송이 204로 red | `Ownership.requireOwner` 호출 | 없음 |
| `RecordingManage.test` 소유자에게 수정·삭제 버튼, 다른 회원·비로그인·소유자 없음에는 없음 | 소유자에게 버튼 없음 | 로그인 회원 id와 소유자 id가 같으면 버튼 표시 | 없음 |
| `RecordingManage.test` 제목을 고쳐 저장하면 CSRF 헤더와 함께 PATCH, 새 제목 표시 | 제목 입력란 없음 | `RecordingTitleEditor`, `api.updateRecordingTitle` | 없음 |
| `RecordingManage.test` 취소 | 처음부터 green(앞 사이클의 취소 버튼) | 변경 없음 | 없음 |
| `RecordingManage.test` 400 입력란 옆 이유, 401 로그인 안내·링크, 403 권한 없음 | 안내 없음(처리되지 않은 거절) | 입력란 오류 표시, `ActionErrorNotice`(401·403) | 수정·삭제가 함께 쓰도록 안내를 페이지 수준으로 둠 |
| `RecordingManage.test` 삭제 확인 → DELETE → 목록 이동, 확인 취소 시 요청 없음, 403 안내 | 삭제 버튼이 아무 일도 하지 않음 | `window.confirm` 후 `api.deleteRecording`, 성공 시 `/`로 이동, 실패 시 `ActionErrorNotice` | 없음 |
