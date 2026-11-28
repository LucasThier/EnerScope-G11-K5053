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

`npm test` runs on the built-in `node:test` runner (296 tests in 35 files on
2026-11-22, all passing). The per-file catalog is in
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
│  │  ├─ versions/         Version tree, new/delete/merge/conflict dialogs, ImportVersionCsvModal
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
- **Sidebar.** Two modes, no preference (`hooks/sidebarMode.ts`, one media
  query, `FIXED_QUERY = (min-width: 80rem)`). At **80rem (1280px) and wider** it
  is always a fixed column: full height, the logo inside it, the top bar to its
  right, the content pushed; no hamburger, and `Ctrl`/`Cmd`+`B` does nothing.
  Below **80rem** it is a drawer under the top bar (56px, 64px from `lg`):
  the hamburger (*Abrir/Cerrar menú*) or `Ctrl`/`Cmd`+`B` opens it over a scrim,
  `Tab` is trapped, and `Escape`, a press outside, any navigation or crossing
  80rem closes it, returning focus to the hamburger. There is no pin; the old
  `sidebarPinned` LocalStorage key is removed once per load. Exactly one item is highlighted as current (`activeLeaf` in
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
- **Inicio** (`/app`, "Tu espacio de trabajo") fits a 1280×720 screen without scrolling. From `lg` it is a 12-column grid
  (`gap-6`, `gap-5` on a viewport ≤ 760px tall): the recent project (hero, 8 columns) and the project counter (4); below,
  **Escenarios** (7) and, on the right (5), **Proyectos recientes** (up to 3) and **Accesos rápidos** (up to 4 chips, an
  icon and a title each; the description is the tooltip and the accessible name). Below `lg` the blocks stack in that order.
  **Escenarios** shows the recent project's scenarios from calls that already exist: `GET /projects/:p/access` (cached),
  `GET /projects/:p/versions`, and `GET /projects/:p/versions/:v/scenarios` for the **root version** (the oldest root the
  person can view; with none visible, the oldest visible version) — nothing was added to the backend. Up to 5 rows (4 on a
  viewport ≤ 760px tall): the default scenario first, then the most recently modified; each row has the name, the status chip
  (the same texts as the version lists, plus **Borrador** when `draftPending`), the relative date and one action — *Simular*
  (opens the existing simulation dialog) when it needs a run and the person may start it, *Ver* otherwise, nothing while it
  is simulating. Rows sit at the top at their natural height; the card has its own height (it does not stretch to the right
  column), at least about three rows. Under the rows, a **next-step footer** (`scenarioFooter`): no scenario → *Todavía no hay
  escenarios* + **Crear escenario**; one → *Agregá otro escenario para poder compararlos* + **Nuevo escenario** (both buttons
  only if `createBlockedReason` allows it, and they open the scenario list's own name dialog); two or three with something to
  simulate → *N escenarios por simular* + *Ver todos*; all up to date → the last saved comparison (`GET /projects/:p/comparisons`,
  when the person can read it); four or more rows → no footer. **Comparar** (header, `outline` button variant) and the
  **Comparar escenarios** chip appear when the sidebar reaches Comparaciones; below 2 scenarios in the shown version Comparar
  carries the title *Se necesitan al menos 2 escenarios* (the comparison screen needs 2 in the whole project and never disables
  itself). Chips: administrators
  get *Nuevo proyecto, Comparar escenarios, Usuarios, Organizaciones*; everyone else *Nuevo proyecto, Comparar escenarios,
  Mis organizaciones, Mi perfil*, each only if the sidebar's rules offer it (`quickActionsFor`, built on `useNavigationTree`).
  The logic lives in `src/utils/homeScenarios.ts` (tests: `tests/homeScenarios.test.mjs`); the layout is checked by the
  manual `tests/homeLayout-browser.mjs`.
- **Escenarios** (flow: version → its scenarios → one scenario). A **version** is the physical base; a **scenario** is the
  economic and simulation configuration that rests on it. `Configuración del Escenario` (`/projects/:p/scenario-configurations`)
  and `Simulación de Escenarios` (`/projects/:p/simulations`) first list the **versions** ("Versión (base física)", with
  search); a version opens its scenarios (`/projects/:p/versions/:v/scenarios` for configuration, `/versions/:v/simulations`
  for simulation): name, status (*Al día / Desactualizado / Sin simular / Simulando…*, read from the API), last simulation,
  and **Nuevo escenario** (the name is always typed), **Duplicar** (asks a name; copies only the economic configuration),
  **Renombrar** and **Eliminar** (the confirmation says results and saved comparisons are kept). Buttons follow
  `/projects/:id/access` and deny by default (`utils/scenarioRules.ts`): create/duplicate/rename/edit need to edit the
  version, delete needs to administer it, simulate needs to simulate it, the read-only Ejecutivo only reads; a disabled
  button carries the reason. A scenario opens at `/versions/:v/scenarios/:s/configuration` (economic configuration;
  **"Operación" is not edited here**: a note and **Editar operación en la versión** lead to the version's node editor,
  `/editor?versionId=…`; an *Desactualizado* scenario shows "La base cambió: revisá este escenario.", and a recovered draft
  that carried node values shows "Este borrador traía cambios de operación; editalos en el nodo." with *Descartar
  borrador*) and `/versions/:v/scenarios/:s/history|results/:evaluationId?`. The old `/versions/:v/economics/...` routes
  redirect to the version's default scenario. All scenario calls are in `api/scenarios.ts`
  (`/projects/{p}/scenarios/{s}/...`); their errors are translated in `api/errors.ts`. The comparison screens are unchanged.
- **Configuración del escenario**: *Guardar* follows the version-edit permission (`utils/scenarioRules.ts`); *Crear copia simple*
  creates a **new scenario** (typed name) with the simple equivalent of an advanced configuration; a failure leaves the
  empty scenario in the list and says so.
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
- **Importación de nodos por CSV en una versión** (`components/versions/ImportVersionCsvModal`,
  `hooks/useVersionCsvImport`, `utils/versionCsvImport.ts`, `projectsApi.importVersionNodesCsv`).
  *Versionado* → botón **Importar CSV** de cada fila de versión (ícono de subida), solo en las
  versiones donde `/access` da `canEdit` (deniega por defecto). Importa en una versión que **ya
  existe**: el backend (`POST /version/{id}/nodes/import`, multipart, campo `file`, hasta 10 MB y
  10.000 filas) **solo agrega** nodos nuevos, no actualiza ni reemplaza los existentes, y una fila
  `SCENARIO` reemplaza la configuración económica del «Escenario base». Es todo o nada: si falla
  algo, no se importa nada. Flujo del modal:
  1. Elegir el archivo. El cliente lo lee con las mismas reglas del backend (comillas, BOM, CRLF,
     líneas vacías; el encabezado es la línea 1) y muestra qué trae (nodos, conexiones, economía)
     o los problemas que el backend rechazaría seguro, con su número de línea. Detecta, entre otros,
     una `identity` repetida en el archivo o que no es un UUID. **Que una `identity` ya exista en la
     versión solo lo sabe el backend** (`400`).
  2. Permisos del archivo contra `/access` de esa versión: las verticales de sus nodos
     (`editableVerticals`), las conexiones (`canEditConnections`) y la economía
     (`canEditEconomicConfiguration`). Si falta algo, se explica y no se puede importar.
  3. Avisos con una casilla de confirmación obligatoria: solo agrega (puede duplicar), reemplaza el
     Escenario base (si hay fila `SCENARIO`) y no hay deshacer.
  4. Importar, con **Cancelar importación** mientras se envía. Cancelar o perder la conexión **no
     prueba que no se importó**: el mensaje manda a revisar la versión en el editor antes de
     reintentar.
  5. Resultado con la cantidad de nodos y los botones **Ver en el editor** y **Ver escenarios**.
  Se sube **un solo archivo** (nodos y, opcionalmente, conexiones y economía). El selector es
  `components/ui/FilePicker`: el input nativo queda oculto solo a la vista (conserva foco, etiqueta,
  Enter y Espacio) y los textos son propios, en español. Tres plantillas alternativas: la principal,
  `plantilla-nodos-y-conexiones.csv` (sin economía), y los ejemplos secundarios `plantilla-nodos.csv`
  y `plantilla-nodos-con-economia.csv` (este deshabilitado sin `canEditEconomicConfiguration`). Las
  conexiones de ejemplo (`TREATMENT_PLANT → PIPELINE`) cumplen `canConnect`. Van **sin columna
  `identity`** para que un ejemplo no choque. La ayuda sobre `type` e `identity` está en un
  desplegable cerrado; aclara que `identity` es opcional y, si viene, no puede existir ya en la
  versión ni repetirse en el archivo. Con un archivo listo para confirmar, las plantillas y la ayuda
  se ocultan (así la ventana entra en 1280×720) y el botón dice qué se importa («Importar 2 nodos y
  1 conexión»). Los errores
  del backend (en inglés, uno por vez) se traducen en `api/errors.ts` por expresión regular
  (`translateCsvImportMessage`, `describeCsvImportError`): «Línea N: …» con el motivo; lo que no se
  reconoce se muestra con un texto genérico y el original como detalle. Las tres plantillas se
  verifican contra el importador real en `VersionNodeCsvImportTemplatesTest` (copias en
  `backend/src/test/resources/csv-templates/`: regenerarlas si cambian las plantillas; un test del
  frontend falla si no son idénticas byte a byte).
- **Escenario que no se puede simular** (Comparaciones, `components/comparison/ScenarioTile` y
  `utils/readiness.ts`): los motivos del backend (`readinessErrors`) se muestran traducidos. Si son
  del diagrama (conexión no válida, buque metanero sin terminal, ciclo) el enlace es *Revisar el
  diagrama* (editor de la versión); si falta configuración, *Completar configuración*; si el
  escenario no se pudo verificar, no hay enlace. No hay estados nuevos: `ScenarioStatusChip` no cambia.
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
- **Where colour goes.** The page is `ink-50`; cards, the top bar, the menus
  and the form controls are white. That one step of separation, plus
  a `shadow-sm`, is what makes a card read as a sheet — never tint the card.
  Brand green is an *accent, never a surface*: primary buttons, links, and the
  active navigation item, and nothing else. Every other surface, border, icon
  and text colour comes from the ink ramp. Analysts sit in this tool for hours;
  green earns attention because it is rare. **One exception: the sidebar** is a
  deep forest-green rail (`bg-sidebar`: `#114636` in the light theme, a deeper
  `#0e2f26` in the dark one, 1.33:1 above the dark page), styled only from its
  own `sidebar-*` tokens — white labels (10.75:1 light, 14.45:1 dark), section
  titles at 85% white, a pill for the current row (light `#D6F5C3` with
  `#1B5E20` text, 6.64:1; dark `#2D6A53` with white text, 6.37:1), a white focus
  ring. Never reuse those tokens elsewhere, and never point `nav-*` at them:
  the top bar and its popovers use `nav-*`. `tests/sidebarTheme.test.mjs` holds
  every pair to AA in both themes.
- **Surfaces come from tokens.** `body` carries the page background in
  `index.css`; a full-height page container must use `bg-ink-50` rather than a
  hard-coded white, so a change to the token reaches every screen.
- **Page title, tables, empty and loading states come from `components/ui`, not from markup written in a screen.**
  - A page title is `<PageHeader title description back eyebrow actions>`: one
    `h1` per page, `text-2xl font-semibold text-ink-800`, `mb-6` below. Inside a
    container that already spaces its children (`flex gap-6`, `space-y-*`) pass
    `spaced={false}`. Sign-in and the editor keep their own.
  - A data table takes its cells from `ui/tableClasses.ts`: `tableHeaderCell` /
    `tableBodyCell` (`px-4 py-3`, for a table that is the content of a page) or
    the `…Compact` pair (`px-3 py-2`, for a modal, a side panel or a dense
    numeric table). The header is the small uppercase `ink-500` label. Add
    `align-top`, `whitespace-nowrap` or `text-right` where the table is written.
  - "Nothing here" inside a card or section is `<EmptyState action={…}>`;
    "still loading" is `<LoadingState>` (a `role="status"` paragraph). A whole
    screen waiting is `PageLoader`; a spinner beside a label is `Spinner`.
  - `tests/pageHeader.test.mjs`, `tableClasses.test.mjs` and `emptyState.test.mjs`
    fail on a hand-written `<h1>`, table-cell copy or `px-4 py-8 text-center`.
- **The economic flow uses the same controls as the rest of the app.**
  - A `<select>` is `SelectField` (a form, 48px) or `FilterSelect` (a filter
    with a hidden label, 40px); never a hand-written one. A text button is
    `Button`; a link that must look like a button is `ButtonLink` (same
    variants), because an action with an address is an `<a>`.
  - A sheet is `Card`, with `padded={false}` plus a `className` when it needs
    another padding. A box *inside* a card is not a `Card` (it would add a second
    shadow): give it `rounded-control border border-ink-200`.
  - A dialog is `Modal`. Never a native `<dialog>`; a dialog that must not close
    while it submits passes `onClose={() => { if (!busy) onClose(); }}`.
  - `tests/economicsControls.test.mjs` fails on a `<select>`, a `<dialog>`, a
    hand-drawn card or an undocumented `<button>` in the economic module.
- **Type scale: six steps and two outside it.** The family is the system sans
  (nothing is declared or loaded), weights are `semibold` for titles and buttons,
  `medium` for labels and `normal` for text. Sizes are named by role:

  | Step | Classes | Used for |
  |---|---|---|
  | `title-page` | `text-2xl font-semibold text-ink-800` | the page's `h1` (`PageHeader`) |
  | `title-section` | `text-lg font-semibold text-ink-800` | `h2` of a card, a section or a modal |
  | `title-block` | `text-base font-semibold text-ink-800` | `h3`, a block inside a card |
  | `body` | `text-sm text-ink-700` | text, table cells, form controls |
  | `caption` | `text-xs text-ink-500` | hints, metadata, errors (`text-danger-600`) |
  | `label-caps` | `text-xs font-medium uppercase tracking-wide text-ink-500` | table headers, definition labels |

  Outside the scale: **display**, only the sign-in headline and title
  (`clamp(...)`, in `LoginPage`), and **micro**, `text-[11px]`, only for badges,
  a small caption and the sidebar's section titles. No new size without a role
  here; `tests/uiLegibility.test.mjs` pins where an arbitrary pixel size may
  appear. `ink-500` is the floor for text: `ink-300` / `ink-400` are for borders,
  icons and placeholders, and the same test fails on one used as a text colour.
  Every section heading (`h2`) is `text-lg`; `tests/uiLegibility.test.mjs` fails
  on a `text-xl` one. *What the code does today that this table does not say:*
  some `h3` are `text-sm`, KPI figures are `text-xl` / `text-3xl` / `text-5xl`
  (`ProjectsCounter`, `EconomicSummary`, `ExecutiveSummary`, `MetricBars`),
  the Inicio hero is `text-3xl`, and the value-chain editor has two
  `text-[10px]` (`EnerNode`, `EditorPage`).
- **Spacing is on the 4px grid, and sections are 24px apart.** Sections and
  cards of a page: `gap-6` or `space-y-6`, and the page header's own `mb-6`
  (`PageHeader`). Inside a card or a form: `gap-4` / `space-y-4`. A row of
  related items: `gap-2` or `gap-3`. **Never `space-y-5` or `space-y-10`**:
  there is none left, and `tests/uiLegibility.test.mjs` fails on a new one.
  Half steps (`py-2.5`, `gap-1.5`) are off the grid.
- **Shared components: use them, do not rebuild them.** `PageHeader` (a page's
  title), `tableClasses` (a table's cells), `EmptyState` and `LoadingState`
  (inside a card or a section), `ButtonLink` (a navigation that looks like a
  button), `Card` (a sheet), `Modal` + `ModalFooter` (a dialog), and, as before,
  `Button`, `TextField` / `TextArea` / `SelectField`, `SearchField` /
  `FilterSelect`, `Alert`, `Tabs`, `RowButton`. A `className` on a `Button` may
  lay it out (`mt-4`, `w-full`) but not recolour it: the variant already did.
- **Touch targets.** Two of them have a hit area bigger than what is drawn, with an
  `::after` and no change to the picture. The tabs are 44px tall (the 36px button
  extended 4px above and below). A `RowButton` is a 24×24 button whose area is
  **24×44**: 10px added above and below (`after:-inset-y-2.5`) and none sideways,
  because the buttons of a row sit 4px apart. It stays inside its own row (a table
  cell pads 12px around it, a project row is 56px, a members row is 56px because the
  role select is 40px tall), so it overlaps neither its neighbour nor the rows above
  and below. That meets WCAG 2.2 AA (2.5.8, target size minimum 24px) and **not** the
  44px guideline (2.5.5, AAA), which would need the row to grow on a phone (see
  `docs/considerations.md`). `tests/uiLegibility.test.mjs` holds the arithmetic.
- **Brand mark.** `<Logo variant="mark">` is the monogram for tight spots (the
  top bar); `<Logo variant="full">` adds the wordmark, for sign-in. Both are
  sized by height only (`size="sm" | "md" | "lg"`; `md` — 44px for the mark — is the green sidebar's logo cell), never by width. `tone` names the
  **artwork**, not the surface: the default `dark` is the charcoal original for
  light backgrounds, `tone="light"` is the white cut for `ink-900` ones, and
  `tone="sidebar"` is the white cut with its dark greens lifted to clear 3:1 on
  the green rail (where the pinned sidebar carries the logo). Never fake any
  of them with a CSS filter — inverting turns the brand green magenta.
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
  need `APP_URL` and `PLAYWRIGHT_MODULE`). **They are out of date**: they still mock the old version-level
  endpoints (`/versions/:v/economics/...`) and were neither run nor updated for the scenario flow (see
  `docs/considerations.md`, *Deuda de escenarios*); do not treat them as acceptance.

## Scenario economics

Open **Configuración del Escenario** or **Simulación de Escenarios** with an active project: pick a **version**
(the physical base), then one of its **scenarios**, configure a simplified economic evaluation and inspect saved
results (flow described under *Escenarios* above).
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

Inside a version (`/projects/:p/versions/:v/scenarios`), users who may edit that version select **Nuevo escenario**
and type a name (unique in the version ignoring case, up to 20 scenarios per version). It creates an empty
**scenario**, never a version. **Duplicar** asks a name and copies only the economic configuration. A scenario has
no map of its own: **Editar operación en la versión** opens the version's node editor. Project selection is shared
with the global header.


### Scenario configuration and simulation

Configuration starts at `/projects/:projectId/scenario-configurations` (versions) and goes on to `/projects/:projectId/versions/:versionId/scenarios` and `.../scenarios/:scenarioId/configuration`; simulation starts at `/projects/:projectId/simulations` and goes on to `/versions/:versionId/simulations`, with a rounds/seed dialog. The old `/versions/:versionId/economics/*` routes redirect to the version's default scenario. The map is unchanged. Browser acceptance: economic-browser.mjs, projectScoping-browser.mjs, results-browser.mjs and probabilistic-browser.mjs under tests; set APP_URL and PLAYWRIGHT_MODULE for a local Vite server and Playwright installation.

## Scenario comparison

**Análisis → Comparaciones** has two screens. Step 1, `/projects/:projectId/comparisons`:
pick 2–8 scenarios of **the project in the URL** (no project selector; search over scenario
tiles grouped by version). Each tile says whether its latest simulation is usable ("al día"), changed
since ("cambió"), was never run or is running, and can be simulated right there
(10 rounds) — the panel "Tu comparación" lists the choice, can simulate everything
pending at once and enables **Comparar escenarios** only
when every scenario is up to date. Earlier runs are listed below. Comparing saves
the run and opens step 2, `/projects/:projectId/comparisons/:comparisonId`.
Scenarios are compared with each other — there is no reference — : a conclusion
(the scenario with the highest NPV, big, and its lead over the second) with every
scenario ranked by value, the four key indicators as bars ordered best first with
each one's distance to the best, the most important differences in plain words, the yearly chart
with a hover crosshair, more indicators in smaller cards and, collapsed, the full
detail tables. Scenario colours are the `series-1…8` tokens in `src/index.css`.

Code: `src/pages/ComparisonsPage.tsx`, `src/pages/ComparisonResultPage.tsx`,
`src/components/comparison/*`, `src/api/comparisons.ts`, `src/types/comparison.ts`
(keep in sync with the backend DTOs) and the pure helpers in
`src/utils/comparison.ts` (covered by `tests/comparison.test.mjs`). Rules and API:
[scenario comparison](../docs/scenario-comparison.md).
