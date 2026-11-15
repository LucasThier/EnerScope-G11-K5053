# EnerScope — Frontend

React + Vite + TypeScript client. Styling is **Tailwind CSS only** — the single
stylesheet is `src/index.css` (`@import "tailwindcss";` plus the brand design
tokens under `@theme`).

Ships the **authentication portal**: an `AuthProvider`/`useAuth` context
(login/register/logout/refresh + role state), reusable `LoginForm`/`RegisterForm`
components and role-gated routes. The single create-user form assigns the new
user to an organization via an **optional picker** (choose an existing org or
create one inline); with no org chosen it creates a platform account.

It also ships the **application shell**: a full-width top bar (brand, active
project switcher, user menu) above a sidebar that is fixed on wide screens and
an overlay on narrow ones. User-facing copy in the shell is in **Spanish**;
identifiers, file names and route paths stay in English.

And the product screens built on it: **Projects** (`/projects`), **Equipo del
proyecto** (members and per-version roles), **Versionado**, the value-chain map
(**Cadena de valor**, `/editor`), scenario configuration and simulation, and
the administration screens (users, organizations, CSV import).

- React 19 · Vite 7 · TypeScript
- Tailwind CSS v4 (via `@tailwindcss/vite`)
- React Router (route guards by auth + platform role)
- Axios API client with automatic token refresh

## Running the frontend

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
```

Other scripts:

```bash
npm test         # node:test unit suite (tests/*.test.mjs), no extra dependency
npm run build    # type-check + production build into dist/
npm run preview  # preview the production build
npm run lint     # eslint
```

`npm test` runs on the built-in `node:test` runner (229 tests in 32 files on
2026-11-15, all passing). The per-file catalog is in
[docs/testing.md](../docs/testing.md) → *Frontend unit tests*.

The dev server proxies `/api` to the backend at `http://localhost:8080`, so run
the backend alongside it (see [backend/README.md](../backend/README.md)).

## Environment variables

None are required for local development — the Vite proxy handles API routing.
See `.env.example`. Vite only exposes variables prefixed with `VITE_`.

## Production (Cloudflare Pages)

The app is hosted on Cloudflare Pages, connected to the GitHub repository, so
every push to `master` publishes a new build (and every other branch gets a
preview URL). In production, `functions/api/[[path]].js` — a Pages Function —
plays the role of the Vite dev proxy: it forwards every `/api/*` request to the
backend on AWS Lightsail. The client keeps calling relative `/api/v1/...` URLs,
so there is no API URL in the build and no CORS between browser and API.

The Pages project needs a single environment variable, `BACKEND_URL` — the
Lightsail container service's public URL (origin only). Project settings and
the backend side are in [docs/deploy.md](../docs/deploy.md).

## Folder structure

