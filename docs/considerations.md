# Considerations & expectations

A running log of decisions, assumptions and things future work should keep in
mind. **Read this before making changes, and append to it when you introduce
something non-obvious.** Keep entries concise, dated, and human-readable.

Format: `- YYYY-MM-DD — <note>` (newest at the bottom of each section).

## Standing rules

- **The backend is the source of truth when a wireframe and the backend
  disagree.** Never implement something in the frontend that contradicts a
  security or design decision already taken in the backend. Adjust the frontend
  to what the backend actually allows, and record the mismatch as a separate
  note here so the team can decide whether to reopen that decision — do not
  reopen it on your own. Example: wireframe `1a` shows a self-service "Crear
  cuenta" tab, but `SecurityConfig` deliberately keeps `POST /auth/register`
  behind `hasRole("ADMIN")` because accounts are created by admins and
  organization owners. The login screen must therefore ship without a
  self-registration path.

## Current expectations

- The `User` model carries `mail`, `firstName`, `lastName`, `passwordHash`, a
  `platformRole` (`ADMIN`/`USER`) and an optional `jobTitle` — plus `BaseEntity`
  audit fields. There is still **no phone number**. The seeded
  `admin@enerscope.org` is an `ADMIN`. `platformRole` is the app-wide role;
  organization/project membership roles are scoped and separate. `jobTitle` is
  descriptive only and never affects authorization.
- Authentication is **stateless** (JWT). There is no session table; a `Session`
  is rebuilt from the token on each request. Revocation before expiry is not
  supported — keep access-token lifetimes short.
- Flyway owns the schema; Hibernate runs in `validate`. The H2 profile used by
  tests disables Flyway and lets Hibernate build the schema — keep entities
  portable enough for both, or adjust the test profile deliberately.
- The frontend now has the **auth portal**: an `AuthProvider`/`useAuth`
  (login/register/logout/refresh + role state), reusable `LoginForm`/
  `RegisterForm`, role-gated routes and admin/user panels. Design tokens live in
  `src/index.css` under Tailwind v4 `@theme` (brand greens and the ink ramp, from
  the logo); reuse `bg-brand-*`/`text-ink-*` instead of raw hex. Tailwind only;
  no hand-written CSS beyond those tokens. **The page surface is white and brand
  green is an accent, never a surface** — primary buttons, links and the active
  nav item only. **`ink-500` is the floor for text** (5.69:1 on white); `ink-300`
  and `ink-400` are for borders, decorative icons and placeholders. White text
  needs `brand-800` or darker. Type hierarchy comes from size and weight, and
  spacing stays on the 4px grid — the full rules are in
  [`frontend/README.md`](../frontend/README.md) → Conventions.

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
- 2026-09-01 — Visual editor backend (SCRUM — módulo editor de diagrama). Prepares
  the backend for the canvas/map editor. Decisions:
  - **Two positions per node.** `NodeGraphData`'s previous single `coordinates`
    field was replaced by two `@Embeddable` value objects: `GraphPosition`
    (`graph_x`/`graph_y`, the abstract diagram-canvas position) and
    `GeographicalPosition` (`longitude`/`latitude`, the real-world position for
    the MapLibre 2D/globe view). Both nullable and independent. Migration
    `V10__split_node_graph_position.sql` (adds the 4 columns, migrates the old
    `x_position`/`y_position`, drops the 3 old columns; the old single
    `coordinates` value has no meaningful lng/lat mapping and is dropped).
  - **`id` vs `identityId`.** Confirmed with the class diagram: `id` is the table
    PK; `identityId` is the cross-version identity (same real node across
    versions). **Within a version, connections reference nodes by `id`** (that's
    what `addConnectionToVersion` validates and what the diagram read exposes) —
    identity-based referencing is reserved for the cross-version diff/merge work.
  - **Diagram read model.** `GET /version/{versionId}/diagram` returns a flat
    `DiagramDTO { versionId, nodes[], connections[] }` built from the version
    snapshot, so the API never serialises lazy JPA associations or the diff
    history. The canvas renders from this; per-type node detail beyond the common
    fields is a follow-up.
  - **Move persistence.** `PATCH /version/{versionId}/node/{nodeId}/position`
    updates a node's graph and/or geographical position in place **without**
    recording a `NodeChange` (a drag is presentation, not a structural edit).
    The full-node `PATCH /version/{versionId}/node/{nodeId}` still exists for
    structural edits.
  - **Project → versions navigation.** Added `GET /projects/{projectId}/versions`
    (`VersionSummaryDTO` list) so the editor can pick a version to open.
  - **Bug fixed:** `ProjectService.saveVersion` ended with
    `throw new UnsupportedOperationException(...)` after persisting, so
    `POST /projects/{projectId}/version` always returned `500`. It now returns the
    created version.
  - **Docs were stale:** `domain-model.md` claimed the version node/connection
    snapshots and `NodeChange`/`ConnectionChange` were "not modeled" and that a
    Flyway `V2` collision blocked them. Both were already resolved by the merged
    `version_module`; the docs were corrected in this change.
  - **Still out of scope (separate versioning module):** snapshot-every-N-versions
    and the diff engine's merge semantics. The base editor only needs a version to
    own its nodes/connections, which it does.
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
- 2026-09-08 — Groundwork for the Vistas module (SCRUM-158/159/160). Three
  changes, scoped deliberately narrow:
  - **`User.jobTitle`** (`V7__add_job_title.sql`, nullable `VARCHAR(120)`).
    Optional on both registration DTOs and surfaced through `UserSummaryDTO`, so
    the top bar renders the user's title from the login/refresh payload with no
    extra call. It is **not** a JWT claim: `POST /auth/refresh` reloads the user
    from the database, so the claims did not need to change.
  - **`GET /organizations/{id}/members`** — readable by a platform admin or any
    member of the organization via the new
    `OrganizationService.assertCanViewOrganization`, which is deliberately more
    permissive than `assertCanManageUsers`: listing who has access is not a
    management action. `OrganizationMemberDTO` gained `firstName`, `lastName`,
    `jobTitle` and `active` (additive — the two existing POST responses simply
    return more fields).
  - **`GET /projects`** with an optional `?organizationId=`. Visibility follows
    **project** membership, not organization membership: a user in the org but
    not on the project does not see it. One endpoint serves both the top bar's
    project picker and the Projects screen's org filter. It returns
    `ProjectSummaryDTO` built by a JPQL projection with a `COUNT` subquery —
    `Project.organization` and `Project.members` are lazy, so mapping entities
    would have meant a `LazyInitializationException` outside a transaction plus
    an N+1 on the member count. `ProjectDTO` is untouched and stays the create
    response.
  - **Fixed `ProjectService.saveVersion`**, which persisted the version, linked
    it to the project and *then* threw `UnsupportedOperationException` — so the
    endpoint always answered `500` with the data already committed, and retries
    produced duplicate versions. It now returns the version and is
    `@Transactional`, making the insert and the project link one unit.
    `VersionService.saveVersion` is unannotated and joins that transaction.
  - Deliberately **out of scope**, each its own card: batch user creation from
    the UI (`POST /organizations/{id}/users/bulk` already exists and takes a
    multipart CSV — decide CSV vs. a JSON variant once that screen is designed);
    `PATCH`/`DELETE` for users and projects (the ABM screens show edit/delete
    actions with no endpoints behind them); an `INVITED` membership state (the
    Users screen shows Activo/Invitado/Suspendido but `BaseEntity.active` is a
    boolean, so only Activo/Suspendido are representable); and the "rol en la
    cadena de valor de GNL" field from the new-user modal, which has no backend
    concept at all.
  - Known debt left untouched on purpose: `VersionController.getVersion` and
    `POST /projects/{id}/version` return the raw `Version` **entity**, so Jackson
    serialises `nodeSnapshot`, `connectionSnapshot`, both change logs and the
    whole `parentVersion` chain. It does not fail today only because
    `spring.jpa.open-in-view` is left at its default (`true`). Now that
    `saveVersion` actually returns, that payload is real — a `VersionResponseDTO`
    is worth its own card. Also: `V6__add_platform_role.sql` has a `-- V7:`
    header typo, `common/EntityNotFoundException.class` is a compiled file
    committed under `src/main/java`, and `Version` is annotated with Spring's
    `@Lazy`, which does nothing on a JPA entity.
  - `docs/testing.md` was updated with the 24 new cases only. Its totals still
    trail `mvn test` by 24 cases for the pre-existing reasons noted above (the
    `node.*`/`strategyCost.*` classes are uncatalogued, a
    `version.controller.VersionControllerTest` is catalogued but does not exist,
    and `VersionServiceTest`/`MoneyAmountTest` counts drifted). Reconciling the
    catalog is still a separate task.
- 2026-09-08 — SCRUM-158: the signed-in shell (top bar + sidebar) now follows the
  product design.
  - **Brand tokens re-anchored** in `src/index.css` — `brand-500 #4CAF50`,
    `brand-900 #1B5E20`, `ink-800 #232B33`, `cream #FAF3E0`. The two brand
    anchors are Material Green 500/900, so the intermediate steps are that
    canonical ramp rather than invented values; `ink` keeps a single blue-tinted
    hue through the scale. Same three families and the same token names as
    before — **always extend the theme here, never introduce raw hex.**
  - **`Button` primary moved to `brand-700`.** White text on `brand-500` is
    2.8:1, under the 4.5:1 WCAG AA requires; `brand-700` is 4.6:1. The tokens
    themselves are untouched — only which one the primary variant uses.
  - **Layout restructured**: the top bar now spans the full width with the
    sidebar beneath it, matching the design. Previously the sidebar was a
    full-height column and the header only covered the content area.
  - **UI copy is in Spanish; identifiers, file names and route paths stay in
    English.** A URL is closer to an identifier than to copy, so `/projects`
    rather than `/proyectos` — a deliberate departure from the wireframe, which
    shows Spanish URLs. The auth and admin pages still have English copy: only
    the shell was migrated, the pages are their own cards.
  - **Active project** resolves URL → stored preference → first project, via
    `ActiveProjectProvider`. The URL branch reads `PROJECT_ROUTE_PATTERN`
    (`/projects/:projectId/*`); no such routes exist yet, so today the stored
    preference decides, and the branch starts working when SCRUM-160 adds them,
    with no change to the provider.
  - **Sidebar lists all eight sections, but the seven without a page render
    locked** (`aria-disabled`, no link, a padlock, and a lighter weight than a
    navigable entry — see the 2026-09-08 entry for why they are no longer
    dimmed). Showing the shape of the product is intentional; linking to pages
    that do not exist is not. Platform
    admins additionally get an "Administración" group with the existing
    `/admin/users` and `/admin/organizations` — those are not in the wireframe
    but are live functionality that would otherwise become unreachable.
  - The notification bell from the design was **left out**: nothing in the
    backend produces notifications, and an icon that does nothing is a false
    affordance. Same reasoning for "Configuración" in the user menu.
  - `RoleBadge` lost its only caller when the old top bar was replaced. Left in
    place rather than deleted — the users screen (SCRUM-159) may want it — but
    it is dead code until then.
  - The frontend still has **no test setup** (no `test` script, no vitest/RTL),
    so this shipped on `npm run build` type-check plus `npm run lint`, as
    `AGENTS.md` allows. Introducing the harness deserves its own card.
  - Verified manually against a preview session in the browser: palette, both
    sidebar states, the user menu, and the "Proyectos no disponibles" fallback
    the switcher shows when `GET /projects` cannot be reached.

- 2026-09-08 — UX pass over the SCRUM-158 shell, aimed at analysts who sit in
  the tool for hours: legibility over decoration.
  - **The logo is the real artwork.** `src/assets/logo.png` replaces the
    hand-drawn SVG approximation in `Logo.tsx`. The source JPEG had the cream
    background baked in, so it was cropped to the mark and keyed to
    transparency (the semi-transparent edges were un-multiplied, otherwise the
    antialiased outline keeps a cream halo on a white header). Recomposited
    over the original cream it differs by 2.33/255 on average, so the alpha
    extraction is faithful. Sized by height only (`h-8` in the top bar, `h-12`
    on sign-in) with `width`/`height` set from the artwork, so it can neither
    be squashed nor shift the layout while loading.
  - **The base surface is white; cream is gone.** The `--color-cream` tokens
    were deleted rather than redefined to `#fff` — a token named `cream`
    holding white is a trap for whoever reads it next. Brand green is now an
    accent only (primary buttons, links, active nav); every other surface,
    border and text comes from the ink ramp. Cards are separated from the page
    by their border, not by a tinted background.
  - **Contrast is WCAG AA across the shell.** Audited every text/background
    pair in `TopBar`, `Sidebar`, `UserMenu` and `ProjectSwitcher`: ten failed,
    all of them pre-existing. The systematic cause was `ink-400` (3.44:1) used
    as secondary text, so **`ink-500` (5.69:1) is now the floor for text** and
    `ink-300`/`ink-400` are reserved for borders, decorative icons and
    placeholders. The primary button was also below AA — `brand-700` gives
    4.12:1 under white, and a 14px semibold label is not "large text" — so it
    moved to `brand-800` (5.13:1), hover `brand-900`.
  - **Locked sidebar entries keep full contrast.** They were `ink-300`
    (2.06:1) and unreadable, which matters because seven of the eight sections
    are locked. WCAG exempts disabled controls, but these are a map of the
    product rather than dead controls, so they went to `ink-500` and the state
    moved to a padlock plus a lighter font weight. A padlock rather than a
    "Pronto" badge: it promises no date, keeps a constant width next to long
    labels like "Mapa de la Cadena de Valor", and still works on the collapsed
    rail as a small badge over the icon.
  - **The `ProjectSwitcher` trigger is ink, not green.** It names the current
    context rather than offering an action, and at `brand-700` it was 4.12:1
    anyway. Green stays on the selected row inside the menu, where it means
    "this one".
  - **Type scale and spacing were regularised.** Four steps (page title,
    section title, body, uppercase label) so hierarchy comes from size and
    weight instead of colour — most visible on `WorkspacePage`, where label and
    value were both `text-sm` separated only by a colour that failed contrast.
    Spacing snapped to the 4px grid; the half steps (`py-2.5`, `gap-1.5`) sit
    on a 2px grid and drifted. The top bar's horizontal padding is now 24px so
    the logo shares a vertical axis with the sidebar icons.
  - **Known gaps, deliberately left.** `TextField`'s border is `ink-200`
    (1.48:1), below the 3:1 that WCAG 1.4.11 asks of a control's boundary, and
    its placeholder is `ink-400` (3.44:1) — raised from `ink-300`, but a
    placeholder that fully clears AA reads as a filled value. Both are outside
    the four components this pass audited and deserve their own decision.
  - Verified with `npm run build` and `npm run lint`, plus before/after passes
    in the browser over the dashboard, the sign-in page, the admin users page
    and both sidebar states.

