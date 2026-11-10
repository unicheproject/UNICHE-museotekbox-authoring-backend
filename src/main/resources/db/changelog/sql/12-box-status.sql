--liquibase formatted sql

--changeset museotekbox:0032-add-box-status-report
--comment What the player project last reported about a Box. last_seen_at: when we last heard about it at all. status_at: when the applied report happened, so a late report never overwrites a newer one. current_scene_key: the scene of the running experience
ALTER TABLE boxes
    ADD COLUMN last_seen_at      TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN status_at         TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN current_scene_key VARCHAR(255);
--rollback ALTER TABLE boxes DROP COLUMN last_seen_at, DROP COLUMN status_at, DROP COLUMN current_scene_key;
