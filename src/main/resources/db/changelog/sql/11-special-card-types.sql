--liquibase formatted sql

--changeset museotekbox:0030-add-scan-object-type-flags
--comment universal: the type works in any experience (Right/Wrong, answers A/B/C), never as a start card. is_exit: the org's Exit card, ends any visit, never a start card or in a rule. scan_objects.reusable is no longer used
ALTER TABLE scan_object_types
    ADD COLUMN universal BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN is_exit   BOOLEAN NOT NULL DEFAULT false;
--rollback ALTER TABLE scan_object_types DROP COLUMN universal, DROP COLUMN is_exit;

--changeset museotekbox:0031-one-exit-card-type-per-org
--comment At most one Exit card type per org (the app checks first and answers 409; this catches races)
CREATE UNIQUE INDEX uk_scan_object_types_exit_per_org ON scan_object_types (org_id) WHERE is_exit;
--rollback DROP INDEX uk_scan_object_types_exit_per_org;
