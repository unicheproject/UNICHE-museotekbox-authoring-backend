--liquibase formatted sql

--changeset museotekbox:0028-add-rule-target-scene-keys
--comment GO_TO_RANDOM: the scenes the Box picks one from at random (e.g. the dice face that lets the system choose); null for every other destination
ALTER TABLE rules
    ADD COLUMN target_scene_keys JSONB;
--rollback ALTER TABLE rules DROP COLUMN target_scene_keys;
