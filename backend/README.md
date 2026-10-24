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
| `POST` | `/auth/register` | admin | Create an account (any role, optional `jobTitle`); returns the created user, not a session |
| `POST` | `/auth/login` | public | Authenticate, returns a session (with the user + role) |
| `POST` | `/auth/refresh` | public | Exchange a refresh token for a new session |
| `POST` | `/auth/logout` | public | No-op server side (client clears tokens) |
| `GET` | `/organizations` | bearer | List organizations (all for admins; own for other users) |
| `POST` | `/organizations` | admin | Create an organization |
| `GET` | `/organizations/{organizationId}/members` | admin / org member | List the organization's members with their identity fields |
| `POST` | `/organizations/{organizationId}/members` | admin / org owner | Add an existing user to the organization with a role (`OWNER`/`MEMBER`) |
| `POST` | `/organizations/{organizationId}/users` | admin / org owner | Register a **new** user straight into the organization as a `MEMBER` |
| `POST` | `/organizations/{organizationId}/users/bulk` | admin / org owner | Bulk-register users into the organization from a CSV; returns generated credentials |
| `GET` | `/projects` | bearer | List projects (all for admins; own memberships for other users). Optional `?organizationId=` filter |
| `POST` | `/projects` | admin / org member | Create a project under an organization the caller belongs to |
| `POST` | `/projects/{projectId}/members` | admin / project admin | Add a user to the project with a role (`ADMIN`/`EDITOR`) |
| `POST` | `/projects/{projectId}/version` | admin / project editor | Create a version of a project, optionally under a parent version |
| `POST` | `/version/createtest` | admin | Create a version detached from every project |
| `GET` | `/version/{id}` | admin / project member | Read a version |
| `PATCH` | `/version/{id}` | admin / project editor | Rename a version or re-parent it |
| `DELETE` | `/version/{id}` | admin / project editor | Delete a version and every version below it |
| `POST` `PATCH` `DELETE` | `/version/{id}/node[/{nodeId}]` | admin / project editor | Add, edit or remove a node in a version |
| `POST` `PATCH` `DELETE` | `/version/{id}/connection[/{connectionId}]` | admin / project editor | Add, edit or remove a connection in a version |
| `PATCH` | `/users/me/password` | bearer | Change your own password (requires the current one) |
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

### Your own account (`PATCH /users/me/password`)

- Changes the caller's password, given the current one. The account is taken
  from the session, **never from the request body**, so the endpoint cannot be
  pointed at another user; an admin resetting someone else's password would be
  a separate endpoint with its own authorization.
- The new password is held to the same minimum as registration (8 characters).
  A wrong current password answers `400 Current password is incorrect`.
- This is what closes the loop on admin-created and bulk-registered accounts,
  which start with a password chosen by somebody else. **There is no screen for
  it yet** — the frontend card is pending, so for now it is reachable from
  Swagger or a client.

### Organizations (`POST /organizations/**`)

- `POST /organizations` creates an organization from just a `name`. **Platform
  admins only**: an organization is an administrative container created *for*
  someone else, which is also why the creator is deliberately not enrolled in
  it. The frontend already gated `/admin/organizations` behind an ADMIN-only
  route, so this only closes the gap on the API side.
- `POST /organizations/{organizationId}/members` adds an existing user
  (`userId`) to the organization with a `memberType` (`OWNER` or `MEMBER`).
  The role's permissions are derived server-side from the type — `OWNER` gets
  `MANAGE_ORGANIZATION` + `VIEW_ORGANIZATION`, `MEMBER` gets
  `VIEW_ORGANIZATION` only. Adding the same user to the same organization
  twice is rejected.
- `GET /organizations/{organizationId}/members` lists the members with the
  fields the team screens show: mail, first/last name, `jobTitle`, `active`,
  membership type and permissions. Readable by a platform admin or by **any**
  member of that organization — seeing who has access is not a management
  action, so it does not require `MANAGE_ORGANIZATION`. Non-members get `403`.
  `active` reflects the user account, not a membership status; there is no
  "invited" state yet.
- `POST /organizations/{organizationId}/users` registers a **brand new** user
  (`mail`, `firstName`, `lastName`, `password`) and adds them as a `MEMBER` in
  one step. Allowed for platform admins and organization owners (members with
  `MANAGE_ORGANIZATION`); anyone else gets `403`.

### Projects (`/projects/**`)

- `GET /projects` lists projects as summaries (name, description, owning
  organization id **and name**, member count, `lastModified`). A platform admin
  gets every project; anyone else gets only the projects they are a **member**
  of — belonging to the owning organization is not enough. `?organizationId=`
  narrows the result to one organization. The rows come from a JPQL projection
  rather than entities, so the lazy `organization`/`members` associations are
  never loaded and the member count is not an N+1.
- `POST /projects` creates a project (`name` + `description`) under an
  organization (`organizationId`). The caller has to **belong to that
  organization** — plain membership, not `MANAGE_ORGANIZATION`, because a
  project is created by the people who are going to work on it. Projects are
  their own top-level resource, not nested under `/organizations`.
- `POST /projects/{projectId}/members` adds an existing user (`userId`) to
  the project with a `memberType` (`ADMIN` or `EDITOR`). The role's
  permissions are derived server-side from the type — `ADMIN` gets
  `MANAGE_PROJECT` + `EDIT_PROJECT` + `VIEW_PROJECT`, `EDITOR` gets
  `EDIT_PROJECT` + `VIEW_PROJECT`. Adding the same user to the same project
  twice is rejected. Restricted to callers holding `MANAGE_PROJECT` on that
  project (its `ADMIN` members) or a platform admin.

### Versions (`POST /projects/{projectId}/version`)

