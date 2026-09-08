# Considerations & expectations

A running log of decisions, assumptions and things future work should keep in
mind. **Read this before making changes, and append to it when you introduce
something non-obvious.** Keep entries concise, dated, and human-readable.

Format: `- YYYY-MM-DD — <note>` (newest at the bottom of each section).

## Current expectations

- The `User` model carries `mail`, `firstName`, `lastName`, `passwordHash` and a
  `platformRole` (`ADMIN`/`USER`) — plus `BaseEntity` audit fields. There is
  still **no phone number**. The seeded `admin@enerscope.org` is an `ADMIN`.
  `platformRole` is the app-wide role; organization/project membership roles are
  scoped and separate.
- Authentication is **stateless** (JWT). There is no session table; a `Session`
  is rebuilt from the token on each request. Revocation before expiry is not
  supported — keep access-token lifetimes short.
- Flyway owns the schema; Hibernate runs in `validate`. The H2 profile used by
  tests disables Flyway and lets Hibernate build the schema — keep entities
  portable enough for both, or adjust the test profile deliberately.
- The frontend now has the **auth portal**: an `AuthProvider`/`useAuth`
  (login/register/logout/refresh + role state), reusable `LoginForm`/
  `RegisterForm`, role-gated routes and admin/user panels. Design tokens live in
  `src/index.css` under Tailwind v4 `@theme` (brand greens, ink, cream — from the
  logo); reuse `bg-brand-*`/`text-ink-*`/`bg-cream` instead of raw hex. Tailwind
  only; no hand-written CSS beyond those tokens.

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
  **authenticated** (no `/auth` permitAll). As of 2026-08-30 this global endpoint
  was **removed** and replaced by an organization-scoped bulk endpoint (see that
  log entry). CSV parsing/writing uses a small in-repo helper
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
- 2026-08-22 — SCRUM-35 (ABM de Organización): added `organization/{model,
  repository,service,controller,dto}` following the `user`/`auth` layering,
  with `V3__create_organization_tables.sql` (`V2` is already taken twice by
  `V2__create_all_tables.sql`/`V2__create_nodes.sql`, so new migrations start
  at `V3`). Modeling decisions:
  - `OrganizationMember` is a join **entity** between `Organization` and
    `User` (not a direct `@ManyToMany`) because membership needs its own
    roles/permissions.
  - `OrganizationMemberRole` belongs to exactly one `OrganizationMember` (not
    a shared role catalog) — each membership owns its own role instance(s),
    per the provided class/ER diagrams.
  - The "add member" endpoint (`POST /organizations/{id}/members`) takes only
    `userId` + `memberType` (enum `OWNER`/`MEMBER`). The role's `name` and
    `permissions` are derived server-side from a fixed
    `memberType → permissions` map in `OrganizationService`
    (`OWNER` → `MANAGE_ORGANIZATION` + `VIEW_ORGANIZATION`, `MEMBER` →
    `VIEW_ORGANIZATION` only). There is **no API yet** to define a custom role
    name or a custom permission set — if that's needed later, extend
    `AddOrganizationMemberRequestDTO` instead of hardcoding more types.
  - `OrganizationMemberType` (`OWNER`, `MEMBER`) and
    `OrganizationMemberPermission` (`MANAGE_ORGANIZATION`,
    `VIEW_ORGANIZATION`) are intentionally minimal — the class diagram didn't
    pin concrete values, so these were chosen as the smallest set covering the
    ticket's scope. Extend them (with a migration, since permissions are
    persisted as strings) if a future ticket needs finer-grained roles.
  - Creating an organization does **not** auto-add the creator as a member —
    the ticket lists "create organization" and "add user" as separate steps,
    so that's how they're implemented. Revisit if product wants the creator
    to become `OWNER` automatically.
  - `Project` only has `name`/`description`/`organization` for this ticket —
    `members`/`versions` from the class diagram belong to a later
    ticket/module and were deliberately left out.
  - `(organization_id, user_id)` has a DB-level unique constraint (and a
    matching `@UniqueConstraint` on the entity) in addition to the
    application-level `existsByOrganizationIdAndUserId` check, mirroring how
    `app_user.mail` enforces uniqueness at both levels.
  - `gen_random_uuid()` (used by `id DEFAULT` on every new table) needs no
    extension: it's a PostgreSQL core builtin since v13, and
    `docker-compose.yml` pins `postgres:16-alpine`. Same as the pre-existing
    `V1`/`V2` migrations, which already rely on it without a `CREATE
    EXTENSION`.
