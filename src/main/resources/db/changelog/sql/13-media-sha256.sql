--liquibase formatted sql

--changeset museotekbox:0033-add-media-sha256
--comment SHA-256 of the file, hex, so the player project can check the copy it caches. Calculated on upload; files uploaded before this are filled in once after startup (MediaChecksumBackfill)
ALTER TABLE media
    ADD COLUMN sha256 VARCHAR(64);
--rollback ALTER TABLE media DROP COLUMN sha256;
