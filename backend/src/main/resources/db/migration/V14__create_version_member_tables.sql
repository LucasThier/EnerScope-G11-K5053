-- V14: version-level membership and roles. A version member can hold any
-- number of roles at once (unlike project/organization members, who hold
-- exactly one): an EDITOR role is scoped to one vertical via a nullable
-- column, so the same user can be EDITOR for several verticals plus e.g.
-- SIMULATOR, all on the same version.
CREATE TABLE version_member (
    id            UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id       UUID                     NOT NULL    REFERENCES app_user (id),
    version_id    UUID                     NOT NULL    REFERENCES version (id),
    CONSTRAINT uq_version_member_version_user UNIQUE (version_id, user_id)
);

CREATE INDEX idx_version_member_version ON version_member (version_id);
CREATE INDEX idx_version_member_user ON version_member (user_id);

CREATE TABLE version_member_role (
    id                 UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    active             BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified      TIMESTAMP WITH TIME ZONE NOT NULL,
    name               VARCHAR(60)              NOT NULL,
    member_type        VARCHAR(30)              NOT NULL,
    vertical           VARCHAR(30),
    version_member_id  UUID                     NOT NULL REFERENCES version_member (id)
);

CREATE INDEX idx_version_member_role_member ON version_member_role (version_member_id);

CREATE TABLE version_member_role_permission (
    version_member_role_id UUID        NOT NULL REFERENCES version_member_role (id),
    permission              VARCHAR(40) NOT NULL,
    PRIMARY KEY (version_member_role_id, permission)
);
