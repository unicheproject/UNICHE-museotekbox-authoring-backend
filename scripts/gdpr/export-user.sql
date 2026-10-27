-- GDPR export: every row that holds personal data of one user.
-- Read-only. Run with: psql -v subject='<JWT subject>' -f export-user.sql
-- The JWT subject is the Keycloak user id; find it in Keycloak or in users.subject.

\pset format aligned

\echo '== users =='
SELECT subject, email, preferred_username, display_name, first_seen_at, last_seen_at
FROM users
WHERE subject = :'subject';

\echo '== comments written by the user =='
SELECT id, project_id, parent_id, author_name, body, created_at
FROM comments
WHERE author_subject = :'subject'
ORDER BY created_at;

\echo '== comments resolved by the user =='
SELECT id, project_id, resolved_at
FROM comments
WHERE resolved_by = :'subject'
ORDER BY resolved_at;

\echo '== media uploaded by the user =='
SELECT id, org_id, name, uploaded_at
FROM media
WHERE uploaded_by = :'subject'
ORDER BY uploaded_at;

\echo '== experience templates created by the user =='
SELECT id, org_id, name, created_at
FROM experience_templates
WHERE created_by = :'subject'
ORDER BY created_at;