- `POST /projects/{projectId}/version` creates a version (`name`) of a
  project, optionally derived from a `parentVersionId`. A `parentVersionId`
  must belong to the same project or the request is rejected. The handler is
  transactional: the version row and the link to the project are written as one
  unit, so a failure cannot leave a version orphaned. Versions stay
  nested under `/projects` for now — this minimal slice has no sub-resource
  of its own to justify promoting it to a top-level `/versions` resource like
  `Project` was.
- **Snapshots start empty, never null.** A version is created with empty
  `nodeSnapshot`/`connectionSnapshot` lists, so the create response and a later
  read answer the same shape (`[]`), and the in-place mutations the service does
  on those lists cannot hit a null.
- **`/version/**` is guarded through the owning project.** Every one of those
  endpoints takes nothing but a version UUID, so each resolves the project the
  version hangs off (`ProjectRepository.findIdByVersionId`) and checks the
  caller against it: reading needs membership, every mutation needs
  `EDIT_PROJECT`, and a platform admin passes either way.
- **`POST /version/createtest` is platform-admin only.** It creates a version
  attached to no project, and a version with no owning project has no membership
  that could grant access to it — so only an admin can make one, and by the same
  rule only an admin can touch it afterwards. Versions that belong to a project
  are created with `POST /projects/{projectId}/version`.
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

## Economic API

The economic API evaluates project versions, with persisted configuration and
immutable NPV history. See [economic documentation and runnable example](../docs/economic-module.md).
Use the complete migration chain through V15. The incoming master V11 for node columns is redundant with this branch's V12 and is not included. Existing databases require reviewing their Flyway history before deployment; this merge does not reset databases or repair checksums.

Economic routes use `/api/v1/projects/{projectId}/versions/{versionId}/economics`:
`PUT/GET /configuration`, `POST/GET /evaluations`, and `GET /evaluations/{id}`.
Read access requires `VIEW_PROJECT`; writes/calculation require `EDIT_PROJECT`.
Platform administrators also have access. All routes require Bearer authentication.

Run `mvn test` for the standard suite. For real schema/persistence verification,
set `ECONOMIC_TEST_DATABASE_URL` to a disposable `enerscope_economic_test` PostgreSQL
database (optionally `ECONOMIC_TEST_DATABASE_USER` and `ECONOMIC_TEST_DATABASE_PASSWORD`),
then run `mvn -Dtest=PostgreSqlEconomicIT test`. The integration suite runs Flyway
and Hibernate validation and rolls back its example records.

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

## Scenario selection

GET /api/v1/projects/{projectId}/versions returns active version summaries with
node identifiers, names and types. Requires VIEW_PROJECT through an active project
membership/role, or a platform administrator. Responses use ApiResponse.
See [economic frontend](../docs/economic-frontend.md).

Economic drafts: migration V13 adds economic_draft. 
GET/PUT /projects/{projectId}/versions/{versionId}/economics/draft reads/stores 
editor drafts with VIEW_PROJECT/EDIT_PROJECT checks. 
Drafts are structurally validated separately from simulation-ready configuration.

Version-conflict support from master uses V14__create_version_conflict_tables.sql on 
this branch (upstream calls it V12). Review Flyway history before switching an existing
database between these branches.

V15 normalizes legacy export vertical values so existing export nodes remain readable 
after the enum rename. Apply through Flyway; no database reset is needed.

## Probabilistic economic evaluations

POST /api/v1/projects/{projectId}/versions/{versionId}/economics/evaluation-jobs 
accepts {"rounds":10,"seed":"42"} and returns 202 with persisted progress. 
The worker uses economic.worker.enabled (default true) and 
economic.worker.poll-ms (default 1000); deploy one worker instance. 
POST /evaluations remains synchronous and single-round. 
V16 adds jobs/rounds. See [economic module](../docs/economic-module.md) for migration 
reconciliation, restart/retry, permissions and the example script. Use a disposable 
ECONOMIC_TEST_DATABASE_URL for the explicit PostgreSqlEconomicIT suite.

### Updating a running local instance

After pulling backend changes, rebuild the API rather than only starting an old 
container: `docker compose up -d --build --no-deps api`. Keep `DATASOURCE_DB` set to 
the intended database in the local `.env`. The probabilistic frontend requires 
the `/economics/evaluation-jobs` endpoints. Legacy scalar well declines are preserved 
as constant distributions by V17; V18 adds the FLNG capacity column. Do not repair or 
reset historical migrations to bypass incompatibilities.


### Scenario configuration migration and verification 

Fresh PostgreSQL installations use Flyway B18 followed by V19. Existing Flyway histories ignore B18; do not repair or reset databases to adopt it. V19 adds joint scenario drafts. Run `mvn test`, then `mvn -Dtest=PostgreSqlEconomicIT,ScenarioConfigurationPostgreSqlIT test` with ECONOMIC_TEST_DATABASE_URL pointing exclusively to a disposable enerscope_economic_test database. See docs/economic-module.md for the joint configuration/readiness API.

### Project description input

POST `/projects` accepts an omitted, null or blank `description` and stores an empty string. The maximum length remains 500 characters. PATCH accepts an empty description to clear it; an omitted or null description leaves it unchanged.

### Explicit node type 

POST `/version/{versionId}/node` and PATCH `/version/{versionId}/node/{nodeId}` require `type.nodeType`. The backend chooses the concrete DTO using that value, not overlapping physical fields. Supported values are WELL, GATHERING_NETWORK, TREATMENT_PLANT, PIPELINE, COMPRESSING_PLANT, GROUND_LIQUEFACTION_PLANT, FLNG_UNIT, SEAPORT_TERMINAL and LNG_CAMER (existing enum spelling). Missing or unsupported values return 400. Existing editor payloads already contain the discriminator; no migration is required.
