# Role management — spec + implementation plan

- **Date:** 2026-10-14
- **Status:** approved for implementation
- **Scope:** backend only (`backend/`)

## 1. Problem

Roles exist today but only as three disconnected, per-scope pairs
(`platformRole` on `User`; `OrganizationMember`+`OrganizationMemberRole`;
`ProjectMember`+`ProjectMemberRole`). There is no API to assign/remove/list a
user's roles across levels, no rule stopping a lower-level admin from
assigning a higher-level role, and several endpoints perform **no**
authorization check at all today (confirmed by reading the code, not
assumed):

- `OrganizationController.createOrganization` — any authenticated user can
  create an organization.
- `OrganizationService.addMember` — no `assertCanManageUsers` call (unlike
  `registerUserInOrganization`, which already has it).
- `ProjectService.createProject` — any authenticated user can create a
  project inside *any* organization (flagged as a known gap in
  `docs/considerations.md`, 2026-09-09 entry).
- `ProjectService.addMember` — no permission check at all.
- `ProjectService.listVersions` / `saveVersion` — no permission check at all.
- `VersionController` (every node/connection/diagram endpoint) — no
  permission check at all; any authenticated user can read or mutate any
  version by guessing/knowing its UUID, regardless of project membership.

## 2. Decisions (locked in during brainstorming)

1. **Version has no membership of its own.** Access to a version (and its
   nodes/connections) is always derived from the caller's role in the
   **owning project**. No `VersionMember`/`VersionMemberRole` entity, no new
   migration for this.
2. **Hierarchy = level implies full scope.** Rank:
   `PLATFORM(3) > ORGANIZATION(2) > PROJECT(1)`. A platform `ADMIN` has
   authority everywhere. An org `OWNER` has authority over their
   organization **and every project inside it**, without needing an explicit
   `ProjectMember` row. A project `ADMIN` has authority only over that one
   project. A caller can only assign/remove a role at a level **less than or
   equal to** their own authority rank at that resource, and only via a role
   type that itself is a "manage" type at or below their own.
3. **Fix every access-control gap listed in §1** as part of this work, not
   just the new role-management surface.

## 3. Role catalog (no new enums)

The five roles named in the spec already exist; this feature does not add
new role types, only a uniform way to read/assign/remove them.

| Spec role | Level | Existing enum value |
|---|---|---|
| Admin de plataforma | PLATFORM | `PlatformRole.ADMIN` |
| Admin de organización | ORGANIZATION | `OrganizationMemberType.OWNER` |
| Admin de proyecto | PROJECT | `ProjectMemberType.ADMIN` |
| Editor | PROJECT | `ProjectMemberType.EDITOR` |
| Miembro | ORGANIZATION | `OrganizationMemberType.MEMBER` |

`PlatformRole.USER` is the implicit "no special platform role" baseline — it
is never "assigned" as a privilege, only restored when an `ADMIN` role is
removed.

## 4. New package: `role/`

Per `AGENTS.md`'s "feature, then layer" convention, this is a new top-level
feature package — it is cross-cutting (acts on `user`, `organization`,
`project`), so it does not belong inside any of those three.

```
role/
  controller/UserRoleController.java
  service/RoleService.java
  service/RoleAuthorizationService.java
  dto/RoleLevel.java            (enum: PLATFORM, ORGANIZATION, PROJECT)
  dto/UserRoleDTO.java          (response: level, resourceId, resourceName, roleName, permissions)
  dto/AssignRoleRequestDTO.java (level, resourceId nullable, roleType)
  dto/RemoveRoleRequestDTO.java (level, resourceId nullable, roleType)
```

### 4.1 Endpoints (`UserRoleController`, `@RequestMapping("/users/{userId}/roles")`)

