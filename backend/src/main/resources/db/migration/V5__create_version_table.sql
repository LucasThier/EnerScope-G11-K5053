-- V5: project versions
CREATE TABLE version (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active             BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified      TIMESTAMP WITH TIME ZONE NOT NULL,
    name               VARCHAR(120)             NOT NULL,
    project_id         UUID                     NOT NULL    REFERENCES project (id),
    parent_version_id  UUID                                 REFERENCES version (id)
);

CREATE INDEX idx_version_project ON version (project_id);
CREATE INDEX idx_version_parent_version ON version (parent_version_id);
