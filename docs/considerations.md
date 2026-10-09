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
