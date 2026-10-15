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

### Version node CSV import

`POST /version/{versionId}/nodes/import` accepts a multipart `file` CSV upload
and is authenticated like the other `/version/**` routes. The CSV uses a
`nodeKind` column with an uppercase snake-case discriminator; each row can
contain a different node type. It imports the concrete `BaseNode` subclasses
from `node.model.export`, `extraction`, `liquefaction` and `transportation`
(including `PipelineConnection`, but not graph edges/`NodeConnection`).
Supported values are `WELL`, `GATHERING_NETWORK`, `TREATMENT_PLANT`, `PIPELINE`,
`PIPELINE_CONNECTION`, `COMPRESSING_PLANT`, `GROUND_BASED_LIQUEFACTION_PLANT`,
`FLNG_UNIT`, `LNG_CARRIER`, `SEAPORT_TERMINAL`, `INTERNAL_CONSUMPTION` and
`INDUSTRIAL_CONSUMPTION`.

CSV headers otherwise use the DTO property names. The nested `type`,
`investmentCost`, `graphData` and `declineCurve` values are JSON objects in
their cells; quote/escape them as normal CSV fields. Columns for other node
types may be present in the shared header but must be blank on rows that do not
support them. Common required fields include `name`, `state`, `lifespanInMonths`,
`maintenanceIntervalInDays`, `upkeepCosts`, `operatingCosts`, `wastePercentage`,
a complete `type` object and `investmentCost.components`; required nullable
fields specific to each node type must also be populated.
The importer verifies edit permission before decoding/parsing, validates node
type metadata, numeric fields and nested distribution/position data, and rejects
malformed CSV quoting. A file is limited to 5 MB and 10,000 data rows. An upload
is one transaction: an invalid row rejects the request and rolls back the
batch. Invalid CSV/data returns `400`; missing multipart fields return `400`;
requests over the framework upload limit return `413`, all in the `ApiResponse`
envelope. The response's `data` is the number of imported nodes.

## Frontend

- **React 19 + Vite + TypeScript**, styled exclusively with **Tailwind CSS**
  (no hand-written CSS files beyond the single Tailwind import). Brand colours
  live as `@theme` tokens in `src/index.css` and are the only source of colour.
- The `src/api` layer wraps Axios: `client.ts` injects the access token and
  transparently refreshes it on `401`; `session.ts` centralises token storage;
  `auth.ts` exposes the auth endpoints.
- **Shared state is React context, one provider per concern.** `AuthProvider`
  holds identity and the session; `ActiveProjectProvider` holds the project list
  and which project the app is scoped to. Each pairs a `use*` hook with the
  provider so components never touch the context object directly.
- **The signed-in shell** is `AppLayout`: a full-width top bar (brand, project
  switcher, user menu) above a collapsible sidebar, with pages rendered through
  the router `Outlet`. Route guards (`ProtectedRoute`, `RoleRoute`) sit above
  it, so the shell only ever renders for an authenticated user.
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