- 2026-08-22 — Project ABM: moved `Project` out of
  `organization/` into its own `project/{model,repository,service,controller,
  dto}` package (new `ProjectService`, `ProjectController`), and added
  `project/model/{ProjectMember,ProjectMemberRole}` +
  `project/model/enums/{ProjectMemberType,ProjectMemberPermission}`, mirroring
  the `organization/` membership pattern from SCRUM-35. Migration
  `V4__create_project_member_tables.sql` (`V3` was already taken by the
  organization tables). Modeling decisions:
  - Project creation moved from `POST /organizations/{organizationId}/projects`
    to `POST /projects` (with `organizationId` in the body): now that `Project`
    has its own ABM (members with roles), it stands as a top-level resource
    instead of staying nested under `/organizations`. `OrganizationService`/
    `OrganizationController` no longer own project creation; `projectRepository`
    was removed from `OrganizationService`. `Organization.addProject` still
    exists as the domain method `ProjectService` calls (same relationship
    `OrganizationService.addMember` has with `Organization.addMember`).
  - `Project.members: List<ProjectMember>` was added (`@OneToMany
    mappedBy="project"`, cascade `ALL` + orphan removal), same shape as
    `Organization.members`. This is the field the class diagram already showed
    on `Project` but SCRUM-35 deliberately left out as future work.
  - `ProjectMemberType` is `ADMIN`/`EDITOR` — different vocabulary from
    `OrganizationMemberType`'s `OWNER`/`MEMBER` on purpose, per the ticket's
    own wording ("admin"/"modificador").
  - `ProjectMemberPermission` has **three** values (`MANAGE_PROJECT`,
    `EDIT_PROJECT`, `VIEW_PROJECT`) — one more than
    `OrganizationMemberPermission`'s two. This was needed so `EDITOR` gets an
    actual "can modify" permission distinct from `ADMIN`'s "can manage
    membership" permission; with only two values (mirroring organizations
    exactly) `EDITOR` would have ended up view-only, which contradicts the
    role's name. Mapping in `ProjectService`: `ADMIN` → all three, `EDITOR` →
    `EDIT_PROJECT` + `VIEW_PROJECT`. Same as organizations, there is no API yet
    to customize this mapping.
  - Same as `organization_member`, `(project_id, user_id)` has a DB-level
    unique constraint plus an application-level
    `existsByProjectIdAndUserId` check.
  - Versions/scenarios (`Version`, `NodeChange`, `ConnectionChange`) and
    project export remain explicitly out of scope for this ticket — not
    modeled, not stubbed. (A minimal `Version` entity was added afterwards;
    see the entry below.)
