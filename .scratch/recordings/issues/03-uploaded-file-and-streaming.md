# 03: 업로드 파일 등록과 Range 스트리밍

**What to build:** 등록자가 등록 화면에서 출처 종류를 "업로드 파일"로 바꾸고 mp4/webm 파일을 올리면 녹화방송이 등록되고, 상세 화면에서 재생되며 원하는 위치로 탐색할 수 있다. 스펙: `.scratch/recordings/spec.md`, 결정: ADR-0001.

02(등록 검증)와 병행한다. 이 티켓은 파일 관련 규칙(형식, 크기)과 업로드 흐름을 맡는다. 등록 폼에서는 출처 종류 전환, 파일 입력, 업로드 중 버튼 비활성화 부분만 건드린다.

**Blocked by:** 01

**Status:** resolved

- [x] 업로드 파일 저장소가 인터페이스 뒤에 있고, 1단계 구현은 `RECORDING_STORAGE_DIR`(기본 `./data/uploads`, 없으면 시작 시 생성) 아래 로컬 디스크다
- [x] `POST /api/recordings`에 `title`과 `file`(mp4/webm)을 보내면 `201`이고, 파일은 UUID + 확장자 이름으로 저장되며 DB에는 그 이름만 남는다
- [x] mp4/webm이 아닌 파일은 `400`(`errors`에 `file`)
- [x] `RECORDING_MAX_UPLOAD_SIZE`(기본 `500MB`)를 넘으면 `413` Problem Details
- [x] 메타데이터 저장이 실패하면 방금 저장한 파일을 지운다
- [x] 상세의 `videoUrl`은 `/api/recordings/{id}/video`다
- [x] `/video`: 일반 요청 `200` 전체, `Range` 요청 `206` + 정확한 `Content-Range`와 바이트, 범위 밖 `416`, `Accept-Ranges: bytes`, `Content-Type` `video/mp4`/`video/webm`
- [x] 녹화방송이 없거나 출처가 외부 URL이거나 파일이 디스크에 없으면 `/video`는 `404`
- [x] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다(작은 테스트 파일 사용)
- [x] 등록 화면에서 출처 종류를 전환하면 해당 입력란만 보이고, 업로드 중에는 등록 버튼이 비활성화되며, 성공하면 상세로 이동한다(앱 단위 테스트)
- [x] 실제 브라우저에서 업로드 → 재생 → 탐색을 확인했다
- [x] 모든 사이클이 기록되어 있다(병행 작업 충돌을 피하려고 아래 `## TDD 기록`에 남겼다. 병합 때 `docs/tdd-log.md`로 옮긴다)

## TDD 기록

