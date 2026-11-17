--liquibase formatted sql

--changeset museotekbox:0036-create-display-sessions
--comment A TV or monitor next to one Box, opened from the CMS by a manager ("Open display"). token_hash: SHA-256 (hex) of its long random token, which is shown once and never stored. expires_at moves forward with use. A revoked display keeps its row with revoked_at set; deleting the Box deletes its rows
CREATE TABLE display_sessions
(
    id           UUID PRIMARY KEY,
    box_id       BIGINT                      NOT NULL,
    token_hash   VARCHAR(64)                 NOT NULL,
    created_by   VARCHAR(255),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP(6) WITH TIME ZONE,
    expires_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    revoked_at   TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_display_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_display_sessions_box FOREIGN KEY (box_id) REFERENCES boxes (id)
);
CREATE INDEX ix_display_sessions_box ON display_sessions (box_id);
--rollback DROP TABLE display_sessions;