- 2026-08-22 — Minimal `Version` entity: added `version/{model,repository,
  service,controller,dto}` with only `name`, `project` (`@ManyToOne`) and
  `parentVersion` (self-reference `@ManyToOne`, nullable). Migration
  `V5__create_version_table.sql` — a single table, no join tables, no enums.
  `Project.versions: List<Version>` + `Project.addVersion(...)` added,
  mirroring `Project.members`/`addMember`. Modeling decisions:
  - No dedicated `creationDate` field — reuses `createdAt` inherited from
    `BaseEntity`, like every other entity in the codebase (`User`,
    `Organization`, `Project` don't redeclare it either), even though the
    class diagram lists `creationDate` as an explicit attribute of `Version`.
  - Endpoint stays nested: `POST /projects/{projectId}/versions` (not
    promoted to a top-level `/versions` resource like `Project` was). Unlike
    `Project`, this minimal `Version` has no sub-resource of its own yet
    (no "add X to version" endpoint) to justify promotion — revisit if/when
    one is added.
  - `VersionService.createVersion` rejects a `parentVersionId` that resolves
    to a version belonging to a different project than the one being created
    under (`IllegalArgumentException`, same style as every other domain
    validation in the codebase).
  - **Explicitly still out of scope**, and blocked on the same two issues
    already documented above: `nodeSnapshot`/`connectionSnapshot`
    (`VersionXNode`/`VersionXConnection` join tables) and
    `nodeChanges`/`connectionChanges` (`NodeChange`/`ConnectionChange`).
    Investigation found `node/model/NodeChange.java` and
    `node/model/ConnectionChange.java` are plain classes — no `@Entity`, no
    `@Id`, not persistable despite carrying JPA annotations — and that
    `node/`'s migrations have a Flyway version collision
    (`V2__create_all_tables.sql` and `V2__create_nodes.sql` both claim
    version `2`, which `spring.flyway.locations=classpath:db/migration`
    would load together against a real Postgres; the H2 test profile never
    exercises this because it disables Flyway). Neither is fixed by this
    change; both must be resolved before a `Version` with real node/
    connection snapshots can be built.
- 2026-08-30 — Platform roles + auth portal. Added `PlatformRole` (`ADMIN`/
  `USER`) to `User` (`V7__add_platform_role.sql`; column defaults `USER`, the
  default admin mail is promoted to `ADMIN`, and `AdminSeeder` now seeds the
  admin as `ADMIN`). The access-token gained a `role` claim; `AuthFilter` maps it
  to a `ROLE_*` authority so `SecurityConfig`/method security can gate on it.
  Decisions:
  - **Registration is no longer self-service.** `POST /auth/register` now
    requires an `ADMIN` (returns the created `UserSummaryDTO`, **not** a session —
    the admin stays logged in), and `POST /users/bulk` is likewise `ADMIN`-only.
    `RegisterRequestDTO` gained an optional `role` (admins can mint admins;
    defaults to `USER`; bulk always passes `USER`).
  - **Org-owner registration:** `POST /organizations/{id}/users` creates a
    `USER` account and adds it to the org as a `MEMBER`. Authorized for platform
    admins or an `OrganizationMember` with `MANAGE_ORGANIZATION` (checked in
    `OrganizationService` via `AuthUtil.currentSession()` + a new
    `findByOrganizationIdAndUserId`). Added `ForbiddenException` → HTTP `403` in
    `GlobalExceptionHandler`, and `SecurityConfig` now returns the `ApiResponse`
    envelope for `401`/`403` (auth entry point + access-denied handler).
  - `NewSessionDTO` now embeds a `UserSummaryDTO` (id/mail/name/role) so the
    frontend gets identity + role at login/refresh without decoding the JWT.
  - **Frontend:** `react-router-dom` added; `AuthProvider`/`useAuth`, reusable
    `LoginForm`/`RegisterForm` (the latter drives both the platform-admin and
    organization flows via props), `ProtectedRoute`/`RoleRoute` guards, and
    `AdminPanel`/`UserPanel`. Brand design tokens in `src/index.css` (`@theme`).
  - **Carries the migration prerequisites so this PR merges independently** of
    the migration-fix PR (`fix/migration-collision-check`), in any order: it
    removes the duplicate `V2__create_all_tables.sql` and adds the
    `well.surface` fix as `V6` — the same identical changes as that PR, so git
    merges them cleanly whichever lands first. This branch also adds
    `V7__add_platform_role.sql`. `V2__create_nodes.sql` is the surviving `V2`
    (matches the `@Inheritance(JOINED)` node entities).
  - Pre-existing gap noticed: `docs/testing.md` never catalogued the `node.*`
    and `strategyCost.*` test classes, so its total trails the actual `mvn test`
    count. Left as-is to keep this change scoped; worth a dedicated catalog
    reconciliation.
- 2026-08-30 — Bulk registration is now organization-scoped only. Removed the
  global `POST /users/bulk` (and `UserController`, `BulkRegistrationService`, the
  `user/dto` bulk records) and replaced it with
  `POST /organizations/{id}/users/bulk` (`OrganizationBulkRegistrationService`,
  bulk DTOs moved to `organization/dto`). Each CSV row creates a regular platform
  `USER` and adds them to the path organization as a member; an optional `role`
  column (`OWNER`/`MEMBER`, default `MEMBER`) sets the membership type.
  Authorization reuses the single-user path's rule (platform admin or org member
  with `MANAGE_ORGANIZATION`), extracted to the public
  `OrganizationService.assertCanManageUsers`; the member-type→permissions map is
  exposed via `OrganizationService.defaultPermissionsFor`. `PasswordGenerator`
  stays in `user/service` (reused cross-feature, like `UserService`).
- 2026-08-30 — Added `GET /organizations` (list): a platform ADMIN gets every
  organization (`findAll`), any other user gets the ones they are a member of
  (`findDistinctByMembers_User_Id`). Drives the frontend org picker. `POST
  /organizations` (create) stays open to any bearer for now; the admin UI is the
  only surface that exposes it. Frontend: the two separate create-user forms were
  merged into **one** `RegisterForm` with an optional `OrganizationPicker`
  (select an existing org or create one inline) — empty selection creates a
  platform user, a selected org registers the user into it. The signed-in shell
  is now `AppLayout` with a role-aware `Sidebar` (admins: Users + Organizations;
  regular users: Workspace); `PanelLayout`/`AdminPanel`/`UserPanel` were removed.
