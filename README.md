# chzzz

누구나 로그인 없이 **녹화방송**과 **라이브 방송**을 시청하고, **회원**은 녹화방송을 등록하거나 휴대폰 카메라·마이크로 라이브 방송을 송출하고 방송에 **댓글**을 다는 서비스입니다. 방송을 등록·시작한 회원이 그 방송의 **소유자**가 되어 수정·삭제·종료할 수 있고, 다른 회원은 방송 화면에서 소유자(**방송자**)에게 **테스트 결제로 후원**합니다(실제 돈은 나가지 않습니다). 용어는 [GLOSSARY.md](GLOSSARY.md)를 따릅니다.

- 백엔드: Spring Boot 4.1 + Kotlin, JDK 21, Gradle(Kotlin DSL), PostgreSQL 17 + Flyway
- 프론트엔드: React + Vite + TypeScript, React Router
- 라이브 송출: WebRTC WHIP(송출)·WHEP(시청) + [MediaMTX](https://github.com/bluenviron/mediamtx) ([ADR-0003](docs/adr/0003-webrtc-whip-whep-with-mediamtx.md))
- 휴대폰 HTTPS: Caddy 로컬 인증서 ([ADR-0004](docs/adr/0004-https-with-caddy-local-ca.md))
- 인증: Spring Security 서버 세션 쿠키 + CSRF 토큰 ([ADR-0005](docs/adr/0005-session-cookie-auth-with-spring-security.md)), 세션은 PostgreSQL에 저장 ([ADR-0009](docs/adr/0009-shared-session-in-postgresql.md)), 송출 키는 송출 허가 전용 ([ADR-0006](docs/adr/0006-stream-key-only-for-publish.md))
- 후원 결제: [토스페이먼츠](https://docs.tosspayments.com/reference) 결제위젯 **테스트 모드** + 서버 승인 확인 ([ADR-0007](docs/adr/0007-toss-payments-test-mode-with-server-confirmation.md)), 중복 확정 방지 ([ADR-0008](docs/adr/0008-idempotent-donation-confirmation.md))
- 라이브 상태 정리: MediaMTX 송출 목록과 주기 대조 ([ADR-0010](docs/adr/0010-reconcile-live-status-with-mediamtx.md))
- 실행: Docker Compose(db, backend, frontend, mediamtx, caddy), 그리고 5단계의 로컬 Kubernetes(Docker Desktop 내장 클러스터 + kustomize, 백엔드 2~5대 HPA 자동 스케일, [ADR-0011](docs/adr/0011-docker-desktop-kubernetes-with-kustomize.md))
- 앱(6단계): Windows **데스크톱 앱**(exe, Electron 45.2.3 + electron-builder 26.18.0)과 **Android 앱**(apk, Capacitor 8.6.0 + Kotlin). 앱은 저장한 **서버 주소**의 화면을 앱 창에서 열고([ADR-0012](docs/adr/0012-app-opens-server-screen.md)), 처음 연결할 때 서버 인증서 지문을 확인받아 고정합니다([ADR-0013](docs/adr/0013-pin-server-certificate-fingerprint.md)). 설치 파일은 [GitHub Release `stage-6`](https://github.com/bin0421/chzzz/releases/tag/stage-6)에 있습니다.

## 단계별 구현 내역

과제는 6단계로 진행했습니다.

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1 | 녹화방송 등록·목록·상세·재생 (TDD) | 완료 |
| 2 | 라이브 방송 송출·시청, 방송 중·종료 표시 (TDD) | 완료 |
| 3 | 회원가입·로그인, 방송 소유자 권한(수정·삭제·송출 제어), 방송별 댓글 (TDD) | 완료 |
| 4 | 방송자에게 테스트 결제 후원, 서버 승인 확인, 중복 확정 방지, 방송별 후원 내역·합계 (TDD) | 완료 |
| 5 | 로컬 Kubernetes에서 백엔드 여러 대 실행, CPU 기반 자동 스케일 아웃·인(2~5대), 요청 분산, 세션·영상·방송 상태를 공용 저장소에, k6 부하·상태 유지 확인 스크립트 (세션 공유·라이브 대조는 TDD) | 완료 |
| 6 | 설치형 앱: Windows 데스크톱 앱(exe)과 Android 앱(apk). 서버 주소 저장·연결 확인, 인증서 지문 신뢰, 주소 열기 규칙, 카메라·마이크 앱 권한, 로그인 유지, GitHub Release 배포 (판단 규칙은 TDD) | 완료 |

## 실행 방법

### 전체 실행 (채점용)

필요한 도구: Docker (Docker Compose v2 포함). JDK와 Node는 필요 없습니다.

```bash
docker compose up --build
```

다섯 서비스(db, mediamtx, backend, frontend, caddy)가 모두 healthy가 되면 접속합니다. 백그라운드로 띄우려면 `docker compose up -d --build --wait`를 씁니다.

- PC: **http://localhost:3000**
- 휴대폰(같은 Wi-Fi): **https://`PUBLIC_HOST`:8443** (아래 [휴대폰 연결](#휴대폰-연결))
- 멈추기: `docker compose down` (DB 데이터, 업로드 파일, HTTPS 인증서는 볼륨에 남습니다)
- 데이터까지 지우기: `docker compose down -v` (인증서도 새로 만들어지므로 휴대폰에서 경고를 다시 넘겨야 합니다)
- 3000 포트가 이미 쓰이고 있으면 `.env`에 `APP_PORT=다른포트`를 적습니다.
- 후원 결제는 토스 서버를 부르므로 **PC와 백엔드 컨테이너가 인터넷에 닿아야** 합니다(아래 [후원과 테스트 결제](#후원과-테스트-결제)).

### 개발 실행

필요한 도구: Docker, JDK 21, Node.js 22 이상. PC `localhost`에서만 확인합니다(휴대폰 확인은 전체 실행으로 합니다).

MediaMTX가 호스트에서 도는 백엔드를 부를 수 있게 주소를 함께 줍니다.

```bash
MEDIAMTX_BACKEND_URL=http://host.docker.internal:8080 docker compose up -d db mediamtx
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

브라우저에서 http://localhost:5173 에 접속합니다. Vite 개발 서버가 `/api`를 백엔드(기본 http://localhost:8080)로, `/media`를 MediaMTX(기본 http://localhost:8889)로 넘깁니다. PC 웹캠이 있으면 `/live/new`에서 송출할 수 있습니다(`localhost`는 HTTPS가 아니어도 카메라를 쓸 수 있습니다).

8080 포트를 다른 프로그램이 쓰고 있으면 백엔드 포트, 프록시 대상, MediaMTX 훅 주소를 함께 바꿉니다.

```bash
MEDIAMTX_BACKEND_URL=http://host.docker.internal:8081 docker compose up -d db mediamtx
```

```bash
cd backend && SERVER_PORT=8081 ./gradlew bootRun
```

```bash
cd frontend && VITE_API_PROXY_TARGET=http://localhost:8081 npm run dev
```

개발 실행은 `MEDIAMTX_API_URL`이 비어 있어 송출 대조를 하지 않습니다. 라이브 방송은 끊김 통지와 대기 시간 초과로만 정리됩니다([라이브 상태 정리](#라이브-상태-정리-adr-0010)).

### Kubernetes 실행 (5단계, 자동 스케일)

백엔드를 여러 대로 띄우고 CPU 사용률에 따라 2~5대로 자동으로 늘리고 줄이는 실행 경로입니다. PC 브라우저(`http://localhost:8000`)로만 확인합니다. 휴대폰 송출은 위 전체 실행(compose)으로 합니다. 구성과 상태 저장 위치는 [여러 인스턴스와 자동 스케일](#여러-인스턴스와-자동-스케일-5단계)에 있습니다.

필요한 도구: Docker Desktop(내장 Kubernetes), `kubectl`(Docker Desktop에 들어 있음). k6는 Docker 이미지로 실행하므로 설치하지 않습니다. 모든 명령은 저장소 루트에서 실행합니다.

1. **클러스터 켜기**: Docker Desktop 설정의 **Kubernetes**에서 클러스터를 켭니다(이 과제는 kind 방식 단일 노드로 확인했습니다). 켜지면 컨텍스트를 고르고 노드가 `Ready`인지 봅니다.
   ```bash
   kubectl config use-context docker-desktop
   ```
   ```bash
   kubectl get nodes
   ```
2. **이미지 빌드**: compose와 같은 Dockerfile로 `:k8s` 태그 이미지를 만듭니다. Docker Desktop 클러스터는 엔진의 이미지를 레지스트리 미러로 받아 오므로 따로 올리지 않습니다(그래서 매니페스트의 `imagePullPolicy`는 `Always`입니다. `Never`로는 이미지를 찾지 못했습니다).
   ```bash
   docker build -t chzzz-backend:k8s backend
   ```
   ```bash
   docker build -t chzzz-frontend:k8s frontend
   ```
3. **metrics-server 설치**(처음 한 번): HPA와 `kubectl top`이 CPU 사용률을 읽는 클러스터 공용 애드온입니다. 공식 릴리스 매니페스트 v0.9.1을 [k8s/metrics-server/](k8s/metrics-server/)에 그대로 두고 `--kubelet-insecure-tls`만 패치했습니다(이유는 그 폴더의 `kustomization.yaml` 주석). `kubectl top nodes`가 CPU·메모리 값을 보여 주면 됩니다.
   ```bash
   kubectl apply -k k8s/metrics-server
   ```
4. **서비스 올리기**: 네임스페이스 `chzzz`에 DB·업로드 볼륨과 db, mediamtx, backend(2대 + HPA), frontend(2대)를 올립니다. 파드 6개가 모두 `1/1 Running`이 되면 접속합니다(백엔드는 JVM 시작과 마이그레이션 때문에 1분 안팎 걸립니다).
   ```bash
   kubectl apply -k k8s
   ```
   ```bash
   kubectl -n chzzz get pods
   ```
5. **접속**: **http://localhost:8000** (compose의 3000과 겹치지 않습니다). 라이브 시청·송출의 WebRTC 미디어는 `localhost:8190`(UDP·TCP)으로 오갑니다.
6. **이미지를 다시 빌드했을 때**: 같은 태그로 빌드한 뒤 다시 띄웁니다.
   ```bash
   kubectl -n chzzz rollout restart deploy/backend deploy/frontend
   ```
7. **정리**
   - 앱만 지우기(DB·업로드 데이터와 네임스페이스는 남음): `kubectl delete -k k8s/app`
   - 데이터까지 지우기: `kubectl delete -k k8s`
   - metrics-server 지우기: `kubectl delete -k k8s/metrics-server` (`k8s`를 지워도 남는 별도 애드온입니다)

`kubectl apply -k k8s --dry-run=server`로 매니페스트를 미리 검사할 수 있습니다. 클러스터에 `chzzz` 네임스페이스가 아직 없으면 실패하므로, 처음에는 `kubectl apply -f k8s/storage/namespace.yaml`을 먼저 합니다.

## 휴대폰 연결

휴대폰 브라우저는 HTTPS 주소에서만 카메라·마이크를 허용합니다. 그래서 전체 실행에는 Caddy가 PC의 LAN IP로 HTTPS 인증서를 직접 만들어 8443 포트로 서비스합니다. 영상은 MediaMTX의 8189 포트(UDP, TCP)로 오갑니다.

1. **같은 Wi-Fi**: 휴대폰과 PC를 같은 공유기(같은 Wi-Fi)에 연결합니다. 모바일 데이터(LTE·5G)로는 접속할 수 없습니다.
2. **PC의 LAN IP 확인**: Windows는 `ipconfig`에서 Wi-Fi 또는 이더넷 어댑터의 `IPv4 주소`(예: `192.168.0.5`)를 찾습니다. VPN·WSL·Docker 가상 어댑터의 주소는 쓰지 않습니다. macOS는 `ipconfig getifaddr en0`입니다.
3. **`.env`에 `PUBLIC_HOST` 지정**: [.env.example](.env.example)을 `.env`로 복사하고 `PUBLIC_HOST`를 위 IP로 바꾼 뒤 전체 실행을 띄웁니다(이미 떠 있으면 다시 `docker compose up -d --build --wait`). 이 값이 HTTPS 인증서 주소와 영상이 오갈 주소(ICE 후보)에 쓰입니다.
   ```dotenv
   PUBLIC_HOST=192.168.0.5
   ```
4. **방화벽 허용 (Windows)**: 관리자 PowerShell에서 실행합니다. 같은 LAN(로컬 서브넷)의 기기만 들어오도록 제한합니다. 포트를 바꿨으면 숫자를 `.env` 값으로 고칩니다.
   ```powershell
   New-NetFirewallRule -DisplayName "chzzz HTTPS 8443/TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443 -RemoteAddress LocalSubnet -Profile Any
   New-NetFirewallRule -DisplayName "chzzz WebRTC 8189/UDP" -Direction Inbound -Action Allow -Protocol UDP -LocalPort 8189 -RemoteAddress LocalSubnet -Profile Any
   New-NetFirewallRule -DisplayName "chzzz WebRTC 8189/TCP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8189 -RemoteAddress LocalSubnet -Profile Any
   ```
   확인이 끝나면 지웁니다.
   ```powershell
   Remove-NetFirewallRule -DisplayName "chzzz *"
   ```
5. **접속과 인증서 경고 넘기기**: 휴대폰에서 `https://<PUBLIC_HOST>:8443`에 접속합니다. 공인 인증서가 아니라서 경고가 나옵니다.
   - Android Chrome: "연결이 비공개로 설정되어 있지 않습니다" → **고급** → **`<PUBLIC_HOST>`(안전하지 않음)(으)로 이동**
   - iOS Safari: "이 연결은 비공개 연결이 아닙니다" → **세부사항 보기** → **이 웹 사이트 방문** → 한 번 더 **웹 사이트 방문** (직접 확인하지는 않았습니다)

## 카메라·마이크 권한

- **허용**: 송출 화면(`/live/new`)을 열면 브라우저가 카메라와 마이크 권한을 묻습니다. 둘 다 **허용**하면 미리보기가 보입니다. 전면·후면 카메라는 방송을 시작하기 전에 고릅니다.
- **거부했을 때 다시 켜기**: 송출 화면에 이유와 해결 방법이 안내됩니다.
  - Android Chrome: 주소창 왼쪽 아이콘(사이트 설정) → **권한** → 카메라·마이크를 **허용**으로 바꾸고 새로고침합니다.
  - iOS Safari: 주소창의 **aA** → **웹 사이트 설정** → 카메라·마이크를 **허용**으로 바꾸고 새로고침합니다. 기기 전체에서 막았다면 **설정 → Safari → 카메라/마이크**도 확인합니다. (직접 확인하지는 않았습니다)
- HTTPS가 아닌 주소(예: `http://192.168.0.5:3000`)로 들어오면 브라우저가 카메라를 막습니다. 송출 화면이 `https://` 주소로 다시 접속하라고 안내합니다.
- 앱에서 거부했을 때는 [앱 권한 다시 허용](#앱-권한-다시-허용)을 봅니다.

## 앱 (6단계: 데스크톱 exe, Android apk)

**앱**은 기기에 설치해 브라우저 없이 쓰는 chzzz 클라이언트입니다. 앱 안에 화면을 따로 넣지 않고, 사용자가 처음 입력한 **서버 주소**의 chzzz 화면을 앱 창에서 엽니다([ADR-0012](docs/adr/0012-app-opens-server-screen.md)). 그래서 화면과 API가 같은 출처이고, 로그인 세션·CSRF·토스 결제 흐름이 웹과 똑같이 동작합니다. 서버가 없으면 앱만으로는 쓸 수 없습니다.

### 내려받기

[GitHub Release `stage-6`](https://github.com/bin0421/chzzz/releases/tag/stage-6)에서 내려받습니다. 버전은 둘 다 1.0.0입니다.

| 앱 | 파일 | 크기 | SHA-256 |
| --- | --- | --- | --- |
| 데스크톱 앱 (Windows 10 이상, 64비트) | [chzzz-Setup-1.0.0.exe](https://github.com/bin0421/chzzz/releases/download/stage-6/chzzz-Setup-1.0.0.exe) | 121,311,212바이트(약 116MB) | `d38b3193c236f3a6acb81a986864effc34beb32f6f1eddd99103f12cde2e759e` |
| Android 앱 (Android 7.0 이상) | [chzzz-1.0.0.apk](https://github.com/bin0421/chzzz/releases/download/stage-6/chzzz-1.0.0.apk) | 3,121,619바이트(약 3.0MB) | `a97163e11acf4f77a26ba1cb6e467879957749191f9d5031967b6c47e04fc4b3` |

내려받은 파일의 체크섬은 PowerShell에서 확인합니다(출력은 대문자이고 위 값과 같아야 합니다).

```powershell
Get-FileHash .\chzzz-Setup-1.0.0.exe -Algorithm SHA256
```

- 지원 범위: 데스크톱 앱은 Electron 45가 지원하는 Windows 10 이상(x64), Android 앱은 `minSdk 24`라 Android 7.0 이상입니다. 실제 확인은 Windows 11, Android 에뮬레이터(API 37), 실제 Android 휴대폰에서 했습니다.
- 코드 서명 인증서가 없어 exe는 서명하지 않았고, apk는 저장소 밖의 자체 키스토어로 서명했습니다(서명 인증서 `CN=chzzz, O=chzzz`, 인증서 SHA-256 `1de32827c33483c85ed48640a4fe68eab55e010127c1c0785db8b63c5c752dab`. `apksigner verify --print-certs`로 확인). 그래서 아래 설치 경고가 나옵니다.

### 설치

**데스크톱 앱 (Windows)**

1. `chzzz-Setup-1.0.0.exe`를 실행합니다.
2. 서명이 없어 SmartScreen이 "Windows의 PC 보호" 창을 띄우면 **추가 정보** → **실행**을 누릅니다.
3. 설치 마법사에서 설치 위치를 고르고 설치합니다. 사용자 단위 설치라 관리자 권한이 필요 없고, 기본 위치는 `%LOCALAPPDATA%\Programs\chzzz`입니다. 시작 메뉴와 바탕화면에 chzzz 바로 가기가 생깁니다.
4. 지우기: Windows 설정 → 앱 → 설치된 앱 → **chzzz** → 제거. 서버 주소·신뢰한 인증서·쿠키는 `%APPDATA%\chzzz`에 남습니다.

이 과제의 확인에서는 설치 마법사 대신 조용한 설치(`chzzz-Setup-1.0.0.exe /S`)로 설치했습니다.

**Android 앱**

1. 휴대폰 브라우저로 Release 페이지에서 `chzzz-1.0.0.apk`를 내려받습니다(또는 PC에서 받은 파일을 휴대폰 다운로드 폴더로 옮깁니다).
2. 다운로드 알림이나 파일 앱에서 apk를 엽니다.
3. "출처를 알 수 없는 앱" 안내가 나오면 **설정**으로 가서 그 앱(Chrome, 파일 앱 등)의 **이 출처 허용**을 켜고 돌아와 **설치**를 누릅니다. 메뉴 이름은 제조사·Android 버전마다 조금 다릅니다.
4. 설치가 끝나면 앱 목록의 **chzzz** 아이콘으로 엽니다.

### 서버 준비

앱은 [전체 실행](#전체-실행-채점용)(`docker compose up --build`) 서버에 연결합니다.

- **휴대폰(Android 앱) 또는 다른 PC**: [휴대폰 연결](#휴대폰-연결)의 1~4(같은 Wi-Fi, `.env`의 `PUBLIC_HOST`를 서버 PC의 LAN IP로, 방화벽 허용)를 한 뒤 서버 주소 **`https://<PUBLIC_HOST>:8443`**을 씁니다.
- **서버와 같은 PC의 데스크톱 앱**: `http://localhost:3000`도 됩니다(`http`는 `localhost`·`127.0.0.1`만 허용). 이 주소는 인증서가 없어 지문 확인을 하지 않습니다.
- 서버는 Caddy가 만든 로컬 CA로 **1년짜리 서버 인증서**를 직접 발급합니다(`caddy/Caddyfile`의 `issuer internal { lifetime 365d sign_with_root }`). 앱이 이 인증서의 지문을 고정하기 때문입니다([ADR-0013](docs/adr/0013-pin-server-certificate-fingerprint.md)).

### 첫 실행: 서버 주소와 인증서 지문

1. 앱을 처음 열면 **chzzz 서버 연결** 화면이 나옵니다. 서버 주소(예: `https://192.168.0.5:8443`)를 넣고 **연결 확인 후 저장**을 누릅니다.
   - 주소 형식이 틀리면 저장하기 전에 이유가 보입니다(스킴 없음, 지원하지 않는 스킴, `localhost`가 아닌 `http` 등). 경로·쿼리는 버리고 출처(`스킴://호스트:포트`)만 저장합니다.
   - 앱은 `/api/auth/me`가 `200` 또는 `401`로 응답하면 chzzz 서버로 봅니다. 닿지 않으면 **서버에 연결하지 못했습니다** 화면에서 **다시 시도**나 **서버 주소 변경**을 고릅니다.
2. HTTPS 서버면 **서버 인증서 확인** 화면에 서버 주소와 **SHA-256 지문**이 나옵니다. 서버 PC에서 확인한 지문(아래)과 같을 때만 **신뢰하고 연결**을 누릅니다. **연결하지 않음**을 누르면 아무것도 저장하지 않고 주소 입력 화면으로 돌아갑니다.
3. 신뢰하면 그 서버 주소와 지문 조합만 **신뢰한 인증서**로 저장하고 서버 화면을 엽니다. 다음 실행부터는 묻지 않고 바로 서버 화면이 열립니다. 저장한 서버 주소가 아닌 곳의 신뢰되지 않은 인증서는 묻지 않고 거절합니다.
4. 같은 주소에서 지문이 바뀌면 "인증서 지문이 바뀌었습니다" 경고와 함께 새 지문과 전에 신뢰한 지문을 보여 주고 다시 묻습니다. 서버 데이터를 지웠거나(`docker compose down -v`) 인증서를 새로 만든 게 아니라면 연결하지 않습니다.

**서버 PC에서 지문 보기**: 전체 실행이 떠 있는 서버 PC의 저장소 루트에서 실행합니다. Caddy가 보관한 서버 인증서(첫 번째 인증서)의 DER SHA-256을 앱과 같은 형식(대문자, `:` 구분)으로 찍습니다.

```powershell
docker compose exec caddy sh -c 'sed -n 1,/END/p /data/caddy/certificates/local/$PUBLIC_HOST/$PUBLIC_HOST.crt | grep -v CERTIFICATE | base64 -d | sha256sum | cut -c1-64 | tr a-f A-F | fold -w2 | paste -sd:'
```

```text
C4:16:15:54:28:74:6B:86:7E:4B:D5:CB:74:E3:69:31:0F:6E:19:B2:AB:23:01:2D:14:9F:57:55:77:F4:37:81
```

(위 출력은 이 과제의 확인 서버 예시입니다. 서버마다 다릅니다.) PC 브라우저로 `https://<PUBLIC_HOST>:8443`에 들어가 인증서 보기의 SHA-256 지문으로 비교해도 됩니다.

**기존 `caddy-data` 볼륨을 쓰는 서버**: 6단계 전에 전체 실행을 띄운 적이 있으면 볼륨에 이전 설정의 12시간짜리 인증서가 남아 있습니다. 이 인증서가 한 번 갱신될 때(최대 12시간 안) 1년 인증서로 바뀌므로, 그때 앱이 지문이 바뀌었다고 **한 번 더 묻습니다**. 위 명령으로 새 지문을 확인하고 신뢰하면 그 뒤로는 1년 동안 묻지 않습니다. 처음부터 1년 인증서로 시작하려면 데이터를 지우고(`docker compose down -v`, DB·업로드도 지워짐) 다시 띄웁니다.

### 앱에서 쓰기

- 방송 목록, 녹화·라이브 시청, 회원가입·로그인, 댓글, 후원은 웹과 같은 화면입니다.
- **로그인 유지**: 앱을 닫았다 열어도 로그인이 남습니다. 앱이 서버의 세션 쿠키를 30일 만료로 다시 저장하기 때문입니다. 서버 세션은 30분 동안 요청이 없으면 끝나므로(3단계 규칙 그대로) 그보다 오래 비우면 다시 로그인합니다.
- **송출**: Android 앱은 카메라·마이크 앱 권한을 처음 송출할 때 묻습니다. 데스크톱 앱은 PC 웹캠·마이크를 씁니다. 두 앱 모두 카메라·마이크를 저장한 서버 주소의 화면에만 줍니다.
- **후원**: 후원하기 → 결제하기를 누르면 토스 결제창이 앱 안에서 열리고, 결제 후(또는 취소 후) 같은 로그인 상태로 결과 화면에 돌아옵니다. PC는 결제창이 같은 화면의 iframe으로, 모바일은 결제창 주소로 화면이 이동해 열립니다.
- **주소 열기 규칙**: 앱 안에서는 서버 주소와 토스 결제 도메인(`https://`의 `tosspayments.com`·`toss.im`과 그 하위 도메인)만 엽니다. 다른 `http(s)` 링크는 기기의 기본 브라우저로, `mailto:`·`tel:`·`intent://`·결제 앱 스킴은 운영체제로 넘기고, `file:`·`javascript:`·`data:`·`blob:`·`content:` 같은 주소는 막습니다. 링크 이동과 새 창(`window.open`, `target=_blank`) 모두 같은 규칙입니다.
- **뒤로 가기(Android)**: 앱 안 이전 화면으로 가고, 첫 화면에서는 앱을 닫습니다.
- **연결 실패**: 서버가 꺼졌거나 닿지 않으면(15초 안에 서버 화면이 열리기 시작하지 않으면) 연결 실패 화면에서 **다시 시도** 또는 **서버 주소 변경**을 고릅니다.

### 서버 주소 바꾸기

- **데스크톱 앱**: 메뉴 **chzzz → 서버 주소 변경**(`Ctrl+,`). **다시 불러오기**(`Ctrl+R`)는 캐시를 무시하고 서버 화면을 다시 엽니다.
- **Android 앱**: 메뉴가 없으므로 서버에 닿지 않을 때 나오는 연결 실패 화면의 **서버 주소 변경**으로 바꿉니다. 휴대폰 설정에서 chzzz 앱의 저장공간(데이터)을 지우면 처음 실행 상태로 돌아갑니다(서버 주소·신뢰한 인증서·로그인 모두 지워짐, 직접 확인하지는 않았습니다).
- 새 주소는 처음 실행과 똑같이 다시 확인합니다. 신뢰한 인증서는 출처마다 따로 저장되므로, 이전 서버의 신뢰 기록은 새 주소에 적용되지 않습니다.

### 앱 권한 다시 허용

- **Android 앱**: 카메라·마이크 권한을 거부하면 송출 화면에 "권한이 거부되었습니다"가 보이고, 앱이 "카메라·마이크 권한이 꺼져 있습니다" 대화상자를 띄웁니다. **설정 열기**를 누르면 chzzz 앱 정보 화면이 열립니다. **권한**에서 카메라와 마이크를 허용한 뒤 송출 화면을 다시 엽니다. 직접 들어가려면 휴대폰 **설정 → 애플리케이션 → chzzz → 권한**입니다.
- **데스크톱 앱**: 앱이 서버 주소 화면의 권한 요청을 자동으로 허용하므로 따로 묻지 않습니다. 카메라를 못 쓰면 Windows **설정 → 개인 정보 및 보안 → 카메라(마이크)**에서 데스크톱 앱 접근이 켜져 있는지 봅니다(직접 확인하지는 않았습니다).

### 앱 빌드

빌드는 Windows PC에서 했습니다(GitHub Actions는 쓰지 않음). 결과물(`apps/desktop/release/`, `apps/android/android/app/build/`)과 키스토어는 커밋하지 않습니다(`.gitignore`).

필요한 도구:
- Node.js 22 이상(npm)
- 데스크톱: 추가 도구 없음(Electron과 electron-builder는 npm으로 받음)
- Android: Android Studio(Android SDK, compileSdk·targetSdk 36. 첫 빌드 때 Gradle이 platform 36을 받습니다), **JDK 21**(Temurin 21로 확인. JDK 25와 Android Studio 내장 JBR 25는 쓰지 않았습니다), 환경변수 `ANDROID_HOME`(보통 `%LOCALAPPDATA%\Android\Sdk`). 환경변수 대신 `apps/android/android/local.properties`에 `sdk.dir=C\:\\...\\Android\\Sdk`를 적어도 됩니다(이 파일은 커밋하지 않음). Gradle이 `SDK location not found`로 멈추면 이 경로가 실제 SDK 폴더(`platforms`, `build-tools`가 있는 곳)인지 확인합니다

고정한 버전: Electron `45.2.3`, electron-builder `26.18.0`, `@capacitor/core`·`@capacitor/cli`·`@capacitor/android` `8.6.0`, Kotlin Gradle 플러그인 2.3.21, Android Gradle 플러그인 8.13.0, Gradle 8.14.3. 앱 버전 1.0.0(Android `versionCode 1`, `applicationId dev.chzzz.app`, `minSdk 24`).

두 앱 모두 시작 화면([apps/shell/](apps/shell/), 서버 주소 입력·연결 확인·연결 실패·인증서 확인)을 함께 쓰고, 빌드 스크립트가 먼저 그것을 빌드합니다.

**데스크톱 exe** → `apps/desktop/release/chzzz-Setup-1.0.0.exe`

```powershell
cd apps/shell; npm ci; cd ../desktop; npm ci; npm run dist
```

개발 실행은 `npm start`입니다(설정은 `%APPDATA%\chzzz-dev`에 따로 저장되고 메뉴에 개발자 도구가 생깁니다).

**Android apk** → `apps/android/android/app/build/outputs/apk/release/app-release.apk`

1. 시작 화면을 빌드해 Android 프로젝트로 복사합니다.
   ```powershell
   cd apps/shell; npm ci; cd ../android; npm ci; npm run sync
   ```
2. 릴리스 서명 키스토어를 저장소 밖에 한 번 만듭니다(이 과제는 `%USERPROFILE%\.keystores\chzzz-release.jks`, 별칭 `chzzz`, PKCS12, RSA 2048, 10000일). 같은 설정으로 만드는 명령은 아래와 같고, 비밀번호는 `keytool`이 물을 때 입력합니다. `keytool`은 JDK 21의 `bin`에 있습니다.
   ```powershell
   keytool -genkeypair -keystore $env:USERPROFILE\.keystores\chzzz-release.jks -storetype PKCS12 -alias chzzz -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=chzzz, O=chzzz"
   ```
3. 서명 정보는 환경변수로만 받습니다. 비밀번호는 파일·저장소·명령 기록에 남기지 않도록 `Read-Host -AsSecureString`으로 그 터미널에만 넣습니다.

   | 변수 | 설명 |
   | --- | --- |
   | `CHZZZ_ANDROID_KEYSTORE` | 키스토어 파일 경로 |
   | `CHZZZ_ANDROID_KEYSTORE_PASSWORD` | 키스토어 비밀번호 |
   | `CHZZZ_ANDROID_KEY_ALIAS` | 키 별칭 |
   | `CHZZZ_ANDROID_KEY_PASSWORD` | 키 비밀번호(PKCS12는 키스토어 비밀번호와 같음) |

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'; $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"; $env:CHZZZ_ANDROID_KEYSTORE = "$env:USERPROFILE\.keystores\chzzz-release.jks"; $env:CHZZZ_ANDROID_KEY_ALIAS = 'chzzz'
   ```
   ```powershell
   $s = Read-Host -AsSecureString '키스토어 비밀번호'; $p = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s)); $env:CHZZZ_ANDROID_KEYSTORE_PASSWORD = $p; $env:CHZZZ_ANDROID_KEY_PASSWORD = $p; Remove-Variable s, p
   ```
4. 서명된 apk를 만들고 비밀번호 환경변수를 지웁니다. 서명 변수가 하나라도 없으면 Gradle은 서명하지 않은 릴리스 apk를 만듭니다.
   ```powershell
   cd android; .\gradlew.bat clean assembleRelease; Remove-Item Env:CHZZZ_ANDROID_KEYSTORE_PASSWORD, Env:CHZZZ_ANDROID_KEY_PASSWORD
   ```

이 PC는 PowerShell 실행 정책이 `.ps1` 스크립트를 막아서, 스크립트 파일 대신 위 명령을 터미널에서 직접 실행했습니다(정책은 바꾸지 않았습니다). Android 단위 테스트는 `.\gradlew.bat testReleaseUnitTest`입니다.

## 회원·로그인과 공개 범위

### 누가 무엇을 할 수 있나

시청과 목록 조회는 지금처럼 **로그인 없이 누구나** 합니다. 방송을 만들거나 바꾸는 일과 댓글 작성은 로그인한 회원만, 방송 관리는 그 방송의 소유자만, 댓글 수정·삭제는 그 댓글의 작성자만 합니다. 화면은 권한 없는 버튼을 숨기지만, 실제 거절은 **서버가 매 요청마다** 합니다.

| 기능 | 비로그인 | 로그인 회원 | 소유자 | 작성자 |
| --- | --- | --- | --- | --- |
| 방송 목록, 녹화방송 상세·재생, 라이브 방송 시청 | ✅ | ✅ | ✅ | ✅ |
| 방송별 댓글 목록 | ✅ | ✅ | ✅ | ✅ |
| 녹화방송 등록, 라이브 방송 시작 | ❌ 401 | ✅ (소유자가 됨) | | |
| 댓글 작성(녹화방송·라이브 방송, 종료된 방송 포함) | ❌ 401 | ✅ | ✅ | |
| 방송 제목 수정·삭제, 라이브 방송 종료 | ❌ 401 | ❌ 403 | ✅ | |
| 댓글 수정·삭제 | ❌ 401 | ❌ 403 (방송 소유자도) | ❌ 403 | ✅ |
| 방송별 후원 내역·합계 | ✅ | ✅ | ✅ | ✅ |
| 후원(남의 방송, 소유자가 있는 방송) | ❌ 401 | ✅ | ❌ 409 (자기 방송) | |
| 후원 확인 요청·실패 보고 | ❌ 401 | 자기 후원만 (남의 후원 ❌ 403) | | |

- 서버의 거절 순서는 **401**(로그인 필요) → **404**(대상 없음) → **403**(소유자·작성자 아님) → **409**(상태 충돌: 대기·방송 중인 라이브 방송 삭제, 이미 종료된 방송 다시 종료). 모두 Problem Details입니다.
- 회원 기능 이전(1·2단계)에 만든 방송은 **소유자가 없어** 누구도 수정·삭제·종료할 수 없고, 시청과 댓글은 됩니다.

### 회원 규칙

- 아이디: 영문 소문자·숫자·`_` 4~20자, 중복 불가. 로그인에만 쓰고 화면·응답에는 나오지 않습니다.
- 비밀번호: 8~64자. BCrypt 해시로만 저장합니다.
- 닉네임: 앞뒤 공백을 지운 1~20자. 화면에서 소유자·작성자를 나타내며, 대소문자를 무시하고 비교해 중복을 막습니다.
- 가입하면 바로 로그인됩니다. 로그인 실패는 어느 쪽이 틀렸는지 알려 주지 않고 "아이디 또는 비밀번호가 올바르지 않습니다."로 답합니다.

### 로그인 방식 (ADR-0005)

- 로그인하면 서버가 세션을 만들고 브라우저는 `HttpOnly` 세션 쿠키(`JSESSIONID`)만 가집니다. 로그인할 때 세션 ID를 새로 바꿉니다.
- 쿠키는 브라우저가 자동으로 붙이므로, 다른 사이트의 요청 위조(CSRF)를 막으려고 서버가 `XSRF-TOKEN` 쿠키를 내려 주고 상태를 바꾸는 모든 요청에 같은 값을 `X-XSRF-TOKEN` 헤더로 받습니다. 헤더가 없거나 틀리면 403입니다.
- 세션은 백엔드 메모리가 아니라 **PostgreSQL**(Spring Session JDBC, Flyway `V5`)에 있습니다([ADR-0009](docs/adr/0009-shared-session-in-postgresql.md)). 그래서 **백엔드를 재시작해도 로그인이 유지**되고, 백엔드가 여러 대여도 어느 인스턴스가 응답하든 같은 로그인 상태이며, 로그아웃하면 모든 인스턴스에서 로그아웃됩니다(3·4단계까지는 메모리에 있어 재시작하면 모두 로그아웃되었습니다). 휴대폰(`https://<PUBLIC_HOST>:8443`)과 PC(`http://localhost:3000`)는 주소가 달라 로그인도 각각 따로 합니다.
- 로그인이 필요한 화면(녹화방송 등록, 방송 시작)에 비로그인으로 들어가면 로그인 화면으로 갔다가, 로그인·가입하면 원래 화면으로 돌아옵니다.

### 송출 키 (ADR-0006)

1. 로그인한 회원이 방송 시작을 누르면 서버가 라이브 방송을 **대기**로 만들고 그 회원을 소유자로 기록한 뒤, 그 방송 전용 **송출 키**를 **한 번만** 돌려줍니다. 서버에는 키의 SHA-256 해시만 저장합니다.
2. 송출 화면은 그 키로 MediaMTX에 WHIP 송출을 시작합니다. MediaMTX는 송출·시청 요청마다 백엔드 내부 엔드포인트에 허가를 묻고, 백엔드는 대기인 방송에 맞는 키일 때만 송출을, 방송 중일 때만 시청을 허가합니다. MediaMTX는 브라우저의 세션 쿠키를 넘겨주지 않아서 송출 허가만은 키로 판단합니다.
3. **종료·제목 수정·삭제는 송출 키가 아니라 로그인한 소유자인지로** 검사합니다. 송출하던 탭을 닫았거나 다른 기기에서도 소유자는 로그인만 하면 방송을 끝낼 수 있고, 송출 키가 새어도 그것으로 방송을 끄거나 바꿀 수는 없습니다.
4. 영상이 들어오면 **방송 중**, 소유자가 종료하거나 연결이 끊기면 **종료**가 됩니다. 영상이 60초(`LIVE_PENDING_TIMEOUT`) 안에 들어오지 않은 대기도 종료로 정리되고, 방송 중인데 MediaMTX에 송출이 없는 상태가 30초(`LIVE_PUBLISH_GRACE`)를 넘기면 송출 대조가 종료로 정리합니다([라이브 상태 정리](#라이브-상태-정리-adr-0010)).
5. 목록·상세 응답에는 키가 나오지 않습니다. 백엔드의 `/internal/*` 엔드포인트는 Caddy·nginx가 바깥에서 404로 막습니다.

## 후원과 테스트 결제

결제는 [토스페이먼츠 결제위젯](https://docs.tosspayments.com/guides/v2/get-started/environment)의 **테스트 모드**로만 합니다. 테스트 키로 만든 결제는 실제로 출금되지 않습니다(위젯에도 "테스트 환경이에요. 실제로 결제되지 않아요."가 보입니다). 백엔드가 토스 승인·조회 API(`https://api.tosspayments.com`)를 부르므로 **PC와 백엔드 컨테이너가 인터넷에 닿아야** 합니다. 확인 때 백엔드 컨테이너에서 토스가 실제로 응답하는 것을 위조 확인 요청의 거절 코드(`NOT_FOUND_PAYMENT_SESSION`)로 확인했습니다.

### 후원 흐름 (ADR-0007)

1. **금액 고르기**: 방송 화면(녹화방송 상세, 라이브 시청 화면, 종료된 라이브 포함) 아래 **후원** 영역에서 빠른 선택(1,000·5,000·10,000·50,000원)이나 직접 입력으로 금액을 고릅니다. 1,000원 이상 1,000,000원 이하의 정수만 됩니다. 비로그인이면 "로그인하면 후원할 수 있습니다"와 로그인 링크가, 자기 방송과 소유자 없는 방송에는 후원 버튼이 없습니다(내역·합계는 보입니다).
2. **주문 만들기**: "○○님에게 5,000원 후원하기"를 누르면 서버가 **결제 대기** 후원을 만듭니다. 이때 금액과 방송자(그 순간의 방송 소유자)를 서버가 정하고 무작위 주문번호(UUID)를 돌려줍니다.
3. **결제위젯**: 화면이 서버가 정한 금액으로 결제위젯(결제수단, 약관 동의)을 그립니다. **결제하기**를 누르면 토스의 테스트 결제 화면으로 넘어갑니다.
4. **결제 후 돌아오기**: 성공하면 토스가 `/donations/success?paymentKey=…&orderId=…&amount=…`로, 실패·취소하면 `/donations/fail?code=…&message=…&orderId=…`로 돌려보냅니다.
5. **서버 승인 확인**: 성공 화면은 주소의 값을 서버에 보낼 뿐이고, **확정은 서버가 정합니다**. 화면의 성공 표시나 주소만으로는 적립되지 않습니다.

### 서버가 확정하는 규칙

확인 요청(`POST /api/donations/confirm`)이 오면 서버는 주문 행을 잠그고 아래 순서로 봅니다. 모든 확인 요청과 웹훅은 결과와 함께 **승인 통지 기록**(`payment_notification`)에 한 줄씩 남습니다.

| 경우 | 결과 | 응답 | 토스 승인 호출 |
| --- | --- | --- | --- |
| 없는 주문 / 남의 주문 | 그대로 | `404` / `403` | 안 함 |
| 이미 확정, 같은 결제 키 | 확정 그대로, 저장된 결과를 다시 돌려줌 (`ALREADY_CONFIRMED`) | `200` | **안 함** |
| 이미 실패·취소·확인 필요 | 그대로 | `409` | 안 함 |
| 요청 금액 ≠ 후원 금액 (금액 위조) | 실패 | `400` (`errors`의 `amount`) | 안 함 |
| 방송 삭제, 방송자 바뀜 | 실패 | `409` | 안 함 |
| 다른 후원에 이미 쓰인 결제 키 | 결제 대기 그대로 | `409` | 안 함 |
| 토스가 거절, 결제 상태가 완료 아님 | 실패(토스 코드·메시지 저장) | `200` + `status: FAILED`, `failure` | 함 |
| 토스가 "이미 처리된 결제"(`ALREADY_PROCESSED_PAYMENT`) | 조회 API로 실제 상태를 다시 확인해 같은 규칙 | | 함 + 조회 |
| 완료인데 주문번호·금액이 다름 | **확인 필요**(자동 확정 안 함) | `200` + `status: NEEDS_REVIEW` | 함 |
| 완료, 주문번호·금액 일치 | **확정** (`NEW_CONFIRMED`) | `200` + `status: CONFIRMED` | 함 |
| 토스와 통신 실패 | 결제 대기 그대로(다시 확인하면 됨) | `502` | 시도 |

실패 화면은 `POST /api/donations/fail`로 서버에 알립니다. 코드가 `PAY_PROCESS_CANCELED`(결제 화면 닫기)면 **취소**, 그 밖은 **실패**이고, 이미 확정된 후원은 바꾸지 않습니다(`409`).

### 중복 적립 방지 (ADR-0008)

같은 결제의 승인 통지가 여러 번, 여러 경로로 와도 한 번만 확정됩니다.

- **코드**: 이미 확정된 주문은 토스를 다시 부르지 않고 같은 결과를 돌려줍니다(성공 화면 새로고침, 같은 확인 요청 반복). 확정은 결제 대기에서만 일어나고, 확정된 후원은 실패·취소로 덮어쓰지 않습니다.
- **동시 요청**: 확인 요청과 웹훅은 주문 행을 `SELECT … FOR UPDATE`로 잠그고 처리하므로, 동시에 온 두 요청 중 뒤의 것은 앞의 확정을 보고 `ALREADY_CONFIRMED`로 끝납니다.
- **저장 구조**: `donation.order_id`와 `donation.payment_key`에 유니크 제약이 있어, 코드를 비껴가도 한 결제가 두 후원에 붙거나 한 주문이 두 번 만들어지지 않습니다. 합계는 확정된 후원 행만 더합니다.
- **웹훅** (`POST /api/payments/toss/webhook`, 로그인·CSRF 없음): 본문은 믿지 않고 결제 키로 토스 **조회 API**를 불러 실제 상태를 확인한 뒤 같은 규칙으로 처리합니다. 없는 결제 키, 완료가 아닌 결제, 다른 이벤트 같은 위조·무관한 통지는 **무시**(`IGNORED`)로 기록하고 `200`을 돌려줍니다. 토스 조회가 통신 실패한 경우만 `502`로 답해 토스가 다시 보내게 합니다.
  - 토스가 이 PC로 웹훅을 보내려면 공개 주소(터널)와 토스 개발자센터 등록이 필요해서 이 과제에서는 하지 않았습니다. 웹훅 처리는 아래 직접 확인 절차의 `curl`과 자동 테스트로 확인합니다.

### 테스트 키, 테스트 결제, 실패 재현

- **키**: 토스 [API 키 문서](https://docs.tosspayments.com/reference/using-api/api-keys)에 공개된 **문서용 결제위젯 테스트 키**(`test_gck_docs_…`, `test_gsk_docs_…`)를 기본값으로 넣었습니다. 가입 없이 바로 됩니다. 시크릿 키는 백엔드만 가지고 화면에는 클라이언트 키만 나갑니다. 자기 토스 개발자센터의 결제위젯 테스트 키로 바꾸려면 `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`를 고칩니다.
  - 공식 샘플의 공개 결제창 키(`test_ck_…`)는 토스 샌드박스에서 `401`로 거절되어 결제창 대신 결제위젯을 씁니다(ADR-0007).
- **테스트 결제**: 결제수단은 **신용·체크카드**로 확인합니다. 토스 [테스트 환경 안내](https://docs.tosspayments.com/guides/v2/get-started/environment)에 따르면 테스트 키로 한 카드 결제는 실제 출금 없이 승인 흐름만 거칩니다. **이 과제의 확인에서는 카드 정보를 넣는 결제를 끝까지 진행하지 않았습니다**(아래 [직접 확인 절차](#후원-4단계-테스트-결제)의 범위 참고). 위젯에 보이는 간편결제·계좌이체 등도 테스트로 열리지만 확인 대상은 아닙니다.
- **실패 재현**: 토스는 승인 API에 `TossPayments-Test-Code` 헤더를 붙여 카드사 거절 같은 실패를 흉내 내게 해 줍니다([오류 코드](https://docs.tosspayments.com/reference/error-codes)). 이 프로젝트는 운영 코드에 테스트 전용 설정을 넣지 않고, 대신 **결제하지 않은 가짜 결제 키로 확인 요청**을 보내 "토스 거절 → 실패" 경로를 카드 없이 재현합니다(아래 직접 확인 22).

## 여러 인스턴스와 자동 스케일 (5단계)

백엔드를 여러 대로 띄우고, 부하에 따라 Kubernetes HPA가 **2~5대** 사이에서 자동으로 늘리고 줄입니다. 백엔드는 자기 메모리나 디스크에 상태를 두지 않으므로 어느 인스턴스가 응답해도 결과가 같고, 인스턴스가 늘거나 줄어도 로그인·방송·댓글·후원이 그대로입니다. 실행 방법은 [Kubernetes 실행](#kubernetes-실행-5단계-자동-스케일), 확인 절차와 실제 출력은 [스케일 아웃/인과 상태 유지](#스케일-아웃인과-상태-유지-5단계-kubernetes)에 있습니다.

### 구성 (Kubernetes 경로, 네임스페이스 `chzzz`)

| 구성 요소 | 대수 | 바깥에서 닿는 주소 | 설명 |
| --- | --- | --- | --- |
| `frontend` (nginx) | 2 고정 | `localhost:8000` (LoadBalancer) | 진입점. 정적 파일, `/api`는 `backend` Service로, `/media`는 `mediamtx`로 넘김 |
| `backend` (Spring Boot) | **2~5 (HPA)** | 없음 (ClusterIP `backend:8080`) | 처음 2대로 뜨고 HPA가 대수를 정함 |
| `db` (PostgreSQL 17) | 1 | 없음 | PVC `db-data` |
| `mediamtx` | 1 | WebRTC 미디어 `localhost:8190` UDP·TCP (LoadBalancer) | WHIP/WHEP HTTP(8889)와 제어 API(9997)는 클러스터 안에서만 닿음 |

Caddy(휴대폰 HTTPS)는 Kubernetes 경로에 올리지 않습니다. 매니페스트는 [k8s/](k8s/)에 일반 YAML + kustomize로 있습니다: `k8s/storage`(네임스페이스·PVC), `k8s/app`(나머지와 HPA), `k8s/metrics-server`(공용 애드온).

### 상태를 어디에 두나

| 상태 | 저장 위치 | 설명 |
| --- | --- | --- |
| 로그인 세션 | **PostgreSQL** (`SPRING_SESSION` 테이블, Flyway `V5`) | Spring Session JDBC([ADR-0009](docs/adr/0009-shared-session-in-postgresql.md)). 쿠키 이름(`JSESSIONID`)·속성, 로그인 시 세션 ID 교체, 로그아웃 무효화는 3단계와 같음. 만료 세션은 Spring Session 기본 정리 작업이 지움 |
| CSRF 토큰 | 브라우저 쿠키 `XSRF-TOKEN` | 서버에 저장하지 않아 인스턴스끼리 공유할 것이 없음 |
| 업로드 영상 | **공유 볼륨** (PVC `uploads`) | 모든 백엔드 파드가 같은 볼륨을 `RECORDING_STORAGE_DIR`(`/app/data/uploads`)에 마운트. 코드는 1단계 그대로([ADR-0001](docs/adr/0001-local-disk-upload-storage.md)) |
| 녹화방송, 라이브 방송과 상태(대기·방송 중·종료), 회원, 댓글, 후원·승인 통지 기록 | **PostgreSQL** (PVC `db-data`) | 1~4단계 그대로 |
| 송출 중인 영상 경로 | MediaMTX 1대 | 영상은 저장하지 않음. 방송 상태 판단은 DB가 기준이고 MediaMTX는 대조에만 씀 |

- 업로드 볼륨은 `ReadWriteOnce`(local-path)입니다. 이 클러스터는 노드가 1대라 여러 파드가 함께 마운트할 수 있습니다. **노드가 여러 대면** `ReadWriteMany` 스토리지(NFS 등)나 오브젝트 스토리지가 필요합니다(이 과제 범위 밖).
- 백엔드 주기 작업(대기 시간 초과 정리, 송출 대조)은 인스턴스마다 10초마다 돕니다. 같은 행을 같은 값으로 바꾸는 작업이라, 두 인스턴스에서 8번 동시에 돌려도 예외 없이 같은 결과인 것을 테스트로 확인했습니다.
- 후원 확정의 행 잠금(`SELECT … FOR UPDATE`)과 유니크 제약은 DB에서 걸리므로 인스턴스가 여러 대여도 그대로 한 번만 확정됩니다([ADR-0008](docs/adr/0008-idempotent-donation-confirmation.md)).

### 요청 분산과 `X-Backend-Instance` 헤더

```text
브라우저 ─▶ localhost:8000 (frontend LoadBalancer) ─▶ nginx 2대 ─/api─▶ backend Service (ClusterIP) ─▶ 백엔드 파드 2~5대
                                                              └/media─▶ mediamtx Service ─(인증·상태 훅)─▶ backend Service
```

- `/api` 요청은 nginx가 `backend` Service로 넘기고, kube-proxy가 **연결마다** 백엔드 파드를 고릅니다. nginx에 upstream keepalive를 두지 않아 요청마다 새 연결이 열리므로 한 파드로 쏠리지 않습니다. 백엔드 2대로 확인했을 때 요청마다 연결을 따로 열면 **20:20**, 클라이언트 연결 하나를 재사용해도 **17:23**으로 두 파드에 나뉘었습니다.
- 백엔드는 모든 응답(보안 필터의 401·403 포함)에 **`X-Backend-Instance`** 헤더로 응답한 인스턴스 이름을 싣습니다. 값은 `INSTANCE_NAME`이고, 비어 있으면(기본) 호스트 이름입니다. Kubernetes에서는 파드 이름(예: `backend-84fbdc547f-52694`), compose에서는 컨테이너 ID입니다. 상태 유지 확인 스크립트가 이 헤더로 응답한 파드를 찍습니다.
  ```bash
  curl -s -o /dev/null -D - http://localhost:8000/api/recordings | grep -i x-backend-instance
  ```
- MediaMTX의 송출·시청 허가와 상태 훅도 `backend` Service로 갑니다. 어느 파드가 받아도 DB 상태만 바꾸므로 결과가 같습니다.

### 자동 스케일 (HPA)

[k8s/app/backend-hpa.yaml](k8s/app/backend-hpa.yaml), `autoscaling/v2`.

| 설정 | 값 | 설명 |
| --- | --- | --- |
| 지표 | CPU 평균 사용률 **50%** | 사용률 = 사용량 / `requests.cpu`(250m). 즉 파드 평균 약 125m를 넘으면 늘림. 필요 대수 = ceil(지금 대수 × 평균 사용률 / 50%) |
| 범위 | `minReplicas: 2`, `maxReplicas: 5` | 한 대가 죽어도 서비스가 이어지게 2대 아래로는 줄이지 않음. 노드(8GB)에 메모리 요청 512Mi × 5대가 들어감 |
| 줄일 때 | `scaleDown.stabilizationWindowSeconds: 60` | 최근 60초의 권장 대수 중 가장 큰 값을 씀(기본 300초보다 짧게) |
| 늘릴 때 | 기본값 | 안정화 0초, 15초마다 +100% 또는 +4대 중 큰 쪽 |
| 백엔드 자원 | requests CPU 250m·메모리 512Mi, limits CPU 1·메모리 1Gi | 힙은 `-XX:MaxRAMPercentage=60` |

- 준비되지 않은 파드(JVM 시작·마이그레이션 중)는 readiness 프로브(`/actuator/health/readiness`, DB 포함)가 통과할 때까지 요청을 받지 않고, HPA도 그 파드의 CPU를 계산에서 뺍니다. 시작 시 CPU가 튀어도 대수가 더 늘지 않았습니다.
- 줄어드는 파드는 `preStop` 10초 동안 Service에서 빠지는 것을 기다린 뒤, graceful shutdown으로 처리 중인 요청을 최대 20초 끝내고 내려갑니다.

### 라이브 상태 정리 (ADR-0010)

4단계까지는 백엔드가 시작할 때 남은 대기·방송 중을 모두 종료로 정리했습니다. 백엔드가 여러 대가 되자 HPA가 파드를 하나 늘릴 때마다 다른 인스턴스에서 진행 중인 방송이 끊기는 문제가 생겨, 시작 시 정리를 없애고 **MediaMTX와 주기 대조**로 바꿨습니다([ADR-0010](docs/adr/0010-reconcile-live-status-with-mediamtx.md)).

- 각 백엔드가 10초마다 MediaMTX 제어 API(`/v3/paths/list`)에서 실제로 송출 중인 경로를 가져와 DB의 **방송 중**과 비교합니다. 방송 중이 된 지 `LIVE_PUBLISH_GRACE`(30초)가 지났는데 송출이 없으면 종료로 정리합니다. 방송 중이 된 직후 MediaMTX 목록 반영이 늦어도 바로 끊지 않기 위한 유예입니다.
- MediaMTX에 닿지 않으면(연결 실패·오류 응답) 아무것도 종료하지 않고 경고 로그만 남깁니다.
- **대기**는 대조하지 않고 지금처럼 대기 시간 초과(`LIVE_PENDING_TIMEOUT`, 60초)가 정리합니다.
- 제어 API(9997)는 인증 없이 열려 있어(`authHTTPExclude`에 `action: api`) compose 내부 네트워크·클러스터 안에서만 닿게 두고 호스트에 공개하지 않습니다.
- 개발 실행(`./gradlew bootRun`)은 `MEDIAMTX_API_URL`이 비어 있어 대조하지 않습니다.

## 환경변수

### Docker Compose (`.env`)

[.env.example](.env.example)을 `.env`로 복사해서 고칩니다. `.env`가 없으면 아래 기본값으로 뜹니다.

```dotenv
APP_PORT=3000
DB_NAME=chzzz
DB_USERNAME=chzzz
DB_PASSWORD=chzzz
RECORDING_MAX_UPLOAD_SIZE=500MB
PUBLIC_HOST=localhost
HTTPS_PORT=8443
WEBRTC_PORT=8189
LIVE_PENDING_TIMEOUT=60s
LIVE_PUBLISH_GRACE=30s
TOSS_CLIENT_KEY=test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm
TOSS_SECRET_KEY=test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6
TOSS_API_BASE_URL=https://api.tosspayments.com
```

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `APP_PORT` | `3000` | PC 브라우저로 접속할 HTTP 포트 |
| `DB_NAME` | `chzzz` | PostgreSQL 데이터베이스 이름 |
| `DB_USERNAME` | `chzzz` | DB 계정 |
| `DB_PASSWORD` | `chzzz` | DB 비밀번호 |
| `RECORDING_MAX_UPLOAD_SIZE` | `500MB` | 최대 업로드 크기. 백엔드 multipart 한도와 프론트 nginx 요청 본문 한도에 같은 값이 적용됩니다 |
| `PUBLIC_HOST` | `localhost` | 휴대폰이 접속할 PC의 LAN IP. HTTPS 인증서와 MediaMTX ICE 후보에 쓰입니다. 휴대폰으로 확인하려면 반드시 바꿉니다 |
| `HTTPS_PORT` | `8443` | 휴대폰 등 다른 기기가 접속할 HTTPS 포트(Caddy) |
| `WEBRTC_PORT` | `8189` | MediaMTX WebRTC 미디어 포트(UDP·TCP 모두) |
| `LIVE_PENDING_TIMEOUT` | `60s` | 영상이 들어오지 않은 대기를 종료로 정리할 때까지의 시간 |
| `LIVE_PUBLISH_GRACE` | `30s` | 방송 중인데 MediaMTX에 송출이 없는 상태를 이 시간까지는 두고, 넘기면 송출 대조가 종료로 정리(5단계) |
| `TOSS_CLIENT_KEY` | 문서용 결제위젯 테스트 클라이언트 키 | 화면의 결제위젯이 쓰는 키(`GET /api/payments/config`로 내려감). 테스트 키(`test_`)만 씁니다 |
| `TOSS_SECRET_KEY` | 문서용 결제위젯 테스트 시크릿 키 | 백엔드가 토스 승인·조회 API를 부를 때 쓰는 키. 화면에 나가지 않습니다 |
| `TOSS_API_BASE_URL` | `https://api.tosspayments.com` | 토스 API 주소 |

### 백엔드 (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/chzzz` | DB 접속 주소 |
| `DB_USERNAME` / `DB_PASSWORD` | `chzzz` / `chzzz` | DB 계정 |
| `SERVER_PORT` | `8080` | 백엔드 포트 |
| `RECORDING_STORAGE_DIR` | `./data/uploads` | 업로드 파일 보관 폴더(없으면 시작할 때 만듭니다) |
| `RECORDING_MAX_UPLOAD_SIZE` | `500MB` | 최대 업로드 크기 |
| `LIVE_PENDING_TIMEOUT` | `60s` | 대기 시간 초과 |
| `LIVE_PUBLISH_GRACE` | `30s` | 송출 대조의 유예 시간 |
| `MEDIAMTX_API_URL` | 빈 값 | MediaMTX 제어 API 주소. 비어 있으면 송출 대조를 하지 않음. compose 전체 실행과 Kubernetes는 `http://mediamtx:9997` |
| `INSTANCE_NAME` | 빈 값 | 응답 헤더 `X-Backend-Instance`에 실을 인스턴스 이름. 비어 있으면 호스트 이름(compose는 컨테이너 ID, Kubernetes는 파드 이름) |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` / `TOSS_API_BASE_URL` | 위와 같음 | 토스 결제위젯 테스트 키와 API 주소 |

### 프론트엔드·MediaMTX (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | Vite가 `/api`를 넘길 백엔드 주소 |
| `VITE_MEDIA_PROXY_TARGET` | `http://localhost:8889` | Vite가 `/media`를 넘길 MediaMTX 주소 |
| `MEDIAMTX_BACKEND_URL` | `http://backend:8080` | MediaMTX가 인증·훅을 보낼 백엔드 주소. 개발 실행에서는 `http://host.docker.internal:<백엔드 포트>` |

전체 실행에서는 `DB_URL`, `SERVER_PORT`, `RECORDING_STORAGE_DIR`, `MEDIAMTX_BACKEND_URL`, `MEDIAMTX_API_URL`을 `docker-compose.yml`이 컨테이너 내부 값으로 고정합니다.

### Kubernetes (5단계)

`.env`를 읽지 않고 [k8s/app/config/app.env](k8s/app/config/app.env)(ConfigMap `app-config`)와 [k8s/app/config/secret.env](k8s/app/config/secret.env)(Secret `app-secret`)를 씁니다. 값은 compose 기본값과 같습니다. 고친 뒤 `kubectl apply -k k8s`를 다시 하면 이름에 붙은 내용 해시가 바뀌어 그 값을 쓰는 파드가 새로 뜹니다.

```dotenv
# k8s/app/config/app.env
DB_NAME=chzzz
DB_USERNAME=chzzz
DB_URL=jdbc:postgresql://db:5432/chzzz
RECORDING_STORAGE_DIR=/app/data/uploads
RECORDING_MAX_UPLOAD_SIZE=500MB
LIVE_PENDING_TIMEOUT=60s
LIVE_PUBLISH_GRACE=30s
MEDIAMTX_API_URL=http://mediamtx:9997
TOSS_CLIENT_KEY=test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm
TOSS_API_BASE_URL=https://api.tosspayments.com
```

```dotenv
# k8s/app/config/secret.env (로컬 확인용 기본값. 실제 값을 쓸 때는 커밋하지 않습니다)
DB_PASSWORD=chzzz
TOSS_SECRET_KEY=test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6
```

매니페스트에 직접 적은 설정값:

| 항목 | 값 | 위치 |
| --- | --- | --- |
| 진입 포트 | `localhost:8000` (frontend LoadBalancer). compose의 3000, 이 PC에서 다른 프로그램이 쓰는 8080을 피함 | `k8s/app/frontend.yaml` |
| WebRTC 미디어 포트 | `localhost:8190` UDP·TCP (compose의 8189와 겹치지 않게) | `k8s/app/mediamtx.yaml` |
| HPA 범위·목표 | 2~5대, CPU 평균 50%, 줄일 때 안정화 60초 | `k8s/app/backend-hpa.yaml` |
| 백엔드 CPU·메모리 | requests 250m·512Mi, limits 1·1Gi | `k8s/app/backend.yaml` |
| 백엔드 종료 | `preStop` 10초, `SERVER_SHUTDOWN=graceful` + 단계별 최대 20초, `terminationGracePeriodSeconds: 45` | `k8s/app/backend.yaml` |
| `INSTANCE_NAME` | 설정하지 않음(파드 이름이 헤더에 실림) | |

### 부하·상태 유지 확인 스크립트 (k6, 5단계)

[load/](load/)의 스크립트를 `grafana/k6:2.3.0` 이미지로 실행합니다(설치 불필요). 값은 `docker run`의 `-e 이름=값`으로 바꿉니다.

| 스크립트 | 변수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `public-get.js` (부하) | `BASE_URL` | `http://host.docker.internal:8000` | 대상 주소(컨테이너 안에서 본 호스트의 `localhost:8000`) |
| | `VUS` | `30` | 동시 가상 사용자 수 |
| | `DURATION` | `3m` | 부하 시간(k6 형식: `30s`, `3m`) |
| | `SLEEP` | `0.1` | 가상 사용자가 한 바퀴(공개 GET 8개)를 돈 뒤 쉬는 초 |
| `state-check.js` (상태 유지 확인) | `BASE_URL` | `http://host.docker.internal:8000` | 대상 주소 |
| | `DURATION` | `420` | 조회를 계속할 초(스케일 아웃 → 부하 중지 → 스케일 인까지 덮게) |
| | `INTERVAL` | `5` | 조회 간격 초 |

## 구현 범위

### 구현한 것 (1단계: 녹화방송)

- **등록** (`/recordings/new`): 제목과 영상 출처를 입력합니다. 영상 출처는 업로드 파일과 외부 URL 중 하나를 고릅니다.
  - 제목: 앞뒤 공백을 지우고 1~100자
  - 업로드 파일: mp4, webm. 최대 크기는 `RECORDING_MAX_UPLOAD_SIZE`
  - 외부 URL: `http`/`https` 주소. 브라우저 `<video>`가 바로 재생할 수 있는 영상 파일 주소여야 합니다
  - 화면이 먼저 입력을 검사하고, 서버가 최종 판단합니다. 서버가 거절하면 이유를 입력란 옆에 보여 줍니다
  - 업로드 중에는 등록 버튼이 비활성화되고, 성공하면 상세 화면으로 이동합니다
- **목록** (`/`의 녹화방송 섹션): 최신 등록순. 제목, 등록일시(브라우저 로컬 시간), 출처 종류(업로드/URL)를 보여 줍니다. 비어 있으면 안내와 등록 버튼을 보여 줍니다.
- **상세** (`/recordings/:id`): 제목, 등록일시, 영상 플레이어. 업로드 파일은 HTTP Range 요청으로 스트리밍해서 원하는 위치로 탐색할 수 있습니다. 재생에 실패하면 안내 문구를, 없는 녹화방송이면 "찾을 수 없음"과 목록 링크를 보여 줍니다.
- **영속 저장**: 녹화방송 메타데이터는 PostgreSQL에, 업로드 파일은 디스크(Compose에서는 볼륨)에 저장되어 재시작 후에도 남습니다. 스키마는 Flyway로 관리합니다.
- 3단계부터 등록에는 로그인이 필요하고, 등록한 회원이 소유자가 됩니다(아래 3단계).

### 구현한 것 (2단계: 라이브 방송)

- **송출 화면** (`/live/new`, 헤더 "방송 시작"): 카메라·마이크 미리보기(전면은 거울처럼), 전면·후면 카메라 선택, 제목 입력(녹화방송과 같은 규칙) → **방송 시작** → "방송 중" 배지와 시청 화면 주소 → **방송 종료**. 권한 거부·카메라 없음·HTTPS 아님·송출 연결 실패는 이유와 해결 방법을 안내합니다.
- **상태**: 대기(시작했지만 영상이 아직 오지 않음) → 방송 중 → 종료. 종료되는 경우는 송출자의 종료, 연결 끊김(탭 닫기·네트워크 끊김, MediaMTX가 알아채는 데 수십 초 걸릴 수 있음), 대기 시간 초과, 송출 대조(방송 중인데 MediaMTX에 송출이 없는 상태가 30초를 넘김, [라이브 상태 정리](#라이브-상태-정리-adr-0010))입니다. 4단계까지는 백엔드가 시작할 때 남은 대기·방송 중을 모두 종료로 정리했지만, 5단계에서 백엔드가 늘어날 때마다 다른 인스턴스의 방송을 끊는 문제가 있어 송출 대조로 바꿨습니다.
- **방송 목록** (`/`의 라이브 방송 섹션): 방송 중 전부(최근 시작순)와 최근 종료 10개(최근 종료순)를 "방송 중"(빨강)·"종료" 배지와 시각으로 보여 줍니다. 5초마다 다시 불러옵니다. 대기는 보이지 않습니다.
- **시청 화면** (`/live/:id`): 대기면 "곧 방송이 시작됩니다", 방송 중이면 WHEP으로 실시간 재생, 종료면 "방송이 종료되었습니다"와 시작·종료 시각. 3초마다 상태를 다시 불러와 바뀌면 화면도 바뀝니다. 브라우저가 소리 자동재생을 막으면 음소거로 재생하고 **소리 켜기** 버튼을 보여 줍니다. 연결 실패와 없는 방송("찾을 수 없음")도 안내합니다.
- **영속 저장**: 라이브 방송 기록(제목, 상태, 생성·시작·종료 시각, 송출 키 해시)은 PostgreSQL에 남습니다(Flyway `V2`). 영상 자체는 저장하지 않습니다.
- 3단계부터 시작에는 로그인이 필요하고, 종료는 소유자만 합니다([송출 키](#송출-키-adr-0006)).

### 구현한 것 (3단계: 회원, 방송 소유자 권한, 댓글)

- **회원가입·로그인·로그아웃** (`/signup`, `/login`, 헤더): 헤더에 비로그인이면 "로그인"·"회원가입", 로그인하면 "닉네임님"·"로그아웃". 입력 오류와 중복은 입력란 옆에 보여 줍니다.
- **소유자 표시**: 방송 목록(녹화방송·라이브 방송)과 상세·시청 화면에 소유자 닉네임.
- **녹화방송 관리** (상세 화면, 소유자에게만): **수정**(제목 인라인 수정, 등록과 같은 제목 규칙), **삭제**(확인 후 삭제, 업로드 파일과 댓글도 함께 지우고 목록으로).
- **라이브 방송 관리** (시청 화면, 소유자에게만): **제목 수정**(모든 상태), **방송 종료**(대기·방송 중, 다른 기기에서도), **삭제**(종료된 방송만, 댓글도 함께).
- **댓글** (녹화방송 상세·라이브 시청 화면 아래): 누구나 오래된 순 목록을 봅니다. 로그인하면 작성란, 비로그인이면 "로그인하면 댓글을 쓸 수 있습니다"와 로그인 링크. 내용은 앞뒤 공백을 지운 1~500자. 작성자에게만 **수정**("수정됨" 표시)·**삭제**. 라이브 방송 댓글은 3초마다 다시 불러옵니다.
- **거절 안내**: 서버가 401을 주면 로그인 안내와 링크, 403이면 권한 없음, 409면 서버가 준 이유를 보여 줍니다.
- **영속 저장**: 회원·소유자·댓글은 Flyway `V3` 하나로 추가했습니다. 2단계까지의 데이터가 있는 DB에 `V3`만 적용되고 기존 방송은 소유자 없이 남습니다.

### 구현한 것 (4단계: 후원)

- **후원 영역** (녹화방송 상세·라이브 시청 화면 아래): 확정된 후원의 **합계·건수**("합계 6,000원 · 2건")와 **내역**(후원자 닉네임, 금액, 확정 시각, 최신순). 라이브 시청 화면은 3초마다 다시 불러옵니다. 금액 고르기, 역할별 표시는 [후원 흐름](#후원-흐름-adr-0007).
- **결제위젯과 결과 화면**: 서버가 만든 주문의 금액으로 결제위젯을 그리고, `/donations/success`는 서버 확인 결과에 따라 "후원이 확정되었습니다"·"후원에 실패했습니다"(이유)·확인 필요를, `/donations/fail`은 "후원을 취소했습니다"·"후원에 실패했습니다"를 보여 주고 방송으로 돌아가는 링크를 둡니다. 서버와 통신이 안 되면 "다시 확인" 버튼이 나옵니다.
- **서버 확인과 중복 방지**: [서버가 확정하는 규칙](#서버가-확정하는-규칙), [중복 적립 방지](#중복-적립-방지-adr-0008).
- **영속 저장**: 후원과 승인 통지 기록은 Flyway `V4` 하나로 추가했습니다. 3단계까지의 데이터가 있는 DB에 `V4`만 적용되고 기존 회원·방송·댓글은 그대로 남는 것을 확인했습니다. 후원은 방송을 외래 키로 묶지 않아(녹화방송·라이브 방송 두 종류) 방송이 삭제돼도 후원 기록은 남습니다.

### 구현한 것 (5단계: 여러 인스턴스와 자동 스케일)

- **컨테이너 이미지**: compose와 같은 `backend`·`frontend` Dockerfile로 이미지를 만들어 Kubernetes에서도 그대로 씁니다.
- **로컬 클러스터에서 여러 인스턴스**: Docker Desktop 내장 Kubernetes에 kustomize 명령 하나(`kubectl apply -k k8s`)로 db, mediamtx, backend(2대로 시작), frontend(nginx 2대)를 올립니다([ADR-0011](docs/adr/0011-docker-desktop-kubernetes-with-kustomize.md)). 백엔드는 readiness·liveness·startup 프로브, `preStop`과 graceful shutdown을 둡니다.
- **자동 스케일 아웃·인**: metrics-server(v0.9.1) + HPA가 백엔드 CPU 평균 사용률 50%를 기준으로 2~5대 사이에서 늘리고 줄입니다([자동 스케일](#자동-스케일-hpa)).
- **요청 분산**: frontend LoadBalancer(`localhost:8000`) → nginx → `backend` Service가 연결마다 파드를 고릅니다. 응답 헤더 `X-Backend-Instance`로 어느 파드가 응답했는지 보입니다([요청 분산](#요청-분산과-x-backend-instance-헤더)).
- **공용 저장소**: 세션은 PostgreSQL(Flyway `V5`, 1~4단계 데이터가 있는 DB에 `V5`만 적용되고 기존 회원이 그대로인 것을 확인), 업로드 영상은 공유 볼륨, 방송·라이브 상태·댓글·후원은 PostgreSQL([상태를 어디에 두나](#상태를-어디에-두나)). 백엔드를 모두 재시작해도 로그인이 남습니다(compose 경로 포함).
- **라이브 상태 정리 변경**: 시작 시 정리를 없애고 MediaMTX 송출 대조로 바꿔, 파드가 늘거나 재시작되어도 다른 인스턴스의 방송이 끊기지 않습니다([라이브 상태 정리](#라이브-상태-정리-adr-0010)).
- **부하·상태 유지 확인 스크립트**: [load/](load/)의 k6 스크립트 두 개. 부하는 로그인 없는 공개 GET(녹화·라이브 방송 목록·상세, 댓글·후원 목록), 상태 유지 확인은 회원가입·로그인 → 방송 등록·댓글 작성 → 같은 쿠키로 로그인 상태·방송 목록·댓글을 주기적으로 조회하고 응답한 파드와 결과를 찍습니다. 하나라도 어긋나면 0이 아닌 종료 코드(99)로 끝납니다.
- 1~4단계의 compose 실행 경로(휴대폰 HTTPS 포함)는 그대로입니다.

### 구현한 것 (6단계: 설치형 앱)

자세한 사용법은 [앱](#앱-6단계-데스크톱-exe-android-apk)에 있습니다. `frontend/`·`backend/`는 바꾸지 않았고, 서버 쪽 변경은 `caddy/Caddyfile`의 1년 인증서 발급뿐입니다.

- **시작 화면**([apps/shell/](apps/shell/), TypeScript + Vite, 프레임워크 없음): 서버 주소 입력·검증, 연결 확인(`/api/auth/me` 200·401), 연결 실패(다시 시도·서버 주소 변경), 서버 인증서 확인(주소·지문·바뀐 지문 경고). 두 앱이 같은 화면을 쓰고, 앱과는 "앱 다리"(데스크톱 preload, Android 앱 안 Capacitor 로컬 플러그인 `ChzzzApp`)로 말합니다.
- **데스크톱 앱**([apps/desktop/](apps/desktop/), Electron 45.2.3 + TypeScript): 서버 주소·신뢰한 인증서를 `%APPDATA%\chzzz\settings.json`에 저장, 메뉴(서버 주소 변경, 다시 불러오기, 개발 빌드만 개발자 도구), `certificate-error`에 지문 고정, 서버 출처에만 미디어 권한, 로그인 쿠키 30일 보존, 주소 열기 규칙, electron-builder NSIS exe(서명 없음).
- **Android 앱**([apps/android/](apps/android/), Capacitor 8.6.0 + Kotlin, 추가 플러그인 없음): SharedPreferences에 저장, WebView SSL 오류 처리에 지문 고정(연결 확인도 지문 고정 TrustManager로), `CAMERA`·`RECORD_AUDIO` 런타임 요청 뒤 서버 출처에만 WebView 권한, 거절 시 앱 설정 안내 대화상자, 로그인 쿠키 30일 보존, 주소 열기 규칙(`intent://`는 안전하게 다듬어 운영체제로), 뒤로 가기, 15초 연결 시간 초과, 릴리스 서명 apk.
- **같은 규칙, 두 구현**: 신뢰한 인증서 판단과 주소 열기 규칙은 하나의 규칙 표(입력 → 기대 결과)를 데스크톱(Vitest)과 Android(JUnit)가 각각 테스트합니다.
- **인증서 지문 고정**: Caddy 기본값(12시간 서버 인증서)으로는 지문이 하루에도 여러 번 바뀌므로, 루트가 1년 서버 인증서를 직접 발급하게 바꾸고 그 지문을 (서버 주소, 지문) 조합으로 고정합니다([ADR-0013](docs/adr/0013-pin-server-certificate-fingerprint.md)).
- **배포**: GitHub Release `stage-6`에 exe·apk와 SHA-256 체크섬.

### API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/auth/me` | 로그인 중이면 `{ id, nickname }`, 아니면 `401`. CSRF 토큰 쿠키(`XSRF-TOKEN`)도 이때 받습니다 |
| `POST` | `/api/auth/signup` | JSON `{ loginId, password, nickname }`. `201 { id, nickname }` + 로그인 세션. 규칙 위반 `400`, 아이디·닉네임 중복 `409`(`errors`) |
| `POST` | `/api/auth/login` | JSON `{ loginId, password }`. `200 { id, nickname }`, 틀리면 `401` |
| `POST` | `/api/auth/logout` | 로그인 필요. `204` |
| `POST` | `/api/recordings` | 로그인 필요. `multipart/form-data`: `title` + `file` 또는 `url`. 성공 `201` + `Location` |
| `PATCH` | `/api/recordings/{id}` | 소유자. JSON `{ title }` → `200` 상세 |
| `DELETE` | `/api/recordings/{id}` | 소유자. `204`(업로드 파일·댓글도 삭제) |
| `GET` | `/api/recordings` | 녹화방송 목록(최신순) |
| `GET` | `/api/recordings/{id}` | 녹화방송 상세. 없으면 `404` |
| `GET` | `/api/recordings/{id}/video` | 업로드 파일 스트리밍. `Range` 요청에 `206`, 범위 밖 `416`, 업로드 파일이 아니거나 없으면 `404` |
| `POST` | `/api/live-broadcasts` | 로그인 필요. JSON `{ title }`. 대기 상태로 만들고 `201` + `Location` + `{ id, title, status, streamKey, whipUrl, createdAt }`. 송출 키는 이 응답에만 나옵니다 |
| `GET` | `/api/live-broadcasts` | 라이브 방송 목록: 방송 중(최근 시작순) + 최근 종료 10개(최근 종료순) |
| `GET` | `/api/live-broadcasts/{id}` | 상세 `{ id, title, status, createdAt, startedAt, endedAt, whepUrl }`. 없으면 `404` |
| `POST` | `/api/live-broadcasts/{id}/end` | 소유자(세션으로 검사, 송출 키 아님). 종료하고 상세를 돌려줍니다. 이미 종료면 `409` |
| `PATCH` | `/api/live-broadcasts/{id}` | 소유자. JSON `{ title }` → `200` 상세(모든 상태) |
| `DELETE` | `/api/live-broadcasts/{id}` | 소유자. 종료된 방송만 `204`(댓글도 삭제), 대기·방송 중이면 `409` |
| `GET` | `/api/recordings/{id}/comments`, `/api/live-broadcasts/{id}/comments` | 공개. 오래된 순 `[{ id, content, author: { id, nickname }, createdAt, updatedAt }]`. 방송 없음 `404` |
| `POST` | 위와 같은 경로 | 로그인 필요. JSON `{ content }` → `201` 댓글 |
| `PATCH` | `/api/comments/{id}` | 작성자. JSON `{ content }` → `200`(`updatedAt` 채워짐) |
| `DELETE` | `/api/comments/{id}` | 작성자. `204` |
| `GET` | `/api/payments/config` | 공개. `{ clientKey }`(결제위젯 테스트 클라이언트 키) |
| `POST` | `/api/recordings/{id}/donations`, `/api/live-broadcasts/{id}/donations` | 로그인 필요. JSON `{ amount }`. 결제 대기 후원을 만들고 `201 { orderId, amount, orderName, streamer, customerKey }`. 금액 규칙 위반 `400`(`errors`), 자기 방송·소유자 없는 방송 `409`, 방송 없음 `404` |
| `POST` | `/api/donations/confirm` | 로그인 필요(후원자 본인). JSON `{ paymentKey, orderId, amount }` → `{ id, status, amount, streamer, broadcast: { type, id }, confirmedAt, failure }`. 규칙은 [서버가 확정하는 규칙](#서버가-확정하는-규칙) |
| `POST` | `/api/donations/fail` | 로그인 필요(후원자 본인). JSON `{ orderId, code, message }` → `{ status, broadcast }`. `PAY_PROCESS_CANCELED`면 취소, 그 밖은 실패. 확정된 후원 `409` |
| `POST` | `/api/payments/toss/webhook` | 토스 웹훅(`PAYMENT_STATUS_CHANGED`). 로그인·CSRF 없음. 조회 API로 재확인 후 처리, `200`(조회 통신 실패만 `502`) |
| `GET` | `/api/recordings/{id}/donations`, `/api/live-broadcasts/{id}/donations` | 공개. 확정된 후원만 `{ totalAmount, count, items: [{ id, donor: { id, nickname }, amount, confirmedAt }] }`(최신순). 방송 없음 `404` |
| `POST` | `/media/live/{id}/whip`, `/media/live/{id}/whep` | MediaMTX WebRTC 송출·시청(SDP). 바깥에서는 이 경로로만 MediaMTX에 닿습니다 |

`/internal/mediamtx/{auth,available,unavailable}`은 MediaMTX만 부르는 내부 엔드포인트(송출·시청 허가, 영상 들어옴·끊김 훅)이고 바깥에는 열지 않습니다.

모든 백엔드 응답에는 응답한 인스턴스 이름이 `X-Backend-Instance` 헤더로 실립니다(5단계). 응답의 소유자·작성자는 `{ id, nickname }`만 담습니다(소유자 없으면 `owner: null`). 상태를 바꾸는 요청은 모두 `X-XSRF-TOKEN` 헤더가 필요합니다. 오류는 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식입니다. 입력 검증 실패(`400`)는 `errors` 배열에 `{ field, message }`를 담습니다. 업로드 크기를 넘으면 `413`입니다.

### 구현하지 않은 것

- 녹화방송 검색, 페이지네이션, 썸네일, 영상 출처 변경
- 외부 URL의 재생 가능 여부를 서버가 확인하는 기능: 서버가 사용자가 준 주소로 요청하면 내부망 접근(SSRF) 위험이 있어 형식만 검사합니다([ADR-0002](docs/adr/0002-no-server-check-for-external-url.md)). 재생할 수 없는 주소는 상세 화면의 안내 문구로 알립니다.
- 유튜브 같은 플랫폼 링크, HLS/DASH 스트리밍 주소
- mp4/webm 이외 형식, 서버 쪽 트랜스코딩
- 업로드 이어 올리기, 진행률 표시
- 라이브 방송을 녹화방송으로 저장(다시보기)
- 시청자 수, 채팅, 좋아요
- 방송 중 음소거·카메라 전환·화면 필터·화질 조절(카메라는 시작 전에만 고릅니다)
- 같은 네트워크 밖(LTE 등)에서의 송출·시청: TURN 서버가 없어서 휴대폰·시청 기기와 서버가 같은 LAN에 있어야 합니다([ADR-0003](docs/adr/0003-webrtc-whip-whep-with-mediamtx.md))
- 공인 인증서(경고 없는 HTTPS), 도메인
- 라이브 방송 검색·페이지네이션, 종료된 라이브 방송의 송출 재개(다시 방송하려면 새로 시작합니다)
- 이메일 인증, 비밀번호 찾기·변경, 회원 정보 수정, 회원 탈퇴, 소셜 로그인
- 관리자 역할, 방송 소유자의 댓글 관리(남의 댓글 삭제), 소유자 없는 기존 방송에 소유자 지정
- 로그인 시도 횟수 제한·계정 잠금
- 실시간 채팅(웹소켓), 댓글 답글·좋아요·신고·페이지네이션, "내 방송" 모아 보기
- 후원 메시지, 익명 후원, 후원 알림(방송 화면 위 효과)
- 환불·결제 취소(확정된 후원 되돌리기), 확인 필요 상태의 자동 처리(사람이 DB와 토스 기록을 보고 판단)
- 방송자 정산, 받은 후원 모아 보기, 후원 순위, 후원 내역 페이지네이션
- 가상계좌 입금 처리(위젯에 보이는 카드 외 결제수단은 테스트로만 열어 둠)
- 결제 대기 주문의 만료·정리 작업(결제하지 않은 주문은 결제 대기로 남고 합계에 들어가지 않습니다)
- 실제(운영) 결제 키, 결제 서비스 가맹 심사
- 토스 웹훅을 로컬로 받기 위한 공개 주소(터널), 웹훅 서명 검증(본문 대신 조회 API로 재확인합니다. ADR-0008)
- 5단계 범위 밖:
  - 클라우드 배포, 여러 노드 클러스터, 인그레스 컨트롤러, Kubernetes 경로의 HTTPS(TLS)
  - Kubernetes 경로의 휴대폰 접속·송출(휴대폰은 compose 경로에서 확인)
  - PostgreSQL·MediaMTX의 이중화·자동 스케일(각 1대), 프론트(nginx) 자동 스케일(2대 고정)
  - 요청 수·지연 시간 같은 사용자 지정 지표 기반 스케일(Prometheus 어댑터 등), 직접 만든 스케일러
  - 업로드 저장소를 `ReadWriteMany` 스토리지·오브젝트 스토리지로 바꾸기(노드 1대라 공유 볼륨으로 충분)
  - 분산 락(ShedLock 등)으로 주기 작업을 한 인스턴스에서만 돌리기(같은 값으로 바꾸는 작업이라 모든 인스턴스가 돌림)
  - 무중단 롤링 업데이트 전략 튜닝, 부하 테스트의 성능 목표(응답 시간 SLA)
- 6단계 범위 밖:
  - 스토어 배포(Play 스토어, Microsoft Store), 코드 서명 인증서, 자동 업데이트
  - iOS·macOS·Linux 앱
  - 빌드한 프론트를 앱에 넣는 방식(오프라인 화면, 딥링크 결제 복귀, 출처 분리에 따른 인증 재설계, [ADR-0012](docs/adr/0012-app-opens-server-screen.md))
  - 클라우드 서버, 앱에 서버 주소를 미리 넣어 두는 방식
  - 푸시 알림, 백그라운드 송출, 화면 꺼짐 중 송출, 앱 전용 화면·기능
  - GitHub Actions 자동 빌드
  - 카드 정보를 넣는 테스트 결제(4단계 결정 그대로. 결제창이 앱 안에서 열리고 취소하면 돌아오는 것까지만 확인)
- E2E 자동화 테스트(대신 아래 직접 확인 절차)

## 직접 확인 절차

전체 실행 기준입니다. 아래 순서대로 확인했습니다(2026-11-10, Windows PC + Android Chrome). 3단계부터 등록·시작은 로그인이 필요하므로, 1·2단계 절차는 **먼저 헤더의 "회원가입"으로 가입한 뒤** 진행합니다.

**준비**: 깨끗한 상태로 시작합니다. 업로드용 mp4 파일(예: 휴대폰으로 찍은 짧은 영상)을 하나 준비합니다. 휴대폰 확인을 하려면 먼저 [휴대폰 연결](#휴대폰-연결)의 1~4를 합니다.

```bash
docker compose down -v
```

```bash
docker compose up --build
```

### 녹화방송 (1단계)

1. **빈 목록**: http://localhost:3000 에 접속합니다. 녹화방송 섹션에 "아직 등록된 녹화방송이 없습니다."와 "첫 녹화방송 등록하기" 버튼이 보이면 통과입니다.
2. **외부 URL 등록 → 재생**: "첫 녹화방송 등록하기"를 누르고, 제목을 입력하고, 영상 출처는 "외부 URL" 그대로 두고 아래 주소를 넣은 뒤 "등록"을 누릅니다. 상세 화면으로 이동하고 재생 버튼을 눌러 영상이 재생되면 통과입니다.
   ```text
   https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4
   ```
3. **업로드 파일 등록 → 재생·탐색**: 헤더의 "녹화방송 등록"에서 제목을 입력하고, 영상 출처를 "업로드 파일"로 바꾸고, mp4 파일을 고른 뒤 "등록"을 누릅니다. 상세 화면으로 이동해 재생되고, 재생 막대를 눌러 중간 위치로 이동한 뒤에도 그 위치부터 재생되면 통과입니다.
4. **목록**: 헤더의 "목록"을 누릅니다. 두 녹화방송이 최신 등록순(업로드한 것이 위)으로, 등록일시와 출처 종류 배지("업로드", "URL")와 함께 보이면 통과입니다.
5. **잘못된 입력**: 등록 화면에서 다음을 각각 시도하고, 등록되지 않고 안내가 보이면 통과입니다.
   - 아무것도 입력하지 않고 "등록": "제목을 입력해 주세요.", "영상 URL을 입력해 주세요."
   - 외부 URL에 `ftp://example.com/video.mp4`: "http 또는 https로 시작하는 영상 주소를 입력해 주세요."
   - 업로드 파일에 `.avi` 같은 다른 형식의 파일: "mp4 또는 webm 파일만 올릴 수 있습니다."
6. **없는 녹화방송**: http://localhost:3000/recordings/9999 에 접속합니다. "찾을 수 없음"과 "목록으로 돌아가기" 링크가 보이면 통과입니다.

### 라이브 방송 (2단계, 휴대폰 송출 + PC 시청)

7. **접속·로그인·권한·미리보기 (휴대폰)**: `https://<PUBLIC_HOST>:8443/live/new`에 접속해 인증서 경고를 넘깁니다. 로그인 화면이 나오면 회원가입(또는 로그인)하고, 송출 화면으로 돌아오면 카메라·마이크를 허용합니다. 전면 카메라 미리보기가 보이고, "후면 카메라"를 고르면 후면 화면으로 바뀌면 통과입니다.
8. **송출 시작 (휴대폰)**: 제목을 넣고 **방송 시작**을 누릅니다. "연결 중…" 뒤에 빨간 **방송 중** 배지와 시청 화면 주소가 보이면 통과입니다.
9. **목록·시청 (PC)**: http://localhost:3000 의 라이브 방송 섹션에 그 방송이 **방송 중** 배지와 함께 보입니다. 눌러서 시청 화면으로 들어가 휴대폰 영상과 소리가 나오면 통과입니다(**소리 켜기**가 보이면 누릅니다). 확인 때 지연은 체감상 거의 없었습니다.
10. **송출 종료 (휴대폰 → PC)**: 휴대폰에서 **방송 종료**를 누릅니다(소유자는 다른 기기의 시청 화면에서도 종료할 수 있습니다. 아래 15). 휴대폰에 "방송이 종료되었습니다"가 보이고 카메라가 꺼집니다. 몇 초 안에 PC 시청 화면이 "방송이 종료되었습니다"와 시작·종료 시각으로 바뀌고, 목록에서 **종료** 배지로 보이면 통과입니다.
11. **연결 끊김 자동 종료**: 새 방송을 시작하고 **방송 중**이 되면 종료 버튼을 누르지 않고 휴대폰 Chrome 탭을 닫습니다. PC 재생이 멈추고, 약 30초 안에(MediaMTX가 끊김을 알아채는 시간) 시청 화면과 목록이 종료로 바뀌면 통과입니다.
12. **재시작 후 유지·정리**: 휴대폰에서 방송을 시작해 둔 채(또는 시작 직후) 서비스를 멈췄다가 다시 띄웁니다(`-v` 없이). 녹화방송 두 개와 라이브 방송 기록이 그대로 있고, 녹화방송은 상세 화면에서 재생되며, 남아 있던 대기·방송 중이 **종료**로 정리되면 통과입니다.
    ```bash
    docker compose down
    ```
    ```bash
    docker compose up
    ```
    - 5단계부터는 시작할 때 한꺼번에 정리하지 않습니다. 대기는 대기 시간 초과(60초)로, 방송 중은 다시 뜬 MediaMTX에 송출이 없으므로 송출 대조(유예 30초, 10초마다)로 종료됩니다. 그래서 다시 띄운 직후에는 잠깐 남아 있다가 1분 안쪽에 종료로 바뀝니다.
    - 확인한 날짜: 2026-11-10에 4단계까지의 방식(시작할 때 정리)으로 이 절차를 통과했습니다. 5단계(2026-11-18)에서는 PC 브라우저 송출로 **백엔드만** 재시작해 방송 중이 70초 넘게 유지되는 것, 백엔드를 멈춘 채 송출 탭을 닫고 다시 띄우면 10초 뒤 대조가 종료로 정리하는 것, 백엔드가 떠 있을 때 탭을 닫으면 끊김 통지로 1초 안에 종료되는 것, MediaMTX를 멈추면 경고만 남기고 아무것도 종료하지 않는 것을 확인했습니다. `docker compose down`/`up`으로 전체를 다시 띄우는 이 절차 자체는 5단계 방식으로 다시 해 보지 않았습니다.

### 회원·권한·댓글 (3단계, 세 역할)

브라우저를 역할별로 나눕니다: **소유자 A** = 휴대폰(그리고 PC의 일반 창), **다른 회원 B** = PC의 다른 브라우저나 시크릿 창, **비로그인** = 로그아웃한 창.

13. **가입·로그인·로그아웃**: 헤더의 "회원가입"으로 가입하면 바로 헤더에 "닉네임님"·"로그아웃"이 보입니다. 로그아웃하면 "로그인"·"회원가입"으로 바뀝니다. 같은 아이디나 대소문자만 다른 닉네임으로 다시 가입하면 입력란 옆에 "이미 사용 중인 …"이 보입니다. 비로그인으로 `/recordings/new`에 들어가면 로그인 화면으로 가고, 로그인하면 등록 화면으로 돌아오면 통과입니다.
14. **녹화방송 소유자**: A로 녹화방송을 등록합니다. 상세 화면에 A의 닉네임과 **수정**·**삭제**가 보입니다. B와 비로그인으로 같은 주소를 열면 버튼이 보이지 않습니다. A가 제목을 고쳐 저장하면 새 제목이 보이면 통과입니다(삭제는 17에서).
15. **라이브 방송 소유자 (휴대폰 A → PC)**: 휴대폰 A로 방송을 시작합니다. PC의 비로그인·B 창에서 목록에 소유자 닉네임과 **방송 중**이 보이고 시청 화면에서 영상이 나오지만 **제목 수정·방송 종료·삭제**는 보이지 않습니다. PC 일반 창에서 A로 로그인해 같은 시청 화면을 열면 소유자 버튼이 보이고, **방송 종료**를 누르면 휴대폰 송출이 끊기고 B 화면도 "방송이 종료되었습니다"로 바뀌면 통과입니다. 이어서 A 화면에 **삭제**가 생깁니다.
16. **댓글**: 비로그인 창의 상세·시청 화면 아래에는 작성란 대신 "로그인하면 댓글을 쓸 수 있습니다"가 보입니다. B로 댓글을 쓰면 바로 목록에 나오고 B의 댓글에만 **수정**·**삭제**가 보입니다. A(방송 소유자) 창에서는 B의 댓글에 버튼이 없습니다. B가 수정하면 "수정됨"이 붙습니다. 모두 그렇게 보이면 통과입니다(라이브 시청 화면 댓글은 3초마다 다시 불러옵니다).
17. **삭제와 댓글 정리**: A가 종료된 라이브 방송과 녹화방송을 각각 **삭제**(확인)하면 목록으로 돌아가고, 목록에서 사라지며 달린 댓글도 함께 지워지면 통과입니다.
18. **API를 직접 불러도 서버가 거절한다**: 버튼을 숨기는 것과 별개로 서버가 막는지 `curl`로 확인합니다. CSRF 토큰 쿠키를 받고, 비로그인으로 A의 녹화방송(아래 `<id>`)을 지워 본 뒤, 다른 회원 C로 가입해 다시 지워 보고, CSRF 헤더 없이 댓글을 써 봅니다.
    ```bash
    curl -s -c jar.txt -b jar.txt -o /dev/null http://localhost:3000/api/auth/me
    ```
    ```bash
    TOKEN=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
    ```
    ```bash
    curl -s -c jar.txt -b jar.txt -w " %{http_code}\n" -X DELETE -H "X-XSRF-TOKEN: $TOKEN" http://localhost:3000/api/recordings/<id>
    ```
    ```bash
    curl -s -c jar.txt -b jar.txt -o /dev/null -w "%{http_code}\n" -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"loginId":"member_c","password":"password123","nickname":"member_c"}' http://localhost:3000/api/auth/signup
    ```
    ```bash
    curl -s -c jar.txt -b jar.txt -w " %{http_code}\n" -X DELETE -H "X-XSRF-TOKEN: $TOKEN" http://localhost:3000/api/recordings/<id>
    ```
    ```bash
    curl -s -c jar.txt -b jar.txt -o /dev/null -w "%{http_code}\n" -H "Content-Type: application/json" -d '{"content":"hi"}' http://localhost:3000/api/recordings/<id>/comments
    ```
    차례로 비로그인 삭제 `401`, 가입 `201`, 다른 회원 삭제 `403`("이 방송의 소유자만 할 수 있습니다."), CSRF 헤더 없는 댓글 `403`이 나오고 녹화방송이 그대로 남아 있으면 통과입니다. 같은 방식으로 라이브 방송 종료(`POST /api/live-broadcasts/<id>/end`, 송출 키를 `Authorization`에 실어도)와 남의 댓글 수정(`PATCH /api/comments/<id>`)도 `401`/`403`입니다. 확인 때는 비로그인·소유자·다른 회원으로 상태 변경 API 36건을 직접 불러 모두 위 표대로 허용·거절되는 것을 확인했습니다.

### 후원 (4단계, 테스트 결제)

2026-11-11에 `docker compose down -v` 후 `docker compose up --build`로 띄운 PC(`http://localhost:3000`)에서 확인했습니다. 회원 A(방송자)가 외부 URL 녹화방송과 라이브 방송을 만들고, 회원 B가 후원합니다. 그 전에 3단계 데이터가 남은 DB로 띄워 `V4`만 적용되고 회원·녹화방송이 그대로인 것도 확인했습니다.

**확인 범위**: 테스트 결제 화면에 카드 정보를 넣어 결제를 끝내는 확인(아래 27~29)은 **하지 않았습니다.** 테스트 키라 실제 출금은 없지만, 확인하는 사람이 카드 정보 입력을 원하지 않아 카드 없이 할 수 있는 확인(19~26)만 실제로 했고, 결제 완료 뒤의 확정·중복 처리는 백엔드 자동 테스트(`DonationApiTest`, `DonationDuplicateApiTest`, `DonationAlreadyProcessedApiTest`, `DonationWebhookApiTest`)가 가짜 결제 서비스로 확인합니다. 27~29는 카드로 확인하려는 채점자를 위한 절차입니다.

19. **역할별 후원 영역**: 비로그인으로 A의 녹화방송 상세를 열면 후원 영역에 "합계 0원 · 0건"과 "로그인하면 후원할 수 있습니다"·로그인 링크가 보입니다. B로 로그인하면 빠른 선택(1,000·5,000·10,000·50,000원), "후원 금액(원)" 입력, "A님에게 1,000원 후원하기" 버튼이 보입니다. A로 로그인하면 **수정**·**삭제**만 보이고 후원 버튼은 없습니다. A의 라이브 방송 시청 화면(대기 "곧 방송이 시작됩니다")에서도 B에게 같은 후원 영역이 보이면 통과입니다.
20. **결제위젯 (B)**: "5,000원"을 누르고 "A님에게 5,000원 후원하기"를 누르면 "A님에게 5,000원을 후원합니다." 아래에 토스 결제위젯이 "테스트 환경이에요. 실제로 결제되지 않아요."와 결제수단 목록으로 그려지면 통과입니다([화면](docs/screenshots/stage-4/01-payment-widget.jpg)). 여기서 결제하지 않고 멈춥니다. 이 주문은 결제 대기로 남고 합계에 들어가지 않습니다.
21. **취소 화면**: 결제 화면을 닫으면 토스가 `PAY_PROCESS_CANCELED` 코드로 실패 주소에 돌려보냅니다. 확인 때는 같은 주소를 직접 열었습니다(`<orderId>`는 20에서 만든 주문, 아래 26의 조회로 찾습니다). "후원을 취소했습니다"와 "방송으로 돌아가기"가 보이고 합계가 그대로면 통과입니다.
    ```text
    http://localhost:3000/donations/fail?code=PAY_PROCESS_CANCELED&message=사용자가%20결제를%20취소하였습니다&orderId=<orderId>
    ```
22. **위조 성공 주소 (결제 실패 재현)**: 결제하지 않은 새 주문(20처럼 만들고 결제하지 않음)으로 성공 주소를 직접 엽니다. 서버가 토스에 승인을 요청하고 토스가 `NOT_FOUND_PAYMENT_SESSION`으로 거절하므로 "후원에 실패했습니다"와 토스의 이유("결제 시간이 만료되어 결제 진행 데이터가 존재하지 않습니다.")가 보이고, 방송 화면의 합계가 "0원 · 0건" 그대로면 통과입니다. 화면의 성공 주소만으로는 적립되지 않는다는 확인이고, 백엔드가 인터넷으로 토스에 닿는다는 확인이기도 합니다.
    ```text
    http://localhost:3000/donations/success?paymentKey=forged_key&orderId=<orderId>&amount=1000
    ```
23. **API 거절**: [18](#회원권한댓글-3단계-세-역할)처럼 `jar.txt`에 B로 로그인한 세션을 만들고(`POST /api/auth/login`, 로그인 뒤 `TOKEN`을 다시 읽습니다) 아래를 보냅니다. `<id>`는 A의 녹화방송입니다.
    ```bash
    curl -s -b jar.txt -w " %{http_code}\n" -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"amount":999}' http://localhost:3000/api/recordings/<id>/donations
    ```
    ```bash
    curl -s -b jar.txt -w " %{http_code}\n" -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"amount":5000}' http://localhost:3000/api/recordings/<id>/donations
    ```
    금액 999원은 `400`("후원 금액은 1,000원 이상 1,000,000원 이하의 정수로 입력해 주세요."), 5,000원은 `201`과 `orderId`입니다. 같은 요청을 비로그인으로 보내면 `401`, A로 보내면 `409`("후원할 수 없는 방송입니다."), A가 B의 `orderId`로 확인 요청을 보내면 `403`("자기 후원만 확인할 수 있습니다.")이면 통과입니다.
24. **금액 위조**: 23에서 받은 `orderId`로 금액만 바꿔 확인 요청을 보냅니다. `400`("결제 금액이 후원 금액과 다릅니다.")이고, 토스는 부르지 않으며 그 후원은 실패가 되고 합계는 그대로면 통과입니다.
    ```bash
    curl -s -b jar.txt -w " %{http_code}\n" -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"paymentKey":"forged_key2","orderId":"<orderId>","amount":50000}' http://localhost:3000/api/donations/confirm
    ```
25. **위조 웹훅 반복**: 로그인·CSRF 없이 없는 결제 키로 웹훅을 두 번 보냅니다. 둘 다 `200`이고 합계가 그대로면 통과입니다.
    ```bash
    for i in 1 2; do curl -s -o /dev/null -w "%{http_code}\n" -H "Content-Type: application/json" -d '{"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"forged_hook","orderId":"<orderId>","status":"DONE","totalAmount":5000}}' http://localhost:3000/api/payments/toss/webhook; done
    ```
26. **DB 기록**: 후원 상태와 승인 통지 기록을 봅니다.
    ```bash
    docker compose exec db psql -U chzzz -d chzzz -c "SELECT id, amount, status, failure_code FROM donation ORDER BY id;"
    ```
    ```bash
    docker compose exec db psql -U chzzz -d chzzz -c "SELECT id, payment_key, source, result, detail FROM payment_notification ORDER BY id;"
    ```
    확인 때 23~25를 API로 보낸 직후에는 후원이 `FAILED`(`NOT_FOUND_PAYMENT_SESSION`, 위조 성공 요청), `FAILED`(`AMOUNT_MISMATCH`, 금액 위조), `CANCELED`(`PAY_PROCESS_CANCELED`)로, 통지 기록이 확인 요청 `REJECTED` 4줄(토스 거절, 실패한 주문 재확인, 금액 위조, 남의 주문)과 웹훅 `IGNORED` 2줄("결제 서비스에 없는 결제입니다(NOT_FOUND_PAYMENT).")로 남았고, 합계는 `0원 · 0건`이었습니다. `CONFIRMED`는 0건입니다.

**카드로 확인할 때 (하지 않음)**

27. **성공**: B가 20에서 **결제하기** → 신용·체크카드 → 테스트 결제 화면 안내대로 결제합니다. "후원이 확정되었습니다"와 "A님에게 5,000원을 후원했습니다."가 보이고, 방송으로 돌아가면 합계 "5,000원 · 1건"과 내역에 B가 보이면 통과입니다. 주소창의 `paymentKey`·`orderId`·`amount`를 적어 둡니다.
28. **실패**: 결제 화면에서 카드사 거절이 나오면 "후원에 실패했습니다"와 이유가 보이고 합계가 그대로입니다. 테스트 카드로 거절을 만들기 어려우면 22가 같은 서버 경로(토스 거절 → 실패)를 확인합니다.
29. **승인 통지 중복**: 27의 확정 화면을 새로고침하고, 같은 확인 요청과 같은 웹훅을 각각 두 번 보냅니다. 모두 같은 확정 결과(`200`)이고 합계가 "5,000원 · 1건"에서 늘지 않으며, 26의 통지 기록에 `NEW_CONFIRMED` 1줄과 나머지는 `ALREADY_CONFIRMED`로 남으면 통과입니다.
    ```bash
    for i in 1 2; do curl -s -b jar.txt -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" -d '{"paymentKey":"<paymentKey>","orderId":"<orderId>","amount":5000}' http://localhost:3000/api/donations/confirm; echo; done
    ```
    ```bash
    for i in 1 2; do curl -s -o /dev/null -w "%{http_code}\n" -H "Content-Type: application/json" -d '{"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"<paymentKey>","orderId":"<orderId>","status":"DONE"}}' http://localhost:3000/api/payments/toss/webhook; done
    ```

**1~3단계 유지**: 같은 날 같은 실행에서 외부 URL 녹화방송 등록·재생, 소유자 버튼 표시, 라이브 방송 시청 화면(대기), 댓글 작성을 PC 브라우저로 다시 확인했습니다. 휴대폰 송출·시청은 4단계에서 송출·재생 코드가 바뀌지 않아(시청 화면에는 후원 영역만 붙음) 2·3단계 확인(2026-11-10)으로 갈음했습니다.

### 스케일 아웃/인과 상태 유지 (5단계, Kubernetes)

[Kubernetes 실행](#kubernetes-실행-5단계-자동-스케일)의 1~5를 마친 상태에서 합니다. 2026-11-18에 이 PC(Docker Desktop Kubernetes, 단일 노드 12 CPU·8GB)에서 확인했습니다. 아래 출력의 시각은 UTC입니다.

#### 백엔드 2대에서 요청 분산과 상태 공유

`X-Backend-Instance` 헤더로 응답한 파드를 구분해 확인했습니다.

30. **분산**: 요청마다 연결을 새로 열면 두 파드가 20:20, 연결 하나를 재사용해도 17:23으로 나눠 응답했습니다.
31. **로그인**: 로그인한 쿠키로 `/api/auth/me`를 10번 부르면 두 파드가 나눠 응답하고 모두 `200`이었습니다. 한 파드에서 로그아웃하면 두 파드 모두 `401`이었습니다.
32. **업로드 영상**: 한 파드로 올린 영상을 다른 파드가 Range 요청에 `206`으로 응답했습니다.
33. **댓글**: 다른 파드에서 써도 CSRF 확인을 통과했고, 두 파드 모두 같은 목록(3개)을 돌려줬습니다. 비로그인 작성은 `403`이었습니다.
34. **라이브**: PC 브라우저에서 송출·시청·종료가 되고, 미디어는 LoadBalancer(`localhost:8190`)를 거쳐 UDP로 연결되었습니다. PC에 카메라가 없어 캔버스 영상과 오실레이터 소리로 송출했습니다.
35. **파드 하나 삭제**: `kubectl -n chzzz delete pod <백엔드 파드>` 뒤 75초 동안 73번 조회가 모두 `200`이었고 로그인 상태와 방송 목록이 그대로였습니다. 새 파드는 약 30초 뒤부터 응답했습니다.

#### 스케일 아웃 → 스케일 인

터미널 세 개를 씁니다. 모두 저장소 루트에서 실행합니다(PowerShell·bash 같음).

36. **HPA 지켜보기** (터미널 1): `REPLICAS`가 2, `TARGETS`가 낮은 값(`cpu: 2%/50%` 등)이면 준비된 것입니다.
    ```bash
    kubectl -n chzzz get hpa backend -w
    ```
37. **상태 유지 확인 시작** (터미널 2): 회원가입·로그인·방송 등록·댓글 작성 뒤 5초마다 로그인 상태(`/api/auth/me`), 방송 목록, 댓글 목록을 조회해 응답한 파드와 결과를 한 줄씩 찍습니다. 확인 때는 스케일 인까지 덮으려고 `DURATION=540`(9분)으로 돌렸습니다.
    ```bash
    docker run --rm -i -v "${PWD}/load:/load" -e DURATION=540 grafana/k6:2.3.0 run -q --log-format=raw /load/state-check.js
    ```
38. **부하 걸기** (터미널 3, 상태 확인을 시작하고 30초쯤 뒤): 가상 사용자 30명이 3분 동안 공개 GET을 계속 보냅니다.
    ```bash
    docker run --rm -i -v "${PWD}/load:/load" -e VUS=30 -e DURATION=3m grafana/k6:2.3.0 run /load/public-get.js
    ```
39. **중간중간 대수와 CPU 보기**:
    ```bash
    kubectl -n chzzz get hpa,pods
    ```
    ```bash
    kubectl -n chzzz top pods -l app=backend
    ```
40. **통과 기준**: 부하 중에 `REPLICAS`가 2보다 커지고(5를 넘지 않음), 부하가 끝나고 1~2분 뒤 다시 2로 줄어듭니다. 그동안 상태 유지 확인의 모든 줄이 `OK`이고 마지막에 `checks_succeeded 100%`, 종료 코드 0이면 통과입니다.

**확인한 인스턴스 수 변화**: **2대 → 5대 → 2대**

| 시각 (UTC) | 일 |
| --- | --- |
| 08:03:15 | 상태 유지 확인 시작 (백엔드 2대, CPU 2%) |
| 08:03:44 | 부하 시작 (VUS 30, 3분) |
| 08:04:06 | **5대로 한 번에 늘어남** (기본 스케일 업 정책: 15초마다 +100% 또는 +4대 중 큰 값, 최대 5에서 멈춤). 새 파드는 40~55초 준비 중(0/1)이었다가 Ready |
| 08:06:47 | 부하 끝 (k6 종료 코드 0) |
| 08:08:06 | **2대로 줄어듦** (부하가 끝나고 약 80초 = 지표 지연 + 안정화 60초) |
| 08:12:33 | 상태 유지 확인 끝 (종료 코드 0) |

- 부하: 요청 86,650개(초당 약 481개), 실패 0%, 응답 시간 p95 411ms.
- 상태 유지 확인: 체크 **315/315** 성공. 응답한 백엔드 파드 5개 모두에서 로그인이 유지되고 방송 목록·댓글이 같았습니다. 스케일 아웃 중(08:07:47)에 쓴 댓글 2는 `cmct5` 파드가 받았고, 그 뒤 `52694`·`fxwmq`·`jflrt` 파드도 댓글 2개를 보여 줬습니다. 그 사이 늘어났다 사라진 파드 3개(`cmct5`·`fxwmq`·`vszs6`) 때문에 실패한 요청은 없었습니다.
- 조정: 첫 실행에서 바로 5대까지 늘고 2대로 돌아와 CPU requests(250m)·부하 세기·프로브를 바꾸지 않았습니다.
- 부하가 끝난 뒤 08:11:50~08:13 사이 부하 없이 평균 CPU가 잠깐 53%까지 올랐습니다(파드당 107m·161m). HPA 허용 오차(10%) 안이라 늘지 않았고 그 뒤 3분 동안 2~26%로 안정되었습니다. 세션 테이블은 4행, 오류 로그는 없어 JVM 내부 작업(GC·JIT)의 일시 사용으로 보고 조치하지 않았습니다.

실제 출력은 아래와 같습니다(확인 때 기록한 그대로).

**부하 전**

```
## 08:03:12 UTC  kubectl -n chzzz get hpa,pods
NAME                                          REFERENCE            TARGETS       MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/backend   Deployment/backend   cpu: 2%/50%   2         5         2          3m45s

NAME                            READY   STATUS    RESTARTS   AGE
pod/backend-84fbdc547f-52694    1/1     Running   0          35m
pod/backend-84fbdc547f-jflrt    1/1     Running   0          31m
pod/db-786d8ff659-8mx46         1/1     Running   0          35m
pod/frontend-66747978cc-bldk8   1/1     Running   0          35m
pod/frontend-66747978cc-dh7rc   1/1     Running   0          35m
pod/mediamtx-567f9b69d5-gnvrw   1/1     Running   0          35m
## 08:03:13 UTC  kubectl -n chzzz top pods -l app=backend
NAME                       CPU(cores)   MEMORY(bytes)   
backend-84fbdc547f-52694   6m           375Mi           
backend-84fbdc547f-jflrt   3m           361Mi           
```

**부하 중 (부하 시작 2분 뒤)**

```
## 08:05:45 UTC  kubectl -n chzzz get hpa,pods
NAME                                          REFERENCE            TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/backend   Deployment/backend   cpu: 204%/50%   2         5         5          6m18s

NAME                            READY   STATUS    RESTARTS   AGE
pod/backend-84fbdc547f-52694    1/1     Running   0          38m
pod/backend-84fbdc547f-cmct5    1/1     Running   0          107s
pod/backend-84fbdc547f-fxwmq    1/1     Running   0          106s
pod/backend-84fbdc547f-jflrt    1/1     Running   0          34m
pod/backend-84fbdc547f-vszs6    1/1     Running   0          106s
pod/db-786d8ff659-8mx46         1/1     Running   0          38m
pod/frontend-66747978cc-bldk8   1/1     Running   0          38m
pod/frontend-66747978cc-dh7rc   1/1     Running   0          38m
pod/mediamtx-567f9b69d5-gnvrw   1/1     Running   0          38m
## kubectl -n chzzz top pods -l app=backend
NAME                       CPU(cores)   MEMORY(bytes)   
backend-84fbdc547f-52694   242m         436Mi           
backend-84fbdc547f-cmct5   598m         384Mi           
backend-84fbdc547f-fxwmq   714m         391Mi           
backend-84fbdc547f-jflrt   236m         421Mi           
backend-84fbdc547f-vszs6   764m         388Mi           
```

**부하 끝난 직후**

```
## 08:06:47 UTC  kubectl -n chzzz get hpa,pods
NAME                                          REFERENCE            TARGETS         MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/backend   Deployment/backend   cpu: 120%/50%   2         5         5          7m20s

NAME                            READY   STATUS    RESTARTS   AGE
pod/backend-84fbdc547f-52694    1/1     Running   0          39m
pod/backend-84fbdc547f-cmct5    1/1     Running   0          2m49s
pod/backend-84fbdc547f-fxwmq    1/1     Running   0          2m48s
pod/backend-84fbdc547f-jflrt    1/1     Running   0          35m
pod/backend-84fbdc547f-vszs6    1/1     Running   0          2m48s
pod/db-786d8ff659-8mx46         1/1     Running   0          39m
pod/frontend-66747978cc-bldk8   1/1     Running   0          39m
pod/frontend-66747978cc-dh7rc   1/1     Running   0          39m
pod/mediamtx-567f9b69d5-gnvrw   1/1     Running   0          39m
## 08:06:48 UTC  kubectl -n chzzz top pods -l app=backend
NAME                       CPU(cores)   MEMORY(bytes)   
backend-84fbdc547f-52694   261m         436Mi           
backend-84fbdc547f-cmct5   300m         393Mi           
backend-84fbdc547f-fxwmq   366m         400Mi           
backend-84fbdc547f-jflrt   235m         422Mi           
backend-84fbdc547f-vszs6   340m         414Mi           
```

**부하 중지 후 (상태 유지 확인 끝난 시점)**

```
## 08:12:35 UTC  kubectl -n chzzz get hpa,pods
NAME                                          REFERENCE            TARGETS        MINPODS   MAXPODS   REPLICAS   AGE
horizontalpodautoscaler.autoscaling/backend   Deployment/backend   cpu: 30%/50%   2         5         2          13m

NAME                            READY   STATUS    RESTARTS   AGE
pod/backend-84fbdc547f-52694    1/1     Running   0          44m
pod/backend-84fbdc547f-jflrt    1/1     Running   0          40m
pod/db-786d8ff659-8mx46         1/1     Running   0          44m
pod/frontend-66747978cc-bldk8   1/1     Running   0          44m
pod/frontend-66747978cc-dh7rc   1/1     Running   0          44m
pod/mediamtx-567f9b69d5-gnvrw   1/1     Running   0          44m
## 08:12:36 UTC  kubectl -n chzzz top pods -l app=backend
NAME                       CPU(cores)   MEMORY(bytes)   
backend-84fbdc547f-52694   103m         432Mi           
backend-84fbdc547f-jflrt   48m          419Mi           
```

<details>
<summary><code>kubectl -n chzzz get hpa backend -w</code> (08:03:12 UTC 시작, 약 15초 간격)</summary>

```
NAME      REFERENCE            TARGETS       MINPODS   MAXPODS   REPLICAS   AGE
backend   Deployment/backend   cpu: 2%/50%   2         5         2          3m45s
backend   Deployment/backend   cpu: 1%/50%   2         5         2          3m46s
backend   Deployment/backend   cpu: 3%/50%   2         5         2          4m1s
backend   Deployment/backend   cpu: 5%/50%   2         5         2          4m16s
backend   Deployment/backend   cpu: 106%/50%   2         5         2          4m31s
backend   Deployment/backend   cpu: 365%/50%   2         5         5          4m46s
backend   Deployment/backend   cpu: 399%/50%   2         5         5          5m1s
backend   Deployment/backend   cpu: 391%/50%   2         5         5          5m17s
backend   Deployment/backend   cpu: 391%/50%   2         5         5          5m32s
backend   Deployment/backend   cpu: 263%/50%   2         5         5          5m47s
backend   Deployment/backend   cpu: 298%/50%   2         5         5          6m2s
backend   Deployment/backend   cpu: 204%/50%   2         5         5          6m17s
backend   Deployment/backend   cpu: 223%/50%   2         5         5          6m32s
backend   Deployment/backend   cpu: 180%/50%   2         5         5          6m47s
backend   Deployment/backend   cpu: 178%/50%   2         5         5          7m2s
backend   Deployment/backend   cpu: 120%/50%   2         5         5          7m17s
backend   Deployment/backend   cpu: 117%/50%   2         5         5          7m32s
backend   Deployment/backend   cpu: 19%/50%    2         5         5          7m47s
backend   Deployment/backend   cpu: 1%/50%     2         5         5          8m2s
backend   Deployment/backend   cpu: 2%/50%     2         5         5          8m17s
backend   Deployment/backend   cpu: 7%/50%     2         5         5          8m32s
backend   Deployment/backend   cpu: 3%/50%     2         5         2          8m47s
backend   Deployment/backend   cpu: 3%/50%     2         5         2          9m17s
backend   Deployment/backend   cpu: 2%/50%     2         5         2          9m32s
backend   Deployment/backend   cpu: 4%/50%     2         5         2          9m47s
backend   Deployment/backend   cpu: 2%/50%     2         5         2          10m
backend   Deployment/backend   cpu: 1%/50%     2         5         2          11m
backend   Deployment/backend   cpu: 3%/50%     2         5         2          11m
backend   Deployment/backend   cpu: 4%/50%     2         5         2          12m
backend   Deployment/backend   cpu: 16%/50%    2         5         2          12m
backend   Deployment/backend   cpu: 23%/50%    2         5         2          12m
backend   Deployment/backend   cpu: 24%/50%    2         5         2          12m
backend   Deployment/backend   cpu: 30%/50%    2         5         2          13m
backend   Deployment/backend   cpu: 39%/50%    2         5         2          13m
```

</details>

<details>
<summary>상태 유지 확인 출력 (같은 시간대)</summary>

```
08:03:15 OK   회원가입 sc_mwlu3oog175: 201 @ backend-84fbdc547f-52694
08:03:15 OK   로그인: 200 @ backend-84fbdc547f-52694
08:03:15 OK   방송 등록: 201 @ backend-84fbdc547f-jflrt
08:03:15 OK   댓글 1 작성 (방송 4): 201 @ backend-84fbdc547f-jflrt
시각     | me 파드 / 결과 | 목록 파드 / 결과 | 댓글 파드 / 결과
08:03:15 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:03:20 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:03:25 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:03:30 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:03:35 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:03:40 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:03:45 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:03:54 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:03:59 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:05 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:10 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:16 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:04:22 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:29 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:35 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:04:40 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:04:45 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:52 | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-52694 OK 댓글 1개
08:04:57 | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:05:03 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:05:08 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:05:14 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-cmct5 OK 댓글 1개
08:05:19 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:05:26 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:05:31 | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:05:38 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:05:43 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:05:49 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:05:54 | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:06:00 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:06:05 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:06:10 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:06:15 | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:06:21 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-vszs6 OK 댓글 1개
08:06:26 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-vszs6 OK 댓글 1개
08:06:31 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:06:36 | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-vszs6 OK 댓글 1개
08:06:41 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:06:46 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-52694 OK 댓글 1개
08:06:51 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:06:56 | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:07:01 | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 1개
08:07:07 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-cmct5 OK 댓글 1개
08:07:12 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-cmct5 OK 댓글 1개
08:07:17 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-cmct5 OK 댓글 1개
08:07:22 | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:07:27 | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-vszs6 OK 댓글 1개
08:07:32 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 1개
08:07:37 | backend-84fbdc547f-cmct5 OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:07:42 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-fxwmq OK 댓글 1개
08:07:47 OK   댓글 2 작성: 201 @ backend-84fbdc547f-cmct5
08:07:48 | backend-84fbdc547f-vszs6 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:07:53 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-fxwmq OK 댓글 2개
08:07:58 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-fxwmq OK | backend-84fbdc547f-cmct5 OK 댓글 2개
08:08:03 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:08:08 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:08:13 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:08:18 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:08:23 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:08:28 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:08:33 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:08:38 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:08:43 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:08:48 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:08:53 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:08:58 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:09:03 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:09:08 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:09:13 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:09:18 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:09:23 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:09:28 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:09:33 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:09:38 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:09:43 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:09:48 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:09:53 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:09:59 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:04 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:09 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:10:14 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:10:19 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:10:24 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:29 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:34 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:39 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:10:44 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:10:49 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:54 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:10:59 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:11:04 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
08:11:09 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:11:15 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:11:20 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:11:25 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:11:30 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:11:36 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:11:41 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:11:47 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:11:52 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:11:57 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:12:03 | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-jflrt OK 댓글 2개
08:12:08 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK | backend-84fbdc547f-52694 OK 댓글 2개
08:12:14 | backend-84fbdc547f-52694 OK | backend-84fbdc547f-jflrt OK | backend-84fbdc547f-52694 OK 댓글 2개
응답한 파드 5개: backend-84fbdc547f-52694, backend-84fbdc547f-cmct5, backend-84fbdc547f-fxwmq, backend-84fbdc547f-jflrt, backend-84fbdc547f-vszs6
댓글 2 작성 파드: backend-84fbdc547f-cmct5, 그 뒤 댓글 2를 보여 준 다른 파드: backend-84fbdc547f-52694, backend-84fbdc547f-fxwmq, backend-84fbdc547f-jflrt
  █ THRESHOLDS 
    checks
    ✓ 'rate==1' rate=100.00%
    checks_succeeded...: 100.00% 315 out of 315
```

</details>

### 앱 (6단계, 데스크톱 앱 + Android 앱)

**준비**: 전체 실행 서버를 [휴대폰 연결](#휴대폰-연결)의 1~4대로 띄우고(`.env`의 `PUBLIC_HOST`=서버 PC의 LAN IP), [서버 PC에서 지문 보기](#첫-실행-서버-주소와-인증서-지문)로 지문을 적어 둡니다. 앱은 [내려받기](#내려받기)·[설치](#설치)대로 설치합니다. 카드 정보는 넣지 않습니다.

1. **첫 실행과 주소 검증**: 앱을 열면 **chzzz 서버 연결** 화면 → `192.168.0.5:8443`(스킴 없음), `http://192.168.0.5:3000`(localhost 아닌 http)을 넣으면 저장하지 않고 이유가 보이는지 → 서버가 꺼진 주소면 **서버에 연결하지 못했습니다**.
2. **인증서 지문 신뢰**: `https://<PUBLIC_HOST>:8443` → **서버 인증서 확인**에 서버 PC에서 본 것과 같은 지문 → **연결하지 않음**이면 주소 입력 화면(아무것도 저장 안 됨) → 다시 확인 → **신뢰하고 연결** → 서버 화면.
3. **로그인 유지**: 회원가입·로그인 → 앱을 완전히 닫았다 다시 열면 묻지 않고 바로 서버 화면, 헤더에 닉네임이 그대로.
4. **송출·시청**: Android 앱에서 `/live/new` → 카메라·마이크 권한 허용 → 방송 시작 → 데스크톱 앱(또는 PC 브라우저)에서 그 방송 재생 → Android 앱에서 종료 → 시청 화면에 "방송이 종료되었습니다". 권한을 거부하면 이유와 **설정 열기** 대화상자가 보이는지도 봅니다.
5. **댓글·후원**: 댓글 작성 → 다른 회원의 방송에서 후원하기 → 결제하기 → 토스 결제창이 **앱 안에서** 열림 → 입력하지 않고 닫거나 뒤로 → "후원을 취소했습니다", 로그인 그대로.
6. **주소 열기 규칙**: 서버 밖 링크는 기본 브라우저(Android는 Chrome)로 열리고 앱 화면은 그대로.
7. **뒤로 가기(Android)**: 앱 안 이전 화면 → 첫 화면에서는 앱이 닫힘.
8. **연결 실패와 주소 변경**: 서버(또는 `caddy` 컨테이너)를 멈추고 앱을 다시 열면 연결 실패 화면 → 서버를 켜고 **다시 시도** → 서버 화면, 로그인 그대로. 데스크톱 앱은 메뉴 **chzzz → 서버 주소 변경**으로 다른 주소를 넣었다 되돌립니다.
9. **지문이 바뀌면 다시 묻기**: 같은 주소로 인증서가 다른 서버(예: 다른 `caddy-data` 볼륨)를 띄우고 앱을 다시 열면 "인증서 지문이 바뀌었습니다"와 두 지문 → **연결하지 않음**이면 저장한 지문 그대로.

**실제로 확인한 결과** (2026-11-19, Windows 11 PC, 서버 `https://192.168.0.5:8443`, 서버 인증서 `C4:16:15:54:…:F4:37:81`, 기존 데이터를 지우지 않으려고 별도 볼륨의 compose 프로젝트 `chzzz-app02`로 띄움):

- **데스크톱 앱, `http://localhost:3000`** (exe를 `/S`로 설치): 첫 실행 시작 화면 → 형식 오류·연결 실패 문구 → 주소 저장 → 회원가입·로그아웃·로그인 → 앱 재시작 시 바로 서버 화면·로그인 유지 → 녹화 재생·댓글 → 웹캠 송출 시작, 웹(960x540)과 앱 새 창(1280x720)에서 시청, 종료(`ENDED`) → `frontend` 컨테이너 정지 시 연결 실패 화면, 켠 뒤 다시 시도로 복귀 → 메뉴로 서버 주소 변경·다시 불러오기, 설치본 메뉴에 개발자 도구 없음 → 다른 출처(`127.0.0.1`)의 마이크 요청은 거절, 서버 출처는 허용. 이 PC에는 카메라가 없어(마이크만 있음) 송출은 Chromium 가짜 카메라(`chzzz.exe --use-fake-device-for-media-stream`)로 확인했습니다.
- **데스크톱 앱, `https://192.168.0.5:8443`**: 지문 확인 화면의 지문이 Node로 직접 읽은 서버 인증서 지문과 같음 → **연결하지 않음**이면 설정 그대로 → **신뢰하고 연결** → `settings.json`에 그 출처·지문만 저장 → 회원가입 → 재실행 시 묻지 않고 열림·로그인 유지 → 후원 1,000원 결제하기 → 토스 퀵계좌이체 테스트 화면이 앱 창 안(iframe)에 열림, 입력 없이 닫음 → `window.open`·`target=_blank`·`location.href`로 연 `example.com`·`example.net`은 Chrome에 열리고 앱은 그대로, `file:///C:/Windows/win.ini`는 막힘 → 가짜 카메라로 송출, 앱 새 창에서 1280x720 재생, 종료 → 같은 주소에 기존 볼륨(12시간 인증서 `B9:A7:A1:23:…:80:34`)의 서버를 띄우자 "인증서 지문이 바뀌었습니다"와 두 지문, 거절하면 저장 지문 그대로 → 원래 서버로 되돌리자 묻지 않고 열림 → 다른 출처(`https://127.0.0.1:8443`, 이름 불일치)의 인증서 오류는 묻지 않고 실패.
- **Android 앱, 에뮬레이터(API 37)**: 지문 확인(Node로 본 지문과 같음) → 거절·신뢰 → 로그인 후 앱 프로세스를 끝내고 다시 열어도 로그인 유지 → 뒤로 가기(이전 화면, 첫 화면에서 종료) → 외부 링크는 Chrome, `file:`은 막힘 → 댓글 → 후원 결제하기: 모바일은 결제창(`payment-gateway-sandbox.tosspayments.com/mobile`)으로 화면이 이동해 앱 안에서 열림, 입력 없이 `<` → "후원을 취소했습니다", 로그인 유지 → 권한 거절 시 "권한이 거부되었습니다" + 앱 설정 안내 대화상자, **설정 열기**는 chzzz 앱 정보 화면 → 허용 뒤 720x1280 송출, 설치된 데스크톱 앱에서 720x1280 재생, 종료하면 데스크톱에도 종료 표시 → Caddy 정지 시 연결 실패 화면, 켜고 다시 시도로 복귀. 마지막에 서명된 릴리스 apk로 다시 설치해 핵심 흐름(지문 신뢰, 로그인 유지)을 확인했습니다.
- **Android 앱, 실제 휴대폰**: 서명된 apk를 휴대폰 다운로드 폴더로 옮겨 "출처를 알 수 없는 앱" 허용 후 설치 → 지문 `C4:16:15:54…F4:37:81` 일치 확인 후 신뢰 → 로그인 → 최근 앱에서 닫고 다시 열어도 로그인 유지 → 카메라·마이크 허용, 실제 카메라로 송출(방송 중) → 데스크톱 앱에서 720x1280 재생(3초 동안 재생 위치 7.6→10.6초) → 휴대폰에서 종료 → 데스크톱에 "방송이 종료되었습니다" → 뒤로 가기는 이전 화면, 첫 화면에서 앱 종료.
- 확인하지 않은 것: 카드 정보를 넣는 결제, Windows 10 실기기, 설치 마법사 화면(조용한 설치로 확인), Android 앱 데이터 삭제로 주소 초기화.

화면 예시는 [docs/screenshots/stage-1](docs/screenshots/stage-1), [docs/screenshots/stage-2](docs/screenshots/stage-2), [docs/screenshots/stage-3](docs/screenshots/stage-3), [docs/screenshots/stage-4](docs/screenshots/stage-4)에 있습니다.

## 테스트

테스트는 TDD(red → green)로 작성했고, 사이클 기록은 [docs/tdd-log.md](docs/tdd-log.md)에 있습니다.

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm test
```

```bash
cd apps/shell && npm test
```

```bash
cd apps/desktop && npm test
```

```bash
cd apps/android && npm run sync && cd android && ./gradlew testReleaseUnitTest
```

- 후원: 토스 결제 서비스는 테스트에서 가짜(`FakePaymentGateway`)로 바꿔 끼워 승인·조회의 성공·거절·통신 실패·"이미 처리된 결제"를 만들고, 승인 호출 횟수로 "다시 부르지 않음"을 확인합니다. 동시 확인 요청은 두 스레드로 같은 주문을 확인해 한 건만 확정되는지 봅니다. 실제 토스 연동 코드(`TossPaymentGateway`)는 PC에 띄운 가짜 HTTP 서버로 요청 모양(Basic 인증, 본문)과 응답 해석을 확인합니다. 프론트는 결제위젯 어댑터를 가짜로 바꿉니다.
- 백엔드: Spring Boot 앱 전체를 띄우고 REST API로만 검증합니다. 로그인은 테스트도 실제 회원가입·로그인 API로 받은 세션을 쓰고, 상태를 바꾸는 API마다 비로그인·소유자(작성자)·다른 회원 세 경우를 확인합니다. 쿠키 속성·CSRF 흐름은 실제 서버에 브라우저처럼 요청해 확인합니다. DB는 Testcontainers로 실제 PostgreSQL을 띄우므로 **Docker가 실행 중이어야** 합니다. MediaMTX의 인증·훅 요청은 테스트가 내부 엔드포인트를 직접 불러 흉내 내고, 시간(대기 시간 초과)은 테스트용 시계로 바꿔 끼웁니다.
- 프론트엔드: Vitest + React Testing Library로 라우터를 포함한 앱 전체를 렌더링하고, 네트워크 경계(`fetch`)와 미디어 어댑터(카메라·WebRTC)만 가짜로 바꿉니다. 로그인 상태도 스텁한 `/api/auth/me` 응답으로 만듭니다.
- 여러 인스턴스(5단계): 같은 PostgreSQL(Testcontainers)을 쓰는 실제 서버 두 개(인스턴스 A·B)를 띄워, A에서 로그인한 쿠키로 B가 로그인 상태를 보이고, A에서 로그아웃하면 B도 `401`이며, B에서 CSRF가 필요한 댓글 작성이 통과하는지 확인합니다(`MultiInstanceApiTest`). 응답 헤더 `X-Backend-Instance`, 주기 작업(대기 정리·송출 대조)을 두 인스턴스에서 동시에 돌려도 예외 없이 같은 결과인지도 확인합니다. MediaMTX 제어 API는 가짜 HTTP 서버로 흉내 냅니다. Kubernetes 매니페스트·HPA·k6 스크립트는 자동 테스트 대신 위 [직접 확인](#스케일-아웃인과-상태-유지-5단계-kubernetes)으로 확인합니다.
- 앱(6단계): 앱이 겉으로 보이는 판단만 테스트합니다. 서버 주소 허용·거절, 연결 확인 결과 해석, 인증서를 받아들임·묻기·거절, 주소를 앱 안·기본 브라우저·운영체제·막음 중 어디서 열지, 권한 허용, 로그인 쿠키 보존을 Electron·WebView 없이 도는 순수 함수로 만들어 데스크톱은 Vitest, Android는 JUnit으로 같은 규칙 표를 확인합니다. 시작 화면은 Vitest + DOM 테스트로 앱 다리만 가짜로 바꿉니다. 설치·인증서 신뢰·송출·결제창·외부 링크는 [앱 직접 확인](#앱-6단계-데스크톱-앱--android-앱)으로 확인했습니다. 마지막 실행 결과: shell 41, desktop 65, frontend 119, backend 219, Android 단위 26개 모두 통과.

## 문서

- [GLOSSARY.md](GLOSSARY.md): 도메인 용어
- [docs/adr/](docs/adr/): 설계 결정 기록
- [docs/tdd-log.md](docs/tdd-log.md): TDD 사이클 기록
- [k8s/](k8s/): Kubernetes 매니페스트(kustomize), [load/](load/): k6 부하·상태 유지 확인 스크립트
- [apps/](apps/): 앱(`shell` 시작 화면, `desktop` 데스크톱 앱, `android` Android 앱)
- [AGENTS.md](AGENTS.md): 작업 지침
