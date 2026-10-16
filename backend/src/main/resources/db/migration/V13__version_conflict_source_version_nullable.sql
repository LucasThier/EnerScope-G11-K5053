ALTER TABLE version_conflict DROP CONSTRAINT fk_vc_source_version;
ALTER TABLE version_conflict ALTER COLUMN source_version_id DROP NOT NULL;
ALTER TABLE version_conflict
    ADD CONSTRAINT fk_vc_source_version FOREIGN KEY (source_version_id) REFERENCES version (id) ON DELETE SET NULL;