```
frontend/
├─ index.html
├─ vite.config.ts          Vite + React + Tailwind, /api dev proxy
├─ functions/api/[[path]].js  Cloudflare Pages Function: /api/* → backend (production proxy)
├─ tests/                  node:test unit suite (*.test.mjs), the navigation golden
│                          (permissionsNav.golden.json) and Playwright scripts (*-browser.mjs)
├─ src/
│  ├─ main.tsx             React entry point
│  ├─ App.tsx              Router, route guards, providers (AuthProvider at the root)
│  ├─ index.css            Tailwind import + brand design tokens (@theme)
│  ├─ assets/              Brand artwork (monogram and full logo, dark and inverse)
│  ├─ api/                 All HTTP: client.ts (axios + refresh), session.ts (tokens),
│  │                       auth, users, organizations, projects, versionAccess (/access and
│  │                       version roles), diagram, economics, scenarioConfiguration,
│  │                       errors.ts (ApiResponse message + Spanish translations of known errors)
│  ├─ permissions/         access.ts — the role checks (platform admin, project admin/editor,
│  │                       version capabilities) in one place
│  ├─ hooks/               Providers and hooks: AuthProvider/useAuth, ActiveProjectProvider/
│  │                       useActiveProject, MyOrganizationsProvider/useMyOrganizations,
│  │                       SidebarProvider/useSidebar, ThemeProvider, useNavigationTree,
│  │                       useProjectAccess (/access), useVersions, useCsvUserImport, …
│  ├─ routes/              ProtectedRoute, RoleRoute, ProjectRouteGuard, OrganizationMemberRoute,
│  │                       OrganizationOwnerRoute, DashboardRedirect
│  ├─ components/
│  │  ├─ ui/               Brand-styled primitives (Button, TextField, Card, Modal, Tabs, Alert, …)
│  │  ├─ layout/           AppLayout, TopBar, Sidebar, navItems, ProjectSwitcher, UserMenu
│  │  ├─ versionRoles/     Roles por versión: VersionRolesTab, VersionPicker, VersionRolesPanel,
│  │  │                    MyAccessCard, AssignVersionRoleForm
│  │  ├─ versions/         Version tree, new/delete/merge/conflict dialogs
│  │  ├─ projects/ members/ organizations/ users/ import/   Admin and team screens (import/ = CSV flow)
│  │  ├─ editor/           Cadena de valor: canvas, map, node forms, nodeCatalog (labels)
│  │  └─ economics/ auth/ dashboard/ settings/
│  ├─ pages/               One component per route
│  ├─ utils/               Pure functions kept free of React and of the API client so they are
│  │                       unit-testable: activeProject, versionRoles, csvImport, csvImportBatches,
│  │                       workspaceRoutes, projectPermissions, membershipGuards, …
│  └─ types/               Types mirroring the backend DTOs (auth, project, versionAccess, …)
```

## The application shell

`AppLayout` renders a full-width `TopBar` above a `Sidebar` and the routed page,
inside the `MyOrganizationsProvider` and the sidebar provider.

- **Active project.** `ActiveProjectProvider` loads `GET /projects` and resolves
  the active project in this order: the `projectId` of `/projects/:projectId/*`,
  then the last selection (`lastProjectId` in LocalStorage), then the first
  project. A route id that is not in the caller's list resolves to **none** —
  never silently to another project. Read it with `useActiveProject()`; never
  fetch projects from a component. The pure rules are in
  `utils/activeProject.ts`.
- **`ProjectRouteGuard`** wraps every `/projects/:projectId/*` route (team,
  versions, scenario configuration, simulations, economics). If the id is not in
  the list (deleted, or someone else's) it redirects to the same module of the
  stored or first project, or to `/projects`; while the list loads it shows a
  loader. `/versions` (*Versionado*) is only an index: with an active project it
  redirects to `/projects/{active}/versions`, otherwise it lists the projects.
  Switching project in the top bar keeps the module and drops version and
  evaluation ids of the old one.
- **Sidebar.** At **80rem and wider** (`PIN_QUERY`) and with the pin on — the
  default — the sidebar is fixed and pushes the content. The pin button at its
  foot (only shown at that width) or `Ctrl`/`Cmd`+`B` unpins it and the choice is
  stored in LocalStorage as `sidebarPinned`. Unpinned, or between 64rem and 80rem,
  it is an overlay: it opens once on `/app`, the left screen edge and
  `Ctrl`/`Cmd`+`B` open it, any navigation closes it. Below **64rem** it is a
  drawer. Exactly one item is highlighted as current (`activeLeaf` in
  `navItems.tsx` picks the best match). The sections the caller cannot reach are
  hidden (`useNavigationTree`); entries without a `to` render locked on purpose
  (padlock, lighter weight, never dimmed — dimming made them unreadable).
  Platform admins also get an "Administración" group.
- **Organizations.** `MyOrganizationsProvider` fetches
  `GET /organizations?mine=true` (the caller's own memberships — platform admins
  included), `GET /organizations/owned` and, for a platform admin, the full list,
  once; `useMyOrganizations()` and `useOwnedOrganizations()` read from it. It
  decides who sees *Mis organizaciones*.

## Permissions and `/access`

- **Where the checks live.** `src/permissions/access.ts` holds the platform,
  project and version role checks; pages and hooks import from it instead of
  re-deriving them inline.
- **`/access` is the source of truth for versions.** `useProjectAccess(projectId)`
  calls `GET /projects/{id}/access` (through `api/versionAccess.ts`) and returns,
  per version, the caller's `source` (`PLATFORM_ADMIN`, `PROJECT_ADMIN`,
  `VERSION_ROLES`, `PROJECT_FALLBACK`), roles and `can*` flags. Whether someone
  may **manage the roles** of a version is decided by `canManageRoles` only — not
  by `canManage`: a version administrator who is not a project administrator
  can delete or merge the version but sees a notice instead of the assignment
  form. The backend enforces the same rule; the frontend only hides what would
  be refused.
