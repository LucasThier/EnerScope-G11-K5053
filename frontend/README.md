# EnerScope — Frontend

Minimal React + Vite + TypeScript client. Styling is **Tailwind CSS only** — the
single stylesheet is `src/index.css` with `@import "tailwindcss";`.

This scaffold intentionally ships just enough to boot, plus the API and session
layer, so features can be built on a working foundation.

- React 19 · Vite 7 · TypeScript
- Tailwind CSS v4 (via `@tailwindcss/vite`)
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
│  ├─ App.tsx              Minimal root component
│  ├─ index.css            Tailwind import (the only stylesheet)
│  ├─ vite-env.d.ts        Vite client type references
│  ├─ api/
│  │  ├─ client.ts         Axios instance + token/refresh interceptors
│  │  ├─ session.ts        Token storage helper (LocalStorage)
│  │  └─ auth.ts           Auth endpoints (login/register/refresh/logout)
│  └─ types/
│     └─ auth.ts           Request/response types mirroring the backend DTOs
```

## Conventions

- **Styling:** Tailwind utility classes only. Do not add `.css` files or
  inline styles; extend the theme in `index.css` via Tailwind if needed.
- **Naming:** all identifiers, files and folders in English. Components in
  `PascalCase`, everything else in `camelCase`.
- **API access:** always go through `src/api/*`. Never call `axios` directly in
  components — use `authApi` / the shared `client` so token handling and refresh
  stay centralised.
- **Session:** read and write tokens only through `session` (`src/api/session.ts`).
- **Types:** keep `src/types/auth.ts` in sync with the backend DTOs.
