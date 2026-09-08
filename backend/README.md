# EnerScope — Backend

Spring Boot REST API for users, authentication and session management.

- Java 21 · Spring Boot 3.5.5
- PostgreSQL + Flyway migrations
- Stateless JWT authentication (access + refresh tokens)
- OpenAPI / Swagger UI
- BCrypt password hashing

## Running the backend

### Option A — Docker Compose (recommended)

Starts PostgreSQL and the API together:

```bash
cd backend
docker compose up --build
```

### Option B — Locally with Maven

Requires a running PostgreSQL and a JDK 21+.

```bash
cd backend
# create the database expected by the default config
# (db: enerscope, user: enerscope, password: enerscope)
mvn spring-boot:run
```

Run the tests:

```bash
mvn test
```

See [`../docs/testing.md`](../docs/testing.md) for the test catalog — every test
class and what each case verifies.

Build a jar:

```bash
mvn clean package
java -jar target/enerscope-backend-1.0-SNAPSHOT.jar
```

The API is served under the context path **`/api/v1`** (port `8080`).

## Environment variables

Copy `.env.example` to `.env` and adjust. All variables have sensible dev
defaults, so the app boots without any of them set.

| Variable | Default | Description |
| --- | --- | --- |
| `DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/enerscope` | JDBC URL |
| `DATASOURCE_USERNAME` | `enerscope` | DB user |
| `DATASOURCE_PASSWORD` | `enerscope` | DB password |
| `LOG_LEVEL_ROOT` | `INFO` | Root log level (framework included) |
| `LOG_LEVEL_APP` | `INFO` | App log level (the `AppLogger` output) |
| `JWT_SECRET` | dev placeholder | HMAC signing key (≥ 256 bits). **Change in prod.** |
| `JWT_ACCESS_EXPIRY_MINUTES` | `60` | Access token lifetime |
| `JWT_REFRESH_EXPIRY_DAYS` | `30` | Refresh token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` | Allowed CORS origins |
| `ADMIN_MAIL` | `admin@enerscope.org` | Seeded admin email |
| `ADMIN_PASSWORD` | `admin12345` | Seeded admin password |
| `ADMIN_FIRST_NAME` | `EnerScope` | Seeded admin first name |
| `ADMIN_LAST_NAME` | `Admin` | Seeded admin last name |

## Swagger / OpenAPI

Once running:

- **Swagger UI:** http://localhost:8080/api/v1/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/api/v1/v3/api-docs

`/auth/login`, `/auth/refresh`, `/auth/logout`, `/health` and Swagger are public;
every other endpoint requires a `Bearer` access token, and some also require a
role. Use the **Authorize** button in Swagger UI to paste a token obtained from
`/auth/login`.

## Roles

Every user has a **platform role** (`ADMIN` or `USER`), carried in the access
token and mapped to a Spring Security authority. Registration is not
self-service:

- A platform **`ADMIN`** creates any account via `POST /auth/register` (and may
  set the new account's role).
- An **organization owner** (an org member with `MANAGE_ORGANIZATION`) — or any
  admin — registers users into their organization, one at a time via
  `POST /organizations/{id}/users` or in batch from a CSV via
  `POST /organizations/{id}/users/bulk`.

The seeded `admin@enerscope.org` is the bootstrap `ADMIN`.

## API overview

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | admin | Create an account (any role); returns the created user, not a session |
| `POST` | `/auth/login` | public | Authenticate, returns a session (with the user + role) |
| `POST` | `/auth/refresh` | public | Exchange a refresh token for a new session |
| `POST` | `/auth/logout` | public | No-op server side (client clears tokens) |
| `GET` | `/organizations` | bearer | List organizations (all for admins; own for other users) |
| `POST` | `/organizations` | bearer | Create an organization |
| `POST` | `/organizations/{organizationId}/members` | bearer | Add an existing user to the organization with a role (`OWNER`/`MEMBER`) |
| `POST` | `/organizations/{organizationId}/users` | admin / org owner | Register a **new** user straight into the organization as a `MEMBER` |
| `POST` | `/organizations/{organizationId}/users/bulk` | admin / org owner | Bulk-register users into the organization from a CSV; returns generated credentials |
| `POST` | `/projects` | bearer | Create a project under an organization |
| `POST` | `/projects/{projectId}/members` | bearer | Add a user to the project with a role (`ADMIN`/`EDITOR`) |
| `POST` | `/projects/{projectId}/versions` | bearer | Create a version of a project, optionally under a parent version |
| `GET` | `/health` | public | Liveness probe |

### Bulk user registration (`POST /organizations/{id}/users/bulk`)

Upload a CSV (`multipart/form-data`, form field **`file`**) to create many users
at once and add them **into the organization** as members. Allowed for platform
admins and organization owners (members with `MANAGE_ORGANIZATION`). The file
needs a header row with columns for the email, first name and last name, plus an
optional `role` column. Column names are case-insensitive and a few aliases are
accepted:

- email → `mail`, `email`, `e-mail`, `correo`
- first name → `firstName`, `first_name`, `first name`, `nombre`
- last name → `lastName`, `last_name`, `last name`, `apellido`
- role (optional) → `role`, `rol`, `memberType`, `tipo` — values `OWNER`/`MEMBER`,
  defaulting to `MEMBER` when the column or cell is absent

Example input:

```csv
mail,firstName,lastName,role
jane@example.com,Jane,Doe,MEMBER
john@example.com,John,Roe,OWNER
```

Each valid row is created as a regular platform user with a **securely generated
password** (never stored in plaintext — only the BCrypt hash is persisted) and
added to the organization with the given membership role. The response envelope's
`data` contains a summary plus a `credentialsCsv` field with the
`mail,password` rows to save as a `.csv` and distribute:

```json
{
  "success": true,
  "message": "Processed 2 rows: 2 created, 0 failed",
  "data": {
    "total": 2,
    "created": 2,
    "failed": 0,
    "credentialsCsv": "mail,password\r\njane@example.com,Str0ng!Pass-01\r\n...",
    "failures": []
  }
}
```

Invalid or duplicate rows do not abort the batch: they are skipped and listed in
`failures` (with the line number and reason), never carrying a password.

### Organizations (`POST /organizations/**`)

- `POST /organizations` creates an organization from just a `name`.
- `POST /organizations/{organizationId}/members` adds an existing user
  (`userId`) to the organization with a `memberType` (`OWNER` or `MEMBER`).
  The role's permissions are derived server-side from the type — `OWNER` gets
  `MANAGE_ORGANIZATION` + `VIEW_ORGANIZATION`, `MEMBER` gets
  `VIEW_ORGANIZATION` only. Adding the same user to the same organization
  twice is rejected.
- `POST /organizations/{organizationId}/users` registers a **brand new** user
  (`mail`, `firstName`, `lastName`, `password`) and adds them as a `MEMBER` in
  one step. Allowed for platform admins and organization owners (members with
  `MANAGE_ORGANIZATION`); anyone else gets `403`.

### Projects (`POST /projects/**`)

- `POST /projects` creates a project (`name` + `description`) under an
  organization (`organizationId`). Projects are their own top-level resource,
  not nested under `/organizations`.
- `POST /projects/{projectId}/members` adds an existing user (`userId`) to
  the project with a `memberType` (`ADMIN` or `EDITOR`). The role's
  permissions are derived server-side from the type — `ADMIN` gets
  `MANAGE_PROJECT` + `EDIT_PROJECT` + `VIEW_PROJECT`, `EDITOR` gets
  `EDIT_PROJECT` + `VIEW_PROJECT`. Adding the same user to the same project
  twice is rejected.

### Versions (`POST /projects/{projectId}/versions`)

- `POST /projects/{projectId}/versions` creates a version (`name`) of a
  project, optionally derived from a `parentVersionId`. A `parentVersionId`
  must belong to the same project or the request is rejected. Versions stay
  nested under `/projects` for now — this minimal slice has no sub-resource
  of its own to justify promoting it to a top-level `/versions` resource like
  `Project` was.
- This is a **minimal slice**: node/connection snapshots and the node/
  connection change log from the class diagram (`nodeSnapshot`,
  `connectionSnapshot`, `nodeChanges`, `connectionChanges`) are not modeled.
  Scenarios and project export are also out of scope for now.

All of the above endpoints require a Bearer token like every non-`/auth`
route.

All responses are wrapped in a standard envelope:

```json
{ "success": true, "message": "...", "data": { }, "timestamp": "..." }
```

## Backend structure

```
backend/src/main/java/org/enerscope/
├─ Main.java                 Application entry point
├─ auth/                     Authentication feature
│  ├─ controller/            AuthController
│  ├─ filter/                AuthFilter (JWT bearer filter)
│  └─ dto/                   Login/Register/Refresh/NewSession records
├─ user/                     User feature
│  ├─ service/               UserService, PasswordGenerator
│  ├─ repository/            UserRepository
│  ├─ model/                 User entity
│  │  └─ enums/               PlatformRole (ADMIN/USER)
│  └─ dto/                   UserSummaryDTO
├─ organization/             Organization feature
│  ├─ controller/            OrganizationController
│  ├─ service/               OrganizationService, OrganizationBulkRegistrationService
│  ├─ repository/            Organization/OrganizationMember repositories
│  ├─ model/                 Organization, OrganizationMember, OrganizationMemberRole
│  │  └─ enums/               OrganizationMemberType, OrganizationMemberPermission
│  └─ dto/                   Create/Add/Register request records + response + bulk result records
├─ project/                  Project feature
│  ├─ controller/            ProjectController
│  ├─ service/               ProjectService
│  ├─ repository/            Project/ProjectMember repositories
│  ├─ model/                 Project, ProjectMember, ProjectMemberRole
│  │  └─ enums/               ProjectMemberType, ProjectMemberPermission
│  └─ dto/                   Create/Add request records + response records
├─ version/                  Version feature (minimal slice, no node/connection snapshots yet)
│  ├─ controller/            VersionController
│  ├─ service/               VersionService
│  ├─ repository/            VersionRepository
│  ├─ model/                 Version
│  └─ dto/                   Create request record + response record
├─ session/                  Session feature
│  ├─ model/                 Session (non-persistent)
│  └─ service/               SessionService
├─ common/                   BaseEntity, GlobalExceptionHandler, exceptions, CsvUtil
├─ config/                   Security, CORS, Crypto, JPA, OpenAPI config
├─ health/                   HealthController
├─ jwt/                      JwtService (token issue + validation)
├─ logging/                  AppLogger interface + ConsoleAppLogger
├─ money/                    MoneyAmount value type
├─ seed/                     AdminSeeder (default admin on startup)
└─ util/                     ApiResponse, Responses, AuthUtil

Backend packages are organised by feature, then by layer. Feature packages with
more than one class split into layer subpackages (`controller`, `service`,
`repository`, `model`, `filter`, `dto`) and own their DTOs; single-class or
purely cross-cutting packages (`jwt`, `health`, `money`, `seed`, `logging`,
`util`, `common`, `config`) stay flat. See `AGENTS.md` for the convention.

backend/src/main/resources/
├─ application.properties
└─ db/migration/             Flyway migrations (V1__init.sql, V2__create_nodes.sql, V3__create_organization_tables.sql, V4__create_project_member_tables.sql, V5__create_version_table.sql, V6__add_well_surface.sql, V7__add_platform_role.sql)
```

### Logging

All application logging goes through the `AppLogger` interface
(`org.enerscope.logging`). The default implementation, `ConsoleAppLogger`,
writes to the console. To change how the app logs (JSON, file, remote
collector), provide a different `AppLogger` bean — no call site changes.

`AppLogger` exposes the standard levels — `debug`, `info`, `warn`, `error` —
and code must use the one that fits the event (routine flow → `info`,
diagnostics → `debug`, recoverable problems → `warn`, failures → `error`).
Verbosity is controlled from the environment: `LOG_LEVEL_APP` sets the
application logger level and `LOG_LEVEL_ROOT` the framework level
(`TRACE < DEBUG < INFO < WARN < ERROR < OFF`). For example, set
`LOG_LEVEL_APP=DEBUG` in `.env` to see debug output locally.
