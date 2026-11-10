# 04: 방송별 댓글

**What to build:** 녹화방송 상세와 라이브 방송 시청 화면 아래에 그 방송의 댓글 목록이 보인다. 누구나 볼 수 있고 오래된 순이며, 라이브 방송은 3초마다 다시 불러온다. 로그인한 회원은 댓글을 쓰고, 비로그인은 작성란 대신 로그인 안내를 보며 API로 써도 `401`로 거절된다. 작성자는 자기 댓글을 수정("수정됨" 표시)·삭제하고, 다른 회원은 방송 소유자라도 `403`이다. 방송이 삭제되면 그 방송의 댓글도 지워진다. 스펙: `.scratch/members-and-comments/spec.md`.

02(녹화방송 수정·삭제)·03(라이브 방송 소유자)과 병행한다. 댓글 테이블, 작성자 검사 규칙, "방송 삭제됨" 이벤트는 01에 있다.

**Blocked by:** 01

**Status:** resolved

- [x] `GET /api/recordings/{id}/comments`, `GET /api/live-broadcasts/{id}/comments`: 공개, 오래된 순 전체 `[{ id, content, author: { id, nickname }, createdAt, updatedAt }]`. 방송 없음 `404`
- [x] 같은 경로 `POST` JSON `{ content }`: 로그인하면 `201` 댓글. 비로그인 `401`, 방송 없음 `404`, 내용(공백 제거 1~500자) 위반 `400`(`errors`에 `content`). 종료된 라이브 방송에도 쓸 수 있다
- [x] `PATCH /api/comments/{id}` JSON `{ content }`: 작성자면 `200`과 `updatedAt`. 비로그인 `401`, 없음 `404`, 다른 회원(방송 소유자 포함) `403`
- [x] `DELETE /api/comments/{id}`: 작성자면 `204`. 같은 거절 규칙
- [x] 거절 뒤 댓글이 그대로임을 조회로 확인하고, CSRF 토큰 없는 작성·수정·삭제는 거절된다
- [x] "방송 삭제됨" 이벤트를 받으면 그 방송의 댓글을 모두 지운다(테스트는 이벤트를 직접 발행해 확인. 실제 삭제 API와의 연결은 05에서 병합 후 확인)
- [x] 백엔드 HTTP API 경계 테스트가 비로그인·작성자·다른 회원 세 경우를 확인한다
- [x] 프론트: 녹화방송 상세·라이브 시청 화면 아래 댓글 영역(닉네임, 작성 시각, "수정됨"). 로그인 시 작성란(입력 오류 안내), 비로그인 시 "로그인하면 댓글을 쓸 수 있습니다"와 `next` 로그인 링크
- [x] 프론트: 작성자에게만 "수정"(인라인)·"삭제"(확인 후). 서버 `401`·`403`에 맞는 안내
- [x] 프론트: 라이브 방송 댓글은 3초마다 다시 불러와 다른 사람의 댓글이 나타난다
- [x] 새 테스트는 새 파일에 둔다. 1·2단계·01 테스트는 모두 green
- [x] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## Comments

