-- V9: final (aggregated) result tables.
-- `result` and `result_per_node` (with their indexes) are already created by
-- V8__create_results.sql; this migration only adds what V8 does not have.
CREATE TABLE final_result (
                              id           UUID NOT NULL DEFAULT gen_random_uuid(),
                              time         INT ,
                              media_output REAL ,
                              version_id   UUID,
                              PRIMARY KEY (id),
                              CONSTRAINT fk_final_result_version FOREIGN KEY (version_id) REFERENCES version(id) ON DELETE CASCADE
);

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

CREATE INDEX idx_final_result_version_id ON final_result(version_id);
