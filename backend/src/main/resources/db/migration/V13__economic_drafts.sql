CREATE TABLE economic_draft (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version_id UUID NOT NULL UNIQUE REFERENCES version(id) ON DELETE CASCADE,
    draft_json TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);
