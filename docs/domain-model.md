# Domain model

The initial domain covers **users** and their **authentication**, plus a first
slice of **organizations** and **projects** (SCRUM-35).

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

## Organization

Persistent entity mapped to the `organization` table.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `name` | String | 2–120 chars |
| `members` | List\<OrganizationMember\> | `@OneToMany`, owned by `OrganizationMember.organization`, cascade `ALL` + orphan removal |
| `projects` | List\<Project\> | `@OneToMany`, owned by `Project.organization`, cascade `ALL` + orphan removal |

## OrganizationMember

Persistent entity mapped to the `organization_member` table. Join entity
between `Organization` and `User` — used instead of a direct `@ManyToMany`
because membership carries its own roles/permissions.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `user` | User | `@ManyToOne`, required |
| `organization` | Organization | `@ManyToOne`, required |
| `roles` | Set\<OrganizationMemberRole\> | `@OneToMany`, owned by `OrganizationMemberRole.member`, cascade `ALL` + orphan removal |

A `(organization_id, user_id)` unique constraint prevents adding the same user
to the same organization twice.

## OrganizationMemberRole

Persistent entity mapped to the `organization_member_role` table. Belongs to
exactly one `OrganizationMember` — roles are not a shared catalog, each
membership owns its own role instance(s).

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `name` | String | Defaults to the `memberType` name — no dedicated naming API yet |
| `memberType` | `OrganizationMemberType` (enum) | `OWNER` \| `MEMBER` |
| `permissions` | Set\<`OrganizationMemberPermission`\> (enum) | `@ElementCollection` in `organization_member_role_permission`; `MANAGE_ORGANIZATION` \| `VIEW_ORGANIZATION` |

`OrganizationService` assigns permissions from a fixed `memberType` →
`permissions` mapping when a member is added (`OWNER` gets both permissions,
`MEMBER` gets `VIEW_ORGANIZATION` only). There is no API yet to define custom
roles or permission sets.

## Project

Persistent entity mapped to the `project` table. **Minimal slice for
SCRUM-35** — only what "add a project to an organization" needs; members and
versions are a separate module/ticket.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `name` | String | 2–120 chars |
| `description` | String | Up to 500 chars |
| `organization` | Organization | `@ManyToOne`, required |

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
