# Demo data for a local environment

`docs/examples/seed-local-demo.mjs` fills a **local** EnerScope with demo data so the whole flow can be tried without
building it by hand: organization, users with every role, a network with the 12 node types, a branch, scenarios,
an economic configuration, a draft, simulations and a saved comparison. It replaced `seed-comparison-demo.mjs`
on 2026-11-22.

> **Never run `docker compose down -v`.** It deletes the local database volume (`enerscope_pgdata`) and every real row
> with it. The seed never needs it, and neither does its cleanup.

## How it works

- **Through the public API**, as real users, so every rule the app enforces applies: permissions, vertical and
  structural role derived from the node type (`NodeTypeEnum`), one "Escenario base" per version, the scenario limit,
  unique names, "an EXECUTIVE holds no version roles", "a version role needs project membership". Nothing is written
  to the database directly, and it is not a Flyway migration or a startup runner, so it can never reach QA or production
  through a deploy.
- **On demand only.** Nothing runs it when the stack starts.
- **Idempotent.** Each item is looked up by name or mail and created only when missing. A second run prints
  `Listo: 0 cambio(s)` and leaves every table with the same row count (verified, see below). It never edits or removes
  anything that is not demo data.
- **Marked.** Every name it creates starts with `Demo - ` and every mail ends in `@enerscope.test` (a reserved
  top-level domain). The "Escenario base" keeps its fixed name; it is recognizable because it lives in a demo project.
  Node names inside the demo versions (Pozo Norte, Empalme, …) have no prefix either, for the same reason.
- **Double guard.** It stops before any network call unless **both** hold:
  1. the API URL is plain `http://` on `localhost`, `127.0.0.1` or `::1` (no credentials in the URL), and
  2. `ENERSCOPE_SEED_LOCAL=yes` is set.

  There is no flag to skip it. A tunnel or port-forward to a remote server would pass the first check; the second one
  is the explicit "this is my machine" that covers that case.
- **No passwords in the repository.** `ENERSCOPE_ADMIN_PASSWORD` (the platform admin used to bootstrap) and
  `ENERSCOPE_DEMO_PASSWORD` (shared by the nine demo users, at least 8 characters) are mandatory and have no
  default. The script stops with a clear message when one is missing and never prints either.

## Run it

From the repository root, with the backend running locally (`docker compose up` in `backend/`, or `mvn spring-boot:run`):

```bash
ENERSCOPE_SEED_LOCAL=yes ENERSCOPE_ADMIN_PASSWORD= ENERSCOPE_DEMO_PASSWORD= node docs/examples/seed-local-demo.mjs --simulate
```

Fill in the two passwords; leave out `--simulate` to load the data without simulating (the comparison then stays
pending until a run with `--simulate`). Node 18 or later, no dependencies.

| Option / variable | Meaning |
| --- | --- |
| `--simulate` | Also simulates "Escenario base" and "Demo - Costos altos" (10 rounds each, seeds 1001 and 1002) and saves the comparison. |
| `--api <url>` / `ENERSCOPE_API` | Backend base URL. Default `http://localhost:8080/api/v1`; only loopback hosts are accepted. |
| `ENERSCOPE_ADMIN_MAIL` | Bootstrap admin. Default `admin@enerscope.org`. |
| `--cleanup --dry-run` / `--cleanup --yes` | See [Clean up](#clean-up). |

Measured against a disposable PostgreSQL 16: about **50 s** for the first run with `--simulate` on an empty
database (most of it is the two simulations), about **24 s** for a later run that has nothing to do.

## What it creates

Organization **Demo - EnerScope**. The bootstrap admin creates the organization and the users; everything inside the
projects is done by **Demo Admin Proyecto** signed in as themselves, so the bootstrap admin never joins a demo project.

### Users and roles

| Mail | Platform | Organization | Demo - Cuenca Neuquina | Version roles |
| --- | --- | --- | --- | --- |
| `demo-owner@enerscope.test` | USER | OWNER | — | — |
| `demo-admin-plataforma@enerscope.test` | ADMIN | MEMBER | — | — (a platform admin sees everything) |
| `demo-admin-proyecto@enerscope.test` | USER | MEMBER | ADMIN (also ADMIN of *Demo - Cuenca Austral*) | — |
| `demo-editor-extraccion@enerscope.test` | USER | MEMBER | EDITOR | EDITOR / EXTRACTION on *Demo - Base* and *Demo - Ampliación* |
| `demo-editor-transporte@enerscope.test` | USER | MEMBER | EDITOR | EDITOR / TRANSPORTATION on *Demo - Base* and *Demo - Ampliación* |
| `demo-simulador@enerscope.test` | USER | MEMBER | EDITOR | SIMULATOR on *Demo - Base* and *Demo - Ampliación* |
| `demo-ejecutivo@enerscope.test` | USER | MEMBER | EXECUTIVE | — (an EXECUTIVE cannot hold version roles) |
| `demo-admin-version@enerscope.test` | USER | MEMBER | EDITOR | ADMIN of *Demo - Ampliación* only |
| `demo-sin-acceso@enerscope.test` | USER | MEMBER | — | — (organization membership alone opens no project) |

All of them sign in with `ENERSCOPE_DEMO_PASSWORD`. The version roles are granted on the root **before** the branch
is created, so the branch receives them by the app's own copy. "Version admin without being in the project" is not
possible (a version role needs an active project membership), hence the project EDITOR + branch ADMIN.

### Project "Demo - Cuenca Neuquina"

- **Version "Demo - Base" (root)**: 13 nodes covering the 12 types, 12 connections.

  ```text
  Pozo Norte ─┐
              ├─> Red de recolección -> Planta de tratamiento -> Planta compresora -> Gasoducto troncal -> Empalme
  Pozo Sur ───┘                                                                                           │
          ┌───────────────────────────────────────────────────────────────────────────────────────────────┘
          ├─> Licuefacción en tierra -> Terminal portuaria
          ├─> Unidad FLNG -> Buque metanero
          ├─> Consumo interno
          └─> Consumo industrial        (INDUSTRIAL_CONSUMPTION, vertical INTERNAL_CONSUMPTION)
  ```

  Scenarios:
  - **Escenario base**: economic configuration from the backend fixture
    `backend/src/test/resources/economic-examples/economic-configuration.json`, pointed at the two wells, equipment
    USD 1.000. Simulated with `--simulate`.
  - **Demo - Costos altos**: same configuration, equipment USD 1.800 and every fixed expense doubled. Simulated with `--simulate`.
  - **Demo - Borrador**: an incomplete configuration saved as the scenario's **draft** (so it cannot be simulated
    until it is completed or discarded; that is on purpose).
