# chzzz

누구나 로그인 없이 **녹화방송**과 **라이브 방송**을 시청하고, **회원**은 녹화방송을 등록하거나 휴대폰 카메라·마이크로 라이브 방송을 송출하고 방송에 **댓글**을 다는 서비스입니다. 방송을 등록·시작한 회원이 그 방송의 **소유자**가 되어 수정·삭제·종료할 수 있고, 다른 회원은 방송 화면에서 소유자(**방송자**)에게 **테스트 결제로 후원**합니다(실제 돈은 나가지 않습니다). 용어는 [GLOSSARY.md](GLOSSARY.md)를 따릅니다.

- 백엔드: Spring Boot 4.1 + Kotlin, JDK 21, Gradle(Kotlin DSL), PostgreSQL 17 + Flyway
- 프론트엔드: React + Vite + TypeScript, React Router
- 라이브 송출: WebRTC WHIP(송출)·WHEP(시청) + [MediaMTX](https://github.com/bluenviron/mediamtx) ([ADR-0003](docs/adr/0003-webrtc-whip-whep-with-mediamtx.md))
- 휴대폰 HTTPS: Caddy 로컬 인증서 ([ADR-0004](docs/adr/0004-https-with-caddy-local-ca.md))
- 인증: Spring Security 서버 세션 쿠키 + CSRF 토큰 ([ADR-0005](docs/adr/0005-session-cookie-auth-with-spring-security.md)), 송출 키는 송출 허가 전용 ([ADR-0006](docs/adr/0006-stream-key-only-for-publish.md))
- 후원 결제: [토스페이먼츠](https://docs.tosspayments.com/reference) 결제위젯 **테스트 모드** + 서버 승인 확인 ([ADR-0007](docs/adr/0007-toss-payments-test-mode-with-server-confirmation.md)), 중복 확정 방지 ([ADR-0008](docs/adr/0008-idempotent-donation-confirmation.md))
- 실행: Docker Compose(db, backend, frontend, mediamtx, caddy)

## 단계별 구현 내역

과제는 4단계로 진행합니다.

| 단계 | 내용 | 상태 |
| --- | --- | --- |
| 1 | 녹화방송 등록·목록·상세·재생 (TDD) | 완료 |
| 2 | 라이브 방송 송출·시청, 방송 중·종료 표시 (TDD) | 완료 |
| 3 | 회원가입·로그인, 방송 소유자 권한(수정·삭제·송출 제어), 방송별 댓글 (TDD) | 완료 |
| 4 | 방송자에게 테스트 결제 후원, 서버 승인 확인, 중복 확정 방지, 방송별 후원 내역·합계 (TDD) | 완료 |

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
- 세션은 백엔드 메모리에 있어서 **백엔드를 재시작하면 모두 로그아웃**됩니다. 휴대폰(`https://<PUBLIC_HOST>:8443`)과 PC(`http://localhost:3000`)는 주소가 달라 로그인도 각각 따로 합니다.
- 로그인이 필요한 화면(녹화방송 등록, 방송 시작)에 비로그인으로 들어가면 로그인 화면으로 갔다가, 로그인·가입하면 원래 화면으로 돌아옵니다.

### 송출 키 (ADR-0006)

1. 로그인한 회원이 방송 시작을 누르면 서버가 라이브 방송을 **대기**로 만들고 그 회원을 소유자로 기록한 뒤, 그 방송 전용 **송출 키**를 **한 번만** 돌려줍니다. 서버에는 키의 SHA-256 해시만 저장합니다.
2. 송출 화면은 그 키로 MediaMTX에 WHIP 송출을 시작합니다. MediaMTX는 송출·시청 요청마다 백엔드 내부 엔드포인트에 허가를 묻고, 백엔드는 대기인 방송에 맞는 키일 때만 송출을, 방송 중일 때만 시청을 허가합니다. MediaMTX는 브라우저의 세션 쿠키를 넘겨주지 않아서 송출 허가만은 키로 판단합니다.
3. **종료·제목 수정·삭제는 송출 키가 아니라 로그인한 소유자인지로** 검사합니다. 송출하던 탭을 닫았거나 다른 기기에서도 소유자는 로그인만 하면 방송을 끝낼 수 있고, 송출 키가 새어도 그것으로 방송을 끄거나 바꿀 수는 없습니다.
4. 영상이 들어오면 **방송 중**, 소유자가 종료하거나 연결이 끊기면 **종료**가 됩니다. 영상이 60초(`LIVE_PENDING_TIMEOUT`) 안에 들어오지 않은 대기도 종료로 정리됩니다.
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
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` / `TOSS_API_BASE_URL` | 위와 같음 | 토스 결제위젯 테스트 키와 API 주소 |

### 프론트엔드·MediaMTX (개발 실행)

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | Vite가 `/api`를 넘길 백엔드 주소 |
| `VITE_MEDIA_PROXY_TARGET` | `http://localhost:8889` | Vite가 `/media`를 넘길 MediaMTX 주소 |
| `MEDIAMTX_BACKEND_URL` | `http://backend:8080` | MediaMTX가 인증·훅을 보낼 백엔드 주소. 개발 실행에서는 `http://host.docker.internal:<백엔드 포트>` |

전체 실행에서는 `DB_URL`, `SERVER_PORT`, `RECORDING_STORAGE_DIR`, `MEDIAMTX_BACKEND_URL`을 `docker-compose.yml`이 컨테이너 내부 값으로 고정합니다.

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
- **상태**: 대기(시작했지만 영상이 아직 오지 않음) → 방송 중 → 종료. 종료되는 경우는 송출자의 종료, 연결 끊김(탭 닫기·네트워크 끊김, MediaMTX가 알아채는 데 수십 초 걸릴 수 있음), 대기 시간 초과, 서비스 재시작(남아 있던 대기·방송 중을 시작할 때 종료로 정리)입니다.
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

응답의 소유자·작성자는 `{ id, nickname }`만 담습니다(소유자 없으면 `owner: null`). 상태를 바꾸는 요청은 모두 `X-XSRF-TOKEN` 헤더가 필요합니다. 오류는 [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) 형식입니다. 입력 검증 실패(`400`)는 `errors` 배열에 `{ field, message }`를 담습니다. 업로드 크기를 넘으면 `413`입니다.

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
- 로그인 시도 횟수 제한·계정 잠금, 재시작 후에도 로그인 유지(세션 공유·저장)
- 실시간 채팅(웹소켓), 댓글 답글·좋아요·신고·페이지네이션, "내 방송" 모아 보기
- 후원 메시지, 익명 후원, 후원 알림(방송 화면 위 효과)
- 환불·결제 취소(확정된 후원 되돌리기), 확인 필요 상태의 자동 처리(사람이 DB와 토스 기록을 보고 판단)
- 방송자 정산, 받은 후원 모아 보기, 후원 순위, 후원 내역 페이지네이션
- 가상계좌 입금 처리(위젯에 보이는 카드 외 결제수단은 테스트로만 열어 둠)
- 결제 대기 주문의 만료·정리 작업(결제하지 않은 주문은 결제 대기로 남고 합계에 들어가지 않습니다)
- 실제(운영) 결제 키, 결제 서비스 가맹 심사
- 토스 웹훅을 로컬로 받기 위한 공개 주소(터널), 웹훅 서명 검증(본문 대신 조회 API로 재확인합니다. ADR-0008)
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
12. **재시작 후 유지·정리**: 휴대폰에서 방송을 시작해 둔 채(또는 시작 직후) 서비스를 멈췄다가 다시 띄웁니다(`-v` 없이). 녹화방송 두 개와 라이브 방송 기록이 그대로 있고, 녹화방송은 상세 화면에서 재생되며, 남아 있던 대기·방송 중은 **종료**로 정리되어 있으면 통과입니다.
    ```bash
    docker compose down
    ```
    ```bash
    docker compose up
    ```

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

화면 예시는 [docs/screenshots/stage-1](docs/screenshots/stage-1), [docs/screenshots/stage-2](docs/screenshots/stage-2), [docs/screenshots/stage-3](docs/screenshots/stage-3), [docs/screenshots/stage-4](docs/screenshots/stage-4)에 있습니다.

## 테스트

테스트는 TDD(red → green)로 작성했고, 사이클 기록은 [docs/tdd-log.md](docs/tdd-log.md)에 있습니다.

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm test
```

- 후원: 토스 결제 서비스는 테스트에서 가짜(`FakePaymentGateway`)로 바꿔 끼워 승인·조회의 성공·거절·통신 실패·"이미 처리된 결제"를 만들고, 승인 호출 횟수로 "다시 부르지 않음"을 확인합니다. 동시 확인 요청은 두 스레드로 같은 주문을 확인해 한 건만 확정되는지 봅니다. 실제 토스 연동 코드(`TossPaymentGateway`)는 PC에 띄운 가짜 HTTP 서버로 요청 모양(Basic 인증, 본문)과 응답 해석을 확인합니다. 프론트는 결제위젯 어댑터를 가짜로 바꿉니다.
- 백엔드: Spring Boot 앱 전체를 띄우고 REST API로만 검증합니다. 로그인은 테스트도 실제 회원가입·로그인 API로 받은 세션을 쓰고, 상태를 바꾸는 API마다 비로그인·소유자(작성자)·다른 회원 세 경우를 확인합니다. 쿠키 속성·CSRF 흐름은 실제 서버에 브라우저처럼 요청해 확인합니다. DB는 Testcontainers로 실제 PostgreSQL을 띄우므로 **Docker가 실행 중이어야** 합니다. MediaMTX의 인증·훅 요청은 테스트가 내부 엔드포인트를 직접 불러 흉내 내고, 시간(대기 시간 초과)은 테스트용 시계로 바꿔 끼웁니다.
- 프론트엔드: Vitest + React Testing Library로 라우터를 포함한 앱 전체를 렌더링하고, 네트워크 경계(`fetch`)와 미디어 어댑터(카메라·WebRTC)만 가짜로 바꿉니다. 로그인 상태도 스텁한 `/api/auth/me` 응답으로 만듭니다.

## 문서

- [GLOSSARY.md](GLOSSARY.md): 도메인 용어
- [docs/adr/](docs/adr/): 설계 결정 기록
- [docs/tdd-log.md](docs/tdd-log.md): TDD 사이클 기록
- [AGENTS.md](AGENTS.md): 작업 지침
