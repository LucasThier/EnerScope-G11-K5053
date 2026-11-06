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

## Production: AWS Lightsail (backend) + Cloudflare Pages (frontend)

```
browser ──► Cloudflare Pages ──► static SPA (frontend/dist)
               │
               └─ /api/*  (Pages Function: frontend/functions/api/[[path]].js)
                     │
                     ▼
            AWS Lightsail container service ──► Spring Boot (port 8080)
                                                     │
                                                     ▼
                                              PostgreSQL (outside the container)
```

- **Backend.** `.github/workflows/deploy-backend.yml` runs on every push to
  `master` that touches `backend/**` (and on demand from the Actions tab). It
  runs the backend tests, builds `backend/Dockerfile`, pushes the image to the
  Lightsail container service and creates a new deployment, then waits until
  Lightsail reports it `ACTIVE`. Lightsail only switches traffic once the new
  container passes its health check (`GET /api/v1/health`), so a broken build
  leaves the previous version serving; the job then fails and prints the
  container logs.
- **Frontend.** Cloudflare Pages builds `frontend/` from the GitHub repository
  on every push (its own Git integration — no workflow here). The Pages Function
  forwards `/api/*` to Lightsail, so the client keeps its relative
  `/api/v1` base URL and the browser never makes a cross-origin call.

### One-time setup

1. **Database.** A Lightsail container has no persistent disk, so PostgreSQL
   must live outside it (a Lightsail managed database, RDS, Neon, Supabase…).
   Flyway creates the schema on the first boot.
2. **Container service.** Note its name and region. Prefer the *micro* power
   (1 GB) or above: Spring Boot is tight on *nano* (512 MB). There is no need to
   create a deployment by hand — the first workflow run does it.
3. **IAM user for GitHub.** Create an IAM user (e.g. `enerscope-github-deploy`)
   with this inline policy, then an access key for it:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "lightsail:GetContainerServices",
           "lightsail:GetContainerServiceDeployments",
           "lightsail:GetContainerImages",
           "lightsail:GetContainerLog",
           "lightsail:RegisterContainerImage",
           "lightsail:CreateContainerServiceRegistryLogin",
           "lightsail:CreateContainerServiceDeployment"
         ],
         "Resource": "*"
       }
     ]
   }
   ```

4. **GitHub** → repository *Settings → Secrets and variables → Actions*.

   | Kind | Name | Value |
   | --- | --- | --- |
   | Secret | `AWS_ACCESS_KEY_ID` | Access key of the IAM user above |
   | Secret | `AWS_SECRET_ACCESS_KEY` | Its secret key |
   | Secret | `BACKEND_ENV` | The production `.env`, one `KEY=VALUE` per line (below) |
   | Variable | `AWS_REGION` | Region of the container service, e.g. `us-east-1` |
   | Variable | `LIGHTSAIL_SERVICE_NAME` | Name of the container service |
   | Variable | `LIGHTSAIL_CONTAINER_NAME` | Optional, defaults to `api` |

   `BACKEND_ENV` example (blank lines and `#` comments are allowed; inline
   comments are not):

   ```dotenv
   DATASOURCE_URL=jdbc:postgresql://<db-host>:5432/enerscope?sslmode=require
   DATASOURCE_USERNAME=enerscope
   DATASOURCE_PASSWORD=<database password>
   JWT_SECRET=<at least 32 random characters, e.g. openssl rand -base64 48>
   ADMIN_PASSWORD=<strong admin password>
   CORS_ALLOWED_ORIGINS=https://<project>.pages.dev
   APP_FRONTEND_BASE_URL=https://<project>.pages.dev
   APP_BACKEND_BASE_URL=https://<service>.<id>.<region>.cs.amazonlightsail.com/api/v1
   LOG_LEVEL_APP=INFO
   ```

   The deploy refuses to start if `DATASOURCE_URL`, `DATASOURCE_USERNAME`,
   `DATASOURCE_PASSWORD`, `JWT_SECRET` or `ADMIN_PASSWORD` is missing, if
   `DATASOURCE_URL` is not a `jdbc:postgresql://` URL, if `JWT_SECRET` is
   shorter than 32 characters, or if either secret still has its development
   default.

5. **Cloudflare Pages** → *Create a project → Connect to Git* with this
   repository:

   | Setting | Value |
   | --- | --- |
   | Production branch | `master` |
   | Root directory | `frontend` |
   | Build command | `npm run build` |
   | Build output directory | `dist` — relative to the root directory, so **not** `frontend/dist` (Pages would look for `frontend/frontend/dist` and fail with "build output directory not found") |
   | Environment variable `BACKEND_URL` | The container service URL, e.g. `https://<service>.<id>.<region>.cs.amazonlightsail.com` (Production and Preview) |
   | Environment variable `NODE_VERSION` | `22` |

   With no top-level `404.html`, Pages serves `index.html` for unknown paths,
   so client-side routes work on reload.

### Day to day

- **Deploy:** merge to `master`. Backend changes trigger the workflow; the job
  summary links the health check and Swagger of the new version.
- **Change backend configuration:** edit the `BACKEND_ENV` secret and run
  *Actions → Deploy backend → Run workflow*. Every deployment replaces the
  container's whole environment with `BACKEND_ENV`, so variables edited by hand
  in the Lightsail console are lost on the next deploy.
- **Rollback:** revert the commit on `master`, or redeploy an earlier image from
  the Lightsail console (*Deployments → Modify*).

## Production notes

- Lightsail terminates TLS for the container service URL; Cloudflare does it for
  Pages.
- The browser only talks to Pages; `CORS_ALLOWED_ORIGINS` matters only for
  clients that call the Lightsail URL directly from another origin.
- Run at least one migration-capable instance; keep DB backups.
