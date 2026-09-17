-- Align existing result mappings without rewriting applied migrations.
ALTER TABLE result RENAME COLUMN year TO simulation_year;

CREATE TABLE economic_configuration (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id UUID NOT NULL UNIQUE REFERENCES version(id) ON DELETE CASCADE,
    configuration_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE economic_evaluation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id UUID NOT NULL REFERENCES version(id) ON DELETE CASCADE,
    snapshot_json TEXT NOT NULL
);
CREATE INDEX idx_economic_evaluation_version ON economic_evaluation(version_id, created_at);
