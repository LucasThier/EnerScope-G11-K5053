-- V6: the `well` table was missing the `surface` column that the Well entity
-- maps (@Column(name = "surface")), which failed Hibernate schema validation on
-- a real PostgreSQL boot. V2__create_nodes.sql created every other Well column
-- but omitted this one. Additive fix (the node schema is validated, not rebuilt).
ALTER TABLE well
    ADD COLUMN surface REAL;
