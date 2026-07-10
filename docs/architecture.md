# Architecture

EnerScope is a two-tier web application kept in a single repository (monorepo).

```
┌────────────┐        HTTP / JSON        ┌──────────────┐        JDBC        ┌────────────┐
│  Frontend  │  ───────────────────────▶ │   Backend    │ ─────────────────▶ │ PostgreSQL │
│ React+Vite │      /api/v1/**           │ Spring Boot  │                    │            │
└────────────┘  ◀─────────────────────── └──────────────┘ ◀───────────────── └────────────┘
                     JWT (Bearer)
```

## Backend

- **Spring Boot 3.5 / Java 21.** REST API served under the `/api/v1` context path.
- **Stateless authentication.** No server-side session store. Each request
  carries a JWT access token in the `Authorization: Bearer` header. `AuthFilter`
  validates it and populates the Spring Security context; `SessionService`
  rebuilds an in-memory `Session` from the token claims.
- **Refresh tokens.** A separate, longer-lived JWT (`typ=refresh`) lets the
  client obtain a fresh access token via `POST /auth/refresh`.
- **Persistence.** JPA/Hibernate over PostgreSQL. The schema is owned by
  **Flyway** migrations; Hibernate runs in `validate` mode and never mutates
  the schema.
- **Cross-cutting concerns.** A global exception handler returns a uniform
  `ApiResponse` envelope; all logging flows through the `AppLogger` abstraction.

### Request flow (authenticated call)

1. Client sends a request with `Authorization: Bearer <access token>`.
2. `AuthFilter` extracts and validates the token through `SessionService`.
3. On success, a `Session` (with the `User`) is attached to the security context.
4. The controller handles the request; `AuthUtil.currentSession()` exposes the
   caller when needed.
5. Errors are normalised by `GlobalExceptionHandler`.

## Frontend

- **React 19 + Vite + TypeScript**, styled exclusively with **Tailwind CSS**
  (no hand-written CSS files beyond the single Tailwind import).
- The `src/api` layer wraps Axios: `client.ts` injects the access token and
  transparently refreshes it on `401`; `session.ts` centralises token storage;
  `auth.ts` exposes the auth endpoints.
- The Vite dev server proxies `/api` to the backend on port `8080`.

## Layers (backend)

Packages are organised **by feature, then by layer**: a feature package with
more than one class (e.g. `user`, `auth`, `session`) splits into layer
subpackages and owns its DTOs. Single-class or cross-cutting packages stay flat.
See `AGENTS.md` for the full convention.

| Layer | Subpackage | Responsibility |
| --- | --- | --- |
| Controller | `<feature>/controller` | HTTP endpoints, validation, response shaping |
| Service | `<feature>/service` | Business logic |
| Repository | `<feature>/repository` | Data access (Spring Data JPA) |
| Model | `<feature>/model` | Persistent entities / value objects |
| DTO | `<feature>/dto` | Request/response records for that feature |
| Filter | `<feature>/filter` | Servlet filters (e.g. `auth/filter/AuthFilter`) |
| Config | `config` | Security, CORS, crypto, OpenAPI, JPA (flat, cross-cutting) |
