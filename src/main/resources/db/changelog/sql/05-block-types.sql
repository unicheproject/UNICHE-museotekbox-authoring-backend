--liquibase formatted sql

--changeset museotekbox:0021-remove-old-block-types
--comment QUIZ, BUTTON and TRUE_FALSE blocks are gone: questions are SCREEN/media blocks plus rules, and the Box is scan-only, so nothing presses a button. Existing rows were test data only, so they are deleted rather than mapped
DELETE FROM blocks WHERE type IN ('QUIZ', 'BUTTON', 'TRUE_FALSE');
--rollback SELECT 1;
