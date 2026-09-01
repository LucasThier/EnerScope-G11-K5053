# Domain model

The initial domain covers **users** and their **authentication**, plus
**organizations** (SCRUM-35), **projects** with their own membership, and a
minimal **project version** record.

## User

Persistent entity mapped to the `app_user` table.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `mail` | String | Unique, normalised to lower-case |
| `firstName` | String | 2–60 chars |
| `lastName` | String | 2–60 chars |
| `passwordHash` | String | BCrypt hash, never exposed by the API |
| `platformRole` | PlatformRole | `ADMIN` or `USER`; the user's app-wide role. Stored as a string (`platform_role`) |
| `active` | boolean | Soft-activation flag (from `BaseEntity`) |
| `createdAt` | Instant | Audit timestamp (from `BaseEntity`) |
| `lastModified` | Instant | Audit timestamp (from `BaseEntity`) |

`BaseEntity` is a `@MappedSuperclass` providing the id, the `active` flag and the
audit timestamps to every entity.

**Platform role vs. scoped roles.** `platformRole` is distinct from the
organization/project membership roles below: it governs app-wide capabilities
(only an `ADMIN` may create arbitrary accounts). `PlatformRole` is carried in the
access-token `role` claim and mapped to a Spring Security `ROLE_*` authority.

### Authentication & registration

Registration is **not** self-service. Accounts are created by:

- a platform **`ADMIN`** via `POST /auth/register` (may set the new account's
  `platformRole`, defaulting to `USER`); or
- an organization **owner** (an `OrganizationMember` holding
  `MANAGE_ORGANIZATION`) — or any platform `ADMIN` — via
  `POST /organizations/{id}/users` (one user) or
  `POST /organizations/{id}/users/bulk` (a CSV batch), which create `USER`
  accounts and add them to the organization (as `MEMBER` by default; the bulk CSV
  may set `OWNER`/`MEMBER` per row).

The seeded `admin@enerscope.org` starts with `platformRole = ADMIN` and is the
bootstrap administrator.

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

Persistent entity mapped to the `project` table.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `name` | String | 2–120 chars |
| `description` | String | Up to 500 chars |
| `organization` | Organization | `@ManyToOne`, required |
| `members` | List\<ProjectMember\> | `@OneToMany`, owned by `ProjectMember.project`, cascade `ALL` + orphan removal |
| `versions` | List\<Version\> | `@OneToMany`, owned by `Version.project`, cascade `ALL` + orphan removal |

Project export is a separate module/ticket, not modeled yet.

## ProjectMember

Persistent entity mapped to the `project_member` table. Join entity between
`Project` and `User` — used instead of a direct `@ManyToMany` because
membership carries its own roles/permissions.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `user` | User | `@ManyToOne`, required |
| `project` | Project | `@ManyToOne`, required |
| `roles` | Set\<ProjectMemberRole\> | `@OneToMany`, owned by `ProjectMemberRole.member`, cascade `ALL` + orphan removal |

A `(project_id, user_id)` unique constraint prevents adding the same user to
the same project twice.

## ProjectMemberRole

Persistent entity mapped to the `project_member_role` table. Belongs to
exactly one `ProjectMember` — roles are not a shared catalog, each membership
owns its own role instance(s).

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `name` | String | Defaults to the `memberType` name — no dedicated naming API yet |
| `memberType` | `ProjectMemberType` (enum) | `ADMIN` \| `EDITOR` |
| `permissions` | Set\<`ProjectMemberPermission`\> (enum) | `@ElementCollection` in `project_member_role_permission`; `MANAGE_PROJECT` \| `EDIT_PROJECT` \| `VIEW_PROJECT` |

`ProjectService` assigns permissions from a fixed `memberType` →
`permissions` mapping when a member is added (`ADMIN` gets all three
permissions, `EDITOR` gets `EDIT_PROJECT` + `VIEW_PROJECT`). There is no API
yet to define custom roles or permission sets.

## Version

Persistent entity mapped to the `version` table. **Minimal slice** — only
`name`, the owning `project` and an optional `parentVersion` self-reference.
No creation timestamp field of its own; it reuses `createdAt` from
`BaseEntity` instead of duplicating it.

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Primary key (from `BaseEntity`) |
| `name` | String | 2–120 chars |
| `project` | Project | `@ManyToOne`, required |
| `parentVersion` | Version | `@ManyToOne`, self-reference, optional (the first version of a project has none) |

`VersionService` rejects a `parentVersion` that belongs to a different
`project` than the one the new version is being created under.

A `Version` now **owns the diagram**: its `nodeSnapshot` (`List<BaseNode>` via
the `versionXNode` join table) and `connectionSnapshot` (`List<NodeConnection>`
via `versionXConnection`) are the nodes and edges the editor renders, and the
diff log (`nodeChanges`/`connectionChanges` via `NodeChange`/`ConnectionChange`)
records the per-version changes. `VersionService` exposes the full in-version
node/connection ABM (`addNodeToVersion`, `editNodeInVersion`,
`deleteNodeFromVersion`, and the connection equivalents), a read model
(`getDiagram` → `DiagramDTO`) and a presentation-only `updateNodePosition`. See
[`Node model`](#node-model) below.

> The earlier blockers noted here (non-persistable `NodeChange`/`ConnectionChange`
> and a Flyway `V2` collision) are **resolved**: those classes are entities and
> only `V2__create_nodes.sql` remains.

## Node model

The LNG value chain is modelled as a graph of typed nodes joined by connections,
both scoped to a `Version`.

### BaseNode

Abstract `@Entity` (JOINED inheritance) mapped to `base_node`, extended by every
concrete node type (`Well`, `GatheringNetwork`, `TreatmentPlant`, `Pipeline`,
`PipelineConnection`, `CompressingPlant`, `GroundBasedLiquefactionPlant`,
`FLNGUnit`, `SeaportTerminal`, `LNGCarrier`).

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Table primary key (from `BaseEntity`). **Connections reference nodes by this `id` within a version.** |
| `identityId` | UUID | Cross-version identity: the same real node keeps this id across versions (used by the diff/merge logic). |
| `name` | String | |
| `state` | NodeStateEnum | `RUNNING` \| `PROPOSED` \| `PENDING` \| `REMOVED` |
| `type` | NodeTypeData | `@OneToOne` — `vertical` / `role` / `nodeType` enums |
| `graphData` | NodeGraphData | `@OneToOne` — see below |
| `investmentCost` | InvestmentCost | `@OneToOne` |
| plus per-type fields | | capacities, costs, etc. |

### NodeGraphData

Presentation data of a node, holding **two independent, nullable positions**:

| Field | Type | Notes |
| --- | --- | --- |
| `graphPosition` | GraphPosition (`@Embeddable`) | `x` / `y` — abstract position on the diagram canvas |
| `geographicalPosition` | GeographicalPosition (`@Embeddable`) | `longitude` / `latitude` — real-world position for the map/globe view (MapLibre `[lng, lat]` order) |

The two are independent: a node can have a diagram position without a
geographical one, and vice versa.

### NodeConnection

Edge between two nodes (`node_connection` table): `identityId`, `fromNodeId`,
`toNodeId` (both referencing endpoint node `id`s within the version).

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
