# Deployment

## Overview

- **Backend:** a self-contained Spring Boot jar, packaged as a Docker image.
- **Database:** PostgreSQL. Flyway runs migrations automatically on startup.
- **Frontend:** a static bundle (`npm run build` → `dist/`) served by any static
  host or CDN, or behind the same reverse proxy as the API.

## Configuration

All configuration is via environment variables (see
[backend/README.md](../backend/README.md) and `backend/.env.example`).
Before deploying to a real environment, **always** set:

- `JWT_SECRET` — a strong, unique secret (≥ 256 bits).
- `DATASOURCE_URL`, `DATASOURCE_USERNAME`, `DATASOURCE_PASSWORD` — the managed DB.
- `ADMIN_PASSWORD` — a non-default admin password (or rotate after first login).
- `CORS_ALLOWED_ORIGINS` — the production frontend origin(s).

## Local / staging with Docker Compose

```bash
cd backend
cp .env.example .env   # edit values
docker compose up --build -d
```

This starts PostgreSQL and the API. The API waits for the database health check
before booting.

## Building images manually

```bash
# Backend image
cd backend
docker build -t enerscope-backend:latest .

# Frontend static bundle
cd frontend
npm ci
npm run build   # output in dist/
```

## Database migrations

Migrations live in `backend/src/main/resources/db/migration` and are applied by
Flyway on application startup. To add a change, create a new
`V<n>__<description>.sql` file — never edit an already-applied migration.

## Production notes

- Terminate TLS at a reverse proxy / load balancer in front of the API.
- Serve the frontend `dist/` from the same origin (or configure CORS accordingly).
- Point the frontend at the API (the dev Vite proxy is for local development only).
- Run at least one migration-capable instance; keep DB backups.
