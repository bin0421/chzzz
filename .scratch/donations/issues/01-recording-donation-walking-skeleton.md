# 01: 녹화방송에 테스트 결제 후원 한 건 확정 (골격)

**What to build:** 로그인한 회원이 다른 회원의 녹화방송 상세 화면에서 금액을 고르고 "후원하기"를 누르면 토스페이먼츠 결제창(카드, 테스트 모드)이 열린다. 테스트 카드로 결제하고 돌아오면 서버가 주문과 금액을 확인하고 토스 승인 API로 승인을 받은 뒤 후원을 **확정**하고, 그 녹화방송의 후원 내역과 합계에 나타난다. 스펙: `.scratch/donations/spec.md`, 결정: ADR-0007, ADR-0008.

02~04가 이 위에서 진행되므로 이 티켓에서 공용 기반을 마련한다.
- Flyway `V4` 하나에 4단계 스키마 전체(후원, 통지 기록). 02~04는 새 마이그레이션을 만들지 않는다
- 결제 게이트웨이 경계(승인 요청, 결제 조회)와 실제 토스 구현(시크릿 키 Basic 인증, `RestClient`), 테스트용 가짜(테스트마다 결과 지정, 호출 횟수 기록)
- 결제 설정(클라이언트 키·시크릿 키·API 주소 환경변수, 클라이언트 키를 내려 주는 설정 API), compose·`.env.example` 반영
- 후원 시작(녹화방송·라이브 방송 경로 모두, 이 티켓에서는 정상 경로만), 확인(정상 경로만), 방송별 내역·합계 API
- 후원 상태(결제 대기·확정·실패·취소·확인 필요)와 통지 기록 엔티티. 확인 요청은 통지 기록에 한 줄 남긴다
- 프론트: 결제창 어댑터(실제 토스 SDK, npm 버전 고정)와 가짜, 앱 주입. 후원 영역 컴포넌트(녹화방송 상세에 한 줄로 붙임), `/donations/success` 화면

> **변경(2026-11-10, 01 진행 중 사용자 결정)**: 결제창(`payment()`, API 개별 연동 키) 대신 **결제위젯**(`widgets()`, 문서용 공개 키 `test_gck_docs_…`/`test_gsk_docs_…`)을 쓴다. 공식 샘플의 공개 결제창 키가 토스 샌드박스에서 401로 거절되기 때문이다(ADR-0007). 이 문서의 "결제창"은 위젯의 "결제하기"로 이어지는 카드사 테스트 결제 화면을 뜻하고, 후원 시작은 "서버가 주문을 만든다 → 그 금액으로 위젯을 그린다 → 결제하기" 순서다. 위젯은 카드 외 결제수단(간편결제·계좌이체)도 테스트로 보여 주지만 확인은 카드로 한다.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] 토스 문서에서 공개 테스트 키(클라이언트·시크릿), 테스트 카드 결제 방법, 실패 재현 방법, 사용자 취소 오류 코드, 승인·조회 API 모양, 웹훅 본문 모양을 확인해 Comments에 출처와 함께 적는다
- [x] Flyway `V4`가 후원 테이블(주문번호 유니크, 결제 키 유니크·nullable, 방송 종류+식별자, 후원자·방송자, 금액, 상태, 실패 코드·메시지, 생성·확정 시각)과 통지 기록 테이블을 만든다. `V1`~`V3`는 그대로이고 기존 데이터가 남는다
- [x] `GET /api/payments/config`가 클라이언트 키를 준다(공개)
- [x] `POST /api/recordings/{id}/donations` JSON `{ amount }`: 로그인한 다른 회원이면 `201 { orderId, amount, orderName, streamer: { id, nickname }, customerKey }`이고 결제 대기 후원이 저장된다(방송자 = 그 순간의 소유자)
- [x] `POST /api/live-broadcasts/{id}/donations`도 같은 계약으로 동작한다(화면은 02)
- [x] `POST /api/donations/confirm` JSON `{ paymentKey, orderId, amount }`: 자기 결제 대기 주문이고 금액이 같으면 게이트웨이 승인을 부르고, 결과가 완료이며 금액·주문번호가 같으면 확정(결제 키·확정 시각 저장)해 `200 { id, status, amount, streamer, broadcast: { type, id }, confirmedAt }`. 통지 기록에 "새로 확정"이 남는다
- [x] `GET /api/recordings/{id}/donations`, `GET /api/live-broadcasts/{id}/donations`: 공개, 확정만, 최신순 `{ totalAmount, count, items }`
- [x] 위 동작이 백엔드 HTTP API 경계 테스트로 검증된다. 게이트웨이는 테스트 설정에서 가짜로 바꾸고, 확정 뒤 내역·합계와 승인 호출 1회를 확인한다
- [x] compose가 백엔드에 `TOSS_CLIENT_KEY`·`TOSS_SECRET_KEY`·`TOSS_API_BASE_URL`을 넘기고 `.env.example`에 공개 테스트 키 기본값과 설명이 있다
- [x] 프론트: 녹화방송 상세 아래 후원 영역에 합계·건수·내역과 금액 입력·"<방송자>님에게 <금액>원 후원하기". 누르면 후원 시작 요청(CSRF 헤더) 후 결제창 어댑터가 서버가 준 주문번호·금액·주문명·성공/실패 주소로 호출된다(앱 단위 테스트, 가짜 어댑터)
- [x] 프론트: `/donations/success?paymentKey&orderId&amount`가 확인 요청을 보내고 "후원이 확정되었습니다"와 방송자·금액, 방송으로 돌아가는 링크를 보여 준다(앱 단위 테스트)
- [x] 1~3단계 테스트는 모두 green
- [ ] 실제 브라우저(PC, 전체 실행 또는 개발 실행)에서 토스 샌드박스 결제창으로 테스트 결제 한 건을 확정하고, 내역·합계에 나타나는 것을 확인했다(인터넷 필요) — **하지 않음(사용자 결정, 아래 Comments). 05에서 다시 정한다**
- [x] 모든 사이클이 `docs/tdd-log.md`에 기록되어 있다

