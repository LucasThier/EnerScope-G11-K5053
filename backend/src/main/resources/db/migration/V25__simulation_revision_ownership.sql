-- Preserve V24 as applied; enforce revision ownership for both execution paths.
ALTER TABLE economic_job ADD CONSTRAINT fk_simulation_scenario_revision
 FOREIGN KEY (version_id,scenario_revision_id) REFERENCES scenario_revision(scenario_id,id);
ALTER TABLE economic_evaluation ADD CONSTRAINT fk_evaluation_scenario_revision
 FOREIGN KEY (version_id,scenario_revision_id) REFERENCES scenario_revision(scenario_id,id);
