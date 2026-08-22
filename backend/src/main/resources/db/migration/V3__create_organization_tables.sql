-- V3: organizations, organization membership/roles and projects
CREATE TABLE organization (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    name          VARCHAR(120)             NOT NULL
);

CREATE TABLE organization_member (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active          BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified   TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id         UUID                     NOT NULL    REFERENCES app_user (id),
    organization_id UUID                     NOT NULL    REFERENCES organization (id),
    CONSTRAINT uq_org_member_org_user UNIQUE (organization_id, user_id)
);

CREATE INDEX idx_org_member_organization ON organization_member (organization_id);
CREATE INDEX idx_org_member_user ON organization_member (user_id);

CREATE TABLE organization_member_role (
    id                      UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active                  BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified           TIMESTAMP WITH TIME ZONE NOT NULL,
    name                    VARCHAR(60)              NOT NULL,
    member_type             VARCHAR(30)              NOT NULL,
    organization_member_id  UUID                     NOT NULL REFERENCES organization_member (id)
);

CREATE INDEX idx_org_member_role_member ON organization_member_role (organization_member_id);

CREATE TABLE organization_member_role_permission (
    organization_member_role_id UUID        NOT NULL REFERENCES organization_member_role (id),
    permission                  VARCHAR(40) NOT NULL,
    PRIMARY KEY (organization_member_role_id, permission)
);

CREATE TABLE project (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active          BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified   TIMESTAMP WITH TIME ZONE NOT NULL,
    name            VARCHAR(120)             NOT NULL,
    description     VARCHAR(500)             NOT NULL,
    organization_id UUID                     NOT NULL    REFERENCES organization (id)
);

CREATE INDEX idx_project_organization ON project (organization_id);
