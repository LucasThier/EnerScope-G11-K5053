CREATE TABLE probabilistic_distribution (
                                            id                  UUID NOT NULL DEFAULT gen_random_uuid(),
                                            distribution_type   VARCHAR(30) NOT NULL,
    -- Parámetros (todos deben permitir NULL porque varían según el tipo)
                                            val_a        REAL,
                                            val_b               DOUBLE PRECISION,
                                            PRIMARY KEY (id)
);