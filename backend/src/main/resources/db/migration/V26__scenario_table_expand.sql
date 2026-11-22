-- V26 (E3, "expand" only): a scenario is the economic and simulation configuration that rests on a
-- version. This migration only ADDS: the scenario table, a nullable scenario_id on every table that
-- hangs from a version's economics, and one default scenario ("Escenario base") per existing version.
--
-- Nothing is dropped or made stricter here: the UNIQUE (version_id) constraints of
-- economic_configuration, economic_draft and scenario_draft stay until V27 (start of E4, which is the
-- first change that can create a second scenario in a version), and reads keep using version_id.
-- Every statement is repeatable, so running the file again changes nothing.

CREATE TABLE IF NOT EXISTS scenario (
    id            UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    active        BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id    UUID                     NOT NULL REFERENCES version (id) ON DELETE CASCADE,
    name          VARCHAR(120)             NOT NULL CHECK (btrim(name) <> ''),
    is_default    BOOLEAN                  NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_scenario_version ON scenario (version_id);
-- A name is unique inside its version ignoring case, among the active scenarios (a deleted one frees it).
CREATE UNIQUE INDEX IF NOT EXISTS uq_scenario_version_name ON scenario (version_id, lower(name)) WHERE active;
-- At most one active default scenario per version.
CREATE UNIQUE INDEX IF NOT EXISTS uq_scenario_default_version ON scenario (version_id) WHERE is_default AND active;

-- One default scenario for EVERY version (active or not), so each version always has one to resolve.
-- The maximum of scenarios per version is enforced by the service, not here.
INSERT INTO scenario (id, active, created_at, last_modified, version_id, name, is_default)
SELECT gen_random_uuid(), v.active, v.created_at, v.last_modified, v.id, 'Escenario base', TRUE
FROM version v
WHERE NOT EXISTS (SELECT 1 FROM scenario s WHERE s.version_id = v.id AND s.is_default);

-- The six tables that hang from a version's economics gain a nullable scenario_id.
ALTER TABLE economic_configuration    ADD COLUMN IF NOT EXISTS scenario_id UUID REFERENCES scenario (id);
ALTER TABLE economic_draft            ADD COLUMN IF NOT EXISTS scenario_id UUID REFERENCES scenario (id);
ALTER TABLE scenario_draft            ADD COLUMN IF NOT EXISTS scenario_id UUID REFERENCES scenario (id);
ALTER TABLE economic_evaluation       ADD COLUMN IF NOT EXISTS scenario_id UUID REFERENCES scenario (id);
ALTER TABLE economic_job              ADD COLUMN IF NOT EXISTS scenario_id UUID REFERENCES scenario (id);
ALTER TABLE scenario_comparison_item  ADD COLUMN IF NOT EXISTS scenario_id UUID REFERENCES scenario (id);

CREATE INDEX IF NOT EXISTS idx_economic_configuration_scenario   ON economic_configuration (scenario_id);
CREATE INDEX IF NOT EXISTS idx_economic_draft_scenario           ON economic_draft (scenario_id);
CREATE INDEX IF NOT EXISTS idx_scenario_draft_scenario           ON scenario_draft (scenario_id);
CREATE INDEX IF NOT EXISTS idx_economic_evaluation_scenario      ON economic_evaluation (scenario_id, created_at);
CREATE INDEX IF NOT EXISTS idx_economic_job_scenario             ON economic_job (scenario_id, created_at);
CREATE INDEX IF NOT EXISTS idx_scenario_comparison_item_scenario ON scenario_comparison_item (scenario_id);

-- Backfill: every existing row points at the default scenario of its version. Only rows still
-- without one are touched, and last_modified / revision are left alone.
UPDATE economic_configuration t   SET scenario_id = s.id FROM scenario s WHERE s.version_id = t.version_id AND s.is_default AND t.scenario_id IS NULL;
UPDATE economic_draft t           SET scenario_id = s.id FROM scenario s WHERE s.version_id = t.version_id AND s.is_default AND t.scenario_id IS NULL;
UPDATE scenario_draft t           SET scenario_id = s.id FROM scenario s WHERE s.version_id = t.version_id AND s.is_default AND t.scenario_id IS NULL;
UPDATE economic_evaluation t      SET scenario_id = s.id FROM scenario s WHERE s.version_id = t.version_id AND s.is_default AND t.scenario_id IS NULL;
UPDATE economic_job t             SET scenario_id = s.id FROM scenario s WHERE s.version_id = t.version_id AND s.is_default AND t.scenario_id IS NULL;
UPDATE scenario_comparison_item t SET scenario_id = s.id FROM scenario s WHERE s.version_id = t.version_id AND s.is_default AND t.scenario_id IS NULL;

-- Final control: fail the whole migration (it runs in one transaction) rather than leave any link unset.
DO $$
DECLARE
    tbl     TEXT;
    missing BIGINT;
BEGIN
    SELECT count(*) INTO missing FROM version v
    WHERE NOT EXISTS (SELECT 1 FROM scenario s WHERE s.version_id = v.id AND s.is_default);
    IF missing > 0 THEN
        RAISE EXCEPTION 'V26: % versions have no default scenario', missing;
    END IF;
    FOREACH tbl IN ARRAY ARRAY['economic_configuration', 'economic_draft', 'scenario_draft',
                               'economic_evaluation', 'economic_job', 'scenario_comparison_item'] LOOP
        EXECUTE format('SELECT count(*) FROM %I WHERE scenario_id IS NULL', tbl) INTO missing;
        IF missing > 0 THEN
            RAISE EXCEPTION 'V26: % rows of % have no scenario', missing, tbl;
        END IF;
    END LOOP;
END
$$;
