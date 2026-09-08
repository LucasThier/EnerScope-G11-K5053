CREATE TABLE result (
                        id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Specific fields
                        year                      INT                      NOT NULL,
                        version_id                UUID,
                        PRIMARY KEY (id),
    -- Foreign key constraints
                        CONSTRAINT fk_result_version FOREIGN KEY (version_id) REFERENCES version(id) ON DELETE CASCADE
);

CREATE TABLE result_per_node (
                                 id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
    -- Specific fields
                                 node_id                   UUID                     NOT NULL,
                                 node_class                 VARCHAR(100)             NOT NULL,
                                 total_produced            REAL                     NOT NULL DEFAULT 0.0,
                                 total_deferred            REAL                     NOT NULL DEFAULT 0.0,
                                 max_possible_produced     REAL                     NOT NULL DEFAULT 0.0,
                                 extra                     REAL                     NOT NULL DEFAULT 0.0,
    -- Relationship field
                                 result_id                 UUID                     NOT NULL,
                                 PRIMARY KEY (id),
    -- Foreign key constraints
                                 CONSTRAINT fk_rpn_result FOREIGN KEY (result_id) REFERENCES result(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_rpn_node FOREIGN KEY (node_id) REFERENCES base_node(id)
);

-- Indexes for optimal lookup performance
CREATE INDEX idx_result_version_id ON result(version_id);
CREATE INDEX idx_rpn_result_id ON result_per_node(result_id);
CREATE INDEX idx_rpn_node_id ON result_per_node(node_id);