- **Editor gates (`utils/editorPermissions.ts`).** `editorGates(access, versionId,
  failed, loading)` turns `/access` into what the editor may do: `canEdit` (create,
  move, rename, edit and delete **nodes**), `canEditConnections` (create and delete
  **connections**), `canCreateRoot`, `canBranchFrom(parentId)` and `readOnly`.
  **Deny by default:** no access data, a failed load or a load in flight give
  `false` everywhere. `EditorPage` also checks `canEdit` / `canEditConnections`
  **inside each handler** (`if (!canEdit) return;`), not only by hiding buttons, so
  a shortcut, a drag or a stale panel cannot reach the API. The role-based check
  `canEditProjectByRole` was **removed** (a test fails if it comes back): the
  backend flags are the only source.
- **`ProjectAccessProvider`** (mounted in `AppLayout`) keeps the
  `/projects/{id}/access` answer of the project being viewed so navigating between
  screens does not refetch it. It never fetches on its own: screens ask through
  `useGuardedProjectAccess(projectId)` (`ensure` loads unless the answer is
  cached, fresh or in flight; `retry` always refetches). Shared by **Editor,
  Versionado, Configuración del escenario, Escenarios and Economía**. The state
  machine is in `utils/projectAccessState.ts`.
  - **Where it invalidates:** at the API layer, not in the screens.
    `api/projects.ts`, `api/versionAccess.ts` and `api/organizations.ts` wrap the
    calls that change who can do what (roles, members, versions, deactivations)
    in `invalidatesAccess(...)`, which emits through `api/accessEvents.ts` once the
    request succeeds. The provider listens and drops the cache of that project
    (or of all when no id is given); mounted screens then refetch.
  - **Equipo del proyecto** does **not** use the cache: `ProjectTeamPage` has its
    own hook, `useProjectAccess(projectId, { withSummaries })`, which also loads
    version summaries and reloads after each change made on that screen.
  - **Limit:** invalidation only follows changes made **in this tab**. A change
    from another tab, another browser or another person is not seen until the
    cache is refetched (a retry, a project switch or a reload); the backend still
    refuses what the user may no longer do.
- **Characterization tests and the golden.** `tests/permissionsCharacterization.test.mjs`
  pins the sidebar for every combination of platform role, project role,
  organization membership, project present and loading against
  `tests/permissionsNav.golden.json`, and checks `src/permissions` against the
  inline checks it replaced. When the menu changes **on purpose**, regenerate the
  golden with:

  ```bash
  RECORD_PERMISSIONS_GOLDEN=1 npm test
  ```

  then read the diff of the JSON and commit it with the change. Never regenerate
  it to make an unexpected failure go away.

## Screens worth knowing

- **Equipo del proyecto** (`/projects/:projectId/team`) has two tabs, kept in the
  URL (`?tab=roles&version=…`): *Integrantes* (the project members; the project
  administrator adds, changes and removes them) and *Roles por versión*. The
  second shows **Mis accesos** (what the caller can do on each version), a
  version picker (searchable; *Solo las que administro* filters it) and, for the
  selected version, its members and the form that assigns a role (Administrador
  de versión, Editor of one vertical, Simulador). Only the project administrator
  and the platform administrator can assign or revoke roles.
