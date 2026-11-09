# 이슈 트래커: 로컬 마크다운

이 저장소의 이슈와 스펙은 `.scratch/` 아래 마크다운 파일로 둔다.

최종 작업본은 GitHub 저장소에 제출한다. 이슈는 GitHub Issues로 옮기지 않고 `.scratch/`에 둔다. `.scratch/`를 제출물에 포함할지는 제출 시점에 사용자가 정한다.

## 규칙

- 기능 하나에 디렉터리 하나: `.scratch/<feature-slug>/`
- 스펙은 `.scratch/<feature-slug>/spec.md`
- 구현 이슈는 티켓 하나당 파일 하나: `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, `01`부터 번호를 매긴다. 여러 티켓을 한 파일에 합치지 않는다.
- 트리아지 상태는 각 이슈 파일 맨 위의 `Status:` 줄에 적는다 (역할 문자열은 `triage-labels.md` 참고).
- 코멘트와 대화 기록은 파일 맨 아래 `## Comments` 제목 아래에 덧붙인다.

## 스킬이 "이슈 트래커에 게시"하라고 할 때

`.scratch/<feature-slug>/` 아래에 새 파일을 만든다 (디렉터리가 없으면 만든다).

## 스킬이 "관련 티켓을 가져오라"고 할 때

참조된 경로의 파일을 읽는다. 보통 사용자가 경로나 이슈 번호를 직접 넘겨준다.

## Wayfinding 작업

`/wayfinder`가 사용한다. **map**은 파일 하나이고, 티켓마다 **child** 파일이 하나씩 있다.

- **Map**: `.scratch/<effort>/map.md` (Notes / Decisions-so-far / Fog 본문).
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`, `01`부터 번호를 매기고 본문에 질문을 적는다. `Type:` 줄에 티켓 유형(`research`/`prototype`/`grilling`/`task`)을, `Status:` 줄에 `claimed`/`resolved`를 적는다.
- **Blocking**: 파일 맨 위의 `Blocked by: NN, NN` 줄. 나열된 파일이 모두 `resolved`이면 티켓의 블록이 풀린다.
- **Frontier**: `.scratch/<effort>/issues/`에서 열려 있고, 블록이 풀렸고, claim되지 않은 파일을 찾는다. 번호가 가장 앞선 것이 우선한다.
- **Claim**: 작업 전에 `Status: claimed`로 바꾸고 저장한다.
- **Resolve**: `## Answer` 제목 아래에 답을 덧붙이고 `Status: resolved`로 바꾼 뒤, `map.md`의 Decisions-so-far에 컨텍스트 포인터(요지 + 링크)를 덧붙인다.
