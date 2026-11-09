# 05: 전체 Docker Compose 실행(채점용)

**What to build:** 채점자가 JDK와 Node 없이 `docker compose up --build` 하나로 DB, 백엔드, 프론트를 띄우고 서비스를 쓸 수 있다. `docker compose down` 후 다시 올려도 등록한 녹화방송이 남는다. 스펙: `.scratch/recordings/spec.md`.

02, 03, 04와 병행한다. 환경변수 이름(`DB_*`, `RECORDING_STORAGE_DIR`, `RECORDING_MAX_UPLOAD_SIZE`)은 스펙에서 확정되어 있으므로 03을 기다리지 않고 구성한다. 업로드 파일의 재시작 후 재생 확인은 03이 끝난 뒤 06에서 한다.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] 백엔드 이미지가 빌드된다(멀티 스테이지, 이미지 안에서 Gradle 빌드)
- [ ] 프론트 이미지가 정적 빌드를 서빙하고 `/api`를 백엔드로 프록시한다. 요청 본문 한도가 `RECORDING_MAX_UPLOAD_SIZE`와 같은 값이다
- [ ] DB 데이터와 업로드 폴더가 이름 있는 볼륨이다
- [ ] 백엔드는 DB가 준비된 뒤 시작한다(헬스체크 기반)
- [ ] 루트에 `.env.example`이 있고, `.env`가 없어도 기본값으로 뜬다
- [ ] 깨끗한 상태에서 `docker compose up --build` → 브라우저에서 외부 URL 녹화방송 등록 → `docker compose down` → `up` → 목록과 재생이 유지됨을 확인했다
- [ ] DB만 띄우는 개발용 실행 방식도 그대로 동작한다
