CREATE TABLE scenario_comparison (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    project_id UUID NOT NULL REFERENCES project(id),
    name VARCHAR(500) NOT NULL,
    configuration_json TEXT NOT NULL,
    result_json TEXT NOT NULL
);
CREATE INDEX idx_scenario_comparison_project ON scenario_comparison(project_id, created_at);