- **Detalle de usuario** (`/admin/users`, `UserDetailModal`) offers the role
  change and the deactivation (or reactivation) from inside the detail, reusing
  `ChangeRoleModal` and `DeactivateUserDialog` — the same ones as the list. Which
  actions appear is decided by `userDetailActions` (`utils/userDetail.ts`). After
  an action the page bumps `refreshToken` so the detail is read again; a failed
  load or a failed reactivation shows the error inside the detail with
  *Reintentar*.
- **Versionado** (`/projects/:projectId/versions`). **Nueva versión** from an
  existing version is offered to anyone who can edit the project; the **first
  (root) version** of a project only to the project administrator. An editor on
  an empty project sees *Pedile al administrador del proyecto que cree la primera
  versión.* The parent choices of **Nueva versión** are limited to versions the
  caller can edit, and **Mergear** appears only when the caller can manage the
  parent (both read from `/projects/:id/access`). Deleting a version deletes the
  whole subtree and needs permission on every version in it.
- **Roles del proyecto** are labelled *Administrador del proyecto*, *Editor del
  proyecto* and *Ejecutivo* (`PROJECT_MEMBER_TYPE_LABELS`). An **Ejecutivo**
  (`EXECUTIVE`) only reads the whole project; `EXECUTIVE_LIMITS` (in
  `projectMemberTypeLabels.ts`) is the copy shown when adding one or confirming a
  role change, next to `EDITOR_LIMITS`. `assignableMembers` leaves Ejecutivos (and
  administrators and inactive accounts) out of the version-role form, because an
  Ejecutivo cannot hold version roles. The **Cadena de valor** editor stays
  **read-only** for them: `/access` grants no edit, so `editorGates` returns
  `readOnly`. In the projects table, members who cannot administer see **Ver
  equipo** instead of the manage button.
- **Configuración del escenario**: *Guardar* follows `canEditEconomicConfiguration`;
  *Crear copia simple* needs edit permission on every vertical of the scenario
  (`editableVerticals`) and otherwise shows which verticals are missing.
- A 403 for a user cached as platform administrator re-reads the user (at most
  once every 5 s, `api/authEvents.ts`); if the session cannot be renewed the app
  returns to the login.
- **Error messages** from the backend are translated in `api/errors.ts`, including
  those of branching, merging, per-vertical edits and lineage (*No tenés permiso
  para editar esta versión*, *No se pueden relacionar versiones de proyectos
  distintos*, *El diagrama tiene un ciclo…*). A message that is not in the table
  is shown as the backend sent it, in English.
- Access flags come from `GET /projects/:id/access`. The API field
  `canCreateVersions` was renamed `canCreateRootVersions` (true only for the
  project and platform administrators); the `ProjectAccess` type carries it, but
  no screen reads it — *Nueva versión* decides the root case from
  `canCreateRoot`, fed by the project-administrator check.
- **Cadena de valor** (`/editor`) is in Spanish: screen copy and every node,
  field, state and vertical label live in `components/editor/nodeCatalog.ts`
  (Pozo, Red de recolección, Planta de tratamiento, Gasoducto, Planta
  compresora, Planta de licuefacción en tierra, Unidad FLNG, Terminal portuaria,
  Buque metanero, Consumo interno/industrial; Extracción, Transporte,
  Licuefacción, Exportación). Stored values and API enums stay in English; add a
  Spanish label there whenever you add a node type or field. The catalog also
  holds the connection rules (`ALLOWED_CONNECTIONS`) and `buildNodePayload`.
  - **Conexión de gasoducto** is the node type `PIPELINE_CONECTION` (spelled
    that way in the API; vertical Transporte). Connection rule, confirmed by the
    team: it **only receives from a `PIPELINE`** and sends to the **same
    destinations as a `PIPELINE`** (`PIPELINE_TARGETS`). Its fields are
    *Capacidad de transferencia* and *Prioridad de salida*.
  - **Unidad FLNG** has two more fields, *Capacidad del buque* (`shipCapacity`)
    and *Consumo de gas* (`gasConsumption`).
  - `buildNodePayload` sends **`0`** for any numeric type field the form left
    empty, so every field of the type always travels in the payload.
  - **Known debt:** the catalog's `integer` flag is **not applied** anywhere —
    nothing reads it. A decimal typed in an integer field (e.g. *Capacidad del
    buque*) travels as a decimal, and how the backend treats it (reject,
    truncate or accept) has **not been verified**.
