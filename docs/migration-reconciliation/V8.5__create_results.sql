-- V8: per-node simulation results.
-- The aggregated `final_result*` tables are created by V9__create_results.sql.
CREATE TABLE result_per_node (
                                 id                        UUID                     NOT NULL DEFAULT gen_random_uuid(),
                                 node_id                   UUID                     NOT NULL,
                                 node_class                VARCHAR(100)             NOT NULL,
                                 total_produced            REAL                     NOT NULL DEFAULT 0.0,
                                 total_deferred            REAL                     NOT NULL DEFAULT 0.0,
                                 max_possible_produced     REAL                     NOT NULL DEFAULT 0.0,
                                 extra                     REAL                     NOT NULL DEFAULT 0.0,
                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_rpn_node FOREIGN KEY (node_id) REFERENCES base_node(id)
);

CREATE INDEX idx_rpn_node_id ON result_per_node(node_id);
