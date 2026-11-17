--liquibase formatted sql

--changeset museotekbox:0038-display-session-org-name
--comment The organisation's name for the display's idle screen, copied from Catalogue when the manager opens the display. The display has no user login, so it can't ask Catalogue itself; a renamed organisation shows its new name once the display is opened again. Empty for displays opened before this column
ALTER TABLE display_sessions ADD COLUMN org_name VARCHAR(255);
--rollback ALTER TABLE display_sessions DROP COLUMN org_name;
