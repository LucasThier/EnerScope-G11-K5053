# Agent guide — EnerScope

Instructions for AI coding agents (Codex, Claude Code, etc.) working in this
repository. Read this fully before making changes. It is also useful for human
contributors.

> This file is the canonical agent guide. `CLAUDE.md` points here.

## What this project is

EnerScope is a monorepo web app. This scaffold covers **users, authentication
and session management**; domain features are built on top.

- **Domain:** `enerscope.org`
- **Backend:** `backend/` — Java 21 · Spring Boot 3.5 · PostgreSQL · Flyway · JWT · Swagger
- **Frontend:** `frontend/` — React 19 · Vite · TypeScript · Tailwind CSS only
- **Docs:** `docs/` — architecture, domain model, technical decisions, deploy,
  and running **considerations** for future work.

## Before you change anything

1. **Read the documentation first.** Important context — design decisions, what
   is expected to happen, and gotchas — lives in:
   - [`docs/technical-decisions.md`](docs/technical-decisions.md) — why things are the way they are.
   - [`docs/considerations.md`](docs/considerations.md) — running notes and expectations for future changes. **Always read this**; add to it when relevant.
   - [`docs/domain-model.md`](docs/domain-model.md) and [`docs/architecture.md`](docs/architecture.md).
2. Understand the existing patterns and match them (see Conventions below).

## Definition of done (every change must satisfy all of these)

1. **Tests.** Any change to behavior comes with tests.
   - Backend: unit tests next to the code (`backend/src/test/...`). Prefer fast
     unit tests (no DB); use the H2 context-load test only for wiring.
   - `cd backend && mvn test` must stay green.
   - Frontend: `cd frontend && npm run build` (type-check) must pass; add tests
     when a test setup exists.
   - **Do not** mark work complete with failing or skipped tests.
2. **Logging.** Add logs through `AppLogger` (never `System.out`/`printf`), at
   the **correct level**:
   - `debug` — diagnostics useful when troubleshooting.
   - `info` — routine, meaningful business events (e.g. "user registered").
   - `warn` — unexpected but recoverable situations.
   - `error` — failures; include the exception.
   Verbosity is environment-controlled (`LOG_LEVEL_APP`, `LOG_LEVEL_ROOT`), so
   don't gate logs behind manual flags.
3. **Documentation.** Update docs alongside the code, keeping them
   human-readable:
   - `README.md` (root / `backend` / `frontend`) when setup, commands, env vars
     or the public surface change.
   - `docs/` when architecture, the domain model, or a decision changes.
   - `docs/considerations.md` — append any decision, assumption, or expectation
     that future agents/humans should keep in mind. Prefer dated, concise bullets.
4. **Schema changes** go through a new Flyway migration
   (`backend/src/main/resources/db/migration/V<n>__<desc>.sql`). Never edit an
   already-applied migration. Keep the JPA entities and the migration in sync
   (Hibernate runs in `validate` mode).

## Conventions

- **Language:** all identifiers, files, comments and log messages in **English**.
- **Backend package:** `org.enerscope`. Keep the layered structure
  (controller → service → repository → entity); config under `config/`.
- **API shape:** every response uses the `ApiResponse<T>` envelope via the
  `Responses` helper; errors flow through `GlobalExceptionHandler`.
- **Auth:** stateless JWT (access + refresh). Public endpoints: `/auth/**`,
  `/health`, and Swagger. Everything else requires a Bearer token.
- **Register every new endpoint in `SecurityConfig`** unless an existing matcher
  already covers it. When you add a controller/route, check
  `SecurityConfig.filterChain`: if the path is not already handled by a rule
  (e.g. it falls through to `anyRequest().authenticated()` but should be public,
  or it needs a different rule), add an explicit `requestMatchers(...)` entry.
  Paths there are relative to the `/api/v1` servlet context. Default: a new
  endpoint is authenticated — only make it `permitAll()` on purpose.
- **Frontend:** Tailwind classes only — no `.css` files beyond the single
  `@import "tailwindcss";`. All HTTP goes through `src/api/*`; tokens only
  through `src/api/session.ts`. Keep `src/types/auth.ts` in sync with backend DTOs.
- **Secrets:** never commit `.env` (only `.env.example`). Never hard-code
  credentials or secrets.

## Common commands

```bash
# Backend
cd backend && mvn test                 # run tests
cd backend && mvn spring-boot:run      # run locally (needs PostgreSQL)
cd backend && docker compose up --build# run API + PostgreSQL

# Frontend
cd frontend && npm install && npm run dev
cd frontend && npm run build           # type-check + build
```

- Swagger UI: http://localhost:8080/api/v1/swagger-ui.html
- Health: http://localhost:8080/api/v1/health

## Do not

- Commit build output (`target/`, `dist/`, `node_modules/`) or `.env` files.
- Introduce hand-written CSS on the frontend, or add roles/phone/other fields to
  `User` without updating the domain model docs, a migration, and tests.