| Endpoint | Behavior | Who can call it |
|---|---|---|
| `GET /users/{userId}/roles` | Aggregates `platformRole` + every `OrganizationMemberRole` + every `ProjectMemberRole` the user holds, as `List<UserRoleDTO>`. | The user themself, or a platform `ADMIN`. Anyone else → `403`. |
| `POST /users/{userId}/roles` | Assigns a role. For `ORGANIZATION`/`PROJECT`, creates the `OrganizationMember`/`ProjectMember` row first if the target user is not already a member (this is what lets an outside user get project access without belonging to the organization). For `PLATFORM`, calls `User.updatePlatformRole`. | Caller must pass `RoleAuthorizationService.assertCanAssign(caller, level, resourceId, roleType)` — see §5. |
| `DELETE /users/{userId}/roles` (body = same shape as assign) | Removes a role instance. If the membership ends up with zero roles, the membership row itself is deleted. For `PLATFORM`, resets to `PlatformRole.USER`. | Same `assertCanAssign` check (removing needs the same authority as granting). |

### 4.2 `RoleService` responsibilities

- `listRoles(UUID userId): List<UserRoleDTO>` — read-only aggregation across
  the three existing repositories (`UserRepository`,
  `OrganizationMemberRepository`, `ProjectMemberRepository`). No new
  repository methods beyond what already exists for the org/project side
  (`findByOrganizationIdAndUserId`-style queries already exist; add the
  user-centric equivalents, e.g. `OrganizationMemberRepository.findByUserId`,
  `ProjectMemberRepository.findByUserId`, if they don't already exist).
- `assignRole(UUID callerUserId, UUID targetUserId, AssignRoleRequestDTO)`.
- `removeRole(UUID callerUserId, UUID targetUserId, RemoveRoleRequestDTO)`.

Both mutating methods are `@Transactional`.

## 5. `RoleAuthorizationService` — the hierarchy check

```java
void assertCanAssign(Session caller, RoleLevel level, UUID resourceId, String roleType)
```

Logic:

1. Platform `ADMIN` → always allowed, return.
2. Resolve the caller's **authority rank** at the target resource:
   - `level == PLATFORM` → only a platform `ADMIN` ever has authority here;
     a non-admin caller is rejected regardless of anything else.
   - `level == ORGANIZATION` → caller has authority rank 2 if they hold an
     `OrganizationMemberRole` with `MANAGE_ORGANIZATION` on `resourceId`.
   - `level == PROJECT` → caller has authority rank 1 if they hold a
     `ProjectMemberRole` with `MANAGE_PROJECT` on `resourceId`; **or** rank 2
     (inherited) if they hold `MANAGE_ORGANIZATION` on the organization that
     owns that project (resolved via `ProjectRepository`).
3. The role being assigned has its own rank, equal to its level's rank
   (`PLATFORM`=3, `ORGANIZATION`=2, `PROJECT`=1).
4. Allowed only if `callerAuthorityRank >= roleBeingAssignedRank` **and**
   the caller's authority actually covers `resourceId` (step 2 already
   encodes this). Otherwise throw `ForbiddenException`.

This single method is reused by both `assignRole` and `removeRole`.

## 6. Closing the existing access-control gaps

All of these reuse the existing `ForbiddenException`/`UnauthorizedException`
+ `GlobalExceptionHandler` wiring — no new exception types.

- **`OrganizationService.createOrganization`** — unchanged per discussion
  scope (creating an org is a platform-level action available to any
  authenticated user today; not named as a gap to close in the approved
  scope — **only `addMember`, `createProject`, and the version/node/
  connection endpoints were approved for fixing**). *(See note in §9 if this
  needs revisiting.)*
- **`OrganizationService.addMember`** — add `assertCanManageUsers(organizationId)`
  at the top, mirroring `registerUserInOrganization`.
- **New `ProjectAccessService`** (`project/service/ProjectAccessService.java`),
  with no dependency on `ProjectService` (avoids a circular dependency,
  since `ProjectService` already depends on `VersionService`):
  - `void assertCanView(UUID projectId)` — platform `ADMIN`, or org `OWNER`
    of the owning org, or any `ProjectMember` → else `ForbiddenException`.
  - `void assertCanEdit(UUID projectId)` — platform `ADMIN`, org `OWNER` of
    the owning org, or a `ProjectMember` with `EDIT_PROJECT`/`MANAGE_PROJECT`
    → else `ForbiddenException`.
  - `void assertCanManage(UUID projectId)` — platform `ADMIN`, org `OWNER`
    of the owning org, or a `ProjectMember` with `MANAGE_PROJECT` → else
    `ForbiddenException`.
  - `UUID resolveProjectIdForVersion(UUID versionId)` — backed by a new
    `ProjectRepository.findByVersions_Id(UUID versionId): Optional<Project>`
    derived query (no schema/migration change — the FK column already
    exists, `Version` entity simply has no back-reference field to it).
  - `ProjectService` is refactored to delegate its existing
    `assertCanViewProject` to this new service (keeps the existing public
    method/tests working) and gains calls to `assertCanManage`/`assertCanEdit`
    where missing.
