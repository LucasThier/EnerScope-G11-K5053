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
