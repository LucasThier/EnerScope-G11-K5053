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
project switcher, user menu) above a collapsible sidebar. User-facing copy in
the shell is in **Spanish**; identifiers, file names and route paths stay in
English.

And the **Projects screen** (`/projects`): the table of the projects you belong
to, with a client-side search and organization filter, and a "Nuevo proyecto"
modal.

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
npm run build    # type-check + production build into dist/
npm run preview  # preview the production build
npm run lint     # eslint
```

The dev server proxies `/api` to the backend at `http://localhost:8080`, so run
the backend alongside it (see [backend/README.md](../backend/README.md)).

## Environment variables

None are required for local development — the Vite proxy handles API routing.
See `.env.example`. Vite only exposes variables prefixed with `VITE_`.

## Folder structure

```
frontend/
├─ index.html
├─ vite.config.ts          Vite + React + Tailwind, /api dev proxy
├─ src/
│  ├─ main.tsx             React entry point
│  ├─ App.tsx              Router + route guards (AuthProvider at the root)
│  ├─ index.css            Tailwind import + brand design tokens (@theme)
│  ├─ vite-env.d.ts        Vite client type references
│  ├─ assets/
│  │  ├─ logo-mark.png     Brand artwork: the "ES" monogram alone (transparent)
│  │  ├─ logo-full.png     Brand artwork: monogram + wordmark (transparent)
│  │  ├─ logo-mark-inverse.png  The same monogram, ink cut to white (dark surfaces)
│  │  └─ logo-full-inverse.png  The same lockup, ink cut to white (dark surfaces)
│  ├─ api/
│  │  ├─ client.ts         Axios instance + token/refresh interceptors
│  │  ├─ session.ts        Token + current-user storage helper (LocalStorage)
│  │  ├─ auth.ts           Auth endpoints (login/register/refresh/logout)
│  │  ├─ organizations.ts  Organization endpoints (list, create, register user into org)
│  │  ├─ projects.ts       Project endpoints (list with optional organization filter, create, members)
│  │  └─ errors.ts         Extracts the ApiResponse message from a failed request
│  ├─ hooks/
│  │  ├─ useAuth.ts        AuthContext + useAuth() hook
│  │  ├─ AuthProvider.tsx  Auth state + login/register/logout/refresh actions
│  │  ├─ useActiveProject.ts      ActiveProjectContext + useActiveProject() hook
│  │  ├─ ActiveProjectProvider.tsx  Loads projects, resolves the active one
│  │  ├─ useLocalPreference.ts    Guarded LocalStorage-backed UI state
│  │  ├─ useDismissable.ts        Popover open state (outside click / Escape)
│  │  └─ useOrganizations.ts      Loads/creates organizations for the pickers/pages
│  ├─ components/
│  │  ├─ ui/               Brand-styled primitives (Button, TextField, TextArea, Card, Modal, Alert, Logo, Avatar, icons, NodeGraph, …)
│  │  │                     `Card` takes `padded`, `Modal` takes `size` — both default to the form-shaped variant
│  │  ├─ auth/             LoginForm, RegisterForm (single create-user form, optional org)
│  │  ├─ organizations/    OrganizationPicker (select + inline create)
│  │  ├─ projects/         ProjectsTable, NewProjectModal, ProjectMembersModal
│  │  └─ layout/           AppLayout (shell), TopBar, Sidebar, ProjectSwitcher, UserMenu
│  ├─ pages/               LoginPage, AdminUsersPage, AdminOrganizationsPage, WorkspacePage, ProjectsPage
│  ├─ routes/              ProtectedRoute, RoleRoute, DashboardRedirect
│  ├─ utils/               date.ts (dd/mm/aaaa formatting)
│  └─ types/
│     ├─ auth.ts           Auth/organization types mirroring the backend DTOs
│     └─ project.ts        Project types mirroring the backend DTOs
```

## The application shell

`AppLayout` renders a full-width `TopBar` above a `Sidebar` and the routed page.

- **Active project.** `ActiveProjectProvider` loads `GET /projects` and picks the
  active one in this order: a `projectId` in the URL (`/projects/:projectId/*`),
  then the last selection from LocalStorage, then the first project. Project-
  scoped routes do not exist yet, so the stored preference decides for now.
  Read it with `useActiveProject()`; never fetch projects from a component.
- **Sidebar.** Lists every section of the product. Entries without a `to` render
  locked on purpose — the section exists in the design but has no page yet.
  A locked entry keeps full-contrast text and shows a padlock; it is set in a
  lighter weight than a navigable one. Dimming the label to signal the state is
  what made those entries unreadable, so the state lives in the icon and the
  weight instead. Platform admins also get an "Administración" group. The
  collapsed state is remembered per browser.

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
- **Tests:** there is no test harness yet, so the bar is `npm run build`
  (type-check) plus `npm run lint`. Both must pass.