- **Importación de usuarios por CSV** (`components/import`, `hooks/useCsvUserImport`,
  `utils/csvImport*.ts`). Opened with **Agregar integrantes** on an organization
  (*Administración → Organizaciones*) or from the organization's team screen.
  The file takes up to **500 rows and 1 MB**, with the columns `mail`,
  `firstName`, `lastName` and optionally `role` and `jobTitle` (alias `puesto`);
  the template can be downloaded. The valid rows are sent in **sequential batches
  of 50**, each its own request, with a progress bar and a *Cancelar* button.
  Things to keep in mind:
  - Failures show the **real line of the file** the user uploaded, not the line
    inside a batch.
  - **A cut can leave partial writes.** After a network error or a `5xx` the
    client cannot know whether that batch was written; it reports the rows that
    may exist and the rows never sent. Cancelling aborts the request in
    flight.
  - The **credentials** (`mail,password`) of what was created exist only in the
    response: after a cut or a cancel they can still be downloaded, and closing
    the dialog without downloading them asks for confirmation.
  - The backend refuses a file of more than 500 rows (`400`) and one above 1 MB
    (`413`), so both limits are enforced on both sides. A `413` shows *El archivo
    supera el tamaño máximo de 1 MB.* (the server answers it before checking
    permissions).

## Conventions

- **Styling:** Tailwind utility classes only. Do not add `.css` files or
  inline styles. Reuse the brand design tokens (`bg-brand-*`, `text-ink-*`)
  defined under `@theme` in `index.css`; extend the theme there if a new token
  is genuinely needed. The ramps are anchored on the logo's colours
  (`brand-500 #4CAF50`, `brand-900 #1B5E20`, `ink-800 #232B33`) — never
  hard-code a hex in a component.
- **Where colour goes.** The page is `ink-50`; cards, the top bar, the sidebar,
  the menus and the form controls are white. That one step of separation, plus
  a `shadow-sm`, is what makes a card read as a sheet — never tint the card.
  Brand green is an *accent, never a surface*: primary buttons, links, and the
  active navigation item, and nothing else. Every other surface, border, icon
  and text colour comes from the ink ramp. Analysts sit in this tool for hours;
  green earns attention because it is rare.
- **Surfaces come from tokens.** `body` carries the page background in
  `index.css`; a full-height page container must use `bg-ink-50` rather than a
  hard-coded white, so a change to the token reaches every screen.
- **Brand mark.** `<Logo variant="mark">` is the monogram for tight spots (the
  top bar); `<Logo variant="full">` adds the wordmark, for sign-in. Both are
  sized by height only (`size="sm" | "lg"`), never by width. `tone` names the
  **artwork**, not the surface: the default `dark` is the charcoal original for
  light backgrounds, `tone="light"` is the white cut for `ink-900` ones. Never
  fake either with a CSS filter — inverting turns the brand green magenta.
- **Contrast.** Every text/background pair meets WCAG AA (4.5:1), including the
  tinted hovers. The practical rules that follow from the ramp:
  - `ink-500` (5.69:1 on white, 5.30:1 on the `ink-50` page) is the **floor for
    text** — secondary copy, metadata and labels included.
  - `ink-300` and `ink-400` are for borders, dividers, decorative icons and
    placeholders. Never for text.
  - White text needs `brand-800` or darker (`brand-700` is 4.12:1 and only
    clears AA at large sizes, so it is not enough for a 14px button label).
  - `brand-800` on `brand-50` (4.56:1) is the active-navigation pair.
- **Type scale.** Four steps, and hierarchy is carried by size and weight rather
  than by colour alone: page title `text-2xl font-semibold text-ink-800`,
  section title `text-lg font-semibold text-ink-800`, body `text-sm
  text-ink-700`, label/metadata `text-xs font-medium uppercase tracking-wide
  text-ink-500`.
