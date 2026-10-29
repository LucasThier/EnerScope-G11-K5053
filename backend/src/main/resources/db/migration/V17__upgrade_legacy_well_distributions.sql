CREATE TABLE probabilistic_distribution (
                                            id                  UUID NOT NULL DEFAULT gen_random_uuid(),
                                            distribution_type   VARCHAR(30) NOT NULL,
    -- Parámetros (todos deben permitir NULL porque varían según el tipo)
                                            val_a        REAL,
                                            val_b               REAL,
                                            PRIMARY KEY (id)
);
-- Preserve legacy deterministic decline as a constant distribution per well.
-- Retain the scalar column for audit and rollback of the previous application.
ALTER TABLE well ADD COLUMN decline_curve_id UUID REFERENCES probabilistic_distribution(id);
INSERT INTO probabilistic_distribution (id, distribution_type, val_a)
SELECT id, 'CONSTANT', decline_curve FROM well WHERE decline_curve IS NOT NULL;
UPDATE well SET decline_curve_id = id WHERE decline_curve IS NOT NULL;
