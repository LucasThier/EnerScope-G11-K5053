-- Fields used by the simulator existed in Java but were missing from V2.
ALTER TABLE base_node ADD COLUMN maintenance_duration INTEGER NOT NULL DEFAULT 0;
ALTER TABLE flng_unit ADD COLUMN gas_consumption REAL NOT NULL DEFAULT 0;