- 2026-09-09 — Depth pass over the whole front end: the logo has two variants,
  the page is no longer white, and the sign-in screen gained a brand panel.
  - **Two logo files, one component.** `logo.png` was replaced by
    `src/assets/logo-mark.png` (the "ES" monogram, 640×332) and
    `logo-full.png` (monogram + wordmark, 640×465), so `Logo` takes
    `variant="mark" | "full"` instead of `withWordmark`. The wordmark is now
    part of the artwork rather than type set next to it, which is why the
    variant replaces the flag: with `full` there is nothing left to compose.
    Both sources were JPEGs on a near-white ground (254,254,254) with no alpha
    — pasted as-is they would have drawn a visible rectangle on the ink-50
    page. Each was keyed to transparency on the deficit from white (a 4→26
    ramp, alpha under 0.04 clamped to zero to kill JPEG speckle), the edge
    pixels un-multiplied, then trimmed to the artwork and quantised.
    Recomposited over white they differ from the source by ~1.2/255 on
    average. Sized by height only, with `width`/`height` from the file, so the
    header cannot shift while they load.
  - **The page surface is `ink-50`, cards stay white.** `body` in `index.css`
    carries it, so any screen that does not hard-code a background inherits it.
    Four places did hard-code white and were the reason the token could not
    propagate on its own: `AppLayout` (which backs all three signed-in pages),
    `LoginPage`, `PageLoader` and `body` itself. The white that **stays** is
    deliberate: the top bar, the sidebar, the two popovers and the form
    controls are surfaces sitting *on* the page, and the whole point of the
    change is the one step of separation between them and it.
  - **Cards are lifted, not tinted.** `Card` keeps its `ink-100` border and
    `shadow-sm`, and gained `elevation="md"` for a card that carries a page on
    its own — sign-in is the only one today. It is a prop rather than a
    `className` override because `shadow-sm shadow-md` in one class list is
    decided by stylesheet order, not by the order they are written.
  - **Sign-in is a split screen from `lg` up** (the wireframe's 1a). The empty
    space on that page was horizontal, so a second column is what actually
    spends it: a `brand-50 → ink-50` wash carrying the full logo, a headline
    and three points, against the form card on the right. Below `lg` the panel
    is `hidden` and the layout is exactly what it was, with the logo above the
    card. The panel is a **light** wash on purpose — a dark green panel reads
    well but the monogram is charcoal, so it would have needed an inverted
    logo, i.e. new brand artwork derived from the source rather than given.
  - The panel's three points reuse `ValueChainIcon`/`FlaskIcon`/`CompareIcon`
    from the sidebar and describe sections that are still **locked**. That is
    accepted here where the shell's locked-entry rule is not: a sentence on a
    sign-in page describes the product, it is not a control that promises to
    navigate somewhere.
  - Its copy is **English**, like the rest of `LoginPage`. Spanish next to
    "Sign in" would read as a bug; migrating the auth and admin pages to
    Spanish is still its own card, and it is the whole page's copy or none.
  - Verified with `npm run build` and `npm run lint`, plus the sign-in page in
    the browser at desktop width. The narrow-viewport collapse was **not**
    checked in the browser — the resize tool did not take effect on the window
    — only that `lg:flex`/`lg:hidden` compile under the 64rem breakpoint. The
    three signed-in pages were not re-checked visually either: they change
    only through the `AppLayout` background and `Card`, both shared, but a
    look at them is worth doing.
- 2026-09-09 — SCRUM-160 (backend half): **creating a project now adds the
  creator as a project `ADMIN`.** `ProjectService.createProject` resolves the
  caller from `AuthUtil.currentSession()` (throwing `UnauthorizedException`
  when there is none, same as `listForCurrentUser`) and attaches them as a
  member with `MANAGE_PROJECT` + `EDIT_PROJECT` + `VIEW_PROJECT`.
  - **Why:** `GET /projects` filters by *project* membership, so before this a
    creator who is not a platform admin never saw the project they had just
    created — the Projects screen would answer a successful create with an
    unchanged, possibly empty table. This is the **opposite** of the decision
    taken for organizations, where creating one deliberately does not enrol the
    creator: an organization is an administrative container created *for*
    someone else, a project is created by the person who is going to work on it.
  - The creator is re-read through `userRepository.findById` instead of being
    taken straight off `session.getUser()`: that instance is detached and is
    about to be referenced by a new `project_member` row.
  - `createProject` is now `@Transactional`, so the project and its first
    member are one unit. A half-written create — project saved, membership
    lost — is precisely the invisible-project state this change exists to fix.
  - The member-building block (member + default role + `project.addMember` +
    save) moved to a private `attachMember(Project, User, ProjectMemberType)`
    shared with `addMember`, which keeps its own validations (project exists,
    user exists, not already a member) and only delegates the assembly.
  - Session check runs **before** the organization lookup: an anonymous caller
    gets `401`, not a `400` about an organization it never got to read.
  - **Still open, deliberately not fixed here:** `POST /projects` does not check
    that the creator belongs to the target organization, so any authenticated
    user can create a project inside any organization. That is a broader
    authorization gap (the same shape of check `OrganizationService` already
    has in `assertCanManageUsers`/`assertCanViewOrganization`) and is worth its
    own card rather than a patch on this one.
- 2026-09-09 — SCRUM-160 (frontend half): the **Projects screen** at `/projects`
  — table (Proyecto · Organización · Descripción · Integrantes · Actualizado ·
  Acciones), a search box, an organization filter and a "Nuevo proyecto" modal.
  The sidebar entry for Proyectos is no longer locked.
  - **The page reads `useActiveProject()`; it does not fetch.** The provider
    already loads `GET /projects` once inside `AppLayout`, so reusing it keeps a
    single source of truth and means creating a project refreshes the table and
    the top bar's `ProjectSwitcher` with one `reload()`. The consequence, taken
    on purpose: search and the organization filter are **client-side**, and the
    backend's `?organizationId=` goes unused for now. If the project count ever
    outgrows a single fetch, that parameter is where server-side filtering
    starts.
  - **The filter's options are derived from the loaded projects**, not from
    `GET /organizations`. Since filtering is client-side, listing an
    organization with no visible project would offer an option that can only
    ever produce an empty table. The **modal** does call `useOrganizations()` —
    there you need the full catalog to pick a leading organization — and it is
    the only new network call on the screen.
  - **`Modal` is a new primitive**, controlled (`open`/`onClose`) rather than
    built on `useDismissable`: that hook owns its own open state and binds a
    document `mousedown` meant for the top bar's popovers. The modal closes on
    Escape and on an overlay click, locks body scroll, and moves focus to its
    first field. Focus is **not** trapped inside it yet — worth a pass when
    there is a second modal to share the work.
  - **`TextArea` is a new primitive** too: `TextField` only wraps `<input>` and
    the description is a textarea. `Card` gained a `padded` prop — the table
    runs to the card's edges, and passing `p-0` through `className` would have
    been a Tailwind collision resolved by stylesheet order, not by the order of
    the classes in the string.
  - The modal mirrors the backend's validation client-side (name 2–120,
    description required and ≤ 500, organization required) instead of leaning on
    the `400`. Note the description is **`@NotBlank`** on `CreateProjectRequestDTO`,
    so it is a required field on the form, not an optional one.
  - **Rows are inert** — no navigation. `PROJECT_ROUTE_PATTERN`
    (`/projects/:projectId/*`) coexists with this listing route but no
    project-scoped page exists yet to link to. The **Acciones** column shows
    disabled edit/delete icons, the same "show the shape of the product, do not
    link to nothing" treatment as the locked sidebar entries, because
    `PATCH`/`DELETE /projects/{id}` do not exist.
  - No sorting in the client: `GET /projects` already returns
    `ORDER BY lastModified DESC`.
  - Dates are formatted `dd/mm/aaaa` through `src/utils/date.ts` with
    `Intl.DateTimeFormat('es-AR')` — no date library. It returns an em dash for
    a missing or unparseable value so a cell never reads "Invalid Date".
  - Four distinct empty/edge states, which are easy to collapse into one by
    accident: loading, error, "no projects at all" (with the create button), and
    "no project matches the search".
- 2026-09-09 — Sign-in brand panel (`LoginPage`): Spanish copy, a solid surface
  and a node-network texture.
  - **Copy migrated to Spanish** (voseo, matching the shell). The **form card on
    the right is still English** ("Sign in", "Welcome back…") — the auth pages
    were left as their own card on 2026-09-08 and only the panel was asked for
    here, so the page is deliberately half-migrated for now.
  - **The `brand-50 → ink-50` gradient is gone.** Its bottom stop was the same
    `ink-50` as the page next to it, so the panel dissolved into the background
    and read as a contrast bug rather than a wash. The panel is now solid
    **white**, the same surface role the sidebar already has in `AppLayout`.
    Solid `ink-50` was considered and rejected: the sign-in page *is* `ink-50`,
    so the panel would have been indistinguishable from it, and turning the
    right half white would cost the white card its "sheet" reading.
  - **`NodePattern`** is a tiled SVG texture (nodes and connections, echoing the
    logo) behind the panel, masked by a vertical gradient so it is absent behind
    the copy and densest at the bottom — the corner that was empty. Built with
    `<pattern patternUnits="userSpaceOnUse">` at a fixed 72px tile, **not** a
    scaled `viewBox`: the first attempt used `preserveAspectRatio="slice"` over
    a 400×620 viewBox, which magnified the mesh to the height of the panel and
    ran a heavy lattice straight through the headline. A background texture has
    to keep its cell size independent of the element it fills.
  - Verified in the browser at ~1440×950 only. The `resize_window` browser tool
    reports success but leaves `window.innerWidth/innerHeight` untouched in this
    setup, so **narrow and short viewports remain unverified** for this page —
    check `hidden lg:flex` and the short-viewport behaviour by hand.
- 2026-09-09 — `LoginPage` finished its migration to Spanish and had its
  vertical rhythm rebalanced.
  - **The whole page is Spanish now**, form included (`LoginForm` copy only —
    its logic is untouched). That closes the half-migrated state noted in the
    entry above. `label="Email"` was kept as "Email" rather than "Correo
    electrónico": it is what the product's users say, and it keeps a short label
    over a short field.
  - The welcome line is **"Hola de nuevo"**, not "Bienvenido de nuevo":
    "Bienvenido" genders the reader, and the page has no idea who is signing in.
  - Two calques from the English draft were rewritten: "de punta a punta"
    (end to end) became "Modelá **toda** la cadena de valor del GNL", and
    "lado a lado" (side by side) became "en una sola vista". Translating this
    panel means writing the Spanish sentence, not mapping the English one word
    by word — worth remembering for the auth and admin pages still to migrate.
  - **Vertical rhythm.** The panel is now top-anchored (`lg:justify-start`,
    `pt-[15vh]`) because the node pattern already fills its lower half, while
    the form column stays vertically centred with an upward bias
    (`pt-10 pb-24`). Top-anchoring the form column too was tried and rejected —
    it left a large void under the card, trading dead space at the top for more
    of it at the bottom.
  - **The green ring around the sign-in button is the `focus-visible` indicator,
    not a stray style.** With the button at rest the computed styles are
    `box-shadow: none`, `outline: none`, `border: 0`, `--tw-ring-shadow: 0 0
    #0000`; the ring (`brand-400` with a 2px white offset) appears only on
    `:focus-visible`. It is the WCAG keyboard-focus affordance and must not be
    removed — if it ever needs to be quieter, change its colour or drop the
    offset, never the ring.
- 2026-09-09 — Sign-in brand panel, **direction A ("dark plate")**. Three
  directions were built as throwaway mockups behind a temporary `/mockups`
  route and compared in the browser; A was chosen and the scaffolding deleted.
  - **A (chosen).** The panel is a solid `ink-900` plate. The headline runs at
    `clamp(2.75rem, 3.4vw, 3rem)` — 44px floor, 48px ceiling — and is anchored
    to the **bottom**, so the empty space falls above the type instead of below it
    — that alone supplies the asymmetry, with no compositional trick. The
    value-chain network (`NodeGraph`) bleeds off the top-right corner at real
    scale in `ink-700`/`ink-600` with three `brand-500` nodes. The bullet icons
    are gone: `01/02/03` numerals at 24px do the graphic work the 20px icons
    were too small to do.
  - **B (rejected) — "ink spine".** A full-height `ink-900` rail pinned to the
    panel's left edge, content pushed off-centre against it. It reads well and
    leaves the light system intact, but the vertical rail is a well-worn device
    and the rotated wordmark stays small, so the headline ends up carrying the
    composition alone — a better version of the old panel rather than a new one.
  - **C (rejected) — "diagram as hero".** Content top-anchored, icons enlarged
    to 40px as a three-column rail, an `ink-900` band across the base. The
    oversized icons genuinely worked, but the composition stayed a
    left-aligned stack and the base band read as a footer rather than an anchor.
  - **Type scale**: 48px headline → 24px numerals → 20px subhead → 14px body.
    The subhead was deliberately raised from 14px: at the body size it was
    indistinguishable from the bullets and the jump straight to 14px made
    everything below the headline read as a footnote. The headline itself was
    stepped **down** from an earlier `clamp(…, 4.6vw, 4.25rem)` (~60px), which
    dominated the composition rather than leading it; 44–48px keeps the tension
    against the 14px body without the panel turning into a poster.
  - **The sign-in button stays `brand-800`** — the standard `Button` `primary`
    variant, no override. Measured rather than assumed, because the plate
    changes the arithmetic: relative luminances are `ink-900` 0.0106,
    `brand-700` 0.2051, `brand-800` 0.1548, `brand-900` 0.0834, giving
    white-on-`brand-700` **4.12:1 (fails)**, white-on-`brand-800` **5.13:1**,
    white-on-`brand-900` **7.87:1**; and against the plate, `brand-800`
    **3.38:1** versus `brand-900` **2.20:1 (fails 1.4.11)**. `brand-900` is the
    safest tone on white and the wrong one here — it sinks into the plate.
    `brand-800` is the only tone that clears both. (In this layout the button
    actually sits on the white card, where it is 5.13:1 either way; the plate
    figures are what make the choice hold if it ever moves.)
  - **The logo needs a light plinth.** `logo-full.png` is dark artwork on
    transparency, so on `ink-900` it disappears; it sits on a white chip.
    Inverting is not an option — the green would go magenta. **A monochrome or
    light logo asset would remove the chip**, and is worth requesting.
  - The chip is a **flow child under `lg:justify-between`**, not an absolutely
    positioned one. The first pass positioned it and the enlarged headline grew
    straight into it; anchoring both ends of the column removes the collision
    structurally instead of by tuning numbers.
  - `NodePattern` (the tiled background texture) was **deleted** — superseded by
    `NodeGraph`, which is the same idea promoted from decoration to illustration,
    as the brief asked.
  - Verified at the browser's own viewport only (~1568×741 captured, 614px
    reported by the page — the panel does not scroll at either). `resize_window`
    still has no effect here, so **other viewport sizes remain unverified**.
