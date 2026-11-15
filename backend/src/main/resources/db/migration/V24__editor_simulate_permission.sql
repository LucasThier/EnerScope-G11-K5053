-- V24: an EDITOR version role may simulate. Until now that was a rule in code
-- (VersionAccess.grantedByRoles); it is now stored like every other permission.
-- Backfills existing EDITOR roles (active or not, so a reactivated role is not
-- left behind). Re-runnable: the primary key (role, permission) plus
-- ON CONFLICT DO NOTHING makes a second execution a no-op.
INSERT INTO version_member_role_permission (version_member_role_id, permission)
SELECT id, 'SIMULATE_VERSION'
FROM version_member_role
WHERE member_type = 'EDITOR'
ON CONFLICT DO NOTHING;
