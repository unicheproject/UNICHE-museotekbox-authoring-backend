--liquibase formatted sql

--changeset museotekbox:0037-drop-screens
--comment Screens and their setup codes are replaced by display sessions (0036): a display is opened from the CMS for one Box, with no setup code. 0034 and 0035 never ran on a shared database, so no data is lost
DROP TABLE screen_setup_codes;
DROP TABLE screens;
--rollback CREATE TABLE screens (id UUID PRIMARY KEY, org_id UUID NOT NULL, name VARCHAR(255) NOT NULL, key_hash VARCHAR(64) NOT NULL, created_by VARCHAR(255), created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, last_seen_at TIMESTAMP(6) WITH TIME ZONE, revoked_at TIMESTAMP(6) WITH TIME ZONE, CONSTRAINT uk_screens_key_hash UNIQUE (key_hash));
--rollback CREATE INDEX ix_screens_org ON screens (org_id);
--rollback CREATE TABLE screen_setup_codes (id UUID PRIMARY KEY, org_id UUID NOT NULL, screen_name VARCHAR(255) NOT NULL, code_hash VARCHAR(64) NOT NULL, created_by VARCHAR(255), created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, used_at TIMESTAMP(6) WITH TIME ZONE, cancelled_at TIMESTAMP(6) WITH TIME ZONE, screen_id UUID, CONSTRAINT uk_screen_setup_codes_code_hash UNIQUE (code_hash), CONSTRAINT fk_screen_setup_codes_screen FOREIGN KEY (screen_id) REFERENCES screens (id));
--rollback CREATE INDEX ix_screen_setup_codes_org ON screen_setup_codes (org_id);
