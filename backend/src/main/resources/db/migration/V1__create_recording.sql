CREATE TABLE recording (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(100) NOT NULL,
    source_type  VARCHAR(20)  NOT NULL,
    source_value TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_recording_created_at ON recording (created_at DESC);
