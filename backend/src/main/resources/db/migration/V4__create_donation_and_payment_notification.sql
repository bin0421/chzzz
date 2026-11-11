-- 4단계: 후원과 승인 통지 기록. 방송에는 외래 키를 걸지 않는다(방송을 지워도 돈이 오간 기록은 남긴다).
CREATE TABLE donation (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        VARCHAR(64)  NOT NULL,
    broadcast_type  VARCHAR(20)  NOT NULL,
    broadcast_id    BIGINT       NOT NULL,
    donor_id        BIGINT       NOT NULL REFERENCES member (id),
    streamer_id     BIGINT       NOT NULL REFERENCES member (id),
    amount          BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    -- 같은 결제가 두 후원에 붙지 않게 한다(ADR-0008). 확정 전에는 비어 있다.
    payment_key     VARCHAR(200),
    failure_code    VARCHAR(100),
    failure_message VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    CONSTRAINT uq_donation_order_id UNIQUE (order_id),
    CONSTRAINT uq_donation_payment_key UNIQUE (payment_key)
);

CREATE INDEX idx_donation_broadcast ON donation (broadcast_type, broadcast_id, status);

-- 받은 승인 통지(확인 요청·웹훅)를 한 줄씩 남긴다. 중복 통지가 "이미 확정"으로 끝났는지 보여 주는 근거다.
CREATE TABLE payment_notification (
    id          BIGSERIAL    PRIMARY KEY,
    payment_key VARCHAR(200),
    order_id    VARCHAR(64),
    source      VARCHAR(20)  NOT NULL,
    result      VARCHAR(20)  NOT NULL,
    detail      VARCHAR(500),
    received_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_payment_notification_payment_key ON payment_notification (payment_key);