- 백엔드: 댓글 모듈 `com.chzzz.comment`(엔티티·저장소·서비스·컨트롤러, 내용 규칙 `CommentContent`). `V3`의 `comment` 테이블을 그대로 매핑했고 새 마이그레이션은 없다. 방송 존재 확인(404)은 `RecordingRepository`·`LiveBroadcastRepository`의 `existsById`만 읽는다. 작성자 검사는 `Ownership.requireOwner(authorId, member, "댓글 작성자만 할 수 있습니다.")`라서 방송 소유자도 남의 댓글은 403이다.
- 방송 삭제 연결: `BroadcastCommentsCleaner`가 `BroadcastDeletedEvent`를 `@EventListener` + `@Transactional`로 받아 그 방송의 댓글을 한 번에 지운다. 커밋 뒤 리스너가 아니라 일반 리스너라서, 02·03이 삭제 트랜잭션 안에서 이벤트를 발행하면 방송 삭제와 댓글 삭제가 함께 커밋·롤백된다. 테스트는 이벤트를 직접 발행했다. **05에서 병합 후 실제 삭제 API로 댓글이 지워지는지 확인해야 한다.**
- 프론트: 댓글 영역은 `src/comments/CommentSection.tsx` 하나이고 화면 파일에는 import 한 줄 + 컴포넌트 한 줄씩만 더했다(`RecordingDetailPage`, `LiveBroadcastWatchPage`는 `pollMs={DETAIL_POLL_MS}`로 3초 폴링). 댓글 API는 `src/comments/commentApi.ts`에 두고, 이를 위해 `api.ts`의 `request`·`json`을 export만 했다(02·03과 충돌을 줄이려고 `api` 객체에는 손대지 않음).
- 댓글 목록 불러오기 실패는 `role="alert"`가 아닌 일반 안내로 보여 준다. 기존 화면 테스트가 댓글 경로를 스텁하지 않고 alert 부재를 확인하기 때문이다. 401은 공용 `fetch`가 로그인 상태를 비워 작성란이 로그인 안내로 바뀌고 "로그인이 필요합니다. 다시 로그인해 주세요.", 403은 "댓글 작성자만 수정·삭제할 수 있습니다."를 보여 준다.
- 실제 브라우저 확인(PC, 개발 실행, 2026-11-10): 임시 DB `chzzz_t04` + 백엔드 8084 + Vite 5184. 가입 → 녹화방송 등록 → 상세에서 댓글 작성 → 인라인 수정("수정됨") 확인. 이 과정에서 (1) 수정 입력란에 포커스가 가지 않고 (2) 전역 `li` 가로 배치 때문에 댓글이 옆으로 늘어서는 문제를 찾아 고쳤다(포커스는 테스트로 red→green). API 직접 호출: 비로그인 작성·수정 401, 다른 회원 수정·삭제 403, CSRF 헤더 없는 삭제 403, 다른 회원 작성 201, 거절 뒤 댓글 그대로. 임시 DB와 업로드 폴더는 지웠다.
- 남은 것: 라이브 방송 댓글의 실제 브라우저 확인(라이브 시작이 03에서 바뀌는 중이라 병합 후 05에서), 삭제 API와의 연결 확인(05), README 공개 범위·확인 절차(05). TDD 기록은 병합할 때 `docs/tdd-log.md`로 옮긴다.
- 결과: 백엔드 120개(새 15개), 프론트 68개(새 14개) green, 빌드·린트 통과.

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| `CommentApiTest` 로그인한 회원이 녹화방송에 쓴 댓글이 누구나 보는 목록에 오래된 순으로 나온다 | 댓글 API가 없어 작성이 201이 아님 | `Comment` 엔티티·`CommentContent` 규칙·저장소·서비스·컨트롤러(녹화방송 목록·작성) | - |
| `CommentApiTest` 종료된 라이브 방송에도 댓글을 쓰고 누구나 목록을 본다 / 같은 식별자라도 녹화방송과 라이브 방송의 댓글은 섞이지 않는다 | 라이브 방송 댓글 경로 없음 | 라이브 방송 경로 추가, 존재 확인에 `LiveBroadcastRepository.existsById` | - |
| `CommentWriteRejectionApiTest` (비로그인 401, 방송 없음 404, 401이 404보다 먼저, 내용 400) | red 없음: 01의 `LoginMember`·공통 예외 처리와 위 사이클의 내용 규칙으로 처음부터 green | - | - |
| `CommentAuthorApiTest` 작성자 수정·삭제, 비로그인 401·다른 회원/방송 소유자 403·없는 댓글 404, 거절 뒤 그대로 | `PATCH`·`DELETE /api/comments/{id}` 없음 | `CommentService.edit`·`delete`(`Ownership.requireOwner`), 컨트롤러 매핑 | - |
| `CommentAuthorApiTest` CSRF 토큰 없는 댓글 작성·수정·삭제는 403 | red 없음: 01의 CSRF 설정으로 처음부터 green | - | - |
| `CommentBroadcastDeletedApiTest` 방송 삭제됨 이벤트(녹화방송·라이브 방송)를 받으면 그 방송의 댓글만 지운다 | 리스너가 없어 댓글이 남음 | `BroadcastCommentsCleaner`(`@EventListener`), 저장소 일괄 삭제 쿼리 | - |
| `Comments.test` 상세 화면 아래에 댓글을 오래된 순으로 닉네임·작성 시각·수정됨과 함께 보여 준다 | 댓글 영역 없음 | `CommentSection`·`commentApi.list`, 녹화방송 상세에 한 줄, `api.ts`의 `request`·`json` export | - |
| `Comments.test` 비로그인 로그인 안내·`next` 링크 / 작성(CSRF 헤더, 바로 목록에) / 400 안내 / 빈 댓글은 보내지 않음 | 작성란·안내 없음 | `CommentForm`, `commentRule.checkCommentContent`, `commentApi.write` | - |
| `Comments.test` 작성자에게만 수정·삭제 / 인라인 수정 / 취소 / 확인 후 삭제 / 403 안내 / 401이면 로그인 안내 | 수정·삭제 버튼 없음(비로그인 버튼 없음 테스트는 처음부터 green) | `CommentItem`, `commentApi.edit`·`delete`, 401·403 안내 | - |
| `Comments.test` 종료된 라이브 방송 시청 화면에도 댓글 / 3초마다 다시 불러와 다른 사람 댓글이 나타남 | 시청 화면에 댓글 영역 없음 | `pollMs` 폴링(처음 실패만 안내), 시청 화면에 한 줄 | 댓글 스타일 추가 |
| `Comments.test` 수정을 누르면 입력란에 포커스(기존 수정 테스트에 단언 추가) | 실제 브라우저에서 발견: 포커스가 작성란에 남음 | 수정 입력란 `autoFocus` | 전역 `li` 가로 배치를 `.comments li`에서 덮음 |
