-- Saved scenario comparisons that cross projects (E1 of the Version / Scenario work).
--
-- Since E1 a comparison only holds scenarios of the project it is saved in. A comparison
-- saved earlier may hold a scenario (version) of ANOTHER project; the service now answers
-- 404 for those items, so such comparisons can no longer be opened and are to be deleted.
--
-- NOT a Flyway migration: it is reviewed and run by hand. Do NOT run it on real data
-- before taking a backup, e.g.:
--   pg_dump --data-only -t scenario_comparison -t scenario_comparison_item "$DATABASE_URL" > comparisons-backup.sql
--
-- PART 1 is read-only. PART 2 deletes inside a transaction that ENDS WITH ROLLBACK:
-- it shows what would be deleted and changes nothing. To really delete, review the counts,
-- take the backup, and replace the final ROLLBACK by COMMIT in your own copy.

-- ---------------------------------------------------------------------------
-- PART 1 (read-only): which saved comparisons cross projects, and which projects.
-- A comparison is "cross-project" when any of its items belongs to a project other than
-- the one the comparison is saved in (version.versions_id is the owning project).
-- ---------------------------------------------------------------------------

-- 1a. How many there are, out of how many.
SELECT count(*) FILTER (WHERE crossing.comparison_id IS NOT NULL) AS cross_project_comparisons,
       count(*)                                                   AS total_comparisons
FROM scenario_comparison c
LEFT JOIN (SELECT DISTINCT i.comparison_id
           FROM scenario_comparison_item i
           JOIN scenario_comparison c2 ON c2.id = i.comparison_id
           JOIN version v ON v.id = i.version_id
           WHERE v.versions_id IS DISTINCT FROM c2.project_id) crossing ON crossing.comparison_id = c.id
WHERE c.active;

-- 1b. One row per cross-project comparison: where it is saved and which other projects it touches.
SELECT c.id, c.name, c.created_at,
       sp.name AS saved_in_project,
       string_agg(DISTINCT op.name, ', ' ORDER BY op.name) AS other_projects,
       count(*) FILTER (WHERE v.versions_id IS DISTINCT FROM c.project_id) AS items_in_other_projects,
       count(*) AS items
FROM scenario_comparison c
JOIN project sp ON sp.id = c.project_id
JOIN scenario_comparison_item i ON i.comparison_id = c.id
JOIN version v ON v.id = i.version_id
LEFT JOIN project op ON op.id = v.versions_id AND op.id <> c.project_id
WHERE c.active
GROUP BY c.id, c.name, c.created_at, sp.name
HAVING count(*) FILTER (WHERE v.versions_id IS DISTINCT FROM c.project_id) > 0
ORDER BY c.created_at;

-- 1c. Totals by pair of projects (saved in -> touches).
SELECT sp.name AS saved_in_project, op.name AS other_project, count(DISTINCT c.id) AS comparisons
FROM scenario_comparison c
JOIN project sp ON sp.id = c.project_id
JOIN scenario_comparison_item i ON i.comparison_id = c.id
JOIN version v ON v.id = i.version_id
JOIN project op ON op.id = v.versions_id AND op.id <> c.project_id
WHERE c.active
GROUP BY sp.name, op.name
ORDER BY comparisons DESC, saved_in_project;

-- ---------------------------------------------------------------------------
-- PART 2 (dry run): delete them. Ends with ROLLBACK, so nothing is changed.
-- Includes inactive (soft-deleted) comparisons too, so no orphan item rows are left.
-- ---------------------------------------------------------------------------
BEGIN;

CREATE TEMP TABLE doomed ON COMMIT DROP AS
SELECT DISTINCT i.comparison_id AS id
FROM scenario_comparison_item i
JOIN scenario_comparison c ON c.id = i.comparison_id
JOIN version v ON v.id = i.version_id
WHERE v.versions_id IS DISTINCT FROM c.project_id;

SELECT count(*) AS comparisons_to_delete FROM doomed;

DELETE FROM scenario_comparison_item WHERE comparison_id IN (SELECT id FROM doomed);
DELETE FROM scenario_comparison      WHERE id IN (SELECT id FROM doomed);

SELECT (SELECT count(*) FROM scenario_comparison)      AS comparisons_left,
       (SELECT count(*) FROM scenario_comparison_item) AS items_left;

-- The evaluations (economic_evaluation) and rounds the comparisons pointed at are NOT touched.
ROLLBACK;   -- <- replace with COMMIT only after review and backup
