-- V11: bring the schema in line with what the entities map, so that
-- `spring.jpa.hibernate.ddl-auto=validate` passes on a fresh database.
--   * BaseNode.maintenanceDuration and FLNGUnit.gasConsumption had no column.
--   * Result / ResultPerNode drifted from V8: Version.results is a @OneToMany
--     with @JoinColumn (result.results_id), Result.resultPerNodes is a
--     unidirectional @OneToMany (join table result_result_per_nodes) and
--     ResultPerNode.nodeID maps to "nodeid".

ALTER TABLE base_node ADD COLUMN maintenance_duration INTEGER NOT NULL DEFAULT 0;
ALTER TABLE flng_unit ADD COLUMN gas_consumption REAL;

-- result: owning version is referenced through results_id
DROP INDEX IF EXISTS idx_result_version_id;
ALTER TABLE result DROP COLUMN version_id;
ALTER TABLE result ADD COLUMN results_id UUID;
ALTER TABLE result ADD CONSTRAINT fk_result_results FOREIGN KEY (results_id) REFERENCES version(id) ON DELETE CASCADE;
CREATE INDEX idx_result_results_id ON result(results_id);

-- result_per_node: no back-reference column, node id column renamed
DROP INDEX IF EXISTS idx_rpn_result_id;
ALTER TABLE result_per_node DROP COLUMN result_id;
ALTER TABLE result_per_node RENAME COLUMN node_id TO nodeid;

CREATE TABLE result_result_per_nodes (
    result_id           UUID NOT NULL,
    result_per_nodes_id UUID NOT NULL,
    PRIMARY KEY (result_id, result_per_nodes_id),
    CONSTRAINT uk_result_per_nodes UNIQUE (result_per_nodes_id),
    CONSTRAINT fk_rrpn_result FOREIGN KEY (result_id) REFERENCES result(id) ON DELETE CASCADE,
    CONSTRAINT fk_rrpn_rpn FOREIGN KEY (result_per_nodes_id) REFERENCES result_per_node(id) ON DELETE CASCADE
);
