--liquibase formatted sql

--changeset museotekbox:0029-create-published-experiences
--comment A frozen copy of an experience, made on publish. Boxes play the latest copy, never the draft. Rows are never changed; media_ids and card_type_ids list what the copy uses, for the "still in use" checks
CREATE TABLE published_experiences
(
    id                 UUID         PRIMARY KEY,
    project_id         UUID         NOT NULL,
    version            INT          NOT NULL,
    doc_version        INT          NOT NULL,
    name               VARCHAR(255) NOT NULL,
    output             VARCHAR(15)  NOT NULL,
    start_card_type_id BIGINT       NOT NULL,
    content            JSONB        NOT NULL,
    media_ids          JSONB        NOT NULL,
    card_type_ids      JSONB        NOT NULL,
    published_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_by       VARCHAR(255),
    CONSTRAINT fk_published_experiences_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT uk_published_experiences_project_version UNIQUE (project_id, version)
);
--rollback DROP TABLE published_experiences;