- **Comparison "Demo - Base vs Costos altos"** (saved; needs `--simulate`).
- **Version "Demo - Ampliación" (branch of Demo - Base)**: inherits the network; "Pozo Sur" raised from 500 to 800
  (the edit gives the branch its own copy, same identity). Has its own empty "Escenario base".

The **full 12-type network passes `simulation-readiness` and simulates**; no subset was needed.

### Project "Demo - Cuenca Austral"

Version "Demo - Austral base" with two wells feeding "Gasoducto Austral" (3 nodes, 2 connections). Only Demo Admin
Proyecto is a member: it exists to check that nothing leaks between projects (the Ejecutivo and the Editors do not
see it; reading its comparisons as the Ejecutivo is refused).

### Rows

Exact counts after the seed with `--simulate` on an empty database (the bootstrap admin included), PostgreSQL 16,
2026-11-22; identical after a second and a third run:

| Table | Rows | Table | Rows |
| --- | ---: | --- | ---: |
| `app_user` | 10 | `version` | 3 |
| `organization` | 1 | `version_member` / `_role` | 7 / 7 |
| `organization_member` / `_role` | 9 / 9 | `version_member_role_permission` | 20 |
| `organization_member_role_permission` | 10 | `scenario` | 5 |
| `project` | 2 | `economic_configuration` | 2 |
| `project_member` / `_role` | 7 / 7 | `scenario_draft` | 1 |
| `project_member_role_permission` | 15 | `economic_job` / `economic_evaluation` | 2 / 2 |
| `base_node` (+ `node_type_data`, `node_graph_data`, `investment_cost`) | 30 each | `economic_round` | 20 |
| `well` | 7 | `scenario_comparison` / `_item` | 1 / 2 |
| other node tables | 2–3 each | `node_connection` | 27 |
| `versionxnode` / `versionxconnection` | 29 / 26 | `node_change` / `connection_change` | 17 / 15 |

(The branch has its own copy of each node and connection, and editing "Pozo Sur" adds one more node row: 13 in the
root + 13 in the branch + 3 in Austral + 1 = 30.)

## Clean up

```bash
ENERSCOPE_SEED_LOCAL=yes ENERSCOPE_ADMIN_PASSWORD= node docs/examples/seed-local-demo.mjs --cleanup --dry-run   # list only
ENERSCOPE_SEED_LOCAL=yes ENERSCOPE_ADMIN_PASSWORD= node docs/examples/seed-local-demo.mjs --cleanup --yes       # do it
```

`--cleanup` alone is refused: it needs `--dry-run` (prints the plan, changes nothing; verified: 0 rows changed) or
`--yes`. It selects **only** by the demo markers: organizations named `Demo - …`; projects named `Demo - …` **that
belong to a demo organization** (a real project that happens to start with `Demo - ` elsewhere is left alone);
users whose mail ends in `@enerscope.test` (never the bootstrap admin). Then, through the API:

1. deletes the saved comparisons of the demo projects;
2. deletes each root version with its branches. The backend refuses (`409`) to delete a version that has
   simulations or saved comparisons, so a simulated version is **kept** and becomes inactive with its project;
3. deactivates the demo projects, the demo users and the demo organization.

It goes through the app's own deletes, which are mostly soft: the rows stay in the database, inactive and hidden
from the app. Running the seed again afterwards reactivates the organization and the users and creates fresh projects.
To get rid of every row, use a new disposable database — **never** `docker compose down -v` on the real one.

## Tests

- `node --test docs/examples/seed-local-demo.test.mjs` — the guard, options, credentials, cleanup selection and the
  data builders, with no server (18 tests).
- `docs/examples/seed-local-demo.integration.test.mjs` — runs the seed twice against a backend with an empty disposable
  database and checks that the second run creates nothing and leaves the same state, plus what each role sees
  (2 tests). Skipped unless `ENERSCOPE_SEED_IT_API` is set:

  ```bash
  ENERSCOPE_SEED_IT_API=http://localhost:18080/api/v1 ENERSCOPE_SEED_LOCAL=yes ENERSCOPE_ADMIN_PASSWORD= ENERSCOPE_DEMO_PASSWORD= \
    node --test docs/examples/seed-local-demo.integration.test.mjs
  ```

  It leaves the demo data in that database, so point it only at a throwaway one (for example PostgreSQL on 55432 and
  the backend on 18080, see [testing.md](testing.md#local-demo-seed-node-test)).
