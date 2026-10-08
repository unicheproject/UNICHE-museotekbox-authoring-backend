--liquibase formatted sql

--changeset museotekbox:0022-create-comments
--comment Comments testers leave on an experience: a thread start (parent_id null) or a one-level reply. scene_key null = the whole visit. Never deleted when scenes change: the frontend falls back to "the whole visit"
CREATE TABLE comments
(
    id             UUID         PRIMARY KEY,
    project_id     UUID         NOT NULL,
    parent_id      UUID,
    scene_key      VARCHAR(64),
    step_key       VARCHAR(64),
    body           TEXT         NOT NULL,
    author_subject VARCHAR(255) NOT NULL,
    author_name    VARCHAR(255) NOT NULL,
    resolved       BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_by    VARCHAR(255),
    resolved_at    TIMESTAMP(6) WITH TIME ZONE,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_comments_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments (id)
);
CREATE INDEX ix_comments_project_created ON comments (project_id, created_at);
CREATE INDEX ix_comments_parent ON comments (parent_id);
--rollback DROP TABLE comments;

--changeset museotekbox:0023-add-comment-status-changed-at
--comment When a thread was last resolved or reopened, so the frontend's "anything new since" refresh also notices a reopen (resolved_at is cleared then)
ALTER TABLE comments
    ADD COLUMN status_changed_at TIMESTAMP(6) WITH TIME ZONE;
--rollback ALTER TABLE comments DROP COLUMN status_changed_at;
