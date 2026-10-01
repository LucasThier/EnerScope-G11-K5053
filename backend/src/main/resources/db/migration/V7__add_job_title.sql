-- V7: optional job title for every user (e.g. "Senior Investment Analyst").
-- Nullable and without a default: accounts created before this migration have
-- no title recorded, and the field stays optional afterwards.
ALTER TABLE app_user
    ADD COLUMN job_title VARCHAR(120);