## Comments

### 토스 문서에서 확인한 사실 (2026-11-10)

- 결제창(v2 SDK `payment()`·`requestPayment`, 카드/간편결제 통합결제창)은 **API 개별 연동 키**(`test_ck_…`/`test_sk_…`)를 쓴다. 결제위젯 키(`test_gck_…`/`test_gsk_…`)와 세트가 다르다. 출처: https://docs.tosspayments.com/reference/using-api/api-keys, https://docs.tosspayments.com/guides/v2/payment-window/integration
- 가입 없이 쓸 수 있는 공개 테스트 키(토스 공식 샘플): 클라이언트 `test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq`, 시크릿 `test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R`. 출처: https://github.com/tosspayments/tosspayments-sample (`express-javascript/public/payment/checkout.html`, `express-javascript/server.js`). 테스트 키라 실제 돈은 나가지 않는다.
- SDK: `TossPayments(clientKey)` → `tossPayments.payment({ customerKey })` → `payment.requestPayment({ method: "CARD", amount: { currency: "KRW", value }, orderId, orderName, successUrl, failUrl, … })`. npm `@tosspayments/tosspayments-sdk` 최신 `2.10.0`(2026-10-20 배포), 타입 포함. 모바일에서는 결제창이 항상 페이지 이동으로 열린다(iframe 금지).
- 성공 복귀: `successUrl?orderId=…&paymentKey=…&amount=…`. 실패 복귀: `failUrl?code=…&message=…&orderId=…`. 사용자 취소 코드 `PAY_PROCESS_CANCELED`("결제가 사용자에 의해 취소되었습니다."), 그 밖 `PAY_PROCESS_ABORTED`, `REJECT_CARD_COMPANY` 등. 출처: 위 연동 가이드, https://docs.tosspayments.com/reference/error-codes
- 승인 API: `POST https://api.tosspayments.com/v1/payments/confirm`, 본문 `{ paymentKey, orderId, amount }`, 헤더 `Authorization: Basic base64("<시크릿 키>:")`. 성공 200 + Payment 객체(`status`가 `DONE`, `orderId`, `totalAmount`). 이미 승인된 결제를 다시 승인하면 400 `ALREADY_PROCESSED_PAYMENT`. 없는 결제 404 `NOT_FOUND_PAYMENT`.
- 조회 API: `GET https://api.tosspayments.com/v1/payments/{paymentKey}`(Payment 객체). 출처: https://docs.tosspayments.com/reference
- 웹훅: `PAYMENT_STATUS_CHANGED`, 본문 `{ eventType, createdAt, data: Payment 객체(paymentKey, orderId, status, …) }`. 결제창을 닫으면 상태가 바뀌지 않아 웹훅이 오지 않는다. 결제창 30분·인증 후 10분 안에 승인하지 않으면 `EXPIRED`. 출처: https://docs.tosspayments.com/reference/using-api/webhook-events
- 웹훅 서명: `tosspayments-webhook-signature` 헤더로 HMAC-SHA256 서명을 검증할 수 있지만, 검증에 쓰는 **보안 키는 내 개발자센터 계정에 발급**되는 값이라 공개 테스트 키 세트에는 없다. 그래서 ADR-0008대로 웹훅 본문 대신 조회 API를 믿는다(서명 검증은 범위 밖). `tosspayments-webhook-transmission-id`는 매번 새로 발급되어 중복 처리에 쓰면 안 된다.
- 테스트 환경: 카드는 유효한 카드 번호를 넣어도 실제로 결제되지 않는다(가상 승인). 에러 재현은 API 호출에 `TossPayments-Test-Code: <에러 코드>` 헤더(테스트 시크릿 키에서만 동작). 예: 승인 API에 `REJECT_CARD_PAYMENT`. 테스트 API는 분당 100건 제한. 출처: https://docs.tosspayments.com/guides/v2/get-started/environment
- 03·05에 넘길 것: "실패" 직접 확인은 (1) 결제창에서 인증을 실패시키는 방법과 (2) 승인 API에 테스트 코드 헤더를 붙이는 방법이 있다. (2)를 쓰려면 백엔드가 테스트 키일 때만 승인 요청에 헤더를 붙이는 설정이 필요하다(03에서 결정).

