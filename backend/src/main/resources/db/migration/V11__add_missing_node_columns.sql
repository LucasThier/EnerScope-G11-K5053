-- V11: column that the entity maps but no earlier migration created, which made
-- `spring.jpa.hibernate.ddl-auto=validate` fail at startup on a fresh database.
--   * BaseNode.maintenanceDuration -> base_node.maintenance_duration

ALTER TABLE base_node ADD COLUMN maintenance_duration INTEGER NOT NULL DEFAULT 0;
