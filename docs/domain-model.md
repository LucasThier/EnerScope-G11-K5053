# Domain model

The domain covers **users** and their **authentication**, **organizations**,
**projects** with scoped membership, **physical versions**, **simulation** and
**economic evaluations**.

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
| `jobTitle` | String | Optional, ≤120 chars (`job_title`). Free-form descriptive title (e.g. "Senior Investment Analyst") shown next to the user in the app |
| `active` | boolean | Soft-activation flag (from `BaseEntity`) |
| `createdAt` | Instant | Audit timestamp (from `BaseEntity`) |
| `lastModified` | Instant | Audit timestamp (from `BaseEntity`) |

`BaseEntity` is a `@MappedSuperclass` providing the id, the `active` flag and the
audit timestamps to every entity.

**Platform role vs. scoped roles.** `platformRole` is distinct from the
organization/project membership roles below: it governs app-wide capabilities
(only an `ADMIN` may create arbitrary accounts). `PlatformRole` is carried in the
access-token `role` claim and mapped to a Spring Security `ROLE_*` authority.

**`jobTitle` is not a role.** It is descriptive text with no bearing on
authorization, and it is not carried in the token claims: it reaches the client
through `UserSummaryDTO` on login and refresh, both of which read the user from
the database. Set it at registration; there is no endpoint to change it yet.

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

## Economic And Configuration

Persistent entity mapped to the `version` table. **Minimal slice** — only
`name`, the owning `project` and an optional `parentVersion` self-reference.
No creation timestamp field of its own; it reuses `createdAt` from
`BaseEntity` instead of duplicating it.

`EconomicConfigurationEntity` has a unique, required Version reference, a typed
configuration serialized as JSON text and an optimistic-lock revision.
`EconomicEvaluation` has a required Version reference and immutable input/output
snapshot JSON. Both inherit `BaseEntity`. The configuration records below are
owned values, not separate shared JPA entities:

- `EconomicConfiguration`: investment year, operating horizon, currency, WACC,
  selected boundary and all rule collections.
- `TaxEntity`: hypothetical jurisdiction label, rate, loss carry-forward policy,
  opening losses, expiry, compensation limit and payment lag.
- `NodeEconomicProfile` / `Ownership`: version-specific node rules and shares.
- `EconomicRule` / `Occurrence`: concept, income/expense direction, cash/non-cash,
  driver, tariff, units, dates and optional internal counterparty.
- `CommercialContract`: delivery node and revenue rule, separate from physical assets.
- `CapitalAsset`: purchase/payment, cost, residual, service month and useful life;
  generates capitalizable cash outflow and monthly depreciation/amortization.
- `TaxTreatment`: classification/deductible fraction for concept, taxpayer and validity.
- `MetricConversion`: explicit simulator-unit conversion or static driver quantity.

`AnnualNodeMetrics` records raw annual operational counters. `OperationalMetric`
contains the converted node/year/driver quantity consumed by `EconomicEngine`.
`EconomicEntry` preserves originating rule, node, contract, entity, counterparty,
classification, dates and allocated amount. `EconomicResult` contains entries,
metrics, `PeriodEconomicResult`, `EntityTaxResult`, `PendingBalance` and NPV.
`EvaluationDTO.Snapshot` also stores physical inputs and a snapshot schema version.

## Node/connection identity and clone-on-write

