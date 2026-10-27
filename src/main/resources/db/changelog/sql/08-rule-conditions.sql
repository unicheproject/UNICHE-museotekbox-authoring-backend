--liquibase formatted sql

--changeset museotekbox:0027-rule-conditions-list
--comment A rule's "if" becomes a list of conditions that must all hold (AND), stored like effects; an empty list means always. Existing single conditions become one-item lists
ALTER TABLE rules RENAME COLUMN condition TO conditions;
UPDATE rules SET conditions = jsonb_build_array(conditions) WHERE conditions IS NOT NULL;
UPDATE rules SET conditions = '[]' WHERE conditions IS NULL;
ALTER TABLE rules
    ALTER COLUMN conditions SET DEFAULT '[]',
    ALTER COLUMN conditions SET NOT NULL;
--rollback ALTER TABLE rules ALTER COLUMN conditions DROP NOT NULL, ALTER COLUMN conditions DROP DEFAULT;
--rollback UPDATE rules SET conditions = CASE WHEN jsonb_array_length(conditions) = 0 THEN NULL ELSE conditions -> 0 END;
--rollback ALTER TABLE rules RENAME COLUMN conditions TO condition;