- 2026-09-09 — **Light logo artwork**, so the sign-in plate drops the white chip.
  `logo-mark-inverse.png` and `logo-full-inverse.png` were generated from the
  originals and `Logo` gained a `tone` prop; the panel now sets the lockup
  straight on the `ink-900` surface.
  - `tone` names the **artwork, not the surface**: `dark` (default) is the
    charcoal original for light backgrounds, `light` is the white cut for dark
    ones. Every existing call site keeps the default, so nothing else moved.
  - **How the artwork was cut.** Neither Pillow nor ImageMagick is available on
    this machine and `sips` cannot recolour per pixel, so the conversion is a
    stdlib-only script (`zlib` + `struct`: parse IHDR/IDAT, reverse the PNG
    scanline filters, transform, re-emit). Per opaque pixel it measures
    "greenness" as `(G - max(R, B)) / 50`, clamped to 0–1, and blends the pixel
    toward white by the inverse. So brand green survives untouched, the charcoal
    goes to pure white, and the antialiased boundary between them fades
    smoothly instead of stair-stepping.
  - The divisor started at 60 and was **lowered to 50 after measuring**: at 60
    the dark green `#137a40` scored 0.967 and picked up 3% white, drifting to
    `#1b7e46`. At 50 it clamps to 1 and is preserved exactly. Verified by
    colour histogram: `#73c549` ×10293 and `#137a40` ×4448 are byte-identical
    before and after, while `#263138` ×71790 became `#ffffff`.
  - **Do not fake this with `filter: invert()`** — inverting the artwork turns
    the brand green magenta. That is why a second asset exists at all.