A version's `nodeSnapshot`/`connectionSnapshot` (`Version.java`) start as a
*shallow* copy of the parent's list when the version is created — same row
objects, same `id`s. Editing a node/connection a version inherited this way
must never mutate that row in place: it's still referenced by the parent's
(and any sibling's) own snapshot, so an in-place edit would silently change
what they see too.

`VersionService.editNodeInVersion`/`editConnectionInVersion` guard against
this: they check whether the version already has its *own* row for the
entity's `identityId` (from an earlier `addNodeToVersion`/edit in that same
version — checked by `identityId`, not by comparing objects or the raw id
the caller passed, since a `NodeChange`'s `changedNode`/`resultNode` may be a
different Java instance than what was just fetched). If it does, editing in
place is safe. If it doesn't (the row is still the parent's shared one),
`NodeCloner` (`org.enerscope.node.service`) clones it first — same
`identityId`, new `id` — and the edit is applied to the clone, which replaces
the original in this version's snapshot. The resulting `NodeChange`/
`ConnectionChange` records `changedNode`/`changedConnection` as the
*untouched* original and `resultNode`/`resultConnection` as the clone.
`NodeCloner` is a stateless static utility (not a Spring bean) specifically
so both `VersionService` and `VersionConflictService` can reuse it without
adding a mockable dependency.