| 테스트 | red 이유 | green 변경 |
| --- | --- | --- |
| 백엔드 `업로드 파일로 등록하면 201이고 파일은 UUID 이름으로 저장되며 DB에는 그 이름만 남는다` | `400`: `url`이 필수 파라미터라 파일만 보낼 수 없음 | `UploadedFileStorage` 인터페이스와 로컬 디스크 구현(`recording.storage-dir` ← `RECORDING_STORAGE_DIR`, 시작 시 폴더 생성), `UPLOADED_FILE` 출처 종류, `file`을 선택 파라미터로 받아 UUID + 확장자 이름으로 저장 |
| 백엔드 `mp4나 webm이 아닌 파일은 400 Problem Details로 file 오류를 돌려주고 저장하지 않는다` | `201`: 확장자 검사 없음 | 확장자(mp4/webm) 검사, 위반 시 `FieldValidationException("file")` |
| 백엔드 `메타데이터 저장이 실패하면 방금 저장한 파일을 지운다` | 저장 폴더에 `.webm` 파일이 남음 | 메타데이터 저장 실패 시 저장한 파일 삭제 후 예외 재발생 |
| 백엔드 `최대 업로드 크기를 넘으면 413 Problem Details를 돌려준다` | `201`: 크기 한도 미설정 | `recording.max-upload-size` ← `RECORDING_MAX_UPLOAD_SIZE`(기본 500MB)를 Spring multipart 파일·요청 한도에 연결. 413 Problem Details는 Spring 기본 처리 |
| 백엔드 `업로드 파일 녹화방송의 상세 videoUrl은 video 엔드포인트다` | `videoUrl`이 저장 이름(`…webm`) 그대로 노출 | 출처 종류가 업로드 파일이면 `/api/recordings/{id}/video` |
| 백엔드 `video는 Range 없이 요청하면 200과 전체 파일을 돌려준다` | `404`: 엔드포인트 없음 | `GET /api/recordings/{id}/video`가 저장 파일을 `Resource`로, 확장자에 맞는 `Content-Type`으로 반환 |
| 백엔드 `video는 Range 요청에 206과 Content-Range, 요청한 바이트만 돌려준다` | 처음부터 green: Spring MVC가 `Resource` 본문의 Range를 처리. 테스트 민감도는 `ByteArray`를 반환하도록 잠시 바꿔 `200`으로 실패하는 것을 확인 | 변경 없음 |
| 백엔드 `video는 파일 크기를 벗어난 Range 요청에 416을 돌려준다` | 위와 같음(`ByteArray` 반환에서 `200`으로 실패 확인) | 변경 없음 |
| 백엔드 `없는 녹화방송의 video는 404다` | `NullPointerException` | 녹화방송이 없으면 404 |
| 백엔드 `외부 URL 녹화방송의 video는 404다` | `InvalidPathException`: URL을 파일 이름으로 해석 | 출처 종류가 업로드 파일이 아니면 404 |
| 백엔드 `업로드 파일이 디스크에 없으면 video는 404다` | `NullPointerException` | 저장소에 파일이 없으면 404 |
| 프론트 `출처 종류를 전환하면 고른 종류의 입력란만 보인다` | `업로드 파일` 라디오를 찾지 못함 | 출처 종류 라디오(기본 외부 URL)와 종류별 입력란 |
| 프론트 `파일을 골라 등록하면 상세 화면으로 이동해 업로드한 영상을 재생한다` | 요청 본문의 `file`이 `null` | 고른 파일을 상태로 두고 업로드 파일 종류면 `file`만, 외부 URL이면 `url`만 보냄 |
| 프론트 `업로드가 끝날 때까지 등록 버튼이 비활성화된다` | `toBeDisabled()` 실패 | 제출 중 상태로 버튼 비활성화(문구 `등록 중…`), 끝나면 해제 |
| 프론트 `서버가 파일을 거절하면 그 이유를 파일 입력란 옆에 보여 준다` (main 병합 후) | 파일 입력란의 접근 가능한 설명이 비어 있음: `file` 오류가 폼 위 알림에만 표시됨 | `INPUT_FIELDS`에 `file` 추가, 파일 입력란 옆 오류 문구와 `aria-describedby` |

## Comments

- 테스트별 업로드 폴더: Spring 컨텍스트는 캐시되므로 `RecordingUploadApiTest` 전용 임시 폴더를 쓰고 각 테스트 전에 비운다. 테스트마다 컨텍스트를 새로 띄우면 PostgreSQL 컨테이너까지 다시 뜨기 때문이다.
- 413 테스트는 MockMvc가 multipart 한도를 적용하지 않아서 실제 포트(`RANDOM_PORT`)로 요청한다. 이 테스트 클래스의 한도는 1KB다.
- 메타데이터 저장 실패는 HTTP 요청만으로 만들 수 없어서 리포지토리 저장만 `@MockitoSpyBean`으로 실패시켰다.
- main(02·04·05) 병합 후: 출처 개수 검증은 02의 `VideoSource.requireExactlyOne`이 맡고, 파일만 있으면 업로드 등록으로 이어진다. `/video`의 404는 04의 `NotFoundException`(Problem Details)으로 맞췄다.
- 실제 브라우저 확인(2026-11-09): ffmpeg로 만든 30초 mp4(2.7MB)를 등록 화면에서 업로드 → 상세로 이동 → 재생(1.46초 진행) → 20초로 탐색 후 계속 재생, 오류 없음. 실서버에서 `Range: bytes=1000-1999`는 `206`, `Content-Range: bytes 1000-1999/2872071`, `video/mp4`, 범위 밖은 `416`. 내장 브라우저에 파일 선택 도구가 없어서 파일은 `DataTransfer`로 파일 입력에 넣었다.
- 브라우저 확인 중 발견: 출처 종류를 바꿀 때 React가 같은 `<input>`을 재사용해 "controlled → uncontrolled" 경고가 났다. 입력란 `label`에 `key`를 줘서 해결했고, 화면 동작은 그대로라 테스트는 추가하지 않았다.
