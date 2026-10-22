--liquibase formatted sql

--changeset museotekbox:0025-create-experience-templates
--comment A ready-made starting point for the wizard: one template type plus its default settings; org_id empty = for every organisation
CREATE TABLE experience_templates
(
    id          UUID          PRIMARY KEY,
    org_id      UUID,
    type        VARCHAR(31)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description VARCHAR(1000),
    defaults    JSONB         NOT NULL DEFAULT '{}',
    created_by  VARCHAR(255),
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_experience_templates_org ON experience_templates (org_id);
--rollback DROP TABLE experience_templates;

--changeset museotekbox:0026-seed-global-experience-templates
--comment One global template per type; defaults {} = the frontend's built-in defaults until their shape is agreed. Never put an organisation's ids (card types, media) in a global template.
INSERT INTO experience_templates (id, org_id, type, name, description, defaults, created_by, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'SCAN_AND_DISCOVER', 'Scan & Discover', 'Each object tells its own story', '{}', NULL, now(), now()),
       ('00000000-0000-0000-0000-000000000002', NULL, 'QUIZ', 'Quiz', 'Questions answered by scanning', '{}', NULL, now(), now()),
       ('00000000-0000-0000-0000-000000000003', NULL, 'TREASURE_HUNT', 'Treasure hunt', 'Objects found in the right order', '{}', NULL, now(), now()),
       ('00000000-0000-0000-0000-000000000004', NULL, 'GUIDED_PRESENTATION', 'Guided presentation', 'A story told in a fixed order', '{}', NULL, now(), now()),
       ('00000000-0000-0000-0000-000000000005', NULL, 'FROM_SCRATCH', 'Start from scratch', 'One empty scene, nothing else', '{}', NULL, now(), now());
--rollback DELETE FROM experience_templates WHERE org_id IS NULL;