- **`ProjectService.createProject`** — before creating, require the caller
  to be a member of the target organization (any role) or have
  `MANAGE_ORGANIZATION`/platform `ADMIN`. Reuses
  `OrganizationMemberRepository.existsByOrganizationIdAndUserId` (same
  pattern as `assertCanViewOrganization`).
- **`ProjectService.addMember`** — calls `ProjectAccessService.assertCanManage(projectId)`.
- **`ProjectService.listVersions`** — calls `assertCanView(projectId)`.
- **`ProjectService.saveVersion`** — calls `assertCanEdit(projectId)`.
- **`VersionController`** — every endpoint resolves the owning project via
  `ProjectAccessService.resolveProjectIdForVersion(versionId)` and then:
  - read endpoints (`GET /version/{id}`, `GET .../diagram`,
    `GET .../node/{nodeId}`) → `assertCanView`.
  - mutating endpoints (`PATCH`/`POST`/`DELETE` on version, node,
    connection) → `assertCanEdit`.
  - `DELETE /version/{id}` → `assertCanManage` (deleting a version is closer
    to project management than to editing its content).
  - `POST /version/createtest` — this looks like leftover scaffolding
    (no project association is passed). Confirm during implementation
    whether it is still used by anything; if not, delete it rather than
    gate it. **Flagged for a quick check, not a design decision.**

## 7. `SecurityConfig`

No changes needed to `SecurityConfig.filterChain` — `/users/**` already
falls through to `.anyRequest().authenticated()`, which is the correct
blanket rule here (the real authorization is inside `RoleAuthorizationService`
and `ProjectAccessService`, matching the existing pattern where no controller
in this codebase uses `@PreAuthorize`).

## 8. Testing plan

Per `AGENTS.md`: unit tests next to the code, named
`<action><condition>With<Outcome>`, catalogued in `docs/testing.md` in the
same change.

- `role.service.RoleServiceTest` (new) — `listRoles` aggregation across all
  three levels; `assignRole`/`removeRole` happy paths including "creates
  membership for an outside user" and "deletes membership when last role
  removed"; platform role assign/remove.
- `role.service.RoleAuthorizationServiceTest` (new) — one case per hierarchy
  rule in §5: platform admin bypass, org owner managing org, org owner
  managing a project in their org without explicit project membership,
  project admin blocked from org-level assignment, project admin blocked
  from managing a *different* project, editor/member blocked from assigning
  anything.
- `role.controller.UserRoleControllerTest` (new, `@WebMvcTest`) — `401`
  unauthenticated, `403` for a non-owner/non-admin caller, `200` happy paths
  for list/assign/remove.
- `organization.service.OrganizationServiceTest` — add cases for the new
  `addMember` gate (`addMemberRejectsNonManagerWith403`,
  `addMemberAllowsManagerWith2xx`-style).
- `project.service.ProjectServiceTest` — add cases for `createProject`'s new
  org-membership gate, `addMember`'s new gate, `listVersions`'s new gate,
  `saveVersion`'s new gate.
- `project.service.ProjectAccessServiceTest` (new) — the three assert
  methods plus `resolveProjectIdForVersion`, each with an allow and a deny
  case, plus the org-owner-inherits-project-authority case.
- `version.controller.VersionControllerTest` — add `403`-for-non-member
  cases per endpoint (or a parametrized test if the existing test style
  supports it); confirm this class exists today (not found in
  `docs/testing.md` at the time of this spec — if absent, this is a new
  file and should also cover the pre-existing happy paths that currently
  have **zero** coverage).
- Update `docs/testing.md` counts and per-class case lists in the same
  change; update `docs/domain-model.md` (role catalog table from §3) and
  `docs/considerations.md` (note the gaps closed and why, dated
  2026-10-14).

