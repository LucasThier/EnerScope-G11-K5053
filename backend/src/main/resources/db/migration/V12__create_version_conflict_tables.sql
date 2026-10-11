-- V7: conflicts detected automatically when a subversion is merged into its parent.
-- One row per (sibling, entity) whose node/connection identityId was also changed by the merged subversion.
CREATE TABLE version_conflict (
    id                     UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                 BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified          TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    merged_version_id      UUID                     NOT NULL,
    source_version_id      UUID                     NOT NULL,
    conflicting_version_id UUID                     NOT NULL,
    entity_type            VARCHAR(20)              NOT NULL,
    identity_id            UUID                     NOT NULL,
    resolved               BOOLEAN                  NOT NULL DEFAULT FALSE,
    resolved_at            TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_vc_merged_version FOREIGN KEY (merged_version_id) REFERENCES version (id) ON DELETE CASCADE,
    CONSTRAINT fk_vc_source_version FOREIGN KEY (source_version_id) REFERENCES version (id) ON DELETE CASCADE,
    CONSTRAINT fk_vc_conflicting_version FOREIGN KEY (conflicting_version_id) REFERENCES version (id) ON DELETE CASCADE
);

CREATE INDEX idx_version_conflict_conflicting ON version_conflict (conflicting_version_id);
CREATE INDEX idx_version_conflict_merged ON version_conflict (merged_version_id);
CREATE INDEX idx_version_conflict_identity ON version_conflict (identity_id);
