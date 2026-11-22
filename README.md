# EnerScope
**Grupo 11 — Curso K5053 — Año 2026**

### Integrantes

| Legajo | Nombre | E-Mail |
| --- | --- | --- |
| 172.329-7 | Pasqualino Franco Nehuen | fpasqualino@frba.utn.edu.ar |
| 203.583-2 | Theo Dyzenchauz | tdyzenchauz@frba.utn.edu.ar |
| 204.109-1 | Thier Lucas | lthier@frba.utn.edu.ar |
| 159.668-8 | Julian López | julilopez@frba.utn.edu.ar |
| 164.344-7 | Camila Agustina Sanchez | camsanchez@frba.utn.edu.ar |

---

Monorepo for **EnerScope** — a web application with a Spring Boot backend and a
React + Vite frontend. It covers **users, authentication and session
management** (JWT based), **organizations and projects** with scoped roles,
**versions** of a project's value-chain model (with per-version roles, branches,
merge and conflicts), and the **economic evaluation** of scenarios.

### Vocabulary: Versión and Escenario

Two different concepts, used the same way in the code, the docs and the screens:

- **Versión = base física.** The network model of a project: nodes, connections and
  branches (parent/child versions). It is what the value-chain editor edits.
- **Escenario = configuración económica y de simulación.** The economic rules and the
  simulation settings that rest on a version. Simulations, results and comparisons belong
  to a scenario.

> A version can hold **several scenarios** (up to 20, each with a name the person types, unique in the
> version ignoring case; every version is created with an empty "Escenario base", and a branch does not inherit its parent's scenarios): the backend API for listing, creating, renaming, duplicating, deleting,
> configuring and simulating them is in [`backend/README.md`](backend/README.md#scenario-api). The screens
> follow the flow **version → its scenarios → one scenario** (list with status, *Nuevo escenario*, *Duplicar*,
> *Renombrar*, *Eliminar*; see [`frontend/README.md`](frontend/README.md)), and comparisons compare **scenarios** (of the same or
> different versions of a project, see [`docs/scenario-comparison.md`](docs/scenario-comparison.md)); a
> scenario never creates a version. Node values ("Operación") belong to the version and are edited on the
> node. See [`docs/considerations.md`](docs/considerations.md).

- **Domain:** `enerscope.org`
- **Backend:** Java 21 · Spring Boot 3.5 · PostgreSQL · Flyway · JWT · Swagger
- **Frontend:** React 19 · Vite · TypeScript · Tailwind CSS (no hand-written CSS)

## Repository layout

```
EnerScope/
├─ backend/     Spring Boot API (auth, users, organizations, projects, versions, economics)
├─ frontend/    React + Vite client (shell, projects and teams, versions, value-chain map, economics)
├─ docs/        Architecture and technical documentation
└─ README.md    This file
```

## Getting started

The fastest path is Docker Compose (starts PostgreSQL + the API):

```bash
cd backend
docker compose up --build
```

Then run the frontend:

```bash
cd frontend
npm install
npm run dev
```

- Frontend: http://localhost:5173
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- Health: http://localhost:8080/api/v1/health

Tests:

```bash
cd backend && mvn test        # JUnit, H2 — no database needed
cd frontend && npm test       # node:test unit suite
cd frontend && npm run build  # type-check + build
```

The five `*PostgreSqlIT` backend classes are **not** part of `mvn test`; they are
run by hand against a disposable PostgreSQL (see
[docs/testing.md](docs/testing.md) → *PostgreSQL classes*). Counts and the
case-by-case catalog are in [docs/testing.md](docs/testing.md).

A default administrator is seeded on first boot: `admin@enerscope.org`
(password configurable via `ADMIN_PASSWORD`, default `admin12345`). It has the
platform **`ADMIN`** role — registration is not self-service, so this account is
the one that creates the first users (see [backend/README.md](backend/README.md)
→ Roles).

## Documentation

- [Backend README](backend/README.md) — run, env vars, Swagger, structure
- [Frontend README](frontend/README.md) — run, env vars, structure, conventions
- [docs/architecture.md](docs/architecture.md) — system architecture
- [docs/domain-model.md](docs/domain-model.md) — domain model, membership and version-role rules
- [docs/testing.md](docs/testing.md) — test catalog and counts (backend, frontend, manual checks)
- [docs/technical-decisions.md](docs/technical-decisions.md) — technical decisions
- [docs/considerations.md](docs/considerations.md) — running notes & expectations for future work
- [docs/scenario-comparison.md](docs/scenario-comparison.md) — comparing the results of a project's scenarios
- [docs/deploy.md](docs/deploy.md) — deployment: pushes to `master` deploy the backend to AWS Lightsail and the frontend to Cloudflare Pages
- [AGENTS.md](AGENTS.md) — guide for AI coding agents (and contributors)

## Important links

- Swagger UI: `/api/v1/swagger-ui.html`
- OpenAPI JSON: `/api/v1/v3/api-docs`
