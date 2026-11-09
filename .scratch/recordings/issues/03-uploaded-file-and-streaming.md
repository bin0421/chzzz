# 03: 업로드 파일 등록과 Range 스트리밍

**What to build:** 등록자가 등록 화면에서 출처 종류를 "업로드 파일"로 바꾸고 mp4/webm 파일을 올리면 녹화방송이 등록되고, 상세 화면에서 재생되며 원하는 위치로 탐색할 수 있다. 스펙: `.scratch/recordings/spec.md`, 결정: ADR-0001.

02(등록 검증)와 병행한다. 이 티켓은 파일 관련 규칙(형식, 크기)과 업로드 흐름을 맡는다. 등록 폼에서는 출처 종류 전환, 파일 입력, 업로드 중 버튼 비활성화 부분만 건드린다.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] 업로드 파일 저장소가 인터페이스 뒤에 있고, 1단계 구현은 `RECORDING_STORAGE_DIR`(기본 `./data/uploads`, 없으면 시작 시 생성) 아래 로컬 디스크다
- [ ] `POST /api/recordings`에 `title`과 `file`(mp4/webm)을 보내면 `201`이고, 파일은 UUID + 확장자 이름으로 저장되며 DB에는 그 이름만 남는다
- [ ] mp4/webm이 아닌 파일은 `400`(`errors`에 `file`)
- [ ] `RECORDING_MAX_UPLOAD_SIZE`(기본 `500MB`)를 넘으면 `413` Problem Details
- [ ] 메타데이터 저장이 실패하면 방금 저장한 파일을 지운다
- [ ] 상세의 `videoUrl`은 `/api/recordings/{id}/video`다
- [ ] `/video`: 일반 요청 `200` 전체, `Range` 요청 `206` + 정확한 `Content-Range`와 바이트, 범위 밖 `416`, `Accept-Ranges: bytes`, `Content-Type` `video/mp4`/`video/webm`
- [ ] 녹화방송이 없거나 출처가 외부 URL이거나 파일이 디스크에 없으면 `/video`는 `404`
- [ ] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다(작은 테스트 파일 사용)
- [ ] 등록 화면에서 출처 종류를 전환하면 해당 입력란만 보이고, 업로드 중에는 등록 버튼이 비활성화되며, 성공하면 상세로 이동한다(앱 단위 테스트)
- [ ] 실제 브라우저에서 업로드 → 재생 → 탐색을 확인했다
- [ ] 모든 사이클이 `docs/tdd-log.md`에 기록되어 있다