- 2026-09-10 — **The frontend is now fully Spanish.** The half-migrated state
  noted on 2026-09-08 ("only the shell was migrated, the pages are their own
  card") is closed: `WorkspacePage`, `AdminUsersPage`, `AdminOrganizationsPage`,
  `RegisterForm` and `OrganizationPicker` were translated, and every error
  fallback with them.
  - **The admin pages were translated even though they are slated for a
    redesign** (table + modal, like `ProjectsPage`). That is knowingly throwaway
    work, accepted because the app is being demonstrated and a screen reading
    "Proyectos" beside one reading "Create a user" is worse than the waste.
  - `RegisterForm` and `OrganizationPicker` are used **only** by
    `AdminUsersPage`, so translating them without the page around them would
    have split one card down the middle — English heading over a Spanish form.
    They move together or not at all.
  - **The success message was rebuilt, not translated.** It used to be
    `` `${mail} was registered ${where}.` `` with `where` swapped between two
    fragments. Spanish does not take that shape gracefully, so the two cases are
    now two whole sentences: "Se registró a … en la organización." and "Se creó
    la cuenta de plataforma de …". Composing a sentence from a translated tail
    is how UI copy ends up sounding machine-made.
  - **All twelve error fallbacks are Spanish**, including the shared default in
    `api/errors.ts` (`'Algo salió mal'`) and the three thrown inside
    `AuthProvider`/`useOrganizations`. These sit outside any page, which is why
    a screen-by-screen audit misses them.
  - **Still pending, its own card: the backend answers in English.**
    `getErrorMessage` prefers `ApiResponse.message`, so "Organization not
    found", "User is already a member of this project" and "Validation error"
    still reach the user verbatim. The Spanish fallbacks only show when the
    request never got an answer. **Translating the frontend did not finish the
    language problem** — it made the remaining half visible.
  - Deliberately **out of this pass** (kept out of the diff on purpose):
    `RoleBadge` is dead code that renders the raw `ADMIN`/`USER` enum, and the
    error states use Tailwind's default reds (`red-50/200/400/600/700`) with no
    `danger` ramp in `@theme`. Both are real, neither is about language.
- 2026-09-10 — **`GET /projects/{projectId}/members`** (backend half of the
  "see who is on a project" card, separate from SCRUM-160). Readable by a
  platform ADMIN or by any member of the project; `ProjectMemberDTO` gained the
  identity fields the screen lists.
  - **A `JOIN FETCH` returning entities, not a constructor projection.** The
    plan first called for a projection "like `ProjectRepository.findSummaries`",
    which is wrong: `findSummaries` can project because `memberCount` is a
    scalar `COUNT`, while a member row carries `ProjectMemberRole.permissions`,
    an `@ElementCollection` — **JPQL cannot build a collection into a record**.
    The right precedent was already in the codebase on the organization side
    (`OrganizationMemberRepository.findByOrganizationIdWithUser`), so
    `findByProjectIdWithUser` mirrors it: `JOIN FETCH m.user`,
    `LEFT JOIN FETCH m.roles`, `DISTINCT`, ordered by `createdAt`. Same problem
    solved (no lazy load per row), different tool.
  - **`ProjectMemberDTO` was extended, not duplicated**: `firstName`,
    `lastName`, `jobTitle`, `active`, mirroring `OrganizationMemberDTO`. The
    change is additive, so `POST /projects/{id}/members` simply answers with
    more fields — the same call the 2026-09-08 card made for the organization
    POSTs.
  - **`assertCanViewProject`** mirrors `assertCanViewOrganization` and is
    deliberately more permissive than any management check: listing who has
    access is not a management action. Platform ADMIN passes without a
    membership lookup at all; everyone else needs a row in `project_member`.
  - **`SecurityConfig` was checked and needs no entry**: `/projects/**` has no
    explicit matcher and falls through to `anyRequest().authenticated()`, which
    is the intended rule for this endpoint.
  - Seven new tests (`ProjectServiceTest` 17 → 22, `ProjectControllerTest`
    10 → 12); `mvn test` 148 → 155, all green.
- 2026-09-10 — **Project members screen** (frontend half of the card whose
  backend landed the same day). The eye action in the Projects table opens a
  read-only panel listing who is on the project.
  - **Only the eye is enabled.** The pencil and the trash stay disabled with
    their `aria-disabled` treatment, because `PATCH`/`DELETE /projects/{id}`
    still do not exist. `ProjectsTable` therefore has two action components: a
    real `<button>` (`RowButton`) for the enabled action and the original
    `<span aria-disabled>` (`RowAction`) for the blocked ones — a disabled
    action must not inherit a clickable element's affordances, or the reverse.
  - **The panel is thin but every block is labelled.** The first cut put the
    project name in the title and the organization as a bare line under it,
    which read as an orphan: nothing said whether "Austral LNG" was the
    organization, a client, or part of the project's name. Both blocks now
    carry a label — an `ORGANIZACIÓN` field in the app's label/value step, and
    an `Integrantes` heading with the count over the table, plus `DESCRIPCIÓN`
    and `ACTUALIZADO` as a labelled `<dl>`. **A thin panel is not the same as an
    unlabelled one** — the earlier objection to a "project details" modal was
    that repeating the row *unlabelled and unexplained* added nothing, not that
    the fields themselves were unwanted. Labelled, in a two-column definition
    list above the member table, they read as a project record rather than an
    echo of the row.
  - **`Modal` gained a `size` prop** (`md` default, `lg` for this panel).
    A four-column table inside the 512px `max-w-lg` default wrapped a job title
    across three lines. Same additive shape as `Card.padded`: existing callers
    keep the old geometry.
  - A suspended member is marked **next to the name**, not with a fifth column:
    the table lives inside a modal and cannot afford the width, but a members
    list that shows no difference between an active and a suspended person is
    lying about who has access. `active` is the only state available — `INVITED`
    remains unrepresentable.
  - Members are fetched **when the panel opens**, keyed on the project id, with
    a `cancelled` flag so a fast switch between projects cannot paint the wrong
    list. Nothing is requested for projects nobody opens.
  - Verified end to end against a restarted backend: a project with a member
    (name, mail, job title, translated role), and the empty state. Note the
    projects created before the `createProject` fix show **0 members** — they
    are pre-fix data, not a defect in the current code.
- 2026-09-10 — Diagram editor UX pass (frontend only). Decisions:
  - **Connection rules.** The canvas now only accepts edges that follow the
    real-world gas value chain. The allowed targets per node type live in
    `ALLOWED_CONNECTIONS` in `frontend/src/components/editor/nodeCatalog.ts`
    (`canConnect`/`allowedTargetTypes` helpers): well → gathering → treatment →
    pipeline/compression (pipelines may chain and reach either liquefaction
    type) → ground liquefaction → seaport terminal → LNG carrier, and FLNG →
    LNG carrier. Both connect paths (drag-to-connect and the "Connect to another
    node…" click flow) funnel through `EditorPage.handleConnect`, which rejects
    a disallowed pair with a floating warning instead of calling the API. This
    is **client-side only** for now; if the API needs to be authoritative,
    enforce the same map in `VersionService.addConnectionToVersion` (with tests).
  - **`REMOVED` is set on delete, not by hand.** `NODE_STATES` (the manual state
    dropdown options) no longer includes `REMOVED`. "Delete node" now soft-
    deletes: `useDiagram.removeNode` PATCHes the node's basics to
    `state = REMOVED` (via the existing `/basics` endpoint) rather than calling
    the DELETE endpoint, so the node stays in the version and can be brought back
    with `restoreNode` ("Restore node", sets `PROPOSED`). The forms still render
    `REMOVED` when a node already has it, but never offer it as a fresh choice.
    The hard-delete `deleteNode`/DELETE endpoint is left in place but is no
    longer wired to any UI control.
  - **Per-type icons & cursors.** `nodeIcons.tsx` now draws a distinct line icon
    for every `NodeType` (including the previously icon-less `PIPELINE_CONECTION`
    and `INTERNAL_CONSUMPTION`). Cursors were made intentful: an unselected node
    shows a crosshair (its body is a connection source), a selected node shows a
    grab cursor (drag to move), edges show a pointer (double-click removes), and
    the pane switches to a crosshair while a click-connection is pending. Edges
    render as `smoothstep` with a slightly larger arrowhead.
  - **Node palette.** A left rail (`NodePalette`, shown in diagram mode) lists
    every node type with its icon. A tile can be **dragged onto the canvas** to
    place a node at the drop point (`DiagramCanvas` handles `onDrop`/`onDragOver`,
    the drag carries the type under the `NODE_DRAG_TYPE` key), or **clicked** to
    add one near the current spread. Either path opens the create form
    pre-selected to that type (`NodeFormPanel` gained an `initialType`) so the
    user still names it. The canvas is now a flex sibling of the rail; the
    floating create/edit panel is positioned against a new `canvasWrapRef`
    (previously the whole body).
  - **Double-click to edit.** Double-clicking a node opens its full editable
    form (`DiagramCanvas.onNodeDoubleClick` → `EditorPage.openEditNode` →
    `handleEditData`). The data panel's button was renamed from "Edit all data"
    to just **"Edit"**.

- 2026-10-01 — Editor branch merged onto the new app shell (TopBar + Sidebar +
  `ActiveProjectProvider`). `GET /projects` is the single listing endpoint
  (`ProjectSummaryDTO`, optional `organizationId`); the editor's own
  `listByOrganization` endpoint was dropped in favour of it. The editor lives at
  `/editor` as a full-bleed page under `AppLayout` (the shell is `h-screen`;
  regular pages scroll inside `PaddedMain`) and is reached from the sidebar's
  "Mapa de la Cadena de Valor" entry. The editor still has its own
  organization/project/version pickers; wiring it to the active project from
  `ActiveProjectProvider` is a natural follow-up.
- 2026-10-01 — The node-position migration was renumbered `V7` → `V10` when merging
  master, which had already taken V7–V9 (`V7__add_job_title`, `V8`/`V9__create_results`).
  Check `ls db/migration` before picking a number.
- 2026-09-15 — **Authorization closed on projects, versions and the two create
  endpoints.** One card covering four holes that let any authenticated user act
  outside their own tenant. The worst was `POST /projects/{id}/members`, which
  ran **no check at all**: anyone could add themselves as `ADMIN` to any project
  by UUID and, since membership is what grants read access, walk straight into
  it. Now: `addMember` needs `MANAGE_PROJECT`, `createProject` needs membership
  of the target organization, `createOrganization` and
  `POST /organizations/{id}/members` are admin / org-owner, and every
  `/version/**` endpoint is checked through the project that owns the version.
  - **The rules live in a new `ProjectAccessGuard`, not on `ProjectService`,
    because the obvious home would have been a dependency cycle.**
    `ProjectService` already depends on `VersionService`, so having
    `VersionService` ask the service for a check closes the loop and the context
    does not start. The guard depends only on repositories, so both sides use
    it. This is why the project side looks different from the organization side,
    where `assertCanManageUsers` sits on `OrganizationService`: there,
    `OrganizationBulkRegistrationService → OrganizationService` is a single
    arrow and there is nothing to break.
  - **A version cannot reach its project on its own.** `Project.versions` is a
    unidirectional `@OneToMany` with `@JoinColumn`: the FK lives on the
    `version` table but `Version` has no field pointing back. Rather than add a
    back-reference (a second mapping of the same column, on an entity three
    other features touch), the guard resolves it with
    `ProjectRepository.findIdByVersionId` — a JPQL projection returning the
    **id**, so the lazy `organization`/`members` are never loaded for what is
    only an authorization check.
  - **The platform-admin shortcut runs before the owning project is resolved.**
    Otherwise a version attached to no project is refused for everyone, and the
    admin who created one through `POST /version/createtest` could not touch it
    afterwards. Pinned by
    `assertCanEditVersionAllowsPlatformAdminOnOrphanVersion`.
  - **`/version/createtest` was kept, not deleted.** The first plan was to
    remove it (nothing in the frontend calls `/version/**` or `/nodes/**`), but
    it stays behind a platform-admin check in case something undetected uses it.
    The check could not go inside `VersionService.saveVersion`, which
    `ProjectService.saveVersion` calls for the normal flow — that would have
    stopped regular users from creating versions in their own projects. So the
    exposed entry point and the internal one were split:
    `saveOrphanVersion` (public, admin-only) delegates to `saveVersion`
    (unguarded, with `saveVersionIsUnguardedBecauseItsCallersAuthorizeInstead`
    recording that the absence is deliberate).
  - **Every check runs after the entity is resolved and before any further
    lookup.** After, so an unknown id keeps answering `400` instead of turning
    into a `403`; before, so an unauthorized caller cannot use the endpoint to
    probe which user ids exist. The tests assert the second half with
    `verify(userRepository, never()).findById(any())`.
  - **`assertCanViewOrganization` was split into `assertIsMemberOf(id,
    action)`.** Same rule, but the verb is a parameter, so creating a project in
    an organization you do not belong to answers "You are not allowed to
    **create projects in** this organization" instead of "...to **view**...".
    The frontend prints `ApiResponse.message` verbatim, so the wording is the
    product, not a log detail.
  - **Deliberate duplication:** `assertIsPlatformAdmin` exists twice — on
    `ProjectAccessGuard` and privately on `OrganizationService`. Roughly eight
    lines, accepted so that `OrganizationService` does not depend on a guard
    belonging to the *projects* feature. The clean fix is to lift the primitive
    (`requireSession` + `requirePlatformAdmin`) into `util/AuthUtil`, which
    every service already uses; worth doing next time this area is touched.
  - **Test wiring is not uniform, on purpose.** `ProjectServiceTest` builds a
    **real** guard over its mocked repositories, because the `listMembers`
    cases that already existed assert authorization outcomes and a mocked guard
    would have reduced them to "the mock was called". `VersionServiceTest` and
    the `OrganizationService` collaborator in `ProjectServiceTest` are
    **mocked**, because no existing case there tested authorization and the
    rules have their own coverage in `ProjectAccessGuardTest`. A mocked guard is
    also what kept the nine pre-existing `VersionServiceTest` cases from needing
    a `SecurityContext` each.
  - **Known limitation, left open:** `deleteVersion` checks the permission on
    the **root** version's project. `deleteSubVersions` then walks the whole
    tree, so a child version attached to a *different* project would be deleted
    without that project being checked. Fixing it properly means reworking the
    cascade delete, not patching the check. `deleteSubVersions` is now private,
    so the tree walk is at least unreachable without going through the check.
  - **Still open after this card:** `POST /nodes/**` (10 endpoints) has no
    authorization. It creates nodes with no version and no project, so it
    exposes and modifies nothing of anyone else's — noise in the database rather
    than a leak — but it is the last unguarded surface. Separately, the platform
    role comes from the JWT claim, so a demoted user keeps their old role until
    the token expires; that predates this card and applies to every `assert*`.
  - `mvn test` 155 → 233, all green. New: `ProjectAccessGuardTest` (22) and
    `VersionControllerTest` (10, a class `docs/testing.md` had been listing for
    a while without it existing). The two `version.*` entries in that catalog
    described cases that were never written and were rewritten from the code.
- 2026-09-15 — **`Version` snapshots start empty instead of null.** `saveVersion`
  built a version with `nodeSnapshot`/`connectionSnapshot` set to `null` whenever
  there was no parent version, and `Version` was the only entity in the codebase
  whose collections had no field initialiser (`Project` and `Organization` both
  do `= new ArrayList<>()`).
  - **What it actually broke, and what it did not.** The visible defect was the
    payload: `POST /projects/{id}/version` answered `"nodeSnapshot": null` while
    a later read of the same version answers `[]`, so a client had to handle two
    shapes for one field. The NPE on `version.getNodeSnapshot().add(...)` was
    **latent, not live**: `addNodeToVersion` arrives in a separate request and
    Hibernate never loads a mapped collection as null — it installs an empty
    persistent collection. The null instance is the freshly constructed one, so
    the crash only happened inside the same persistence context, and in unit
    tests, where there is no Hibernate to paper over it. It was a trap set for
    the next person writing there, which is where the 19 unguarded
    `getNodeSnapshot()`/`getConnectionSnapshot()` dereferences in `VersionService`
    become relevant.
  - **Fixed in the entity, not only in the service.** `@AllArgsConstructor` was
    replaced by a hand-written constructor with the identical signature that
    turns a null collection into an empty one, next to field initialisers on all
    four lists. Lombok's generated constructor was overwriting those
    initialisers, which is exactly how the nulls got in; fixing only
    `saveVersion` would have left the door open for the next caller. No call site
    changed, because the signature did not.
  - The `@Setter` still accepts null on purpose — that is how
    `saveVersionFromParentWithNullSnapshotsDoesNotPropagateNull` simulates a row
    created before this change. `saveVersion` normalises what it copies from a
    parent, so legacy nulls stop at the parent and are not inherited.
  - **No migration.** Versions already stored have no rows in `versionXNode` or
    `versionXConnection`, so Hibernate already returns empty lists for them.
  - Written test-first: the four cases were red (one with the real
    `NullPointerException`) before the entity was touched. `mvn test` 233 → 237.
- 2026-09-16 — **The session and platform-ADMIN checks moved to `AuthUtil`.**
  Closes the duplication noted on 2026-09-15. The `session == null →
  UnauthorizedException("Authentication required")` block was written out **seven
  times** across `OrganizationService` (×4), `ProjectService` (×2) and
  `ProjectAccessGuard` (×1), the `platformRole == ADMIN` comparison five times,
  and `assertIsPlatformAdmin` twice in full. `AuthUtil` now owns
  `requireSession()`, `isPlatformAdmin(User)` and
  `requirePlatformAdmin(AppLogger, String)`.
  - **The logger is a parameter**, which is the one odd-looking part.
    `AuthUtil` is a static helper and `AppLogger` is an injected bean, so the
    alternative was either dropping the `warn` that records every refused
    platform-admin action, or leaving the two copies in place and only lifting
    the smaller pieces. Passing it keeps behaviour identical and removes the
    duplication completely; `util` depending on `logging` is fine, both are flat
    infrastructure packages.
  - **`ProjectAccessGuard.assertIsPlatformAdmin` was kept as a one-line
    delegation** rather than deleted: `VersionService` asks the guard for every
    other check, and sending it to `AuthUtil` for this one alone would split the
    guard's surface for no gain.
  - Pure refactor: no message, status code or behaviour changed, and the 237
    existing cases already covered the 401/403/allowed paths at every caller.
    The new `AuthUtilTest` (7) exists because `AuthUtil` had no tests of its own
    and now concentrates the rule — including that a refusal is logged with the
    caller's mail and that an unauthenticated call throws *before* logging,
    since there is no caller to name yet.
  - `mvn test` 237 → 244.
- 2026-09-16 — **`PATCH /users/me/password`**, the endpoint `UserService.
  changePassword` never had. The method had existed since the initial scaffold
  with no controller, no DTO, **no test** and no mention in any doc — the only
  reference in the repository was its own declaration.
  - **Exposed rather than deleted, for a reason that already shipped:**
    `POST /organizations/{id}/users/bulk` hands out generated passwords in a CSV
    and `POST /auth/register` lets an admin pick a password for someone else.
    Until now those passwords were permanent — there was no way, by any route,
    for their owner to replace one.
  - **First controller in the `user` feature.** `user/` already had `dto`,
    `model`, `repository` and `service`, so `controller` is the subpackage that
    was missing; `/users/me` is also where the pending user-ABM card will land.
    `AuthController` was the alternative and was rejected: it is where
    credentials are *exchanged*, not where an account is administered.
  - **The account comes from the session, never from the body.** There is no
    `userId` field to send, so the endpoint cannot be aimed at another user by
    construction rather than by a check. An admin resetting someone else's
    password is a different endpoint with its own authorization, and does not
    exist yet.
  - `currentPassword` carries `@NotBlank` but **no `@Size`**: it is verified
    against the stored hash, and rejecting it for being too short would leak
    that the stored password is short. `newPassword` keeps the 8-character
    minimum from `RegisterRequestDTO`.
  - `SecurityConfig` was checked and needs no entry: `/users/**` falls through
    to `anyRequest().authenticated()`, which is the intended rule.
  - **No screen yet, on purpose.** Decided as a separate card, so today the
    endpoint is only reachable from Swagger or a client. Until that card lands,
    a bulk-registered user still has no way to change their password *in the
    app* — the backend half is done, the loop is not closed for the end user.
  - `mvn test` 244 → 251: `UserServiceTest` 9 → 12 (the method had no coverage
    at all) and a new `UserControllerTest` (4).
- 2026-10-01 — **`Modal` now traps Tab and gives focus back on close.** The
  component already handled Escape, the scroll lock, `role="dialog"`,
  `aria-modal`, `aria-labelledby` and moving focus into the panel on open; what
  was missing was keeping Tab inside the panel and restoring focus afterwards.
  The fix lives entirely in `components/ui/Modal.tsx`, so `NewProjectModal` and
  `ProjectMembersModal` — the only two consumers — were not touched.
  - **No library.** `focus-trap-react` and Headless UI both solve this, but the
    frontend has four runtime dependencies and this is ~30 lines. There is
    precedent for hand-rolling it in `hooks/useDismissable.ts`.
  - **The focusable list is queried on every Tab, not captured on open.** This
    is the one decision the consumers force: `NewProjectModal` renders its
    organization `<select>` with `disabled={loadingOrganizations}`, and
    `ProjectMembersModal` replaces its whole body when `projectsApi.members()`
    resolves. A list captured at open time would be stale in both.
  - **`:disabled` was missing from the old selector**, which was a latent bug:
    `input, select, textarea, button, …` would have put the disabled `<select>`
    in the cycle, stalling Tab on a stop the browser refuses to focus. The
    selector now excludes disabled controls and filters out anything with no
    client rects.
  - **Only the two edges are intercepted.** In the middle of the panel the
    event is left alone, because the browser's own tab order reads the live DOM
    better than a hand-kept index. Verified: Tab on a middle element comes back
    with `defaultPrevented === false`.
  - **A document-level `keydown`, not a handler on the panel.** If focus ever
    leaves the panel — browser chrome, a programmatic `focus()` — a
    panel-scoped handler stops receiving events and the trap dies. The document
    listener detects that case and pulls focus back to the edge Tab was heading
    for.
  - **The focus-management effect depends on `open` alone, and must keep doing
    so.** `onClose` is an inline arrow at both call sites
    (`ProjectsPage.tsx:128` and `:134`), so a new identity on every parent
    render would re-run the cleanup and throw focus back to the page while the
    modal is still open. This is why it is a second effect rather than being
    merged into the Escape one, which does depend on `onClose`.
    `react-hooks/exhaustive-deps` is satisfied today because the effect body
    never reads `onClose`; keep it that way.
  - **The opener is restored only if `isConnected`.** It can be gone by the
    time the modal closes — a `ProjectsTable` row removed while the dialog was
    open — and focusing a detached node does nothing.
  - **The empty-list branch is deliberate, not dead code.** No panel can reach
    it while the close button renders, so it never fires today. It stays because
    the alternative, on a panel with nothing focusable, is Tab silently leaking
    to the page behind the dialog; it falls back to focusing the panel itself,
    which carries `tabIndex={-1}` for exactly that reason.
  - **Known, pre-existing, not changed here: the initial focus lands on the
    close button, not on the first field.** The header precedes the body in the
    panel, so the "Cerrar" button is the first focusable in document order —
    measured as
    `[button[aria-label="Cerrar"], input#name, select#org, textarea#desc, button#submit]`.
    The old `querySelector(FOCUSABLE)` picked it too, so this is not a
    regression, but it does mean a form modal opens with focus on dismiss
    rather than on the first input. Fixing it is a deliberate choice (focus the
    first field in the body, or the panel itself) and belongs in its own card.
  - **Verified without a frontend test setup, which is the real gap here.**
    There is still no vitest/jest in `frontend/`, so per `AGENTS.md` this
    shipped on `npm run build` + `npm run lint`. The behaviour was checked by
    rendering the real `Modal` under jsdom in a throwaway harness outside the
    repo, dispatching actual `Tab`/`Shift+Tab` events and asserting
    `document.activeElement`: 18 checks, all passing, covering a form-shaped
    panel with its `<select>` both enabled and disabled, a read-only panel whose
    only focusable is the close button, Tab and Shift+Tab at both edges and in
    the middle, focus pulled back after escaping the panel, focus restored on
    close, an opener detached while the dialog was open, and unmounting while
    open. **Note for whoever adds the test setup:** jsdom has no layout
    engine, so `getClientRects()` returns an empty list even for visible
    elements, which makes `focusablesIn` see nothing. The harness stubbed
    `Element.prototype.getClientRects`; a real suite will need the same stub or
    `happy-dom`. A focus trap is exactly the kind of thing that breaks silently
    in a later refactor, so this is a strong candidate for the first frontend
    test card.
- 2026-10-01 — **`docs/testing.md` now matches the code exactly.** The catalog
  described 228 of the 251 executions `mvn test` reports; the gap was the
  `node.*` classes (2 cases) and the `strategyCost.*` ones (20), which had never
  been catalogued, plus `money.MoneyAmountTest`, recorded as 6 cases when it has
  7 — `addsAll` was missing. Totals are now 237 catalogued cases, 251 surefire
  executions, and the "this catalog is still incomplete" warning is gone.
  - **`node.controller.NodeControllerTest` is filed as Unit, not Web**, even
    though it drives `MockMvc`. It builds the controller with
    `MockMvcBuilders.standaloneSetup`, so there is no Spring context and no
    `SecurityConfig`/`AuthFilter` chain — by the type legend at the top of the
    catalog that is Unit, and `Web` is reserved for `@WebMvcTest`. The
    distinction is not cosmetic: that test exercises a request which arrives
    already authorized, so it says nothing about who may create a node. The
    eleven `POST /nodes/**` endpoints still carry no authorization check.
  - **The catalog now records how thin the node coverage is.** One of the eleven
    creation endpoints is tested, and `NodeService`'s eleven save methods have
    one case between them. Writing that down is the point: the previous totals
    made the suite look like it covered more than it does.
  - **Counts were reconciled mechanically, not by eye.** Every case name in the
    catalog was matched against the `@Test`/`@ParameterizedTest` methods in
    `backend/src/test`, in both directions, so there are no documented cases
    that do not exist and no tests missing from the catalog: 23 classes, 237
    against 237. Per-class surefire output was then compared with the summary
    table. Worth repeating whenever the catalog drifts again, which it will.
- 2026-10-01 — **Project deletion will be a logical one (`active = false`), not a
  physical `DELETE`.** Decided by the team before any code was written, and it
  shapes both halves of the project ABM card.
  - **Why logical.** A physical delete depends on the still-open question of
    which project a child version belongs to: `Version.parentVersion` is a
    `@ManyToOne` with no cascade and `fk_version_parent` carries no
    `ON DELETE`, so deleting a project's versions in the wrong order violates
    that constraint, and a child living in another project would be left
    pointing at a parent that no longer exists. The logical delete does not
    touch that question. The history — versions, changes, who was a member — is
    also part of what the product is for.
  - **No FK in this schema cascades.** `project_member.project_id`,
    `version.versions_id`, `version.parent_version_id` and
    `project.organization_id` are all plain `REFERENCES`, so a physical delete
    would have depended entirely on Hibernate issuing child deletes in the
    right order, which the test profile cannot check: it runs H2 with
    `ddl-auto=create-drop` and Flyway disabled, so the real constraints are
    never exercised.
  - **Zero migrations.** `active BOOLEAN NOT NULL DEFAULT TRUE` already exists
    on `project` (V3), `project_member` (V4) and `version` (V5), and
    `BaseEntity` already carries `deactivate()`/`activate()`. The flag was
    there from the start and nothing read it: no repository query filtered on
    it, and its only consumers were two DTOs reporting a *user's* account state.
  - **The deactivation cascades to members and versions**, rather than leaving
    every read to remember filtering by the owning project's flag. The cascade
    walks `project.getVersions()` — the project's own list — and not the
    parent/child tree, which is what keeps it independent of the open modelling
    question. Verified that `project.addVersion` is called in exactly one place,
    `ProjectService.saveVersion`, so every version created through
    `POST /projects/{id}/version` is in that list; the only versions outside it
    are the detached ones from `POST /version/createtest`, which belong to no
    project by definition.
  - **A platform ADMIN still reaches versions of a deactivated project.**
    `assertCanViewVersion`/`assertCanEditVersion` return early for platform
    admins before resolving the owning project, so the filter never runs for
    them. Kept deliberately: without it there would be no way to inspect or
    revive a project after it was deactivated.
  - **The frontend cannot tell who may edit.** `ProjectSummaryDTO` does not
    carry the caller's permissions, so the row actions render for every user and
    an unauthorized click comes back as a `403` shown in an `Alert`. Extending
    the summary DTO with per-caller permissions is its own card.
  - **`Organization` has the same shape and is deliberately left alone** — an
    `@OneToMany` to `projects` with `CascadeType.ALL` and no DELETE endpoint.
    Whatever is decided here should eventually apply there; noted as future
    work, not done.
- 2026-10-01 — **`PATCH /projects/{projectId}`**, the first half of the project
  ABM card.
  - **`MANAGE_PROJECT`, not `EDIT_PROJECT`.** The guard already separates the
    two: `EDIT_PROJECT` is for a project's *contents* — its versions, nodes and
    connections — and `MANAGE_PROJECT` is for administering the project itself.
    Renaming is administration, so only project ADMINs and platform admins pass.
  - **True partial semantics: a null field means "leave it".** The DTO carries
    `@Size` but no `@NotBlank`, because Bean Validation skips nulls, and the
    service rejects the two degenerate bodies validation cannot catch — both
    fields null, and a field present but whitespace-only. `@Size(min = 2)`
    happily accepts two spaces, which is why the blank check exists at all.
  - **`organizationId` is not patchable.** Moving a project between
    organizations changes who can see it and has its own authorization rule
    (`createProject` requires membership of the target organization). Left out
    on purpose; it is a different card.
  - **A domain method on the entity, not `@Setter`.** `Project.updateDetails`
    ignores nulls, in the shape of `BaseEntity.deactivate()`. Lombok's
    `@Setter` on `Version` is exactly what produced the null-snapshot bug of
    2026-09-15, and that is not worth repeating for two fields.
  - **Body validation runs before the project lookup**, so a malformed patch
    answers `400` without revealing whether the id exists; the permission check
    still runs after the lookup, keeping an unknown id at `400` as the other
    endpoints do.
  - **The edit modal sends only what changed**, and closes without a request
    when nothing did. That uses the partial semantics instead of echoing both
    fields back, so an unchanged description cannot clobber a concurrent edit.
  - `mvn test` 251 → 267: `ProjectServiceTest` 31 → 41 and
    `ProjectControllerTest` 17 → 23.
- 2026-10-02 — **`DELETE /projects/{projectId}`**, the second half of the project
  ABM card, implementing the logical delete decided on 2026-10-01.
  - **`ProjectService.deactivateProject` deactivates the project, then every
    member and every version in `Project.versions`**, inside one transaction.
    The cascade walks the project's own version list rather than the
    parent/child tree, which is what keeps it clear of the open question about
    which project a child version belongs to.
  - **Idempotent.** Deleting an already inactive project resolves it,
    authorizes the caller, logs at `debug` and returns without a second write.
    That is what a `DELETE` is supposed to do, and it keeps a double click from
    bumping `lastModified`.
  - **`MANAGE_PROJECT`**, the same bar as `PATCH`: deleting is administering the
    project, not editing its contents.
  - **Five read points now filter on `active`.** `findSummaries` and
    `findSummariesForMember` filter `p.active`, their `memberCount` subqueries
    filter `pm.active`, `findIdByVersionId` filters both `v.active` and
    `p.active`, and `listMembers` moved from `existsById` to
    `existsByIdAndActiveTrue`. The project-list filter is not optional: without
    it the delete would do nothing visible.
  - **`findIdByVersionId` checks both flags on purpose.** The cascade already
    guarantees that a deactivated project has deactivated versions, so
    `v.active` alone would do. `p.active` is there so the guard still refuses if
    a project is ever deactivated by some path that does not cascade — a direct
    database edit, or a future endpoint that forgets.
  - **First `@DataJpaTest` in the repository**, and the reason it exists: no
    mocked test can tell whether a `@Query` actually excludes a row. The
    catalog's type legend gained a **Data** entry for it, because the slice is
    neither Unit (it has a real database) nor Web nor a full `@SpringBootTest`.
  - **Each filter was verified by mutation, not just by a passing test.** The
    four filters were removed one at a time and the suite re-run; each removal
    turned exactly one case red — `findSummariesExcludesADeactivatedProject`,
    `findIdByVersionIdIgnoresADeactivatedVersion`,
    `findIdByVersionIdIgnoresAVersionOfADeactivatedProject` and
    `findSummariesCountsOnlyActiveMembers`. A green test over a query nobody
    has tried to break proves very little; this is the cheap way to find out.
  - **The confirm button is a `primary`, not a `danger`.** `Button` has only
    `primary`/`secondary`/`ghost`, and the missing `danger` ramp in `@theme` was
    already noted on 2026-09-10. The dialog carries the weight in its copy
    instead, and says the project can be recovered, which is true under the
    logical delete.
  - **`RowAction` is gone from `ProjectsTable`.** It existed only to render the
    two disabled placeholders; with both actions wired there is nothing left for
    it to do.
  - `mvn test` 267 → 289: `ProjectServiceTest` 41 → 48,
    `ProjectControllerTest` 23 → 27, and a new `ProjectRepositoryTest` (11).
- 2026-10-02 — **`Modal` now puts the initial focus on the first field of the
  body, not on the close button.** This supersedes the note of 2026-10-01 that
  recorded the old behaviour as known and unchanged.
  - **A second ref on the body**, rather than filtering the close button out of
    the selector. The header precedes the body in the panel, so the X was simply
    the first focusable in document order. Initial focus now queries
    `focusablesIn(bodyRef.current)`; the Tab trap still queries the panel, so
    the X stays in the cycle — it only stops being where the modal opens.
  - **Where each modal now lands:** `NewProjectModal` and `EditProjectModal` on
    their name input, `DeleteProjectDialog` on "Cancelar", which is the safe
    action and a good default for a destructive dialog, and
    `ProjectMembersModal` on the panel itself, because its body is a read-only
    table with nothing focusable in it. Focusing the dialog is the standard
    fallback for that case: the panel carries `role="dialog"` and
    `aria-labelledby`, so a screen reader announces it, and Escape still closes.
  - **That fallback exposed a leak, which is fixed here.** With focus on the
    panel, the panel is not one of the focusables, so the old edge test —
    `active === firstFocusable` / `active === lastFocusable` — matched neither,
    and `panel.contains(active)` was true because `contains` includes the node
    itself. `Shift+Tab` therefore fell through uninterrupted and moved focus
    backwards out of the dialog, onto the page behind it.
  - **The edge test is now an index, not a containment check.**
    `focusables.indexOf(document.activeElement)` returning `-1` means "not in
    the cycle" and routes focus to the edge Tab was heading for, which covers
    both the panel and anything outside the dialog in one branch, and let the
    `contains` call go. Plain Tab from the panel now moves to the first
    focusable explicitly instead of relying on the browser doing it.
  - **Verified by re-running the jsdom harness**, which went from 18 checks to
    21: the three new ones cover the panel fallback, Tab from the panel entering
    the cycle, and the `Shift+Tab` leak. Restoring the old `contains` check
    turns four of them red, so the fix is load-bearing rather than cosmetic.
- 2026-10-02 — **A `danger` colour ramp and a matching `Button` variant.** The
  gap noted on 2026-09-10 — error states using Tailwind's default reds with no
  `danger` ramp in `@theme` — is half closed: the ramp exists and the button
  uses it.
  - **The ramp carries Tailwind v4's own red values, on purpose.** Declaring it
    in `@theme` follows what the file already asks for ("reuse these instead of
    raw hex"), while keeping the values identical to the reds already on screen
    means migrating `Alert`, `TextField`, `TextArea`, `NewProjectModal` and
    `OrganizationPicker` later is a find-replace with no visual diff at all.
    The steps declared — 50, 200, 400, 600, 700 — are exactly the ones those
    five files use, so nothing will be missing when that happens. Tailwind
    drops the unused ones from the build, so 50 and 200 cost nothing today.
  - **Contrast, measured rather than assumed**, since this file keeps a WCAG
    budget: `danger-600` is 4.76:1 against white and `danger-700` is 6.42:1, so
    white text on the fill and on the hover both clear AA for body text. It is
    the same shape as `primary`, which is `brand-800` (5.13:1) hovering to
    `brand-900` (7.87:1).
  - **The focus ring moved from `base` into the variants.** It had to:
    `ring-brand-400` and `ring-danger-400` set the same property, and which one
    wins is decided by the order of the generated stylesheet, not by the order
    of the class string, so a red button could not simply append its ring. The
    three existing variants kept `ring-brand-400`, so none of them changed.
  - **`danger-400` for the ring, knowing it is 2.89:1 against white** and so
    under the 3:1 that WCAG 2.2 asks of a non-text indicator. `brand-400`, the
    ring every other button has used since the beginning, is no better. Matching
    it keeps the system coherent; fixing the focus ring is a system-wide change
    and belongs in its own card.
- 2026-10-03 — **`PATCH /users/me`**, the first half of the user ABM. Only the
  caller's own profile: `firstName`, `lastName` and `jobTitle`.
  - **No authorization check, by construction.** The account comes from
    `AuthUtil.requireSession()` and there is no `userId` field in the DTO, so
    the endpoint cannot be aimed at another account — the same shape as
    `PATCH /users/me/password`. An admin editing somebody else is a different
    endpoint, and it is paused pending a team decision.
  - **`mail` and `platformRole` are not patchable.** `mail` is the login
    identity, is `UNIQUE`, is normalised by `User.normalizeMail` and travels in
    the JWT claims, so changing it invalidates live sessions in a way that is
    not obvious from the call. `platformRole` grants the platform-admin bypass
    present in every guard in the system; promoting someone is not editing a
    profile and belongs in its own endpoint with its own log.
  - **The three fields do not share the same blank rule, and that is
    deliberate.** Under partial semantics null means "leave it", so without an
    exception a job title could be set but never removed. `jobTitle` is
    nullable with no default — V7 says accounts predating it simply have none —
    so a blank job title clears it to `null`. `firstName` and `lastName` are
    `NOT NULL`, so a blank one is rejected with a `400`. `@Size(min = 2)` alone
    would accept two spaces, which is why the service checks for blanks at all.
  - **`User.updateJobTitle` was removed**, subsumed by the new
    `updateProfile(firstName, lastName, jobTitle)`. It had been dead since it
    was written: declared on the entity and called from nowhere, exactly like
    `changePassword` before its endpoint existed. **`updatePlatformRole` is
    still dead and was left alone** — the admin-edit card will use it.
  - **The blank-to-null rule lives on the entity**, not in the service, because
    "a blank job title means no job title" is a rule about the field rather than
    about the request. The service rejects what is invalid; the entity
    normalises what it stores.
  - **The frontend updates the cached user instead of re-reading it.** The top
    bar renders the name and job title from `useAuth().user`, so it would go
    stale after a save. Calling the existing `refresh()` would have worked, but
    it posts to `/auth/refresh`, which mints a whole new session — rotating both
    tokens as a side effect of saving your own name is a surprise waiting for
    whoever debugs it next. Instead `session.saveUser` and an `updateUser`
    action write the user the `PATCH` already returned. No extra round trip.
  - **The page sends only what changed**, and when nothing did it shows the
    success state without calling the API, which also avoids the all-fields-null
    `400`.
  - **`/profile` is outside `RoleRoute`**: every authenticated user has a
    profile, unlike `/admin/**`. Reachable from a new "Mi perfil" entry in
    `UserMenu`, which until now only offered logging out.
  - **This gives `jobTitle` its first screen.** The field has existed since V7
    in the database, the entity, `RegisterRequestDTO`, `UserSummaryDTO`,
    `OrganizationMemberDTO` and `ProjectMemberDTO`, and is displayed in the top
    bar and both member tables — but `RegisterForm` never sent it, so nothing in
    the app could set it.
  - **Still not closed: `PATCH /users/me/password` has no screen.** A profile
    page is its natural home and adding the form there needs no backend work.
    Kept out of this card on purpose, as its own next one.
  - `mvn test` 289 → 302: `UserServiceTest` 12 → 20 and
    `UserControllerTest` 4 → 9.
- 2026-10-03 — **`login` and `POST /auth/refresh` now refuse deactivated
  accounts.** `app_user.active` has existed since V1 with no query reading it,
  so until now a logical delete of a user would have been decorative: the
  account kept logging in.
  - **The active check runs after the password check, and the order is the
    point.** Login answers the same generic message for an unknown email and a
    wrong password, so it does not leak which addresses exist. Checking `active`
    first would have broken that: a distinctive "deactivated" reply would tell
    anyone that the address is registered. Checking it second means the specific
    message only reaches someone who already proved they know the password,
    which tells them nothing they did not have.
  - **Login answers `403`, not `401`**, and not for taste: the response
    interceptor in `api/client.ts` retries a refresh on **every** `401`, and
    `authApi.login` goes through that client. A `401` from login would make the
    interceptor try to refresh with whatever stale token is in storage and
    replay the login. A `403` reaches the form untouched.
  - **Refresh answers `401`**, matching the two returns already in that method
    and landing in the `catch` of `AuthProvider.refresh`, which clears the
    session and logs the user out.
  - **No per-request check, deliberately.** `SessionService` rebuilds the user
    from the JWT claims without touching the database, which is the stateless
    trade-off `SecurityConfig` already took. **The consequence: an access token
    issued before the deactivation keeps working until it expires — 60 minutes
    by default.** At that point the client refreshes, the refresh is refused and
    the session is cleared. Immediate revocation would need stateful sessions and
    is a separate decision.
  - **No log on the refresh branch.** `AuthController` has no `AppLogger`
    injected and adding one is a constructor change outside this card; the
    `warn` in `UserService.login` covers the case worth recording.
  - **Verified by mutation.** Removing the login check turns
    `loginRejectsADeactivatedAccount` red; moving it *before* the password check
    turns `loginChecksThePasswordBeforeTheActiveFlag` red with
    `expected IllegalArgumentException but was ForbiddenException`, which is the
    enumeration leak caught directly; removing the refresh check turns
    `refreshRejectsADeactivatedAccountWith401` red. In that last one the mutated
    path then crashes on an unstubbed mock and answers `500` rather than `200`,
    so the assertion carrying the real claim is the
    `verify(sessionService, never()).create(...)` beside it.
  - **A hole this opens, worth knowing before the delete endpoints land:** a
    platform admin who deactivates their own account is now locked out of the
    app, and `AdminSeeder` does not rescue them — it recreates the default admin
    only when the account does not exist, and a deactivated account exists. That
    is question 5 of the team's authorization document. Nothing here can trigger
    it, because this card adds no way to deactivate anybody; it becomes reachable
    with the user DELETE.
  - `mvn test` 302 → 306: `UserServiceTest` 20 → 22 and
    `AuthControllerTest` 14 → 16.
- 2026-10-03 — **The five authorization questions for the user ABM are
  answered.** Decided by the team; recorded here as the reference the remaining
  cards build on. The question that framed all of them is that a user can belong
  to several organizations (`UNIQUE (organization_id, user_id)`), so the account
  is the platform's, not any one organization's.
  - **A user edits their own profile.** `PATCH /users/me` with `firstName`,
    `lastName` and `jobTitle`. Already built.
  - **An organization OWNER does not edit other people's accounts.** Creating
    users stays theirs — single and bulk — but editing a profile belongs to the
    account's owner or to a platform admin. This is the most restrictive of the
    three options that were on the table, and it keeps an OWNER of one
    organization from changing what another organization sees.
  - **An OWNER removes a membership, never an account.** The reach is
    `DELETE /organizations/{organizationId}/members/{memberId}`: the user leaves
    that one organization and keeps signing in everywhere else. Deactivating the
    account platform-wide is not an OWNER's to do. **That endpoint does not
    exist yet** — it is a card of its own, and it is the one an OWNER actually
    wants when they say they need to remove somebody.
  - **Only a platform ADMIN changes `platformRole`,** and the operation refuses
    to leave the platform without one: an admin cannot demote themselves if they
    are the last active ADMIN. It is its own endpoint, not part of the profile
    `PATCH` — the role grants the platform-admin bypass present in every guard.
  - **The last active ADMIN cannot deactivate themselves.** Same rule from the
    other direction, and it closes the hole recorded with the login hardening:
    a self-deactivated admin is locked out of the app, and `AdminSeeder` does not
    rescue them because it only recreates the default admin when the account does
    not exist.
  - **What this changes about the cards that were paused.** The admin-edit card
    shrinks to platform admins only, so it needs no organization-scoped rule at
    all. The user-delete card gains the last-admin guard. And a third card
    appears that the original plan did not have: removing a membership, which is
    the operation an OWNER was missing. The listing card is unaffected — it was
    waiting on a different question, whether the list is per organization or
    platform-wide.
- 2026-10-03 — **`PATCH /users/me/password` finally has a screen.** The endpoint
  shipped on 2026-09-16 with the note that the loop was not closed for the end
  user; a bulk-registered account could not replace its generated password from
  inside the app. No backend work here — the endpoint and its seven tests were
  already in place.
  - **A second card on `ProfilePage`**, not a route or a modal of its own. Same
    "my account" context, the page is already routed, and a separate route for
    one form buys nothing. Both cards gained an `h2` now that there are two.
  - **Its own component in `components/auth/`**, beside `LoginForm` and
    `RegisterForm`, because `ProfilePage` already carried six pieces of state
    and a second form inline would have made it hard to read. Pages compose
    forms here; this keeps that split rather than opening a one-file directory.
  - **A confirmation field, unlike `RegisterForm`, and the difference is
    real.** There an admin types a temporary password they are about to hand
    over, and a typo is undone by handing over another one. Here the user
    replaces their own password, and **this application has no recovery path at
    all**: no forgot-password endpoint, no admin reset, nothing. A typo would
    mean fixing the row in the database. The confirmation is client-side only
    and does not touch the DTO.
  - **No "must differ from the current password" rule.** The backend has none,
    and adding it only in the client would be inventing a rule that a `curl`
    walks past.
  - **The wrong-password error goes in an `Alert`, not on the field.** The
    backend answers `400 Current password is incorrect`; pinning it to the field
    would mean matching that English string, which is fragile and breaks the day
    the language decision lands. Every other form in the app surfaces backend
    errors the same way.
  - **The session survives the change, and so do the ones on other devices.**
    The backend replaces the hash and nothing else: the JWT is stateless and
    there is no session revocation, so a token issued before the change stays
    valid until it expires, anywhere it was issued. Staying signed in here is
    right; invalidating the others would need stateful sessions, the same
    trade-off recorded with the login hardening. Logging the user out locally
    would only look like a fix.
  - **The three fields are cleared on success**, so the plaintext does not sit
    in component state afterwards.
  - `docs/testing.md` is unchanged: no new backend behaviour, so no new cases.
    `mvn test` stays at 306.
- 2026-10-03 — **`DELETE /organizations/{organizationId}/members/{memberId}`**,
  the endpoint decision 3.3 called for: an organization OWNER removes somebody
  from their own organization, and never deactivates the platform account.
  - **Physical delete, unlike `Project`.** The argument that decided `Project`
    does not apply: there the logical delete won because the history *is* the
    product, and a membership carries no history — nothing beyond the (user,
    organization) pair and its roles, while the work it gave access to lives in
    the projects and versions, which are untouched. Two things settled it. The
    unique constraint `uq_org_member_org_user (organization_id, user_id)` means
    a deactivated row keeps occupying the pair, so `addMember` — whose check
    does not read `active` — would answer "User is already a member" and the
    feature would be remove-once-never-re-add unless `addMember` learned to
    reactivate. And four read points would have needed an `active` filter, two
    of them authorization paths (`assertIsMemberOf` and `assertCanManageUsers`),
    which is four chances to forget. What is lost is an audit trail of who was
    in an organization when; nothing audits that today, and if the team wants it
    the answer is the logical delete plus the reactivation path.
  - **`OrganizationMember.active` stays unused, and the name is already taken.**
    The field has existed since V3 and no query reads it. The one `active` the
    API exposes, in `OrganizationMemberDTO`, carries `user.isActive()` — the
    account's state, not the membership's, as that record's own note says.
  - **Project memberships are removed with it.** Project access never consults
    organization membership: `ProjectAccessGuard` reads `projectMemberRepository`
    only, and the organization matters in `createProject` alone. Without the
    propagation, "removed from the organization" would leave every project
    permission intact, which is not a strange state but a hole.
  - **A project can be left with no ADMIN, and that is accepted.** If the person
    removed was its only ADMIN, the project keeps running with none. Decided as
    a consequence rather than an error; blocking it would make removing someone
    depend on project-level state the organization screen cannot show.
  - **Loaded entities and `deleteAll`, never a bulk `DELETE`.**
    `ProjectMember.roles` cascades with `orphanRemoval` and its `permissions` are
    an `@ElementCollection`, so a `@Modifying` bulk delete would skip both and
    violate `project_member_role.project_member_id`. It would read as an
    optimisation and break.
  - **The organization check lives in the query, not in a comparison.** The
    route carries two independent ids, so a member of another organization must
    not be removable by guessing an id. The first attempt compared
    `member.getOrganization().getId()` against the path id and threw a
    `NullPointerException` on the first test run; the fix was not to flip the
    comparison but to let the database answer, via
    `findByIdAndOrganizationId`. One query, no null equality, and it refuses a
    foreign member with the same `Member not found` as a non-existent one — a
    distinctive message would let an owner probe which membership ids exist
    elsewhere.
  - **An OWNER may remove themselves, and there is no last-owner guard.**
    `createOrganization` adds no members at all, so **an organization with zero
    owners is the state it is born in**, not an anomaly to defend. The guard
    from decisions 3.4 and 3.5 does not transfer: the last platform ADMIN is
    irrecoverable because nobody could promote anyone, while an organization with
    no owner is recovered by any platform admin adding one.
  - **The cascade test was worthless until a mutation exposed it.** It asserted
    zero `ProjectMemberRole` rows after the delete and passed with the cascade
    removed from the entity — because without the cascade the role was never
    written, so the assertion was vacuously true. It now asserts one role
    **before** the delete, and the mutation turns it red. The two query filters
    were mutation-checked the same way: dropping the organization filter breaks
    two cases, dropping the user filter breaks one.
  - **Still open, and the real fix: project access does not require organization
    membership.** Propagating on removal closes the case this endpoint opens, but
    somebody added straight to a project without belonging to the owning
    organization still gets in. Making every project guard consult organization
    membership is a change across all of them and belongs in its own card.
  - **No frontend.** There is no organization-members screen yet —
    `AdminOrganizationsPage` is a form and a list of names — so the UI waits on
    the Admin redesign, which is still blocked on whether the user list is per
    organization or platform-wide.
  - `mvn test` 306 → 325: `OrganizationServiceTest` 23 → 33,
    `OrganizationControllerTest` 18 → 22, and a new
    `ProjectMemberRepositoryTest` (5), the second `@DataJpaTest` in the
    repository.
- 2026-10-03 — **`PATCH /users/{id}/role` and `DELETE /users/{id}`**, built
  together because they share one invariant. Platform administrators only; the
  check runs in the service, which holds the `AppLogger` that
  `AuthUtil.requirePlatformAdmin` needs, the way `createOrganization` already
  does it.
  - **One invariant replaces two identity rules: at least one active ADMIN must
    remain.** Decisions 3.4 and 3.5 were written as protections against demoting
    or deactivating *yourself*, but the self-case is the only way to reach zero:
    a caller must be a platform ADMIN, so if the target is a *different* active
    ADMIN then two exist and removing one leaves one. The invariant is also
    strictly stronger than an identity check, because of the login hardening of
    the same day — a deactivated admin keeps a valid access token for up to an
    hour, and in that window could demote the last *other* active admin. Caller
    and target differ there, so an "is it you?" test would allow it; counting
    active admins refuses it.
  - **The guard is skipped where it cannot bite:** promotions, a target that is
    already a `USER`, a target that is an inactive ADMIN (never part of the
    count), and an unchanged role. Each of those is a case in the suite
    asserting the count query is never even called.
  - **Refused with `IllegalArgumentException` (400), not `ForbiddenException`
    (403).** The caller *is* authorized; it is the resulting state that is
    refused. Throughout this codebase a `403` means "wrong caller", and
    borrowing it for a state conflict would blur that. `409` would be the
    textbook answer and there is no handler for it.
  - **Deactivating a user propagates nothing**, and that is the design the UI
    already assumes. Both membership DTOs expose an `active` that carries
    `user.isActive()`, and `ProjectMembersModal` renders **"Suspendido"** beside
    the name from it — so the member lists were built to show a suspended
    account, and propagating would delete what that screen exists to display.
    The account cannot sign in anyway, keeping the row preserves who was on a
    project, and the project member count filters `pm.active`, so a suspended
    member still counts and the number still matches the rows on screen.
    `UserService` has no membership repository, so it cannot propagate by
    construction.
  - **Contrast with removing an organization membership**, shipped the same day:
    that is about one organization's roster and deletes the row; this is about
    the account and keeps every row. Different operations, different semantics.
  - **Third `@DataJpaTest`, and a trap it walked into.** The `test` profile
    points at `jdbc:h2:mem:enerscope;DB_CLOSE_DELAY=-1` — a *named* in-memory
    database that outlives each Spring context and is shared with the
    `@SpringBootTest` one, where `AdminSeeder` **commits**
    `admin@enerscope.org`. `app_user.mail` is `UNIQUE`, so the first version of
    `UserRepositoryTest`, which persisted that same address, did not fail
    cleanly: it blocked on a lock held by a surefire JVM that was still alive,
    and the run hung for minutes. Every address in that class is now prefixed
    `count-`. Worth knowing before the next repository test: committed seed data
    is visible to every context, and a colliding unique value hangs rather than
    throws.
  - **Mutation-checked, and a derived query resists the obvious mutation.**
    Renaming `countByPlatformRoleAndActiveTrue` breaks compilation at every call
    site, which is itself the argument for derived queries over hand-written
    JPQL: the name *is* the specification. The realistic mutation is someone
    replacing the derivation with explicit JPQL, so that is what was tried —
    dropping the `active` filter turns two cases red, dropping the role filter
    turns two red.
  - **No frontend, and the queue behind the Admin redesign is now four
    endpoints:** this pair, `PATCH /users/{id}/role`, and the organization
    membership removal all wait on a users screen, which waits on `GET /users`,
    which waits on the decision of whether that list is per organization or
    platform-wide. They are reachable from Swagger and absent from the product.
    That decision has stopped being one card among several.
  - **`PATCH /users/{id}` for someone else's profile was deliberately not
    built.** Since a user can edit their own name and job title, an admin
    editing other people's profiles unblocks nothing.
  - `mvn test` 325 → 353: `UserServiceTest` 22 → 38,
    `UserControllerTest` 9 → 17, and a new `UserRepositoryTest` (4).
- 2026-10-03 — **`GET /users` and the Usuarios screen (C1).** The list is
  platform-wide and platform-admin only. Decided that way because
  `POST /auth/register` creates accounts outside every organization, so a list
  scoped per organization would leave them visible from nowhere.
  - **`organizationCount` is in the projection, and it is the point.** Showing
    the account without it would still not say the account is *orphaned*; a `0`
    in that column is what makes the problem the decision was made for visible.
    It is a correlated subquery, the shape `ProjectSummaryDTO.memberCount`
    already uses. Organization *names* are not included: they are a collection,
    and JPQL cannot build one into a record — the limitation already written
    down for `ProjectMemberRepository.findByProjectIdWithUser`.
  - **It counts every membership row, with no `active` filter**, because
    removing an organization membership is a physical delete, so there are no
    inactive membership rows to exclude.
  - **This is the one list that must NOT filter on `active`**, which runs against
    the habit of the last four cards. Every other list learned to hide
    deactivated rows; here a platform admin has to see suspended accounts,
    because this screen is the only place one could ever be revived. The state
    is a column, not a filter, and a mutation adding `WHERE u.active = true`
    turns `listItemsIncludeDeactivatedAccounts` red.
  - **No pagination, consistent with every other list, and the note is that this
    one differs in kind.** `GET /projects` already returns every project
    unpaginated for an admin, so the precedent is set — but a project is created
    deliberately while `POST /organizations/{id}/users/bulk` can add hundreds of
    accounts from one CSV. Paginating only this endpoint would leave the
    repository with two conventions; when it is done it should cover the three
    lists together.
  - **Ordered by first then last name.** A roster table is read to find a
    person, and the search box covers lookup; `findSummaries` orders by
    `lastModified DESC` because a project list is read for recent activity.
  - **`RoleBadge` was dead code and this is its first use.** It was written for
    the auth portal, noted as unused on 2026-09-10, and renders the raw enum. It
    now carries a label map — "Administrador"/"Usuario" — matching what
    `UserMenu` already does inline, so the badge no longer puts `ADMIN` in a
    Spanish interface.
  - **`RowButton` was extracted to `components/ui/`.** It was private to
    `ProjectsTable`, and it is a genuine primitive: an icon button with an
    `aria-label` and a focus ring. `ProjectsTable` now imports it.
  - **`headerCell`/`bodyCell` were deliberately left duplicated.** They were
    already copied in `ProjectsTable` and `ProjectMembersModal`, and `UsersTable`
    makes a third. Extracting two short class strings across tables with
    different column counts pays little; noted rather than done.
  - **The create form moved into a modal behind a button**, the shape
    `ProjectsPage` uses, and it refreshes the table through `RegisterForm`'s
    existing `onSuccess` callback. `useUsers` is modelled on `useOrganizations`.
  - **The projection and its subquery were mutation-checked.** Decorrelating the
    subquery (counting every membership rather than the user's) turns
    `listItemsDoNotCountAnotherUsersOrganizations` red with `expected: <1> but
    was: <3>`.
  - **Timing note for whoever runs a single `@DataJpaTest` here.** Nine cases in
    `UserRepositoryTest` measure 0.38s in total, but the class took eight minutes
    when run on its own: the cost is Spring context startup scanning a classpath
    that lives on iCloud Drive, where cold files are materialised on demand. In
    the full suite the context is built once and shared by the three
    `@DataJpaTest` classes, and the whole run is about 20 seconds. The tests are
    not slow; the first context on cold files is.
  - **C2 and C3 are what remains.** The two user actions
    (`PATCH /users/{id}/role`, `DELETE /users/{id}`) go on this table next. The
    organization membership removal does **not**: it acts on one organization's
    roster, so it belongs to an organization-members screen, which needs the
    already-existing `GET /organizations/{id}/members` wired to a frontend.
  - `mvn test` 353 → 364: `UserServiceTest` 38 → 41,
    `UserControllerTest` 17 → 20, `UserRepositoryTest` 4 → 9.
- 2026-10-04 — **The Usuarios screen got its actions (C2), plus the reactivate
  endpoint the previous card left missing.**
  - **`POST /users/{id}/reactivate` was added rather than deferred.** C1's
    justification for listing deactivated accounts was that this is the only
    screen one could be revived from — and no endpoint could revive one:
    `BaseEntity.activate()` had been declared since the first migration and
    called from nowhere, dead in the same way `updateJobTitle` was. Shipping the
    column without it would have shown a state with no way out, which is worse
    than not showing it.
  - **It takes no last-admin check**, because reactivating can only *add* an
    active administrator. A case asserts the count query is never called, so the
    reasoning is pinned rather than just written here.
  - **`POST .../reactivate`, not a `PATCH` with an `active` field.** The second
    would have reused `PATCH /users/{id}`, which is precisely the
    edit-someone-else's-profile endpoint the team decided not to build, and a
    `DELETE` that toggles would be worse.
  - **The role modal is a select, not a pair of confirm dialogs.** With two
    roles a directional dialog ("promote?" / "demote?") needs two texts and
    breaks the day a third role appears; the select maps one-to-one onto
    `UpdateRoleRequestDTO` and shows the current role as its starting value. It
    closes without calling the API when the role is unchanged.
  - **`LockIcon` for the role, deliberately not `PencilIcon`.** In
    `ProjectsTable` the pencil means "edit this entity", and editing another
    person's profile is exactly what was decided against; a pencil here would
    promise something no endpoint does. The reactivate action needed an icon
    that did not exist, so `UndoIcon` was drawn into `icons.tsx` — the file
    exists to hold hand-drawn icons rather than pull in a dependency.
  - **A warning when an admin deactivates themselves.** Decision (B) allows it
    while other admins remain, so hiding the action on your own row would be
    wrong. What is not obvious is the consequence: the session is stateless, so
    the app keeps working until the access token expires — up to an hour — and
    only another admin can bring the account back. The dialog says so when
    `user.id === caller.id`.
  - **No per-row permission gating.** `/admin/users` sits behind
    `RoleRoute role="ADMIN"`, so only platform admins reach the page at all.
    This is the opposite of `ProjectsTable`, where the row actions render for
    everyone and an unauthorized click comes back as a `403`, because that page
    is open to every user.
  - **The last-admin refusal will read in English.** `getErrorMessage` prefers
    the backend message, so an admin demoting themselves as the last one sees
    "The platform would be left without an active administrator" in the alert.
    It is the most visible instance yet of the open language question, because
    it is a message an administrator will actually hit.
  - **Frontend untested, as every UI card here.** `npm run build` and
    `npm run lint` are the gate; the reactivate half is covered on the backend.
  - `mvn test` 364 → 372: `UserServiceTest` 41 → 47 and
    `UserControllerTest` 20 → 22.
- 2026-10-04 — **The Organizaciones screen and the membership removal (C3).**
  The page went from a form plus a list of names to the table-and-modal shape
  `ProjectsPage` uses, and the `DELETE .../members/{memberId}` endpoint from
  2026-10-03 finally has a caller.
  - **Two `Modal`s cannot be stacked, so the confirmation lives inside the
    members modal.** Both of `Modal`'s effects are document-level and keyed on
    `open`: two open modals mean two Escape listeners, two Tab traps each
    querying its own panel, and a scroll lock restored by whichever unmounts
    last. The members modal therefore has two states — the roster and the
    confirmation, with a "Volver" — rather than opening a second dialog over the
    first. Worth knowing before the next nested-dialog idea.
  - **The orphaned-project warning is written as a possibility, because the UI
    cannot know.** Detecting that the person was a project's only ADMIN would
    need per-project admin counts, and no endpoint gives them. The copy says "si
    era el único administrador de alguno de ellos, ese proyecto queda sin
    administrador" — conditional on purpose rather than asserting something
    unverified.
  - **The self-removal warning says the opposite of what it would elsewhere.**
    This page is behind `RoleRoute role="ADMIN"`, so only platform admins reach
    it, and a platform admin removing their own membership **keeps full access
    through the admin bypass**. The alert says they stop appearing as a member
    and that their access continues through the role, not the membership —
    unlike the user-deactivation dialog of the previous card, where access is
    genuinely lost.
  - **`OrganizationDTO` gained `memberCount`,** which turned
    `listForCurrentUser` from returning entities into returning the projection,
    mirroring `ProjectRepository.findSummaries`/`findSummariesForMember`. The
    derived `findDistinctByMembers_User_Id` it replaced was deleted rather than
    left unused. `DISTINCT` is not needed on the member-scoped query because
    `uq_org_member_org_user` already makes one row per (organization, user).
    `toDTO(Organization)` stays for `createOrganization` and passes `0L`, which
    is true of a freshly created organization.
  - **Fourth `@DataJpaTest`.** The correlated subquery is the kind of thing
    mocks cannot check; decorrelating it turns
    `summariesCountTheMembersOfEachOrganization` and
    `summariesDoNotCountAnotherOrganizationsMembers` red. Its emails are
    prefixed `orgs-` for the shared-H2 reason recorded with
    `UserRepositoryTest`.
  - **`memberType` and `permissions` are now unions in `types/auth.ts`,** which
    they had to be for the role labels and which closes a real inconsistency:
    the project equivalents in `types/project.ts` were already typed as unions
    while the organization ones were `string` and `string[]`.
  - **Future card: an organization OWNER has no screen for this.** The endpoint
    allows them — `assertCanManageUsers` covers `MANAGE_ORGANIZATION` — and
    decision 3.3 was written for them, but `/admin/organizations` sits behind
    `RoleRoute role="ADMIN"` and the sidebar reaches it only through
    `ADMIN_ITEMS`. **An OWNER still needs the API to remove anybody from their
    own organization.** Giving them a screen is a navigation decision — where it
    lives in the sidebar for a non-admin, whether it is one page per
    organization or a picker — and is bigger than this card, so it was left out
    deliberately rather than missed.
  - `mvn test` 372 → 378, all in the new `OrganizationRepositoryTest`.
- 2026-10-04 — **The nine loose `red-*` utilities were migrated to the `danger`
  ramp.** This closes the gap first noted on 2026-09-10 and half-closed on
  2026-10-02, when the ramp was added for the `Button` variant while the existing
  reds stayed on Tailwind's defaults.
  - **Twelve occurrences across five files** — `Alert`, `TextField`, `TextArea`,
    `NewProjectModal` and `OrganizationPicker` — replaced token for token:
    `red-NNN` became `danger-NNN` with the step numbers unchanged.
  - **The five steps in use are exactly the five the ramp declares** (50, 200,
    400, 600, 700), which is why the migration needed no new values. The ramp was
    declared with those steps in 2026-10-02 precisely so this day would be a
    find-and-replace; nothing had to be invented.
  - **Zero visual change, verified rather than asserted.** The ramp's values were
    compared against `--color-red-*` in the installed
    `node_modules/tailwindcss/theme.css`: all five match character for character,
    so no rendered colour moved. `grep` confirms no `red-[0-9]` remains anywhere
    in `frontend/src`, and the built stylesheet carries no `red-*` class.
  - **`danger-50` and `danger-200` now reach the build.** They were declared in
    2026-10-02 and tree-shaken out, because only the button used the ramp;
    `Alert` is what pulls them in, so all five variables are emitted now.
  - **Nine `danger` utilities are generated**, counting the variant forms:
    `bg-danger-50`, `bg-danger-600`, `border-danger-200`, `border-danger-400`,
    `focus:border-danger-400`, `focus-visible:ring-danger-400`,
    `hover:bg-danger-700`, `text-danger-600` and `text-danger-700`.
  - **What this does not fix:** the focus ring is still `brand-400` on every
    variant but `danger`, and both sit under the 3:1 that WCAG 2.2 asks of a
    non-text indicator. That remains its own card, unchanged by this one.
- 2026-10-04 — **`PATCH /organizations/{organizationId}` (SCRUM-55, first
  half).** Renames an organization, which is the only editable field it has:
  `Organization` carries `name` and two collections, everything else coming from
  `BaseEntity`.
  - **Platform admin only, and deliberately not an OWNER.** `createOrganization`
    already requires `requirePlatformAdmin`, so letting an OWNER rename or delete
    an organization they cannot create would be the odd asymmetry. There is a
    second reason: by the finding of the previous card, `/admin/organizations`
    sits behind `RoleRoute role="ADMIN"`, so an OWNER has no screen and the
    permission would only be exercisable through Swagger. If owners should rename
    their own organization, that belongs with the owner screen already recorded
    as future work.
  - **`assertCanManageUsers` was not reused**, which is a side benefit: its name
    says users while it actually checks `MANAGE_ORGANIZATION`, and borrowing it
    for organization edits would make that name worse.
  - **The name is required, not optional.** The project `PATCH` has partial
    semantics because it has two fields; with one field "partial" is degenerate —
    a null name leaves nothing to do, so the only outcome would be the
    "at least one field" error. `@NotBlank @Size(min = 2, max = 120)` matches
    `CreateOrganizationRequestDTO`, and it also removes the need for the
    service-side blank check the project card required: there `@Size(min = 2)`
    accepted two spaces, here `@NotBlank` rejects them.
  - **`organization.name` has no unique constraint** (V3 declares it
    `NOT NULL` only), so a rename cannot collide — unlike `app_user.mail`.
  - **The response carries the real member count.** `OrganizationDTO` gained
    `memberCount` in the previous card, and the service has the entity rather
    than the projection. Reusing the controller's `toDTO(Organization)` would
    have published `0L`, which is true of a freshly created organization and a
    lie about an existing one. The service builds the DTO itself with a new
    `countByOrganizationId` — it already holds `organizationMemberRepository`,
    which is also why the count lives in the service rather than injecting a
    repository into the controller. A case asserts the count is the real one.
  - **No `@DataJpaTest`.** This card adds no filtered query:
    `countByOrganizationId` is a derived query over a single criterion, where the
    method name is the specification. The `active` filters and their mutation
    checks are all in the second half.
  - **Second half (D2) is where the risk is:** `DELETE` and `reactivate` as a
    logical delete, plus the ten read points that resolve an organization and
    would otherwise keep letting writes into a deactivated one. Splitting was
    deliberate so the rename could be reviewed without that noise.
  - `mvn test` 378 → 388: `OrganizationServiceTest` 33 → 39 and
    `OrganizationControllerTest` 22 → 26.
- 2026-10-04 — **`DELETE /organizations/{id}` and
  `POST /organizations/{id}/reactivate` (SCRUM-55, second half).** A logical
  delete on the organization row alone, platform admin only.
  - **Physical delete was never an option here.** `Organization` cascades `ALL`
    with `orphanRemoval` to both `members` and `projects`, and through projects
    to project members, roles, permissions, versions, node and connection
    changes and the two snapshot join tables. No FK in the schema carries
    `ON DELETE CASCADE`, so it would have rested entirely on Hibernate ordering
    across that graph — including the `fk_version_parent` self-reference that
    made physical delete unacceptable for `Project`. The organization case
    contains the project case and adds a level.
  - **Nothing is propagated, which is the opposite of what `Project` does, and
    for a reason `Project` never faced.** Deep propagation would make
    reactivation lossy: a project already deactivated before the organization
    went down could not be told apart from one deactivated by the cascade, so
    reviving the organization would wrongly revive it. `Project` never hit this
    because project reactivation was never built. Here the state is shown in the
    table, and the lesson from the users screen is that a visible state with no
    way back is worse than no state at all — so reactivation had to be lossless,
    and that ruled out propagation. A case asserts that deactivating an
    organization leaves its project and its member with `active = true`.
  - **Nine read points filter instead.** `findSummariesForMember`, `listMembers`,
    `updateOrganization`, `addMember`, `registerUserInOrganization`,
    `removeMember`, the CSV batch, `createProject`, and both project list
    queries through their `JOIN p.organization o`. The five that would otherwise
    let a write into a deactivated organization each have an explicit rejection
    case.
  - **`updateOrganization` was the tenth point the approved list missed.**
    Renaming is a write into the organization, so leaving it open while blocking
    `addMember` would have been inconsistent. Added with the same one-line
    change as the others.
  - **The check was taken back out of `assertCanManageUsers` and
    `assertIsMemberOf`,** which the plan had put there for defence in depth. All
    six entry points resolve the organization with `findByIdAndActiveTrue`
    *before* calling a guard, so the guard's copy is unreachable in every
    existing path — and it cost 34 test failures whose fix would have been
    stubbing the same fact twice in every member-operation test, permanently.
    The invariant is now stated instead: **every public entry point resolves an
    active organization before it authorizes**, and `requireActiveOrganization`
    remains as the helper `listMembers` uses. If a future caller reaches a guard
    without resolving first, that invariant is what has to be re-checked.
  - **`GET /organizations` does not filter for a platform admin**, deliberately:
    `findSummaries` is the admin path and the suspended rows have to be visible
    for reactivation. `findSummariesForMember` does filter. A case asserts the
    admin list includes deactivated organizations, so the asymmetry is pinned
    against someone "fixing" it later. `OrganizationDTO` carries `active` for
    the column.
  - **`DELETE` and `reactivate` resolve with plain `findById`,** not the
    filtered one: with the filter the idempotent case would answer `400` instead
    of being the no-op it is meant to be.
  - **All nine filters were mutation-checked**, one at a time, and each turns at
    least one case red. Worth recording what kind of coverage each has: the two
    in the repositories are verified behaviourally against rows that really carry
    `active = false`, while the seven in the services are verified at the
    interaction level — a mocked repository cannot model "the row exists but is
    inactive", so what the service tests pin is *which* method is called. The
    behavioural backing for those is that `findByIdAndActiveTrue` and
    `existsByIdAndActiveTrue` are themselves proven in
    `OrganizationRepositoryTest`.
  - **An environment note worth knowing: the mutation run made iCloud fork the
    files.** Writing and restoring the same five sources in a quick loop left
    conflicted copies named `OrganizationService 2.java` and the like, which
    broke the build with duplicate-class errors. They were untracked, each
    original was verified to hold the correct filter before deleting the copies.
    A mutation loop on this repository should expect that.
  - `mvn test` 388 → 416.
- 2026-10-04 — **The organization owner finally has a screen for their own
  roster**, at `/organizations/:organizationId`. Closes the gap recorded on
  2026-10-03: the removal endpoint allowed owners and only platform admins could
  reach a page.
  - **`GET /organizations/owned` rather than a field on `OrganizationDTO`.**
    Adding `callerMemberType` to the projection would have meant a
    `LEFT JOIN ... ON m.user.id = :callerId` in the admin query — which can
    legitimately match no membership — plus a changed signature and reworked
    tests across two closed cards. The dedicated query is self-scoped, needs no
    guard beyond a session, and is exactly what the sidebar asks. **The page
    needs no new field at all:** `GET /organizations/{id}/members` already
    returns the caller among the members, with their `memberType`.
  - **It filters on the permission, not the member type.**
    `MANAGE_ORGANIZATION MEMBER OF r.permissions`, matching what
    `assertCanManageUsers` actually checks. Filtering on `memberType = OWNER`
    would miss a `MEMBER`-typed role that was ever granted the permission.
    `DISTINCT` because a member with two roles would otherwise duplicate the row.
  - **`GET /users/search?mail=` closes the real hole:** an owner can create
    accounts but had no way to learn the `userId` of somebody who already has
    one, and `POST /{id}/members` takes a `userId`. Exact match, case-insensitive
    through `LOWER()` on both sides, and a projection of nothing but id, name and
    address — no `platformRole`, no `active`, no counts.
  - **A suspended account answers the same `404` with the same fixed message as
    an address that was never registered,** so the endpoint cannot be used to
    probe account states. The message does not repeat the address that was
    searched, and two cases pin both properties: one asserts the two messages are
    identical, the other that the message does not contain the needle. The
    `warn` logged when a non-admin's lookup misses names the caller, never the
    address.
  - **`UserService` took `OrganizationRepository`, not `OrganizationService`.**
    `OrganizationService` already depends on `UserService`, so the reverse would
    close a cycle; a repository depends on nothing. Same reasoning
    `ProjectAccessGuard` documents for its own shape.
  - **The sidebar reuses the slot the design already reserved.** `NAV_ITEMS` has
    carried "Organización / Equipo" with a padlock since the shell was built;
    this card gives it a `to` **only when the caller owns an organization**, so a
    plain member still sees it locked. The list is now built inside the
    component rather than as a module constant.
  - **One entry, and a selector inside the page when there is more than one
    organization.** The sidebar does not grow with the number of organizations,
    the URL still identifies one organization so it stays shareable, and an
    owner of a single organization never sees a selector.
  - **`OrganizationOwnerRoute` is UX, not security.** Every operation is already
    guarded by `assertCanManageUsers`; the route only stops somebody who types
    the URL from seeing a shell and collecting `403`s. It shows `PageLoader`
    while the owned list loads instead of redirecting, because without that a
    refresh bounces to the home page. `RoleRoute` was deliberately **not**
    generalised: platform role and organization role are different questions, and
    one parameterised guard doing both reads worse than two of fifteen lines.
  - **The remove dialog says the opposite of the admin one.** An owner removing
    themselves **does** lose access — to the organization and, through the
    membership propagation built on 2026-10-03, to its projects — and only a
    platform admin can add them back. The page then redirects to `/app` rather
    than reloading a roster that would answer `403`.
  - **`OrganizationMembersTable` was extracted** from
    `OrganizationMembersModal`, which had the markup, the role labels and the
    cell classes inline. The admin modal and the owner page now share it; the
    confirmation copy stays duplicated on purpose, because the two dialogs say
    different things.
  - **Still missing, and worth its own card: the owner has no screen for
    creating a *new* user.** `POST /organizations/{id}/users` and
    `/users/bulk` both allow owners, `AdminUsersPage` is platform-admin only, and
    this card covers adding somebody who already has an account. An owner
    onboarding a brand-new person still needs the API.
  - **The mutation run used backups outside the iCloud volume this time**, after
    the forked-file incident of the previous card, and no conflicted copies
    appeared. Seven mutations, all red: the three filters of `findOwnedBy`, the
    permission filter of `ownsAnyActiveOrganization`, and the active, case and
    exact-match properties of the user lookup.
  - `mvn test` 416 → 440.
- 2026-10-05 — **Results schema: the migration is the design, the entities
  follow it.** `docker compose up` on a fresh database failed on a chain of
  mismatches (a table created twice, then columns the entities map and no
  migration creates). The migrations and the entities were written separately
  and nothing in the suite compares them: the tests build their H2 schema from
  the entities with Flyway off, so only Hibernate's `validate` on PostgreSQL
  notices. What was wrong and what was done:
  - `V8` and `V9` both created `result_per_node`. `V9` now only adds what `V8`
    lacks (`final_result` and its three percentile tables); `V8` is untouched.
  - `V11` adds two columns the entities already mapped:
    `base_node.maintenance_duration` and `flng_unit.gas_consumption`.
  - `Result.resultPerNodes` had no `@JoinColumn`, so JPA expected a join table
    (`result_result_per_nodes`) that no migration creates. It now maps
    `result_per_node.result_id`, the `NOT NULL` foreign key V8 designed, which
    also gives cascade delete in the database. `Version.results` maps
    `result.version_id` (it defaulted to `results_id`).
  - `ResultPerNode.nodeID` maps `node_id` explicitly. Spring's naming strategy
    only inserts an underscore before an upper-case letter that is followed by a
    lower-case one, so `nodeID` silently became `nodeid`. Prefer spelling out
    snake_case names for anything with an acronym in it.
  - `year` is a reserved word in H2 2.x: Hibernate cannot create `result` on the
    default H2 URL (it logs a WARN and carries on), so nothing could ever
    persist a result in the H2 tests. `ResultMappingTest` opts into
    `NON_KEYWORDS=YEAR`; do the same for any new test that touches `result`.
  - **To check when the `Probabilistica` work is merged:** `FinalResult` there
    declares three `@OneToMany List<ResultPerNode>` (`percentile90/50/10`) with
    no `@JoinColumn` or `@JoinTable`, while `V9` creates the join tables
    `final_result_p90/p50/p10`. Unless the mapping names those tables and
    columns, startup will fail the same way. Not run, only read.
  - **Worth its own card:** a test that applies the Flyway migrations to a real
    PostgreSQL (Testcontainers) and starts the context with `ddl-auto=validate`
    would have caught all of the above. It needs Docker on the CI runner, so it
    was left out here.
  - Anyone whose local database already ran an earlier copy of `V9` or `V11`
    needs a fresh one (`docker compose down -v`): Flyway rejects a migration
    whose checksum changed.
- 2026-10-05 — **Migrations: how branches stop stepping on each other, and what
  CI now blocks.** The recurring breakage had three causes: sequential numbers
  chosen independently on every branch (across the branches, `V7` exists under four
  different names and `V11` was taken twice at the same time), two branches adding
  the same columns, and a test suite that cannot see any of it (H2, Flyway off).
  - **Naming.** New migrations are `V<yyyyMMddHHmm>__<desc>.sql` (UTC time of
    creation), so two branches cannot pick the same version. `V1`–`V11` stay as
    they are and sort first. `spring.flyway.out-of-order=true` lets a migration
    with an older timestamp that merges later still apply on a database that is
    already ahead; without it Flyway refuses to start.
  - **`scripts/check-migrations.sh`** (CI job *Flyway migrations check*): file
    names, unique versions (`8`, `08` and `8.0` are the same version), and, for a
    pull request, it fails if the branch edits, deletes or renames a migration the
    target branch already has, or adds one that is not timestamped. The label
    `migration-edit-ok` turns the first rule into a warning. Run it locally with
    `bash scripts/check-migrations.sh origin/master`.
  - **`MigrationsOnPostgresTest`** (CI job *Backend tests*): applies every
    migration to an empty PostgreSQL and starts the context with
    `ddl-auto=validate`, which catches a table created twice, a missing column and
    a misnamed file. CI fails the build if the test was skipped (no Docker), so it
    cannot pass by not running. Testcontainers is pinned to 1.21.4 in `pom.xml`:
    the 1.21.3 that Spring Boot manages speaks Docker API 1.32, which Docker
    Engine 29 (minimum 1.44) rejects. Drop the override once Boot manages 1.21.4
    or later. It only tests a fresh database; upgrading one that holds data is not
    covered.
  - **Needs a GitHub setting, not code** (repository admin): protect `master`,
    require the checks *Flyway migrations check*, *Backend tests* and *Frontend
    lint and build*, and turn on **Require branches to be up to date before
    merging**. The last one is the part that matters: two pull requests that are
    each green can still break `master` once both merge, and only a re-run on the
    updated branch sees it. On a private repository of a free personal account
    GitHub does not offer branch protection; then the rule is not to merge on a red
    check.
  - **Already visible on open branches.** `modulo-resultados-v2` has `V11`–`V13`
    and renames `V8` to `V8.5`; it will be flagged after it is updated from
    `master`. Its `V12` adds the same two columns as
    `V11__add_missing_node_columns.sql` (`base_node.maintenance_duration`,
    `flng_unit.gas_consumption`), so whichever merges second must drop its copy or
    use `ADD COLUMN IF NOT EXISTS`; `MigrationsOnPostgresTest` will fail on it
    until then.
