# 04: 중복 승인 통지 방지와 웹훅

**What to build:** 같은 결제의 승인 통지가 몇 번, 어떤 경로로 와도 후원은 한 번만 확정된다. 확정 결과 화면을 새로고침하거나 확인 요청을 다시 보내면 결제 서비스를 다시 부르지 않고 같은 결과를 돌려준다. 동시에 온 두 요청도 한 건만 확정된다. 토스 웹훅을 받는 엔드포인트가 있고, 본문 대신 조회 API로 실제 상태를 확인해 같은 규칙으로 처리하며 위조된 통지는 무시한다. 모든 통지가 통지 기록에 처리 결과와 함께 남는다. 스펙: `.scratch/donations/spec.md`, 결정: ADR-0008.

03이 다듬은 확정 규칙 위에서 진행한다. 02가 아직 진행 중이면 병행한다.

> **변경(2026-11-10, 01 진행 중 사용자 결정)**: 결제창(`payment()`, API 개별 연동 키) 대신 **결제위젯**(`widgets()`, 문서용 공개 키 `test_gck_docs_…`/`test_gsk_docs_…`)을 쓴다. 공식 샘플의 공개 결제창 키가 토스 샌드박스에서 401로 거절되기 때문이다(ADR-0007). 이 문서의 "결제창"은 위젯의 "결제하기"로 이어지는 카드사 테스트 결제 화면을 뜻하고, 후원 시작은 "서버가 주문을 만든다 → 그 금액으로 위젯을 그린다 → 결제하기" 순서다. 위젯은 카드 외 결제수단(간편결제·계좌이체)도 테스트로 보여 주지만 확인은 카드로 한다.

**Blocked by:** 03

**Status:** resolved

- [x] 확정된 주문에 같은 확인 요청을 다시 보내면 `200` 같은 결과, 합계·건수 그대로, 게이트웨이 승인 호출 총 1회, 통지 기록에 "새로 확정"과 "이미 확정"
- [x] 같은 확인 요청을 동시에 두 개(스레드 두 개) 보내면 한 건만 확정되고 합계가 한 번만 늘어난다(주문 행 잠금)
- [x] 결제 키 유니크 제약으로 같은 결제 키가 두 후원에 붙지 않는다
- [x] 게이트웨이가 "이미 처리된 결제"를 주면 조회로 실제 상태를 확인해 완료·값 일치면 확정한다(승인 뒤 서버가 확정 전에 끊긴 상황)
- [x] `POST /api/payments/toss/webhook`: 로그인·CSRF 없이 받는다. 본문에서 결제 키만 얻어 조회하고, 완료·주문번호·금액이 맞으면 확정(이미 확정이면 "이미 확정"). 항상 `200`
- [x] 같은 웹훅 두 번 → 한 번만 적립, 통지 기록 두 줄
- [x] 확인 요청 뒤 웹훅, 웹훅 뒤 확인 요청 → 어느 쪽이든 한 번만 적립
- [x] 위조 웹훅(조회 결과가 완료가 아님, 주문번호·금액이 다름, 없는 결제 키) → 적립 안 함, 통지 기록 "무시"
- [x] 실패·취소된 주문에 온 웹훅 → 확정하지 않음
- [x] 백엔드 HTTP API 경계 테스트(가짜 게이트웨이, 동시성 테스트 포함)로 위를 확인한다
- [x] 프론트: `/donations/success` 새로고침·다시 렌더링해도 같은 확정 화면(앱 단위 테스트)
- [x] README용 확인 방법 초안을 Comments에 적는다: 확인 요청과 웹훅을 `curl`로 반복하는 명령, 통지 기록을 DB에서 조회하는 명령
- [x] 새 테스트는 새 파일에 둔다. 1~3단계·01·03 테스트는 모두 green
- [x] 모든 사이클이 기록되어 있다. 병행 중이라 `docs/tdd-log.md` 대신 이 파일의 `## TDD 기록`에 남기고, 병합할 때 옮긴다

## Comments

### 정한 것 (2026-11-11)