- **Spacing.** Stay on the 4px grid: `1 2 3 4 6 8` (4/8/12/16/24/32px). Avoid
  the half steps (`py-2.5`, `gap-1.5`), which land on a 2px grid and drift out
  of rhythm with everything else.
- **Copy:** user-facing text in Spanish; identifiers, file names, folders,
  code comments and route paths in English. That includes the fallback strings
  passed to `getErrorMessage` and thrown from the providers — every screen is
  Spanish now, so an English fallback would surface as a language break the
  moment a request fails. **Caveat:** `getErrorMessage` prefers the backend's
  `ApiResponse.message`, and the backend still answers in English, so a real
  API error still reaches the user in English. Fixing that is a backend card.
- **Auth:** read auth state and actions through `useAuth()`; never touch tokens
  or call the auth endpoints directly from components.
- **Naming:** all identifiers, files and folders in English. Components in
  `PascalCase`, everything else in `camelCase`.
- **API access:** always go through `src/api/*`. Never call `axios` directly in
  components — use `authApi` / the shared `client` so token handling and refresh
  stay centralised.
- **Session:** read and write tokens only through `session` (`src/api/session.ts`).
- **Types:** keep `src/types/*` in sync with the backend DTOs.
- **Tests:** the bar is `npm test`, `npm run build` (type-check) and `npm run
  lint`; all three must pass. New logic goes in `src/utils` or `src/permissions`
  as pure functions (no React, no API client) so it can be tested with
  `node:test`; register every new test in [docs/testing.md](../docs/testing.md).
  If the sidebar changes on purpose, regenerate the golden (see *Permissions and
  `/access`*). The `tests/*-browser.mjs` Playwright scripts are separate (they
  need `APP_URL` and `PLAYWRIGHT_MODULE`).

## Scenario economics

Open **Simulaciones / Escenarios** with an active project to select an existing
scenario, configure a simplified economic evaluation and inspect saved results.
See [economic frontend](../docs/economic-module/economic-frontend.md) for routes, assumptions,
permissions and historical compatibility.

Run `npm test` for the dependency-free Node test runner covering the economic
configuration adapter and indicator display. The optional browser fixture check
is documented in the economic frontend guide.

Saved results include **Operativos** and **Económicos** tabs. Operational results
show annual metrics for a selected node using the saved simulation snapshot.
Run the optional results browser checks with node tests/results-browser.mjs;
see the economic frontend guide for fixture screenshots and prerequisites.

Guardar preserves incomplete economic drafts in the database. Guardar y simular 
requires a complete configuration; the summary lists every pending section and 
simulation attempts open the first invalid one.

## Probabilistic evaluation results 

Guardar y simular saves configuration and submits 1–100 rounds with an optional seed 
string. Progress polls every two seconds while visible. A statistical summary opens 
completed evaluations; one round selector drives both economic/operational tabs. 
Old snapshots stay individual. Editors can repeat failed/interrupted jobs. 
The physical well editor remains outside this delivery.

Validation: npm test, npm run build and npm run lint. Browser scripts 
tests/economic-browser.mjs, tests/results-browser.mjs and 
tests/probabilistic-browser.mjs use local Vite and PLAYWRIGHT_MODULE pointing to 
Playwright, with isolated HTTP fixtures. 
See [economic module](../docs/economic-module/economic-module.md).

### Scenario creation

In Simulaciones / Escenarios, users with edit permission can select Nuevo escenario, 
enter a name, and optionally copy a version from the same project. Crear y configurar 
opens the economic editor. Editar mapa opens that scenario’s physical graph. 
Project selection is shared with the global header.


### Scenario configuration and simulation

Configuration is at `/projects/:projectId/scenario-configurations`; scenario forms retain `/projects/:projectId/versions/:versionId/economics/configuration`. Simulation remains `/projects/:projectId/versions`, with a rounds/seed dialog. The map is unchanged. Browser acceptance: economic-browser.mjs, projectScoping-browser.mjs, results-browser.mjs and probabilistic-browser.mjs under tests; set APP_URL and PLAYWRIGHT_MODULE for a local Vite server and Playwright installation.
