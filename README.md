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
React + Vite frontend. This initial scaffold covers **users, authentication and
session management** (JWT based). Domain features are added on top of this base.

- **Domain:** `enerscope.org`
- **Backend:** Java 21 · Spring Boot 3.5 · PostgreSQL · Flyway · JWT · Swagger
- **Frontend:** React 19 · Vite · TypeScript · Tailwind CSS (no hand-written CSS)

## Repository layout

```
EnerScope/
├─ backend/     Spring Boot API (auth, users, sessions, health)
├─ frontend/    React + Vite client (auth portal: login/panels + API/session layer)
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

A default administrator is seeded on first boot: `admin@enerscope.org`
(password configurable via `ADMIN_PASSWORD`, default `admin12345`). It has the
platform **`ADMIN`** role — registration is not self-service, so this account is
the one that creates the first users (see [backend/README.md](backend/README.md)
→ Roles).

## Documentation

- [Backend README](backend/README.md) — run, env vars, Swagger, structure
- [Frontend README](frontend/README.md) — run, env vars, structure, conventions
- [docs/architecture.md](docs/architecture.md) — system architecture
- [docs/domain-model.md](docs/domain-model.md) — domain model
- [docs/technical-decisions.md](docs/technical-decisions.md) — technical decisions
- [docs/considerations.md](docs/considerations.md) — running notes & expectations for future work
- [docs/deploy.md](docs/deploy.md) — deployment
- [AGENTS.md](AGENTS.md) — guide for AI coding agents (and contributors)

## Important links

- Swagger UI: `/api/v1/swagger-ui.html`
- OpenAPI JSON: `/api/v1/v3/api-docs`