- **이미 확정**: 확인 요청은 주문 행을 잠그고(`findForUpdateByOrderId`, `PESSIMISTIC_WRITE` → `SELECT … FOR UPDATE`) 소유자 확인(403) 뒤, 확정이고 저장된 결제 키와 같으면 결제 서비스를 부르지 않고 저장된 결과를 `200`으로 돌려준다(`ALREADY_CONFIRMED`). 스펙 확정 규칙 3이 금액 확인(5)보다 앞이라, 확정된 주문은 요청 금액을 다시 보지 않는다(새로 적립하지 않으므로 해가 없음). 확정인데 결제 키가 다르면 `409`(`REJECTED`).
- **결제 키 유니크**: V4에 이미 `uq_donation_payment_key`가 있어 새 마이그레이션은 만들지 않았다. 코드에서도 승인 요청 전에 `existsByPaymentKey`로 확인해, 다른 후원에 붙은 결제 키의 확인 요청은 승인 호출 없이 `409`(`REJECTED`)로 거절하고 그 후원은 결제 대기로 둔다(그 주문의 진짜 결제가 나중에 올 수 있으므로 실패로 바꾸지 않는다). 서로 다른 두 주문이 같은 결제 키로 동시에 오는 경우는 DB 제약이 막고 `500`이 된다(실제 토스에선 생기지 않는 조합이라 따로 매핑하지 않음).
- **이미 처리된 결제**: 승인 응답이 `ALREADY_PROCESSED_PAYMENT`면 `gateway.find(paymentKey)`로 조회한 결과를 승인 결과 자리에 넣어 같은 규칙(완료·값 일치 → 확정, 완료·값 다름 → 확인 필요, 미완료·조회 거절 → 실패, 통신 실패 → 502)으로 처리한다.
- **동시 요청**: 행 잠금으로 두 번째 요청은 첫 트랜잭션이 끝날 때까지 기다렸다가 확정 상태를 보고 `ALREADY_CONFIRMED`로 끝난다. 잠금을 잡은 채로 결제 서비스를 부른다(승인 HTTP 호출 동안 그 주문 행만 잠김). 이 과제 규모에선 단순함을 택했다.
- **웹훅** `POST /api/payments/toss/webhook`(CSRF 예외에 추가, 로그인 불필요, 항상 `200`, 응답 본문 없음). 본문에서는 `eventType`, `data.paymentKey`, `data.orderId`만 읽는다.
  - `eventType`이 `PAYMENT_STATUS_CHANGED`가 아니거나 결제 키가 없으면 조회 없이 `IGNORED`.
  - 결제 키로 조회 → 조회 거절(없는 결제 키) `IGNORED`, 통신 실패 `REJECTED`(결제 대기 유지, 다음 통지에서 다시 확인).
  - 조회 결과의 주문번호로 후원을 잠가 찾는다. 본문 주문번호와 조회 결과가 다르면, 후원이 없으면, 완료가 아니면, 금액이 다르면 `IGNORED`(위조로 본다. 확인 요청과 달리 확인 필요로 바꾸지 않는다: 본문이 위조일 수 있는 통지로 후원 상태를 바꾸지 않기 위해).
  - 확정·같은 결제 키 `ALREADY_CONFIRMED`, 확정·다른 결제 키 `IGNORED`, 실패·취소·확인 필요 `REJECTED`(상태 그대로), 다른 후원에 붙은 결제 키 `IGNORED`, 방송 삭제·방송자 변경은 확인 요청과 같이 실패 + `REJECTED`, 그 밖은 확정 `NEW_CONFIRMED`.
  - 웹훅은 이미 확정된 주문이어도 먼저 조회한다(조회는 읽기라 중복 적립 위험이 없고, 규칙을 한 갈래로 유지).
- 프론트는 바꾸지 않았다. 성공 화면은 서버가 준 결과를 그대로 그리므로, 서버가 같은 결과를 돌려주면 새로고침해도 같은 화면이다(테스트로 고정).
- `DonationConfirmOutcomeApiTest`의 03 테스트 "확정된 후원은 다시 확인한 요청을 결제 서비스가 거절해도 확정 그대로다"를 인계대로 "확정된 후원을 다시 확인하면 결제 서비스를 부르지 않고 확정 그대로다"로 바꾸고, 기대값을 `ALREADY_CONFIRMED`와 승인 호출 1회로 다듬었다.

### 남은 판단 거리

- 웹훅 조회 통신 실패도 `200`이라 토스가 재전송하지 않는다(스펙 "항상 200"). 재전송을 받으려면 이 경우만 `5xx`로 바꾸는 선택이 있다.
- 웹훅에서 "완료인데 금액이 다름"은 무시로 두었다. 결제 서비스 조회 결과 자체가 다르다는 것이라 확인 요청처럼 확인 필요로 바꿔도 된다.

### README용 확인 방법 초안 (05에서 다듬기)

전체 실행(`docker compose up --build`) 기준, PC `http://localhost:3000`. 확인 요청은 로그인 세션과 CSRF 토큰이 필요하다. 먼저 화면에서 테스트 카드로 결제를 끝내 성공 화면 주소의 `paymentKey`·`orderId`·`amount`를 얻는다.

