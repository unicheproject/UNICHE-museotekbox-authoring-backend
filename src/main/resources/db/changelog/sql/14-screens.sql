--liquibase formatted sql

--changeset museotekbox:0034-create-screens
--comment A museum PC's browser that shows DISPLAY experiences through the player project. key_hash: SHA-256 (hex) of its long random key, which is shown once and never stored. A removed screen keeps its row with revoked_at set
CREATE TABLE screens
(
    id           UUID PRIMARY KEY,
    org_id       UUID                        NOT NULL,
    name         VARCHAR(255)                NOT NULL,
    key_hash     VARCHAR(64)                 NOT NULL,
    created_by   VARCHAR(255),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP(6) WITH TIME ZONE,
    revoked_at   TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_screens_key_hash UNIQUE (key_hash)
);
CREATE INDEX ix_screens_org ON screens (org_id);
--rollback DROP TABLE screens;

--changeset museotekbox:0035-create-screen-setup-codes
--comment One-time code a manager creates with "Add screen"; the player project redeems it once for a screen and its key. code_hash: SHA-256 (hex) of the code. screen_id: the screen it became
CREATE TABLE screen_setup_codes
(
    id           UUID PRIMARY KEY,
    org_id       UUID                        NOT NULL,
    screen_name  VARCHAR(255)                NOT NULL,
    code_hash    VARCHAR(64)                 NOT NULL,
    created_by   VARCHAR(255),
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at      TIMESTAMP(6) WITH TIME ZONE,
    cancelled_at TIMESTAMP(6) WITH TIME ZONE,
    screen_id    UUID,
    CONSTRAINT uk_screen_setup_codes_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_screen_setup_codes_screen FOREIGN KEY (screen_id) REFERENCES screens (id)
);
CREATE INDEX ix_screen_setup_codes_org ON screen_setup_codes (org_id);
--rollback DROP TABLE screen_setup_codes;