`identityId` must stay stable for all of this to work — nothing in the merge
or conflict machinery re-derives it. `NodeService`'s `editWell`/`editTreatmentPlant`/
etc. (and `editPipelineConnection`) never write to `identityId` from the
incoming DTO, even though `BaseNodeDTO`/`PipelineConnectionDTO` carry an
`identity` field: that field exists for the ADD path (`saveWell` etc., where
a caller can supply an explicit identity, e.g. to set up a conflict for
testing) and must be ignored on edit, or a client sending back whatever
`identity` value its edit form happened to hold (a stale one, a copy-pasted
placeholder, anything other than the node's actual current one) would
silently reassign the node to a different conceptual identity mid-edit.

## Merging is differential, not a wholesale snapshot replace

`VersionService.mergeSubVersionIntoParent` used to replace the parent's
entire `nodeSnapshot`/`connectionSnapshot` with the merged subversion's. That
silently discarded anything the parent already had that the subversion never
touched — including, critically, whatever an *earlier, already-merged*
sibling had added: merging a second, untouched sibling would revert the
first sibling's contribution with no warning.

It now applies each of the subversion's own `nodeChanges`/`connectionChanges`
onto the parent's *current* snapshot as an upsert-or-remove keyed by
`identityId` (`VersionService.applyNodeChangesToSnapshot`/
`applyConnectionChangesToSnapshot`): a change with a `resultNode`/
`resultConnection` (ADD or EDIT) replaces whatever the parent currently holds
for that identity; a change with none (DELETE) removes it. Identities the
subversion never touched are left exactly as they were.

The change lists (the audit trail) are still combined, via
`mergeNodeChanges`/`mergeConnectionChanges`, but also keyed by `identityId`
now rather than raw row id — grouping by raw id never matched anything
across separate merges, since clone-on-write gives every edit a fresh id,
so a node changed across three merges used to accumulate three stale
entries (e.g. `ADD`, `EDIT`, `ADD`) instead of collapsing to one.
`mergeNodeChangesForNode`/`mergeConnectionChangesForConnection` fold the
*entire* chronological chain for one identity (the parent's already-stored
history, then this merge's new changes) down to a single net change: the
state right before that whole history began versus what it nets out to now
— ADD+DELETE across any number of intervening edits still cancels out to
nothing, EDIT+EDIT still collapses to one EDIT, and it stays correct no
matter how many merges have touched the identity by now.

`NodeChange.changedNode`/`resultNode` (and the `ConnectionChange`
equivalents) are `@ManyToOne`, not `@OneToOne`: several change rows can
legitimately reference the same node/connection (its `resultNode` from an
ADD can later be some other change's `changedNode`), and `@OneToOne` makes
Hibernate infer a DB-level uniqueness the real schema was never given.

## VersionConflict

Persistent entity (`version_conflict`). Created **automatically** by
`VersionService.mergeSubVersionIntoParent` (through
`VersionConflictService.recordMergeConflicts`) — there is no detection
endpoint. `Version` itself keeps no merge metadata; everything lives here.

Entities are matched across versions by `identityId` (on `BaseNode`/
`NodeConnection`), not by primary key `id` — two versions can hold
independent rows for the same conceptual node/connection, but `identityId`
stays stable across edits. For every identity the merged subversion touched,
and every other child of the merge target, `recordMergeConflicts` compares
that sibling's *current* node/connection for the identity against what the
merge just left in the parent — **regardless of whether the sibling has any
`NodeChange`/`ConnectionChange` of its own for it**. A sibling's snapshot is
a static copy taken when it branched off; it can simply be stale relative to
what its parent now holds, with no explicit edit on the sibling's part (e.g.
the merge added a brand-new node the sibling was never going to see
otherwise). Any mismatch — different row, or one side missing it entirely —
is a conflict. One record per conflicting entity (not a bundle — a sibling
with three conflicting nodes gets three rows, so each can be resolved
independently):

| Field | Type | Notes |
| --- | --- | --- |
| `mergedVersion` | Version | Parent that received the merge |
| `sourceVersion` | Version | Subversion that was merged |
| `conflictingVersion` | Version | Sibling that changed the same entity |
| `entityType` | NODE / CONNECTION | |
| `identityId` | UUID | The shared `identityId` of the conflicting node/connection |
| `resolved` / `resolvedAt` | boolean / Instant | Set by resolving |
| `createdAt` | Instant | From `BaseEntity`; the moment of the merge |

The entity itself only stores `identityId` — everything else about the
conflict is resolved on demand in `VersionConflictDTO.from`, since snapshots
can keep changing between when the conflict was recorded and when it's
viewed. The DTO adds, so a client never has to make a second call just to
explain a conflict to a user:

| Field | From | Notes |
| --- | --- | --- |
| `mergedDescription` | current snapshot | e.g. `WELL "nodito5"`, or the connection's endpoint names; null if the parent has nothing for this identity |
| `conflictingDescription` | current snapshot | same, for the sibling; null if it has nothing |
| `sourceChangeType` | the merged subversion's own `nodeChanges`/`connectionChanges` | ADD / EDIT / DELETE — what the merged branch itself did |
| `conflictingChangeType` | the sibling's own `nodeChanges`/`connectionChanges` | ADD / EDIT / DELETE, or **null** if the sibling never explicitly touched this identity (the "just stale" case) |

Conflicts of a version are listed with `GET /version/{versionId}/conflicts`
and resolved with
`PATCH /version/conflicts/{conflictId}/resolve/{decision}` (`ACCEPT` or
`REJECT`), which does real reconciliation, not just a flag flip
(`VersionConflictService.resolveConflict`):

- **ACCEPT** — the sibling's node/connection for that identity becomes
  identical to the merged parent's. If the sibling already has one, its data
  is overwritten *in place* (its own `id` is kept, so any connection already
  referencing it stays valid); if only the parent has it, a clone (new `id`,
  same `identityId`) is added to the sibling's snapshot; if only the sibling
  has it, it's removed from the sibling's snapshot (the row itself isn't
  hard-deleted, in case another version still references it). Any
  `NodeChange`/`ConnectionChange` the sibling had for that identity is
  deleted — the sibling now matches the parent, so there's no diff to record.
- **REJECT** — the sibling keeps its own version of the entity, but since the
  parent's content changed at merge time, the divergence is re-recorded as a
  single compensating change: `EDIT` if both still have it, `DELETE` if only
  the parent has it (e.g. the merge added a node the sibling doesn't want),
  `ADD` if only the sibling has it.

Connection endpoints (`fromNodeId`/`toNodeId`) are remapped through node
identity on accept, since the parent's and the sibling's copies of an
endpoint node can have different raw ids; if the endpoint's own conflict
hasn't been resolved yet, the sibling has no matching node and the parent's
raw id is used as a fallback — resolve node conflicts before their
connections' for fully correct rewiring.

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

## EconomicDraftEntity

Version-owned editor draft, unique version_id, schema-versioned draft_json text and optimistic-lock revision. Raw strings preserve partial amounts and dates. V13 adds the table independently of economic_configuration and economic_evaluation.
