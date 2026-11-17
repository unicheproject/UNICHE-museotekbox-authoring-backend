--liquibase formatted sql

--changeset museotekbox:0039-box-keys
--comment A Box's own login key, made by a manager in the CMS and installed on the Box. key_hash: SHA-256 (hex) of the long random key, which is shown once and never stored. One key per Box: a new key replaces the old one at once. Empty for a Box without a key, which can't log in
ALTER TABLE boxes ADD COLUMN key_hash VARCHAR(64);
ALTER TABLE boxes ADD COLUMN key_created_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE boxes ADD CONSTRAINT uk_boxes_key_hash UNIQUE (key_hash);
--rollback ALTER TABLE boxes DROP CONSTRAINT uk_boxes_key_hash;
--rollback ALTER TABLE boxes DROP COLUMN key_created_at;
--rollback ALTER TABLE boxes DROP COLUMN key_hash;