## 9. Explicitly out of scope

- `OrganizationController.createOrganization` staying open to any
  authenticated user — not part of the approved fix list. Revisit as its
  own card if product wants it gated.
- Any new Spring Security authority/`@PreAuthorize` usage — this feature
  follows the existing manual `assertCan*`-in-service pattern, not a new one.
- Frontend changes (role-aware UI for assigning roles) — backend only.
- An `INVITED`/pending-role state — same limitation already noted elsewhere
  in `docs/considerations.md` (`BaseEntity.active` is a plain boolean).

---

## 10. Implementation plan (tasks)

Ordered so each task leaves `mvn test` green. TDD within each task (tests
before/alongside the implementation), per `AGENTS.md`.

### Task 1 — `ProjectAccessService` + version→project resolution
- Add `ProjectRepository.findByVersions_Id(UUID versionId): Optional<Project>`.
- Create `project/service/ProjectAccessService.java` with `assertCanView`,
  `assertCanEdit`, `assertCanManage`, `resolveProjectIdForVersion`,
  implementing the org-owner-inherits-authority rule.
- Refactor `ProjectService.assertCanViewProject` to delegate to it (keep the
  public method for existing callers/tests).
- Tests: `ProjectAccessServiceTest` (new).

### Task 2 — Close the `organization`/`project` write-path gaps
- `OrganizationService.addMember`: add `assertCanManageUsers(organizationId)`.
- `ProjectService.createProject`: add the org-membership-or-manage check.
- `ProjectService.addMember`: add `projectAccessService.assertCanManage(projectId)`.
- `ProjectService.listVersions`: add `assertCanView`.
- `ProjectService.saveVersion`: add `assertCanEdit`.
- Tests: new cases in `OrganizationServiceTest`/`OrganizationControllerTest`
  and `ProjectServiceTest`/`ProjectControllerTest` for each new gate (403 for
  a non-authorized caller, 2xx for an authorized one).

### Task 3 — Gate `VersionController`/`VersionService`
- Check what `POST /version/createtest` actually does; delete it if it is
  unused scaffolding, otherwise gate it like the rest.
- Add the view/edit/manage checks per §6 to every `VersionController`
  endpoint, resolving the project via `ProjectAccessService`.
- Tests: `VersionControllerTest` (create if absent) covering 403-for-
  non-member and the pre-existing happy paths (currently uncovered).

### Task 4 — `role` package: DTOs + `RoleAuthorizationService`
- `role/dto/{RoleLevel,UserRoleDTO,AssignRoleRequestDTO,RemoveRoleRequestDTO}`.
- `role/service/RoleAuthorizationService.java` implementing §5.
- Add any missing user-centric repository lookups
  (`OrganizationMemberRepository.findByUserId`,
  `ProjectMemberRepository.findByUserId`) if not already present.
- Tests: `RoleAuthorizationServiceTest` covering every rule in §5.

### Task 5 — `RoleService` (assign / remove / list)
- Implement `listRoles`, `assignRole`, `removeRole` per §4.2, including the
  "create membership on the fly for an outside user" and "delete membership
  when its last role is removed" behaviors.
- Tests: `RoleServiceTest`.

### Task 6 — `UserRoleController`
- `GET`/`POST`/`DELETE /users/{userId}/roles` per §4.1, with the
  self-or-platform-admin gate on `GET`.
- Tests: `UserRoleControllerTest` (`@WebMvcTest`), 401/403/200 cases.

### Task 7 — Docs
- `docs/domain-model.md`: add the role-catalog table (§3) and describe the
  new `role` package.
- `docs/testing.md`: append every new test class/case, update totals.
- `docs/considerations.md`: dated 2026-10-14 entry describing the gaps
  closed (§1/§6) and the hierarchy rule (§5), so future agents don't
  reopen them without reading why.
- `README.md` (root/backend) only if new env vars or setup steps were
  introduced (not expected here).

### Task 8 — Full verification
- `cd backend && mvn test` green.
- Manual sanity pass via Swagger: assign a project role to a user outside
  the organization, confirm they can then see/edit that project and not
  others; confirm a project admin gets `403` trying to assign an
  organization-level role.
