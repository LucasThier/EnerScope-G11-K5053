-- V13: what a simulation run needs to be listed, compared and costed.
--   * final_result gains when it ran, how many Monte Carlo rounds it used, and the
--     cost of the simulated model over the run's horizon (snapshotted at run time).
--   * node_cost_result keeps that cost per node, with the node's name, type and
--     state copied so the run still reads correctly after the model changes.
--   * result_per_node loses its foreign key to base_node: a run is a historical
--     record, and it must not stop a node from being deleted from the model later.

ALTER TABLE final_result ADD COLUMN rounds     INT                      NOT NULL DEFAULT 10;
ALTER TABLE final_result ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE final_result ADD COLUMN capex      NUMERIC(19, 2)           NOT NULL DEFAULT 0;
ALTER TABLE final_result ADD COLUMN opex       NUMERIC(19, 2)           NOT NULL DEFAULT 0;
ALTER TABLE final_result ADD COLUMN upkeep     NUMERIC(19, 2)           NOT NULL DEFAULT 0;

CREATE TABLE node_cost_result (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    final_result_id UUID           NOT NULL,
    node_id         UUID           NOT NULL,
    identity_id     UUID,
    node_name       VARCHAR(320)   NOT NULL,
    node_type       VARCHAR(50),
    vertical        VARCHAR(30),
    node_state      VARCHAR(20),
    capex           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    opex            NUMERIC(19, 2) NOT NULL DEFAULT 0,
    upkeep          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_ncr_final_result FOREIGN KEY (final_result_id) REFERENCES final_result (id) ON DELETE CASCADE
);

CREATE INDEX idx_ncr_final_result ON node_cost_result (final_result_id);

ALTER TABLE result_per_node DROP CONSTRAINT fk_rpn_node;
