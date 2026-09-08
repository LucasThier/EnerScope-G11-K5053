-- V4: project membership and roles
CREATE TABLE project_member (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id       UUID                     NOT NULL    REFERENCES app_user (id),
    project_id    UUID                     NOT NULL    REFERENCES project (id),
    CONSTRAINT uq_project_member_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_member_project ON project_member (project_id);
CREATE INDEX idx_project_member_user ON project_member (user_id);

CREATE TABLE project_member_role (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active             BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified      TIMESTAMP WITH TIME ZONE NOT NULL,
    name               VARCHAR(60)              NOT NULL,
    member_type        VARCHAR(30)              NOT NULL,
    project_member_id  UUID                     NOT NULL REFERENCES project_member (id)
);

CREATE INDEX idx_project_member_role_member ON project_member_role (project_member_id);

CREATE TABLE project_member_role_permission (
    project_member_role_id UUID        NOT NULL REFERENCES project_member_role (id),
    permission              VARCHAR(40) NOT NULL,
    PRIMARY KEY (project_member_role_id, permission)
);
