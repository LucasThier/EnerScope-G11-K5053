-- V27 (E4a): a version can now hold several scenarios, so the economic configuration, its draft and the
-- scenario draft are unique per SCENARIO instead of per version.
--
-- The UNIQUE (version_id) constraints created inline by V11, V13 and V19 are dropped (looked up by their
-- columns, not by name) and replaced by unique indexes on scenario_id. scenario_id is still nullable
-- (made NOT NULL, and version_id dropped, in the final "contract" migration), but V26 linked every row, and
-- UNIQUE accepts several NULLs anyway. Nothing is deleted or rewritten. Repeatable: running it again changes nothing.

DO $$
DECLARE
    found RECORD;
BEGIN
    FOR found IN
        SELECT c.conrelid::regclass AS tbl, c.conname
        FROM pg_constraint c
        WHERE c.contype = 'u'
          AND c.conrelid IN ('economic_configuration'::regclass, 'economic_draft'::regclass, 'scenario_draft'::regclass)
          AND (SELECT array_agg(a.attname::text)
               FROM unnest(c.conkey) AS k(attnum)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum) = ARRAY['version_id']
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', found.tbl, found.conname);
    END LOOP;
END
$$;

-- E3 created plain indexes on these columns; the unique ones below replace them.
DROP INDEX IF EXISTS idx_economic_configuration_scenario;
DROP INDEX IF EXISTS idx_economic_draft_scenario;
DROP INDEX IF EXISTS idx_scenario_draft_scenario;

CREATE UNIQUE INDEX IF NOT EXISTS uq_economic_configuration_scenario ON economic_configuration (scenario_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_economic_draft_scenario         ON economic_draft (scenario_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_scenario_draft_scenario         ON scenario_draft (scenario_id);

-- Lookups by version (all the scenarios of one version) keep an index each.
CREATE INDEX IF NOT EXISTS idx_economic_configuration_version ON economic_configuration (version_id);
CREATE INDEX IF NOT EXISTS idx_economic_draft_version         ON economic_draft (version_id);
CREATE INDEX IF NOT EXISTS idx_scenario_draft_version         ON scenario_draft (version_id);
