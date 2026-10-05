-- V11: columns that the entities map but no earlier migration created, which made
-- `spring.jpa.hibernate.ddl-auto=validate` fail at startup on a fresh database.
--   * BaseNode.maintenanceDuration  -> base_node.maintenance_duration
--   * FLNGUnit.gasConsumption       -> flng_unit.gas_consumption

ALTER TABLE base_node ADD COLUMN maintenance_duration INTEGER NOT NULL DEFAULT 0;
ALTER TABLE flng_unit ADD COLUMN gas_consumption REAL;
