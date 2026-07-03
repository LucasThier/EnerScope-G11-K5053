# EnerScope — Backend

Spring Boot REST API for users, authentication and session management.

- Java 21 · Spring Boot 3.5.5
- PostgreSQL + Flyway migrations
- Stateless JWT authentication (access + refresh tokens)
- OpenAPI / Swagger UI
- BCrypt password hashing

## Running the backend

### Option A — Docker Compose (recommended)

Starts PostgreSQL and the API together:

```bash
cd backend
docker compose up --build
```

### Option B — Locally with Maven

Requires a running PostgreSQL and a JDK 21+.

```bash
cd backend
# create the database expected by the default config
# (db: enerscope, user: enerscope, password: enerscope)
mvn spring-boot:run
```

Run the tests:

```bash
mvn test
```

Build a jar:

```bash
mvn clean package
java -jar target/enerscope-backend-1.0-SNAPSHOT.jar
```

The API is served under the context path **`/api/v1`** (port `8080`).

## Environment variables

Copy `.env.example` to `.env` and adjust. All variables have sensible dev
defaults, so the app boots without any of them set.

| Variable | Default | Description |
| --- | --- | --- |
| `DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/enerscope` | JDBC URL |
| `DATASOURCE_USERNAME` | `enerscope` | DB user |
| `DATASOURCE_PASSWORD` | `enerscope` | DB password |
| `LOG_LEVEL_ROOT` | `INFO` | Root log level (framework included) |
| `LOG_LEVEL_APP` | `INFO` | App log level (the `AppLogger` output) |
| `JWT_SECRET` | dev placeholder | HMAC signing key (≥ 256 bits). **Change in prod.** |
| `JWT_ACCESS_EXPIRY_MINUTES` | `60` | Access token lifetime |
| `JWT_REFRESH_EXPIRY_DAYS` | `30` | Refresh token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:4173` | Allowed CORS origins |
| `ADMIN_MAIL` | `admin@enerscope.org` | Seeded admin email |
| `ADMIN_PASSWORD` | `admin12345` | Seeded admin password |
| `ADMIN_FIRST_NAME` | `EnerScope` | Seeded admin first name |
| `ADMIN_LAST_NAME` | `Admin` | Seeded admin last name |

## Swagger / OpenAPI

Once running:

- **Swagger UI:** http://localhost:8080/api/v1/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/api/v1/v3/api-docs

The `/auth/**`, `/health` and Swagger endpoints are public; every other endpoint
requires a `Bearer` access token. Use the **Authorize** button in Swagger UI to
paste a token obtained from `/auth/login`.

## API overview

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | public | Create an account, returns a session |
| `POST` | `/auth/login` | public | Authenticate, returns a session |
| `POST` | `/auth/refresh` | public | Exchange a refresh token for a new session |
| `POST` | `/auth/logout` | public | No-op server side (client clears tokens) |
| `GET` | `/health` | public | Liveness probe |

All responses are wrapped in a standard envelope:

```json
{ "success": true, "message": "...", "data": { }, "timestamp": "..." }
```

## Backend structure

```
backend/src/main/java/org/enerscope/
├─ Main.java                 Application entry point
├─ auth/                     AuthController, AuthFilter (JWT bearer filter)
├─ common/                   BaseEntity, GlobalExceptionHandler, exceptions
├─ config/                   Security, CORS, Crypto, JPA, OpenAPI config
├─ dto/session/              Request/response records
├─ health/                   HealthController
├─ jwt/                      JwtService (token issue + validation)
├─ logging/                  AppLogger interface + ConsoleAppLogger
├─ money/                    MoneyAmount value type
├─ seed/                     AdminSeeder (default admin on startup)
├─ session/                  Session, SessionService
├─ user/                     User entity, repository, service
└─ util/                     ApiResponse, Responses, AuthUtil

backend/src/main/resources/
├─ application.properties
└─ db/migration/             Flyway migrations (V1__init.sql)
```

### Logging

All application logging goes through the `AppLogger` interface
(`org.enerscope.logging`). The default implementation, `ConsoleAppLogger`,
writes to the console. To change how the app logs (JSON, file, remote
collector), provide a different `AppLogger` bean — no call site changes.

`AppLogger` exposes the standard levels — `debug`, `info`, `warn`, `error` —
and code must use the one that fits the event (routine flow → `info`,
diagnostics → `debug`, recoverable problems → `warn`, failures → `error`).
Verbosity is controlled from the environment: `LOG_LEVEL_APP` sets the
application logger level and `LOG_LEVEL_ROOT` the framework level
(`TRACE < DEBUG < INFO < WARN < ERROR < OFF`). For example, set
`LOG_LEVEL_APP=DEBUG` in `.env` to see debug output locally.
