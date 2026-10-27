-- GDPR erase: removes the users row of one user and anonymises the places that name them.
-- Comments, media and templates stay: they belong to the museum's work. Only the link to the person goes.
-- Run with: psql -v subject='<JWT subject>' -f erase-user.sql
-- Runs in one transaction and prints the row counts before COMMIT. Run export-user.sql first if the
-- user asked for a copy.
--
-- If the person still has a Keycloak account and logs in again, the app creates a new users row
-- (JIT provisioning). Erase them in Keycloak first.

\set ON_ERROR_STOP on
BEGIN;

DELETE FROM users WHERE subject = :'subject';

-- author_subject and author_name are NOT NULL. Nobody can delete an anonymised comment as its author
-- any more; org managers still can.
UPDATE comments
SET author_subject = 'erased', author_name = 'Deleted user'
WHERE author_subject = :'subject';

UPDATE comments SET resolved_by = NULL WHERE resolved_by = :'subject';
UPDATE media SET uploaded_by = NULL WHERE uploaded_by = :'subject';
UPDATE experience_templates SET created_by = NULL WHERE created_by = :'subject';

COMMIT;
