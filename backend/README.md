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

> **Do not run `docker compose down -v`** (or `docker volume rm enerscope_pgdata`). The database lives in the named
> volume `enerscope_pgdata`, mounted at `/var/lib/postgresql/data` of the `db` service, and it survives
> `docker compose down`, `stop` and rebuilds of the API. `down -v` deletes that volume, and with it every
> organization, project, version, scenario, simulation and saved comparison of the local database; there is no
> way back without a backup. The port `5432` of the container is published on the host, so check what else uses it.

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
| `SCENARIOS_MAX_PER_VERSION` | `20` | Maximum scenarios a version may have (read as `enerscope.scenarios.max-per-version`; enforced by `ScenarioService` when a scenario is created or duplicated, under the version's write lock; must be at least 1; the unique name is checked in the same place) |
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
| `GET` | `/organizations` | bearer | List organizations (all for admins; own for other users). `?mine=true` returns only the caller's own memberships, admins included, each with the caller's `memberType` |
| `POST` | `/organizations` | admin | Create an organization |
| `GET` | `/organizations/{organizationId}/members` | admin / org member | List the organization's members with their identity fields |
| `POST` | `/organizations/{organizationId}/members` | admin / org owner | Add an existing user to the organization with a role (`OWNER`/`MEMBER`) |
| `POST` | `/organizations/{organizationId}/users` | admin / org owner | Register a **new** user straight into the organization as a `MEMBER` |
| `POST` | `/organizations/{organizationId}/users/bulk` | admin / org owner | Bulk-register users into the organization from a CSV; returns generated credentials |
| `GET` | `/projects` | bearer | List projects (all for admins; own memberships for other users). Optional `?organizationId=` filter |
| `POST` | `/projects` | admin / org member | Create a project under an organization the caller belongs to |
| `POST` | `/projects/{projectId}/members` | admin / project admin | Add a user to the project with a role (`ADMIN`/`EDITOR`/`EXECUTIVE`) |
| `GET` | `/projects/{projectId}/version-members` | admin / **project admin** | Role matrix: every **active** version of the project with its members and roles, in one call (same member shape as `GET /version/{id}/members`) |
| `GET` | `/projects/{projectId}/access` | admin / project member | What the caller may do on each version of the project (`source`, roles, `can*` flags, `canManageRoles`) |
| `POST` | `/projects/{projectId}/version` | admin / project editor with a parent; **admin / project admin without one** | Create a version of a project, optionally under a parent version; the creator becomes `ADMIN` of a child |
| `POST` | `/version/createtest` | admin | Create a version detached from every project |
| `GET` | `/version/{id}` | admin / project member | Read a version |
| `PATCH` | `/version/{id}` | admin / project editor (re-parenting also needs to manage the version and edit the new parent) | Rename a version or re-parent it |
| `DELETE` | `/version/{id}` | admin / project admin or version admin, of **every** version in the subtree | Delete a version and every version below it, in one transaction (**physical** delete, with its scenarios); `403` (naming no version) and nothing deleted if any is refused; `409` (nothing deleted) when the version or any version below it has simulation jobs, saved evaluations or saved comparison items |
| `POST` `PATCH` `DELETE` | `/version/{id}/node[/{nodeId}]` | admin / project editor | Add, edit or remove a node in a version |
| `POST` | `/version/{versionId}/nodes/import` | admin / project editor | Import up to 10,000 typed nodes, connections and optional economic data from a CSV file (max 10 MB) |
| `POST` `PATCH` `DELETE` | `/version/{id}/connection[/{connectionId}]` | admin / project editor | Add, edit or remove a connection in a version |
| `PATCH` | `/version/merge/{subId}` | admin / admin of the destination version (project or version admin) who may also edit the changed verticals | Merge a subversion into its parent |
| `POST` | `/version/{id}/members` | admin / **project admin** | Grant a user a version role (`ADMIN`/`EDITOR`+vertical/`SIMULATOR`) |
| `GET` | `/version/{id}/members` | admin / **project admin** | List a version's members and their roles |
| `PUT` | `/version/{id}/members/{memberId}/roles/{roleId}` | admin / **project admin** | Change one version role to another type/vertical in place (keeps the role id and the membership) |
| `DELETE` | `/version/{id}/members/{memberId}/roles/{roleId}` | admin / **project admin** | Revoke one version role |
| `GET` | `/version/{id}/member-candidates` | admin / **project admin** | List project members who can be granted a version role |
| `PATCH` | `/users/me/password` | bearer | Change your own password (requires the current one) |
| `GET` | `/health` | public | Liveness probe |

### Node CSV import (`POST /version/{versionId}/nodes/import`)

Upload `multipart/form-data` with the CSV in the **`file`** field. The caller
must have project edit permission. Include a `nodeKind` column with one of the
supported node kinds (`WELL`, `GATHERING_NETWORK`, `TREATMENT_PLANT`,
`PIPELINE`, `PIPELINE_CONNECTION`, `COMPRESSING_PLANT`,
`GROUND_BASED_LIQUEFACTION_PLANT`, `FLNG_UNIT`, `LNG_CARRIER`,
`SEAPORT_TERMINAL`, `INTERNAL_CONSUMPTION`, `INDUSTRIAL_CONSUMPTION`); other
columns use node DTO property names. Nested `type`, `investmentCost`,
`graphData` and `declineCurve` values are JSON objects inside quoted CSV cells.
To import graph connections, add `id`, `recordType`, `fromNodeId` and
`toNodeId` columns. A node's optional `id` is a unique file-local reference key;
the backend still generates its persisted UUID. Add one `CONNECTION` row per
edge, with `fromNodeId` and `toNodeId` set to the external node keys. The
importer saves nodes first, resolves those references to generated IDs, then
saves the connections. The endpoints must refer to nodes in the same file.
For economic data, `recordType` also supports `NODE` rows with an
`economicProfile` JSON object with that node's `ownership` and `rules`, while
one `SCENARIO` row carries an `economicScenario` JSON object containing the
scenario-wide configuration (excluding `nodeProfiles`). Profiles are linked to
the node created from their own row; node UUIDs are generated during import.
The `SCENARIO` row must leave all other columns blank. When profiles are
provided, the scenario row is required. Existing CSV files without `recordType`
continue to import nodes only, and legacy node cost fields are never converted
automatically into economic rules.

The upload is limited to 10 MB and 10,000 data rows, and any invalid node or
economic data rolls back the entire import. The response count includes only
nodes, not connection or scenario rows. Invalid input returns `400`; a request
over the server multipart limit returns `413`.

### Bulk user registration (`POST /organizations/{id}/users/bulk`)

Upload a CSV (`multipart/form-data`, form field **`file`**) to create many users
at once and add them **into the organization** as members. Allowed for platform
admins and organization owners (members with `MANAGE_ORGANIZATION`). The file
needs a header row with columns for the email, first name and last name, plus
optional `role` and `jobTitle` columns, and **at most 500 data rows** (the header
does not count): a larger file answers `400 The CSV file has more than 500
rows`, after the caller is authorized and before any row is created. The file
itself is capped at **1 MB** (`spring.servlet.multipart.max-file-size=1MB`,
`max-request-size=2MB`, set explicitly): a larger one answers `413 The uploaded
file exceeds the maximum size of 1 MB` (`GlobalExceptionHandler`), not `500`.
Spring rejects an oversized multipart before the request reaches the
controller, so the `413` comes **before** any authorization `403`: a caller who
may not use the endpoint still sees `413` for a too-large file. The web client
splits larger imports into batches of 50 rows. Column names are
case-insensitive and a few aliases are accepted:

- email → `mail`, `email`, `e-mail`, `correo`
- first name → `firstName`, `first_name`, `first name`, `nombre`
- last name → `lastName`, `last_name`, `last name`, `apellido`
- role (optional) → `role`, `rol`, `memberType`, `tipo` — values `OWNER`/`MEMBER`,
  defaulting to `MEMBER` when the column or cell is absent
- job title (optional) → `jobTitle`, `job_title`, `job title`, `puesto`, `cargo` —
  up to 120 characters; a longer one fails that row, a blank one is stored as none

Example input:

```csv
mail,firstName,lastName,role,jobTitle
jane@example.com,Jane,Doe,MEMBER,Analyst
john@example.com,John,Roe,OWNER,Project lead
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
`failures` (with the line number — within the uploaded file, the header being
line 1 — and the reason), never carrying a password.

### Your own account (`PATCH /users/me/password`)

- Changes the caller's password, given the current one. The account is taken
  from the session, **never from the request body**, so the endpoint cannot be
  pointed at another user; an admin resetting someone else's password would be
  a separate endpoint with its own authorization.
- The new password is held to the same minimum as registration (8 characters).
  A wrong current password answers `400 Current password is incorrect`.
- This is what closes the loop on admin-created and bulk-registered accounts,
  which start with a password chosen by somebody else. The web client
  reaches it from the profile screen (`ProfilePage` → `ChangePasswordForm`).

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
  the project with a `memberType` (`ADMIN`, `EDITOR` or `EXECUTIVE`). The role's
  permissions are derived server-side from the type — `ADMIN` gets
  `MANAGE_PROJECT` + `EDIT_PROJECT` + `VIEW_PROJECT`, `EDITOR` gets
  `EDIT_PROJECT` + `VIEW_PROJECT`, `EXECUTIVE` gets `VIEW_PROJECT` only. Adding
  the same user to the same project twice is rejected. Restricted to callers
  holding `MANAGE_PROJECT` on that project (its `ADMIN` members) or a platform
  admin.
- **`EXECUTIVE` is a read-only project member.** With only `VIEW_PROJECT` it
  can read the project, its members, every version, the diagram and any node,
  and the economic configuration, draft, scenario, evaluations and jobs; the
  project access answers `canView` and nothing else (`effectiveRole`
  `EXECUTIVE`, `source` `PROJECT_FALLBACK`). It gets `403` on everything that
  writes — nodes, connections, creating, renaming or deleting versions, saving
  the configuration, draft or scenario, simulating and retrying jobs — and on
  managing project members, listing member candidates and version roles. Only a
  project or platform `ADMIN` assigns or changes the type (an `EDITOR` gets
  `403`); changing the last `ADMIN` into an `EXECUTIVE` is refused like any
  other demotion. `GET /projects` reports the caller's role in each project as
  `myRole`, so for an executive it is `EXECUTIVE` (`ADMIN` still wins when a
  membership somehow carries two roles). No migration: `member_type` is a
  free `VARCHAR(30)` with no `CHECK`.
- **An `EXECUTIVE` cannot hold version roles.** `POST
  /version/{id}/members` and `PUT /version/{id}/members/{memberId}/roles/{roleId}`
  answer `400 An EXECUTIVE of the project cannot hold version roles` for any
  role type (after the `403`/`404` checks, which keep their order). Turning an
  existing member into an `EXECUTIVE` (`PATCH /projects/{id}/members/{memberId}`)
  **deletes all of that user's version memberships in that project**, in the
  same transaction, and logs how many; changing between `ADMIN` and `EDITOR`
  leaves them alone. Going back to `EDITOR` does not restore them.
  Tests: [`docs/testing.md`](../docs/testing.md) → *Project member type
  EXECUTIVE*.

### Versions (`POST /projects/{projectId}/version`)

- `POST /projects/{projectId}/version` creates a version (`name`) of a
  project, optionally derived from a `parentVersionId`. A `parentVersionId`
  must belong to the same project or the request is rejected. The handler is
  transactional: the version row and the link to the project are written as one
  unit, so a failure cannot leave a version orphaned. Versions stay
  nested under `/projects` for now — this minimal slice has no sub-resource
  of its own to justify promoting it to a top-level `/versions` resource like
  `Project` was.
### Node structural role

`type.role` (`GENERATOR`, `INTERMEDIATE`, `SINK`) is **derived from `type.nodeType`**
on the server, the same way `type.vertical` is: `WELL` is `GENERATOR`;
`GATHERING_NETWORK`, `TREATMENT_PLANT`, `PIPELINE`, `PIPELINE_CONECTION`,
`COMPRESSING_PLANT`, `GROUND_LIQUEFACTION_PLANT` and `FLNG_UNIT` are
`INTERMEDIATE`; `SEAPORT_TERMINAL`, `LNG_CAMER`, `INTERNAL_CONSUMPTION` and
`INDUSTRIAL_CONSUMPTION` are `SINK` (`NodeTypeEnum.role()`). `role` is optional
in the request; if sent and different, create and edit (`/nodes/*` and
`/version/{id}/node`) answer `400 Node role does not match its type: …`.
Cloning a node (branches) recomputes the role from the type; with a `null` type
it copies the stored role, since there is nothing to derive it from.

### FLNG unit attributes

`GET /version/{id}/node/{nodeId}` returns `attributes.shipCapacity` (the vessel)
and `attributes.gasConsumption` (its own gas use) for a `FLNG_UNIT`; they are
independent and `PATCH /version/{id}/node/{nodeId}` stores each from its own
field. Before `3d19cd7` an edit copied `shipCapacity` into `gasConsumption` and
the detail returned neither, so FLNG edited earlier may hold equal values; no
migration repairs them. Known gaps in the node DTOs (not fixed): they carry no
`@Valid`/`@NotNull`, numeric wrapper fields (`Integer`/`Float`) unbox into
primitive entity fields and throw `NullPointerException` when `null`, and
`TreatmentPlant.contaminantWaste` is accepted but not persisted (reads back as
`0`). Details: [`docs/considerations.md`](../docs/considerations.md).

### Version members (`/version/{id}/members`, `/version/{id}/member-candidates`)

- **Project administrators administer every version.** An active project member
  holding `MANAGE_PROJECT` (the project `ADMIN`) can do everything on every
  version of the project, whatever version roles they hold; their explicit roles
  are kept and shown but never narrow them (`GET /projects/{id}/access` reports
  the source as `PROJECT_ADMIN`). A platform `ADMIN` can do everything too.
- For everybody else, version-level roles only come into play for someone who
  has **no** role on the version at all: with none, access falls back to project
  membership. The moment a non-admin holds *any* role on a version, that role is
  the **sole** source of truth for what they may do on it — it is never topped
  up by what their project role would otherwise allow. A `SIMULATOR` cannot edit
  the version's name, a node or a connection even though every project member
  can; an `EDITOR` scoped to `EXTRACTION` cannot touch a `TRANSPORTATION` node.
  A role only counts while its holder is an **active** member of the project.
- Three roles: `ADMIN` (delete the version, merge a subversion into it,
  resolve its conflicts, re-parent it — **not** manage who has access, which is
  reserved to project and platform admins), `EDITOR` (scoped to exactly one `vertical` — e.g.
  `EXTRACTION` — plus `INTERNAL_CONSUMPTION` nodes and connections, which any
  `EDITOR` may touch regardless of their assigned vertical), and `SIMULATOR`
  (view and simulate; no editing). Every role grants read access to the
  version.
- Unlike a project member, a version member may hold **any number of roles
  at once** — e.g. `EDITOR` for two verticals plus `SIMULATOR`. `POST
  /version/{id}/members` always **adds** a role (`userId`, `memberType`,
  `vertical` — required for `EDITOR`, forbidden otherwise). `PUT
  /version/{id}/members/{memberId}/roles/{roleId}` (`memberType`, `vertical`)
  **changes one role in place**, in one transaction: the role keeps its id, the
  membership is never removed (revoke + grant would delete it when it was the
  last role) and the permissions become those of the new type. Order of
  answers: `403` (same guard as granting; a version `ADMIN`, `EDITOR` or
  `SIMULATOR` included) → `404` (version, member or role not on that version)
  → `400` (shape, or the member already holds the resulting role — the same
  `User already has this role` as `POST`). Asking for the role's current shape
  is a no-op (`200`, nothing saved). `DELETE
  /version/{id}/members/{memberId}/roles/{roleId}` revokes one role; once a
  member's last role is revoked, the membership itself is removed.
- **The `EDITOR` role may simulate through a stored permission.** Its default
  permissions are `EDIT_VERSION`, `SIMULATE_VERSION` and `VIEW_VERSION`; the old
  rule in code (`VersionAccess.grantedByRoles`) is gone and migration `V24`
  backfills the permission on existing `EDITOR` rows. **Deploy the migration and
  this code together**: the new code on a database without `V24` would stop
  letting existing editors simulate. Validate it with
  `VersionMemberRepositoryPostgreSqlIT.v24BackfillsEditorSimulatePermissionAndIsIdempotent`
  (run by hand: `ECONOMIC_TEST_DATABASE_URL=… mvn -B verify`).
- **`GET /projects/{projectId}/version-members` returns the whole matrix.** One
  entry per **active** version (`versionId`, `versionName`, `members`), each
  `members` being exactly what `GET /version/{id}/members` returns, so the UI
  needs one call instead of one per version. Same callers (project `ADMIN`,
  platform `ADMIN`); `403` comes before `404`, so only a platform `ADMIN` is told
  a project does not exist. It runs one query for members, users and roles; the
  roles' permissions are loaded with `@BatchSize`, so the statement count does
  not grow with members or roles (pinned by
  `VersionMemberRepositoryPostgreSqlIT`, by hand).
- **Only a project `ADMIN` or a platform `ADMIN` may grant, change, revoke or list
  roles** (and list the candidates): `403 You are not allowed to manage the
  roles of this version` for anyone else, a version `ADMIN` included. The
  permission is checked **before** the version is looked up, so for anyone but a
  platform `ADMIN` an unknown version id answers `403`, never `404`; only a
  platform `ADMIN` (who skips the check) gets `404`, so the endpoints no longer
  reveal whether a version exists.
- Granting a role requires the target user to already be an active member of
  the version's owning project — `GET /version/{id}/member-candidates` lists
  exactly those users.
- Creating a child version **copies the parent's roles** to the child, and its
  creator becomes `ADMIN` of the child (a project or platform admin gets no row:
  they already administer it). A version with no parent (a root) can only be
  created by a project or platform `ADMIN`.
- Deleting a version deletes everything below it and needs permission to
  administer **every** version in the subtree; a descendant of another project
  (or of none) can only be deleted by a platform `ADMIN`.
- **A version role counts only while the project and the version are active.**
  `VersionMemberRepository.findEffectiveByVersionIdAndUserId` requires an active
  project, an active version and an active project membership of the holder, so
  deactivating the version or the project cuts off its role holders (`403` on
  every read); a platform `ADMIN` still reads a deactivated version.
  `VersionService.getVersion` and `VersionConflictService.getConflictsForVersion`
  are `@Transactional(readOnly = true)`, so the guard works without an open
  persistence session (see the `open-in-view` debt in `docs/considerations.md`).
- `GET /projects/{id}/access` reports `canCreateRootVersions` (it was
  `canCreateVersions`): true for the project `ADMIN` (`MANAGE_PROJECT`) and the
  platform `ADMIN`, false for a project `EDITOR` — who can still branch from a
  version they may edit.
- **Economic configuration and drafts answer `403` before `400`.**
  `EconomicService.save` and `saveDraft` run the per-vertical check
  (`ScenarioChangeScope`) before the business/structural validation, so an
  editor who changes another vertical's node and also sends an invalid value
  gets `403`, not `400`.
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
- A `Version` owns its diagram (`nodeSnapshot`, `connectionSnapshot` and the change logs) and its **scenarios**
  (see [Scenario API](#scenario-api)). Project export is out of scope for now.

All of the above endpoints require a Bearer token like every non-`/auth`
route.

All responses are wrapped in a standard envelope:

```json
{ "success": true, "message": "...", "data": { }, "timestamp": "..." }
```

## Economic API

The economic API evaluates project versions, with persisted configuration and
immutable NPV history. See [economic documentation and runnable example](../docs/economic-module/economic-module.md).
Use the complete migration chain (V1–V28, 28 files, V8.5 included). The incoming master V11 for node columns is redundant with this branch's V12 and is not included. Existing databases require reviewing their Flyway history before deployment; this merge does not reset databases or repair checksums.

Economic routes use `/api/v1/projects/{projectId}/versions/{versionId}/economics`:
`PUT/GET /configuration`, `POST/GET /evaluations`, and `GET /evaluations/{id}`.
Read access requires `VIEW_PROJECT`; writes/calculation require `EDIT_PROJECT`.
Platform administrators also have access. All routes require Bearer authentication.

Run `mvn test` for the standard suite. For real schema/persistence verification,
set `ECONOMIC_TEST_DATABASE_URL` to a disposable `enerscope_economic_test` PostgreSQL
database (optionally `ECONOMIC_TEST_DATABASE_USER` and `ECONOMIC_TEST_DATABASE_PASSWORD`),
then run `mvn -Dtest=PostgreSqlEconomicIT test`. The integration suite runs Flyway
and Hibernate validation and rolls back its example records.

Two levels of tests (counts in [`docs/testing.md`](../docs/testing.md)):

- `mvn test` — the `*Test` classes on H2; no database needed (last reported run: 107 classes, 1813 executions).
- `mvn -B verify` — also runs the eleven `*IT` classes with failsafe (last reported run: 11 classes, 108 executions)
  against a **disposable** PostgreSQL named `enerscope_economic_test`, pointed to by `ECONOMIC_TEST_DATABASE_URL`
  (`ECONOMIC_TEST_DATABASE_USER` / `ECONOMIC_TEST_DATABASE_PASSWORD` if needed). The ITs are
  `PostgreSqlEconomicIT`, `ScenarioConfigurationPostgreSqlIT`, `ProjectRepositoryPostgreSqlIT`,
  `ProjectVersionCreationPostgreSqlIT`, `VersionMemberRepositoryPostgreSqlIT`, `ScenarioMigrationIT`,
  `ScenarioLifecyclePostgreSqlIT`, `VersionDefaultScenarioPostgreSqlIT`, `VersionDeletionPostgreSqlIT` and
  `VersionNodeCsvImportIdentityPostgreSqlIT` and `VersionNodeCsvImportTemplatesPostgreSqlIT`.
  Never point them at a database with real data. CI runs the same `mvn --batch-mode verify` in the `backend-it` job,
  and since it no longer has `continue-on-error`, a failing `*IT` fails the build.

## Scenario API

A **scenario** is the economic and simulation configuration that rests on a **version** (the physical base). Every version gets its default scenario **when it is created** (root, branch or detached): a branch does not inherit its parent's scenarios or economics, it starts with its own empty "Escenario base". A
version holds up to **20** scenarios (`enerscope.scenarios.max-per-version` / `SCENARIOS_MAX_PER_VERSION`), each with
a name the person types, unique in the version ignoring case. Every version has one *default* scenario, "Escenario
base", which the old `/versions/{versionId}/...` routes keep acting on. Creating a scenario never creates a version
(`POST /projects/{id}/version` still creates a version only). Routes (Swagger documents each one):

| Method and path | Needs | Notes |
| --- | --- | --- |
| `GET /projects/{p}/versions/{v}/scenarios` | `VIEW_PROJECT` | Active scenarios, oldest first |
| `POST /projects/{p}/versions/{v}/scenarios` | `EDIT_PROJECT` + edit the version | Empty scenario; body `{"name"}`; `201` |
| `GET /projects/{p}/scenarios/{s}` | `VIEW_PROJECT` | |
| `PATCH /projects/{p}/scenarios/{s}` | `EDIT_PROJECT` + edit the version | Rename |
| `POST /projects/{p}/scenarios/{s}/duplicate` | `EDIT_PROJECT` + edit the version | Copies only the economic configuration; needs the vertical rights its rules need; `201` |
| `DELETE /projects/{p}/scenarios/{s}` | `EDIT_PROJECT` + administer the version | Logical; `409` for the last scenario or a queued/running simulation; deleting the default promotes the oldest remaining |
| `GET`/`PUT /projects/{p}/scenarios/{s}/configuration`, `DELETE .../configuration/draft` | view / edit the version | Economic configuration and draft; node values are **not** saved here (`400`) |
| `POST`/`GET .../simulations`, `GET .../simulations/{job}`, `POST .../simulations/{job}/retry` | simulate the version / view | `202` for POST |
| `GET .../evaluations`, `.../evaluations/{id}`, `.../evaluations/{id}/rounds[/{n}]` | `VIEW_PROJECT` | Results of that scenario only |

Order of answers: `403` (project permission) → `404` (unknown, deleted, in an inactive version or in another
project: one generic "Scenario not found") → `403` (capability on the version) → `400` (name or body) → `409`
(repeated name, limit reached, deletion refused, stale token). Roles: the read-only Ejecutivo reads only; the
Simulador reads and simulates; the version Editor creates, renames, duplicates, configures (within its vertical)
and simulates; deleting needs the version administrator, the project administrator or a platform administrator.

Fields and states worth knowing:

- Each scenario in the responses carries `configured`, `draftPending`, `defaultScenario`, `latestEvaluatedAt` and a
  `status` (`ScenarioStatus`): `UP_TO_DATE` (its latest simulation matches its configuration **and** its version's
  nodes and connections), `OUTDATED` (either changed since), `NEVER_SIMULATED` or `SIMULATING` (a job is queued or
  running). The API does not say *which* of the two changed. **Editing a node (or a connection) makes every scenario
  of its version `OUTDATED`**; editing one scenario's economics outdates only that one.
- "Operación" (node values) belongs to the version: `PUT …/scenarios/{s}/configuration` never saves node values. A
  request whose `operations` would change them is refused with `400`. A *stale* request is a `409` first.
- `GET …/scenarios/{s}/configuration` returns `draftOperationsIgnored: true` when a stored draft (written before
  node values moved to the version) carries node values that differ from the version's current nodes; the draft is
  kept, but those values are not applied. The screen explains it and offers to discard the draft.
- The old `/projects/{p}/versions/{v}/…` economics routes are kept and always act on the version's **default**
  scenario (the shared rules live in one place, behind both route families).
- Deleting a scenario only deactivates it: its evaluations, jobs and the comparisons that used it stay. Deleting a
  **version**, in contrast, is physical (and refused with `409` when it has results; see the API overview).

## Scenario comparison API

Compares the latest, up-to-date simulations of two to eight **scenarios** of one
project — of the same version or of different ones — with each other (no reference): financial indicators (NPV,
IRR, ROI, ROE, paybacks), costs (CAPEX/OPEX), cash totals and physical flows, with
each scenario's position and distance to the best, and the relevant differences
flagged. See
[scenario comparison](../docs/scenario-comparison.md).

Routes use `/api/v1/projects/{projectId}/comparisons`: `GET /scenarios` (every scenario, grouped by version, with the status of
its own latest simulation), `POST /preview`, `POST /` (compare and save the
run), `GET /`, `GET /{comparisonId}` and `DELETE /{comparisonId}`. Items are `{"scenarioId"}` (`{"versionId"}` is deprecated and
means that version's default scenario). `403` for the whole request without `VIEW_PROJECT`; a generic `404` for an unknown,
deleted or other-project scenario; then `400`. A scenario that changed since its last simulation is refused until it is simulated
again. Saving needs `EDIT_PROJECT` (the Ejecutivo only previews; the Simulador can save). V25 adds the `scenario_comparison` and
`scenario_comparison_item` tables; **V28** stores the version and scenario names in each item, so a saved comparison keeps
opening, with its data and an `deleted` flag, after a scenario or version is deleted.

Demo data to try it (two projects, four scenarios ready to simulate): `node docs/examples/seed-comparison-demo.mjs [--simulate]`
from the repository root against a local backend — see [demo data](../docs/scenario-comparison.md#demo-data).

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
└─ db/migration/             Flyway migrations V1–V28 (28 files, V8.5 included; V26–V28 are the scenario migrations)
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

## Version selection

GET /api/v1/projects/{projectId}/versions returns active version summaries with
node identifiers, names and types. Requires VIEW_PROJECT through an active project
membership/role, or a platform administrator. Responses use ApiResponse.
See [economic frontend](../docs/economic-module/economic-frontend.md).

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
V16 adds jobs/rounds. See [economic module](../docs/economic-module/economic-module.md) for migration 
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

Fresh PostgreSQL installations execute the individual V1–V28 migrations (28 files, V8.5 included). The former B18 snapshot is archived outside Flyway in docs/migration-reconciliation. Databases that recorded B18 require a separate compatibility review before upgrading; do not repair or reset their history automatically. V19 adds joint scenario drafts. Run `mvn test`, then `mvn -Dtest=PostgreSqlEconomicIT,ScenarioConfigurationPostgreSqlIT test` with ECONOMIC_TEST_DATABASE_URL pointing exclusively to a disposable enerscope_economic_test database. See docs/economic-module/economic-module.md for the joint configuration/readiness API.

### Project description input

POST `/projects` accepts an omitted, null or blank `description` and stores an empty string. The maximum length remains 500 characters. PATCH accepts an empty description to clear it; an omitted or null description leaves it unchanged.

### Start-up date and maintenance duration

Editing a node keeps its stored `startupDate` and `maintenanceDuration` when the request omits them (a present value
updates them; a negative duration is `400`). **A start-up date cannot be cleared by sending `null`**: "absent" and
"clear it" are the same thing for the edit endpoints. See `docs/considerations.md` (2026-11-22, *E3b*).

### Explicit node type 

POST `/version/{versionId}/node` and PATCH `/version/{versionId}/node/{nodeId}` require `type.nodeType`. The backend chooses the concrete DTO using that value, not overlapping physical fields. Supported values are WELL, GATHERING_NETWORK, TREATMENT_PLANT, PIPELINE, COMPRESSING_PLANT, GROUND_LIQUEFACTION_PLANT, FLNG_UNIT, SEAPORT_TERMINAL and LNG_CAMER (existing enum spelling). Missing or unsupported values return 400. Existing editor payloads already contain the discriminator; no migration is required.

### Migration reconciliation 

Use docker compose up -d --build api to compile current sources and wait for the database health check. Pending migrations continue through V28 (28 files, V8.5 included). Previously published V2/V8.5/V9 retain their historical contents; incoming alternatives are preserved in docs/migration-reconciliation outside Flyway discovery. Do not repair checksums or reset existing databases to resolve a conflicting migration number.
