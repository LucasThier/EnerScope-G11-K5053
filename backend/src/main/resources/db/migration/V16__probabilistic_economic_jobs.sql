CREATE TABLE economic_job (
 id UUID PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL,
 last_modified TIMESTAMPTZ NOT NULL, version_id UUID NOT NULL REFERENCES version(id),
 input_json TEXT NOT NULL, status VARCHAR(24) NOT NULL, rounds INT NOT NULL CHECK (rounds BETWEEN 1 AND 100),
 completed_rounds INT NOT NULL DEFAULT 0 CHECK (completed_rounds >= 0 AND completed_rounds <= rounds),
 seed VARCHAR(20) NOT NULL, started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ, error_message TEXT,
 evaluation_id UUID UNIQUE REFERENCES economic_evaluation(id),
 CHECK (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','INTERRUPTED'))
);
CREATE INDEX idx_economic_job_queue ON economic_job(status,created_at);
CREATE INDEX idx_economic_job_version ON economic_job(version_id,created_at);
CREATE TABLE economic_round (
 id UUID PRIMARY KEY, active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL,
 last_modified TIMESTAMPTZ NOT NULL, job_id UUID NOT NULL REFERENCES economic_job(id),
 round_number INT NOT NULL CHECK (round_number BETWEEN 1 AND 100), snapshot_json TEXT NOT NULL,
 summary_json TEXT NOT NULL, UNIQUE(job_id,round_number)
);
-- New final-result percentile collections no longer require a legacy per-round Result owner.
ALTER TABLE result_per_node ALTER COLUMN result_id DROP NOT NULL;
