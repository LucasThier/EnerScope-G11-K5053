# Domain model

The initial domain covers only **users** and their **authentication**.

## User

Persistent entity mapped to the `app_user` table.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `mail` | String | Unique, normalised to lower-case |
| `firstName` | String | 2–60 chars |
| `lastName` | String | 2–60 chars |
| `passwordHash` | String | BCrypt hash, never exposed by the API |
| `active` | boolean | Soft-activation flag (from `BaseEntity`) |
| `createdAt` | Instant | Audit timestamp (from `BaseEntity`) |
| `lastModified` | Instant | Audit timestamp (from `BaseEntity`) |

`BaseEntity` is a `@MappedSuperclass` providing the id, the `active` flag and the
audit timestamps to every entity.

## Session (non-persistent)

A `Session` is an in-memory object rebuilt from a JWT on every request. It is
**not** stored in the database (authentication is stateless). It holds:

- the raw access `token`,
- the `User` (reconstructed from token claims),
- the `expiresAt` instant.

## Tokens

| Token | Claim `typ` | Default lifetime | Purpose |
| --- | --- | --- | --- |
| Access | `access` | 60 minutes | Sent on every request to authenticate |
| Refresh | `refresh` | 30 days | Exchanged for a new access token |

Access tokens carry `sub` (user id), `mail`, `firstName` and `lastName`.

## MoneyAmount (value object)

`MoneyAmount` is an `@Embeddable` value type for monetary values: fixed scale of
2, `HALF_UP` rounding, and safe arithmetic (`add`, `subtract`, `multiply`,
`divide`). It is included as reusable infrastructure for future domain features.

## Seed data

On startup `AdminSeeder` ensures a default administrator exists
(`admin@enerscope.org` by default). It is idempotent: it only creates the user
if the email is not already present.
