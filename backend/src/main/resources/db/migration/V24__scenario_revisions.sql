-- Additive migration: legacy versions, inputs, results and memberships remain intact.
CREATE TABLE scenario (
 id UUID PRIMARY KEY REFERENCES version(id),
 project_id UUID NOT NULL REFERENCES project(id),
 name VARCHAR(320) NOT NULL,
 description VARCHAR(500) NOT NULL DEFAULT '',
 base_scenario_id UUID REFERENCES scenario(id),
 branch_revision_id UUID,
 current_revision_id UUID,
 generation BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE scenario_revision (
 id UUID PRIMARY KEY,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL,
 last_modified TIMESTAMPTZ NOT NULL,
 scenario_id UUID NOT NULL REFERENCES scenario(id),
 number INTEGER NOT NULL CHECK (number > 0),
 created_by UUID REFERENCES app_user(id),
 parent_revision_id UUID REFERENCES scenario_revision(id),
 merge_source_revision_id UUID REFERENCES scenario_revision(id),
 merge_base_revision_id UUID REFERENCES scenario_revision(id),
 snapshot_json TEXT NOT NULL,
 content_hash VARCHAR(64) NOT NULL,
 UNIQUE (scenario_id, number),
 UNIQUE (scenario_id, id),
 CHECK (merge_base_revision_id IS NULL OR merge_source_revision_id IS NOT NULL)
);
ALTER TABLE scenario ADD CONSTRAINT fk_scenario_current_revision
 FOREIGN KEY (id, current_revision_id) REFERENCES scenario_revision(scenario_id, id);
ALTER TABLE scenario ADD CONSTRAINT fk_scenario_branch_revision
 FOREIGN KEY (branch_revision_id) REFERENCES scenario_revision(id);
CREATE INDEX idx_scenario_project ON scenario(project_id);
-- Existing parent relationships are provenance, not proof of a historical merge base.
INSERT INTO scenario(id, project_id, name)
 SELECT id, versions_id, name FROM version WHERE versions_id IS NOT NULL;
UPDATE scenario s SET base_scenario_id = v.parent_version_id
 FROM version v WHERE v.id = s.id AND EXISTS (SELECT 1 FROM scenario p WHERE p.id = v.parent_version_id);

-- Null revision denotes legacy input; historical JSON is not rewritten.
ALTER TABLE economic_job ADD COLUMN scenario_revision_id UUID REFERENCES scenario_revision(id);
ALTER TABLE economic_job ADD COLUMN economic_calculation_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE economic_evaluation ADD COLUMN scenario_revision_id UUID REFERENCES scenario_revision(id);
CREATE INDEX idx_simulation_revision ON economic_job(scenario_revision_id);
CREATE INDEX idx_evaluation_revision ON economic_evaluation(scenario_revision_id);

CREATE TABLE scenario_merge (
 id UUID PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL, last_modified TIMESTAMPTZ NOT NULL,
 target_id UUID NOT NULL REFERENCES scenario(id),
 source_revision_id UUID NOT NULL REFERENCES scenario_revision(id),
 target_revision_id UUID NOT NULL REFERENCES scenario_revision(id),
 base_revision_id UUID REFERENCES scenario_revision(id),
 state VARCHAR(20) NOT NULL, resolutions_json TEXT NOT NULL, conflicts_json TEXT NOT NULL,
 workspace_hash VARCHAR(64), generation BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_prepared_scenario_merge ON scenario_merge(target_id) WHERE state='PREPARED';
