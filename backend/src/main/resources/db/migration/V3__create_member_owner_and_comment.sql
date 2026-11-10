-- 3단계: 회원, 방송 소유자, 댓글. 기존 방송은 소유자 없음(NULL)으로 남는다.
CREATE TABLE member (
    id            BIGSERIAL    PRIMARY KEY,
    login_id      VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(20)  NOT NULL,
    -- 닉네임 중복 판정용: 앞뒤 공백을 지우고 소문자로 바꾼 값
    nickname_key  VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_member_login_id UNIQUE (login_id),
    CONSTRAINT uq_member_nickname_key UNIQUE (nickname_key)
);

ALTER TABLE recording ADD COLUMN owner_id BIGINT REFERENCES member (id);
ALTER TABLE live_broadcast ADD COLUMN owner_id BIGINT REFERENCES member (id);

CREATE INDEX idx_recording_owner_id ON recording (owner_id);
CREATE INDEX idx_live_broadcast_owner_id ON live_broadcast (owner_id);

-- 녹화방송과 라이브 방송 모두에 달리므로 방송 종류 + 식별자로 가리킨다. 방송 삭제 시 서비스가 함께 지운다.
CREATE TABLE comment (
    id             BIGSERIAL    PRIMARY KEY,
    broadcast_type VARCHAR(20)  NOT NULL,
    broadcast_id   BIGINT       NOT NULL,
    author_id      BIGINT       NOT NULL REFERENCES member (id),
    content        VARCHAR(500) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ
);

CREATE INDEX idx_comment_broadcast ON comment (broadcast_type, broadcast_id, created_at);
