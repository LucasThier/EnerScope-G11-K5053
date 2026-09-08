-- V7: platform-level role for every user (ADMIN or USER).
-- Existing rows default to USER; the seeded default administrator is promoted.
ALTER TABLE app_user
    ADD COLUMN platform_role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Promote the default seeded administrator. Deployments that override
-- app.admin.mail rely on the AdminSeeder to create the admin with the ADMIN
-- role instead (see docs/considerations.md).
UPDATE app_user
    SET platform_role = 'ADMIN'
    WHERE LOWER(mail) = LOWER('admin@enerscope.org');
