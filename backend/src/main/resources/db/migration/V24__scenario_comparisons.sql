-- V24: saved scenario comparisons. Only the definition is stored (which
-- immutable economic evaluations, in which order, and the relevance
-- thresholds); the result is recalculated on read. Position 0 is the baseline.
CREATE TABLE scenario_comparison (
    id                  UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    active              BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified       TIMESTAMP WITH TIME ZONE NOT NULL,
    project_id          UUID                     NOT NULL    REFERENCES project (id),
    name                VARCHAR(120)             NOT NULL,
    description         TEXT,
    relevance_threshold NUMERIC(9, 6)            NOT NULL    CHECK (relevance_threshold > 0),
    rate_threshold      NUMERIC(9, 6)            NOT NULL    CHECK (rate_threshold > 0)
);

CREATE INDEX idx_scenario_comparison_project ON scenario_comparison (project_id, created_at);

CREATE TABLE scenario_comparison_item (
    id            UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    active        BOOLEAN                  NOT NULL    DEFAULT TRUE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    comparison_id UUID                     NOT NULL    REFERENCES scenario_comparison (id),
    version_id    UUID                     NOT NULL    REFERENCES version (id),
    evaluation_id UUID                     NOT NULL    REFERENCES economic_evaluation (id),
    position      INT                      NOT NULL    CHECK (position >= 0),
    CONSTRAINT uq_scenario_comparison_item_position UNIQUE (comparison_id, position),
    CONSTRAINT uq_scenario_comparison_item_evaluation UNIQUE (comparison_id, evaluation_id)
);

CREATE INDEX idx_scenario_comparison_item_evaluation ON scenario_comparison_item (evaluation_id);
