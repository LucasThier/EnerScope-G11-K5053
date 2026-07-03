# Technical decisions

Short rationale for the main choices made in this scaffold.

## Monorepo (backend + frontend)

Backend and frontend live in one repository under `backend/` and `frontend/`.
Keeps API contracts (`types/auth.ts` mirroring the DTOs) in sync and simplifies
onboarding and CI.

## Java 21 (LTS)

The build targets Java 21 even though a newer JDK may be installed locally.
Spring Boot 3.5.x fully supports 21, and the Docker images use `temurin-21`.
A newer JDK can still compile/run bytecode 21 without issues.

## Stateless JWT authentication

No server-side session table. Access tokens are short-lived and self-contained;
refresh tokens allow renewal. Benefits: horizontal scalability and no session
store. Trade-off: tokens can't be revoked before expiry — mitigated by short
access-token lifetimes.

Tokens are stored client-side in LocalStorage and sent via the
`Authorization: Bearer` header, so CSRF is not a concern and is disabled;
cross-origin access is restricted through an explicit CORS allow-list.

## Flyway owns the schema

Hibernate runs in `validate` mode; the schema is created and versioned by
Flyway migrations (`db/migration`). This makes schema changes explicit,
reviewable and reproducible across environments.

## Logging behind an interface

Application code depends on the `AppLogger` interface rather than a concrete
framework. The default `ConsoleAppLogger` logs to the console via SLF4J.
Swapping the logging strategy later (structured JSON, file, remote collector)
means providing a different bean — no call sites change.

`AppLogger` exposes `debug`/`info`/`warn`/`error`, and callers must pick the
level that matches the event. Verbosity is set from the environment
(`LOG_LEVEL_APP` for the application logger, `LOG_LEVEL_ROOT` for the framework),
so how much is logged is a deployment concern, not a code change.

## Uniform API envelope

Every response uses `ApiResponse<T>` (`success`, `message`, `data`,
`timestamp`), built through the `Responses` helper and enforced for errors by a
global exception handler. Predictable shape for the frontend.

## Tailwind-only styling

The frontend uses Tailwind CSS exclusively; the only stylesheet is a single
`@import "tailwindcss";`. No bespoke CSS/design-system files, by request.

## Password hashing

Passwords are hashed with BCrypt (strength 12) and never returned by the API.

## OpenAPI / Swagger

`springdoc-openapi` exposes interactive docs at `/api/v1/swagger-ui.html` with a
Bearer security scheme, so protected endpoints can be exercised from the UI.
