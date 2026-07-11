# Considerations & expectations

A running log of decisions, assumptions and things future work should keep in
mind. **Read this before making changes, and append to it when you introduce
something non-obvious.** Keep entries concise, dated, and human-readable.

Format: `- YYYY-MM-DD — <note>` (newest at the bottom of each section).

## Current expectations

- The `User` model is intentionally minimal: `mail`, `firstName`, `lastName`,
  `passwordHash` (plus `BaseEntity` audit fields). There are **no roles,
  permissions, or phone number** yet. The seeded `admin@enerscope.org` is a
  plain user; if admin privileges become real, add a role/flag **with** a
  migration, updated `docs/domain-model.md`, and tests.
- Authentication is **stateless** (JWT). There is no session table; a `Session`
  is rebuilt from the token on each request. Revocation before expiry is not
  supported — keep access-token lifetimes short.
- Flyway owns the schema; Hibernate runs in `validate`. The H2 profile used by
  tests disables Flyway and lets Hibernate build the schema — keep entities
  portable enough for both, or adjust the test profile deliberately.
- The frontend is a minimal shell on purpose: it must build and run, plus the
  API/session layer and types. Tailwind only; no hand-written CSS.

## Log

- 2026-07-03 — Initial scaffold: backend (auth/users/sessions/JWT/health),
  minimal frontend, docs, Docker, Flyway migration, admin seeder, tests.
- 2026-07-03 — Logging goes through the `AppLogger` interface; console output is
  the default (`ConsoleAppLogger`). Levels are configurable via `.env`
  (`LOG_LEVEL_APP`, `LOG_LEVEL_ROOT`).
- 2026-07-03 — Surefire loads Mockito as an explicit `-javaagent` (future JDKs
  disallow self-attaching); `maven-dependency-plugin` exposes the jar path.
- 2026-07-10 — Bulk user registration: `POST /users/bulk` (multipart `file`)
  parses a CSV, generates a strong password per row (`PasswordGenerator`,
  `SecureRandom`) and returns a `mail,password` CSV (`credentialsCsv`) plus a
  per-row failure list. Plaintext passwords are returned **once** in the
  response and never persisted; whoever calls the endpoint is responsible for
  distributing them securely. The endpoint is under `/users/**`, so it is
  **authenticated** (no `/auth` permitAll) — but there are still **no roles**,
  so *any* logged-in user can bulk-create accounts. When roles land, gate this
  behind an admin role. CSV parsing/writing uses a small in-repo helper
  (`common/CsvUtil`) instead of a new dependency; input is read fully into
  memory (fine for expected list sizes, bounded by Spring's multipart limits).
- 2026-07-10 — Backend packages reorganised **by feature, then by layer**.
  Feature packages with several classes now split into `controller`, `service`,
  `repository`, `model`, `filter`, `dto` subpackages (`user`, `auth`,
  `session`); the shared top-level `dto/` package was removed and each feature
  owns its DTOs (`user/dto`, `auth/dto`). The former `dto/session/*` records are
  now `auth/dto` (they belong to the auth endpoints). Single-class / infra
  packages (`jwt`, `health`, `money`, `seed`, `logging`, `util`, `common`,
  `config`) stay flat to avoid needless nesting. Convention documented in
  `AGENTS.md`; add new classes to the matching layer subpackage of their
  feature.
- 2026-07-10 — Added `AuthControllerTest` (`@WebMvcTest` + real
  `SecurityConfig`/`AuthFilter`, mocked services) covering register/login/
  refresh/logout, including validation (`400`), domain errors (`400`) and
  refresh failures (`401`). Test suite is now 44 cases. Introduced
  [`docs/testing.md`](testing.md) as a **test catalog** (class, type, per-case
  "what it verifies") and made keeping it in sync a Definition-of-done item in
  `AGENTS.md` — update it in the same change as any test add/remove/rename.
