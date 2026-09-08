# EnerScope — Frontend

React + Vite + TypeScript client. Styling is **Tailwind CSS only** — the single
stylesheet is `src/index.css` (`@import "tailwindcss";` plus the brand design
tokens under `@theme`).

Ships the **authentication portal**: an `AuthProvider`/`useAuth` context
(login/register/logout/refresh + role state), reusable `LoginForm`/`RegisterForm`
components, role-gated routes, and a signed-in shell with a **role-aware
sidebar**. The single create-user form assigns the new user to an organization
via an **optional picker** (choose an existing org or create one inline); with no
org chosen it creates a platform account.

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
│  ├─ api/
│  │  ├─ client.ts         Axios instance + token/refresh interceptors
│  │  ├─ session.ts        Token + current-user storage helper (LocalStorage)
│  │  ├─ auth.ts           Auth endpoints (login/register/refresh/logout)
│  │  ├─ organizations.ts  Organization endpoints (list, create, register user into org)
│  │  └─ errors.ts         Extracts the ApiResponse message from a failed request
│  ├─ hooks/
│  │  ├─ useAuth.ts        AuthContext + useAuth() hook
│  │  ├─ AuthProvider.tsx  Auth state + login/register/logout/refresh actions
│  │  └─ useOrganizations.ts  Loads/creates organizations for the pickers/pages
│  ├─ components/
│  │  ├─ ui/               Brand-styled primitives (Button, TextField, Card, Alert, Logo, …)
│  │  ├─ auth/             LoginForm, RegisterForm (single create-user form, optional org)
│  │  ├─ organizations/    OrganizationPicker (select + inline create)
│  │  └─ layout/           AppLayout (sidebar + top bar), Sidebar (role-aware nav)
│  ├─ pages/               LoginPage, AdminUsersPage, AdminOrganizationsPage, WorkspacePage
│  ├─ routes/              ProtectedRoute, RoleRoute, DashboardRedirect
│  └─ types/
│     └─ auth.ts           Request/response types mirroring the backend DTOs
```

## Conventions

- **Styling:** Tailwind utility classes only. Do not add `.css` files or
  inline styles. Reuse the brand design tokens (`bg-brand-*`, `text-ink-*`,
  `bg-cream`) defined under `@theme` in `index.css`; extend the theme there if
  a new token is genuinely needed.
- **Auth:** read auth state and actions through `useAuth()`; never touch tokens
  or call the auth endpoints directly from components.
- **Naming:** all identifiers, files and folders in English. Components in
  `PascalCase`, everything else in `camelCase`.
- **API access:** always go through `src/api/*`. Never call `axios` directly in
  components — use `authApi` / the shared `client` so token handling and refresh
  stay centralised.
- **Session:** read and write tokens only through `session` (`src/api/session.ts`).
- **Types:** keep `src/types/auth.ts` in sync with the backend DTOs.