### 실제 샌드박스 확인에서 발견한 것 (2026-11-10)

- 공식 샘플의 공개 API 개별 연동 키(`test_ck_D5GeP…`)로 결제창(`payment().requestPayment`)을 열면 SDK가 `UNKNOWN` 오류를 낸다. 원인은 토스 샌드박스가 그 키를 `401 UNAUTHORIZED_KEY`("인증되지 않은 시크릿 키 혹은 클라이언트 키 입니다.")로 거절하기 때문(`apigw-sandbox.tosspayments.com/.../px-payment-parameters`). 샘플의 키가 더 이상 유효하지 않다.
- 결제창(`payment()`)은 API 개별 연동 키(`test_ck_`)만 받는다. 문서용 결제 키(`test_gck_docs_…`)를 넣으면 `NOT_SUPPORTED_WIDGET_KEY`.
- 문서용 공개 키 `test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm`(시크릿 `test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6`, 같은 샘플)으로는 **결제위젯**(`widgets()` → `setAmount` → `renderPaymentMethods`·`renderAgreement` → `requestPayment`)이 가입 없이 동작한다. "테스트 환경이에요. 실제로 결제되지 않아요." 안내와 함께 결제수단 목록이 페이지 안에 그려지고, 카드사(국민)를 고르면 카드사 테스트 결제창까지 열린다(카드 정보 입력 단계 전에서 멈춤).
- 결정 필요(사용자): (a) 결제위젯 + 문서 공개 키로 바꾼다 / (b) 사용자가 개발자센터에 가입해 받은 내 테스트 키(`test_ck_`/`test_sk_`)로 결제창을 유지한다.

### 결제위젯으로 바꾼 뒤 확인한 것 (2026-11-11)

- 개발 실행(임시 DB `chzzz_dev4` + 백엔드 8081 + Vite 5174)과 앱 브라우저 패널에서, 로그인한 다른 회원이 녹화방송 상세의 "방송자A님에게 1,000원 후원하기" → 서버가 결제 대기 후원을 만들고 → 그 금액으로 결제위젯이 그려짐("테스트 환경이에요. 실제로 결제되지 않아요.", 결제수단 목록, 약관 동의, "1,000원 결제하기").
- 카드 정보 입력 단계부터는 에이전트가 하지 않는다. 사용자는 "테스트라 실제로 결제되지 않는다는 말을 믿을 수 없다"며 실제 카드 입력을 하지 않기로 했다. 그래서 **샌드박스에서 결제를 끝까지 마쳐 확정되는 것은 확인하지 못했다.**
- 대신 카드 없이 확인한 것:
  - 문서용 공개 시크릿 키(`test_gsk_docs_…`)로 토스 실제 API를 부르면 인증이 통과한다. 없는 결제 키 조회 `404 NOT_FOUND_PAYMENT`, 승인 `404 NOT_FOUND_PAYMENT_SESSION`. 틀린 키는 `401 UNAUTHORIZED_KEY`. 즉 키·주소·Basic 인증 형식이 맞다.
  - 실제로 동작 중인 백엔드에 결제하지 않은 주문으로 위조한 성공 요청(`paymentKey=forged-payment-key`)을 보내면, 토스가 승인하지 않아 후원은 결제 대기로 남고 합계·건수는 0 그대로다(화면 성공 표시만으로 확정되지 않음). 01의 정상 경로만 있는 지금은 응답이 200이고 상태가 `AWAITING_PAYMENT`다. 거절 응답(실패로 바꾸기, 오류 코드)은 03에서 만든다.
- 결과: 백엔드 159개, 프론트 96개 테스트 green, 빌드·린트 통과. 임시 DB와 개발 서버는 정리했다.
