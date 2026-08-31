
-- Version table
CREATE TABLE  version (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields
    name                      VARCHAR(320)               NOT NULL,
    parent_version_id         UUID,
    versions_id        UUID                     NOT NULL,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_version_parent FOREIGN KEY (parent_version_id) REFERENCES version(id),
    CONSTRAINT fk_project FOREIGN KEY (versions_id) REFERENCES project (id)
);


CREATE TABLE  node_change (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields for CompressingPlant
    change_type                VARCHAR(320)             NOT NULL,
    -- Foreign keys (NOT NULL as per entity mappings)
    changed_node_id              UUID,
    result_node_id               UUID,
    -- Version relationship
    node_changes_id              UUID,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_nc_changed_node FOREIGN KEY (changed_node_id) REFERENCES base_node(id),
    CONSTRAINT fk_nc_result_node FOREIGN KEY (result_node_id) REFERENCES base_node(id),
    CONSTRAINT fk_nc_node_changes FOREIGN KEY (node_changes_id) REFERENCES version(id)
    );

CREATE TABLE  connection_change (
    id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Inherited from BaseEntity
    active                    BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified             TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Specific fields for CompressingPlant
    change_type                VARCHAR(320)             NOT NULL,
    -- Foreign keys (NOT NULL as per entity mappings)
    changed_connection_id              UUID,
    result_connection_id               UUID,
    -- Version relationship
    connection_changes_id              UUID,
    PRIMARY KEY (id),
    -- Foreign key constraints
    CONSTRAINT fk_cc_changed_connection FOREIGN KEY (changed_connection_id) REFERENCES node_connection(id),
    CONSTRAINT fk_cc_result_connection FOREIGN KEY (result_connection_id) REFERENCES node_connection(id),
    CONSTRAINT fk_cc_connection_changes FOREIGN KEY (connection_changes_id) REFERENCES version(id)
    );


-- Join table for version and node snapshot (many-to-many)
CREATE TABLE  versionXNode (
    version_id                UUID                     NOT NULL,
    node_id                   UUID                     NOT NULL,
    PRIMARY KEY (version_id, node_id),
    CONSTRAINT fk_versionxnode_version FOREIGN KEY (version_id) REFERENCES version(id),
    CONSTRAINT fk_versionxnode_node FOREIGN KEY (node_id) REFERENCES base_node(id)
);

-- Join table for version and connection snapshot (many-to-many)
CREATE TABLE  versionXConnection (
    version_id                UUID                     NOT NULL,
    connection_id             UUID                     NOT NULL,
    PRIMARY KEY (version_id, connection_id),
    CONSTRAINT fk_versionxconnection_version FOREIGN KEY (version_id) REFERENCES version(id),
    CONSTRAINT fk_versionxconnection_connection FOREIGN KEY (connection_id) REFERENCES node_connection(id)
);
