--liquibase formatted sql

--changeset museotekbox:0020-create-media
--comment One uploaded file of an organisation's media library (picture, video or sound); the bytes live on disk under stored_filename, relative to MEDIA_UPLOAD_DIR
CREATE TABLE media
(
    id              UUID         PRIMARY KEY,
    org_id          UUID         NOT NULL,
    kind            VARCHAR(15)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    uploaded_by     VARCHAR(255),
    uploaded_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_media_stored_filename UNIQUE (stored_filename)
);
CREATE INDEX ix_media_org ON media (org_id);
--rollback DROP TABLE media;