```bash
# 1) 세션·CSRF 쿠키 받기와 로그인(후원한 회원으로)
curl -s -c jar.txt http://localhost:3000/api/auth/me > /dev/null
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' jar.txt)
curl -s -b jar.txt -c jar.txt -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
  -d '{"loginId":"<아이디>","password":"<비밀번호>"}' http://localhost:3000/api/auth/login
XSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' jar.txt)

# 2) 같은 확인 요청 두 번: 둘 다 200, 같은 본문
for i in 1 2; do
  curl -s -b jar.txt -H "X-XSRF-TOKEN: $XSRF" -H 'Content-Type: application/json' \
    -d '{"paymentKey":"<paymentKey>","orderId":"<orderId>","amount":<amount>}' \
    http://localhost:3000/api/donations/confirm; echo
done

# 3) 같은 웹훅 두 번(로그인·CSRF 없음): 둘 다 200
for i in 1 2; do
  curl -s -o /dev/null -w '%{http_code}\n' -H 'Content-Type: application/json' \
    -d '{"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"<paymentKey>","orderId":"<orderId>","status":"DONE"}}' \
    http://localhost:3000/api/payments/toss/webhook
done

# 4) 위조 웹훅(없는 결제 키): 200, 적립 안 됨
curl -s -o /dev/null -w '%{http_code}\n' -H 'Content-Type: application/json' \
  -d '{"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"forged","orderId":"<orderId>","status":"DONE"}}' \
  http://localhost:3000/api/payments/toss/webhook

# 5) 합계는 한 번만 늘었다
curl -s http://localhost:3000/api/recordings/<방송 id>/donations

# 6) 통지 기록: NEW_CONFIRMED 1줄 + ALREADY_CONFIRMED 3줄 + IGNORED 1줄
docker compose exec db psql -U chzzz -d chzzz -c \
  "SELECT id, payment_key, source, result, detail, received_at FROM payment_notification ORDER BY id DESC LIMIT 10;"
```

- 로그인 본문은 `{ loginId, password }`(`AuthController.LoginRequest`). 로그인 뒤 CSRF 토큰 쿠키가 바뀌므로 다시 읽는다.
- 카드 결제 없이 확인하려면 3)~6)의 웹훅만으로는 확정되지 않는다(실제 토스 조회가 `NOT_FOUND_PAYMENT`를 주므로 `IGNORED`). 위조 웹훅이 무시되는 것만 카드 없이 확인할 수 있다.

### 확인한 것

- 백엔드 `./gradlew test` 202개 green(새 17개: `DonationDuplicateApiTest` 4, `DonationAlreadyProcessedApiTest` 2, `DonationWebhookApiTest` 11).
- 프론트 `npm test` 119개 green(새 `DonationSuccessRefresh.test.tsx` 1), `npm run build`·`npm run lint` 통과. 이 worktree의 `node_modules`에 토스 SDK가 없어 `npm ci`로 다시 설치했다.
- 직접 확인(실행 중인 서버·브라우저·실제 토스)은 하지 않았다.

## TDD 기록

| 테스트 이름 | red 이유 | green 변경 | 리팩터 |
| --- | --- | --- | --- |
| 확정된 주문에 같은 확인 요청을 다시 보내면 승인을 다시 요청하지 않고 같은 결과를 돌려주며 이미 확정으로 기록한다 | 승인 호출 2회 | 확정·같은 결제 키면 승인 호출 없이 `ALREADY_CONFIRMED` | `ConfirmOutcome`이 통지 결과를 직접 가짐, 03 테스트 기대값 갱신 |
| 이미 처리된 결제라는 답을 받으면 조회해서 완료이고 값이 같으면 확정한다 | 상태 `FAILED` | `ALREADY_PROCESSED_PAYMENT`면 `gateway.find` 결과로 같은 규칙 | - |
| 조회 결과의 금액이 다르면 적립하지 않고 확인 필요로 남긴다 | 상태 `FAILED` | (위와 같은 변경) | - |
| 같은 확인 요청이 동시에 두 개 와도 한 건만 새로 확정되고 승인은 한 번만 요청한다 | 승인 호출 2회 | `findForUpdateByOrderId`(`PESSIMISTIC_WRITE`) | - |
| 다른 후원에 이미 붙은 결제 키로 확인하면 승인을 요청하지 않고 409이며 그 후원은 결제 대기 그대로다 | 500(유니크 위반) | 승인 전 `existsByPaymentKey` → 409 | - |
| 저장 구조에서도 같은 결제 키는 두 후원에 붙지 않는다 | red 아님(V4의 유니크 제약, 확인용) | - | - |
| (웹훅 10개) 확인 요청 없이 웹훅만 와도 확정 / 같은 웹훅 두 번 / 확인 요청 뒤 웹훅 / 웹훅 뒤 확인 요청 / 완료 아님 / 주문번호 다름 / 금액 다름 / 없는 결제 키 / 다른 이벤트 / 실패·취소된 주문 | 모두 403(CSRF, 경로 없음) | 웹훅 엔드포인트, CSRF 예외, `handleWebhook`(조회 → 같은 규칙) | 방송자 확인을 `failIfStreamerChanged`로 분리해 두 경로가 공유 |
| 결제 서비스에 조회하지 못하면 적립하지 않고 결제 대기로 남겨 다음 통지에서 확정한다 | red 아님(웹훅 구현과 함께 들어간 분기를 뒤늦게 고정) | - | - |
| (프론트) 다시 열어도 같은 확인 요청을 보내고 서버가 돌려준 같은 확정 화면을 보여 준다 | red 아님(화면은 서버 결과를 그대로 그림, 회귀 방지용) | - | - |
