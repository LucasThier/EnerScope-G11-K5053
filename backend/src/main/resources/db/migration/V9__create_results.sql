--V7: Result tables
CREATE TABLE final_result (
                              id           UUID NOT NULL DEFAULT gen_random_uuid(),
                              time         INT ,
                              media_output REAL ,
                              version_id   UUID,
                              PRIMARY KEY (id),
                              CONSTRAINT fk_final_result_version FOREIGN KEY (version_id) REFERENCES version(id) ON DELETE CASCADE
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

CREATE TABLE final_result_p90 (
                                  final_result_id    UUID NOT NULL,
                                  result_per_node_id UUID NOT NULL,
                                  PRIMARY KEY (final_result_id, result_per_node_id),
                                  CONSTRAINT fk_p90_fr FOREIGN KEY (final_result_id) REFERENCES final_result(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_p90_rpn FOREIGN KEY (result_per_node_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);

CREATE TABLE final_result_p50 (
                                  final_result_id    UUID NOT NULL,
                                  result_per_node_id UUID NOT NULL,
                                  PRIMARY KEY (final_result_id, result_per_node_id),
                                  CONSTRAINT fk_p50_fr FOREIGN KEY (final_result_id) REFERENCES final_result(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_p50_rpn FOREIGN KEY (result_per_node_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);

CREATE TABLE final_result_p10 (
                                  final_result_id    UUID NOT NULL,
                                  result_per_node_id UUID NOT NULL,
                                  PRIMARY KEY (final_result_id, result_per_node_id),
                                  CONSTRAINT fk_p10_fr FOREIGN KEY (final_result_id) REFERENCES final_result(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_p10_rpn FOREIGN KEY (result_per_node_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);

-- Índices de búsqueda
CREATE INDEX idx_final_result_version_id ON final_result(version_id);
CREATE INDEX idx_rpn_node_id ON result_per_node(node_id);