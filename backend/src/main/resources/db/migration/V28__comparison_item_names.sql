-- V28 (E6, "expand" only): a saved comparison keeps the names of the version and of the scenario of each item as they were
-- when it was saved, so it can still be shown, with its immutable evaluation, after the scenario or the version is deleted
-- (both are only deactivated, never removed, and the comparison reads each item through its own evaluation).
-- Nothing is dropped or made stricter; every statement is repeatable.

ALTER TABLE scenario_comparison_item ADD COLUMN IF NOT EXISTS version_name  VARCHAR(320);
ALTER TABLE scenario_comparison_item ADD COLUMN IF NOT EXISTS scenario_name VARCHAR(120);

-- Existing comparisons take the names the version and the scenario have now. Only items still without a name are touched.
UPDATE scenario_comparison_item i SET version_name = v.name
FROM version v WHERE v.id = i.version_id AND i.version_name IS NULL;
UPDATE scenario_comparison_item i SET scenario_name = s.name
FROM scenario s WHERE s.id = i.scenario_id AND i.scenario_name IS NULL;

-- Final control: every item has both names (the version and the scenario of an item always exist, V26 linked them all).
DO $$
DECLARE
    missing BIGINT;
BEGIN
    SELECT count(*) INTO missing FROM scenario_comparison_item WHERE version_name IS NULL OR scenario_name IS NULL;
    IF missing > 0 THEN
        RAISE EXCEPTION 'V28: % comparison items have no version or scenario name', missing;
    END IF;
END
$$;
