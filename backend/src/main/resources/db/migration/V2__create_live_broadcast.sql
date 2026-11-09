CREATE TABLE live_broadcast (
    id              BIGSERIAL    PRIMARY KEY,
    title           VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    stream_key_hash VARCHAR(64)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ
);

CREATE INDEX idx_live_broadcast_status ON live_broadcast (status);
