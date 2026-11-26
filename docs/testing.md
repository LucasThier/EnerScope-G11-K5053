# Test catalog

A registry of the automated tests: what each test class covers and what every
case verifies. **Keep this in sync with the code** — whenever you add, remove or
change a test, update the matching entry here (see `AGENTS.md` → Definition of
done).

Run the backend with `cd backend && mvn test` (H2, no database), and with `cd backend && mvn -B verify` to also run the
twelve `*IT` classes (failsafe) against a **disposable** PostgreSQL pointed to by `ECONOMIC_TEST_DATABASE_URL` (see
[*PostgreSQL classes*](#postgresql-classes-it-by-hand)). CI runs `mvn --batch-mode verify` in the `backend-it` job.
Last reported counts, verified on 2026-11-22 from `backend/target/{surefire,failsafe}-reports` and `npm test`:
Surefire **108 classes / 1822 executions**, failsafe **12 classes / 113 executions**, frontend **435 tests in 46 files**
(`npm test`, 2026-11-23), all with 0 failures, errors and skips.
Run on 2026-11-22 with `mvn -B verify` against a throwaway PostgreSQL 16 on port 55432: Surefire 1822 run / 0 failures / 0 errors / 0 skipped,
failsafe 113 run / 0 failures / 0 errors / 0 skipped, `BUILD SUCCESS`.

Two automated suites exist:

- **Backend** — `cd backend && mvn test` (JUnit 5, H2). Catalogued class by
  class below.
- **Frontend** — `cd frontend && npm test` (the dependency-free `node:test`
  runner, `node --test tests/*.test.mjs`). It covers pure functions, API
  adapters and a few components rendered through a small TypeScript loader; it
  does **not** render the app in a browser. See
  [*Frontend unit tests*](#frontend-unit-tests-npm-test) for the per-file
  counts.

A third, small suite covers the local demo seed (`docs/examples/seed-local-demo.mjs`): 18 `node:test` unit tests
with no server, plus 2 integration tests that need a backend on a disposable database. See
[*Local demo seed*](#local-demo-seed-node-test).

What neither suite covers — layout, focus, keyboard and the screens as a user
sees them — stands on the by-hand checklist at the end of the backend catalog,
[*Manual verification (frontend)*](#manual-verification-frontend), plus the
Playwright scripts `frontend/tests/*-browser.mjs` (not part of `npm test`; they
need a local Vite server and a Playwright install, see
[`frontend/README.md`](../frontend/README.md)). **Those scripts are out of date**: they still mock the old
version-level endpoints (`/versions/:v/economics/...`) and were not run or updated for the scenario flow
(`considerations.md`, *Deuda de escenarios*). Update them before relying on them, and keep them in sync when a screen
changes.

## Conventions

- Tests live next to the code under `backend/src/test/java`, mirroring the class
  under test (e.g. `user/service/UserServiceTest`).
- Test method names describe the scenario and expected outcome; this document
  provides the human-readable "what it verifies".
- **Type** legend:
  - **Unit** — plain JUnit + Mockito, no Spring context, no database.
  - **Web** — `@WebMvcTest` (web layer + security only, collaborators mocked, no
    database).
  - **Data** — `@DataJpaTest` (the JPA slice against H2 on the `test` profile:
    real entities, real schema, real queries, no web layer). Use it for what
    only a database can answer, such as whether a `@Query` filters the rows it
    claims to.
  - **Integration** — `@SpringBootTest` with the full context on the H2 `test`
    profile.

## Summary

Counts come from two sources on this branch: **cases** are the `@Test` and
`@ParameterizedTest` methods in each class, read from the source; **executions**
are the `tests` attribute of its Surefire report from the latest `mvn test`
(`backend/target/surefire-reports`), which expands each parameterized method
into one execution per argument.

> **Superseded by the later reconciliations at the end of this file — the current figures are 107 classes /
> 1818 executions and failsafe 11 classes / 111 executions (the table below sums to them).** The note that follows
> is kept as the history of the first reconciliation. **Reconciled 2026-11-22** (first pass) against
> `mvn -B clean verify` (PostgreSQL 16 throwaway database, port 55432): Surefire **98 classes, 1647 executions,
> 0 failures/errors/skips**; failsafe **6 classes, 58 executions**; at that moment the table summed to those
> 1647 executions and 1415 cases. The previous total
> (1541 executions) had drifted: four classes were stale (`common.CsvUtilTest` 5 → 8,
> `common.GlobalExceptionHandlerTest` 1 → 3, `version.controller.VersionControllerTest` 20 → 27,
> `version.service.VersionServiceTest` 63 → 66) and `version.service.VersionNodeCsvImportServiceTest`
> (27 cases, documented further down) was missing from this table. The per-case sections of those four
> classes were **not** rewritten, only their counts.

Six classes use `@ParameterizedTest` — `economic.controller.ScenarioVerticalAccessHttpTest`,
`node.controller.NodeCreateAccessHttpTest`, `node.model.enums.NodeTypeEnumTest`,
`version.controller.VersionBranchAccessHttpTest`,
`version.controller.VersionMergeAccessHttpTest` and
`version.controller.VersionNodeVerticalAccessHttpTest` — so for them cases and
executions differ; for every other class they are the same number. No class uses
`@RepeatedTest` or `@Disabled`.

| Test class | Type | Cases | Executions |
| --- | --- | --- | --- |
| `ApplicationContextTest` | Integration | 1 | 1 |
| `auth.controller.AuthControllerTest` | Web | 16 | 16 |
| `auth.filter.SessionRevocationHttpTest` | Integration | 9 | 9 |
| `common.CsvUtilTest` | Unit | 8 | 8 |
| `comparison.controller.ScenarioComparisonControllerTest` | Web | 6 | 6 |
| `comparison.repository.ScenarioComparisonMigrationTest` | Data (JDBC/H2) | 3 | 3 |
| `comparison.service.ScenarioComparisonCalculatorTest` | Unit | 16 | 16 |
| `comparison.service.ScenarioComparisonServiceTest` | Unit | 22 | 22 |
| `comparison.service.ScenarioMetricsExtractorTest` | Unit | 6 | 6 |
| `comparison.service.ScenarioStatusServiceTest` | Unit | 6 | 6 |
| `common.GlobalExceptionHandlerTest` | Unit | 3 | 3 |
| `comparison.controller.ScenarioComparisonHttpTest` | Integration | 10 | 15 |
| `economic.controller.EconomicControllerTest` | Web | 12 | 12 |
| `economic.controller.EconomicJobControllerTest` | Web | 4 | 4 |
| `economic.controller.EconomicWriteAccessHttpTest` | Integration | 9 | 9 |
| `economic.controller.ScenarioConfigurationControllerTest` | Web | 4 | 4 |
| `economic.controller.ScenarioVerticalAccessHttpTest` | Integration | 22 | 26 |
| `economic.service.EconomicEngineTest` | Unit | 21 | 21 |
| `economic.service.EconomicIndicatorsCalculatorTest` | Unit | 17 | 17 |
| `economic.service.EconomicJobServiceTest` | Unit | 10 | 10 |
| `economic.service.EconomicJobWorkerTest` | Unit | 4 | 4 |
| `economic.service.EconomicServiceTest` | Unit | 34 | 34 |
| `economic.service.EconomicSimulationAdapterTest` | Unit | 2 | 2 |
| `economic.service.EconomicStatisticsCalculatorTest` | Unit | 5 | 5 |
| `economic.service.ProbabilisticSimulationTest` | Unit | 8 | 8 |
| `economic.service.ScenarioConfigurationTest` | Integration | 16 | 16 |
| `economic.service.ScenarioOperationsTest` | Unit | 6 | 6 |
| `jwt.JwtServiceTest` | Unit | 5 | 5 |
| `logging.ConsoleAppLoggerTest` | Unit | 1 | 1 |
| `money.MoneyAmountTest` | Unit | 7 | 7 |
| `node.controller.NodeControllerTest` | Unit | 1 | 1 |
| `node.controller.NodeCreateAccessHttpTest` | Integration | 10 | 46 |
| `node.dto.NodeRequestDeserializerTest` | Unit | 4 | 4 |
| `node.migration.ExportVerticalMigrationTest` | Data (JDBC/H2) | 2 | 2 |
| `node.migration.MigrationCatalogTest` | Unit | 3 | 3 |
| `node.model.enums.NodeTypeEnumTest` | Unit | 6 | 50 |
| `node.repository.ConsumptionPersistenceTest` | Data | 1 | 1 |
| `node.service.NodeClonerRoleTest` | Unit | 2 | 2 |
| `node.service.NodeServiceOptionalBaseValuesTest` | Unit | 4 | 48 |
| `node.service.NodeServiceTest` | Unit | 7 | 7 |
| `organization.controller.BulkUploadSizeLimitHttpTest` | Integration | 2 | 2 |
| `organization.controller.OrganizationControllerTest` | Web | 47 | 47 |
| `organization.repository.OrganizationMemberRepositoryTest` | Data | 17 | 17 |
| `organization.repository.OrganizationRepositoryTest` | Data | 24 | 24 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 18 | 18 |
| `organization.service.OrganizationServiceTest` | Unit | 88 | 88 |
| `project.controller.ProjectControllerTest` | Web | 46 | 46 |
| `project.model.ProjectMemberTest` | Unit | 3 | 3 |
| `project.repository.ProjectMemberRepositoryTest` | Data | 21 | 21 |
| `project.repository.ProjectRepositoryTest` | Data | 18 | 18 |
| `project.service.ProjectAccessGuardTest` | Unit | 26 | 26 |
| `project.service.ProjectServiceTest` | Unit | 108 | 108 |
| `scenario.controller.ScenarioApiHttpTest` | Integration | 17 | 79 |
| `scenario.service.ScenarioAccessTest` | Unit | 7 | 7 |
| `scenario.service.ScenarioChangeScopeTest` | Unit | 16 | 16 |
| `scenario.service.ScenarioDefaultsTest` | Unit | 4 | 4 |
| `scenario.service.ScenarioLifecycleTest` | Unit | 20 | 20 |
| `scenario.service.ScenarioLimitsTest` | Unit | 2 | 2 |
| `scenario.service.ScenarioReferenceServiceTest` | Unit | 4 | 4 |
| `scenario.service.VersionDefaultScenarioTest` | Unit | 6 | 6 |
| `session.service.SessionServiceTest` | Unit | 9 | 9 |
| `simulator.EconomicMetricsTest` | Unit | 7 | 7 |
| `simulator.ResultMappingTest` | Data | 8 | 8 |
| `simulator.ResultPerRoundTest` | Unit | 12 | 12 |
| `simulator.SimulatorTest` | Unit | 15 | 15 |
| `strategyCost.CostBasisCalculatorsTest` | Unit | 10 | 10 |
| `strategyCost.CostTest` | Unit | 8 | 8 |
| `strategyCost.InvestmentCostTest` | Unit | 2 | 2 |
| `user.controller.UserControllerTest` | Web | 30 | 30 |
| `user.repository.UserRepositoryTest` | Data | 22 | 22 |
| `user.service.PasswordGeneratorTest` | Unit | 4 | 4 |
| `user.service.UserServiceTest` | Unit | 58 | 58 |
| `util.AuthUtilTest` | Unit | 7 | 7 |
| `version.controller.FlngUnitShipAndGasHttpTest` | Integration | 2 | 2 |
| `version.controller.ProjectExecutiveHttpTest` | Integration | 12 | 12 |
| `version.controller.ProjectVersionMembersHttpTest` | Integration | 5 | 5 |
| `version.controller.VersionAccessControllerTest` | Web | 3 | 3 |
| `version.controller.VersionAccessHttpTest` | Integration | 15 | 15 |
| `version.controller.VersionBranchAccessHttpTest` | Integration | 25 | 29 |
| `version.controller.VersionConflictsListAccessHttpTest` | Integration | 8 | 8 |
| `version.controller.VersionControllerTest` | Web | 27 | 27 |
| `version.controller.VersionGetJsonOpenInViewHttpTest` | Integration | 1 | 1 |
| `version.controller.VersionGetJsonWithoutOpenInViewHttpTest` | Integration | 3 | 3 |
| `version.controller.VersionInactiveAccessHttpTest` | Integration | 5 | 5 |
| `version.controller.VersionMergeAccessHttpTest` | Integration | 24 | 41 |
| `version.controller.VersionNodeVerticalAccessHttpTest` | Integration | 24 | 94 |
| `version.controller.VersionQueryControllerTest` | Web | 4 | 4 |
| `version.controller.VersionReadAccessHttpTest` | Integration | 11 | 11 |
| `version.controller.VersionReadWithoutOpenInViewHttpTest` | Integration | 4 | 4 |
| `version.controller.VersionRoleAfterProjectRemovalHttpTest` | Integration | 7 | 7 |
| `version.controller.VersionRoleChangeHttpTest` | Integration | 10 | 10 |
| `version.controller.VersionRoleCopyDeactivatedUsersHttpTest` | Integration | 3 | 3 |
| `version.dto.VersionConflictDTOTest` | Unit | 1 | 1 |
| `version.repository.VersionConflictRepositoryTest` | Data | 6 | 6 |
| `version.service.VersionAccessGuardTest` | Unit | 56 | 56 |
| `version.service.VersionAccessServiceTest` | Unit | 16 | 16 |
| `version.service.VersionAccessTest` | Unit | 11 | 11 |
| `version.service.VersionConflictIntegrationTest` | Integration | 11 | 11 |
| `version.service.VersionConflictServiceTest` | Unit | 30 | 30 |
| `version.service.VersionDeletionTest` | Unit | 9 | 9 |
| `version.service.VersionLineageTest` | Unit | 10 | 10 |
| `version.service.VersionMemberServiceTest` | Unit | 39 | 39 |
| `version.service.VersionNodeCsvImportAllKindsTest` | Integration | 2 | 2 |
| `version.service.VersionNodeCsvImportIdentityTest` | Integration | 7 | 7 |
| `version.service.VersionNodeCsvImportServiceTest` | Unit | 34 | 45 |
| `version.service.VersionNodeCsvImportTemplatesTest` | Integration | 2 | 2 |
| `version.service.VersionQueryServiceTest` | Unit | 10 | 10 |
| `version.service.VersionServiceTest` | Unit | 69 | 69 |
| **Total (108 classes)** | | **1525** | **1822** |

> **`mvn test` on 2026-11-15 (13:35, run for this update): 1362 executions,
> 0 failures, 0 errors, 0 skipped**, in 80 classes (`1238` methods). Executions
> are the `tests` attribute of the 80 Surefire reports that run wrote
> (`backend/target/surefire-reports`, 0 failures / 0 errors / 0 skipped in
> each); cases are the `@Test` / `@ParameterizedTest` methods counted in the
> sources, and the table was regenerated from both. The previous pass of this
> catalog recorded 73 classes, 1153 cases and 1272 executions; the difference
> is the seven new classes below plus the cases added to existing ones
> (`VersionServiceTest` +20, `VersionAccessGuardTest` +7,
> `VersionMemberServiceTest` +7, `VersionMergeAccessHttpTest` +9 cases / +14
> executions, `ScenarioVerticalAccessHttpTest` +3 / +3, `EconomicServiceTest`
> +2, `VersionAccessServiceTest` +1, `OrganizationControllerTest` +1). New
> classes: `common.GlobalExceptionHandlerTest`,
> `organization.controller.BulkUploadSizeLimitHttpTest`,
> `simulator.ResultMappingTest` (rewritten, see below),
> `version.controller.VersionAccessHttpTest`,
> `version.controller.VersionInactiveAccessHttpTest`,
> `version.controller.VersionReadWithoutOpenInViewHttpTest` and
> `version.controller.VersionRoleCopyDeactivatedUsersHttpTest`. The
> PostgreSQL classes — the `*IT`, ten today — are **not** part of that run and not in the
> table; they are run by hand against a disposable PostgreSQL (see below). The
> per-class sections below keep their historical case catalogs; where one
> disagrees with this table, this table wins. The cases added since the
> 2026-11-14 catalogs are in
> [*Version roles, cascade delete, root versions and CSV limit — 2026-11-15*](#version-roles-cascade-delete-root-versions-and-csv-limit--2026-11-15)
> and, for the later commits, in
> [*Active-only guards, 413, 403 before 400 and the rewritten `ResultMappingTest` — 2026-11-15*](#active-only-guards-413-403-before-400-and-the-rewritten-resultmappingtest--2026-11-15).

> **Update — structural role, V24, role change and project matrix.** `mvn test`
> after those four changes: **1463 executions, 0 failures, 0 errors, 0 skipped,
> in 85 classes (1285 cases)**; the table above was regenerated from the sources
> and the Surefire reports, which also refreshed rows other changes had left
> stale (`VersionAccessHttpTest`, `EconomicServiceTest`, `NodeCreateAccessHttpTest`,
> `NodeRequestDeserializerTest` and the two `VersionGetJson…` classes, now listed).
> Added or changed by these four changes — details in
> [*Structural role, editor simulate permission, role change and project matrix*](#structural-role-editor-simulate-permission-role-change-and-project-matrix--2026-11-15):
> `NodeTypeEnumTest` 4→6 cases (26→50 executions), `VersionNodeVerticalAccessHttpTest`
> 18→24 (58→94), `VersionMemberServiceTest` 26→36, `VersionAccessTest` 10→11, new
> `NodeClonerRoleTest` (2), `VersionRoleChangeHttpTest` (10) and
> `ProjectVersionMembersHttpTest` (5); `MigrationCatalogTest` now expects 24
> migrations. The two new `*IT` cases are **not** in that 1463.

> **Update — project member type `EXECUTIVE`.** `mvn test` after it:
> **1488 executions, 0 failures, 0 errors, 0 skipped, in 87 classes (1310
> cases)**; the table above already reflects it. +25 cases: new
> `ProjectMemberTest` (3) and `ProjectExecutiveHttpTest` (12), `ProjectServiceTest`
> 104→108, `VersionMemberServiceTest` 36→39, `ProjectRepositoryTest` 17→18,
> `VersionAccessServiceTest` 15→16, `VersionAccessHttpTest` 14→15. Details in
> [*Project member type EXECUTIVE*](#project-member-type-executive--2026-11-15).
> The new `ProjectRepositoryPostgreSqlIT` case is **not** in that 1488.

> **Update — FLNG ship capacity and gas consumption.** `mvn test` after it:
> **1493 executions, 0 failures, 0 errors, 0 skipped, in 88 classes (1316
> cases)** — the last line of a clean `mvn clean test` (`Tests run: 1493`); the
> table above already reflects it. +5 executions over the 1488 above: `NodeServiceTest`
> 5→7, the new `FlngUnitShipAndGasHttpTest` (2) and one
> `NodeCreateAccessHttpTest` case (pipeline connection). The 1316 cases also
> correct `NodeCreateAccessHttpTest`, which the table listed as 8 although it had 9
> before that case. Details in
> [*FLNG unit: ship capacity and gas consumption*](#flng-unit-ship-capacity-and-gas-consumption--2026-11-15).

### PostgreSQL classes (`*IT`, by hand)
Counts below treat each JUnit test method as one case; parameterized methods
are counted once in the table, with their extra executions explained below.

Not run by `mvn test`; they need a PostgreSQL the test connects to (see each
class). **Today: 11 classes, 111 executions** (table below), read on 2026-11-22 from the eleven
`TEST-*IT.xml` failsafe reports in `backend/target/failsafe-reports` (written that day by
`mvn -B verify`, 0 failures, 0 errors, 0 skipped). The first version of this section counted **49 executions in
five classes** from a manual run on 2026-11-15; that figure is history and no longer applies.

| Test class | Cases | Executions |
| --- | --- | --- |
| `ApplicationContextTest` | Integration | 1 |
| `node.migration.ExportVerticalMigrationTest` | Data (JDBC/H2) | 2 |
| `auth.controller.AuthControllerTest` | Web | 16 |
| `common.CsvUtilTest` | Unit | 8 |
| `common.GlobalExceptionHandlerTest` | Unit | 3 |
| `jwt.JwtServiceTest` | Unit | 5 |
| `util.AuthUtilTest` | Unit | 7 |
| `logging.ConsoleAppLoggerTest` | Unit | 1 |
| `money.MoneyAmountTest` | Unit | 7 |
| `node.controller.NodeControllerTest` | Unit | 1 |
| `node.service.NodeServiceTest` | Unit | 2 |
| `organization.controller.OrganizationControllerTest` | Web | 39 |
| `organization.repository.OrganizationMemberRepositoryTest` | Data | 11 |
| `organization.repository.OrganizationRepositoryTest` | Data | 16 |
| `user.service.UserServiceTest` | Unit | 58 |
| `user.service.PasswordGeneratorTest` | Unit | 4 |
| `user.controller.UserControllerTest` | Web | 30 |
| `user.repository.UserRepositoryTest` | Data | 22 |
| `organization.service.OrganizationServiceTest` | Unit | 77 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 13 |
| `organization.controller.OrganizationControllerTest` | Web | 41 |
| `organization.repository.OrganizationRepositoryTest` | Data | 16 |
| `project.service.ProjectAccessGuardTest` | Unit | 22 |
| `project.service.ProjectServiceTest` | Unit | 49 |
| `project.controller.ProjectControllerTest` | Web | 27 |
| `project.repository.ProjectRepositoryTest` | Data | 13 |
| `project.repository.ProjectMemberRepositoryTest` | Data | 5 |
| `version.service.VersionServiceTest` | Unit | 22 [^p] |
| `version.service.VersionNodeCsvImportServiceTest` | Unit | 27 [^p] |
| `version.controller.VersionControllerTest` | Web | 10 |
| `node.service.NodeServiceTest` | Unit | 1 |
| `node.controller.NodeControllerTest` | Unit | 1 |
| `simulator.ResultPerRoundTest` | Unit | 12 |
| `simulator.SimulatorTest` | Unit | 15 |
| `organization.service.OrganizationServiceTest` | Unit | 68 |
| `project.controller.ProjectControllerTest` | Web | 43 |
| `project.repository.ProjectMemberRepositoryTest` | Data | 16 |
| `project.repository.ProjectRepositoryTest` | Data | 17 |
| `project.service.ProjectAccessGuardTest` | Unit | 26 |
| `project.service.ProjectServiceTest` | Unit | 83 |
| `simulator.EconomicMetricsTest` | Unit | 7 |
| `simulator.ResultMappingTest` | Data | 7 |
| `simulator.ResultTest` | Unit | 10 |
| `simulator.SimulatorTest` | Unit | 12 |
| `strategyCost.CostBasisCalculatorsTest` | Unit | 10 |
| `strategyCost.CostTest` | Unit | 8 |
| `strategyCost.InvestmentCostTest` | Unit | 2 |
| `strategyCost.CostBasisCalculatorsTest` | Unit | 10 |
| **Total** | | **460 [^p]** |
| `economic.service.PostgreSqlEconomicIT` | 4 | 4 |
| `economic.service.ScenarioConfigurationPostgreSqlIT` | 1 own + 16 inherited from `ScenarioConfigurationTest` | 17 |
| `project.repository.ProjectRepositoryPostgreSqlIT` | 7 | 7 |
| `project.service.ProjectVersionCreationPostgreSqlIT` | 10 | 10 |
| `scenario.repository.ScenarioMigrationIT` | 13 | 13 |
| `scenario.service.ScenarioLifecyclePostgreSqlIT` | 20 inherited from `ScenarioLifecycleTest` | 20 |
| `scenario.service.VersionDefaultScenarioPostgreSqlIT` | 6 inherited from `VersionDefaultScenarioTest` | 6 |
| `version.service.VersionDeletionPostgreSqlIT` | 9 inherited from `VersionDeletionTest` | 9 |
| `version.service.VersionNodeCsvImportAllKindsPostgreSqlIT` | 2 inherited from `VersionNodeCsvImportAllKindsTest` | 2 |
| `version.service.VersionNodeCsvImportIdentityPostgreSqlIT` | 7 inherited from `VersionNodeCsvImportIdentityTest` | 7 |
| `version.service.VersionNodeCsvImportTemplatesPostgreSqlIT` | 2 inherited from `VersionNodeCsvImportTemplatesTest` | 2 |
| `version.repository.VersionMemberRepositoryPostgreSqlIT` | 16 | 16 |
| **Total (12 classes)** | | **113** |

`ProjectRepositoryPostgreSqlIT` and `ProjectVersionCreationPostgreSqlIT` are new
in the 2026-11-15 section; the two new `VersionMemberRepositoryPostgreSqlIT`
cases are in the section after it. **Three of the 52 were never run**: those two
(V24 backfill and project matrix query count) and the case added to
`ProjectRepositoryPostgreSqlIT` for `EXECUTIVE`
(`summariesReportExecutiveAsMyRoleOnPostgreSql`); the other 49 are as read from
the earlier manual run.
[^p]: Two methods in `version.service.VersionServiceTest` are
`@ParameterizedTest`s running over the eight mutating version entry points;
`VersionNodeCsvImportServiceTest` has one parameterized method for the 12 node
kinds. The 460 catalogued cases therefore make 485 executions.

> **This catalog has drifted from the code.** On 2026-10-05 `mvn test` ran
> **587** executions, against the 447 above. The gap is not a mistake in the
> rows: it is tests merged since the catalog was last reconciled. Three classes
> have no entry (`organization.repository.OrganizationMemberRepositoryTest`,
> `simulator.ResultTest` and `simulator.SimulatorTest`: 33 executions) and
> eleven have grown past their recorded count (`user.*`, `organization.*`,
> `project.*` and `version.service.VersionServiceTest`: 107 executions in
> total). Reconciling them is its own task. `simulator.ResultMappingTest` was
> added on 2026-10-05 and is counted correctly.

## `ApplicationContextTest` — Integration

Smoke test that the whole application wires together.

| Case | Verifies |
| --- | --- |
| `contextLoadsAndSeedsAdmin` | The full Spring context (security, filters, JWT, JPA, OpenAPI, seeder) starts, and `AdminSeeder` creates the default `admin@enerscope.org` on boot. |

## `auth.controller.AuthControllerTest` — Web

Exercises `AuthController` through the real `SecurityConfig`/`AuthFilter` chain
(`/auth/login`, `/auth/refresh`, `/auth/logout` are public; `/auth/register`
requires an ADMIN bearer token); `SessionService`, `UserService` and
`UserRepository` are mocked.

| Case | Verifies |
| --- | --- |
| `registerCreatesUserWhenCallerIsAdmin` | `POST /auth/register` by an authenticated ADMIN with a valid body → `201` `User registered` with the created user's `mail` and `platformRole` (no session for the admin). |
| `registerRequiresAuthenticationWith401` | `POST /auth/register` with no token → `401`; `UserService.register` is never called. |
| `registerRejectsNonAdminWith403` | `POST /auth/register` by an authenticated non-admin → `403`; `UserService.register` is never called. |
| `registerRejectsDuplicateEmailWith400` | For an ADMIN caller, when registration throws (duplicate email) → `400`, `success=false`, and the domain error message. |
| `registerRejectsInvalidBodyWithValidationError` | For an ADMIN caller, invalid email/too-short name/password → `400` `Validation error` with field details; `UserService.register` is never called. |
| `loginReturnsSessionForValidCredentials` | `POST /auth/login` with valid credentials → `200` `Authenticated` and tokens. |
| `loginExposesJobTitleInTheUserSummary` | The login response's `data.user.jobTitle` carries the user's job title, so the client needs no extra call. |
| `loginRejectsBadCredentialsWith400` | Wrong credentials → `400` `Invalid email or password`. |
| `loginRejectsADeactivatedAccountWith403` | An inactive account → `403` `This account has been deactivated`. A `403` and not a `401` on purpose: the client's response interceptor retries a refresh on every `401`, and login goes through that client. |
| `loginRejectsBlankFieldsWithValidationError` | Blank mail/password → `400` `Validation error`; `UserService.login` is never called. |
| `refreshIssuesNewSessionForValidToken` | Valid refresh token for an existing user → `200` `Session renewed` with a new refresh token. |
| `refreshRejectsInvalidTokenWith401` | Invalid/expired refresh token → `401` `Invalid or expired refresh token`. |
| `refreshRejectsWhenUserNoLongerExistsWith401` | Token valid but the user no longer exists → `401` `User not found`. |
| `refreshRejectsADeactivatedAccountWith401` | Token valid but the account is inactive → `401` `This account has been deactivated`, and no session is minted. |
| `refreshRejectsBlankTokenWithValidationError` | Blank `refreshToken` → `400` `Validation error`; `SessionService.validateRefreshToken` is never called. |
| `logoutReturnsOk` | `POST /auth/logout` → `200` `Session closed` (stateless no-op). |

## `common.CsvUtilTest` — Unit

Covers the in-repo CSV reader/writer used by bulk registration and strict node
CSV import.

| Case | Verifies |
| --- | --- |
| `parsesSimpleRowsAndTrims` | Header + data rows parse into fields, each trimmed of surrounding spaces. |
| `handlesQuotedFieldsWithCommasAndEscapedQuotes` | Quoted fields keep embedded commas, and `""` decodes to a literal quote. |
| `skipsBlankLinesAndHandlesCrlfAndMissingTrailingNewline` | `\r\n` endings, fully blank lines skipped, and a final row without a trailing newline are handled. |
| `parseEmptyContentReturnsNoRows` | `null`/empty input yields no rows. |
| `writeQuotesOnlyWhenNecessaryAndRoundTrips` | The writer quotes only fields that need it (comma/quote), and output re-parses back to the original values. |
| `strictParserRejectsUnterminatedQuotedFields` | Strict CSV parsing rejects a quoted field that never closes. |
| `strictParserRejectsQuotesInsideUnquotedFields` | Strict CSV parsing rejects quotes embedded in an unquoted field. |
| `strictParserSupportsMultilineQuotedFieldsAndEnforcesRowLimit` | Quoted newlines are preserved and the configured row limit is enforced. |

## `common.GlobalExceptionHandlerTest` — Unit

Multipart request failures are returned in the standard API envelope rather
than becoming generic `500` responses.

| Case | Verifies |
| --- | --- |
| `oversizedMultipartUploadReturns413WithApiResponse` | A request exceeding the server multipart limit returns `413` with an error envelope. |
| `missingMultipartFieldReturns400WithFieldName` | A missing required multipart field returns `400` and names the field. |
| `malformedMultipartRequestReturns400` | An invalid multipart request returns `400` instead of a generic server error. |

## `jwt.JwtServiceTest` — Unit

Token issuing and validation.

| Case | Verifies |
| --- | --- |
| `accessTokenCarriesUserClaims` | An access token carries `sub`, `mail`, `firstName`, `lastName`, `role` and validates successfully. |
| `accessTokenCarriesAdminRoleClaim` | An access token for an ADMIN carries `role=ADMIN`. |
| `accessAndRefreshTokensAreNotInterchangeable` | The `typ` claim keeps access and refresh tokens from being accepted in the other's place. |
| `rejectsGarbageAndBlankTokens` | Non-JWT, empty and `null` tokens are rejected. |
| `rejectsTokenSignedWithAnotherKey` | A token signed with a different secret fails validation. |

## `util.AuthUtilTest` — Unit

The session lookup and the platform-ADMIN check every service runs. These rules
were copy-pasted across `OrganizationService`, `ProjectService` and
`ProjectAccessGuard` before they moved here, so they are now verified once on
top of the coverage each caller keeps.

| Case | Verifies |
| --- | --- |
| `currentSessionReturnsNullWhenThereIsNoAuthentication` | With no security context the lookup answers `null` rather than throwing — this is the raw accessor. |
| `requireSessionReturnsTheSessionBoundToTheRequest` | The session the auth filter attached is the one returned. |
| `requireSessionRejectsUnauthenticated` | No session throws `UnauthorizedException` (`Authentication required`). |
| `isPlatformAdminIsTrueOnlyForAdmins` | The predicate answers `true` for `PlatformRole.ADMIN` and `false` for a regular user. |
| `requirePlatformAdminAllowsAnAdminWithoutLogging` | A platform ADMIN passes and nothing is logged — refusals are the only interesting event. |
| `requirePlatformAdminRejectsRegularUserWith403AndLogsTheRefusal` | A regular user gets `ForbiddenException` with the action in the message, and the refusal is logged at `warn` with the caller's mail. |
| `requirePlatformAdminRejectsUnauthenticatedBeforeLogging` | No session throws `UnauthorizedException` before anything is logged: there is no caller to name yet. |

## `logging.ConsoleAppLoggerTest` — Unit

| Case | Verifies |
| --- | --- |
| `logsAtEveryLevelWithoutThrowing` | `debug`/`info`/`warn`/`error` (including the throwable overload) run without throwing. |

## `money.MoneyAmountTest` — Unit

The `MoneyAmount` value object.

| Case | Verifies |
| --- | --- |
| `normalizesToTwoDecimalsWithHalfUpRounding` | Values are scaled to 2 decimals with `HALF_UP` rounding. |
| `addsAndSubtracts` | `add`/`subtract` produce the expected amounts. |
| `multipliesAndDivides` | `multiply`/`divide` produce the expected amounts. |
| `rejectsNullValue` | Constructing from `null` throws `IllegalArgumentException`. |
| `rejectsDivisionByZero` | Dividing by zero throws `ArithmeticException`. |
| `equalityIsValueBased` | Equality and `hashCode` are based on the numeric value. |
| `addsAll` | `addAll` folds a list of amounts onto the receiver. |

## `user.service.UserServiceTest` — Unit

Registration, login and password logic.

| Case | Verifies |
| --- | --- |
| `registerHashesPasswordAndPersists` | Registration normalises the email, hashes the password, and persists the user. |
| `registerDefaultsToUserRoleWhenRoleOmitted` | Registration with no role creates a `USER` platform role. |
| `registerPersistsJobTitle` | A `jobTitle` in the request is stored on the created user. |
| `registerLeavesJobTitleNullWhenOmitted` | Omitting `jobTitle` leaves it null rather than blank. |
| `registerHonorsExplicitAdminRole` | Registration with `role=ADMIN` creates an `ADMIN` platform role. |
| `registerRejectsDuplicateMail` | A duplicate email throws and neither saves nor hashes. |
| `loginReturnsUserWhenPasswordMatches` | Login returns the user when the password matches. |
| `loginRejectsWrongPassword` | A wrong password throws `IllegalArgumentException`. |
| `changePasswordReplacesTheStoredHash` | With the correct current password the stored hash is replaced and the user is saved. |
| `changePasswordRejectsWrongCurrentPasswordAndLeavesTheHashAlone` | A wrong current password throws `IllegalArgumentException`, the hash is untouched, and nothing is encoded or saved. |
| `changePasswordRejectsUnknownUser` | An unknown user id throws `IllegalArgumentException`; nothing is saved. |
| `loginRejectsUnknownMail` | An unknown email throws `IllegalArgumentException`. |
| `loginRejectsADeactivatedAccount` | Correct credentials on an inactive account throw `ForbiddenException` (403) rather than opening a session. |
| `loginChecksThePasswordBeforeTheActiveFlag` | A wrong password on an inactive account answers the generic `IllegalArgumentException`, not the "deactivated" message. Pins the ordering that keeps the endpoint from leaking which emails exist. |
| `updateProfileChangesAllThreeFields` | A patch carrying `firstName`, `lastName` and `jobTitle` applies all three and saves. |
| `updateProfileLeavesOutTheFieldsThatAreNull` | A patch carrying only `firstName` leaves the stored last name and job title untouched. |
| `updateProfileClearsTheJobTitleWhenItArrivesBlank` | A whitespace-only `jobTitle` stores `null`, which is the only way to remove a job title under partial semantics. |
| `updateProfileDoesNotLetABlankNameThrough` | A whitespace-only first or last name throws `IllegalArgumentException`; both columns are `NOT NULL`, so blank is not the same as clearing. |
| `updateProfileRejectsAPatchWithEveryFieldNull` | A body with all three fields null throws `IllegalArgumentException`; the user is never looked up. |
| `updateProfileRejectsANullBody` | A null DTO throws `IllegalArgumentException` before any repository call. |
| `updateProfileRejectsUnknownUser` | An unknown user id throws `IllegalArgumentException`; nothing is saved. |
| `updateProfileNeverTouchesMailRoleOrPassword` | `mail`, `platformRole` and `passwordHash` come out unchanged: the DTO has no field that could carry them. |
| `listAllReturnsWhatTheRepositoryProjects` | The service hands back the projection untouched. |
| `listAllRejectsANonAdminCallerWith403` | A regular user is refused before the query runs. |
| `listAllRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `searchByMailReturnsTheMinimalProjectionForAnOwner` | An owner of an active organization gets the name and address, nothing else. |
| `searchByMailAllowsAPlatformAdminWithoutOwningAnything` | A platform admin passes without the ownership query running at all. |
| `searchByMailAnswersTheSameMessageForAnUnknownAndASuspendedAccount` | Both answer `User not found`, identical, so the endpoint cannot be used to tell a suspended account from one that never existed. |
| `searchByMailNeverEchoesTheAddressItWasGiven` | The message is fixed and does not repeat the address that was searched. |
| `searchByMailRejectsACallerWhoOwnsNoOrganizationWith403` | A plain member is refused before the lookup runs. |
| `searchByMailRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `updateRolePromotesAUserToAdminWithoutCountingAdmins` | A promotion applies and never runs the admin count, which only guards demotions. |
| `updateRoleDemotesAnAdminWhenAnotherActiveAdminRemains` | With two active ADMINs, demoting one is allowed. |
| `updateRoleRefusesToDemoteTheLastActiveAdmin` | With one active ADMIN, the demotion throws and the in-memory role is unchanged. |
| `updateRoleDemotesAnInactiveAdminWithoutCountingAdmins` | An already inactive ADMIN is demoted without consulting the count — it was never part of it. |
| `updateRoleIsANoOpWhenTheRoleIsUnchanged` | Setting the role a user already has saves nothing and counts nothing. |
| `updateRoleRejectsANullRole` | A null role throws before the user is looked up. |
| `updateRoleRejectsUnknownUser` | An unknown id throws; nothing is saved. |
| `updateRoleRejectsANonAdminCallerWith403` | A regular user is refused by `requirePlatformAdmin` before any lookup. |
| `updateRoleRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `deactivateUserDeactivatesARegularAccountWithoutCountingAdmins` | A `USER` is deactivated and the admin count is never consulted. |
| `deactivateUserDeactivatesAnAdminWhenAnotherActiveAdminRemains` | With two active ADMINs, deactivating one is allowed. |
| `deactivateUserRefusesToDeactivateTheLastActiveAdmin` | With one active ADMIN, the deactivation throws and the account stays active. |
| `deactivateUserIsIdempotent` | Deactivating an already inactive account saves nothing. |
| `deactivateUserRejectsUnknownUser` | An unknown id throws; nothing is saved. |
| `deactivateUserRejectsANonAdminCallerWith403` | A regular user is refused before any lookup. |
| `deactivateUserRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `reactivateUserBringsASuspendedAccountBack` | A deactivated account is activated and saved. |
| `reactivateUserNeverConsultsTheAdminCount` | The last-admin invariant does not apply: reactivating can only add an active administrator. |
| `reactivateUserIsIdempotent` | Reactivating an already active account saves nothing. |
| `reactivateUserRejectsUnknownUser` | An unknown id throws; nothing is saved. |
| `reactivateUserRejectsANonAdminCallerWith403` | A regular user is refused before any lookup. |
| `reactivateUserRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `getDetailAssemblesIdentityAndBothMembershipLists` | `getDetail` combines the user row with `findOrganizationMembershipsForUser` and `findProjectMembershipsForUser` into one `UserDetailDTO`. |
| `getDetailReturnsTheDetailOfASuspendedAccount` | A deactivated account still has a detail, reported with `active=false` — the screen needs to show who it is in order to offer reactivation. |
| `getDetailThrowsEntityNotFoundForAnUnknownUser` | An unknown id throws `EntityNotFoundException("User not found")` and neither membership query runs. |
| `getDetailRejectsANonAdminCallerWith403` | A regular user is refused before the user is even looked up. |
| `getDetailRejectsUnauthenticated` | No session throws `UnauthorizedException`; nothing is read. |
| `getDetailRejectsANonAdminCallerWith403` | get Detail Rejects ANon Admin Caller With403. |
| `getDetailAssemblesIdentityAndBothMembershipLists` | get Detail Assembles Identity And Both Membership Lists. |
| `getDetailRejectsUnauthenticated` | get Detail Rejects Unauthenticated. |
| `getDetailThrowsEntityNotFoundForAnUnknownUser` | get Detail Throws Entity Not Found For An Unknown User. |
| `getDetailReturnsTheDetailOfASuspendedAccount` | get Detail Returns The Detail Of ASuspended Account. |

## `user.service.PasswordGeneratorTest` — Unit

Secure password generation.

| Case | Verifies |
| --- | --- |
| `generatesRequestedLength` | Passwords have the default and any requested length. |
| `meetsComplexityRequirements` | Every password contains a lower-case, upper-case, digit and symbol. |
| `generatesDistinctPasswords` | 1000 generated passwords are all distinct (randomness sanity check). |
| `rejectsTooShortLength` | Requesting a length below 8 throws `IllegalArgumentException`. |

## `user.repository.UserRepositoryTest` — Data

`countByPlatformRoleAndActiveTrue`, the query the last-admin invariant rests on.
It combines two filters, so a mocked test could only ever prove the mock was
called; each filter was mutation-checked here.

**Test emails must not collide with `app.admin.mail`.** The `test` profile points
at `jdbc:h2:mem:enerscope;DB_CLOSE_DELAY=-1`, a *named* in-memory database that
outlives each context and is shared with the `@SpringBootTest` one, where
`AdminSeeder` **commits** `admin@enerscope.org`. `app_user.mail` is `UNIQUE`, so a
case persisting that address hangs on a lock rather than failing cleanly. Every
address here is prefixed `count-` for that reason.

| Case | Verifies |
| --- | --- |
| `countsTheActiveAdmins` | Two active ADMINs count as two. |
| `excludesInactiveAdmins` | A deactivated ADMIN is not counted, which is what makes the invariant about *active* administrators. |
| `excludesRegularUsers` | `USER` rows are not counted, however many there are. |
| `countsZeroWhenEveryAdminIsInactive` | With every ADMIN deactivated the count is zero, the state the invariant exists to prevent. |
| `listItemsCarryTheFieldsTheTableShows` | `findListItems` projects mail, job title, role and active state into the record the users table renders. |
| `listItemsIncludeDeactivatedAccounts` | Suspended accounts are **in** the list. This is the one list in the repository that must not filter on `active` — it is the only screen from which an account could be revived. |
| `listItemsCountTheOrganizationsEachUserBelongsTo` | A user in two organizations reports 2. |
| `listItemsCountZeroForAnAccountWithNoOrganization` | An account created outside any organization reports 0, which is what makes an otherwise invisible account visible as orphaned. |
| `listItemsDoNotCountAnotherUsersOrganizations` | The subquery is correlated: two users sharing one organization report 1 and 2, not 3 each. |
| `searchFindsAnActiveAccountByItsExactAddress` | The baseline for the owner-facing lookup. |
| `searchIgnoresCase` | `LOWER()` on both sides, so a typed address matches whatever case it was stored in. |
| `searchNeverMatchesPartially` | Neither a local part nor a bare domain matches — this is what keeps the endpoint from enumerating the roster. |
| `searchSkipsASuspendedAccount` | A deactivated account is not found, which is what makes the `404` indistinguishable from an unknown address. |
| `organizationMembershipsCarryTheOrganizationAndTheRole` | `findOrganizationMembershipsForUser` projects the organization's name and the membership's `memberType`. |
| `organizationMembershipsIncludeASuspendedOrganizationMarked` | A membership in a deactivated organization is still returned, flagged with `organizationActive=false`, rather than hidden. |
| `organizationMembershipsExcludeOtherUsers` | The query is scoped to the requested user. |
| `organizationMembershipsAreEmptyForAUserInNoOrganization` | No memberships yields an empty list, not a null. |
| `projectMembershipsCarryTheProjectAndTheOrganization` | `findProjectMembershipsForUser` projects the project name, its organization's name and `active` flag, and the membership's `memberType`. |
| `projectMembershipsExcludeADeactivatedProject` | A membership in a logically deleted project is left out entirely — unlike a suspended organization, a deleted project is not something the detail should offer. |
| `projectMembershipsIncludeAProjectOfASuspendedOrganizationMarked` | A project of a suspended organization is returned with `organizationActive=false`. |
| `projectMembershipsExcludeOtherUsers` | The query is scoped to the requested user. |
| `projectMembershipsAreEmptyForAUserInNoProject` | No memberships yields an empty list. |
| `organizationMembershipsAreEmptyForAUserInNoOrganization` | organization Memberships Are Empty For AUser In No Organization. |
| `organizationMembershipsExcludeOtherUsers` | organization Memberships Exclude Other Users. |
| `projectMembershipsExcludeADeactivatedProject` | project Memberships Exclude ADeactivated Project. |
| `projectMembershipsExcludeOtherUsers` | project Memberships Exclude Other Users. |
| `projectMembershipsAreEmptyForAUserInNoProject` | project Memberships Are Empty For AUser In No Project. |
| `organizationMembershipsIncludeASuspendedOrganizationMarked` | organization Memberships Include ASuspended Organization Marked. |
| `projectMembershipsCarryTheProjectAndTheOrganization` | project Memberships Carry The Project And The Organization. |
| `organizationMembershipsCarryTheOrganizationAndTheRole` | organization Memberships Carry The Organization And The Role. |
| `projectMembershipsIncludeAProjectOfASuspendedOrganizationMarked` | project Memberships Include AProject Of ASuspended Organization Marked. |

## `user.controller.UserControllerTest` — Web

`PATCH /users/me/password`, through the real `SecurityConfig`/`AuthFilter`
chain; `UserService` is mocked.

| Case | Verifies |
| --- | --- |
| `changeOwnPasswordUsesTheCallerFromTheSession` | A valid request answers `200` `Password changed`, and the service is called with **the user id from the token** — the body carries no account, so the endpoint cannot be aimed at someone else. |
| `changeOwnPasswordRejectsWrongCurrentPasswordWith400` | When the service refuses the current password → `400` with `Current password is incorrect`. |
| `changeOwnPasswordRejectsShortNewPasswordWithValidationError` | A `newPassword` under 8 characters → `400` `Validation error` with the per-field message; the service is never called. |
| `changeOwnPasswordRequiresAuthenticationWith401` | The same call without a bearer token → `401`; the service is never reached. |
| `updateOwnProfileUsesTheCallerFromTheSession` | `PATCH /users/me` → `200` with the updated summary, and the service is called with the id from the token, not from the body. |
| `updateOwnProfileAcceptsABodyWithOnlyOneField` | A body carrying only `firstName` is accepted: the optional fields are not rejected by validation. |
| `updateOwnProfileRejectsATooShortNameWithValidationError` | A one-character first name → `400` from `@Size`; the service is never reached. |
| `updateOwnProfilePropagatesAnEmptyPatchWith400` | When the service refuses a body with every field null → `400` carrying its message. |
| `updateOwnProfileRequiresAuthenticationWith401` | `PATCH /users/me` without a token → `401`; the service is never reached. |
| `listUsersReturnsTheProjection` | `GET /users` → `200` with the mail, active flag and organization count in the envelope. |
| `listUsersPropagatesForbiddenWith403` | A non-admin caller → `403`. |
| `listUsersRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `searchUserReturnsTheMinimalProjection` | `GET /users/search?mail=…` → `200`, and the payload carries no `platformRole` and no `active`. |
| `searchUserAnswers404WithAFixedMessage` | A miss is a `404` with `User not found`. |
| `searchUserPropagatesForbiddenWith403` | A caller who owns nothing → `403`. |
| `searchUserRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `updateRoleReturnsTheUpdatedUser` | `PATCH /users/{id}/role` → `200` with the new role in the summary. |
| `updateRoleRejectsANullRoleWithValidationError` | An empty body → `400` from `@NotNull`; the service is never reached. |
| `updateRolePropagatesTheLastAdminRefusalWith400` | The last-admin refusal reaches the client as `400` with its message. |
| `updateRolePropagatesForbiddenWith403` | A non-admin caller → `403`. |
| `updateRoleRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `deleteUserReturnsOk` | `DELETE /users/{id}` → `200` `User deleted`, and the service is asked to deactivate that id. |
| `deleteUserPropagatesTheLastAdminRefusalWith400` | The last-admin refusal reaches the client as `400`. |
| `deleteUserRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `reactivateUserReturnsOk` | `POST /users/{id}/reactivate` → `200` `User reactivated`, and the service is asked to reactivate that id. |
| `reactivateUserRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `getUserDetailReturnsTheFullDetail` | `GET /users/{id}` → `200` with the identity fields, `active`, and both membership lists (`organizations[].organizationName`, `projects`) in the envelope. |
| `getUserDetailAnswers404WithAFixedMessage` | An unknown id → `404` `User not found` — the same message whatever the id, so the endpoint does not confirm which addresses exist. |
| `getUserDetailPropagatesForbiddenWith403` | A non-admin caller → `403`. |
| `getUserDetailRequiresAuthenticationWith401` | No token → `401`; `UserService.getDetail` is never called. |
| `getUserDetailPropagatesForbiddenWith403` | get User Detail Propagates Forbidden With403. |
| `getUserDetailReturnsTheFullDetail` | get User Detail Returns The Full Detail. |
| `getUserDetailAnswers404WithAFixedMessage` | get User Detail Answers404 With AFixed Message. |
| `getUserDetailRequiresAuthenticationWith401` | get User Detail Requires Authentication With401. |

## `organization.service.OrganizationBulkRegistrationServiceTest` — Unit

CSV-driven bulk registration of users **into an organization** (creates the
account and adds it as a member).

| Case | Verifies |
| --- | --- |
| `registersValidRowsAndAddsThemAsMembers` | Valid rows create users, add a membership each, and the returned `credentialsCsv` holds `mail,password` with lower-cased emails. |
| `defaultsToMemberWhenNoRoleColumn` | With no `role` column, members are created as `MEMBER`. |
| `assignsMemberTypeFromRoleColumn` | A `role` column value (`OWNER`) sets the membership type. |
| `rejectsInvalidRoleValueAsFailure` | An unknown role value is reported as a per-row failure; the user is not created. |
| `collectsInvalidRowsAsFailuresWithoutAborting` | Missing/invalid email and too-short name rows are reported as failures (line + reason) without stopping the batch. |
| `rejectsDuplicateEmailWithinFile` | A second occurrence of the same email in the file is rejected as a duplicate. |
| `propagatesRegistrationFailuresPerRow` | A per-row registration exception (e.g. already registered) becomes a failure entry, not a batch abort. |
| `acceptsHeaderAliasesAndAnyColumnOrder` | Header aliases (`Email`/`Nombre`/`Apellido`) and arbitrary column order are resolved. |
| `throwsWhenRequiredHeaderColumnMissing` | A header missing a required column throws, naming the missing column. |
| `throwsWhenFileIsEmpty` | Empty content throws `IllegalArgumentException`. |
| `rejectsUnknownOrganization` | An unknown organization id throws before any user is created. |
| `rejectsWhenCallerNotAuthorized` | A `ForbiddenException` from the authorization check aborts the batch; nothing is created or saved. |
| `registerRejectsADeactivatedOrganization` | No batch can be loaded into a deactivated organization. |

## `organization.service.OrganizationServiceTest` — Unit

Organization creation and member addition (with role/permission derivation).

| Case | Verifies |
| --- | --- |
| `createOrganizationPersistsAndReturnsOrganization` | Creating an organization persists it and returns it with the given name. |
| `listForCurrentUserReturnsAllForAdmin` | A platform ADMIN gets every organization through `findSummaries`, without the member-scoped query running. |
| `listForCurrentUserReturnsMembershipsForRegularUser` | A regular user lists only the organizations they are a member of. |
| `listForCurrentUserRejectsUnauthenticated` | No authenticated caller → `UnauthorizedException`. |
| `listOwnedByCurrentUserAsksForTheManagePermission` | The service asks for `MANAGE_ORGANIZATION`, not for the OWNER member type. |
| `listOwnedByCurrentUserIsEmptyForSomebodyWhoOwnsNothing` | A plain member gets an empty list rather than an error. |
| `listOwnedByCurrentUserRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `updateOrganizationRenamesIt` | A platform ADMIN renames the organization and the new name comes back in the DTO. |
| `updateOrganizationAnswersWithTheRealMemberCount` | The response carries the count from `countByOrganizationId`, not the `0L` the controller's entity mapper would have produced. |
| `updateOrganizationRejectsANullBody` | A null DTO throws before the organization is looked up. |
| `updateOrganizationRejectsUnknownOrganization` | An unknown id throws `IllegalArgumentException`; nothing is saved. |
| `updateOrganizationRejectsAnOrganizationOwnerWith403` | An OWNER is refused: renaming is platform-admin only, symmetric with creating. |
| `updateOrganizationRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `addMemberRejectsADeactivatedOrganization` | No member can be added to a deactivated organization. |
| `registerUserInOrganizationRejectsADeactivatedOrganization` | No account can be created inside a deactivated organization. |
| `removeMemberRejectsADeactivatedOrganization` | No membership can be removed from a deactivated organization. |
| `listMembersRejectsADeactivatedOrganization` | Its roster is not readable either. |
| `updateOrganizationRejectsADeactivatedOrganization` | It cannot be renamed while deactivated. |
| `deactivateOrganizationDeactivatesTheRowOnly` | Only the organization row flips; its projects and members keep `active = true`, which is what makes reactivation lossless. |
| `deactivateOrganizationIsIdempotent` | Deactivating an already inactive organization saves nothing. |
| `deactivateOrganizationRejectsUnknownOrganization` | An unknown id throws `IllegalArgumentException`. |
| `deactivateOrganizationRejectsANonAdminCallerWith403` | An organization OWNER is refused: platform admins only. |
| `deactivateOrganizationRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `reactivateOrganizationBringsItBack` | A deactivated organization is activated and saved. |
| `reactivateOrganizationIsIdempotent` | Reactivating an active organization saves nothing. |
| `reactivateOrganizationRejectsANonAdminCallerWith403` | An OWNER is refused. |
| `reactivateOrganizationRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `createOrganizationRejectsRegularUserWith403` | A non-admin caller gets `ForbiddenException`; nothing is saved. |
| `createOrganizationRejectsUnauthenticated` | No session throws `UnauthorizedException`; nothing is saved. |
| `addMemberAllowsOrganizationOwner` | A member holding `MANAGE_ORGANIZATION` adds another user; the membership is persisted. |
| `addMemberRejectsPlainMemberWith403` | A member with only `VIEW_ORGANIZATION` gets `ForbiddenException`; the user is never looked up and nothing is saved. |
| `addMemberRejectsUnauthenticated` | No session throws `UnauthorizedException` before the user lookup; nothing is saved. |
| `addMemberGrantsOwnerFullPermissions` | Adding a member with `memberType=OWNER` creates a role with both `MANAGE_ORGANIZATION` and `VIEW_ORGANIZATION`. |
| `addMemberGrantsMemberViewOnlyPermission` | Adding a member with `memberType=MEMBER` creates a role with only `VIEW_ORGANIZATION`. |
| `addMemberRejectsUnknownOrganization` | An unknown organization id throws `IllegalArgumentException` before the user is looked up or anything is saved. |
| `addMemberRejectsUnknownUser` | An unknown user id throws `IllegalArgumentException`; nothing is saved. |
| `addMemberRejectsDuplicateMembership` | Adding a user already in the organization throws `IllegalArgumentException`; nothing is saved. |
| `registerUserInOrganizationAllowsPlatformAdmin` | A platform ADMIN caller can register a new user into any organization as a `MEMBER`. |
| `registerUserInOrganizationAllowsOrganizationOwner` | A caller who is an org member with `MANAGE_ORGANIZATION` can register a new user into that organization. |
| `registerUserInOrganizationRejectsNonOwnerMemberWith403` | A member without `MANAGE_ORGANIZATION` gets `ForbiddenException`; no user is created or saved. |
| `registerUserInOrganizationRejectsUnauthenticatedCaller` | No authenticated caller → `UnauthorizedException`; no user is created. |
| `registerUserInOrganizationPropagatesJobTitle` | The request's `jobTitle` reaches `UserService.register` on the built `RegisterRequestDTO`. |
| `listMembersReturnsMembersForPlatformAdmin` | A platform ADMIN lists the members of any organization. |
| `listMembersAllowsAnyMemberOfTheOrganization` | A plain member (no `MANAGE_ORGANIZATION`) can still list the members. |
| `listMembersRejectsNonMemberWith403` | A caller who is not a member gets `ForbiddenException`; the members are never queried. |
| `listMembersRejectsUnknownOrganization` | An unknown organization id throws `IllegalArgumentException`; the members are never queried. |
| `removeMemberDeletesTheMembership` | The membership row is deleted and drops out of `Organization.members`. |
| `removeMemberAlsoRemovesTheirProjectMembershipsInThatOrganization` | The user's `ProjectMember` rows in that organization's projects go with it, loaded and passed to `deleteAll` so the roles cascade. |
| `removeMemberTouchesNoProjectMembershipWhenTheUserIsInNone` | With no project memberships to remove, `deleteAll` is never called. |
| `removeMemberAllowsAnOwnerToRemoveThemselves` | An OWNER may remove their own membership **as long as another active owner remains** (the case stubs the owner count at 2). Self-removal is not a special path: it goes through the same last-owner guard as removing anybody else. |
| `removeMemberAllowsPlatformAdmin` | A platform ADMIN removes without any membership lookup. |
| `removeMemberRejectsAPlainMemberWith403` | A member holding only `VIEW_ORGANIZATION` is refused; nothing is deleted. |
| `removeMemberRejectsUnauthenticated` | No session throws `UnauthorizedException`; nothing is deleted. |
| `removeMemberRejectsUnknownOrganization` | An unknown organization id throws `IllegalArgumentException`. |
| `removeMemberRejectsUnknownMember` | An unknown member id throws `EntityNotFoundException("Member not found")` — a `404`, not a `400`; nothing is deleted. |
| `removeMemberRejectsAMemberOfAnotherOrganization` | A member id belonging to a different organization raises the same `EntityNotFoundException("Member not found")` as one that does not exist, so an owner cannot probe which membership ids exist elsewhere. |
| `changeMemberRoleDemotesAnOwnerToMember` | Demoting replaces the whole role: one role row survives, with `memberType`/`name` `MEMBER` and `VIEW_ORGANIZATION` as its only permission, and the member is saved. |
| `changeMemberRolePromotesAMemberToOwner` | Promoting gives a single OWNER role carrying both `MANAGE_ORGANIZATION` and `VIEW_ORGANIZATION`. |
| `changeMemberRoleCollapsesSeveralRolesIntoOne` | A membership that somehow holds two roles ends up with exactly one after the change, without the old permissions. |
| `changeMemberRoleToTheCurrentRoleIsANoOp` | Asking for the role the member already has returns the member untouched and saves nothing — so a double click cannot trip the last-owner guard. |
| `changeMemberRoleRejectsAPlainMemberWith403` | A caller holding only `VIEW_ORGANIZATION` is refused before the target membership is even looked up. |
| `changeMemberRoleAllowsAnOrganizationOwner` | An OWNER (via `MANAGE_ORGANIZATION`) may change roles, not just a platform admin. |
| `changeMemberRoleRejectsUnauthenticated` | No session throws `UnauthorizedException`; nothing is saved. |
| `changeMemberRoleRejectsAMemberOfAnotherOrganizationWithTheSameMessageAsAMissingOne` | A foreign membership id and an unknown one both raise `EntityNotFoundException("Member not found")` with the identical message. |
| `changeMemberRoleRejectsADeactivatedOrganization` | A suspended organization is refused before the membership is looked up. |
| `changeMemberRoleRejectsAMissingRole` | A null `memberType` throws before anything is read or written. |
| `changeMemberRoleLeavesProjectMembershipsAlone` | Changing an organization role touches `ProjectMemberRepository` not at all: the two role scopes are independent. |
| `demotedOwnerLosesManageUsersAndPromotedMemberGainsIt` | End-to-end on the permission derived from the role: before the swap the owner passes `assertCanManageUsers` and the plain member does not; after demoting one and promoting the other, exactly the reverse holds. |
| `removeMemberRefusesToRemoveTheLastActiveOwner` | With one active owner, removing them throws `IllegalArgumentException("The organization would be left without an owner")`, the membership stays in `Organization.members`, nothing is deleted and the project repository is never touched. |
| `removeMemberAllowsRemovingAnOwnerWhenAnotherOneRemains` | With two active owners, removing one goes through and it drops out of `Organization.members`. |
| `removeMemberRefusesTheLastActiveOwnerEvenForAPlatformAdmin` | The guard is not a permission check: a platform ADMIN — who skips the membership lookup entirely — still gets the refusal. |
| `removeMemberDoesNotApplyTheOwnerGuardToAnOwnerWhoseUserIsInactive` | An owner whose account is deactivated does not count, so the owner count is never even queried and the removal proceeds. |
| `removeMemberRefusesWhenAProjectWouldBeLeftWithoutAnAdministrator` | When `countProjectsLeftWithoutAdmin` reports one, the whole removal is refused with `A project in this organization would be left without an administrator`: the membership survives and **no** project membership is loaded or deleted. The cascade is rejected, not trimmed. |
| `removeMemberRemovesTheMembershipWhenEveryProjectKeepsAnAdministrator` | With that count at zero, the project memberships are deleted through `deleteAll` and then the organization membership. |
| `changeMemberRoleRefusesToDemoteTheLastActiveOwner` | The same guard on the demotion path: the role is unchanged and nothing is saved. |
| `changeMemberRoleAllowsDemotingAnOwnerWhenAnotherOneRemains` | With a second active owner the demotion goes through and is saved. |
| `changeMemberRolePromotesAMemberWithoutConsultingTheOwnerCount` | A promotion cannot empty the role, so the count query is never run. |
| `demotedOwnerLosesManageUsersAndPromotedMemberGainsIt` | demoted Owner Loses Manage Users And Promoted Member Gains It. |
| `changeMemberRoleToTheCurrentRoleIsANoOp` | change Member Role To The Current Role Is ANo Op. |
| `changeMemberRoleRejectsAPlainMemberWith403` | change Member Role Rejects APlain Member With403. |
| `changeMemberRoleRejectsUnauthenticated` | change Member Role Rejects Unauthenticated. |
| `changeMemberRoleRejectsAMissingRole` | change Member Role Rejects AMissing Role. |
| `changeMemberRoleRejectsADeactivatedOrganization` | change Member Role Rejects ADeactivated Organization. |
| `changeMemberRoleLeavesProjectMembershipsAlone` | change Member Role Leaves Project Memberships Alone. |
| `changeMemberRoleCollapsesSeveralRolesIntoOne` | change Member Role Collapses Several Roles Into One. |
| `changeMemberRoleRejectsAMemberOfAnotherOrganizationWithTheSameMessageAsAMissingOne` | change Member Role Rejects AMember Of Another Organization With The Same Message As AMissing One. |
| `changeMemberRolePromotesAMemberToOwner` | change Member Role Promotes AMember To Owner. |
| `changeMemberRoleDemotesAnOwnerToMember` | change Member Role Demotes An Owner To Member. |
| `changeMemberRoleAllowsAnOrganizationOwner` | change Member Role Allows An Organization Owner. |

## `organization.repository.OrganizationRepositoryTest` — Data

The two projections behind `GET /organizations`, added when the list gained a
`memberCount`. The correlated subquery was mutation-checked: decorrelating it
turns two cases red.

**Test emails are prefixed `orgs-`** so they cannot collide with the
`admin@enerscope.org` row that `AdminSeeder` commits into the shared H2 database
— see the note on `user.repository.UserRepositoryTest`.

| Case | Verifies |
| --- | --- |
| `summariesCountTheMembersOfEachOrganization` | Two members count as two, and an organization with none reports zero. |
| `summariesDoNotCountAnotherOrganizationsMembers` | The subquery is correlated: two organizations with one member each report 1 and 1, not 2. |
| `summariesComeBackOrderedByName` | Rows arrive alphabetically, which is what the table reads. |
| `memberSummariesReturnOnlyTheOrganizationsTheUserBelongsTo` | The member-scoped query leaves out organizations the user is not in. |
| `memberSummariesStillCarryTheFullMemberCount` | A member sees the organization's whole headcount, not just their own row. |
| `memberSummariesAreEmptyForAUserInNoOrganization` | A user in no organization gets nothing, not everything. |
| `summariesIncludeDeactivatedOrganizations` | The admin list does **not** filter on `active` — a platform admin has to see suspended organizations to reactivate them. A mutation adding the filter turns this red. |
| `memberSummariesExcludeDeactivatedOrganizations` | The member-scoped list does filter: a regular user stops seeing a deactivated organization. |
| `existsByIdAndActiveTrueIsTrueWhileActive` | The baseline for the entry-point check. |
| `existsByIdAndActiveTrueIsFalseOnceDeactivated` | Which is what makes `listMembers` refuse. |
| `findByIdAndActiveTrueSkipsADeactivatedOrganization` | The resolution used by the five write entry points returns empty for a deactivated organization. |
| `ownedReturnsTheOrganizationsWhereTheCallerHoldsManage` | The baseline for the sidebar entry and the route guard. |
| `ownedExcludesOrganizationsWhereTheCallerIsOnlyAMember` | A `MEMBER` owns nothing, which is what keeps the sidebar entry locked for them. |
| `ownedExcludesDeactivatedOrganizations` | A deactivated organization drops out, so its owner stops being offered a page they cannot write to. |
| `ownedExcludesOrganizationsOwnedBySomebodyElse` | Correlated on the caller: two owners of different organizations see one each. |
| `ownsAnyActiveOrganizationFollowsTheSameRule` | The boolean behind the user lookup agrees with the list: true for the owner, false for the plain member. |

## `organization.repository.OrganizationMemberRepositoryTest` — Data

The two `@Query` methods on `OrganizationMemberRepository` that only a database
can answer: `findProjectMemberCandidates` (who in the organization may still be
added to a given project) and `countOwnersByOrganization` (the count behind the
last-owner guard). The candidate query is a constructor projection, so the cases
also pin that it exposes nothing but id, first name, last name and mail.

| Case | Verifies |
| --- | --- |
| `returnsTheActiveOrganizationMembersWhoAreNotOnTheProject` | The baseline: an active organization member who is not on the project is a candidate, projected with their four fields. |
| `excludesUsersAlreadyOnTheProject` | Somebody already on the project is not offered again. |
| `excludesUsersWhoseProjectMembershipIsInactive` | A deactivated project membership still excludes them — consistent with `existsByProjectIdAndUserId`, which would refuse the re-add anyway. |
| `excludesUsersWhoAreOnlyInAnotherOrganization` | Candidates come from the project's own organization only. |
| `excludesInactiveOrganizationMemberships` | A deactivated organization membership is not a candidate. |
| `excludesInactiveUsers` | Neither is a deactivated account. |
| `returnsNothingForAnotherProjectsOrganization` | Asking with an organization the project does not belong to yields nothing, rather than leaking the other organization's roster. |
| `keepsUsersWhoAreOnlyOnAnotherProjectOfTheSameOrganization` | Being on a *different* project of the same organization does not disqualify anyone. |
| `filtersByFirstNameLastNameOrMailIgnoringCase` | `%mart%` matches a first name, a last name and a mail, case-insensitively, and leaves the non-matching member out. |
| `treatsPercentAndUnderscoreInTheEscapedPatternAsLiterals` | With the pattern `ProjectService` builds (`%ana\_maria%`), the `_` is a literal: `Ana_Maria` matches and `AnaXMaria` does not. |
| `ordersByLastNameThenFirstName` | The rows come back ordered by last name, then first name. |
| `countsTheActiveOwnersOfAnOrganization` | Two OWNER memberships count as 2. |
| `doesNotCountPlainMembersAsOwners` | A MEMBER does not count towards the owner floor. |
| `doesNotCountAnOwnerWhoseUserIsInactive` | A deactivated account does not keep a seat warm. |
| `doesNotCountAnOwnerWhoseMembershipIsInactive` | Neither does a deactivated membership — both flags are required. |
| `doesNotCountTheOwnersOfAnotherOrganization` | The count is scoped to one organization. |
| `countsZeroOwnersForAnOrganizationWithNone` | An organization with only members reports 0 rather than failing. |

## `organization.controller.OrganizationControllerTest` — Web

Exercises `OrganizationController` through the real `SecurityConfig`/
`AuthFilter` chain (a valid Bearer token is required on every request, like
every non-`/auth` route); `OrganizationService` and
`OrganizationBulkRegistrationService` are mocked.

| Case | Verifies |
| --- | --- |
| `listOrganizationsReturnsList` | `GET /organizations` → `200` with the list of organizations (`data[0].name`). |
| `updateOrganizationReturnsTheUpdatedOrganization` | `PATCH /organizations/{id}` → `200` with the new name and the real `memberCount`. |
| `updateOrganizationRejectsABlankNameWithValidationError` | A whitespace-only name → `400` from `@NotBlank`; the service is never reached. |
| `updateOrganizationPropagatesForbiddenWith403` | A caller who is not a platform admin → `403`. |
| `updateOrganizationRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `listMembersReturnsMembersWithIdentityFields` | `GET /organizations/{id}/members` → `200` with `firstName`, `lastName`, `jobTitle`, `active` and `memberType` per row. |
| `listMembersRequiresAuthenticationWith401` | Without a Bearer token → `401`; `OrganizationService.listMembers` is never called. |
| `listMembersPropagatesForbiddenWith403` | When the service throws `ForbiddenException` (caller is not a member) → `403`, `success=false`. |
| `createOrganizationReturnsCreatedOrganization` | `POST /organizations` with a valid body → `201` and an envelope with `success=true`, message `Organization created`, and the created organization's name. |
| `createOrganizationRejectsBlankNameWithValidationError` | Blank `name` → `400` `Validation error`; `OrganizationService.createOrganization` is never called. |
| `addMemberReturnsCreatedMember` | `POST /organizations/{id}/members` with a valid body → `201` with the member's `memberType` and `userMail`. |
| `addMemberRejectsUnknownOrganizationWith400` | When the service throws for an unknown organization → `400` with the domain error message. |
| `addMemberRejectsInvalidBodyWithValidationError` | Missing `userId`/`memberType` → `400` `Validation error`; the service is never called. |
| `registerUserReturnsCreatedMember` | `POST /organizations/{id}/users` with a valid body → `201` `User registered into organization` with the member's `memberType`. |
| `registerUserPropagatesForbiddenWith403` | When the service throws `ForbiddenException` → `403`, `success=false`. |
| `registerUserRejectsInvalidBodyWithValidationError` | Invalid email → `400` `Validation error`; the service is never called. |
| `createOrganizationPropagatesForbiddenWith403` | When the service refuses a non-admin → `403` with `Only platform admins can create organizations`. |
| `createOrganizationRequiresAuthenticationWith401` | `POST /organizations` without a bearer token → `401`; the service is never reached. |
| `addMemberPropagatesForbiddenWith403` | When the service refuses the caller → `403` with the domain message. |
| `addMemberRequiresAuthenticationWith401` | `POST /organizations/{id}/members` without a token → `401`; the service is never reached. |
| `bulkRegisterUsersReturnsResultSummary` | `POST /organizations/{id}/users/bulk` with a CSV file → `200` with the result summary (`total`/`created`) and `credentialsCsv`. |
| `bulkRegisterUsersPropagatesForbiddenWith403` | When the bulk service throws `ForbiddenException` → `403`, `success=false`. |
| `deleteOrganizationReturnsOk` | `DELETE /organizations/{id}` → `200` `Organization deleted`, and the service is asked to deactivate that id. |
| `deleteOrganizationPropagatesForbiddenWith403` | A caller who is not a platform admin → `403`. |
| `deleteOrganizationRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `reactivateOrganizationReturnsOk` | `POST /organizations/{id}/reactivate` → `200` `Organization reactivated`. |
| `reactivateOrganizationRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `listOwnedOrganizationsReturnsThem` | `GET /organizations/owned` → `200` `Owned organizations`. |
| `listOwnedOrganizationsRequiresAuthenticationWith401` | No token → `401`; the service is never reached. |
| `removeMemberReturnsOk` | `DELETE /organizations/{id}/members/{memberId}` → `200` `Member removed`, and the service is asked to remove that pair. |
| `removeMemberPropagatesForbiddenWith403` | When the service refuses the caller → `403` with the domain message. |
| `removeMemberRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `removeMemberAnswers404ForAnUnknownMember` | An unknown member now answers `404` `Member not found` (it used to be documented as `400`; the service throws `EntityNotFoundException`, which `GlobalExceptionHandler` maps to `404`). |
| `removeMemberRejectsALastOwnerRemovalWith400` | The last-owner refusal reaches the client as `400` with `The organization would be left without an owner`, verbatim. |
| `removeMemberRejectsACascadeThatOrphansAProjectWith400` | The cascade refusal reaches the client as `400` with `A project in this organization would be left without an administrator`, verbatim. |
| `changeMemberRoleReturnsTheUpdatedMember` | `PATCH /organizations/{id}/members/{memberId}` → `200` `Member role updated` with the new `memberType` and its derived `permissions`. |
| `changeMemberRoleRejectsAMissingRoleWith400` | An empty body → `400`; the service is never called. |
| `changeMemberRoleRejectsAnUnknownRoleWith400` | `"memberType":"ADMIN"` — a *project* role, not an organization one — is rejected at deserialization with `400`; the service is never called. |
| `changeMemberRolePropagatesForbiddenWith403` | A caller without `MANAGE_ORGANIZATION` → `403` with `You are not allowed to manage users in this organization`. |
| `changeMemberRolePropagatesAnUnknownMemberWith404` | An unknown member → `404` `Member not found`. |
| `changeMemberRoleRequiresAuthenticationWith401` | No token → `401`; the service is never called. |
| `changeMemberRoleRejectsAMissingRoleWith400` | change Member Role Rejects AMissing Role With400. |
| `changeMemberRoleReturnsTheUpdatedMember` | change Member Role Returns The Updated Member. |
| `changeMemberRoleRejectsAnUnknownRoleWith400` | change Member Role Rejects An Unknown Role With400. |
| `changeMemberRoleRequiresAuthenticationWith401` | change Member Role Requires Authentication With401. |
| `changeMemberRolePropagatesAnUnknownMemberWith404` | change Member Role Propagates An Unknown Member With404. |
| `removeMemberAnswers404ForAnUnknownMember` | remove Member Answers404 For An Unknown Member. |
| `changeMemberRolePropagatesForbiddenWith403` | change Member Role Propagates Forbidden With403. |

## `project.repository.ProjectMemberRepositoryTest` — Data

The queries on `ProjectMemberRepository` that no mocked test can answer:
`findByUserInOrganization` (a user's project memberships inside one
organization, so removing their organization membership can take them with it),
`findByIdAndProjectId`, the two counting queries behind the last-administrator
guards, and the role cascade. The filters and the delete cascade of the first
group were checked by mutation: each one turns a case red when removed.

| Case | Verifies |
| --- | --- |
| `findsTheMembershipsOfAUserInTheProjectsOfOneOrganization` | Two projects of the same organization both come back. |
| `excludesProjectsOfAnotherOrganization` | A membership in another organization's project is left out — this is what keeps the removal from reaching across organizations. |
| `excludesTheMembershipsOfOtherUsers` | Two members on the same project resolve to one row for the user asked about. |
| `returnsEmptyWhenTheUserIsInNoProjectOfThatOrganization` | No project in that organization means no rows, not every row. |
| `deletingTheFoundMembershipsCascadesToTheirRoles` | `deleteAll` over the loaded entities removes their `ProjectMemberRole` rows too. The role count is asserted as 1 **before** the delete: without that, the case passes even with the cascade removed, because the role would never have been written. |
| `findsAMembershipByIdWithinItsProject` | `findByIdAndProjectId` finds the membership when the project matches. |
| `doesNotFindAMembershipThroughAnotherProject` | The same membership id under a different project, and an unknown id under the right project, both come back empty — the project in the path is part of the key, so a member of project A cannot be touched through project B. |
| `countsTheActiveAdminsOfAProject` | `countAdminsByProject` counts two ADMIN memberships as 2. |
| `doesNotCountEditorsAsAdmins` | An EDITOR does not count towards the administrator floor. |
| `doesNotCountAnAdminWhoseMembershipIsInactive` | A deactivated membership does not keep a seat warm. |
| `doesNotCountAnAdminWhoseUserIsInactive` | Neither does an ADMIN whose account was deactivated — both flags are required. |
| `doesNotCountTheAdminsOfAnotherProject` | The count is scoped to one project. |
| `countsZeroAdminsForAProjectWithNone` | A project with only editors reports 0 rather than failing. |
| `anInactiveMembershipStillCountsAsExistingForTheUniquePair` | `existsByProjectIdAndUserId` is true for a deactivated membership, which is why re-adding that person is refused with `User is already a member of this project` instead of violating the unique constraint. |
| `replacingTheRoleOfAPersistedMemberLeavesASingleRoleWithTheNewPermissions` | `changeRole` on a loaded entity replaces the role instead of adding a second one: one row survives, with the new `memberType` and the new permission set. |
| `deletingAMembershipByEntityCascadesToItsRoles` | Deleting one membership entity removes its role row (orphan removal), checked with the role count asserted before the delete. |
| `countsTheProjectWhereTheUserIsTheOnlyAdmin` | `countProjectsLeftWithoutAdmin` reports 1 for a project where the user is the only ADMIN and the other member is an EDITOR. |
| `doesNotCountAProjectThatKeepsAnotherActiveAdmin` | A project with a second active ADMIN is not at risk, so it is not counted. |
| `doesNotCountAProjectWhereTheUserIsOnlyAnEditor` | Leaving does not endanger a project the user does not administer. |
| `doesNotCountTheProjectsOfAnotherOrganization` | The query is scoped to the organization being left. |
| `countsOnlyTheProjectsThatWouldBeLeftWithoutAnAdmin` | The combined case: of five projects (two sole-admin, one shared, one where the user is an editor, one in another organization) exactly the two sole-admin ones are counted. |
| `doesNotFindAMembershipThroughAnotherProject` | does Not Find AMembership Through Another Project. |
| `doesNotCountTheAdminsOfAnotherProject` | does Not Count The Admins Of Another Project. |
| `anInactiveMembershipStillCountsAsExistingForTheUniquePair` | an Inactive Membership Still Counts As Existing For The Unique Pair. |
| `doesNotCountEditorsAsAdmins` | does Not Count Editors As Admins. |
| `countsTheActiveAdminsOfAProject` | counts The Active Admins Of AProject. |
| `doesNotCountAnAdminWhoseUserIsInactive` | does Not Count An Admin Whose User Is Inactive. |
| `doesNotCountAnAdminWhoseMembershipIsInactive` | does Not Count An Admin Whose Membership Is Inactive. |
| `findsAMembershipByIdWithinItsProject` | finds AMembership By Id Within Its Project. |
| `deletingAMembershipByEntityCascadesToItsRoles` | deleting AMembership By Entity Cascades To Its Roles. |
| `countsZeroAdminsForAProjectWithNone` | counts Zero Admins For AProject With None. |
| `replacingTheRoleOfAPersistedMemberLeavesASingleRoleWithTheNewPermissions` | replacing The Role Of APersisted Member Leaves ASingle Role With The New Permissions. |

## `project.service.ProjectAccessGuardTest` — Unit

The single home of the project and version authorization rules, shared by
`ProjectService` and `VersionService`. Every check is exercised in its three
states: authorized, authenticated but not allowed (`403`), and no session
(`401`).

| Case | Verifies |
| --- | --- |
| `assertCanViewProjectAllowsAnyMember` | Any member of the project may read it, regardless of permissions. |
| `assertCanViewProjectAllowsPlatformAdminWithoutMembershipLookup` | A platform ADMIN passes without the membership repository being touched at all. |
| `assertCanViewProjectRejectsNonMemberWith403` | A user outside the project gets `ForbiddenException`. |
| `assertCanViewProjectRejectsUnauthenticated` | No session throws `UnauthorizedException` before any lookup. |
| `assertCanEditProjectAllowsMemberWithEditPermission` | A member holding `EDIT_PROJECT` may change the project's contents. |
| `assertCanEditProjectAllowsPlatformAdminWithoutMembershipLookup` | A platform ADMIN passes without a membership lookup. |
| `assertCanEditProjectRejectsMemberWithoutEditPermissionWith403` | A member whose role lacks `EDIT_PROJECT` gets `ForbiddenException` — the guard reads permissions, never the member type label. |
| `assertCanEditProjectRejectsNonMemberWith403` | A user with no membership row gets `ForbiddenException`. |
| `assertCanEditProjectRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `assertCanManageProjectAllowsProjectAdmin` | A member holding `MANAGE_PROJECT` may administer the project. |
| `assertCanManageProjectRejectsEditorWith403` | An EDITOR (`EDIT_PROJECT` but no `MANAGE_PROJECT`) gets `ForbiddenException`. |
| `assertCanManageProjectRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `assertCanEditVersionResolvesOwningProjectAndAllowsEditor` | A version id is resolved to its owning project and the edit rule is applied there. |
| `assertCanEditVersionRejectsCallerOutsideOwningProjectWith403` | A caller with no membership in the owning project gets `ForbiddenException`. |
| `assertCanEditVersionRejectsVersionWithoutOwningProjectWith403` | A version attached to no project is refused without any membership lookup: no membership could grant access to it. |
| `assertCanEditVersionAllowsPlatformAdminOnOrphanVersion` | A platform ADMIN may edit a detached version — the admin shortcut runs before the project is resolved, so versions created through `POST /version/createtest` stay reachable by whoever may create them. |
| `assertCanEditVersionRejectsUnauthenticated` | No session throws `UnauthorizedException` before the project is resolved. |
| `assertCanViewVersionAllowsAnyMemberOfOwningProject` | Reading a version only requires membership in its owning project. |
| `assertCanViewVersionRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `assertIsPlatformAdminAllowsPlatformAdmin` | A platform ADMIN passes the platform-level check. |
| `assertIsPlatformAdminRejectsRegularUserWith403` | A regular user gets `ForbiddenException` with the action named in the message. |
| `assertIsPlatformAdminRejectsUnauthenticated` | No session throws `UnauthorizedException`. |
| `assertCanManageProjectDeniesAnAdminAfterTheyAreDemotedToEditor` | The same membership instance passes the manage check as an ADMIN and is refused once `changeRole` has turned it into an EDITOR — the guard reads the role at call time, it does not cache a decision. |
| `assertCanEditProjectStillAllowsAnAdminAfterTheyAreDemotedToEditor` | A demotion from ADMIN to EDITOR keeps `EDIT_PROJECT`, so the demoted member can still edit; only `MANAGE_PROJECT` is lost. |
| `assertCanManageProjectAllowsAnEditorAfterTheyArePromotedToAdmin` | The mirror case: an EDITOR refused the manage check passes it after being promoted to ADMIN. |
| `changingTheRoleDoesNotShareThePermissionSetWithTheDefaults` | `changeRole` copies `ProjectMember.defaultPermissionsFor(...)` instead of storing the returned set, so mutating a member's permissions cannot corrupt the defaults for every other member. |
| `assertCanManageProjectDeniesAnAdminAfterTheyAreDemotedToEditor` | assert Can Manage Project Denies An Admin After They Are Demoted To Editor. |
| `changingTheRoleDoesNotShareThePermissionSetWithTheDefaults` | changing The Role Does Not Share The Permission Set With The Defaults. |
| `assertCanManageProjectAllowsAnEditorAfterTheyArePromotedToAdmin` | assert Can Manage Project Allows An Editor After They Are Promoted To Admin. |
| `assertCanEditProjectStillAllowsAnAdminAfterTheyAreDemotedToEditor` | assert Can Edit Project Still Allows An Admin After They Are Demoted To Editor. |

## `project.service.ProjectServiceTest` — Unit

Project creation and member addition (with role/permission derivation), plus
the authorization each one runs. `ProjectAccessGuard` is wired as a real
collaborator over the same mocked repositories, so the authorization cases here
assert real outcomes; `OrganizationService` is mocked, since its rule has its own
coverage and what matters here is that `createProject` runs it.

| Case | Verifies |
| --- | --- |
| `createProjectPersistsAndLinksToOrganization` | Creating a project persists it linked to the organization and appends it to `Organization.projects`. |
| `createProjectAddsCreatorAsProjectAdmin` | Creating a project puts the caller on it as a member whose role is `ADMIN` with `MANAGE_PROJECT`, `EDIT_PROJECT` and `VIEW_PROJECT` — without it the creator would not see their own project in `GET /projects`. |
| `createProjectRejectsUnauthenticated` | Creating a project with no session throws `UnauthorizedException`; neither the project nor a membership is saved. |
| `createProjectRejectsUnknownOrganization` | An unknown organization id throws `IllegalArgumentException`; the project is never saved. |
| `createProjectChecksCallerBelongsToTargetOrganization` | Creating a project runs the organization membership check with that organization's id and the `create projects in` action. |
| `createProjectRejectsADeactivatedOrganization` | No project can be created inside a deactivated organization. |
| `createProjectRejectsCallerOutsideOrganizationWith403` | A caller who does not belong to the target organization gets `ForbiddenException`; neither the project nor a membership is saved. |
| `addMemberAllowsProjectAdmin` | A project ADMIN (`MANAGE_PROJECT`) adds a member and it is persisted. |
| `addMemberAllowsPlatformAdmin` | A platform ADMIN adds a member without any membership lookup. |
| `addMemberRejectsProjectEditorWith403` | An EDITOR gets `ForbiddenException`; the user is never looked up and nothing is saved. |
| `addMemberRejectsCallerWhoIsNotAMemberWith403` | A user outside the project gets `ForbiddenException`; nothing is saved. |
| `addMemberRejectsUnauthenticated` | No session throws `UnauthorizedException` before the user lookup; nothing is saved. |
| `addMemberGrantsAdminFullPermissions` | Adding a member with `memberType=ADMIN` creates a role with `MANAGE_PROJECT`, `EDIT_PROJECT` and `VIEW_PROJECT`. |
| `addMemberGrantsEditorEditAndViewPermissions` | Adding a member with `memberType=EDITOR` creates a role with only `EDIT_PROJECT` and `VIEW_PROJECT`. |
| `addMemberRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException` before the user is looked up or anything is saved. |
| `addMemberRejectsUnknownUser` | An unknown user id throws `IllegalArgumentException`; nothing is saved. |
| `addMemberRejectsDuplicateMembership` | Adding a user already in the project throws `IllegalArgumentException`; nothing is saved. |
| `listMembersReturnsEveryMemberForPlatformAdmin` | A platform ADMIN reads any project's members without a membership check being run. |
| `listMembersReturnsMembersForProjectMember` | A regular user who is on the project reads its members. |
| `listMembersRejectsCallerWhoIsNotAMember` | A user outside the project gets `ForbiddenException`; the members are never queried. |
| `listMembersRejectsUnauthenticated` | No session throws `UnauthorizedException`; the members are never queried. |
| `listMembersRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException` before any membership work. |
| `listForCurrentUserReturnsEveryProjectForAdmin` | A platform `ADMIN` gets the unrestricted summary query; the membership query is never used. |
| `listForCurrentUserReturnsOnlyMembershipsForRegularUser` | A regular user gets only the projects they are a member of; the unrestricted query is never used. |
| `listForCurrentUserPassesOrganizationFilterThrough` | An `organizationId` is forwarded verbatim to the repository. |
| `listForCurrentUserRejectsUnauthenticated` | No security context → `UnauthorizedException`. |
| `saveVersionReturnsVersionLinkedToProject` | The created version is returned, appended to `Project.versions`, and the project is saved. |
| `saveVersionRejectsCallerWithoutEditPermissionWith403` | Creating a version in a project the caller cannot edit throws `ForbiddenException`; `VersionService` is never called and the project is not saved. |
| `saveVersionRejectsUnauthenticated` | No session throws `UnauthorizedException`; nothing is created. |
| `saveVersionRejectsNullProjectId` | A null project id throws `IllegalArgumentException`; `VersionService` is never called. |
| `saveVersionRejectsNullVersionData` | A null `VersionDTO` throws `IllegalArgumentException`; `VersionService` is never called. |
| `saveVersionRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException`; no version is created and nothing is saved. |
| `updateProjectChangesNameAndDescription` | A project ADMIN patching both fields gets both applied and the project saved. |
| `updateProjectLeavesOutTheFieldsThatAreNull` | A patch carrying only `name` leaves the stored description untouched — the partial semantics of `PATCH`. |
| `updateProjectRejectsAPatchWithEveryFieldNull` | A body with both fields null throws `IllegalArgumentException`; the project is never even looked up. |
| `updateProjectRejectsANullBody` | A null DTO throws `IllegalArgumentException` before any repository call. |
| `updateProjectRejectsABlankName` | A whitespace-only name throws `IllegalArgumentException`; nothing is saved. `@Size(min = 2)` alone would accept two spaces. |
| `updateProjectRejectsABlankDescription` | A whitespace-only description throws `IllegalArgumentException`; nothing is saved. |
| `updateProjectRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException`; nothing is saved. |
| `updateProjectAllowsPlatformAdmin` | A platform ADMIN patches without any membership lookup. |
| `updateProjectRejectsProjectEditorWith403` | A member holding only `EDIT_PROJECT` is refused: renaming is `MANAGE_PROJECT`. The in-memory project keeps its old name and nothing is saved. |
| `updateProjectRejectsUnauthenticated` | No session throws `UnauthorizedException`; nothing is saved. |
| `deactivateProjectDeactivatesTheProject` | A project ADMIN deleting the project clears its `active` flag and saves it. |
| `deactivateProjectCascadesToMembersAndVersions` | Members and versions are deactivated with the project, child versions included — the cascade walks `Project.versions`, not the parent/child tree. |
| `deactivateProjectIsIdempotent` | Deleting an already inactive project is a no-op: nothing is saved a second time. |
| `deactivateProjectRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException`; nothing is saved. |
| `deactivateProjectAllowsPlatformAdmin` | A platform ADMIN deletes without any membership lookup. |
| `deactivateProjectRejectsProjectEditorWith403` | A member holding only `EDIT_PROJECT` is refused; the project stays active and nothing is saved. |
| `deactivateProjectRejectsUnauthenticated` | No session throws `UnauthorizedException`; the project stays active. |
| `addMemberRejectsInactiveProjectBeforeLookingUpTheUser` | A deleted project throws `Project not found` before the candidate is read, so a deleted project cannot be used to probe which user ids exist. |
| `addMemberRejectsAnInactiveUser` | A deactivated account throws `User is not active`; nothing is saved. |
| `addMemberRejectsAUserOutsideTheProjectsOrganizationEvenForAPlatformAdmin` | `User is not a member of the project's organization` applies to a platform ADMIN too: being allowed to act is not being allowed to break the membership prerequisite. |
| `addMemberRejectsAUserWhoseOrganizationMembershipIsInactive` | A deactivated organization membership counts as not belonging. |
| `addMemberCannotReAddAUserWhoseProjectMembershipIsInactive` | A deactivated project membership still answers `User is already a member of this project` — the unique pair survives the logical delete, so the row has to be reactivated, not re-created. |
| `changeMemberRoleDemotesAnAdminReplacingTheWholeRole` | Demoting an ADMIN leaves one role row named `EDITOR` with the editor permissions, and saves it. |
| `changeMemberRolePromotesAnEditorWithoutConsultingTheAdminCount` | A promotion cannot empty the role, so `countAdminsByProject` is never run. |
| `changeMemberRoleIsANoOpWhenTheRoleDoesNotChange` | Asking for the current role saves nothing and never consults the admin count. |
| `changeMemberRoleRefusesToDemoteTheLastActiveAdmin` | With one active administrator, demoting them throws `IllegalArgumentException("The project would be left without an administrator")` and nothing is saved. |
| `changeMemberRoleAllowsDemotingAnAdminWhenAnotherOneRemains` | With a second active administrator the demotion goes through. |
| `changeMemberRoleDoesNotApplyTheGuardToAnAdminThatNoLongerCounts` | A deactivated membership does not count as an administrator, so the guard is skipped and the change is saved. |
| `changeMemberRoleDoesNotApplyTheGuardToAnAdminWhoseUserIsInactive` | Same for an administrator whose account is deactivated. |
| `changeMemberRoleAllowsAProjectAdminCaller` | A project ADMIN may change roles, not only a platform admin. |
| `changeMemberRoleRejectsAProjectEditorCallerWith403` | An EDITOR caller is refused before the target membership is looked up; nothing is saved. |
| `changeMemberRoleRejectsAnOrganizationOwnerWhoIsNotOnTheProjectWith403` | Owning the organization does not grant `MANAGE_PROJECT` on a project you are not a member of. |
| `changeMemberRoleAnswers404ForAMemberOfAnotherProject` | A member id from another project raises `EntityNotFoundException("Member not found")`; the lookup is scoped by project id and `findById` is never used. |
| `changeMemberRoleRejectsAnInactiveProject` | A deleted project throws `Project not found` before the membership is looked up. |
| `changeMemberRoleRejectsAMissingMemberType` | A null DTO and a null `memberType` both throw. |
| `removeMemberDeletesTheMembershipAndDetachesItFromTheProject` | Removing an EDITOR deletes the row, drops it from `Project.members` and never consults the admin count. |
| `removeMemberRefusesToRemoveTheLastActiveAdmin` | With one active administrator the removal throws the same `The project would be left without an administrator` and deletes nothing. |
| `removeMemberAllowsRemovingAnAdminWhenAnotherOneRemains` | With a second active administrator the removal goes through. |
| `removeMemberRefusesTheSelfRemovalOfTheLastAdmin` | The guard is not about who is being removed by whom: the sole administrator cannot walk out either. |
| `removeMemberAllowsAnAdminToLeaveWhenAnotherOneRemains` | An administrator may remove their own membership when another one remains. |
| `removeMemberRejectsAProjectEditorCallerWith403EvenForTheirOwnMembership` | Leaving a project needs `MANAGE_PROJECT`: an EDITOR cannot remove even themselves, which is a deliberate limitation and not an oversight. |
| `removeMemberAnswers404ForAMemberOfAnotherProject` | A member id from another project raises `EntityNotFoundException("Member not found")`; nothing is deleted. |
| `removeMemberRejectsAnInactiveProject` | A deleted project is refused before the membership is looked up. |
| `listMemberCandidatesQueriesTheProjectsOrganizationWithAMatchAllPatternWhenQIsBlank` | A null `q` and a whitespace-only one both become the pattern `%`, against the project's own organization. |
| `listMemberCandidatesLowercasesTrimsAndEscapesTheSearchTerm` | `"  Ja_N%\x "` becomes `%ja\_n\%\\x%`: trimmed, lower-cased, and `_`, `%` and `\` escaped so a user's input cannot act as a LIKE wildcard. |
| `listMemberCandidatesRejectsAProjectEditorWith403` | Listing candidates needs `MANAGE_PROJECT`; the query never runs for an EDITOR. |
| `listMemberCandidatesAllowsAProjectAdmin` | A project ADMIN may list them. |
| `listMemberCandidatesRejectsAnInactiveProject` | A deleted project is refused and the query never runs. |
| `addMemberRejectsAUserWhoseOrganizationMembershipIsInactive` | add Member Rejects AUser Whose Organization Membership Is Inactive. |
| `changeMemberRoleDoesNotApplyTheGuardToAnAdminWhoseUserIsInactive` | change Member Role Does Not Apply The Guard To An Admin Whose User Is Inactive. |
| `changeMemberRoleDemotesAnAdminReplacingTheWholeRole` | change Member Role Demotes An Admin Replacing The Whole Role. |
| `listMemberCandidatesAllowsAProjectAdmin` | list Member Candidates Allows AProject Admin. |
| `removeMemberRejectsAProjectEditorCallerWith403EvenForTheirOwnMembership` | remove Member Rejects AProject Editor Caller With403 Even For Their Own Membership. |
| `addMemberRejectsAnInactiveUser` | add Member Rejects An Inactive User. |
| `changeMemberRoleAnswers404ForAMemberOfAnotherProject` | change Member Role Answers404 For AMember Of Another Project. |
| `changeMemberRoleRefusesToDemoteTheLastActiveAdmin` | change Member Role Refuses To Demote The Last Active Admin. |
| `changeMemberRoleIsANoOpWhenTheRoleDoesNotChange` | change Member Role Is ANo Op When The Role Does Not Change. |
| `addMemberRejectsAUserOutsideTheProjectsOrganizationEvenForAPlatformAdmin` | add Member Rejects AUser Outside The Projects Organization Even For APlatform Admin. |
| `changeMemberRoleRejectsAMissingMemberType` | change Member Role Rejects AMissing Member Type. |
| `removeMemberDeletesTheMembershipAndDetachesItFromTheProject` | remove Member Deletes The Membership And Detaches It From The Project. |
| `addMemberCannotReAddAUserWhoseProjectMembershipIsInactive` | add Member Cannot Re Add AUser Whose Project Membership Is Inactive. |
| `addMemberRejectsInactiveProjectBeforeLookingUpTheUser` | add Member Rejects Inactive Project Before Looking Up The User. |
| `removeMemberAnswers404ForAMemberOfAnotherProject` | remove Member Answers404 For AMember Of Another Project. |
| `saveVersionReturnsCreatedVersionAndLinksItToProject` | save Version Returns Created Version And Links It To Project. |
| `removeMemberRejectsAnInactiveProject` | remove Member Rejects An Inactive Project. |
| `listMemberCandidatesRejectsAnInactiveProject` | list Member Candidates Rejects An Inactive Project. |
| `changeMemberRoleRejectsAnOrganizationOwnerWhoIsNotOnTheProjectWith403` | change Member Role Rejects An Organization Owner Who Is Not On The Project With403. |
| `removeMemberAllowsAnAdminToLeaveWhenAnotherOneRemains` | remove Member Allows An Admin To Leave When Another One Remains. |
| `changeMemberRoleRejectsAnInactiveProject` | change Member Role Rejects An Inactive Project. |
| `changeMemberRolePromotesAnEditorWithoutConsultingTheAdminCount` | change Member Role Promotes An Editor Without Consulting The Admin Count. |
| `changeMemberRoleAllowsDemotingAnAdminWhenAnotherOneRemains` | change Member Role Allows Demoting An Admin When Another One Remains. |
| `removeMemberRefusesToRemoveTheLastActiveAdmin` | remove Member Refuses To Remove The Last Active Admin. |
| `removeMemberAllowsRemovingAnAdminWhenAnotherOneRemains` | remove Member Allows Removing An Admin When Another One Remains. |
| `listMemberCandidatesLowercasesTrimsAndEscapesTheSearchTerm` | list Member Candidates Lowercases Trims And Escapes The Search Term. |
| `changeMemberRoleRejectsAProjectEditorCallerWith403` | change Member Role Rejects AProject Editor Caller With403. |
| `removeMemberRefusesTheSelfRemovalOfTheLastAdmin` | remove Member Refuses The Self Removal Of The Last Admin. |
| `listMemberCandidatesQueriesTheProjectsOrganizationWithAMatchAllPatternWhenQIsBlank` | list Member Candidates Queries The Projects Organization With AMatch All Pattern When QIs Blank. |
| `changeMemberRoleDoesNotApplyTheGuardToAnAdminThatNoLongerCounts` | change Member Role Does Not Apply The Guard To An Admin That No Longer Counts. |
| `changeMemberRoleAllowsAProjectAdminCaller` | change Member Role Allows AProject Admin Caller. |
| `listMemberCandidatesRejectsAProjectEditorWith403` | list Member Candidates Rejects AProject Editor With403. |

## `project.controller.ProjectControllerTest` — Web

Exercises `ProjectController` through the real `SecurityConfig`/`AuthFilter`
chain (a valid Bearer token is required on every request, like every
non-`/auth` route); `ProjectService` is mocked.

| Case | Verifies |
| --- | --- |
| `listProjectsReturnsSummaries` | `GET /projects` → `200` with `name`, `organizationName` and `memberCount` per row. |
| `listProjectsForwardsOrganizationFilter` | `?organizationId=` reaches `ProjectService.listForCurrentUser` unchanged. |
| `listProjectsRequiresAuthenticationWith401` | Without a Bearer token → `401`; the service is never called. |
| `createVersionReturnsCreatedVersion` | `POST /projects/{id}/version` → `200` `Version created successfully` with the version's `name` (previously always `500`). |
| `createVersionWithParentReturnsParentIdWithoutSerializingEntity` | Creating a child version returns a summary DTO with `parentVersionId` instead of serializing the entity (the lazy `parentVersion` proxy caused a `500` although the version was saved). |
| `createVersionRequiresAuthenticationWith401` | Without a Bearer token → `401`; `ProjectService.saveVersion` is never called. |
| `createProjectReturnsCreatedProject` | `POST /projects` with a valid body → `201` and an envelope with `success=true`, message `Project created`, and the created project's `name`/`description`. |
| `createProjectRejectsBlankFieldsWithValidationError` | Blank `name`/`description` → `400` `Validation error`; `ProjectService.createProject` is never called. |
| `addMemberReturnsCreatedMember` | `POST /projects/{id}/members` with a valid body → `201` with the member's `memberType` and `userMail`. |
| `addMemberRejectsUnknownProjectWith400` | When the service throws for an unknown project → `400` with the domain error message. |
| `addMemberRejectsInvalidBodyWithValidationError` | Missing `userId`/`memberType` → `400` `Validation error`; the service is never called. |
| `listMembersReturnsMembers` | `GET /projects/{id}/members` → `200` with the flattened member rows (mail, first/last name, `active`, `memberType`). |
| `listMembersRequiresAuthenticationWith401` | The same call without a bearer token → `401`; the service is never reached. |
| `createProjectPropagatesForbiddenWith403` | When the service refuses a caller outside the organization → `403` with `You are not allowed to create projects in this organization`. |
| `createProjectRequiresAuthenticationWith401` | `POST /projects` without a token → `401`; the service is never reached. |
| `createVersionPropagatesForbiddenWith403` | When the service refuses a caller without `EDIT_PROJECT` → `403` with the domain message. |
| `addMemberPropagatesForbiddenWith403` | When the service refuses the caller → `403` with `You are not allowed to manage this project`. |
| `addMemberRequiresAuthenticationWith401` | `POST /projects/{id}/members` without a token → `401`; the service is never reached. |
| `updateProjectReturnsTheUpdatedProject` | `PATCH /projects/{id}` → `200` with the updated name and description in the envelope. |
| `updateProjectAcceptsABodyWithOnlyOneField` | A body carrying only `name` is accepted — the optional fields are not rejected by validation. |
| `updateProjectRejectsATooShortNameWithValidationError` | A one-character name → `400` from `@Size`; the service is never reached. |
| `updateProjectRejectsUnknownProjectWith400` | When the service reports an unknown id → `400` with `Project not found`. |
| `updateProjectPropagatesForbiddenWith403` | When the service refuses the caller → `403` with `You are not allowed to manage this project`. |
| `updateProjectRequiresAuthenticationWith401` | `PATCH /projects/{id}` without a token → `401`; the service is never reached. |
| `deleteProjectReturnsOk` | `DELETE /projects/{id}` → `200` with `Project deleted`, and the service is asked to deactivate that id. |
| `deleteProjectRejectsUnknownProjectWith400` | When the service reports an unknown id → `400` with `Project not found`. |
| `deleteProjectPropagatesForbiddenWith403` | When the service refuses the caller → `403` with `You are not allowed to manage this project`. |
| `deleteProjectRequiresAuthenticationWith401` | `DELETE /projects/{id}` without a token → `401`; the service is never reached. |
| `changeMemberRoleReturnsTheUpdatedMember` | `PATCH /projects/{id}/members/{memberId}` → `200` with the new `memberType` and the member's `userMail`. |
| `changeMemberRoleRejectsALastAdminDemotionWith400` | The last-administrator refusal reaches the client as `400` with `The project would be left without an administrator`, verbatim. |
| `changeMemberRoleRejectsAMissingMemberTypeWith400` | An empty body → `400`; the service is never called. |
| `changeMemberRolePropagatesForbiddenWith403` | A caller without `MANAGE_PROJECT` → `403` with `You are not allowed to manage this project`. |
| `changeMemberRoleAnswers404ForAnUnknownMember` | An unknown member → `404` `Member not found`. |
| `changeMemberRoleRequiresAuthenticationWith401` | No token → `401`; the service is never called. |
| `removeMemberReturnsOk` | `DELETE /projects/{id}/members/{memberId}` → `200` `Member removed`, and the service is asked to remove that pair. |
| `removeMemberRejectsALastAdminRemovalWith400` | The same refusal on the removal path → `400` with the same message. |
| `removeMemberPropagatesForbiddenWith403` | A caller without `MANAGE_PROJECT` → `403`. |
| `removeMemberAnswers404ForAnUnknownMember` | An unknown member → `404` `Member not found`. |
| `removeMemberRequiresAuthenticationWith401` | No token → `401`; the service is never called. |
| `listMemberCandidatesReturnsTheMinimalProjection` | `GET /projects/{id}/member-candidates` → `200` with id, first name, last name and mail per row, and **no** `passwordHash` — the projection is asserted not to leak it. |
| `listMemberCandidatesForwardsTheSearchTerm` | `?q=` reaches `ProjectService.listMemberCandidates` unchanged (the escaping happens in the service). |
| `listMemberCandidatesRejectsAnInactiveProjectWith400` | A deleted project → `400` `Project not found`. |
| `listMemberCandidatesPropagatesForbiddenWith403` | A caller without `MANAGE_PROJECT` → `403`. |
| `listMemberCandidatesRequiresAuthenticationWith401` | No token → `401`; the service is never called. |
| `listMemberCandidatesPropagatesForbiddenWith403` | list Member Candidates Propagates Forbidden With403. |
| `changeMemberRoleAnswers404ForAnUnknownMember` | change Member Role Answers404 For An Unknown Member. |
| `listMemberCandidatesRejectsAnInactiveProjectWith400` | list Member Candidates Rejects An Inactive Project With400. |
| `changeMemberRoleReturnsTheUpdatedMember` | change Member Role Returns The Updated Member. |
| `changeMemberRoleRejectsALastAdminDemotionWith400` | change Member Role Rejects ALast Admin Demotion With400. |
| `removeMemberPropagatesForbiddenWith403` | remove Member Propagates Forbidden With403. |
| `changeMemberRoleRejectsAMissingMemberTypeWith400` | change Member Role Rejects AMissing Member Type With400. |
| `listMemberCandidatesRequiresAuthenticationWith401` | list Member Candidates Requires Authentication With401. |
| `listMemberCandidatesReturnsTheMinimalProjection` | list Member Candidates Returns The Minimal Projection. |
| `removeMemberRejectsALastAdminRemovalWith400` | remove Member Rejects ALast Admin Removal With400. |
| `changeMemberRoleRequiresAuthenticationWith401` | change Member Role Requires Authentication With401. |
| `removeMemberAnswers404ForAnUnknownMember` | remove Member Answers404 For An Unknown Member. |
| `removeMemberRequiresAuthenticationWith401` | remove Member Requires Authentication With401. |
| `listMemberCandidatesForwardsTheSearchTerm` | list Member Candidates Forwards The Search Term. |
| `removeMemberReturnsOk` | remove Member Returns Ok. |
| `changeMemberRolePropagatesForbiddenWith403` | change Member Role Propagates Forbidden With403. |

## `project.repository.ProjectRepositoryTest` — Data

The `active` filters on `ProjectRepository`, which are the half of the logical
delete that no mocked test can reach: whether a `@Query` actually leaves a
deactivated row out is a question only a database answers. The first
`@DataJpaTest` in the repository.

Each case was checked by removing the filter it covers and confirming it turns
red, so none of them passes for the wrong reason.

| Case | Verifies |
| --- | --- |
| `findSummariesReturnsAnActiveProject` | The baseline: an active project appears in the summaries. |
| `findSummariesExcludesADeactivatedProject` | A deactivated project is left out — this is what makes the delete visible in the UI. |
| `findSummariesCountsOnlyActiveMembers` | `memberCount` counts active members only: a project with one active and one deactivated member reports 1. |
| `findSummariesForMemberReturnsAnActiveProject` | The baseline for the member-scoped query. |
| `findSummariesForMemberExcludesADeactivatedProject` | A member of a deactivated project no longer sees it. |
| `findIdByVersionIdReturnsTheOwningProject` | The baseline: a version resolves to the project it hangs off. |
| `findIdByVersionIdIgnoresADeactivatedVersion` | A deactivated version resolves to nothing, so `ProjectAccessGuard` refuses it. |
| `findIdByVersionIdIgnoresAVersionOfADeactivatedProject` | Belt and braces: even an active version inside a deactivated project resolves to nothing, so the guard holds whether or not the cascade ran. |
| `findSummariesExcludesProjectsOfADeactivatedOrganization` | A project of a deactivated organization drops out of the admin list, which is how the organization's contents become unreachable without propagating the flag. |
| `findSummariesForMemberExcludesProjectsOfADeactivatedOrganization` | Same for the member-scoped list. |
| `existsByIdAndActiveTrueIsTrueForAnActiveProject` | The baseline for the `listMembers` guard. |
| `existsByIdAndActiveTrueIsFalseOnceTheProjectIsDeactivated` | Listing the members of a deleted project answers `400`. |
| `existsByIdAndActiveTrueIsFalseForAnUnknownId` | An id that was never stored is not active either. |
| `findSummariesForMemberExposesTheCallerRole` | Each row's `myRole` is the caller's own role in that project — ADMIN in one, EDITOR in another — and not the role of whoever else is on it. |
| `findSummariesExposesNullRoleWhenCallerIsNotAMember` | For the platform-wide query, a project the caller does not belong to comes back with `myRole = null`, which is how the UI tells "I may see this" from "I may act on it". |
| `findSummariesExposesTheCallerRoleWhenPlatformAdminIsAMember` | A platform admin who *is* a member gets their actual membership role, not null. |
| `findSummariesForMemberPrefersAdminWhenTheCallerHoldsTwoRoles` | A membership carrying both roles reports ADMIN — the more capable one wins, so a leftover second role cannot downgrade someone. |
| `findSummariesExposesNullRoleWhenCallerIsNotAMember` | find Summaries Exposes Null Role When Caller Is Not AMember. |
| `findSummariesForMemberPrefersAdminWhenTheCallerHoldsTwoRoles` | find Summaries For Member Prefers Admin When The Caller Holds Two Roles. |
| `findSummariesExposesTheCallerRoleWhenPlatformAdminIsAMember` | find Summaries Exposes The Caller Role When Platform Admin Is AMember. |
| `findSummariesForMemberExposesTheCallerRole` | find Summaries For Member Exposes The Caller Role. |

## `version.service.VersionServiceTest` — Unit

Version and node mechanics: editing a node inside a version, adding one, and
merging a sub-version into its parent. `ProjectAccessGuard` is mocked and every
case here is about the mechanics, not the authorization.

> **Coverage regression — the authorization cases are gone.** An earlier revision
> of this class carried thirteen more cases that pinned the guard on every entry
> point (`mutatingOperationsRejectCallerWithoutEditPermission` and
> `mutatingOperationsRejectUnauthenticatedCaller`, both `@ParameterizedTest`s
> over the eight mutating methods; the three `getVersion*` cases; the three
> `saveOrphanVersion*` cases; and the five snapshot-initialisation /
> `saveVersionIsUnguardedBecauseItsCallersAuthorizeInstead` cases), plus the four
> `getDiagram`/`updateNodePosition`/`updateNodeBasics`/`getNodeDetail` cases.
> They were lost in a merge, **not removed on purpose**: `VersionService` still
> calls `accessGuard.assertCanEditVersion` on nine entry points,
> `assertCanViewVersion` on `getVersion` and `assertIsPlatformAdmin` on
> `saveOrphanVersion`, so the code is still guarded and nothing in this class
> notices any more. Restoring them is its own card; until then the only
> protection against a dropped guard here is `ProjectAccessGuardTest` (which
> tests the guard, not its callers) and the `403`/`401` cases in
> `VersionControllerTest`.

| Case | Verifies |
| --- | --- |
| `modifyVersionShouldUpdateNameWithoutCreatingNodeChange` | Renaming a version updates the name and creates no `NodeChange`/`ConnectionChange`. |
| `editNodeInVersion_WhenNodeAddedInThisVersion_ShouldEditInPlaceAndKeepSingleAddChange` | Editing a node that was *added* in this version edits it in place and leaves a single `ADD` change — the history records that the version added the node in its final shape, not an `ADD` followed by an `EDIT`. |
| `editNodeInVersion_WhenNodePreviouslyEditedInThisVersion_ShouldEditInPlaceAndCreateAnotherEditChange` | Editing an already-edited node edits in place and records a further `EDIT` change. |
| `editNodeInVersion_WhenNodeCameFromParent_ShouldUpdateSnapshotAndCreateEditChange` | Editing a node inherited from the parent version clones it into this version's snapshot and records an `EDIT` change, leaving the parent's row alone. |
| `editNodeInVersion_WhenCallerPassesAStaleIdForTheSameIdentity_EditsTheCurrentRowInsteadOfDuplicatingIt` | A client holding the row id from before a clone-on-write edit still edits the current row, because the node is resolved by `identityId` rather than by raw id. |
| `editNodeInVersion_WhenIdentityNotInThisVersionsSnapshot_ShouldThrowIllegalArgumentException` | An identity absent from this version's snapshot throws `IllegalArgumentException`. |
| `editNodeInVersion_WhenNodeDTOIsNull_ShouldThrowNullPointerException` | A null node DTO throws `NullPointerException`. |
| `editNodeInVersion_WhenNodeIdIsNull_ShouldThrowNullPointerException` | A null node id throws `NullPointerException`. |
| `editNodeInVersion_WhenVersionNotFound_ShouldThrowVersionNotFoundException` | An unknown version id throws `VersionNotFoundException`. |
| `editNodeInVersion_WhenNodeNotFound_ShouldThrowEntityNotFoundException` | A node absent from the version throws `EntityNotFoundException`. |
| `addNodeToVersion_WithWellDTO_ShouldAddWellToVersion` | Adding a `WellDTO` saves the well, appends it to the snapshot and records an `ADD` change. |
| `addNodeToVersionSupportsPipelineConnectionAndConsumptionNodes` | Adding a `PipelineConnectionDTO`, `InternalConsumptionDTO` or `IndustrialConsumptionDTO` dispatches to its matching save method and records each node in the snapshot. |
| `editNodeInVersionSupportsPipelineConnectionAndConsumptionNodes` | Editing those same three node subtypes dispatches to the matching edit method when the version owns the node. |
| `mutatingOperationsRejectCallerWithoutEditPermission` | **Parameterized over all eight mutating entry points** (`deleteVersion`, `modifyVersion`, `addNodeToVersion`, `addConnectionToVersion`, `editNodeInVersion`, `editConnectionInVersion`, `deleteNodeFromVersion`, `deleteConnectionFromVersion`): each requires `EDIT_PROJECT` on the owning project, and a refused call reads and writes nothing. |
| `mutatingOperationsRejectUnauthenticatedCaller` | The same eight entry points, with no session: `UnauthorizedException`, and again nothing is read or written. |
| `getVersionChecksViewPermissionAndReturnsTheVersion` | Reading a version runs the view check and returns it. |
| `getVersionRejectsCallerOutsideTheOwningProject` | A caller with no claim on the owning project gets `ForbiddenException`; the repository is never touched. |
| `getVersionRejectsUnauthenticatedCaller` | No session throws `UnauthorizedException`; the repository is never touched. |
| `saveOrphanVersionCreatesTheVersionForAPlatformAdmin` | `POST /version/createtest` creates a detached version for a platform ADMIN, running the platform-level check. |
| `saveOrphanVersionRejectsNonPlatformAdmin` | A regular user gets `ForbiddenException`; nothing is saved. |
| `saveOrphanVersionRejectsUnauthenticatedCaller` | No session throws `UnauthorizedException`; nothing is saved. |
| `saveVersionWithoutParentInitialisesEmptySnapshots` | A version created with no parent has empty, non-null `nodeSnapshot`/`connectionSnapshot`. |
| `addNodeToVersionWorksOnAFreshlyCreatedRootVersion` | **Regression:** adding the first node to a just-created root version lands it in the snapshot and records an `ADD` change. Before the snapshots were initialised this threw `NullPointerException`. |
| `saveVersionFromParentWithNullSnapshotsDoesNotPropagateNull` | A parent row created before the fix, still carrying null snapshots, produces a child with empty lists rather than inheriting the nulls. |
| `versionNoArgsConstructorStartsWithEmptyCollections` | `new Version()` — the path Hibernate and the controller tests use — starts with all four collections non-null. |
| `saveVersionIsUnguardedBecauseItsCallersAuthorizeInstead` | The internal `saveVersion` deliberately runs no check: `ProjectService.saveVersion` authorizes the owning project and `saveOrphanVersion` requires a platform ADMIN. Adding a third check here would fail this case on purpose. |
| `getDiagram_ShouldMapNodesAndConnectionsToDTOs` | `getDiagram` maps the version snapshot to a `DiagramDTO`, including node type, graph and geographical positions, and the connection endpoints. |
| `updateNodePosition_ShouldUpdateGraphAndGeographicalPosition` | `updateNodePosition` updates both the diagram (x/y) and geographical (lng/lat) positions and persists the node. |
| `updateNodeBasics_ShouldUpdateNameAndState` | `updateNodeBasics` updates a node's name and state and persists it. |
| `getNodeDetail_ShouldReturnCommonAndTypeSpecificFields` | `getNodeDetail` returns a node's common fields and its type-specific values (keyed by the frontend field names). |
| `mergeSubVersionIntoParent_ShouldReplaceSnapshotsAndMergeChanges` | A merge applies the child's snapshots onto the parent and folds the child's change lists into the parent's. |
| `mergeSubVersionIntoParent_AddAndDelete_ShouldCancelOut` | An identity added and then deleted in the child leaves no net change in the parent, instead of two history entries that undo each other. |
| `mergeSubVersionIntoParent_SingleEdit_ShouldBePreserved` | A lone `EDIT` survives the merge as an `EDIT`. |
| `mergeSubVersionIntoParent_VersionHasNoParent_ThrowsIllegalArgumentException` | Merging a root version throws `IllegalArgumentException`. |

## `version.service.VersionConflictServiceTest` — Unit

`VersionConflictService`, with every repository mocked. Two halves: **detection**
(`recordMergeConflicts`, run right after a merge, which flags every sibling of
the merged parent whose own row for a touched identity differs) and
**resolution** (`resolveConflict`, which either copies the parent's row into the
sibling or writes the change that keeps the sibling as it is). The matching key
throughout is the `identityId`, never the row id.

| Case | Verifies |
| --- | --- |
| `recordMergeConflicts_SiblingHasADifferentRowForATouchedIdentity_CreatesOneConflictPerIdentity` | The baseline: one unresolved conflict per diverging identity, carrying the merged parent, the source version, the conflicting sibling, `NODE` and the identity. A sibling change to an *unrelated* identity adds nothing. |
| `recordMergeConflicts_SiblingHasADifferentConnectionForATouchedIdentity_CreatesConflict` | The same for a connection, recorded with `entityType = CONNECTION`. |
| `recordMergeConflicts_SiblingNeverTouchedIdentityButHasNoRowForIt_StillCreatesConflict` | When the merge *added* a node, a sibling that never touched it is still flagged: its snapshot is a static copy taken when it branched, so it simply does not have the node, and a human has to decide whether it should adopt it. |
| `recordMergeConflicts_SiblingAlreadyHasTheIdenticalMergedRow_CreatesNothing` | A sibling that inherited the exact same row (branched off after an earlier merge applied it) has nothing to diverge about, so nothing is saved. |
| `recordMergeConflicts_SiblingChangedDifferentIdentity_CreatesNothing` | Two branches touching different identities do not conflict. |
| `recordMergeConflicts_SourceWithoutChanges_DoesNotQuerySiblings` | A source with no changes short-circuits: the siblings are never even loaded. |
| `recordMergeConflicts_PendingDuplicateAlreadyExists_UpdatesItInsteadOfInsertingANewRow` | Re-merging into the same identity updates the existing pending row rather than inserting a second one, so the list does not grow on every merge. |
| `recordMergeConflicts_OnlyAResolvedRowExistsForTheIdentity_InsertsANewPendingRow` | An already-resolved row does not suppress a fresh conflict: a new pending row is inserted. |
| `recordMergeConflicts_TwoDifferentIdentitiesConflict_EachGetsItsOwnRowNotTheOthers` | Two diverging identities produce two rows — the pre-existing one is updated and a new one inserted — and neither is mistaken for the other. |
| `resolveConflict_NodeAccept_BothHaveIt_CopiesDataKeepsSiblingId` | Accepting copies the parent's data onto the sibling's **own row**, keeping that row's id, drops the sibling's now-redundant `NodeChange`, leaves one node in the snapshot and saves the sibling. |
| `resolveConflict_NodeAccept_OnlyParentHasIt_ClonesIntoSiblingSnapshot` | When only the parent has the node, accepting clones it into the sibling's snapshot with the same identity and a **new** row id, so the two versions never share a row. |
| `resolveConflict_NodeAccept_OnlySiblingHasIt_RemovesFromSnapshot` | When only the sibling has it, accepting the parent's state means removing it: the snapshot and the change list both end up empty and no node is saved. |
| `resolveConflict_NodeReject_BothHaveIt_WritesEditChange` | Rejecting keeps the sibling's row and records an `EDIT` change from the parent's node to the sibling's, so the divergence is explicit in the history. |
| `resolveConflict_NodeReject_OnlyParentHasIt_WritesDeleteChange` | Rejecting a node the parent added records a `DELETE` change with no result node, and the sibling's snapshot stays empty. |
| `resolveConflict_NodeReject_OnlySiblingHasIt_WritesAddChange` | Rejecting the parent's absence records an `ADD` change with no changed node, and the sibling keeps its row. |
| `resolveConflict_ConnectionAccept_BothHaveIt_RemapsEndpointsThroughIdentity` | Accepting a connection remaps its endpoints onto **the sibling's own copies** of the endpoint nodes (matched by identity), not onto the parent's row ids, and clears the sibling's connection change. |
| `resolveConflict_ConnectionReject_OnlyParentHasIt_WritesDeleteChange` | Rejecting a connection the parent added records a `DELETE` connection change. |
| `getConflictsForVersion_ReturnsMappedConflicts` | The conflicts of one version are returned as DTOs carrying both version ids and `resolved = false`. |
| `getConflictsForVersion_UnknownVersion_Throws` | An unknown version id throws `VersionNotFoundException`. |
| `resolveConflict_UnknownConflict_Throws` | An unknown conflict id throws `EntityNotFoundException`. |

## `version.service.VersionConflictIntegrationTest` — Integration

`@SpringBootTest` on the H2 `test` profile, inside a transaction, driving the
real `VersionService`/`VersionConflictService` against a real Hibernate session.
`ProjectAccessGuard` is the only mocked bean — permissions are
`ProjectAccessGuardTest`'s job, so the callers here have no session. This is the
class that catches what the mocked unit tests cannot: entity identity, snapshot
sharing between versions, and what cascades on delete.

| Case | Verifies |
| --- | --- |
| `mergingOneChildFlagsAConflictOnTheSiblingThatTouchedTheSameIdentity` | The reported scenario end to end: two children of one parent each touch a node, one is merged, and the other shows the conflict through `getConflictsForVersion`. |
| `mergingANewlyAddedNodeFlagsAConflictWithSiblingsThatDontHaveIt` | A node added by the merged branch is flagged against siblings that never had it, rather than quietly never reaching them. |
| `mergingASiblingThatNeverTouchedAnythingDoesNotRevertWhatAnEarlierMergeAdded` | Because the merge is differential (it applies only what the merging branch itself changed), a sibling that changed nothing cannot wipe out another branch's merge. |
| `repeatedMergesTouchingTheSameIdentityCollapseToOneNetNodeChange` | **Regression:** several merges into the same identity collapse to the net change instead of accumulating stale entries (`ADD`, `EDIT`, `ADD`). The old grouping was by raw row id, which changes on every clone-on-write edit; it is by identity now. Includes a third branch re-adding the same identity, as in the reported reproduction. |
| `getAllConflicts_ReturnsConflictsAcrossAllVersions` | The unscoped listing returns conflicts from more than one version. |
| `editingAnInheritedNodeInOneChildDoesNotMutateTheParentOrSiblingRows` | **Regression:** a node inherited from the parent is cloned on write, so the parent's row and any untouched sibling's row keep their old values. Mutating in place made the edit visible in every version sharing the row. |
| `editingAnInheritedConnectionInOneChildDoesNotMutateTheParentOrSiblingRows` | The same for a connection, including that branching deep-copies the nodes and remaps the connection onto *that* version's copies (same identity, new row id). |
| `deletingTheSourceVersionKeepsTheConflictWithNullSourceAndStillAcceptable` | Deleting the version a merge came from leaves the conflict with a null source, and accepting it still works — the conflict describes both remaining sides on its own. |
| `deletingTheSourceVersionKeepsTheConflictWithNullSourceAndStillRejectable` | The same for rejecting it. |
| `deletingTheConflictingSiblingVersionDeletesItsConflicts` | A conflict has no meaning without the sibling it was raised against, so deleting that version deletes its conflicts. |
| `deletingTheMergedParentVersionDeletesItsConflicts` | Same for the merged parent. |

## `version.repository.VersionConflictRepositoryTest` — Data

`VersionConflictRepository.findPendingDuplicate`, the lookup that keeps
`recordMergeConflicts` from inserting a second pending row for a tuple it has
already flagged. The key is the whole tuple (merged version, source version,
conflicting sibling, entity type, identity) **plus** `resolved = false`, and each
case removes one element of it.

| Case | Verifies |
| --- | --- |
| `findPendingDuplicate_ExactMatch_ReturnsIt` | The baseline: the exact tuple finds the stored row. |
| `findPendingDuplicate_DifferentSibling_DoesNotMatch` | A different conflicting sibling is a different row, and asking for it returns that row rather than the original. |
| `findPendingDuplicate_DifferentIdentity_DoesNotMatchTheOriginalRow` | A different identity likewise, and the original tuple still has exactly one pending row. |
| `findPendingDuplicate_DifferentEntityType_DoesNotMatchTheOriginalRow` | A `CONNECTION` row and a `NODE` row for the same identity do not collide. |
| `findPendingDuplicate_OnlyAResolvedRowExistsForTheExactTuple_ReturnsEmpty` | Once resolved, the row stops being a duplicate — which is what lets the same divergence be raised again later. |
| `findPendingDuplicate_HistoricalDuplicatePendingRowsAlreadyExist_StillReturnsExactlyOne` | With two pending rows already stored for one tuple (historical data from before the de-duplication), the query returns one instead of failing, and does not delete the other. |

## `version.dto.VersionConflictDTOTest` — Unit

| Case | Verifies |
| --- | --- |
| `from_WithNullSourceVersion_IsNullSafeAndStillDescribesBothSides` | `VersionConflictDTO.from` survives a conflict whose source version was deleted: `sourceVersionId` and `sourceChangeType` come back null while both side descriptions, both version ids and the identity are still filled in. This is the DTO half of the two `deletingTheSourceVersion*` integration cases. |
| `newVersionInitializesMutableEmptySnapshots` | new Version Initializes Mutable Empty Snapshots. |
| `saveVersion_WithoutParent_InitialisesEmptyNonNullSnapshots` | save Version Without Parent Initialises Empty Non Null Snapshots. |
| `mergeSubVersionIntoParent_AddAndDelete_ShouldCancelOut` | merge Sub Version Into Parent Add And Delete Should Cancel Out. |
| `mergeSubVersionIntoParent_SingleEdit_ShouldBePreserved` | merge Sub Version Into Parent Single Edit Should Be Preserved. |
| `editNodeInVersion_WhenCallerPassesAStaleIdForTheSameIdentity_EditsTheCurrentRowInsteadOfDuplicatingIt` | edit Node In Version When Caller Passes AStale Id For The Same Identity Edits The Current Row Instead Of Duplicating It. |
| `editNodeInVersion_WhenNodeAddedInThisVersion_ShouldEditInPlaceAndKeepSingleAddChange` | edit Node In Version When Node Added In This Version Should Edit In Place And Keep Single Add Change. |
| `mergeSubVersionIntoParent_ShouldReplaceSnapshotsAndMergeChanges` | merge Sub Version Into Parent Should Replace Snapshots And Merge Changes. |
| `editNodeInVersion_WhenIdentityNotInThisVersionsSnapshot_ShouldThrowIllegalArgumentException` | edit Node In Version When Identity Not In This Versions Snapshot Should Throw Illegal Argument Exception. |

## `version.controller.VersionControllerTest` — Web

Exercises `VersionController` through the real `SecurityConfig`/`AuthFilter`
chain, so a request without a token is refused by the actual filter chain rather
than by a stub; `VersionService` is mocked. These endpoints take nothing but a
version UUID, which is what made guarding them necessary.

> **Correction:** this class used to carry three `createVersion*` cases for
> `POST /projects/{projectId}/versions`. That endpoint lives on
> `ProjectController` and is covered by `ProjectControllerTest`
> (`createVersionReturnsCreatedVersion`, `…PropagatesForbiddenWith403`,
> `…RequiresAuthenticationWith401`), so the rows were removed here rather than
> duplicated. **One of the three did not survive the move:** nothing now covers
> a blank version `name` being rejected with a validation error. Worth a case.

| Case | Verifies |
| --- | --- |
| `createVersionReturnsCreatedVersion` | `POST /projects/{projectId}/versions` with a valid body → `201` and an envelope with `success=true`, message `Version created`, and the created version's `name`. |
| `createVersionRejectsBlankNameWithValidationError` | Blank `name` → `400` `Validation error`; `VersionService.createVersion` is never called. |
| `createVersionRejectsUnknownProjectWith400` | When the service throws for an unknown project → `400` with the domain error message. |
| `getVersionReturnsTheVersion` | `GET /version/{id}` → `200` with the envelope and the version's `name`. |
| `getVersionPropagatesForbiddenWith403` | When the service refuses the caller → `403` carrying the domain message. |
| `getVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `deleteVersionPropagatesForbiddenWith403` | `DELETE /version/{id}` for a caller without `EDIT_PROJECT` → `403`. |
| `deleteVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `addNodeToVersionPropagatesForbiddenWith403` | `POST /version/{id}/node` for a caller without `EDIT_PROJECT` → `403`. |
| `addNodeToVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `importNodesFromCsvReturnsImportedRowCount` | An authenticated multipart CSV upload → `200` with the imported row count in the `ApiResponse` envelope. |
| `importNodesFromCsvRequiresAuthenticationWith401` | The same multipart upload without a token → `401`; the importer is never reached. |
| `importNodesFromCsvPropagatesEditPermissionFailureWith403` | A caller without edit permission receives `403` and the importer’s authorization message. |
| `importNodesFromCsvReturnsBadRequestForInvalidRows` | A malformed node row reported by the importer becomes a `400` API error. |
| `importNodesFromCsvRejectsEmptyFiles` | An empty upload returns `400` without invoking the importer. |
| `importNodesFromCsvRejectsFilesOverTenMegabytes` | An upload over 10 MB returns `400` without invoking the importer. |
| `importNodesFromCsvRejectsMissingMultipartFile` | A request without the required `file` part returns `400` with its name. |
| `createDetachedVersionReturnsTheVersionForAPlatformAdmin` | `POST /version/createtest` → `200` with the created version. |
| `createDetachedVersionPropagatesForbiddenWith403` | The same call for a non-admin → `403` with `Only platform admins can create detached versions`. |
| `createDetachedVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `mergeSubVersionIntoParentPropagatesNoParentWith400` | `PATCH /version/merge/{subId}` on a root version → `400` with the domain message. |

## `version.service.VersionNodeCsvImportServiceTest` — Unit

CSV parsing and DTO dispatch for authenticated, version-scoped imports. Legacy
files use `nodeKind` and import nodes only. The optional `recordType` column
supports node-bound `economicProfile` JSON on `NODE` rows, graph edges on
`CONNECTION` rows, and one scenario-wide `economicScenario` JSON object on a
separate `SCENARIO` row.

| Case | Verifies |
| --- | --- |
| `importsEveryNodeKindIntoVersion` | Each of the 12 supported `nodeKind` values is converted to its matching DTO and added to the target version. |
| `importsMixedKindsAndIgnoresEmptyColumnsForOtherKinds` | A mixed CSV creates multiple node DTO types, while blank columns belonging to other types are ignored. |
| `mapsNestedJsonDtoFields` | Nested graph, type, investment-cost and probability-distribution JSON cells map to the Well DTO. |
| `importsConnectionsByResolvingProvidedNodeIdsAfterNodeCreation` | Creates nodes with file-local external IDs, then imports a connection whose endpoints resolve to the backend-generated node IDs. |
| `rejectsConnectionReferencesToUnknownNodeIdsBeforePersisting` | A connection that references a missing external node ID rejects the file before any node or connection is saved. |
| `rejectsDuplicateExternalNodeIdsBeforePersisting` | Duplicate file-local node IDs are rejected before persistence. |
| `importsNodeAndScenarioEconomicsAndResolvesNodeReferences` | Imports operational and economic data together, binds the profile and resolves conversion, contract and asset references to the newly created node ID. |
| `rejectsEconomicReferencesToUnknownExternalNodeIdsBeforePersisting` | Rejects an economic configuration that references a missing file-local node ID before any node is persisted. |
| `rejectsNodeEconomicProfileWithoutScenarioConfigurationBeforePersisting` | A node economic profile without a scenario configuration is rejected before any node is persisted. |
| `rejectsUnknownRecordTypeBeforePersisting` | An unsupported record discriminator is rejected before any node is persisted. |
| `rejectsMalformedEconomicProfileJsonBeforePersisting` | Malformed node economic JSON is rejected before node persistence. |
| `rejectsScenarioRowsContainingNodeDataBeforePersisting` | Scenario-level rows cannot contain node columns. |
| `rejectsDuplicateScenarioRowsBeforePersisting` | More than one scenario-wide economic row is rejected before node persistence. |
| `importsIndustrialConsumptionDeclaredInTheVerticalOfNodeTypeEnum` | An `INDUSTRIAL_CONSUMPTION` row with `type.vertical` `INTERNAL_CONSUMPTION` (the `NodeTypeEnum` vertical) is accepted and handed to the save as an `IndustrialConsumptionDTO` in that vertical. |
| `rejectsIndustrialConsumptionDeclaredAsExportBeforePersisting` | The same row with `EXPORT` is rejected with `CSV record 2 (INDUSTRIAL_CONSUMPTION) has type.vertical 'EXPORT'; expected 'INTERNAL_CONSUMPTION'` and nothing is saved. |
| `rejectsUnknownNodeKindWithSupportedKinds` | An unsupported discriminator is rejected with the accepted values and no node is added. |
| `rejectsUnknownHeaders` | A misspelled header is rejected instead of silently dropping data. |
| `rejectsRowsWithDifferentColumnCounts` | A row with the wrong number of fields is rejected with its record number. |
| `rejectsValuesInColumnsNotSupportedByTheSelectedKind` | A non-empty field from another node type is rejected rather than ignored. |
| `rejectsRowsMissingRequiredFields` | A row missing required common node fields is rejected before persistence. |
| `rejectsMalformedNestedJson` | A nested DTO cell that is not a JSON object is rejected with its column name. |
| `checksEditPermissionBeforeParsingCsv` | A caller without `EDIT_PROJECT` is rejected before malformed CSV is parsed or nodes are added. |
| `rejectsMalformedCsvQuoting` | Quotes inside unquoted fields and non-whitespace after a closing quote are rejected. |
| `rejectsInconsistentNodeTypeMetadata` | `type.nodeType`/`type.vertical` must agree with the row’s `nodeKind`. |
| `rejectsMissingRequiredTypeSpecificFields` | Missing required DTO fields, including primitive fields that otherwise default to zero, are rejected. |
| `rejectsInvalidDistributionParameters` | Unsupported or invalid uniform/normal distribution parameters are rejected. |
| `rejectsInvalidUtf8` | Invalid UTF-8 input bytes are rejected rather than silently replaced. |
| `rejectsImportsOverMaximumRowCount` | More than 10,000 data rows are rejected before node persistence. |
| `rejectsFilesOverMaximumSize` | The service rejects CSV byte arrays larger than 10 MB. |
| `rejectsIdentityThatAlreadyExistsInTheVersionBeforePersisting` | A NODE row whose `identity` is already in the target version → `Invalid CSV record 2 (NODE): identity <uuid> already exists in this version`; no node, connection or economics written. |
| `rejectsExistingIdentityOnALaterRowWithoutWritingTheEarlierValidRows` | The colliding identity is on record 3: the valid record 2 is not written either (the check runs before any write). |
| `rejectsIdentityRepeatedInTheFileBeforePersisting` | Two rows with the same `identity` → `Invalid CSV record 3 (NODE): identity <uuid> is repeated in the file`; nothing written. |
| `importsANewIdentityThatIsNotInTheVersion` | An `identity` the version does not have is accepted and reaches `addNodeToVersion` unchanged. |
| `aFileWithoutIdentityColumnNeverLooksUpTheVersionIdentities` | A file with no `identity` column imports as before and never reads the version's identities. |

## `node.service.NodeServiceTest` — Unit

`NodeService` has eleven save methods; only `saveWell` is covered. That case
builds a complete `WellDTO`, saves it through a mocked `WellRepository` that
echoes its argument back, and reads it again through the same mock. The second
case covers the edit path.

| Case | Verifies |
| --- | --- |
| `saveWellShouldReturnWell` | `saveWell` maps a complete `WellDTO` onto a persisted `Well`, preserving `name`, `maxCollectionCapacity` and `declineCurve`. |
| `editWellShouldNeverChangeIdentityEvenIfDtoCarriesADifferentOne` | **Regression:** `editWell` applies the DTO's fields but keeps the well's original `identityId`, even when the DTO carries a different one (a stale or placeholder value from a client form). The identity is the key merges and conflicts match nodes by, so reassigning it silently would break version history. |
| `editWellShouldNeverChangeIdentityEvenIfDtoCarriesADifferentOne` | edit Well Should Never Change Identity Even If Dto Carries ADifferent One. |

## `node.controller.NodeControllerTest` — Unit

**Not a `@WebMvcTest`**, despite driving `MockMvc`: it builds the controller
with `MockMvcBuilders.standaloneSetup`, so there is no Spring context and no
`SecurityConfig`/`AuthFilter` chain, and the request arrives already
authorized. This case therefore says nothing about *who* may create a node —
the eleven `POST /nodes/**` endpoints still carry no authorization check of
their own. One of the eleven is covered.

| Case | Verifies |
| --- | --- |
| `createWellShouldReturnOk` | `POST /nodes/well` with a complete `WellDTO` → `200`, JSON content type, and `Well created successfully` in the envelope. |

## `simulator.ResultPerRoundTest` — Unit

Per-node simulation results. Each case builds the `BaseNode` it needs as a
Mockito mock (so the node's own persistence and validation are out of the way),
wraps it in its `Sim*` counterpart, wires it to the node upstream, runs a full
year hour by hour (`t = 0 … 8759`) and then asserts the four figures
`createResult()` reports: `maxPossibleProduced`, `totalProduced`,
`totalDeferred` and `extra`.

These are **characterisation tests**: the expected numbers are the engine's
current output to one decimal, not values derived independently. They are what
tells you a change to the engine moved a number; they do not tell you the number
is right. Treat a diff here as a question to answer, not a test to re-baseline
without looking.

| Case | Verifies |
| --- | --- |
| `testResultWell` | A well feeding a gathering network produces its capacity every hour, and the share the network cannot take is reported as deferred. |
| `testResultGatheringNetwork` | A gathering network's result after its loss per metre, including the surplus it reports as `extra`. |
| `testResultTreatmentPlant` | A treatment plant's result once the contaminant is removed. |
| `testResultPipeline` | A pipeline's result after its loss per kilometre. |
| `testResultPipelineConnection` | A pipeline connection's result at its transfer capacity. |
| `testResultCompressingPlant` | A compressing plant's result. |
| `testResultLiquefactionPlant` | A ground-based liquefaction plant's result, where the liquefaction ratio is what caps the output. |
| `testResultFLNGUnit` | An FLNG unit's result, plus the docking limit (two ships, the third refused, a slot freed by releasing one) and that `reset` empties both the ship count and the storage. |
| `testResultSeaportTerminal` | A seaport terminal's result, with the export frequency gating how much actually leaves. |
| `testResultLNGCarrier` | An LNG carrier's result, where `maxPossibleProduced` is bounded by the round trips the year allows. |
| `testResultInternalConsumption` | An internal consumption node takes what it is fed up to its own demand, deferring nothing. |
| `testResultIndustrialConsumption` | The same for an industrial consumption node. |

## `simulator.SimulatorTest` — Unit

Two layers in one class. The first ten cases are **unit tests of a single
`Sim*` node**: give it one upstream node with a known delivery, simulate one or
two hours, assert what it hands on — which is where each node type's physics
(loss per metre, contaminant removal, compression, liquefaction ratio, transfer
capacity, docking slots) is actually pinned. The rest drive the whole
`Simulator` over a mocked `Version` snapshot.

| Case | Verifies |
| --- | --- |
| `testSimWellActiveAction` | A well delivers its full capacity at hour 0 and less a year later, so the decline curve is applied over time rather than per hour. |
| `testSimGatheringNetworkCapacity` | A gathering network passes on the well's delivery minus its length × loss-per-metre. |
| `testSimTreatmentPlant` | A treatment plant removes the contaminant: the volume drops by exactly the contaminant and the contaminant itself ends at 0. |
| `testSimConnection` | A pipeline connection takes what the treatment plant offers, up to its transfer capacity. |
| `testSimPipeline` | A pipeline applies its loss per kilometre. |
| `testSimPipelineConnection` | A pipeline connection fed by a pipeline transfers up to its capacity. |
| `testSimCompressingPlant` | A compressing plant applies its own loss. |
| `testSimLiquefactionPlant` | A ground-based liquefaction plant applies its liquefaction ratio. |
| `testSimSeaportTerminal` | A terminal takes the plant's delivery, reports a free berth, and reports none once both its berths are taken. |
| `testSimLNGCarrier` | A carrier fed by a terminal simulates an hour without failing. **No assertion** — it is a smoke test; the carrier's figures are asserted in `ResultPerRoundTest.testResultLNGCarrier`. |
| `testSimulationSimpleComplete` | A linear eight-node chain simulates 10 years and finishes in under 2s — a guard against the infinite loop a cycle in the graph used to cause, not a correctness check. |
| `testSimulationWhitNNodes` | A randomly wired 500-node network (seeded `Random(42)`, so it is reproducible) simulates 10 years without throwing, in under 8s. |
| `testResultInFiveYearSimulationWithAllNodeTypes` | A graph with all twelve node types, including a branch into FLNG and another into industrial and internal consumption, simulates 5 years and yields 12 per-node results per round, each with a node id and non-negative figures, and a final result whose `time` is 5. |
| `testSimWellProductionAndDecline` | A well's `maxPossibleProduced` after one year and after two: the second year adds less than the first, which is the decline applied cumulatively. |
| `testPipelineConnectionPriorityOrdering` | A pipeline's two outgoing connections are placed in the execution order by ascending `outputPriority`, so the lower value runs first and gets served first. |

> **`simulator.ResultMappingTest` is back (rewritten, 8 cases).** The older
> seven-case section was removed when the class was lost in a merge; the class
> now exists again under `backend/src/test/java/org/enerscope/simulator/` as a
> `@DataJpaTest`, and its cases are in
> the last 2026-11-15 section below (*the rewritten `simulator.ResultMappingTest`*).
> It runs on **H2** with the schema Hibernate generates (`ddl-auto=create-drop`,
> Flyway off), so it pins the entity mapping to the column and table names the
> migrations use, but it does **not** replace Hibernate's `validate` against a
> real PostgreSQL schema — see `considerations.md`, 2026-11-15.

## `strategyCost.CostTest` — Unit

The cost calculations on `BaseNode`, reached through a local `DummyNode`
subclass that exposes the protected fields. `MoneyAmount` and `InvestmentCost`
are mocked, so these cases pin the arithmetic and the failure messages rather
than the money type itself.

| Case | Verifies |
| --- | --- |
| `CalculateInvestmentCost_Success` | `CalculateInvestmentCost` returns whatever the node's `InvestmentCost` computes. |
| `CalculateInvestmentCost_ThrowsException_WhenNull` | With no `InvestmentCost`, it throws with `Investment Cost is empty`. |
| `CalculateOperatingCost_Success` | `CalculateOperatingCost` multiplies the monthly operating cost by the lifespan in months. |
| `CalculateOperatingCost_ThrowsException_WhenNull` | With no operating cost, it throws with `Base Node missing arguments`. |
| `CalculateUpkeepCost_Success` | `CalculateUpkeepCost` multiplies the upkeep cost by the number of maintenances the lifespan allows — 12 months at a 60-day interval gives 6. |
| `CalculateUpkeepCost_ThrowsException_WhenNull` | With no upkeep cost, it throws with `Base Node missing arguments`. |
| `CalculateTotalCost_Success` | `CalculateTotalCost` adds the investment, operating and upkeep totals together. |
| `CalculateTotalCost_HandlesExceptionsAndReturnsZeroForMissingCosts` | With all three costs missing, the exceptions are swallowed and the total comes back as `MoneyAmount.of(0)` instead of failing. |

## `strategyCost.InvestmentCostTest` — Unit

`InvestmentCost.CalculateCost`, which sums its components. The components list
is injected by reflection because the field has no setter.

| Case | Verifies |
| --- | --- |
| `CalculateCost_SumsAllComponentsSuccessfully` | Every component is asked for its cost exactly once, and the results are accumulated onto `MoneyAmount.of(0)`. |
| `CalculateCost_HandlesComponentException` | A component that throws is skipped instead of failing the whole calculation; the remaining components still add up. |

## `strategyCost.CostBasisCalculatorsTest` — Unit

The five cost-basis strategies, each multiplying a `MoneyAmount` by a dimension
read off the node. `Flat` accepts any node; the other four require a specific
node type, so each of those also has a case for the type it must reject.

| Case | Verifies |
| --- | --- |
| `Flat_ReturnsSameMoneyAmount` | `Flat` ignores the node and returns the amount unchanged. |
| `Per_M_CalculatesCostForGatheringNetwork` | `Per_M` multiplies by the gathering network's length. |
| `Per_M_ThrowsException_ForInvalidNodeType` | `Per_M` on anything that is not a `GatheringNetwork` throws `ClassCastException`. |
| `Per_KM_CalculatesCostForPipeline` | `Per_KM` multiplies by the pipeline's length. |
| `Per_KM_ThrowsException_ForInvalidNodeType` | `Per_KM` on anything that is not a `Pipeline` throws `ClassCastException`. |
| `Per_KM2_CalculatesCostForWell` | `Per_KM2` multiplies by the well's surface. |
| `Per_KM2_ThrowsException_ForInvalidNodeType` | `Per_KM2` on anything that is not a `Well` throws `ClassCastException`. |
| `Per_Conections_Total_CalculatesCostForGatheringNetwork` | `Per_Conections_Total` multiplies by the gathering network's connected-well count. |
| `Per_Conections_Total_ReturnsSameMoneyForPipelineConnection` | For a `PipelineConnection` the amount is returned unchanged — the connection counts as one. |
| `Per_Conections_Total_ThrowsException_ForInvalidNodeType` | Any other node type throws with `Wrong type of node`. |

## Manual verification (frontend)

The frontend has a small `node:test` suite (`npm test`, see
[*Frontend unit tests*](#frontend-unit-tests-npm-test)) but **no mocking layer
for the API** — no MSW, no fixtures, no mock mode in `src/api/client.ts`. The
checks below therefore run **against a real backend** (`cd backend && docker
compose up --build`, then `cd frontend && npm run dev`) and cover what the unit
tests cannot: what the screen looks and behaves like. The Playwright scripts
`frontend/tests/*-browser.mjs` use isolated HTTP fixtures instead of a backend;
they are not part of `npm test` and were not run for this catalog.

So the honest answer to "what is tested against the real backend and what only
with mocks" is:

- **Backend behaviour** — every rule in this catalog above — is covered by
  `mvn test`, which runs against **mocks** (Unit/Web) or **H2** (Data/
  Integration). The `*IT` classes are the exception: they run by hand against a
  real PostgreSQL (see *PostgreSQL classes* in the Summary).
- **Frontend logic** (pure functions, API adapters, the navigation tree) is
  covered by `npm test`; **frontend screens** only by the checklist below, by
  hand, against a real backend, and by the Playwright scripts.

**Status of this pass:** the checklist was written from the code on
2026-10-18 and **none of it was executed as part of writing it**. Every row is
therefore `pendiente de verificación`. Fill the status in as you run them, and
do not infer a pass from the fact that the feature was built.

| # | Check | How | Needs | Status |
| --- | --- | --- | --- | --- |
| M1 | A CSV with the canonical header imports, and only the valid rows | Importar CSV in *Mi organización* (owner) with a file mixing valid rows, a bad address, a duplicate and an unknown role; the preview must mark each bad row with its reason, the button must offer only the valid count | Real backend | Pendiente de verificación |
| M2 | An Excel "CSV UTF-8" file (BOM) imports | Save the template from Excel and upload it unchanged; the BOM must not reach the server and the columns must resolve | Real backend | Pendiente de verificación |
| M3 | A semicolon-delimited CSV imports | Same file saved with `;`; the preview must say `separador «;» convertido a coma` | Real backend | Pendiente de verificación |
| M4 | Over 500 rows and over 1 MB are refused in the browser | Upload a 501-row file, then a >1 MB one; both must block with their own message and leave the import button disabled, **without** a request being sent (check the network tab). The server enforces the same 500-row cap (`400 The CSV file has more than 500 rows`), so a client that skips this check still gets refused | None | Pendiente de verificación |
| M5 | The credentials are downloadable once and the warning is real | After a successful import, download `credenciales-*.csv` and check it opens as UTF-8 with the accents intact; then import again and close the dialog without downloading — the confirmation must appear | Real backend | Pendiente de verificación |
| M6 | Rows whose account already exists are reported as "ya existían", not as errors | Import a file containing an address that already has an account | Real backend | Pendiente de verificación |
| M7 | A `role` column creates owners | Import a row with `role=OWNER` and check the member list shows Propietario | Real backend | Pendiente de verificación |
| M8 | A member missing a mandatory column is refused for the whole file | Upload a file whose header lacks `lastName` | None | Pendiente de verificación |
| M9 | An existing account can be added to an organization by exact email | *Mi organización* → Agregar a la organización, search an address that exists | Real backend | Pendiente de verificación |
| M10 | The email lookup does not leak the roster | Search a partial address and a deactivated account; both must come back as not found | Real backend | Pendiente de verificación |
| M11 | The platform-admin modal offers a searchable list and hides current members | *Todas las organizaciones* → an organization's members → Agregar; the combobox must work with the keyboard (↑/↓/Enter/Esc) and must not list people already in the organization | Real backend, platform admin | Pendiente de verificación |
| M12 | The last owner of an organization cannot be removed or demoted | With a single Propietario, both actions must be disabled with the reason; then call the API directly and confirm `400 The organization would be left without an owner` | Real backend | Pendiente de verificación |
| M13 | The last administrator of a project cannot be removed or demoted | Same in *Equipo del proyecto*; direct call must answer `400 The project would be left without an administrator` | Real backend | Pendiente de verificación |
| M14 | A platform admin does not bypass either guard | Repeat M12 and M13 signed in as `admin@enerscope.org` | Real backend, platform admin | Pendiente de verificación |
| M15 | Removing someone from an organization is refused when it would orphan a project | Make a user the only Administrador of a project, then remove them from the organization; the error must name the project problem and the member must still be there afterwards | Real backend | Pendiente de verificación |
| M16 | Removing someone from an organization removes them from its projects | Same with a second administrator present on the project; after the removal they must be gone from both lists | Real backend | Pendiente de verificación |
| M17 | At 1280px and wider the sidebar is always a fixed column | At ≥ `80rem` (e.g. 1280 and 1440px) on every page: the rail runs the full height with the logo, the top bar starts to its right, the content is pushed, there is no hamburger and no pin button, and nothing (route, mouse at the left edge) hides it | None | Pendiente de verificación |
| M18 | Between 1024 and 1279px it is a drawer under a 64px top bar | At 1100px: the logo is in the top bar next to the hamburger *Abrir menú*; opening it slides the drawer in **below** the 64px bar (it must not cover its bottom 8px) over a scrim, focus moves into it, the label becomes *Cerrar menú*; `Escape`, a press on the scrim and a row click close it and return focus to the hamburger. Resize across 1280px both ways: wide shows the column, back below shows the drawer closed. A `sidebarPinned` left in LocalStorage by an old build is gone after a load | None | Pendiente de verificación |
| M19 | `Ctrl`/`Cmd`+`B` toggles the drawer only | Below 1280px it opens and closes the drawer, but not with the caret in a text field nor with a modal open. At ≥ 1280px it does **nothing** (the column stays, nothing is stored) | None | Pendiente de verificación |
| M20 | Below 1024px it is the same drawer under a 56px top bar | At 390px: hamburger, drawer under the 56px bar over a scrim, `Tab` trapped inside it, closed by a row click and by `Escape` | None | Pendiente de verificación |
| M21 | Sections the caller cannot reach are hidden, not disabled | Compare the sidebar as a platform admin, an organization owner and a plain member: *Usuarios* and *Todas las organizaciones* only for the admin, *Mi organización* only for an owner, *Equipo del proyecto* only where the caller administers the active project | Real backend, three accounts | Pendiente de verificación |
| M22 | A module left with one child collapses into a plain row | As a plain member with one project, *Proyectos* must be a single row and not a disclosure | Real backend | Pendiente de verificación |
| M23 | Locked sections are legible | Check *Reportes* (*Comparaciones* is no longer locked since 2026-11-12): padlock or "próximamente", full-contrast label, not dimmed | None | Pendiente de verificación |
| M24 | The tab title follows the route | Walk `/app`, `/projects`, a project's team, a project's versions, `/editor`, `/admin/users`, an organization, and the sign-in page; each must read `<section> · EnerScope`, with the project or organization name inserted where the route is scoped to one | Real backend | Pendiente de verificación |
| M25 | The favicon renders at small sizes in both OS themes | Check the tab in a light and a dark OS appearance — **and note that it follows the OS, not the in-app theme toggle** (known debt). It is also PROVISIONAL artwork | None | Pendiente de verificación |
| M26 | The two themes hold | Switch Configuración between Claro, Oscuro and Sistema and walk the screens above: no hard-coded white or black surface, every control's border visible, focus rings visible on every interactive element | None | Pendiente de verificación |
| M27 | Keyboard-only pass over a dialog | Open any modal (Nuevo proyecto, Cambiar rol, Importar CSV): focus must land on the first field, `Tab` must not escape the panel, `Escape` must close it, and focus must return to the button that opened it | None | Pendiente de verificación |
| M28 | Field errors are announced | Submit an invalid form and check with a screen reader that the error is tied to its input (`aria-describedby`) and the input is marked `aria-invalid` | None | Pendiente de verificación |
| M29 | The sign-in hero's subtitle contrast | Measure it in the **light** theme at 1440px and wider; it currently reads 4.21:1 against a 4.5:1 bar (known debt) | None | Pendiente de verificación |
| M30 | A dropped connection mid-import is survivable | Start an import of more than 50 rows (so it goes out in several batches of 50), kill the backend halfway, and check: the progress stopped, the warning about possibly partial writes appears, the credentials of the batches already confirmed are downloadable, and the member list reflects whatever was created. Also press *Cancelar* mid-import and check the same | Real backend | Pendiente de verificación |
| M31 | Scenario comparison against real evaluations | Simulate two scenarios of a project (one deterministic, one probabilistic), open *Comparaciones*, compare them, save, reopen by URL and delete; the values must match each scenario's results screen and nothing may show `0` where the results screen says unavailable | Real backend (PostgreSQL, V25 applied) | API verified 2026-11-12 on PostgreSQL 16 with `seed-comparison-demo.mjs --simulate` (preview, save, read, delete, cross-project 404; that script was replaced by `seed-local-demo.mjs` on 2026-11-22); the screen itself was only checked against a mock API — UI pass on the real backend pending |
| M32 | The green sidebar, in both themes | In the light and the dark theme: rail `#114636`, white labels, section titles slightly dimmer, current row on a light-green pill with dark-green text and no side marker, its module (e.g. *Proyectos*) on the soft hover fill in semi-bold white, never a second pill; hover lightens the row; `Tab` shows a white ring on the rail and on a hovered row; *Proyectos* opened shows its children with a faint guide line; locked rows keep a white label with a dimmer icon and padlock. At ≥ `80rem` (always), the rail runs the full height with the logo, centred horizontally, in a cell as tall as the top bar, sitting 6px below that cell's centre (16px above it, 4px below) so it reads level between the top edge and *TRABAJO* without touching it and the top bar starts to its right; below `80rem` (the drawer, M18–M20) the logo is in the top bar | None | Pendiente de verificación |

## `economic.controller.EconomicControllerTest` — Web

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `invalidDraftReturns400` | invalid Draft Returns400. |
| `missingConfigurationReturns404` | missing Configuration Returns404. |
| `permissionDenialReturns403` | permission Denial Returns403. |
| `draftEndpointsUseEnvelopeAndAllowNoSavedDraft` | draft Endpoints Use Envelope And Allow No Saved Draft. |
| `invalidConfigurationReturns400` | invalid Configuration Returns400. |
| `anonymousDraftEndpointsReturn401` | anonymous Draft Endpoints Return401. |
| `unavailableIndicatorsExposeNullInsteadOfZero` | unavailable Indicators Expose Null Instead Of Zero. |
| `configurationRoundTripUsesEnvelope` | configuration Round Trip Uses Envelope. |
| `anonymousEvaluationReturns401` | anonymous Evaluation Returns401. |
| `evaluationReturnsCreatedAndHistoryCanBeRead` | evaluation Returns Created And History Can Be Read. |
| `allEvaluationEndpointsExposeIndicatorContract` | all Evaluation Endpoints Expose Indicator Contract. |
| `draftPermissionDenialReturns403` | draft Permission Denial Returns403. |

## `economic.service.EconomicEngineTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `partialDeductibilityMovesCashRemainderToEnai` | partial Deductibility Moves Cash Remainder To Enai. |
| `monthlyDepreciationRespectsServiceDateAndResidual` | monthly Depreciation Respects Service Date And Residual. |
| `disabledLossCarryForwardDoesNotRefundOrOffset` | disabled Loss Carry Forward Does Not Refund Or Offset. |
| `expiredOpeningLossDoesNotReduceFutureTax` | expired Opening Loss Does Not Reduce Future Tax. |
| `evaluationsAreDeterministic` | evaluations Are Deterministic. |
| `lossOffsetLimitPreservesUnusedLosses` | loss Offset Limit Preserves Unused Losses. |
| `zeroWaccEqualsSumOfCashFlows` | zero Wacc Equals Sum Of Cash Flows. |
| `overlappingTreatmentsAndInvalidDatesAreRejected` | overlapping Treatments And Invalid Dates Are Rejected. |
| `nonCashTaxableIncomeIsReversedInCashFlow` | non Cash Taxable Income Is Reversed In Cash Flow. |
| `nonCashEntriesNeverHavePaymentDates` | non Cash Entries Never Have Payment Dates. |
| `capexCannotBeDeductedTwice` | capex Cannot Be Deducted Twice. |
| `emptyYearsRemainInDiscountTimeline` | empty Years Remain In Discount Timeline. |
| `missingMetricsAndDuplicateMetricsAreRejected` | missing Metrics And Duplicate Metrics Are Rejected. |
| `partialBoundaryRetainsTransferWithExcludedCounterparty` | partial Boundary Retains Transfer With Excluded Counterparty. |
| `invalidCurrencyOwnershipAndUnitsAreRejected` | invalid Currency Ownership And Units Are Rejected. |
| `internalTransfersRemainTaxablePerEntityButDisappearOnConsolidation` | internal Transfers Remain Taxable Per Entity But Disappear On Consolidation. |
| `staticDriversUseExplicitQuantitiesAndRates` | static Drivers Use Explicit Quantities And Rates. |
| `completeExampleReconcilesEverySectionAndNpv` | complete Example Reconciles Every Section And Npv. |
| `taxPaymentLagCreatesCashAdjustmentAndPendingTax` | tax Payment Lag Creates Cash Adjustment And Pending Tax. |
| `zeroProductionPreservesFixedCosts` | zero Production Preserves Fixed Costs. |
| `receivableBeyondHorizonIsReportedWithoutTerminalRecovery` | receivable Beyond Horizon Is Reported Without Terminal Recovery. |

## `economic.service.EconomicIndicatorsCalculatorTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `zeroIrrIsAValidCalculatedRate` | zero Irr Is AValid Calculated Rate. |
| `iterationLimitReturnsNumericalFailure` | iteration Limit Returns Numerical Failure. |
| `expansionLimitReturnsNumericalFailureWithoutLosingPayback` | expansion Limit Returns Numerical Failure Without Losing Payback. |
| `flowsWithoutOppositeSignsHaveNoIrr` | flows Without Opposite Signs Have No Irr. |
| `displayedRoundingCannotCreateDiscountedRecovery` | displayed Rounding Cannot Create Discounted Recovery. |
| `missingWaccOnlyPreventsDiscountedPayback` | missing Wacc Only Prevents Discounted Payback. |
| `nonnegativeInitialCashMakesPaybackNotApplicable` | nonnegative Initial Cash Makes Payback Not Applicable. |
| `positiveIrrIsIndependentOfWacc` | positive Irr Is Independent Of Wacc. |
| `completeExampleMatchesIndependentIndicators` | complete Example Matches Independent Indicators. |
| `negativeIrrIsSupported` | negative Irr Is Supported. |
| `exactRecoveryReturnsFirstAnnualClose` | exact Recovery Returns First Annual Close. |
| `missingOrDiscontinuousPeriodsAreInsufficient` | missing Or Discontinuous Periods Are Insufficient. |
| `emptyIntermediateYearsKeepTheirDiscountExponent` | empty Intermediate Years Keep Their Discount Exponent. |
| `unsupportedSignPatternsDoNotChooseARoot` | unsupported Sign Patterns Do Not Choose ARoot. |
| `laterDeficitDoesNotReplaceFirstRecovery` | later Deficit Does Not Replace First Recovery. |
| `unrecoveredInvestmentHasNoInventedDate` | unrecovered Investment Has No Invented Date. |
| `zeroWaccMakesBothPaybacksIdentical` | zero Wacc Makes Both Paybacks Identical. |

## `economic.service.EconomicServiceTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `newEvaluationStoresVersionTwoAndSimulatesOnce` | new Evaluation Stores Version Two And Simulates Once. |
| `savedInvalidExportCannotRunSimulation` | saved Invalid Export Cannot Run Simulation. |
| `draftCannotLeakAcrossProjects` | draft Cannot Leak Across Projects. |
| `absentHistoricalPeriodsAreInsufficientInListAndDetail` | absent Historical Periods Are Insufficient In List And Detail. |
| `outsiderCannotReadOrWriteConfiguration` | outsider Cannot Read Or Write Configuration. |
| `invalidDraftStructureIsRejectedBeforePersistence` | invalid Draft Structure Is Rejected Before Persistence. |
| `historyDerivesFromSnapshotWithoutSimulationConfigurationLookupOrWrites` | history Derives From Snapshot Without Simulation Configuration Lookup Or Writes. |
| `missingHistoricalWaccDoesNotUseCurrentConfiguration` | missing Historical Wacc Does Not Use Current Configuration. |
| `viewerReadsDraftButCannotSaveIt` | viewer Reads Draft But Cannot Save It. |
| `editorCanSaveValidatedConfiguration` | editor Can Save Validated Configuration. |
| `exportOnWellIsRejectedBeforeSaving` | export On Well Is Rejected Before Saving. |
| `exportOnCarrierCanBeSaved` | export On Carrier Can Be Saved. |
| `adminStillCannotAddressVersionInAnotherProject` | admin Still Cannot Address Version In Another Project. |
| `evaluationIsScopedToVersion` | evaluation Is Scoped To Version. |
| `foreignNodeIsRejectedBeforePersistence` | foreign Node Is Rejected Before Persistence. |
| `viewerCanReadButCannotEvaluate` | viewer Can Read But Cannot Evaluate. |
| `truncatedHistoryReturnsInsufficientDataWithoutFillingMissingYears` | truncated History Returns Insufficient Data Without Filling Missing Years. |
| `validatedConfigurationSaveClearsDraft` | validated Configuration Save Clears Draft. |
| `missingHistoricalResultOrConfigurationReturnsInsufficientData` | missing Historical Result Or Configuration Returns Insufficient Data. |
| `storedIndicatorsAreReturnedWithoutRecalculation` | stored Indicators Are Returned Without Recalculation. |
| `editorSavesIncompleteDraftWithoutChangingConfigurationOrSimulating` | editor Saves Incomplete Draft Without Changing Configuration Or Simulating. |
| `absentHistoricalPeriodFieldsDoNotBecomeDefaultZeroes` | absent Historical Period Fields Do Not Become Default Zeroes. |

## `economic.service.EconomicSimulationAdapterTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `unknownRawUnitsAreRejected` | unknown Raw Units Are Rejected. |
| `actualAnnualSimulationFeedsCompleteExampleAndRepeatsWithoutStateLeak` | actual Annual Simulation Feeds Complete Example And Repeats Without State Leak. |

## `organization.repository.OrganizationMemberRepositoryTest` — Data

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `excludesUsersAlreadyOnTheProject` | excludes Users Already On The Project. |
| `excludesInactiveOrganizationMemberships` | excludes Inactive Organization Memberships. |
| `keepsUsersWhoAreOnlyOnAnotherProjectOfTheSameOrganization` | keeps Users Who Are Only On Another Project Of The Same Organization. |
| `filtersByFirstNameLastNameOrMailIgnoringCase` | filters By First Name Last Name Or Mail Ignoring Case. |
| `excludesUsersWhoseProjectMembershipIsInactive` | excludes Users Whose Project Membership Is Inactive. |
| `excludesInactiveUsers` | excludes Inactive Users. |
| `excludesUsersWhoAreOnlyInAnotherOrganization` | excludes Users Who Are Only In Another Organization. |
| `ordersByLastNameThenFirstName` | orders By Last Name Then First Name. |
| `returnsNothingForAnotherProjectsOrganization` | returns Nothing For Another Projects Organization. |
| `treatsPercentAndUnderscoreInTheEscapedPatternAsLiterals` | treats Percent And Underscore In The Escaped Pattern As Literals. |
| `returnsTheActiveOrganizationMembersWhoAreNotOnTheProject` | returns The Active Organization Members Who Are Not On The Project. |

## `simulator.EconomicMetricsTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `compressorCountsNewOutputEvenWhenPreviousOutputRemains` | compressor Counts New Output Even When Previous Output Remains. |
| `shipmentCountsDepartureOnceAndDoesNotSellPartialLoading` | shipment Counts Departure Once And Does Not Sell Partial Loading. |
| `consumptionNodesKeepTheirAnnualCountersInEconomicSimulations` | consumption Nodes Keep Their Annual Counters In Economic Simulations. |
| `simulatorRejectsInvalidHorizons` | simulator Rejects Invalid Horizons. |
| `treatmentCountsSmallerNewBatchWithoutSubtractingPreviousBatch` | treatment Counts Smaller New Batch Without Subtracting Previous Batch. |
| `simulatorRejectsRepeatedExecutionInsteadOfAccumulatingResults` | simulator Rejects Repeated Execution Instead Of Accumulating Results. |
| `pipelineConnectionTransfersActualQuantityAndHonorsPredecessorOrder` | pipeline Connection Transfers Actual Quantity And Honors Predecessor Order. |

## `simulator.ResultTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `testResultIndustrialConsumption` | test Result Industrial Consumption. |
| `testResultInternalConsumption` | test Result Internal Consumption. |
| `testResultWell` | test Result Well. |
| `testResultSeaportTerminal` | test Result Seaport Terminal. |
| `testResultLNGCarrier` | test Result LNGCarrier. |
| `testResultPipeline` | test Result Pipeline. |
| `testResultTreatmentPlant` | test Result Treatment Plant. |
| `testResultGatheringNetwork` | test Result Gathering Network. |
| `testResultLiquefactionPlant` | test Result Liquefaction Plant. |
| `testResultCompressingPlant` | test Result Compressing Plant. |

## `simulator.SimulatorTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `testSimTreatmentPlant` | test Sim Treatment Plant. |
| `testSimGatheringNetworkCapacity` | test Sim Gathering Network Capacity. |
| `testSimLiquefactionPlant` | test Sim Liquefaction Plant. |
| `testSimulationWhitNNodes` | test Simulation Whit NNodes. |
| `testSimulationSimpleComplete` | test Simulation Simple Complete. |
| `testSimPipeline` | test Sim Pipeline. |
| `testSimCompressingPlant` | test Sim Compressing Plant. |
| `testSimWellProductionAndDecline` | test Sim Well Production And Decline. |
| `testSimLNGCarrier` | test Sim LNGCarrier. |
| `testResultInFiveYearSimulationWithAllNodeTypes` | test Result In Five Year Simulation With All Node Types. |
| `testSimWellActiveAction` | test Sim Well Active Action. |
| `testSimSeaportTerminal` | test Sim Seaport Terminal. |

## `version.controller.VersionQueryControllerTest` — Web

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `authorizedRequestReturnsSummaryEnvelope` | authorized Request Returns Summary Envelope. |
| `missingProjectReturns404` | missing Project Returns404. |
| `anonymousRequestReturns401` | anonymous Request Returns401. |
| `forbiddenRequestReturns403` | forbidden Request Returns403. |

## `version.service.VersionQueryServiceTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-08 merge:

| Case | Verifies |
| --- | --- |
| `anonymousCannotListVersions` | anonymous Cannot List Versions. |
| `viewerGetsOnlyActiveVersionsNewestFirst` | viewer Gets Only Active Versions Newest First. |
| `inactiveRoleCannotListVersions` | inactive Role Cannot List Versions. |
| `anotherProjectDoesNotLeakVersions` | another Project Does Not Leak Versions. |
| `summaryIncludesDiagramMetadataAndEconomicNodesTogether` | summary Includes Diagram Metadata And Economic Nodes Together. |
| `editPermissionAloneDoesNotGrantView` | edit Permission Alone Does Not Grant View. |
| `adminCanListWithoutMembershipAndReceivesNodeSummary` | admin Can List Without Membership And Receives Node Summary. |
| `outsiderCannotListVersions` | outsider Cannot List Versions. |
| `missingProjectReturnsNotFound` | missing Project Returns Not Found. |
| `inactiveMembershipCannotListVersions` | inactive Membership Cannot List Versions. |

## Frontend merge verification

`npm test`: 24 cases (18 economic editor/formatting and 6 operational-result cases). Build and lint passed. Browser acceptance scripts passed with isolated API fixtures, including the new organizations/owned request, draft save/reload, simulation validation, permissions, project isolation, historical results and responsive layout.

## `version.service.VersionConflictIntegrationTest` — Integration

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-12 merge:

| Case | Verifies |
| --- | --- |
| `repeatedMergesTouchingTheSameIdentityCollapseToOneNetNodeChange` | repeated Merges Touching The Same Identity Collapse To One Net Node Change. |
| `mergingANewlyAddedNodeFlagsAConflictWithSiblingsThatDontHaveIt` | merging ANewly Added Node Flags AConflict With Siblings That Dont Have It. |
| `getAllConflicts_ReturnsConflictsAcrossAllVersions` | get All Conflicts Returns Conflicts Across All Versions. |
| `mergingOneChildFlagsAConflictOnTheSiblingThatTouchedTheSameIdentity` | merging One Child Flags AConflict On The Sibling That Touched The Same Identity. |
| `mergingASiblingThatNeverTouchedAnythingDoesNotRevertWhatAnEarlierMergeAdded` | merging ASibling That Never Touched Anything Does Not Revert What An Earlier Merge Added. |
| `editingAnInheritedNodeInOneChildDoesNotMutateTheParentOrSiblingRows` | editing An Inherited Node In One Child Does Not Mutate The Parent Or Sibling Rows. |
| `editingAnInheritedConnectionInOneChildDoesNotMutateTheParentOrSiblingRows` | editing An Inherited Connection In One Child Does Not Mutate The Parent Or Sibling Rows. |

## `version.service.VersionConflictServiceTest` — Unit

| Case | Verifies |
| --- | --- |

Additional cases reconciled during the 2026-10-12 merge:

| Case | Verifies |
| --- | --- |
| `resolveConflict_NodeAccept_BothHaveIt_CopiesDataKeepsSiblingId` | resolve Conflict Node Accept Both Have It Copies Data Keeps Sibling Id. |
| `resolveConflict_ConnectionAccept_BothHaveIt_RemapsEndpointsThroughIdentity` | resolve Conflict Connection Accept Both Have It Remaps Endpoints Through Identity. |
| `recordMergeConflicts_SourceWithoutChanges_DoesNotQuerySiblings` | record Merge Conflicts Source Without Changes Does Not Query Siblings. |
| `recordMergeConflicts_SiblingNeverTouchedIdentityButHasNoRowForIt_StillCreatesConflict` | record Merge Conflicts Sibling Never Touched Identity But Has No Row For It Still Creates Conflict. |
| `recordMergeConflicts_SiblingHasADifferentConnectionForATouchedIdentity_CreatesConflict` | record Merge Conflicts Sibling Has ADifferent Connection For ATouched Identity Creates Conflict. |
| `recordMergeConflicts_SiblingAlreadyHasTheIdenticalMergedRow_CreatesNothing` | record Merge Conflicts Sibling Already Has The Identical Merged Row Creates Nothing. |
| `resolveConflict_NodeAccept_OnlySiblingHasIt_RemovesFromSnapshot` | resolve Conflict Node Accept Only Sibling Has It Removes From Snapshot. |
| `getConflictsForVersion_ReturnsMappedConflicts` | get Conflicts For Version Returns Mapped Conflicts. |
| `resolveConflict_NodeReject_OnlyParentHasIt_WritesDeleteChange` | resolve Conflict Node Reject Only Parent Has It Writes Delete Change. |
| `resolveConflict_NodeReject_OnlySiblingHasIt_WritesAddChange` | resolve Conflict Node Reject Only Sibling Has It Writes Add Change. |
| `recordMergeConflicts_SiblingHasADifferentRowForATouchedIdentity_CreatesOneConflictPerIdentity` | record Merge Conflicts Sibling Has ADifferent Row For ATouched Identity Creates One Conflict Per Identity. |
| `resolveConflict_ConnectionReject_OnlyParentHasIt_WritesDeleteChange` | resolve Conflict Connection Reject Only Parent Has It Writes Delete Change. |
| `resolveConflict_UnknownConflict_Throws` | resolve Conflict Unknown Conflict Throws. |
| `getConflictsForVersion_UnknownVersion_Throws` | get Conflicts For Version Unknown Version Throws. |
| `resolveConflict_NodeReject_BothHaveIt_WritesEditChange` | resolve Conflict Node Reject Both Have It Writes Edit Change. |
| `recordMergeConflicts_SiblingChangedDifferentIdentity_CreatesNothing` | record Merge Conflicts Sibling Changed Different Identity Creates Nothing. |
| `resolveConflict_NodeAccept_OnlyParentHasIt_ClonesIntoSiblingSnapshot` | resolve Conflict Node Accept Only Parent Has It Clones Into Sibling Snapshot. |

## `node.migration.ExportVerticalMigrationTest` — Data (JDBC/H2)

Executes the actual V15 SQL resource against an isolated H2 table in PostgreSQL mode.

| Case | Verifies |
| --- | --- |
| `normalizesLegacyExportsWithoutChangingOtherNodeData` | Converts active and inactive legacy Cyrillic exports to the current Java enum; preserves identifiers, active flags, current exports and all other verticals. |
| `migrationAcceptsFreshDatabaseWithoutNodes` | The migration succeeds on an empty table without creating data. |

### Investment filter and duplicate actions (browser acceptance)

`frontend/tests/economic-browser.mjs` additionally checks node/general filtering without dirtying the draft; saving hidden investments through the lower bar; editing node membership; empty matches; adding under the selected node; validating hidden invalid assets through the upper bar and clearing the filter; mobile width; and viewer restrictions on both action bars. Existing save/error/double-submit checks use the upper bar.

### Economic result presentation — 2026-10-13

Frontend total: 26 unit cases (20 economic editor/formatting, 6 operational).
- economicDraft.test.mjs — unit: NPV display rounds upward without changing cash flow precision: positive/negative/integer VAN, missing values, and unchanged monetary precision.
- economicDraft.test.mjs — unit: calculated annual IRR displays exactly two percentage decimals: fractional, zero, negative and missing rates.
- Existing payback status test now expects capitalized Año.
- results-browser.mjs — browser acceptance: rounded VAN/TIR and payback label; positive/negative bar tooltip on hover, keyboard focus and dismissal; no backend mutations.

- results-browser.mjs — browser acceptance: cash-flow entry animation, tooltip follows pointer movement, reduced-motion disables animation; existing keyboard and responsive checks retained. Unit case count remains 26.

- results-browser.mjs — browser acceptance: operational chart animates on tab entry and node change, reduced motion suppresses it, and hours appear next to the accumulated value. Unit test count remains 26.

- operationalResults.test.mjs — unit: converts saved volumes and totals without changing hours or source metrics; different metric conversions preserve unknown losses and missing values. Total: 28 unit cases (20 economic, 8 operational). Browser acceptance verifies converted totals and units.

### Economic summary redesign — 2026-10-13

Frontend total: 30 unit cases (20 economic editor/formatting, 8 operational, 2 chart).
- economicChart.test.mjs — unit: cash flow scale contains mixed, positive, negative and zero flows with a finite baseline.
- economicChart.test.mjs — unit: recovery timeline merges coincident recoveries and excludes unavailable or out of horizon markers.
- results-browser.mjs — browser: grouped summary, collapsible accounting detail and preserved chart animation, tooltip, keyboard and responsive behavior.

- results-browser.mjs — browser: ten-year evaluation has 11 bars; coincident recoveries share a marker; unavailable TIR and payback preserve explanations, negative VAN retains its minus sign, and no recovery marker is invented. Mobile overflow checked for both horizons.

- results-browser.mjs — browser: positive VAN label, negative VAN label/red background, neutral zero label and missing VAN fallback. Unit case count remains 30.

- results-browser.mjs — browser: operational annual detail starts collapsed, opens with a click, toggles with Enter and exposes historical node values on demand. Unit case count remains 30.

- results-browser.mjs — browser: operational bar tooltip shows saved year, metric, converted value/unit; follows pointer movement, dismisses on leave, supports keyboard focus on exports and Escape dismissal. Unit case count remains 30.

## Probabilistic economic integration — 2026-10-14

Validated counts: **711 default backend tests in 46 classes**, plus **4 explicit PostgreSQL tests**, with zero failures/errors/skips. Frontend: **34 unit cases**, plus three browser workflows. This summary supersedes older counts while preserving their history.

### Backend counts from isolated Surefire reports

| Class | Cases |
|---|---:|
| ApplicationContextTest | 1 |
| auth.controller.AuthControllerTest | 16 |
| common.CsvUtilTest | 5 |
| economic.controller.EconomicControllerTest | 12 |
| economic.controller.EconomicJobControllerTest | 4 |
| economic.service.EconomicEngineTest | 21 |
| economic.service.EconomicIndicatorsCalculatorTest | 17 |
| economic.service.EconomicJobServiceTest | 8 |
| economic.service.EconomicJobWorkerTest | 4 |
| economic.service.EconomicServiceTest | 22 |
| economic.service.EconomicSimulationAdapterTest | 2 |
| economic.service.EconomicStatisticsCalculatorTest | 5 |
| economic.service.PostgreSqlEconomicIT | 4 |
| economic.service.ProbabilisticSimulationTest | 8 |
| jwt.JwtServiceTest | 5 |
| logging.ConsoleAppLoggerTest | 1 |
| money.MoneyAmountTest | 7 |
| node.controller.NodeControllerTest | 1 |
| node.migration.ExportVerticalMigrationTest | 2 |
| node.service.NodeServiceTest | 2 |
| organization.controller.OrganizationControllerTest | 39 |
| organization.repository.OrganizationMemberRepositoryTest | 11 |
| organization.repository.OrganizationRepositoryTest | 16 |
| organization.service.OrganizationBulkRegistrationServiceTest | 13 |
| organization.service.OrganizationServiceTest | 68 |
| project.controller.ProjectControllerTest | 43 |
| project.repository.ProjectMemberRepositoryTest | 16 |
| project.repository.ProjectRepositoryTest | 17 |
| project.service.ProjectAccessGuardTest | 26 |
| project.service.ProjectServiceTest | 83 |
| simulator.EconomicMetricsTest | 7 |
| simulator.ResultPerRoundTest | 12 |
| simulator.SimulatorTest | 15 |
| strategyCost.CostBasisCalculatorsTest | 10 |
| strategyCost.CostTest | 8 |
| strategyCost.InvestmentCostTest | 2 |
| user.controller.UserControllerTest | 30 |
| user.repository.UserRepositoryTest | 22 |
| user.service.PasswordGeneratorTest | 4 |
| user.service.UserServiceTest | 58 |
| util.AuthUtilTest | 7 |
| version.controller.VersionControllerTest | 10 |
| version.controller.VersionQueryControllerTest | 4 |
| version.service.VersionConflictIntegrationTest | 7 |
| version.service.VersionConflictServiceTest | 17 |
| version.service.VersionQueryServiceTest | 10 |
| version.service.VersionServiceTest | 15 |

### New and expanded case catalog

#### economic.controller.EconomicJobControllerTest — WebMvcTest with real security filter (4 cases)

| Case | Verifies |
|---|---|
| anonymousRequestsCannotReadOrStartJobsOrRounds | All six new routes require authentication. |
| acceptanceReturns202AndSeedAsStringWithoutWaitingForResults | HTTP 202 returns the envelope, QUEUED state and exact seed string. |
| missingJobPermissionAndInvalidOptionsUseGlobalErrors | 404/403/400 use the global error envelope. |
| readRoutesAndRetryPreserveScopedIdentifiersAndEnvelopes | Job/round routes preserve scoped IDs and response contracts. |

#### economic.service.EconomicJobServiceTest — Unit (8 cases)

| Case | Verifies |
|---|---|
| submissionFreezesInputsAndQueuesWithoutPublishingEvaluation | Submission applies EDIT_PROJECT, defaults and seed text without publishing results. |
| roundsMustBeSequentialAndCompletionRequiresAllRounds | Progress rejects duplicates/out-of-order rounds; completion requires all rounds and survives late failure. |
| successfulAtomicClaimReturnsOnlyFrozenInput | A successful claim reconstructs saved inputs without current configuration reads. |
| invalidExecutionOptionsAreRejectedWithoutSilentCoercion | Round limits and signed 64-bit seed boundaries reject invalid inputs. |
| retryUsesFrozenInputAndSeedWithoutReadingCurrentConfiguration | Retry starts at zero with unchanged JSON/seed, without reading current configuration. |
| runningJobCannotBeRetried | Only failed/interrupted jobs can be repeated. |
| readsRequireViewPermissionAndScopeJobToVersion | List/detail require VIEW_PROJECT and version-scoped lookup. |
| lostAtomicClaimDoesNotStartDuplicateWork | A losing conditional claim cannot start duplicate execution. |

#### economic.service.EconomicJobWorkerTest — Unit (4 cases)

| Case | Verifies |
|---|---|
| physicalFailureKeepsCompletedRoundsButNeverPublishesPartialEvaluation | A physical error stops execution and never publishes partial statistics. |
| nonCalculableIrrDoesNotFailAnOtherwiseValidJob | Unavailable indicators do not fail a valid physical/economic execution. |
| startupInterruptsUnfinishedJobsBeforePollingAndEmptyQueueDoesNothing | Startup interruption precedes queue polling; empty queues never simulate. |
| completedJobSimulatesEachRoundOnceAndPublishesAfterSavingAllRounds | Each round runs once with its own seed; publication follows all round saves. |

#### economic.service.EconomicStatisticsCalculatorTest — Unit (5 cases)

| Case | Verifies |
|---|---|
| summaryUsesEachRoundsOwnIndicatorsAndShowsInvalidCounts | Conditional IRR/Payback denominators exclude unresolved outcomes but retain status counts. |
| emptyAndSingleObservationStatisticsAreExplicit | Empty samples yield null values; single observations are preserved. |
| nearestRankPercentilesDoNotInterpolateAndRetainTies | Ascending nearest-rank percentiles preserve ties without interpolation. |
| uncomputablePaybacksHaveNoInventedRecoveryProbability | Zero applicable observations produce null probability. |
| perRoundTaxationIsNotTaxationOfMeanProduction | Nonlinear taxes make mean round NPV differ from NPV on mean production. |

#### economic.service.PostgreSqlEconomicIT — PostgreSQL/JPA/MockMvc integration (4 cases)

| Case | Verifies |
|---|---|
| completeHttpExampleCreatesVersionNodesAndEvaluation | HTTP creates the example, evaluates synchronously, accepts/claims a two-round job once, and reads persisted results after worker execution without HTTP authentication. |
| restartInterruptionAndRetryPreserveFrozenInputs | PostgreSQL interrupts queued/running work; retry preserves JSON/seed and completes despite changed current inputs. |
| migrationsValidateAndEconomicSnapshotSurvivesReloadAndConfigurationChanges | Fresh migrations and Hibernate validation preserve snapshots and percentile result mappings across reload/configuration changes. |
| historicalIndicatorsAreDerivedWithoutUpdatingPersistedJson | Historical enrichment uses saved inputs without changing stored JSON. |

#### economic.service.ProbabilisticSimulationTest — Unit (8 cases)

| Case | Verifies |
|---|---|
| graphRejectsCyclesMissingReferencesAndDuplicateEdges | Invalid graphs fail before execution. |
| distributionSamplesAreIndependentOfEntitySamplerState | Seeded generators are independent of entity sampling; invalid uniform parameters fail. |
| snapshotRoundTripPreservesDistributionsAndIgnoresLaterPhysicalEdits | Frozen normal parameters survive serialization and later physical edits, including a minimum signed seed. |
| constantRoundsPreserveTheCompleteIndependentExample | Every constant round preserves NPV 169.20, IRR 0.1483447840 and recovery years 2034/2035. |
| declineUsesOperatingAgeAndClampsEachSample | Decline applies at completed operating-age years and clips negative/excess samples. |
| seedReproducesRoundAndDifferentRoundSeedsChangeAnnualQuantities | Identical seeds reproduce metrics; distinct round seeds change quantities without mutating the source. |
| reorderingNodesDoesNotChangeTheirRandomStreams | Node collection order does not change seeded results. |
| annualBoundsAndDelayedStartupRemainExact | Operational years contain exactly 8760 hours, with zero hours before delayed startup. |

### Existing tests adapted to the probabilistic merge

- EconomicServiceTest retains 22 cases; single-execution verification now uses the seeded overload and frozen inputs.
- EconomicSimulationAdapterTest retains 2 cases and checks that managed Version final results remain untouched.
- EconomicMetricsTest retains 7 cases: duration calls use years, node steps use hours; annual metrics verify 8760 hours and reject invalid horizons.
- SimulatorTest retains 15 cases: assertions inspect one physical realization; the random graph fixture excludes duplicate edges.
- ResultPerRoundTest retains 12 cases: testResultPipeline checks delivered material, inventory and in-transit quantities; testResultLiquefactionPlant and testResultFLNGUnit check hourly input after fuel/contaminant removal and conversion; testResultSeaportTerminal checks cargo plus remaining inventory.

### Frontend unit cases — probabilisticResults.test.mjs (4)

| Case | Verifies |
|---|---|
| execution accepts default range and preserves 64-bit seed strings | Defaults/boundaries and seed precision. |
| execution rejects invalid round counts and unsafe seed syntax | Fractional/out-of-range counts and invalid/overflow seeds fail. |
| only unfinished jobs are polled | Only QUEUED/RUNNING remain pending. |
| round selection keeps immutable historical metadata and replaces both result tabs together | Selected physical/economic results share one round while preserving common input metadata. |

### Browser acceptance

- probabilistic-browser.mjs: progress and navigation; polling stops at completion; denominators and seed text; default summary; shared round across tabs; saved node name; round-load error; interrupted-job retry; desktop/mobile screenshots and no content overflow.
- economic-browser.mjs: asynchronous submission and explicit history refresh; existing save-failure, duplicate-click, drafts, dirty-navigation, filters and permission checks.
- results-browser.mjs: legacy individual snapshots, charts, unit conversions, tooltips, keyboard/reduced-motion and mobile behavior remain covered.

### Validation environment

Maven used an otherwise identical temporary POM with isolated output target/probabilistic-20261014-clean, excluding obsolete merged classes/resources. The temporary POM is not part of the deliverable. PostgreSQL used a new disposable enerscope_economic_test_probabilistic_20261014b database, without Flyway repair or resets of existing databases. Frontend tests/build/lint and all three browser workflows passed. Vite retains its large-bundle advisory. The physical well editor is explicitly excluded by the user.

- Final review: existing historyDerivesFromSnapshotWithoutSimulationConfigurationLookupOrWrites also verifies preservation of simulation metadata during in-memory enrichment. probabilistic-browser.mjs additionally checks polling pauses while hidden/resumes when visible, and retry after FAILED as well as INTERRUPTED. Case counts remain unchanged.


## Operational node editor separation

Frontend unit/component suite: 37 cases total (previous 34 plus 3), no failures or skips. New file: frontend/tests/nodeOperationalEditor.test.mjs.

| Case | Type | Verification |
| --- | --- | --- |
| node create and edit forms expose operational fields only for every node type | Component rendering | All nine node types omit monetary inputs in creation and edition, retaining operational fields and guidance to Scenarios. |
| node detail panels omit legacy monetary fields while retaining physical data | Component rendering | All nine node detail panels omit monetary fields and retain lifespan. |
| operational saves retain hidden legacy costs and complete subtype payloads | Unit | Operational edits preserve hidden costs, subtype fields and source inputs. |

Frontend production build and lint passed. Backend code and tests are unchanged by this UI-only adjustment.

- 2026-10-14 — economic-browser.mjs: Scenario entry regression checks both the scenario name and Configurar y simular open configuration, all five editor sections remain reachable, and job submission follows configuration save with the expected rounds/seed payload.

- 2026-10-14 — advancedEconomicDraft.test.mjs (unit, 2 cases): fiscal edits preserve unrelated policies and immutable source; contract edits preserve deferred schedules and unknown fields. economic-browser.mjs now checks multi-entity configuration editing, complete-payload preservation, reload and asynchronous submission.

- advancedEconomicDraft.test.mjs: third unit case verifies asset concept renaming preserves matching capital and depreciation tax treatments. Frontend unit total: 40.

- 2026-10-15 — Runtime upgrade acceptance: clone the populated October 12 database, apply V16/V17 with Flyway validation, verify legacy well decline preservation, authenticate and submit the existing five-year scenario to evaluation-jobs through completion.

- 2026-10-15 — Upgrade verification passed: historical Flyway checksums validated; V16–V18 applied; three legacy well decline values preserved as constants; backend test suite passed with fixture-compatible container directory layout. Live browser acceptance returned HTTP 202 from Guardar y simular, completed 10/10 rounds and rendered probabilistic results for the existing five-year scenario.


## Project isolation and scenario creation

- ProjectServiceTest.saveVersionRejectsParentFromAnotherProject (unit): rejects foreign parent IDs before copying or saving.
- ProjectServiceTest.saveVersionAcceptsParentFromSameProject (unit): accepts a persisted same-project parent and attaches the child. ProjectServiceTest: 83 cases.
- projectScoping-browser.mjs (browser acceptance): toolbar/header synchronization, late version response rejection, empty-project creation payload, navigation to economic configuration, and map selection restricted to the owning project. Uses isolated fixtures; no live scenario is created.
- Frontend: 40 unit tests, production build and lint. Economic browser regression also covers viewer permissions and advanced configuration preservation.
- Backend validation: 713 tests, zero failures/errors/skips, using the previously running enerscope-build-oct15 source plus the two changed project service files. A clean build of the current checkout is blocked by pre-existing missing org.enerscope.probabilistic classes and incompatible physical model sources. The local API uses that validated baseline with the project isolation patch; source files outside this change were not restored or overwritten.

- Live acceptance: Prueba1 returns HTTP 200 with zero versions; editor selects its project ID and displays No versions; Nuevo escenario opens the creation form. No live test scenarios were saved. The stopped local PostgreSQL container was started before checking the API.


## Separate scenario configuration and simulation

Updated totals (superseding previous totals without removing their historical context):
**730 default backend cases**, **13 explicit PostgreSQL cases**, and **45 frontend
unit/component cases**. No failures, errors or skipped tests. Backend output uses
an isolated target directory; the application POM is unchanged. PostgreSQL uses
enerscope_economic_test_scenario_20261020 only. Frontend npm test/build/lint and
four browser scripts passed. Vite reports its existing large-chunk advisory.

### ScenarioOperationsTest — unit, 5 cases

| Case | Verification |
| --- | --- |
| physicalEditPreservesCostsGeometryAndOriginalNode | Physical edit preserves inherited costs, geometry, inactive state and the original row. |
| distributionsRoundTripWithoutSamplingOrSharedMutation | Constant, normal and uniform parameters round-trip into independent distributions. |
| rejectsNonfiniteNegativeAndFractionalIntegerValues | Rejects nonfinite, negative, fractional and oversized integer inputs. |
| rejectsUnknownFieldsAndInvalidDistributionBounds | Rejects monetary/unknown fields and inverted distribution limits. |
| remappingChangesOnlyNodeReferences | Remaps nested nodeId/deliveryNodeId without rewriting concept strings. |

### ScenarioConfigurationTest — Spring/H2 transactional integration, 8 cases

| Case | Verification |
| --- | --- |
| publishesPhysicsAndEconomicsTogetherAndRemapsReferencesWithoutMutatingParent | Joint publication replaces physical rows and economic references without changing the parent. |
| incompleteSavePreservesPublishedValuesAndBlocksJobUntilDiscarded | Incomplete operations save only a draft and prevent a new job. |
| draftSurvivesReloadAndDiscardEnablesSimulation | Draft survives persistence reload; explicit discard restores readiness and job acceptance. |
| mapEditAppearsInConfigurationAndRejectsStaleSave | Map edits appear in shared configuration and stale saves return a conflict. |
| persistedDraftKeepsOriginalSourceAfterMapEdit | Draft source remains unchanged when the map changes published data. |
| fullMapEditRemapsEconomicAndConnectionReferences | Map copy-on-write preserves old rows and updates economic and connection references in the edited version. |
| malformedEditorIsRejectedWithoutPersistingDraft | Rejects missing mode and malformed simple/advanced editor structures without storing them. |
| rejectsForeignVersionAndUnauthorizedUser | Rejects foreign versions and missing authentication. |

### ScenarioConfigurationControllerTest — MVC/security, 4 cases

| Case | Verification |
| --- | --- |
| anonymousReadAndSaveAreRejected | Authentication is required before calling the service. |
| readAndReadinessUseResponseEnvelope | Both read routes retain ApiResponse data envelopes. |
| staleSaveReturnsConflictAndPermissionFailureReturnsForbidden | Stale content returns 409; service permission failures return 403. |
| discardRequiresAndPassesConcurrencyToken | If-Match reaches discard; missing header returns 400. |

### ScenarioConfigurationPostgreSqlIT — PostgreSQL/Flyway, 9 cases

Runs all eight ScenarioConfigurationTest cases above on PostgreSQL with real
migrations and Hibernate schema validation. Additional case:
`baselineIsIgnoredForExistingHistoryAndUpgradePreservesData` creates an isolated
schema with a V18 history fixture and witness row, applies only V19, verifies the
row survives, B18 is ignored and a second migrate applies nothing. The fixture
schema is dropped afterwards. Existing PostgreSqlEconomicIT retains its four
economic/API/history cases, giving 13 explicit integration executions.

### scenarioConfiguration.test.mjs — frontend unit, 5 cases

| Case | Verification |
| --- | --- |
| new configuration reads shared operational values and preserves incomplete legacy drafts | Initializes operations from shared nodes while recovering raw legacy inputs. |
| joint draft keeps its original source token after map changes | Restores draft data without replacing its original source token. |
| complete simple saves publish economics while incomplete saves retain raw input only | Publishes valid configuration or retains incomplete text as draft. |
| advanced saves preserve contracts and ownership without simple conversion | Keeps the entire advanced aggregate intact. |
| remote changes refresh clean forms and conflict with local or saved drafts | Distinguishes automatic refresh from a publication-blocking conflict. |

### Browser acceptance

- economic-browser.mjs: separate screens and active sidebar; configuration has no execution controls; joint save and preserved distributions; focus refresh; dirty conflict and explicit discard; draft persistence and execution blocking; rejected save preserves input; navigation guard; modal cancel/default rounds/seed boundaries; one submission with no configuration save; advanced contracts/ownership; read-only permissions; mobile overflow. Screenshots inspected for configuration, operation, simulation dialog and mobile configuration.
- projectScoping-browser.mjs: existing project/header isolation and stale response checks retained; new scenarios open Configuration; map selection remains scoped to project.
- results-browser.mjs: existing economic/operational results, historical compatibility, node filters, keyboard navigation and responsive layouts retained.
- probabilistic-browser.mjs: progress, visible-only polling, summary, shared round selection, failures/retries and responsive layouts retained.

Browser acceptance uses isolated HTTP fixtures, not mutations of live scenarios.
No existing database was repaired, reset or migrated during this verification.


## Simple economic form and restored examples

Frontend total: **48 cases** (scenarioConfiguration.test.mjs: 8), no failures or skips; production build and lint passed. Backend code is unchanged in this follow-up (last validation: 730 default and 13 PostgreSQL cases).

New unit cases in scenarioConfiguration.test.mjs:
- `simple copy leaves the source untouched and produces a separate single-entity aggregate`: verifies immutable source, independent IDs, unique concepts and exclusion of internal transfers.
- `simple copy with different tax rates requires an explicit choice`: leaves tax rate incomplete instead of guessing.
- `simple copy remaps physical references and preserves gaps in recognition years`: verifies target-node mapping, nonconsecutive income years and omission of nonrepresentable operating t=0 movements.

economic-browser.mjs replaces advanced editing acceptance with preserved-original summary, absence of advanced fields, confirmed copy creation, simple-form navigation and immutable source; it simulates new physical UUIDs and retains conflict/draft/mobile/permission checks. projectScoping-browser.mjs remains passing. Live read-only browser acceptance verifies restored five/ten-year scenarios and the new simple copy; original scenario/evaluation IDs were preserved.

## Optional project descriptions and numeric editing
Validation: 733 backend tests and 50 frontend tests passed, without failures or skips. Frontend production build and lint passed.

Updated backend case counts: ProjectControllerTest and ProjectServiceTest are recorded by the current Surefire reports; additions are two controller cases and one service case (the clearing-description case replaces the old rejection case).
- ProjectControllerTest (MVC): `createProjectAcceptsMissingOrBlankDescription` accepts omitted, null and empty descriptions.
- ProjectControllerTest (MVC): `createProjectRejectsOverlongDescription` preserves the 500-character limit.
- ProjectServiceTest (unit): `createProjectNormalizesMissingAndBlankDescription` persists an empty string for missing or blank descriptions.
- ProjectServiceTest (unit): `updateProjectAllowsClearingDescription` replaces `updateProjectRejectsABlankDescription` and verifies clearing an existing description.
- nodeOperationalEditor.test.mjs (component; 5 cases total): `new node numeric fields start blank except the maintenance default` checks every node type.
- nodeOperationalEditor.test.mjs (component): `editing a node preserves saved zero and nonzero numeric values` verifies stored values remain visible.
- formInputs-browser.mjs (browser acceptance): creates a project without description, clears numeric inputs with the keyboard, enters decimals and verifies numeric submission. HTTP fixtures isolate this test from live user data.

Current per-class totals: ProjectControllerTest **45**, ProjectServiceTest **84**, nodeOperationalEditor.test.mjs **5**.

## Map decline editor

- VersionServiceTest (unit, 16 cases): `nodeDetailPreservesDistributionParametersWithoutSampling` verifies repeated reads of constant, normal and uniform wells return exact parameters without invoking random sampling.
- NodeServiceTest (unit, 3 cases): `wellJsonPreservesAllDistributionTypesThroughSave` deserializes each distribution through the polymorphic node DTO and verifies the saved entity's parameters.
- nodeOperationalEditor.test.mjs (component/unit, 6 cases): `distribution edits preserve parameters and hidden fields in the well payload` checks all distributions, logical identity, legacy costs and populated edit fields. The existing payload case now verifies the distribution object instead of the obsolete scalar.
- formInputs-browser.mjs: extends keyboard/optional-description acceptance with constant creation, invalid normal/uniform blocking, saves and reopening both distributions with unchanged parameters. HTTP fixtures avoid modifying live user data.

Validation totals: **735 backend tests**, **51 frontend tests**, zero failures/skips; build, lint and browser acceptance passed.

## Explicit node type regression tests

NodeRequestDeserializerTest (unit, 3 cases):
- `everySupportedNodeTypeSelectsItsDeclaredClass`: all nine supported values bind the corresponding DTO without needing distinctive physical fields.
- `overlappingPlantFieldsRespectDiscriminatorRegardlessOfOrder`: the screenshot values (140/1/80/30) select ground plant or FLNG according to type.nodeType, whether type appears first or last.
- `missingMalformedUnknownAndUnsupportedTypesAreRejected`: invalid type input fails instead of guessing.

VersionControllerTest (MVC, now 12 cases):
- `groundPlantCreateAndEditUseExplicitType`: POST and PATCH accept the overlapping plant fields and pass a GroundBasedLiquefactionPlantDTO to the service.
- `missingOrUnknownNodeTypeReturnsBadRequestWithoutSaving`: invalid requests return 400 without invoking creation.
- Existing authorization tests now include the explicit type in their well fixtures; their 401/403 expectations remain unchanged.

Validation: **740 backend tests**, **51 frontend tests**, no failures or skipped tests. Frontend build and lint passed. Backend output isolated in target/node-type-20261022.

### Economic navigation regression

`frontend/tests/economicNavigation.test.mjs`: 3 unit cases.
- Economic links follow the active project and preserve navigation groups.
- Economic links lead to project selection without an active project.
- Economic routes keep their module titles and project breadcrumbs, without activating Version management.

Validation: 64 frontend cases passed, zero failures or skips; production build and lint passed. The existing node editor cases also cover the shared decline selector fix.


## Visual round summary — 2026-11-05

`frontend/tests/summaryVisuals.test.mjs` — unit, **9 cases**:

- `scale includes negative positive and zero values without clipping`: mixed financial values and zero remain ordered and inside chart boundaries.
- `scale stays finite for constant zero negative and extreme finite values`: coincident and large finite values produce stable positions.
- `absent or nonfinite values never become a zero-valued chart`: empty data produces no scale/series while actual zero remains available.
- `annual lines and bands split at missing values and omitted periods`: missing medians and missing interval bounds independently interrupt their series.
- `series sorting preserves the saved input and the investment period`: chronological drawing retains t=0 without mutating the snapshot.
- `dispersion distinguishes coincident percentiles one round and incomplete data`: coincident percentiles can identify no observed dispersion; empty data cannot.
- `payback uses determined applicable denominator and separate status counts`: conditional frequency, median year and status categories are retained.
- `no recovered rounds and no applicable rounds remain distinct from zero`: zero recovery is a valid value; zero denominator and absent median are unavailable.
- `missing investment year never invents a recovery calendar year`: a period without its saved investment year does not fabricate a date.

`frontend/tests/summaryVisuals-browser.mjs` — browser acceptance, **1 end-to-end flow**: verifies four cards, negative/zero values, partially calculable TIR, empty denominators, one round, coincident percentiles, collapsed detail, investment t=0, missing-year gaps, focus/Enter/Escape, saved node names, output/hour metric switching, no-data metric, snapshot conversion equivalence with CSV, desktop/mobile layout and no execution panel in Results. Screenshots are generated under `frontend/node_modules/.cache/probabilistic-ui/`.

`frontend/tests/probabilistic-browser.mjs` — existing browser acceptance, **1 end-to-end flow**, updated: waits for completed work in History before opening Results; verifies the active Results tab and absence of executions there; retains polling, round switching, financial/operational tabs, failed round loading, retries and mobile checks. Navigation fixtures now include organization listing used by the current shell.

Validation: **73 frontend unit/component cases**, **843 Maven tests**, zero failures/errors/skips. Backend output is isolated in `backend/target/summary-visual-validation`; no backend source changes. Both browser flows pass; frontend production build and lint pass. The existing bundle-size warning remains. These totals supersede earlier dated counts without removing their historical catalog.


### Scenario presentation refinements — 2026-11-05

- `scenarioPresentation-browser.mjs` (browser, 1 flow): existing-version list without creation, exact requested header/subtext/action, version filtering, configuration navigation, visible and accessible active Economy/Operation state, keyboard switching, mobile layout and no save/simulation requests.
- `summaryVisuals-browser.mjs` (browser, 1 flow, extended): execution details show version without algorithm name, redundant IRR paragraph is absent, brand classes style lines/markers, and all prior numeric/CSV/keyboard/mobile assertions remain.
- `probabilistic-browser.mjs` (browser, 1 flow, updated): checks the calculable-round denominator on its card after removing the redundant paragraph; round selection and execution history remain covered.
- `projectScoping-browser.mjs` (existing browser flow, expectation updated): an empty configuration listing offers no creation action and sends no creation request; replaces the former create-and-configure expectation.

Validation for the presentation changes: 73 frontend unit/component tests pass; build and lint pass. The first three browser flows above pass, including desktop/mobile screenshots. Maven remains unchanged from the preceding 843-test successful run; no backend source or database changes in this adjustment.

### Production deployment (Lightsail + Cloudflare Pages) — 2026-11-05

`frontend/tests/pagesApiProxy.test.mjs` — unit, **6 cases**, for the Cloudflare Pages Function `frontend/functions/api/[[path]].js` (`fetch` stubbed, no network):

| Case | Verifies |
| --- | --- |
| `forwards path and query string to the BACKEND_URL origin` | `/api/v1/...?query` goes to the backend origin unchanged; any path on `BACKEND_URL` is ignored; redirects are not followed. |
| `forwards method, body and Authorization, and drops Host and Origin` | A POST keeps its method, body, `Authorization` and `Content-Type`; `Host` and `Origin` are removed so Spring does not apply CORS to the proxied call. |
| `sends no body for GET requests` | GET requests are forwarded without a body. |
| `passes the backend response through untouched, including a 401` | Status, headers and body come back as the backend sent them, so the client's refresh-on-401 keeps working. |
| `answers 500 with an ApiResponse envelope when BACKEND_URL is missing or invalid` | A misconfigured Pages project answers a clear `ApiResponse` error and never calls `fetch`. |
| `answers 502 with an ApiResponse envelope when the backend is unreachable` | A network failure towards Lightsail becomes a `502` `ApiResponse` instead of an unhandled exception. |

`.github/scripts/lightsail-deployment.test.mjs` — unit, **9 cases**, for the script that turns the `BACKEND_ENV` secret into the Lightsail deployment. Run with `node --test .github/scripts/*.test.mjs`; the deploy workflow runs it before deploying.

| Case | Verifies |
| --- | --- |
| `parseDotenv reads KEY=VALUE lines and skips comments and blank lines` | Comments, blank lines, CRLF endings, surrounding spaces and an `export ` prefix are handled. |
| `parseDotenv keeps "=" inside values and strips one pair of matching quotes` | JDBC URLs with query strings survive; matching quotes are removed, mismatched ones kept. |
| `parseDotenv rejects a malformed line without echoing its content` | The error names the line number but never prints the line (it may hold a secret). |
| `validateEnv accepts a complete production env` | A complete env yields no problems. |
| `validateEnv reports every missing required key` | Each missing required variable is reported. |
| `validateEnv rejects development defaults, a short JWT secret and a non-JDBC URL` | The dev `JWT_SECRET`/`ADMIN_PASSWORD`, a secret under 32 characters and a `postgres://` URL are all refused. |
| `buildDeployment wires image, env, port and health check to the named container` | Container image, environment, port `8080/HTTP` and the `/api/v1/health` health check are set. |
| `CLI write produces the two deployment files from BACKEND_ENV` | `write <dir>` creates `containers.json` and `public-endpoint.json` from the environment. |
| `CLI validate fails without printing secret values` | An invalid env exits `1` with the problem listed and no secret values in the output. |

Validation: **79 frontend unit/component cases** (73 + 6) and the 9 deployment-script cases pass; frontend build and lint pass. No backend source changes; the deploy workflow itself can only be exercised on GitHub.

## Scenario comparison — 2026-11-12

Module documented in [`scenario-comparison.md`](scenario-comparison.md). 37 new backend cases; `node.migration.MigrationCatalogTest.executableCatalogUsesOnlyVersionedMigrations` now expects **24** migrations (V24 added). Validation: `mvn clean test` — **880 tests** at first delivery; **890** after the cross-project / latest-simulation redesign (same day), zero failures/errors/skips. Run from `clean`: stale migrations from other branches left in `target/classes` make `MigrationCatalogTest` fail.

### `comparison.service.ScenarioMetricsExtractorTest` — Unit (6)

| Case | Verifies |
| --- | --- |
| `deterministicEvaluationMeasuresFinancialIndicatorsCostsAndCashTotals` | NPV, IRR, paybacks, CAPEX, OPEX, revenue, taxes, net cash flow, ROI and ROE come out of a saved snapshot; depreciation, entities outside the boundary, internal transfers and entries after the horizon are not counted. |
| `physicalFlowsCountProductionAtSourceNodesOnlyAndMatchNodesByIdentity` | Produced volume counts only source nodes (no double count along a chain); exported, losses, hours and events add up; nodes are keyed by `identityId` with name/type; the annual series has cash, cumulative cash and volumes. |
| `ratiosWithoutInvestmentAreUnavailableInsteadOfDividingByZero` | CAPEX 0 makes ROI/ROE unavailable with status `NO_INVESTMENT`. |
| `missingSavedDataStaysUnavailableAndIsNeverReplacedByZero` | A snapshot without result or flows gives `NOT_AVAILABLE` values (never zero), marks the scenario incomplete and leaves the series empty. |
| `probabilisticEvaluationUsesMeansOverRoundsAndKeepsConditionalDenominators` | Probabilistic evaluations use the mean over rounds with a distribution; IRR defined in 1 of 2 rounds reports `availableRounds` 1 of `totalRounds` 2. |
| `probabilisticEvaluationWithoutRoundsIsReportedAsIncomplete` | A probabilistic evaluation with no saved rounds is reported as incomplete instead of failing. |

### `comparison.service.ScenarioComparisonCalculatorTest` — Unit (15)

Scenarios are compared with each other; there is no reference scenario.

| Case | Verifies |
| --- | --- |
| `ranksEveryScenarioAndMeasuresItsDistanceToTheBest` | Every value gets its rank, its distance to the best (absolute and relative) and BEST / MIDDLE / WORST; the row lists best and worst; `bestIn` counts per scenario. |
| `lowerIsBetterIndicatorsRankTheSmallestFirstAndTiesShareTheRank` | CAPEX ranks the smallest first; equal values share rank 1 and are both best. |
| `aRelevantGapIsAFindingThatNamesTheBestAndTheWorst` | A gap over the threshold is a `LARGE_GAP` finding naming the best and the worst, never a reference. |
| `smallGapsAreNotRelevantUnlessTheThresholdIsLower` | A 4 % gap is not relevant with the 5 % default and is with 3 %. |
| `ratesUsePercentagePointsAndPaybacksWholeYears` | Rates use percentage points (0.5 p.p. is not relevant), paybacks whole years (shorter is best). |
| `npvPositiveForSomeAndNotForOthersIsTheFirstFinding` | NPV positive for some and negative for others is an `NPV_SIGN_SPLIT` listed first ("genera valor" / "pierde valor"). |
| `paybackNeverRecoveredByOneScenarioIsAnAvailabilitySplit` | A payback never recovered is an `AVAILABILITY_SPLIT` naming that scenario and the range of the others; it is not ranked. |
| `missingDataIsNeverAFindingButIsWarned` | Missing saved data is unranked, not a finding, and raises `INCOMPLETE_RESULTS`. |
| `amountsInDifferentCurrenciesAreNotRankedButRatesAre` | With mixed currencies no amount is ranked (NOT_COMPARABLE), rates still are; the warning names every scenario. |
| `neutralIndicatorsAreReportedWithoutAWinner` | Neutral indicators (taxes) are unranked and their spread is reported as "va de … a …". |
| `identicalScenariosTieWithoutWinnerFindingOrWarning` | Identical scenarios are TIED everywhere, with no winner, finding or warning. |
| `nodesInSomeScenariosOnlyAndBigOutputGapsAreSignalled` | Nodes present in some scenarios only (`NODE_PARTIAL`) and big output gaps (`NODE_FLOW_GAP`) are findings. |
| `scenariosThatDisagreeOnHorizonOrModeAreWarnedNamingAllOfThem` | Different horizons or deterministic/probabilistic mixes raise warnings naming every scenario. |
| `scenariosFromDifferentProjectsAreNamedWithTheirProject` | Mixed projects name scenarios "Base (Cuenca Austral)" in the findings. |
| `fewerThanTwoScenariosAreRejected` | Fewer than two scenarios throw `IllegalArgumentException`. |

### `comparison.service.ScenarioComparisonServiceTest` — Unit (13)

| Case | Verifies |
| --- | --- |
| `previewChecksViewPermissionAndComparesEachScenarioThroughItsLatestSimulation` | Preview needs `VIEW_PROJECT`, uses each scenario's latest simulation and saves nothing. |
| `scenarioThatChangedSinceItsLastSimulationMustBeSimulatedAgain` | An outdated scenario is refused (`Scenario changed since its last simulation: …`) by preview and save; nothing is stored. |
| `scenarioNeverSimulatedCannotBeCompared` | A scenario with no simulation answers `Scenario has never been simulated: …`. |
| `unknownVersionIsNotFoundBeforeReadingAnyEvaluation` | An unknown or deleted version is a 404 before any evaluation is read. |
| `scenarioOfAnotherProjectIsNotFoundEvenWhenTheCallerCanViewIt` | (E1) A version of another project is a generic 404 for preview and save, even if the caller can view that project; that project's permission is never checked and its evaluations are never read. |
| `scenarioOfAProjectTheCallerCannotViewIsAlsoAGenericNotFound` | (E1) A version of a project the caller cannot see is the same generic 404 (`Version not found`): nothing about that project is revealed. |
| `withoutAccessToThePathProjectTheWholeRequestIsForbiddenBeforeAnyLookup` | (E1) Without `VIEW_PROJECT` on the URL project the whole request is a 403 before any version or evaluation is looked up. |
| `fewerThanTwoItemsTheSameScenarioTwiceAndInvalidThresholdsAreRejected` | One item, no body, the same scenario twice, a zero threshold, an item without version and more than 8 items are rejected. |
| `saveNeedsEditPermissionNamesItAfterItsScenariosAndStoresTheSimulationsCompared` | Saving needs `EDIT_PROJECT`, is named after its scenarios ("Base vs Ampliación") unless given a name (trimmed), applies default thresholds and stores the simulations compared, in order. |
| `userWithoutEditPermissionCannotSave` | A 403 from the permission check stores nothing. |
| `probabilisticSimulationsAreComparedFromTheirSavedRounds` | Probabilistic simulations are read from their completed job's saved rounds and compared by their mean. |
| `savedComparisonShowsItsOwnSimulationsEvenIfTheScenariosChangedLater` | Reading a saved run recalculates it from its own simulations even if the scenarios are outdated now; listing returns scenario names; unknown ids are 404. |
| `deleteIsASoftDeleteThatNeedsEditPermission` | Delete needs `EDIT_PROJECT` and deactivates instead of deleting the row. |

### `comparison.service.ScenarioStatusServiceTest` — Unit (6)

| Case | Verifies |
| --- | --- |
| `scenarioUnchangedSinceItsSimulationIsUpToDate` | A snapshot frozen from the current configuration and nodes (read back through JSON) is up to date. |
| `changingTheEconomicConfigurationMakesItOutdated` | A configuration change (WACC) makes the scenario outdated. |
| `changingAPhysicalNodeMakesItOutdated` | A physical change (a well's capacity) makes it outdated. |
| `auditTimestampsDoNotCountAsAChange` | A node's `lastModified` changing alone keeps it up to date. |
| `scenarioWithoutConfigurationOrSnapshotDataIsNotUpToDate` | No current configuration, no snapshot or a snapshot without inputs is never up to date. |
| `listReportsEachScenarioStatusReadinessAndWhetherTheCallerCanSimulate` | The picker list reports UP_TO_DATE / NEVER_SIMULATED / SIMULATING (with progress), the readiness errors, the project name and `canSimulate` (false without a session, true for a platform admin). |

### `comparison.controller.ScenarioComparisonControllerTest` — Web (5)

| Case | Verifies |
| --- | --- |
| `previewBindsTheRequestAndAnswersInTheEnvelope` | `POST /projects/{id}/comparisons/preview` binds the request (scenario ids in order) and answers `200` in the envelope. |
| `saveAnswers201AndReadRoutesKeepTheScopedIdentifiers` | Save answers `201`; list (with scenario names), get and delete pass the project and comparison ids through. |
| `scenarioStatusesAreListedForThePicker` | `GET /projects/{id}/comparisons/scenarios` returns each scenario's status and `canSimulate`. |
| `anonymousRequestsCannotCompareReadOrDelete` | Every route, including `/scenarios`, answers `401` without a token and never reaches the services. |
| `serviceErrorsMapToTheGlobalStatusCodes` | Validation, permission and not-found errors become `400`, `403` and `404`. |

### `comparison.repository.ScenarioComparisonMigrationTest` — Data (JDBC/H2) (3)

| Case | Verifies |
| --- | --- |
| `storesAComparisonWithOrderedItemsAndDefaultsActiveAndIds` | V24 creates both tables; ids and `active` get their defaults. |
| `rejectsRepeatedPositionsAndRepeatedEvaluationsInOneComparison` | Repeated position, repeated evaluation and negative position are rejected. |
| `rejectsUnknownReferencesAndNonPositiveThresholds` | Unknown evaluation/project references and a zero threshold are rejected. |

### Scenario comparison frontend — 2026-11-12 (redesign)

`frontend/tests/comparison.test.mjs` — unit, **19 cases**, for `src/utils/comparison.ts` (updated 2026-11-13: no reference scenario):

| Case | Verifies |
| --- | --- |
| `values are formatted by unit and missing values are never shown as zero` | Currency, %, years, hours and simulator units are formatted in es-AR; `null`/`NaN` and unavailable statuses become text, never `0`. |
| `probabilistic means say how many rounds they cover` | "Media de N rondas" / "Media de N de M rondas" only for probabilistic values. |
| `the distance to the best reads in plain words and in the indicator unit` | "USD 514 menos que el mejor", "1 año más que el mejor", "8,73 p.p. menos que el mejor", "El mejor", "Igual en todos"; nothing for unranked values. |
| `ranks read as ordinals and cells sort best first, unranked last` | "1.º" labels and best-first ordering of bars. |
| `positions and findings map to tones` | Best/worst/middle and finding types (losing value and never recovering are warnings) map to tones. |
| `indicators are grouped in a fixed category order and can be limited to relevant ones` | Financial → costs → cash flow → physical; the relevant-only filter drops empty groups. |
| `two evaluations of the same version get distinguishable names` | Repeated version names get `#position`. |
| `scenarios from several projects are named with their project` | Mixed projects give "Base (Neuquina)" / "Base (Austral)", like the backend. |
| `chart lines break at missing years instead of drawing zero` | Missing or skipped years split the line. |
| `chart scale includes zero and every series, and is null without data` | The axis covers all series and zero; flat data still has a range; no data → no chart. |
| `node types are shown in Spanish and unknown ones as they come` | Backend node types get Spanish labels. |
| `the headline is the highest comparable NPV and its margin over the second one` | The conclusion picks the highest comparable NPV and its lead over the second scenario. |
| `the headline says when two scenarios tie, when every NPV is negative, and when there is none` | Ties, all-negative NPVs, a single NPV and no NPV at all. |
| `bar scales always include zero so negative values grow to the left` | Shared bar scale with zero inside it; no values → no scale. |
| `headline numbers are compact but rates and years keep their full form` | "USD 1,3 M" from a million, "USD 1.233" / "USD -418" / "USD 42,5" below; rates and years unchanged. |
| `scenario colours follow the position and never cycle past the palette` | Position → `series-n` classes; the cap is 8. |
| `comparing needs two to eight scenarios, all simulated and up to date` | The Compare button's rule and its reason; which pending scenarios can be simulated from the screen and which are blocked. |
| `scenario status reads in plain words, with progress and failed runs` | Tile labels for up to date / changed / never simulated / simulating (with rounds) and a failed last run. |
| `the selection round-trips through the URL and the request keeps its order` | `?escenarios=project:version,…` round trip (duplicates and broken parts dropped), the request order, and (E1) scenarios of any other project are dropped when the project is given. |

Also updated: `economicNavigation.test.mjs` and `workspaceRoutes.test.mjs` (Comparaciones links to `/projects/{id}/comparisons`; comparison routes keep their title and drop the run id when switching projects).

Validation: **99 frontend cases** (`npm test`; 98 before the no-reference change), `npm run build` and `npm run lint` pass. Checked end to end against this backend on a throwaway PostgreSQL 16 with the demo data: statuses (all up to date → one outdated after a WACC change), re-simulating from the tile, comparing three scenarios from two projects, the result screen top to bottom, earlier runs listed, and 375 px width (no horizontal overflow, single scroll).

## Version access, node verticals and session validation — 2026-11-14

Five backend changes on `update-frontend`, catalogued together. Three classes
now use `@ParameterizedTest`, so for them the case count (methods) and the
execution count differ; both are given. Counts in this
section are those of 2026-11-14; the current ones are in the *Summary* table.

| Test class | Type | Cases | Executions | Status |
| --- | --- | --- | --- | --- |
| `version.service.VersionAccessTest` | Unit | 10 | 10 | new |
| `version.service.VersionAccessServiceTest` | Unit | 9 | 9 | new |
| `version.controller.VersionAccessControllerTest` | Web | 3 | 3 | new |
| `version.service.VersionAccessGuardTest` | Unit | 35 | 35 | +8 (one renamed) |
| `economic.service.EconomicServiceTest` | Unit | 27 | 27 | +5 |
| `economic.service.EconomicJobServiceTest` | Unit | 10 | 10 | +2 |
| `economic.service.ScenarioConfigurationTest` | Integration | 13 | 13 | +3 |
| `version.service.VersionServiceTest` | Unit | 30 | 30 | +12 |
| `version.controller.VersionReadAccessHttpTest` | Integration | 10 | 10 | new |
| `economic.controller.EconomicWriteAccessHttpTest` | Integration | 9 | 9 | new |
| `node.controller.NodeCreateAccessHttpTest` | Integration | 9 | 43 | new |
| `version.controller.VersionConflictsListAccessHttpTest` | Integration | 8 | 8 | new |
| `version.service.VersionConflictServiceTest` | Unit | 22 | 22 | +2 |
| `project.service.ProjectServiceTest` | Unit | 89 | 89 | +5 |
| `organization.service.OrganizationServiceTest` | Unit | 87 | 87 | +3 |
| `version.controller.VersionRoleAfterProjectRemovalHttpTest` | Integration | 7 | 7 | new |
| `node.model.enums.NodeTypeEnumTest` | Unit | 4 | 26 | new |
| `node.service.NodeServiceTest` | Unit | 5 | 5 | +2 |
| `version.controller.VersionNodeVerticalAccessHttpTest` | Integration | 18 | 58 | new |
| `session.service.SessionServiceTest` | Unit | 9 | 9 | new |
| `auth.filter.SessionRevocationHttpTest` | Integration | 9 | 9 | new |
| `version.repository.VersionMemberRepositoryPostgreSqlIT` | PostgreSQL (by hand) | 7 | 7 | new |

The `*HttpTest` classes are `@SpringBootTest` + MockMvc on the H2 `test`
profile with real JWTs, sharing the `version.controller.AccessHttpFixture`
support class (no tests of its own).

### 1. Effective roles per version

`version.service.VersionAccessTest` — Unit, 10 cases:

| Case | Verifies |
| --- | --- |
| `platformAdminHoldsEveryCapabilityOnEveryVertical` | A platform admin can view, edit, edit connections, simulate and manage, on every vertical. |
| `versionAdminHoldsEveryCapabilityOnEveryVertical` | A version `ADMIN` holds every capability on every vertical. |
| `versionEditorEditsOnlyItsVerticalPlusInternalConsumption` | A version `EDITOR` edits its own vertical and `INTERNAL_CONSUMPTION`, nothing else. |
| `versionEditorCanSimulateWithoutAPersistedSimulatePermission` | A version `EDITOR` can simulate even though its stored permissions lack `SIMULATE_VERSION` (rule in code). |
| `versionSimulatorViewsAndSimulatesButCannotEditOrManage` | A `SIMULATOR` views and simulates only. |
| `severalVersionRolesAddUpTheirCapabilities` | Several version roles add up. |
| `projectEditorFallbackEditsEveryVerticalAndSimulatesButCannotManage` | Without a version role, a project `EDITOR` edits every vertical and simulates but cannot manage. |
| `projectAdminFallbackCanManage` | Without a version role, a project `ADMIN` can manage. |
| `nonMemberFallbackHoldsNoCapability` | Neither a version role nor project membership → no capability. |
| `projectMemberWhoseRoleLacksEveryPermissionCanStillViewButNothingElse` | A project member whose role carries no permission can still view, nothing else. |

`version.service.VersionAccessServiceTest` — Unit, 9 cases:

| Case | Verifies |
| --- | --- |
| `versionRolesReplaceTheProjectRoleOnlyOnTheVersionsThatHaveThem` | A version role overrides the project role only on that version; the others keep the project fallback. |
| `editorRolesListTheirVerticalsAndTheHighestRoleIsReported` | `editableVerticals` lists the editor verticals and `effectiveRole` is the highest role. |
| `projectAdminWithoutVersionRolesCanManageEveryVersion` | A project `ADMIN` with no version role can manage every version. |
| `platformAdminGetsFullAccessWithoutLookingUpVersionRoles` | A platform admin gets full access without querying version roles. |
| `inactiveVersionsAreLeftOut` | Inactive versions are not listed. |
| `projectWithoutVersionsSkipsTheVersionRoleLookup` | A project without versions does not query version roles. |
| `nonMemberIsRejectedWith403` | A non-member gets `403`. |
| `unknownProjectIsRejectedWith404` | An unknown project gets `404`. |
| `unauthenticatedCallerIsRejectedWith401` | No session → `401`. |

`version.controller.VersionAccessControllerTest` — Web, 3 cases:

| Case | Verifies |
| --- | --- |
| `accessReturnsResolvedCapabilitiesInTheEnvelope` | `GET /projects/{id}/access` → `200` with the `ApiResponse` envelope and the resolved fields. |
| `accessDeniedReturns403WithTheProjectMessage` | A refused caller gets `403` with the project message. |
| `anonymousAccessReturns401` | No token → `401`. |

`version.service.VersionAccessGuardTest` — Unit, new and changed cases (the
class's earlier cases are not catalogued here yet):

| Case | Verifies |
| --- | --- |
| `assertCanSimulateVersionAllowsVersionEditorWithoutFallingBackToTheProject` | A version `EDITOR` may simulate, decided by the version role alone. Replaces `assertCanSimulateVersionRejectsVersionEditorWith403WithoutFallingBackToTheProject`. |
| `assertCanSimulateVersionAllowsVersionAdmin` | A version `ADMIN` may simulate. |
| `assertCanSimulateVersionFallsBackToProjectEditPermission` | With no version role, simulation falls back to `EDIT_PROJECT`. |
| `assertCanSimulateVersionRejectsCallerWithNeitherVersionRoleNorProjectMembership` | Neither a version role nor membership → `403` with the project message. |
| `assertCanSimulateVersionRejectsCallerWithoutRolesUsingTheSimulateMessage` | A version row with no roles → `403 You are not allowed to simulate this version`. |
| `assertCanManageVersionRejectsVersionEditorAndSimulatorWithoutFallingBackToTheProject` | `EDITOR` + `SIMULATOR` cannot manage, and the project role is not consulted. |
| `guardDecisionsMatchVersionAccessForEveryVersionRole` | Guard ⇔ `VersionAccess` for every version role × capability × vertical. |
| `guardFallbackMatchesVersionAccessForEveryProjectRole` | Guard ⇔ `VersionAccess` for every project-role fallback. |
| `assertCanManageVersionIgnoresARoleRowOfSomeoneNoLongerInTheProjectAndRejectsWith403` | A version-role row of someone no longer in the project is ignored (effective lookup) → project fallback → `403`. |

`economic.service.EconomicServiceTest` — Unit, 5 new cases:

| Case | Verifies |
| --- | --- |
| `projectEditorWithVersionSimulatorRoleCanEvaluateButCannotSaveConfigurationOrDraft` | Project `EDITOR` + version `SIMULATOR`: evaluates, but saving configuration or draft is `403`. |
| `projectEditorWithVersionSimulatorRoleCanReadConfigurationDraftAndHistory` | The same caller reads configuration, draft and history. |
| `versionEditorOfOneVerticalCanSaveConfigurationAndEvaluate` | A version `EDITOR` of one vertical saves configuration and evaluates. |
| `simulationRefusedByVersionGuardHappensBeforeTouchingDrafts` | A simulation refusal happens before any draft is touched. |
| `versionInAnotherProjectIsStill404BeforeTheVersionGuard` | A version of another project is `404` without consulting version roles. |

`economic.service.EconomicJobServiceTest` — Unit, 2 new cases:
`submissionRefusedBySimulationPermissionQueuesNothing` and
`retryRefusedBySimulationPermissionQueuesNothing` — a job submission or retry
refused by the simulate check queues nothing.

`economic.service.ScenarioConfigurationTest` — Integration, 3 new cases, with
real `version_member` rows:

| Case | Verifies |
| --- | --- |
| `versionSimulatorReadsScenarioAndEconomicsButCannotSaveOrDiscard` | A `SIMULATOR` reads scenario and economics but cannot save or discard. |
| `versionSimulatorCanQueueAProbabilisticJob` | A `SIMULATOR` can queue a probabilistic job. |
| `accessEndpointReadsVersionRolesStoredInTheDatabase` | `/access` resolves roles stored in the database (exercises the new JPQL). |

`version.service.VersionServiceTest` — Unit, 4 new cases for reads:

| Case | Verifies |
| --- | --- |
| `getDiagramIsRefusedWhenTheCallerCannotViewTheVersion` | Diagram → `403` without view access. |
| `getDiagramChecksViewAccessForAnExistingVersion` | The diagram read calls `assertCanViewVersion`. |
| `getDiagramOfAMissingVersionIsStill404WithoutCheckingAccess` | A missing version is `404` before the access check. **Replaced on 2026-11-15** by `getDiagramOfAMissingVersionChecksAccessAndThen404sForAnAuthorizedCaller` and `getDiagramOfAMissingVersionAnswers403WithoutLookingItUpForAnUnauthorizedCaller` (access first). |
| `getNodeDetailIsRefusedBeforeLoadingTheNodeWhenTheCallerCannotViewTheVersion` | Node detail → `403` before loading the node. |

`version.controller.VersionReadAccessHttpTest` — Integration, 10 cases:

| Case | Verifies |
| --- | --- |
| `platformAdminReadsDiagramAndNodeWithoutMembership` | Platform admin → `200` on diagram and node. |
| `projectAdminReadsDiagramAndNode` | Project `ADMIN` → `200` / `200`. |
| `projectEditorReadsDiagramAndNode` | Project `EDITOR` → `200` / `200`. |
| `versionSimulatorReadsDiagramAndNode` | Version `SIMULATOR` → `200` / `200`. |
| `versionEditorOfAnotherVerticalReadsDiagramAndNode` | `EDITOR` of `EXPORT` reads an `EXTRACTION` node → `200`. |
| `unrelatedUserGets403WithProjectMessage` | Unrelated user → `403 You are not allowed to view this project`. |
| `organizationOwnerOutsideTheProjectGets403` | Organization `OWNER` outside the project → `403`. |
| `versionWithoutProjectGets403WithVersionMessage` | Version without a project → `403 You are not allowed to access this version`. |
| `missingVersionGets404BeforeTheAccessCheck` | Missing version → `404`. **Replaced on 2026-11-15** by the two cases below in *Version roles, cascade delete…*: a non-admin now gets `403`, a platform admin `404`. |
| `anonymousCallerGets401` | No token → `401`. |

`economic.controller.EconomicWriteAccessHttpTest` — Integration (own H2 with
`NON_KEYWORDS=YEAR`), 9 cases:

| Case | Verifies |
| --- | --- |
| `versionSimulatorCannotSaveEconomicOrScenarioConfiguration` | `SIMULATOR`: `403 You are not allowed to edit this version` on the four writes; stored data unchanged. |
| `versionSimulatorCanEvaluateQueueAndRetry` | `SIMULATOR`: `201` evaluate, `202` queue, `202` retry. |
| `versionSimulatorStillReadsConfigurationDraftScenarioAndHistory` | `SIMULATOR` reads configuration, draft, scenario and history (`200`). |
| `versionEditorCanSaveAndSimulate` | Version `EDITOR` saves and simulates. |
| `versionAdminCanSaveAndSimulate` | Version `ADMIN` saves and simulates. |
| `projectEditorWithoutVersionRoleKeepsSavingAndSimulating` | Project `EDITOR` without a version role keeps saving and simulating. |
| `outsiderGets403WithProjectPermissionMessage` | Outsider → `403 Project permission required: EDIT_PROJECT` on all seven endpoints. |
| `organizationOwnerOutsideTheProjectGets403OnEconomics` | Organization `OWNER` outside the project → same `403`. |
| `versionMemberWithoutRolesGets403WithSimulateMessage` | A version row without roles → `403 You are not allowed to simulate this version`. |

### 2. `POST /nodes/*` and `GET /version/conflicts` are platform-admin only

`node.controller.NodeCreateAccessHttpTest` — Integration, 10 cases / 46
executions (all 13 `POST /nodes/*` endpoints, `/nodes/pipeline-connection`
included, through `everyEndpoint()`):

| Case | Verifies |
| --- | --- |
| `platformAdminCreatesTheNode` (×13) | Platform admin → `200` and one more row. |
| `platformAdminCreatingAPipelineConnectionStoresTransferCapacityAndOutputPriority` | `POST /nodes/pipeline-connection` with `transferCapacity` 250.5 and `outputPriority` 7 stores both on the `PipelineConnection` row (read back through the `EntityManager`). |
| `platformAdminDeclaringAVerticalThatDoesNotMatchTheTypeGets400AndNothingIsCreated` | An `LNG_CAMER` declared as `EXTRACTION` → `400 Node vertical does not match its type: …`, no row. |
| `projectAdminGets403AndNothingIsCreated` (×13) | Project `ADMIN` → `403 Only platform admins can create nodes outside a version`, no row. |
| `projectEditorGets403AndNothingIsCreated` (×3) | Same for a project `EDITOR`. |
| `versionEditorOfAnotherVerticalGets403AndNothingIsCreated` (×3) | Same for a version `EDITOR`. |
| `versionSimulatorGets403AndNothingIsCreated` (×3) | Same for a `SIMULATOR`. |
| `unrelatedUserGets403AndNothingIsCreated` (×3) | Same for an unrelated user. |
| `organizationOwnerOutsideTheProjectGets403AndNothingIsCreated` (×3) | Same for an organization `OWNER`. |
| `anonymousCallerGets401AndNothingIsCreated` (×3) | No token → `401`, no row. |

`version.controller.VersionConflictsListAccessHttpTest` — Integration, 8 cases:
`platformAdminListsEveryConflict` (`200`, conflicts of every version, including
one with no project); `projectAdminGets403`, `projectEditorGets403`,
`versionEditorOfAnotherVerticalGets403`, `versionSimulatorGets403`,
`unrelatedUserGets403`, `organizationOwnerOutsideTheProjectGets403` (`403 Only
platform admins can list every conflict`); `anonymousCallerGets401`.

`version.service.VersionConflictServiceTest` — Unit, 2 new cases:
`getAllConflicts_PlatformAdmin_ReturnsEveryConflict` and
`getAllConflicts_NotPlatformAdmin_ThrowsWithoutReadingConflicts`.

### 3. Removing members removes their version roles

`project.service.ProjectServiceTest` — Unit, 5 new cases:

| Case | Verifies |
| --- | --- |
| `removeMemberAlsoDeletesTheirVersionMembershipsInThatProject` | Removing a project member deletes their `version_member` rows in that project. |
| `removeMemberTouchesNoVersionMembershipWhenTheUserHasNone` | Nothing is deleted when there is nothing to delete. |
| `removeMemberKeepsVersionMembershipsWhenTheLastAdminGuardRefuses` | When the last-admin guard refuses, version rows are kept. |
| `addMemberPurgesLeftoverVersionMembershipsOfTheUserInThatProject` | Adding a member purges leftover version rows of that user in the project. |
| `addMemberLeavesVersionMembershipsAloneWhenItRejectsADuplicate` | A rejected duplicate add touches no version row. |

`organization.service.OrganizationServiceTest` — Unit, 3 new cases:

| Case | Verifies |
| --- | --- |
| `removeMemberAlsoRemovesTheirVersionMembershipsInEveryProjectOfThatOrganization` | Leaving the organization deletes the version rows in all its projects. |
| `removeMemberQueriesNoVersionMembershipWhenTheUserIsInNoProject` | No project membership → no version query. |
| `removeMemberKeepsVersionMembershipsWhenAProjectWouldBeLeftWithoutAnAdministrator` | When a project would lose its last admin, nothing is deleted. |

`version.controller.VersionRoleAfterProjectRemovalHttpTest` — Integration, 7 cases:

| Case | Verifies |
| --- | --- |
| `removingAVersionAdminFromTheProjectDeletesTheirVersionRolesAndRevokesAccess` | `DELETE /projects/{id}/members/{m}` removes the version rows and access. |
| `removingSomeoneFromTheOrganizationDeletesTheirVersionRolesAndRevokesAccess` | Same through `DELETE /organizations/{id}/members/{m}`. |
| `removingOneMemberKeepsTheVersionRolesOfTheOthers` | Other members' version roles stay. |
| `aPreexistingOrphanRoleRowGrantsNoAccessAndIsNotListed` | A leftover row of a non-member grants nothing and is not listed in `GET /version/{id}/members`. |
| `aPlatformAdminWithAnOrphanRoleRowKeepsFullAccess` | A platform admin with such a row keeps full access. |
| `reAddingARemovedMemberDoesNotGiveBackTheirOldVersionRoles` | Re-adding falls back to the project role. |
| `reAddingAMemberPurgesAPreexistingOrphanRoleRow` | Re-adding purges a preexisting orphan row. |

`version.repository.VersionMemberRepositoryPostgreSqlIT` — PostgreSQL/Flyway,
**by hand only** (`ECONOMIC_TEST_DATABASE_URL` pointing at a disposable
`enerscope_economic_test`), 7 cases: `findsOnlyTheCallersMembershipsInTheRequestedVersionsWithTheirRoles`,
`returnsNothingForAUserWithoutVersionRoles`,
`accessResolutionReadsVersionRolesFromMigratedSchema`,
`effectiveLookupReturnsTheRowOfACurrentProjectMember`,
`effectiveLookupIgnoresTheRowOfSomeoneOutsideTheProject`,
`memberListingLeavesOutRowsOfPeopleOutsideTheProject`,
`findsEveryRowOfTheUserInTheGivenProjectsAndNoOtherUsersRows`.

### 4. The server derives a node's vertical from its type

`node.model.enums.NodeTypeEnumTest` — Unit, 4 cases / 26 executions:

| Case | Verifies |
| --- | --- |
| `everyNodeTypeBelongsToItsVertical` (×12) | Each `NodeTypeEnum` maps to its expected vertical. |
| `theMappingCoversEveryNodeType` | There are 12 types and none lacks a vertical. |
| `checkedVerticalAcceptsAMissingOrMatchingDeclaration` (×12) | A missing or matching declaration returns the type's vertical. |
| `checkedVerticalRejectsADeclarationThatDoesNotMatchTheType` | A mismatch throws `Node vertical does not match its type: LNG_CAMER belongs to EXPORT, not EXTRACTION`. |

`node.service.NodeServiceTest` — Unit, 2 new cases:
`editWellStoresTheVerticalOfItsTypeWhenTheBodyOmitsIt` and
`editWellRejectsAVerticalThatDoesNotMatchItsType`.

`version.service.VersionServiceTest` — Unit, 8 new cases:

| Case | Verifies |
| --- | --- |
| `addNodeToVersionAuthorizesWithTheVerticalDerivedFromTheTypeBeforeCheckingTheDeclaredOne` | The guard gets the derived vertical, before the declared one is validated. |
| `addNodeToVersionRejectsDeclaredVerticalThatDoesNotMatchTheType` | A mismatched declaration → `400`, nothing saved. |
| `addNodeToVersionFillsAMissingVerticalFromTheType` | A missing vertical is filled from the type. |
| `addNodeToVersionRejectsMissingNodeType` | No `type.nodeType` → `400 A supported type.nodeType is required`. |
| `editNodeInVersionRejectsChangingTheVerticalOfTheNode` | An edit cannot relabel the vertical. |
| `editNodeInVersionAuthorizesByTheStoredNodeTypeNotTheStoredVertical` | Edits are authorized by the stored type, not the stored vertical. |
| `deleteNodeFromVersionAuthorizesByTheStoredNodeType` | Delete is authorized by the stored type. |
| `diagramAndNodeDetailReportTheVerticalDerivedFromTheType` | Diagram and node detail report the derived vertical. |

`version.controller.VersionNodeVerticalAccessHttpTest` — Integration, 18 cases
/ 58 executions:

| Case | Verifies |
| --- | --- |
| `editorOfTheDerivedVerticalCreatesTheNodeAndTheServerStoresThatVertical` (per type) | The editor of the derived vertical creates the node; the stored vertical is the derived one. |
| `editorOfTheDerivedVerticalDeclaringAnotherOneGets400AndNothingIsCreated` (per type) | Declaring another vertical → `400`, nothing created. |
| `editorOfAnotherVerticalDeclaringTheCorrectOneIsJudgedByTheDerivedVertical` (per type) | Judged by the derived vertical: `403`, or `200` for the two `INTERNAL_CONSUMPTION` types. |
| `editorOfAnotherVerticalDeclaringItsOwnIsRefused` (per type) | Declaring one's own vertical for another type is refused (`403` or `400`). |
| `extractionEditorCreatesAWellDeclaredAsExtraction` | `200`, stored `EXTRACTION`. |
| `extractionEditorCannotCreateAnLngCarrierWhateverVerticalItDeclares` | `403` whether it declares `EXTRACTION`, `INTERNAL_CONSUMPTION`, `EXPORT` or nothing. |
| `extractionEditorDeclaringAWellAsExportGets400WithAClearMessage` | `400` with the mismatch message. |
| `bodyWithoutVerticalGetsTheVerticalOfItsType` | A body without vertical is stored with the type's vertical. |
| `versionAdminCreatesAnyTypeButMustDeclareItsVerticalCorrectly` | Version `ADMIN`: `200` when correct, `400` when not. |
| `projectAdminWithoutVersionRoleCreatesAnyTypeButMustDeclareItsVerticalCorrectly` | Same for a project `ADMIN` (fallback). |
| `platformAdminCreatesAnyTypeButMustDeclareItsVerticalCorrectly` | Same for a platform admin. |
| `simulatorCannotCreateNodesWhateverVerticalItDeclares` | `SIMULATOR` → `403`. |
| `extractionEditorEditsItsWellAndTheVerticalStaysExtraction` | Edit → `200`, vertical stays `EXTRACTION`. |
| `extractionEditorCannotRelabelItsWellIntoAnotherVertical` | Relabel to `EXPORT` or `INTERNAL_CONSUMPTION` → `400`, stored vertical intact. |
| `exportEditorCannotTouchAWell` | `EXPORT` editor → `403` on edit, position, basics and delete. |
| `extractionEditorMovesRenamesAndDeletesAWell` | `EXTRACTION` editor → `200` on position, basics and delete. |
| `wellStoredWithASpoofedVerticalIsJudgedByItsType` | A Well stored as `EXPORT` is still refused to the `EXPORT` editor. |
| `wellStoredWithASpoofedVerticalIsShownAndRepairedAsExtraction` | Reads report `EXTRACTION`; an edit repairs the stored row. |

### 5. Session validated against the database

`session.service.SessionServiceTest` — Unit, 9 cases:

| Case | Verifies |
| --- | --- |
| `validateReturnsSessionWithStoredUserForActiveAccount` | The session carries the stored user, not one rebuilt from claims. |
| `validateRejectsTokenOfDeactivatedAccount` | Deactivated account → no session (and a `warn`). |
| `validateRejectsTokenOfUnknownUser` | User missing from the database → no session. |
| `validateUsesStoredRoleWhenAdminWasDemoted` | Token says `ADMIN`, database says `USER` → `USER`. |
| `validateUsesStoredRoleWhenUserWasPromoted` | Token says `USER`, database says `ADMIN` → `ADMIN`. |
| `validateAcceptsEarlierTokenAgainAfterReactivation` | An unexpired token issued before deactivation works again after reactivation. |
| `validateRejectsInvalidTokenWithoutLookingUpUsers` | Invalid or missing token → no session, no query. |
| `validateRejectsRefreshTokenUsedAsAccessToken` | A refresh token is not accepted as access token. |
| `validateRejectsAccessTokenWithMalformedSubject` | A non-UUID `sub` → no session, no query. |

`auth.filter.SessionRevocationHttpTest` — Integration, 9 cases:

| Case | Verifies |
| --- | --- |
| `deactivatedUserIsRejectedImmediatelyWithTokenIssuedBefore` | After `DELETE /users/{id}`, the earlier token → `401 Authentication required`. |
| `deactivatedAdminLosesAdminEndpointsWithTokenIssuedBefore` | A deactivated admin's earlier token → `401` on `GET /users`. |
| `tokenOfUserMissingFromTheDatabaseIsRejected` | A token for a user not in the database → `401`. |
| `demotedAdminLosesAdminAccessImmediatelyWithTokenIssuedBefore` | After demotion: `403` on `GET /users` and `POST /auth/register`, `200` on `GET /organizations`. |
| `promotedUserGainsAdminAccessWithTokenIssuedBefore` | After promotion, the earlier token reaches `GET /users`. |
| `activeUnchangedSessionsKeepWorking` | Unchanged sessions keep answering `200`. |
| `reactivatedUserCanUseTokenIssuedBeforeDeactivationAgain` | `401` while deactivated, `200` again after reactivation. |
| `publicAndLoginRoutesIgnoreARevokedBearer` | `/health`, `/auth/logout` and `/auth/login` answer normally with a revoked bearer. |
| `refreshOfDeactivatedAccountStaysRejected` | `POST /auth/refresh` of a deactivated account → `401 This account has been deactivated`. |

`economic.service.PostgreSqlEconomicIT` (by hand) now persists the admin that
signs its HTTP token, since a token of a user absent from the database is
rejected.

Validation: **1086 Maven tests** (68 classes), zero failures, errors or skips,
on `update-frontend` at `4f3739b`. By hand against a disposable PostgreSQL 16:
`PostgreSqlEconomicIT` 4, `ScenarioConfigurationPostgreSqlIT` 14,
`VersionMemberRepositoryPostgreSqlIT` 7, all passing. These totals supersede
earlier dated counts without removing their historical catalog.

## Branches, merge and conflicts across projects — 2026-11-14

One backend change on `update-frontend` (commit `5d5ccc3`): creating a child
version, merging, resolving conflicts and re-parenting now authorize the
caller first and only then check that both versions belong to the same
project. Conflicts recorded against another project are skipped when merging
and hidden when listing.

**Cases are methods; executions are what Maven runs.** The classes below use
`@ParameterizedTest` with `@EnumSource(VerticalEnum.class)` (5 verticals), so
for them the two numbers differ. Counts in this section are those of
2026-11-14 (commit `5d5ccc3`); `VersionBranchAccessHttpTest` has grown since —
see the *Summary* table and the 2026-11-15 section.

| Test class | Type | Cases | Executions | Status |
| --- | --- | --- | --- | --- |
| `version.controller.VersionBranchAccessHttpTest` | Integration | 17 | 21 | new |
| `version.controller.VersionMergeAccessHttpTest` | Integration | 15 | 27 | new |
| `version.service.VersionLineageTest` | Unit | 10 | 10 | new |
| `project.service.ProjectServiceTest` | Unit | 95 | 95 | +6 |
| `version.service.VersionAccessGuardTest` | Unit | 39 | 39 | +4 |
| `version.service.VersionConflictServiceTest` | Unit | 30 | 30 | +8 |
| `version.service.VersionServiceTest` | Unit | 38 | 38 | +8 |
| `economic.controller.ScenarioVerticalAccessHttpTest` | Integration | — | — | 1 renamed, +1 |

The two `*HttpTest` classes are `@SpringBootTest` + MockMvc on the H2 `test`
profile with real JWTs, built on `version.controller.AccessHttpFixture`, which
gained `newNode`, `foreignProject`, `joinProject` and `grantVersionRole`.

### 1. Creating a child version

`version.controller.VersionBranchAccessHttpTest` — Integration, 17 cases
(21 executions):

| Case | Verifies |
| --- | --- |
| `editorOfAVerticalCreatesAChildAndCanOnlyEditThatVerticalInIt` (×5) | A version `EDITOR` of one vertical creates a child and, in it, edits only that vertical; another vertical → `403`. |
| `simulatorOfTheParentCannotCreateAChild` | A `SIMULATOR` of the parent → `403`; no version is created. |
| `versionAdminCreatesAChildAndEditsEveryVerticalInIt` | A version `ADMIN` creates a child and edits every vertical in it. |
| `projectAdminCreatesAChildAndEditsEveryVerticalInIt` | Same for a project `ADMIN`. |
| `platformAdminCreatesAChildAndEditsEveryVerticalInIt` | Same for a platform admin. |
| `projectEditorWithoutVersionRoleKeepsTheProjectPermissionInTheChild` | A project `EDITOR` with no version role edits every vertical in the child and gets no version role of its own. |
| `everyRoleOfTheParentIsCopiedWithItsVerticalAndPermissions` | The child holds a copy of each parent role (vertical and permissions included) for the four members. |
| `aVersionRoleOfSomeoneOutsideTheProjectIsNotCopied` | A version role of a non-member of the project is not copied. |
| `aVersionRoleOfSomeoneRemovedFromTheProjectIsNotCopied` | After `DELETE /projects/{id}/members/{memberId}`, that person's role is not copied. |
| `aVersionRoleOfADeactivatedProjectMemberIsCopiedSoItStaysNarrow` | A deactivated project member's role is copied, so it keeps its narrow scope. |
| `aParentFromAnotherProjectAnswers403ToSomeoneWithoutAccessToIt` | A parent of another project, caller cannot edit it → `403`, nothing created. |
| `aParentFromAnotherProjectAnswers400ToSomeoneWhoCanEditIt` | Same parent, caller can edit it → `400 Parent version must belong to the selected project`, nothing created. |
| `anUnknownParentAnswers403ToAProjectEditor` | A parent that does not exist → `403`, not `404`. |

Authorization runs first (`403`) and the project check second (`400`): the two
cases above pin that order, so a caller without access cannot learn that a
version exists in another project.

### 2. Re-parenting

Also in `VersionBranchAccessHttpTest`:

| Case | Verifies |
| --- | --- |
| `reparentingUnderAVersionOfAnotherProjectIsRejectedAndTheParentStays` | A platform admin gets `400 Parent version must belong to the same project`, a project admin without access to it `403`; the parent does not change. |
| `reparentingAVersionUnderItsOwnDescendantIsRejected` | `400 A version cannot be its own ancestor`; the parent stays unset. |
| `anEditorOfTheVersionCannotReparentIt` | A version `EDITOR` and a project `EDITOR` → `403`; the parent stays. |
| `aProjectAdminReparentsWithinTheProject` | A project `ADMIN` moves a version under a sibling in the same project → `200`. |

`version.service.VersionLineageTest` — Unit, 10 cases:

| Case | Verifies |
| --- | --- |
| `versionsOfTheSameProjectPass` | Two versions of one project: no error. |
| `versionsOfDifferentProjectsAreRejectedWith400` | Different projects → `IllegalArgumentException` `The versions belong to different projects`. |
| `twoVersionsDetachedFromEveryProjectPass` | Two versions that belong to no project count as together. |
| `aProjectVersionAndADetachedOneAreRejected` | A project version and a detached one are rejected. |
| `aParentInAnotherProjectIsRejectedWithTheParentMessage` | The parent check uses `Parent version must belong to the same project`. |
| `aVersionCannotBecomeItsOwnParent` | A version as its own parent is rejected. |
| `aVersionCannotHangFromOneOfItsDescendants` | A descendant as the new parent is rejected. |
| `anUnrelatedChainPassesEvenIfItAlreadyLoops` | A candidate chain that loops but does not contain the version passes, and the walk terminates. |
| `isSameProjectTellsVersionsOfTheSameAndOfDifferentProjectsApart` | `isSameProject` is true for one project, false for two. |
| `isSameProjectTreatsTwoDetachedVersionsAsTogetherButNotADetachedOneWithAProjectOne` | Two detached versions are together; a detached and an attached one are not. |

### 3. Merge and conflict resolution

`version.controller.VersionMergeAccessHttpTest` — Integration, 15 cases
(27 executions):

| Case | Verifies |
| --- | --- |
| `adminOfTheDestinationMergesAChangeOfAnyVertical` (×5) | An `ADMIN` of the destination merges a change of any vertical. |
| `editorOfTheChangedVerticalCannotMergeAndNothingIsMerged` (×5) | An `EDITOR` of the changed vertical → `403`; the parent keeps its node. |
| `adminOnlyOfTheChildCannotMergeIntoAParentItDoesNotAdminister` | Administering only the child → `403`; nothing merged. |
| `projectAdminWithoutVersionRoleMerges` | A project `ADMIN` with no version role merges. |
| `platformAdminMerges` | A platform admin merges. |
| `adminOfTheDestinationWithoutEditPermissionCannotMergeANodeChangeAndNothingIsMerged` | `MANAGE_VERSION` without edit permission → `403` for a node change; nothing merged. |
| `adminOfTheDestinationWithoutEditPermissionCannotMergeAConnectionChange` | The same for a connection change; the parent's connections are unchanged. |
| `aChildHangingFromAVersionOfAnotherProjectCannotBeMergedIntoItAndNothingIsCopied` | A child whose parent is in another project: `403` without access; `400 The versions belong to different projects` for an admin of both and for a platform admin; no node copied, no conflict recorded. |
| `adminOfTheSiblingAcceptsTheParentDataOfAnyVertical` (×5) | An `ADMIN` of the sibling accepts the parent's data for a node of any vertical; the conflict is resolved. |
| `editorOfTheNodeVerticalCannotResolveAndNothingIsCopied` | An `EDITOR` of the node's vertical → `403`; sibling and conflict untouched. |
| `adminOfTheSiblingWithoutEditPermissionCannotResolveANodeConflict` | `MANAGE_VERSION` without edit permission → `403`; sibling and conflict untouched. |
| `aChildHangingFromAVersionOfAnotherProjectCannotAcceptItsDataAndNothingIsCopied` | Same order as merge (`403`, then `400`); the child keeps its own node and the conflict stays unresolved. |
| `mergingIntoAParentRecordsNoConflictForAChildOfAnotherProject` | A merge records a conflict for the sibling of its own project and none for the one of another project. |
| `listingAChildHidesConflictsRecordedAgainstAnotherProject` | `GET /version/{id}/conflicts` returns only the same-project conflict; the other project's name and id are not in the body. |
| `listingAChildStillShowsConflictsOfItsOwnProject` | A conflict of its own project is still listed, with its description. |

### 4. Cases added to existing classes

`project.service.ProjectServiceTest` — Unit, 6 new cases:

| Case | Verifies |
| --- | --- |
| `saveVersionCopiesEveryRoleOfTheParentWithItsVerticalAndPermissions` | Every parent role is copied to the child with vertical and permissions. |
| `saveVersionWithoutParentCopiesNoRoles` | No parent → no roles copied. |
| `saveVersionWithParentOfNoOneElseCopiesNothing` | A parent with no members → nothing saved. |
| `saveVersionRefusesASimulatorOfTheParentWith403AndCreatesNothing` | A `SIMULATOR` of the parent → `403`; no version created. |
| `saveVersionLetsAnEditorOfOneVerticalOfTheParentCreateAChild` | An `EDITOR` of one vertical may create the child. |
| `saveVersionAuthorizesAForeignParentBeforeSayingItBelongsToAnotherProject` | The parent is authorized before the same-project check. |

`version.service.VersionAccessGuardTest` — Unit, 4 new cases:

| Case | Verifies |
| --- | --- |
| `assertCanEditNodesJudgesEachNodeByTheVerticalOfItsTypeNotTheStoredOne` | Each node is judged by the vertical of its type. |
| `assertCanEditNodesRejectsWhenOneOfTheVerticalsIsOutsideTheRole` | One vertical outside the role rejects the whole set. |
| `assertCanEditNodesWithAnUntypedNodeRequiresEditingTheVersion` | An untyped node requires edit permission on the version. |
| `assertCanEditNodesWithNoNodesChecksNothing` | An empty set checks nothing. |

`version.service.VersionServiceTest` — Unit, 8 new cases:

| Case | Verifies |
| --- | --- |
| `mergeAuthorizesTheDestinationAndEveryTouchedNodeInsteadOfTheSubversion` | The merge authorizes the destination and every touched node, not the subversion. |
| `mergeRefusedForOneVerticalLeavesTheParentUntouched` | One refused vertical leaves the parent untouched. |
| `mergeOfAConnectionChangeRequiresEditingConnectionsOnTheDestination` | A connection change requires editing connections on the destination. |
| `mergeBetweenProjectsIsRejectedAfterAuthorizingAndChangesNothing` | Merge across projects: authorize, then reject; no change. |
| `reparentingRequiresManagingTheVersionAndEditingTheNewParentThenChecksTheLineage` | Re-parenting requires managing the version and editing the new parent, then checks project and ancestry. |
| `reparentingRejectedByTheLineageKeepsTheOldParent` | A lineage rejection keeps the old parent. |
| `renamingDoesNotRequireManagingTheVersion` | A rename (no parent in the body) requires only editing the version. |
| `branchingStoresTheVerticalDerivedFromTheTypeNotTheStoredOne` | A cloned node stores the vertical derived from its type. |

`version.service.VersionConflictServiceTest` — Unit, 8 new cases:

| Case | Verifies |
| --- | --- |
| `resolveConflict_AuthorizesTheSiblingTheParentAndTheNodeVerticalBeforeTheProjectCheck` | Order: manage the sibling, view the parent, edit the node's vertical, then same project. |
| `resolveConflict_RefusedForTheNodeVertical_LeavesTheSiblingAndTheConflictUntouched` | A refused vertical changes nothing. |
| `resolveConflict_ParentNotViewable_CopiesNothing` | A parent the caller cannot view → nothing copied. |
| `resolveConflict_Connection_RequiresEditingConnectionsOnTheSibling` | A connection conflict requires editing connections on the sibling. |
| `resolveConflict_BetweenProjects_IsRejectedAfterAuthorizingAndCopiesNothing` | Across projects: authorize, then reject; nothing copied. |
| `recordMergeConflicts_SiblingOfAnotherProject_GetsNoConflictWhileTheLocalOneDoes` | A sibling of another project gets no conflict; the local one does. |
| `recordMergeConflicts_OnlySiblingIsOfAnotherProject_SavesNothing` | If the only sibling is of another project, nothing is saved. |
| `getConflictsForVersion_OmitsRowsRecordedAgainstAnotherProject_CheckingEachMergedVersionOnce` | Foreign rows are omitted and each merged version is checked once. |

`economic.controller.ScenarioVerticalAccessHttpTest`: the case
`editorCopyingTheScenarioIntoAChildVersionCanSaveEveryVerticalThere` became
`editorCopyingTheScenarioIntoAChildVersionKeepsOnlyItsVerticalThere` (now
expects `403` and nothing stored, because the child copies the parent roles),
and `projectEditorWithoutVersionRoleCopiesTheScenarioIntoAChildAndSavesEveryVerticalThere`
is new.

Validation: the totals of 2026-11-14 (**1211 Maven executions**) are
superseded by the *Summary* table (1272 executions in 73 classes on
2026-11-15). The per-class figures in the two dated sections above come from
reading the sources (methods and `@EnumSource` expansion), so if a run reports
a different number, trust the run and reconcile again.

## Frontend unit tests (`npm test`)

`cd frontend && npm test` runs `node --test tests/*.test.mjs`: the built-in
`node:test` runner, no extra dependency. Files that need TypeScript or React
transpile the source on the fly with the `typescript` package and stub `react`
and the router (see `tests/loadModule.mjs` and the loader at the top of
`tests/permissionsCharacterization.test.mjs`). Run on 2026-11-15 (for this
update, `npm test`): **173 tests in 27 files, 173 pass, 0 fail, 0 skipped**
(`ℹ tests 173`, `ℹ pass 173`, `ℹ fail 0`, `ℹ skipped 0` from the runner;
per-file counts from running each file on its own with `node --test <file>`,
which add up to the same 173). The previous pass recorded 157 in 24 files; the
16 new tests are in `authEvents` (5), `projectsTableTeam` (4) and
`versionAccessRules` (5), plus 2 more in `versionRoleErrors`.

| File | Tests | Covers |
| --- | --- | --- |
| `activeProjectResolution.test.mjs` | 8 | `resolveActiveProject` (route → stored → first, a foreign route id resolves to none), `projectRouteRedirect`, `ProjectRouteGuard` (page / loader / redirect), no redirect loops, `versionsIndexRedirect`, and when a lost-access response reloads the project list. |
| `advancedEconomicDraft.test.mjs` | 3 | Advanced economic editor draft. |
| `authEvents.test.mjs` | 5 | `api/authEvents.ts` and the response interceptor of `api/client.ts`: a `403` for a user cached as platform administrator refreshes the user data, a `403` for a regular user refreshes nothing, refreshes are at least `MIN_USER_REFRESH_INTERVAL_MS` (5 s) apart, a burst of `403`s triggers a single refresh, and a failed refresh-token exchange (after a `401`) clears the stored token and notifies that the session was lost. |
| `csvImport.test.mjs` | 6 | CSV parsing: the `puesto`/`jobTitle` column under its header aliases, a file without it, the job-title length limit, the normalized CSV sent to the server, the template, and the translated backend row-limit message. |
| `csvImportBatches.test.mjs` | 10 | Limits (500 rows, batches of 50), merging batch results with a single credentials header, failure lines mapped back to the real file line, progress, a failing batch keeping earlier credentials and flagging the uncertain rows, a rejected batch reported as not sent, and cancellation (mid-request, between batches, before the first). |
| `economicChart.test.mjs` | 2 | Economic chart helpers. |
| `economicDraft.test.mjs` | 20 | Economic configuration draft and formatting. |
| `economicNavigation.test.mjs` | 3 | Configuration / simulation navigation. |
| `nodeOperationalEditor.test.mjs` | 10 | Node forms and payloads, and the Spanish labels of the *Cadena de valor* module (every backend value has a label; no English copy). |
| `operationalResults.test.mjs` | 8 | Operational results. |
| `organizationsTable.test.mjs` | 3 | The organizations table: the *Agregar integrantes* action is offered for active organizations only, and reports its own organization. |
| `pagesApiProxy.test.mjs` | 6 | The Cloudflare Pages Function (catalogued above). |
| `permissionsCharacterization.test.mjs` | 8 | `canManageProject`, `canManageOrganizationUsers`, `RoleRoute`, the `src/permissions/access.ts` module against the checks it replaced, and the navigation tree for every role / project-role / membership / loading combination against the golden file `permissionsNav.golden.json`. |
| `probabilisticResults.test.mjs` | 4 | Probabilistic results (catalogued above). |
| `projectsTableTeam.test.mjs` | 4 | `ProjectsTable`: a member who does not administer sees *Ver equipo* instead of the manage button, a project administrator keeps *Administrar integrantes* and gets no *Ver equipo*, *Ver equipo* reports its own project, and the project member types are labelled *Administrador del proyecto* / *Editor del proyecto*. |
| `projectTeamNav.test.mjs` | 4 | The *Equipo del proyecto* entry: visible to every member with an active project, a group for an editor, absent without a project or while projects load. |
| `buttonLink.test.mjs` | 6 | New 2026-11-23. `ButtonLink` is an anchor to its `to` with the label inside; for every variant and size it wears exactly the classes a `Button` of the same variant and size renders (both read `buttonClasses`); the default is the primary button at control height; `compact` is the 40px control and the `link` variant has no box; every variant keeps the standard focus ring and `secondary` is the neutral fill; a custom class and the router props (`replace`) go through. |
| `economicsControls.test.mjs` | 6 | New 2026-11-23. Static scan of the economic module: the scan reaches it; no hand-written `<select>` (use `SelectField` / `FilterSelect`); no hand-written `<button>` beyond an exact, documented list (the two section menus and the Operación / Económico segmented tablist; the count is exact, so removing one without updating the list also fails); no card drawn by hand (rounded border + `bg-surface`) in the economic components and `ScenarioConfigurationPage`; no native `<dialog>` / `showModal()` anywhere in `src/`; and `SimulationDialog` is a `Modal` that ignores close requests while it submits. |
| `emptyState.test.mjs` | 5 | New 2026-11-23. `EmptyState` is the centred `px-4 py-8 text-sm text-ink-500` block; its action goes in a `mt-4` wrapper and only when given; `LoadingState` is a `role="status"` paragraph with the same look; a custom class is appended to both; and a static scan: no screen under `src/` writes `px-4 py-8 text-center` itself. |
| `pageHeader.test.mjs` | 10 | New 2026-11-23. `PageHeader`: the title is the only `h1` and carries `text-2xl font-semibold text-ink-800`; description, back link, eyebrow and actions are absent unless given; the description and back link use the standard classes and sit in the right order (back link, eyebrow, title); actions stack below the title under `sm` (`flex-col sm:flex-row`); children render under the description; `mb-6` by default and none with `spaced={false}`; a long title and a custom class are kept; and a static scan: no file under `src/` writes an `<h1>` except `PageHeader` and the sign-in page. |
| `resultsCsv.test.mjs` | 4 | Results CSV export. |
| `scenarioConfiguration.test.mjs` | 8 | Scenario configuration adapter. |
| `sidebarMode.test.mjs` | 5 | New 2026-11-23. The sidebar's two modes (fixed ≥ `80rem`, drawer below), the layout of each, the one-off removal of the retired `sidebarPinned` key (also with a throwing or missing storage), and that `SidebarProvider` asks only `FIXED_QUERY`. |
| `sidebarTheme.test.mjs` | 26 | New 2026-11-23; per-theme since the dark sidebar entry. Every `--color-sidebar-*` token is declared in both themes; the light values are frozen exactly; the dark rail is its own (`#0e2f26`, 1.3–1.5:1 above the dark page, pill different from the light one); the same WCAG ratios on each theme's own tokens (text ≥ 4.5:1 on the rail, the hover and the active pill, the parent of the current row included; the current pill ≥ 1.8:1 against the rail; focus ring and locked icons ≥ 3:1); every opaque pixel of `logo-mark-sidebar.png` / `logo-full-sidebar.png` ≥ 3:1 on each theme's rail. |
| `sidebarActive.test.mjs` | 6 | At most one current sidebar leaf on every route, with and without a project; *Proyectos* current on `/projects`; the route → current item matrix; *Versionado* as a single entry; analysis items without a project; the short and full label of *Configuración*. |
| `tableClasses.test.mjs` | 4 | New 2026-11-23. Both header densities are the small uppercase `ink-500` label; the two densities differ only in padding (`px-4 py-3` standard, `px-3 py-2` compact); the body cell is `text-sm text-ink-700` and leaves vertical alignment to the table; and a static scan: no table defines its own `headerCell`/`bodyCell` literal or repeats the header classes outside `tableClasses.ts`. |
| `darkThemeContrast.test.mjs` | 26 | New 2026-11-23. Reads the `html[data-theme='dark']` block of `index.css` and measures it: the tokens the contracts read are declared; `ink-500`…`ink-900` clear 4.5:1 on `surface`, `raised` and the hover fill `ink-50` (15 cases); a `brand-800` link clears 4.5:1 on `page`, `surface` and `raised` (3); the primary label `on-accent` clears 4.5:1 on `brand-800` and its hover `brand-900`, and on the neutral fills (secondary button, avatar, tooltips); every status tint keeps its text at 4.5:1 (`brand`, `danger`, `warn`, `info`); the focus ring and `control-border` clear 3:1 on `surface` and `raised` (2); the layers separate (`surface`/`page` ≥ 1.2:1, `raised`/`surface` ≥ 1.15:1); the inner highlight rule exists only under the dark selector. |
| `themeTokens.test.mjs` | 5 | New 2026-11-23. Static scan of every `src/**/*.tsx`: the scan reaches the app and both patterns match what they should (and not `bg-surface`, `text-white`, `danger-*`, `warn-*`); no component paints a surface with the literal `bg-white` (use `bg-surface`, which follows the theme); no component uses `red-*` or `amber-*` (use `danger-*` and `warn-*`). Exceptions go in the `EXCEPTIONS` map of the file with a reason; it is empty. 2026-11-23 (dark theme, phase 2A): the same scan also rejects a literal `fill-white`, `stroke-white` or `text-white` outside `WHITE_INK_EXCEPTIONS` (today only `RecentProjectHero.tsx`, with its reason: the spotlight plate is the same in both themes), and a second case fails when an exception no longer needs to be one. |
| `summaryVisuals.test.mjs` | 9 | Summary charts. |
| `uiLegibility.test.mjs` | 8 | New 2026-11-23. Static scan of `src/`: the scan reaches the app; `text-ink-300` / `text-ink-400` is not a text colour except in an exact list of decorative uses (two chevron icons, the search icon, an `aria-hidden` separator); the tabs are 44px tall (padding 2×8 + `text-sm` 20 + `::after` 2×4) and the area does not widen; a `RowButton` is 24px with a 24×44 area (`after:-inset-y-2.5`, `after:inset-x-0`) that stays inside a standard row (extension 10px ≤ the 12px of cell padding); no `<Button>` carries a colour, border or radius its variant already sets (only `!` overrides); arbitrary pixel font sizes appear only in the six documented places; no `space-y-5` or `space-y-10`; and no `h2` is `text-xl`. |
| `versionAccessApi.test.mjs` | 4 | `versionAccessApi`: `GET /projects/{id}/access`, list members, grant a role (with the vertical when there is one), revoke by member and role id. |
| `versionCsvImport.test.mjs` | 46 (was 27; the `type` check added 17, the `INDUSTRIAL_CONSUMPTION` fix 2) | New file. Node CSV import into a version: the identity messages (exists / repeated, with uuid, upper-case uuid, without a record number, malformed uuid) and the other importer messages translated by regex, previous translations unchanged; the CSV parser (quotes, BOM, CRLF, blank lines) and the analysis (line numbering, identity, file-level problems, kinds, connections, scenario row); both example files clean and without an `identity` column; the identity help text; permissions (deny by default, verticals, connections, economics); confirmation warnings; start conditions; failure classification (rejected vs uncertain); result messages and destinations. Type / role / vertical coherence of each `NODE` row against a hand-written `NodeTypeEnum` matrix (the `type.nodeType` enum spelling included): every kind clean, every wrong role and every wrong vertical for every kind with its expected value and line, the "line 9" message, several errors in one file in order with the header as line 1, empty / non-JSON / non-object / missing-key `type`, case-insensitive kind, no check without a `type` column or on non-`NODE` rows, the 50-problem cap with the remaining count, no import with problems, the help text, the templates agreeing with the rules; `NODE_KIND_VERTICAL` (the edit-permission vertical) equal to the `NodeTypeEnum` vertical for every kind, and an `INDUSTRIAL_CONSUMPTION` file needing *Consumo interno*, not *Exportación*; and the backend `Node role/vertical does not match its type` messages translated (wrapped with a record number or not, unknown shape left alone). |
| `versionAccessRules.test.mjs` | 14 (was catalogued as 5; this change adds 1) | `utils/versionAccessRules.ts`: merging needs to manage the parent (`canMergeIntoParent`), branching needs to edit the chosen parent (`canBranchFromParent`), `uneditableVerticals` lists the scenario verticals the caller cannot edit, a node of an unknown type blocks that check, and `VersionTree` shows *Mergear* only where the parent can be managed and *Importar CSV* only on the versions the caller can import into. |
| `versionRoleErrors.test.mjs` | 9 | The Spanish translation of the version endpoints' `403`/`400`/`404` messages, including "ask a project administrator" for managing roles and the cascading-delete `403` that names no version; and, new, the branch / merge / vertical `403`s (*edit this version*, *this vertical's nodes*, *simulate*, *manage this project*) and the `400`/`404` ones (*Resource not found*, *Node vertical does not match its type* (now translated with the type and the expected vertical), versions of different projects, parent of another project, a version as its own ancestor, a cycle in the simulation graph). |
| `versionRoles.test.mjs` | 18 | `utils/versionRoles.ts`: role labels, grant ordering, "project administrator administers every version", explicit versus inherited access, `canManageRoles` (not `canManage`) deciding who manages roles, assignable people, role choices, one request per vertical, last-role detection, the version tree and its filters, default version, sorting. |
| `versionRolesPanel.test.mjs` | 2 | `VersionRolesPanel`: a version administrator who cannot manage roles sees only their own access and a notice; someone who can loads the members and gets the assignment form. |
| `visibleDiagram.test.mjs` | 2 | Removed nodes and touching edges disappear without changing saved history; empty and restored diagrams. |
| `workspaceRoutes.test.mjs` | 2 | Configuration and results routes never activate version management; switching projects keeps the module and drops foreign ids. |
| **Total (27 files, 2026-11-15)** | **173** | |

> **Current total: 416 tests in 45 files**, verified with `npm test` on 2026-11-23 (416 pass, 0 fail, 0 skipped; was
> 413 before three more `sidebarTheme` cases (26 in all: the dark rail has its own tokens and logo checks), 385 in 44 before `darkThemeContrast` 26 and two more `themeTokens` cases (5 in all), 382 before three more `uiLegibility` cases (8 in all), 377 in 43 before `uiLegibility` 5, 365 in 41 before `buttonLink` 6 and `economicsControls` 6, 346 in 38 before `pageHeader` 10, `tableClasses` 4 and `emptyState` 5, and 343 in 37 before `themeTokens` 3, and 315 in 35 on 2026-11-22 before `sidebarTheme` 23 and `sidebarMode` 5). The table above is the 2026-11-15 snapshot
> with those two rows added; the other files and tests added since are spread over the dated sections below.
> Five files are **not named anywhere in this catalog yet**: `accessInvalidationApi`, `editorPermissions`, `newVersion`,
> `projectAccessState` and `userDetail` (`*.test.mjs`); cataloguing them is pending.

Per-file counts of the older files (economic, results, node editor) come from
running each file; their case-by-case catalogs are in the dated sections above.
`tests/loadModule.mjs` is a helper, not a test file.

**Not covered by a unit test:** the new `413` branch of `useCsvUserImport`
(*El archivo supera el tamaño máximo de 1 MB.*) — `csvImport*.test.mjs` were not
changed in that commit and exercise no `413` — and the `NewVersionModal`
filtering of parents by `canBranchFrom` (only the pure rule it calls is tested).

**The navigation golden.** `permissionsNav.golden.json` records what
`useNavigationTree` returns for every combination; the test fails on any
difference. When the menu changes **on purpose**, regenerate it with
`RECORD_PERMISSIONS_GOLDEN=1 npm test`, read the diff of the JSON and commit it
with the change — never regenerate it to silence a failure you did not expect.

**Playwright scripts (not run for this catalog).** `tests/*-browser.mjs`
(`sidebarLayout`, `startup`, `routes-live`, `projectScoping`, `economic`,
`results`, `probabilistic`, `consumption`, `formInputs`, `scenarioPresentation`,
`summaryVisuals`) drive a local Vite server with isolated HTTP fixtures; set
`APP_URL` and `PLAYWRIGHT_MODULE`. `sidebarLayout-browser.mjs` (2026-11-23,
replacing `sidebarPin-browser.mjs`) covers the fixed column at ≥ `80rem`, the
drawer at 1100 and 390px, the logo's placement, `Ctrl`/`Cmd`+`B` and the
retired `sidebarPinned` key. **It has never been run and, like every other
`*-browser.mjs`, is not part of `npm test` and must be treated as out of
date** until someone runs it against a local Vite server.

## Version roles, cascade delete, root versions and CSV limit — 2026-11-15

Backend changes on `update-frontend` since the 2026-11-14 catalogs (commits
`22c4504`, `158532e`, `e651e24`, `81f93ac`, and the `mine=true` listing and
per-vertical save checks that were never catalogued). Case counts were
refreshed in the later 2026-11-15 pass from the sources and a new `mvn test`
(the table below shows the current ones); one-line
descriptions were written from the method names and a read of their
assertions, **not** by running each case on its own.

| Test class | Type | Cases now | Catalogued here |
| --- | --- | --- | --- |
| `version.service.VersionAccessGuardTest` | Unit | 56 | 26 |
| `version.service.VersionAccessServiceTest` | Unit | 14 | 4 |
| `version.service.VersionMemberServiceTest` | Unit | 26 | 19 (the 7 new ones are in the later section) |
| `version.service.VersionServiceTest` | Unit | 63 | 7 (the 20 new ones are in the later section) |
| `version.controller.VersionControllerTest` | Web | 20 | 7 |
| `version.controller.VersionBranchAccessHttpTest` | Integration | 25 (29 executions) | 10 |
| `version.controller.VersionReadAccessHttpTest` | Integration | 11 | 2 |
| `project.service.ProjectAccessGuardTest` | Unit | 26 | 10 |
| `project.service.ProjectServiceTest` | Unit | 104 | 11 |
| `organization.service.OrganizationServiceTest` | Unit | 88 | 8 |
| `organization.controller.OrganizationControllerTest` | Web | 47 | 5 |
| `organization.repository.OrganizationRepositoryTest` | Data | 24 | 8 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 18 | 5 |
| `scenario.service.ScenarioChangeScopeTest` | Unit | 16 | 16 (all) |
| `economic.controller.ScenarioVerticalAccessHttpTest` | Integration | 22 (28 executions) | 18 |
| `project.service.ProjectVersionCreationPostgreSqlIT` | PostgreSQL (by hand) | 10 | 10 (all) |
| `project.repository.ProjectRepositoryPostgreSqlIT` | PostgreSQL (by hand) | 6 | 6 (all) |
| `version.repository.VersionMemberRepositoryPostgreSqlIT` | PostgreSQL (by hand) | 14 | 5 (+2 in the later section) |

### 1. Who manages version roles

`version.service.VersionAccessGuardTest` — Unit. `assertCanManageVersionRoles`
had **no case of its own in this class** when this section was written; it has
seven now (see the later section) besides the coverage through
`VersionMemberServiceTest` (mocked guard) and the `*HttpTest` classes (real
guard). Cases on the other guard methods:

| Case | Verifies |
| --- | --- |
| `assertCanManageVersionAllowsProjectAdmin` | With no version role, a project `ADMIN` can manage (delete, merge, resolve) the version. |
| `assertCanManageVersionAllowsVersionAdminWithoutConsultingTheProjectRole` | A version `ADMIN` can manage; the project membership is never read. |
| `assertCanManageVersionRejectsVersionEditorWith403WithoutFallingBackToTheProject` | A version `EDITOR` cannot manage and the project role is not consulted. |
| `assertCanManageVersionRejectsUnauthenticated` | No session → `UnauthorizedException`. |
| `aProjectAdminHoldingASimulatorRoleStillManagesEditsAndSimulatesEverything` | A project `ADMIN` who holds a narrow `SIMULATOR` role still passes manage, edit, edit-connections, simulate, view and every vertical. |
| `aProjectAdminHoldingAnEditorRoleOfOneVerticalEditsEveryVertical` | The same for an `EDITOR` of one vertical: every vertical, and manage. |
| `aProjectEditorHoldingASimulatorRoleIsStillRefusedTheProjectAdminExceptionDoesNotApplyToIt` | A project `EDITOR` with a `SIMULATOR` role is refused manage, edit and edit-connections: the exception is for project admins only. |
| `aFormerProjectAdminWithAnInactiveMembershipIsRefusedEvenHoldingAVersionRole` | A project admin whose membership is inactive gets no exception, version role or not. |
| `aVersionAdminIsAllowedWithoutConsultingTheProjectAtAll` | A version `ADMIN` passes manage, edit and simulate with no project lookup. |
| `assertCanManageDescendantAllowsAProjectAdminOfTheSameProject` | A project admin may delete a descendant of the same project as the root. |
| `assertCanManageDescendantRefusesWithAGenericMessageWhenTheCallerCannotManageIt` | A descendant the caller cannot manage → `403 You are not allowed to delete one or more versions below this one`, naming no version. |
| `assertCanManageDescendantRefusesADescendantOfAnotherProjectEvenForItsAdmin` | A descendant that belongs to another project is refused even to that project's admin; no membership lookup. |
| `assertCanManageDescendantRefusesADescendantWithoutProject` | A descendant attached to no project is refused. |
| `assertCanManageDescendantAllowsAPlatformAdminAcrossProjects` | A platform admin passes with no lookup. |
| `assertCanEditVersionAllowsVersionEditorWithoutConsultingTheProjectRole` | A version `EDITOR` may edit the version; the project role is not read. |
| `assertCanViewVersionAllowsAnyVersionRoleWithoutConsultingTheProjectRole` | Any version role is enough to view. |
| `assertCanEditNodeVerticalAllowsEditorMatchingVertical` | An `EDITOR` edits nodes of its own vertical. |
| `assertCanEditNodeVerticalRejectsEditorOfADifferentVerticalWith403WithoutFallingBackToTheProject` | An `EDITOR` of another vertical → `403`, no fallback. |
| `assertCanEditNodeVerticalAllowsAnyEditorForInternalConsumption` | Any `EDITOR` edits `INTERNAL_CONSUMPTION` nodes. |
| `assertCanEditNodeVerticalAllowsVersionAdminForAnyVertical` | A version `ADMIN` edits any vertical. |
| `assertCanEditNodeVerticalFallsBackToProjectEditPermission` | With no version role, the project edit permission decides. |
| `assertCanEditNodeVerticalRejectsVersionSimulatorWith403WithoutFallingBackToTheProject` | A `SIMULATOR` cannot edit nodes, no fallback. |
| `assertCanEditConnectionAllowsAnyVersionEditorRegardlessOfVertical` | Any `EDITOR` edits connections. |
| `assertCanEditConnectionRejectsVersionSimulatorWith403WithoutFallingBackToTheProject` | A `SIMULATOR` cannot edit connections, no fallback. |
| `assertCanSimulateVersionAllowsVersionSimulator` | A `SIMULATOR` may simulate. |
| `assertCanSimulateVersionRejectsVersionSimulatorFromEditingAnythingWithoutFallingBackToTheProject` | A `SIMULATOR` still cannot edit anything. |

`version.service.VersionAccessServiceTest` — Unit, 4 new cases for
`GET /projects/{id}/access`:

| Case | Verifies |
| --- | --- |
| `projectAdminHoldingANarrowVersionRoleKeepsFullAccessAndStillSeesTheRole` | A project admin with a narrow version role gets full access (`PROJECT_ADMIN` source) and the role is still listed. |
| `projectEditorHoldingANarrowVersionRoleDoesNotGetTheProjectAdminException` | A project `EDITOR` keeps only what the version role grants. |
| `aVersionAdminWhoIsNotAProjectAdminManagesTheVersionButNotItsRoles` | `canManage` is true and `canManageRoles` is false for a version admin who is not a project admin. |
| `anInactiveProjectMembershipIsRejectedWith403` | An inactive project membership gets `403`, not access. |

`version.service.VersionMemberServiceTest` — Unit, all 19 cases (the class had
no catalog):

| Case | Verifies |
| --- | --- |
| `addRoleGrantsAnEditorScopedToOneVertical` | An `EDITOR` grant carries its vertical and the default permissions. |
| `addRoleAddsASecondRoleToAnExistingMemberInsteadOfReplacingTheFirst` | A second grant adds a role to the same membership. |
| `addRoleRejectsEditorWithoutAVertical` | `EDITOR` without a vertical → `IllegalArgumentException`. |
| `addRoleRejectsAdminWithAVertical` | `ADMIN`/`SIMULATOR` with a vertical → `IllegalArgumentException`. |
| `addRoleRejectsAUserOutsideTheVersionsProject` | A non-member of the project cannot be granted a role. |
| `addRoleRejectsADuplicateRole` | The same role and vertical twice → `User already has this role`. |
| `addRoleRejectsAProjectEditorCallerWith403` | A project `EDITOR` caller → `403`; nothing is saved. |
| `addRoleRejectsUnknownVersion` | An unknown version → `VersionNotFoundException` (`404`), checked **before** the permission. |
| `addRoleRejectsAUserWhoseProjectMembershipIsInactive` | A user whose project membership is inactive cannot be granted a role. |
| `aProjectAdminCanGrantRoles` | A project admin grants roles. |
| `aVersionAdminWhoIsOnlyAProjectEditorCannotManageRoles` | A version `ADMIN` who is a project `EDITOR` is refused. |
| `listMembersReturnsEveryMemberOfTheVersion` | The listing returns every member. |
| `aProjectAdminCanListTheMembersAndTheCandidatesOfAVersionWithoutRolesOfItsOwn` | A project admin with no role on the version lists members and candidates. |
| `aProjectEditorCannotListTheMembersOfAVersion` | A project `EDITOR` cannot list members. |
| `removeRoleRemovesOnlyTheGivenRoleWhenOthersRemain` | Revoking one role keeps the others. |
| `removeRoleDeletesTheMemberWhenItsLastRoleIsRemoved` | Revoking the last role deletes the membership. |
| `removeRoleAnswers404ForAnUnknownRole` | An unknown role → `404`. |
| `listMemberCandidatesReturnsActiveProjectMembers` | Candidates are the active project members. |
| `listMemberCandidatesLeavesOutInactiveProjectMemberships` | Inactive memberships are left out. |

`version.controller.VersionControllerTest` — Web, 7 cases for the member
endpoints:

| Case | Verifies |
| --- | --- |
| `addVersionMemberReturnsTheCreatedMembership` | `POST /version/{id}/members` → `201` with the membership. |
| `addVersionMemberPropagatesForbiddenWith403` | A `ForbiddenException` → `403` with its message. |
| `addVersionMemberRequiresAuthenticationWith401` | No token → `401`. |
| `listVersionMembersReturnsEveryMember` | `GET /version/{id}/members` → `200` with every member. |
| `removeVersionMemberRoleReturnsOk` | `DELETE …/members/{m}/roles/{r}` → `200`. |
| `removeVersionMemberRolePropagatesForbiddenWith403` | A refusal → `403`. |
| `listVersionMemberCandidatesReturnsCandidates` | `GET /version/{id}/member-candidates` → `200` with the candidates. |

`version.controller.VersionBranchAccessHttpTest` — Integration, 10 new cases
(real guard, H2):

| Case | Verifies |
| --- | --- |
| `projectAdminWithANarrowVersionRoleStillEditsEveryVerticalOfThatVersion` | A project admin holding a narrow role edits every vertical. |
| `projectAdminWithANarrowVersionRoleStillDeletesAndGrantsRolesOnThatVersion` | …and can grant roles on it and delete it. |
| `aVersionAdminWhoIsNotAProjectAdminCannotManageRolesButStillAdministersTheVersion` | A version `ADMIN` who is not a project admin gets `403` on grant, list, candidates and revoke, but can still edit (rename) the version. |
| `aProjectAdminAndAPlatformAdminListAndGrantRolesOfAVersionWithNoRolesOfTheirOwn` | Project admin and platform admin list, list candidates and grant (`201`) with no role of their own. |
| `editorOfAVerticalCreatesAChildBecomesItsAdminAndStaysNarrowInTheParent` | An `EDITOR` of one vertical who creates a child becomes `ADMIN` of the child and stays narrow in the parent. |
| `theCreatorKeepsItsCopiedRolesAndGainsAdminOnTheSameMembershipRow` | The creator's copied roles stay and `ADMIN` is added to the same membership row. |
| `aCreatorWhoIsAlreadyAdminOfTheParentDoesNotGetASecondAdminRole` | No second `ADMIN` role for an existing admin. |
| `theAdminOfTheCreatedChildCanDeleteIt` | The creator, as child admin, deletes it (`200`). |
| `aProjectAdminOrPlatformAdminCreatingAChildGetsNoVersionRole` | Project and platform admins get no version role on a child they create (they already administer it). |
| `projectEditorWithoutVersionRoleBecomesAdminOfTheChildOnly` | A project `EDITOR` with no role becomes `ADMIN` of the child only, not of the parent. |

`version.controller.VersionReadAccessHttpTest` — Integration, 1 case replaced
and 1 new (the class has 11):

| Case | Verifies |
| --- | --- |
| `missingVersionGets403ForANonAdminBeforeAnyLookup` | A non-admin reading the diagram of a version that does not exist gets `403 You are not allowed to access this version`, not `404`. |
| `missingVersionGets404ForAPlatformAdmin` | A platform admin gets `404 Version not found with id: …`. |

(`missingVersionGets404BeforeTheAccessCheck`, catalogued on 2026-11-14, was
replaced by these two — see the 403-before-404 note in `considerations.md`.)

`version.service.VersionServiceTest` — Unit, 7 cases:

| Case | Verifies |
| --- | --- |
| `deleteVersionAuthorizesEveryDescendantAndThenDeletesTheWholeSubtree` | Manage on the root, `assertCanManageDescendant` on each descendant, then one `deleteAllById` from the deepest up. |
| `deleteVersionDeletesNothingWhenAnyDescendantIsForbidden` | One refused descendant → generic `403`, nothing deleted. |
| `deleteVersionRefusesWithoutLookingAtTheTreeWhenTheRootIsForbidden` | A refused root → `403` before the tree is read. |
| `deleteVersionIsTransactional` | `deleteVersion` carries `@Transactional`. |
| `getDiagramOfAMissingVersionChecksAccessAndThen404sForAnAuthorizedCaller` | Access is checked first; an authorized caller then gets `404`. |
| `getDiagramOfAMissingVersionAnswers403WithoutLookingItUpForAnUnauthorizedCaller` | An unauthorized caller gets `403` and the version is never looked up. |
| `consumersCreateEditAndReloadWithinTheirVersion` | The consumer nodes (internal and industrial) create, edit and reload inside their own version; a foreign version is refused. |

### 2. Project administration, root versions and 403 before 400

`project.service.ProjectAccessGuardTest` — Unit, 10 new cases:

| Case | Verifies |
| --- | --- |
| `assertCanViewProjectRejectsAnInactiveMembershipWith403` | An inactive membership cannot view. |
| `assertCanViewProjectRejectsAMemberWhoseAccountIsInactiveWith403` | A member whose account is inactive cannot view. |
| `assertCanEditProjectRejectsAMemberWhoseRoleIsInactiveWith403` | An inactive role grants nothing. |
| `assertCanEditProjectRejectsAnInactiveMembershipWith403` | An inactive membership cannot edit. |
| `canManageProjectIsTrueForAProjectAdmin` | `canManageProject` is true for an admin. |
| `canManageProjectIsFalseForAnEditorWithoutThrowing` | …false for an editor, with no exception. |
| `canManageProjectIsFalseForANonMemberWithoutThrowing` | …false for a non-member. |
| `canManageProjectIsFalseForAnAdminWhoseMembershipIsInactive` | …false for an admin whose membership is inactive. |
| `canManageProjectIsTrueForAPlatformAdminWithoutMembershipLookup` | …true for a platform admin, with no membership lookup. |
| `canManageProjectRejectsUnauthenticated` | No session → `UnauthorizedException`. |

`project.service.ProjectServiceTest` — Unit, 11 new cases:

| Case | Verifies |
| --- | --- |
| `updateProjectAnswers403BeforeValidatingTheBodyForAnEditor` | An `EDITOR` sending an invalid body gets `403`, not `400`. |
| `changeMemberRoleAnswers403BeforeValidatingTheBodyForAnEditor` | The same for changing a member's role. |
| `saveVersionWithoutParentRefusesAProjectEditorWith403` | A root version (no parent) by a project `EDITOR` → `403`; nothing created. |
| `saveVersionWithoutParentAllowsAProjectAdmin` | A project admin creates a root version. |
| `saveVersionRejectsParentFromAnotherProject` | A parent that is not in the project → `Parent version must belong to the selected project`. |
| `saveVersionAcceptsParentFromSameProject` | A parent of the same project is accepted. |
| `saveVersionMakesAProjectEditorWithoutRolesTheAdminOfTheChild` | A project `EDITOR` with no version roles becomes `ADMIN` of the child. |
| `saveVersionAddsAdminToTheCreatorsCopiedRowInsteadOfCreatingASecondOne` | The creator's copied membership gets the `ADMIN` role; no second row. |
| `saveVersionDoesNotGiveTheCreatorASecondAdminRoleWhenTheParentAlreadyMadeThemAdmin` | No duplicate `ADMIN` role. |
| `saveVersionGivesNoRoleToAProjectAdminCreator` | A project admin gets no version role. |
| `saveVersionGivesNoRoleToAPlatformAdminCreator` | A platform admin gets no version role. |

`organization.service.OrganizationServiceTest` — `changeMemberRoleAnswers403BeforeValidatingTheBodyForAPlainMember`:
a plain member sending a null `memberType` gets `403`, not `400`.

### 3. `GET /organizations?mine=true`

| Class | Case | Verifies |
| --- | --- | --- |
| `OrganizationControllerTest` (Web) | `listOrganizationsWithoutMineUsesTheUnfilteredListing` | Without `mine`, the unfiltered listing is used. |
| | `listOrganizationsWithMineFalseKeepsTheUnfilteredListing` | `mine=false` is the same. |
| | `listOrganizationsWithMineTrueReturnsOnlyTheMemberships` | `mine=true` calls `listMineForCurrentUser`. |
| | `listOrganizationsExposesTheCallersMemberType` | Each row carries the caller's `memberType`. |
| | `listOrganizationsWithMineTrueRequiresAuthenticationWith401` | No token → `401`. |
| `OrganizationServiceTest` (Unit) | `listForCurrentUserFillsMemberTypeForRegularUser` | The regular listing fills `memberType`. |
| | `listForCurrentUserLeavesMemberTypeNullForAdminOutsideTheOrganization` | An admin outside an organization gets `null`. |
| | `listForCurrentUserLeavesMemberTypeNullForAdminWithNoMembership` | An admin with no membership gets `null` everywhere. |
| | `listMineForCurrentUserReturnsTheCallersMembershipsForAdmin` | `mine` for an admin returns only their memberships. |
| | `listMineForCurrentUserIsEmptyForAdminWithNoMembership` | …and is empty for an admin with none. |
| | `listMineForCurrentUserReturnsTheCallersMembershipsForRegularUser` | The same for a regular user. |
| | `listMineForCurrentUserRejectsUnauthenticated` | No session → `UnauthorizedException`. |
| `OrganizationRepositoryTest` (Data) | `mineSummariesCarryTheOwnerRole` / `mineSummariesCarryTheMemberRole` | The summaries carry the caller's role. |
| | `mineSummariesReturnOnlyTheCallersOrganizationsWithTheFullMemberCount` | Only the caller's organizations, with the full member count. |
| | `mineSummariesComeBackOrderedByName` | Ordered by name. |
| | `mineSummariesExcludeDeactivatedOrganizations` / `…DeactivatedMemberships` / `…DeactivatedUsers` | Deactivated organizations, memberships and users are excluded. |
| | `mineSummariesAreEmptyForAUserInNoOrganization` | A user in no organization gets nothing. |

### 4. Bulk CSV: `jobTitle` and the 500-row cap

`organization.service.OrganizationBulkRegistrationServiceTest` — Unit, 5 new cases:

| Case | Verifies |
| --- | --- |
| `storesTheJobTitleFromThePuestoColumn` | The `puesto` column (alias of `jobTitle`) is stored on the created user. |
| `leavesTheJobTitleEmptyWhenThereIsNoColumnForIt` | No column → no job title. |
| `rejectsAJobTitleLongerThanTheLimitAsFailure` | A title over 120 characters is a per-row failure, not a batch error. |
| `throwsWhenFileHasMoreThanMaxRows` | More than 500 data rows → `IllegalArgumentException` `The CSV file has more than 500 rows`; nothing is created. |
| `acceptsExactlyMaxRows` | Exactly 500 data rows are processed (the header does not count). |

### 5. Per-vertical checks when saving a scenario

`scenario.service.ScenarioChangeScopeTest` — Unit, 16 cases: which nodes a save
changes, and whether the caller may edit each one's vertical.

| Case | Verifies |
| --- | --- |
| `resendingEverythingUnchangedChangesNoNode` | Resending the stored values changes no node. |
| `numericTextEqualToTheStoredValueIsNotAChange` | `"50"` against a stored `50` is not a change. |
| `physicalFieldChangeMarksOnlyThatNode` | A physical field change marks only its node. |
| `invalidPhysicalValueCountsAsAChange` | An invalid value counts as a change (so it is authorized). |
| `missingAndUnknownNodesAreIgnored` | Nodes not in the version are ignored. |
| `costRuleChangeOnANodeProfileMarksThatNode` | A cost rule on a node profile marks that node. |
| `assetMovedBetweenNodesMarksBothNodes` | Moving an asset marks both nodes. |
| `contractAndConversionAreAttributedToTheirNode` | Contracts (delivery node) and conversions are attributed to their node. |
| `globalOnlyChangeMarksNoNode` | A global-only change marks no node. |
| `firstSaveWithoutStoredConfigurationMarksEveryReferencedNode` | A first save marks every referenced node. |
| `reorderedRowsAndTrailingZerosAreNotAChange` | Reordering and trailing zeros are not changes. |
| `unparseableRequestedConfigurationSkipsTheEconomicComparison` | An unparseable configuration skips the economic comparison. |
| `draftRowsAreComparedPerNodeAgainstThePreviousDraft` | Draft rows are compared per node against the previous draft. |
| `assertCanSaveChecksTheVerticalOfEachChangedNodeOnly` | Only the changed nodes' verticals are checked. |
| `assertCanSavePropagatesTheVerticalDenial` | A vertical denial propagates as `403`. |
| `untypedNodeWithChangedEconomicsFallsBackToVersionEdit` | A node with no type falls back to the version-edit check. |

`economic.controller.ScenarioVerticalAccessHttpTest` — Integration, 18 cases
not catalogued before (22 in the class now, 28 executions; the 3 newest are in
the later section):

| Case | Verifies |
| --- | --- |
| `editorPublishesAChangeToANodeOfItsOwnVertical` | An `EDITOR` saves a change to a node of its vertical. |
| `editorChangingANodeOfAnotherVerticalGets403AndNothingIsSaved` | …another vertical → `403`, nothing stored. |
| `editorResendingOtherVerticalsUnchangedSaves` | Resending other verticals unchanged saves. |
| `editorChangingACostOfAnotherVerticalsNodeGets403` / `editorChangingACostOfItsOwnVerticalsNodeSaves` | A cost on another vertical's node is refused; on its own it saves. |
| `editorChangingOnlyGlobalDataSaves` | Global data saves. |
| `anyEditorChangesAnInternalConsumptionNode` | Any `EDITOR` changes an internal-consumption node. |
| `draftWithAnInvalidValueOnAnotherVerticalsNodeGets403AndIsNotStored` | An invalid value on another vertical's node → `403` (not a validation error), nothing stored. |
| `draftWithAnInvalidValueOnItsOwnVerticalsNodeIsStored` | The same on its own vertical → `200` with the errors listed, draft stored. |
| `editorOfAnyVerticalDiscardsADraftHoldingAnotherVerticalsChange` | Discarding a draft is not vertical-scoped. |
| `simulatorStillCannotSaveOrDiscard` | A `SIMULATOR` cannot save or discard. |
| `versionAdminChangesNodesOfEveryVertical` / `projectAdminWithoutVersionRoleChangesNodesOfEveryVertical` / `platformAdminChangesNodesOfEveryVertical` | Admins change every vertical. |
| `firstSaveWithRowsOfAnotherVerticalGets403ForAnEditorAndSavesForAnAdmin` | A first save with another vertical's rows: `403` for an editor, saved for an admin. |
| `economicConfigurationEndpointDeniesAnotherVerticalsCostAndAllowsGlobalChanges` | The economic configuration endpoint applies the same rule. |
| `economicDraftEndpointChecksTheVerticalOfEachRowsNode` | The economic draft endpoint checks each row's node. |
| `editorCopyingTheScenarioIntoAChildVersionBecomesItsAdminAndSavesEveryVerticalThere` | An editor who copies a scenario into a child becomes its admin and saves every vertical there. |

### 6. PostgreSQL classes (by hand)

`project.service.ProjectVersionCreationPostgreSqlIT` — 10 cases against the real
schema:

| Case | Verifies |
| --- | --- |
| `theChildCopiesEveryRoleOfTheParentWithItsVerticalNameAndPermissions` | Role, vertical, name and permissions are copied. |
| `theChildDoesNotCopyTheRowOfSomeoneWhoIsNotInTheProjectButKeepsThatOfADeactivatedMember` | Non-members are not copied; deactivated members are. |
| `aCreatorWithoutRolesInTheParentBecomesTheOnlyAdminRowOfTheChild` | A creator with no roles gets a single `ADMIN` row. |
| `theCreatorGetsAdminOnTheSameRowCopiedFromTheParent` | `ADMIN` is added to the copied row. |
| `aCreatorWhoIsAlreadyAdminOfTheParentKeepsASingleAdminRole` | One `ADMIN` role only. |
| `theUniqueConstraintRejectsASecondRowForTheSameUserAndVersion` | The unique constraint on user + version holds. |
| `aProjectAdminOrAPlatformAdminCreatingAChildGetsNoRow` | Admins get no row. |
| `aCreatorWhoseProjectMembershipIsInactiveIsRefusedAndNothingIsCreated` | An inactive membership is refused; nothing created. |
| `aProjectEditorCannotCreateARootVersionButAProjectAdminCan` | Root versions: project admin only. |
| `deletingAVersionWithADescendantInAnotherProjectIsRefusedForAProjectAdminAndAllowedForAPlatformAdmin` | A cascading delete reaching another project's version is refused for a project admin and allowed for a platform admin. |

`project.repository.ProjectRepositoryPostgreSqlIT` — 6 cases for
`findIdByVersionId` and its active-only variant: it returns the owning project;
the right one when several exist; empty for a version in no project and for an
unknown id; it still answers for an inactive version or project; and the
active-only lookup leaves those out.

`version.repository.VersionMemberRepositoryPostgreSqlIT` — 5 cases not
catalogued before (14 in the class now; the 2 newest are in the later section):

| Case | Verifies |
| --- | --- |
| `effectiveLookupReturnsTheRowWhileTheProjectMembershipIsActive` | The effective lookup returns the row while the project membership is active. |
| `effectiveLookupIgnoresTheRowOnceTheProjectMembershipIsDeactivated` | …and ignores it once deactivated. |
| `effectiveLookupFindsTheRowAgainOnceTheProjectMembershipIsReactivated` | …and finds it again on reactivation. |
| `memberListingKeepsTheRowOfADeactivatedProjectMember` | The listing keeps the row of a deactivated project member. |
| `accessResolutionTreatsADeactivatedProjectMemberAsHavingNoAccess` | Access resolution gives a deactivated member nothing. |

### Still not catalogued

Seven cases of earlier work remain without a line in this file:
`ScenarioConfigurationTest` (`removedNodesStayInHistoryButLeaveConfigurationAndNewInputs`,
`removingReferencedNodeRequiresEconomicRulesToBeReviewed`),
`ScenarioOperationsTest` (`consumptionFieldsRoundTripAndCloneWithoutChangingOriginal`),
`MigrationCatalogTest` (3 cases) and `ConsumptionPersistenceTest` (1 case). The
per-class sections above also still carry older cases that were renamed.

## Active-only guards, 413, 403 before 400 and the rewritten `ResultMappingTest` — 2026-11-15

Backend and frontend changes on `update-frontend` after the section above
(commits `9a4a27f`, `b4dfa71` and `ff35593`). Counts are the ones in the
[*Summary*](#summary) (a `mvn test` run on 2026-11-15, 13:35, plus the sources).
The one-line descriptions come from the method names and a read of the code of
the classes catalogued below; they were **not** produced by running each case on
its own.

| Test class | Type | New cases | Where |
| --- | --- | --- | --- |
| `version.controller.VersionInactiveAccessHttpTest` | Integration | 5 (new class) | 1 |
| `version.controller.VersionReadWithoutOpenInViewHttpTest` | Integration | 4 (new class) | 2 |
| `version.controller.VersionRoleCopyDeactivatedUsersHttpTest` | Integration | 3 (new class) | 3 |
| `version.controller.VersionAccessHttpTest` | Integration | 12 (new class) | 4 |
| `simulator.ResultMappingTest` | Data | 8 (rewritten class) | 5 |
| `organization.controller.BulkUploadSizeLimitHttpTest` | Integration | 2 (new class) | 6 |
| `common.GlobalExceptionHandlerTest` | Unit | 1 (new class) | 6 |
| `organization.controller.OrganizationControllerTest` | Web | 1 | 6 |
| `version.service.VersionAccessGuardTest` | Unit | 7 | 7 |
| `version.service.VersionMemberServiceTest` | Unit | 7 | 7 |
| `version.service.VersionServiceTest` | Unit | 20 | 8 |
| `version.service.VersionAccessServiceTest` | Unit | 1 (+2 assertions renamed) | 4 |
| `economic.service.EconomicServiceTest` | Unit | 2 (+2 assertions changed) | 9 |
| `economic.controller.ScenarioVerticalAccessHttpTest` | Integration | 3 | 9 |
| `version.controller.VersionMergeAccessHttpTest` | Integration | 9 (2 parameterized: +14 executions) | 10 |
| `version.repository.VersionMemberRepositoryPostgreSqlIT` | PostgreSQL (by hand) | 2 | 11 |

### 1. `version.controller.VersionInactiveAccessHttpTest` — Integration, 5 cases

A version role holder is refused once the version or its project is deactivated.
Each check hits `GET /version/{id}`, `/diagram` and `/conflicts`. These pin the
`v.active = true AND p.active = true` added to
`VersionMemberRepository.findEffectiveByVersionIdAndUserId`.

| Case | Verifies |
| --- | --- |
| `aVersionRoleHolderReadsWhileTheProjectAndTheVersionAreActive` | A project `EDITOR` holding a version `ADMIN` role reads all three endpoints (`200`). |
| `aVersionRoleHolderIsRefusedOnceTheVersionIsDeactivated` | After the version is deactivated, the same three reads answer `403`. |
| `aVersionRoleHolderIsRefusedOnceTheProjectIsDeactivated` | After the project is deactivated, the same three reads answer `403`. |
| `aVersionSimulatorIsRefusedOnceTheVersionIsDeactivated` | A `SIMULATOR` role is cut off the same way (`403`). |
| `aPlatformAdminStillReadsADeactivatedVersion` | A platform admin still gets `200` on the version and its diagram. |

### 2. `version.controller.VersionReadWithoutOpenInViewHttpTest` — Integration, 4 cases

Runs with `spring.jpa.open-in-view=false` (its own H2 database) and no
surrounding transaction, to show the reads authorize without a persistence
session left open by the web layer. These pin the `@Transactional(readOnly =
true)` on `VersionService.getVersion` and
`VersionConflictService.getConflictsForVersion`.

| Case | Verifies |
| --- | --- |
| `getVersionAuthorizesAVersionRoleHolderWithoutAnOpenSession` | `getVersion` called straight on the service by a version role holder returns the version. |
| `getVersionAuthorizesAProjectMemberWithoutAVersionRoleAndWithoutAnOpenSession` | The same for a project member with no version role (fallback to project membership). |
| `getConflictsForVersionAuthorizesAProjectMemberWithoutAVersionRoleAndWithoutAnOpenSession` | `getConflictsForVersion` for a project member with no role answers an empty list. |
| `theConflictsEndpointRespondsToAVersionRoleHolderWithoutOpenInView` | `GET /version/{id}/conflicts` over MockMvc answers `200` for a version role holder. |

> This class deliberately does **not** call `GET /version/{id}` over HTTP: that
> endpoint returns the `Version` entity and fails with `open-in-view=false` (a
> known debt, see `considerations.md`, 2026-11-15). `getVersion` is exercised on
> the service only.

### 3. `version.controller.VersionRoleCopyDeactivatedUsersHttpTest` — Integration, 3 cases

What branching a version does with the roles of people who are no longer active
(`ProjectService.copyVersionRoles`). It pins today's behavior, including the
inert rows (see the debt in `considerations.md`).

| Case | Verifies |
| --- | --- |
| `aBranchCopiesTheRolesOfActiveMembersAndTheyKeepWorkingThere` | The child copies the active holder's role and that person reads the child's diagram (`200`). |
| `aBranchCopiesTheRolesOfADeactivatedProjectMemberButTheyGrantNoAccessThere` | The row of a deactivated project member **is** copied to the child, but `findEffectiveByVersionIdAndUserId` finds nothing for them and they get `403` on the child; the active holder still has an effective role. |
| `aBranchCopiesTheRolesOfADeactivatedUserAccountButTheyAreNeverEffectiveOnceTheMembershipIsDeactivated` | The row of a deactivated account with a deactivated membership is copied and is never effective. |

### 4. `GET /projects/{id}/access` and `canCreateRootVersions`

`version.controller.VersionAccessHttpTest` — Integration, 12 cases over the real
guard and database (`canCreateVersions` became `canCreateRootVersions`, true
only with `MANAGE_PROJECT`):

| Case | Verifies |
| --- | --- |
| `platformAdminGetsFullAccessWithoutBeingAMember` | A platform admin: `platformAdmin`, `canManageProject`, `canCreateRootVersions`, source `PLATFORM_ADMIN`, `canManage` and `canManageRoles`. |
| `projectAdminManagesTheProjectAndEveryVersion` | A project `ADMIN`: `canCreateRootVersions` and `canManageProject` true, source `PROJECT_ADMIN`, effective role `ADMIN`, `canManageRoles`. |
| `projectEditorEditsVersionsButCannotCreateRootsOrManage` | A project `EDITOR`: `canCreateRootVersions` **false**, source `PROJECT_FALLBACK`, can view and edit, cannot manage or manage roles, no roles. |
| `versionSimulatorCanOnlyViewAndSimulate` | A `SIMULATOR` role: source `VERSION_ROLES`, view and simulate only, no editable verticals, `canCreateRootVersions` false. |
| `versionEditorOfOneVerticalEditsThatVerticalAndInternalConsumptionOnly` | An `EDITOR` of `EXTRACTION`: editable verticals are that one plus internal consumption; cannot manage. |
| `versionAdminWhoIsOnlyAProjectEditorManagesTheVersionButNotItsRoles` | A version `ADMIN` who is a project `EDITOR`: `canManage` true, `canManageRoles` false, `canManageProject` false. |
| `deactivatedVersionsAreLeftOutOfTheList` | A deactivated version is not in `versions`. |
| `aUserOutsideTheProjectGets403` | An organization member outside the project: `403`. |
| `anOrganizationOwnerOutsideTheProjectGets403` | An organization owner outside the project: `403`. |
| `aDeactivatedProjectMemberGets403` | A deactivated project member: `403`. |
| `anAnonymousCallerGets401` | No token: `401`. |
| `anUnknownProjectAnswers404ForAPlatformAdmin` | An unknown project id: `404` for a platform admin (a non-admin would also get `404`, before the `403` — see the debt in `considerations.md`). |

`version.service.VersionAccessServiceTest` — one new case,
`projectEditorWithoutVersionRolesCannotCreateRootVersionsButStillEditsThemAll`
(a project editor with no roles: `canManageProject` and `canCreateRootVersions`
false, every version `canEdit`); two existing assertions changed from
`canCreateVersions` to `canCreateRootVersions` (an editor: false; an admin: true).
`VersionAccessControllerTest` follows the rename in its JSON assertions
(`$.data.canCreateRootVersions`); its case count is unchanged.

### 5. The rewritten `simulator.ResultMappingTest` — Data, 8 cases

`@DataJpaTest` on H2 (`MODE=PostgreSQL`, Hibernate-generated schema, Flyway off)
pinning the mapping of `FinalResult` / `ResultPerNode` to the table and column
names `V8__create_results.sql` uses. It does **not** run against PostgreSQL and
does not execute the migration; see `considerations.md`, 2026-11-15.

| Case | Verifies |
| --- | --- |
| `aFinalResultIsStoredAndLoadedBackWithItsPercentilesAndValues` | A saved result reloads with its `time`, `mediaOutput`, the sizes of the three percentile lists and a node's figures. |
| `thePercentileListsAreStoredInTheirOwnJoinTables` | Rows land in `final_result_p90` (2), `final_result_p50` (1), `final_result_p10` (0) and `result_per_node` (3), owned by the result. |
| `resultPerNodeRowsUseTheColumnsOfTheMigration` | A `result_per_node` row has `NODE_CLASS`, `TOTAL_PRODUCED`, `TOTAL_DEFERRED` and `MAX_POSSIBLE_PRODUCED`. |
| `resultPerNodeRowsStoreTheNodeIdInTheNodeIdColumn` | The node id is stored in `NODE_ID`. |
| `theFinalResultRowKeepsItsTimeMediaOutputAndVersionColumns` | `final_result` has `TIME`, `MEDIA_OUTPUT` and `VERSION_ID`. |
| `noJoinTableOutsideTheMigrationsIsCreated` | Hibernate creates no join table the migrations do not define. |
| `removingARowFromAPercentileListDeletesItsPerNodeRow` | Removing an element from a percentile list deletes its `result_per_node` row (orphan removal). |
| `deletingAVersionDeletesItsFinalResultsAndTheirRows` | Deleting the version deletes its results, the join rows and the per-node rows. |

### 6. `413` for a CSV above 10 MB

The limits are `spring.servlet.multipart.max-file-size=10MB` and
`spring.servlet.multipart.max-request-size=11MB`. The integration test sends
just over 10 MB, keeping the multipart request below 11 MB, and the handler
answers `413 Uploaded file exceeds the maximum allowed size`.

| Class | Case | Verifies |
| --- | --- | --- |
| `common.GlobalExceptionHandlerTest` (Unit) | `uploadAboveTheLimitAnswers413WithAClearMessage` | The handler returns `413`, `success=false` and the message. |
| `organization.controller.OrganizationControllerTest` (Web) | `bulkRegisterUsersAnswers413WhenTheFileExceedsTheUploadLimit` | `POST /organizations/{id}/users/bulk` answers `413` with that body when the service throws `MaxUploadSizeExceededException` (mocked). |
| `organization.controller.BulkUploadSizeLimitHttpTest` (Integration, real server on a random port) | `aCsvAboveTenMegabytesAnswers413InsteadOf500` | A real multipart upload of 10 MB + 1 byte, sent by a platform admin, answers `413` with the message. |
| | `aCsvOfExactlyTenMegabytesIsNotRejectedForItsSize` | Exactly 10 MB is not answered `413` (the test only asserts the status is not `413`; it does not check what the endpoint answers instead). |

> **Not tested: `413` before `403`.** The `413` is raised while the multipart
> request is parsed, before the request reaches the controller, so a caller who
> may not use the endpoint still sees `413` for an oversized file. That order
> follows from where Spring parses the body; **no test** sends an oversized file
> as a caller without permission (`BulkUploadSizeLimitHttpTest` uses a platform
> admin). The frontend's *El archivo supera el tamaño máximo de 1 MB.* branch has
> no unit test either.

### 7. `403` before `404` on the role endpoints

`assertCanManageVersionRoles` now runs **before** the version lookup, so an
unknown version id is `403` for everybody but a platform admin (`404`).

`version.service.VersionAccessGuardTest` — 7 cases of `assertCanManageVersionRoles`:

| Case | Verifies |
| --- | --- |
| `assertCanManageVersionRolesAllowsAPlatformAdminWithoutAnyLookup` | A platform admin passes with no repository access. |
| `assertCanManageVersionRolesAllowsAProjectAdmin` | A project `ADMIN` passes; version memberships are never read. |
| `assertCanManageVersionRolesDeniesAVersionAdminWhoIsOnlyAProjectEditor` | A version `ADMIN` who is a project `EDITOR` is refused (`403`). |
| `assertCanManageVersionRolesDeniesAProjectEditor` | A project `EDITOR` is refused with `You are not allowed to manage the roles of this version`. |
| `assertCanManageVersionRolesDeniesAUserOutsideTheProject` | A non-member is refused. |
| `assertCanManageVersionRolesRejectsUnauthenticated` | No session → `UnauthorizedException`, no lookups. |
| `assertCanManageVersionRolesDeniesAUnknownVersionWithoutLookingUpMemberships` | A version attached to no project → `403`, no membership lookups. |

`version.service.VersionMemberServiceTest` — 7 cases:

| Case | Verifies |
| --- | --- |
| `addRoleAnswers403ForAnUnknownVersionToARegularUser` | An unknown version → `403` and `versionRepository.findById` is never called. |
| `listMembersAnswers403ForAnUnknownVersionToARegularUser` | The same for listing members (`existsById` never called). |
| `listMembersAnswers404ForAnUnknownVersionToAPlatformAdmin` | A platform admin gets `VersionNotFoundException` (`404`). |
| `removeRoleAnswers403ForAnUnknownVersionToARegularUser` | The same `403` for revoking a role. |
| `removeRoleAnswers404ForAnUnknownVersionToAPlatformAdmin` | …and `404` for a platform admin. |
| `listMemberCandidatesAnswers403ForAnUnknownVersionToARegularUser` | The same `403` for the candidates listing. |
| `listMemberCandidatesAnswers404ForAnUnknownVersionToAPlatformAdmin` | …and `404` for a platform admin. |

### 8. `version.service.VersionServiceTest` — Unit, 20 new cases (43 → 63)

Access is checked before anything is loaded, and nothing is saved when it is
refused.

| Case | Verifies |
| --- | --- |
| `saveOrphanVersionIsRefusedForANonPlatformAdminAndSavesNothing` / `saveOrphanVersionCreatesTheVersionForAPlatformAdmin` | A detached version needs a platform admin (`assertIsPlatformAdmin("create detached versions")`); a non-admin saves nothing. |
| `getVersionIsRefusedWithoutLookingItUpWhenTheCallerCannotViewIt` / `getVersionChecksViewAccessAndReturnsTheVersion` / `getVersionOfAMissingVersionChecksAccessAndThen404sForAnAuthorizedCaller` | `getVersion` checks view access first (`403` without a lookup), returns the version, and `404`s a missing one only for an authorized caller. |
| `updateNodePositionIsRefusedBeforeLoadingAnythingWhenTheCallerCannotViewTheVersion` / `updateNodeBasicsIsRefusedBeforeLoadingAnythingWhenTheCallerCannotViewTheVersion` | A caller who cannot view the version is refused before any repository is touched. |
| `updateNodePositionIsRefusedWhenTheCallerCannotEditTheVerticalOfTheNodeAndSavesNothing` / `updateNodeBasicsIsRefusedWhenTheCallerCannotEditTheVerticalOfTheNodeAndSavesNothing` | A refusal on the node's vertical saves nothing (the node keeps its name). |
| `updateNodePositionOfAnUntypedNodeFallsBackToTheVersionEditCheck` | A node with no vertical falls back to `assertCanEditVersion`. |
| `addConnectionToVersion…` / `editConnectionInVersion…` / `deleteConnectionFromVersion…` — `IsRefusedBeforeTouchingAnythingWhenTheCallerCannotEditConnections` | Each is refused before any repository is touched (3 cases). |
| `addConnectionToVersion…` / `editConnectionInVersion…` / `deleteConnectionFromVersion…` — `ChecksConnectionEditAccessAndThen404sForAMissingVersion` | Each checks connection-edit access and then `404`s a missing version (3 cases). |
| `modifyVersionIsRefusedBeforeLoadingTheVersionWhenTheCallerCannotEditIt` | `modifyVersion` is refused before loading the version. |
| `saveVersionRejectsABlankName` / `…AWhitespaceOnlyName` / `…ANullName` | Blank and whitespace-only names are `IllegalArgumentException("Version name cannot be blank or whitespace only")`; a null one `Version name cannot be null`; nothing is saved. |

### 9. `403` before `400` in the economic configuration and draft

`EconomicService.save` and `saveDraft` now run the per-vertical check
(`ScenarioChangeScope`) **before** the validation.

`economic.controller.ScenarioVerticalAccessHttpTest` — 3 new cases (22 in the
class, 28 executions):

| Case | Verifies |
| --- | --- |
| `economicConfigurationWithAnotherVerticalsCostAndAnInvalidValueGets403Not400` | An `EXTRACTION` editor who raises an `EXPORT` cost **and** sends `wacc = -1` gets `403`; the stored configuration is unchanged. |
| `economicConfigurationWithOnlyAnInvalidValueStillGets400` | The same editor sending only the invalid value still gets `400`. |
| `economicDraftOnAnotherVerticalsNodeWithAMalformedStructureGets403Not400` | A draft with a malformed structure on another vertical's node: `403` for the `EXTRACTION` editor, `400` for the `EXPORT` editor; no draft is stored. |

`economic.service.EconomicServiceTest` — 2 new cases and 2 changed assertions:

| Case | Verifies |
| --- | --- |
| `saveWithANullConfigurationAnswersIllegalArgumentNotNullPointer` | A null configuration → `IllegalArgumentException`, nothing saved. |
| `saveDraftWithANullBodyAnswersIllegalArgumentNotNullPointer` | A draft whose body is null → `IllegalArgumentException`, no draft saved. |
| (changed) two existing cases | `verifyNoInteractions(configs, …)` / `verifyNoInteractions(drafts)` became `verify(configs, never()).save(any())` / `verify(drafts, never()).save(any())`, because `save` now reads the stored configuration (for the vertical check) before the validation that refuses the request. |

### 10. `version.controller.VersionMergeAccessHttpTest` — 9 new cases (24 in the class, 41 executions)

`REJECT` on a conflict, and conflicts between projects. The two parameterized
cases account for the +14 executions: 5 verticals (`@EnumSource(VerticalEnum)`)
plus 2 decisions (`ACCEPT`, `REJECT`) = 7 executions from the two parameterized
cases, on top of the 7 plain cases: 14 in all.

| Case | Verifies |
| --- | --- |
| `adminOfTheSiblingRejectsAndKeepsItsOwnDataOfAnyVertical` (parameterized over the verticals) | The admin of the sibling can `REJECT` a node conflict of any vertical: `200`, the sibling keeps its own data, the conflict is resolved and an `EDIT` change is recorded. |
| `rejectingRecordsNoChangeWhenTheSiblingNeverHadTheNode` | When the sibling no longer has the node, `REJECT` answers `200` and records a `DELETE` change for that node (despite the method name, a change **is** recorded: the deletion). |
| `editorOfTheNodeVerticalCannotRejectAndNothingIsRecorded` | An editor of the node's vertical cannot reject: no change recorded, conflict still open. |
| `adminOfTheSiblingWithoutEditPermissionCannotRejectANodeConflict` | A sibling admin without edit permission cannot reject a node conflict: nothing recorded, conflict still open. |
| `rejectingAnUnknownConflictAnswers404` | An unknown conflict id → `404`. |
| `anUnknownDecisionAnswers400` | A decision other than the known ones → `400`, conflict unresolved. |
| `aChildHangingFromAVersionOfAnotherProjectCannotRejectAndNothingIsRecorded` | A caller attacking through a child whose parent is in another project gets `403`; nothing recorded, conflict still open. |
| `aConnectionConflictOfAChildHangingFromAnotherProjectCannotBeResolvedAndNothingIsCopied` (parameterized over the decisions) | A connection conflict across projects → `403` for each decision (`ACCEPT` and `REJECT`); the child's connections and changes stay empty, the foreign parent keeps its connection, conflict unresolved. |
| `listingAChildHidesConnectionConflictsRecordedAgainstAnotherProject` | The conflicts listing of a child does not show connection conflicts recorded against another project. |

### 11. `version.repository.VersionMemberRepositoryPostgreSqlIT` — 2 new cases (14 in the class)

By hand, on PostgreSQL (see the *PostgreSQL classes* table above; not run for
this update).

| Case | Verifies |
| --- | --- |
| `effectiveLookupIgnoresTheRowsOfADeactivatedVersionOnly` | After one version is deactivated, `findEffectiveByVersionIdAndUserId` returns nothing for its rows (caller and colleague) and still returns the row of the other version. |
| `effectiveLookupIgnoresEveryRowOfADeactivatedProject` | After the project is deactivated, no row of any of its versions is effective. |

## Structural role, editor simulate permission, role change and project matrix — 2026-11-15

Backend only. Counts are in the Summary; this lists what each test pins.

- **`node.model.enums.NodeTypeEnumTest`** (+2 cases): `everyNodeTypeHasItsStructuralRole`
  (12 executions: `role()`, `checkedRole(null)` and `checkedRole(role)` for each type, with
  `PIPELINE_CONECTION` = `INTERMEDIATE`) and `checkedRoleRejectsEveryOtherRole` (12 executions).
- **`node.service.NodeClonerRoleTest`** (new, 2): a clone recomputes the role from the type when
  the stored role is inconsistent; with a `null` type it copies the stored role.
- **`version.controller.VersionNodeVerticalAccessHttpTest`** (+6 cases): per type, create without a
  role stores the type's role, create with the right role stores it, create with another role is
  `400` and nothing is created; edit with another role is `400` and the stored node is untouched;
  edit without a role keeps it; a platform admin gets the same `400` on `POST /version/{id}/node`.
  Two fixtures were corrected (`SEAPORT_TERMINAL` is `SINK`, in this class and in
  `NodeCreateAccessHttpTest`); the validation was not relaxed. `golden/version-get.json` did not change
  (only `WELL`/`GENERATOR`).
- **`version.service.VersionAccessTest`** (+1): the editor simulates through its stored
  `SIMULATE_VERSION`; an `EDITOR` row without it (not yet backfilled) does not.
  **`node.migration.MigrationCatalogTest`** expects 24 migrations.
- **`version.repository.VersionMemberRepositoryPostgreSqlIT`** (+2, by hand, not run):
  `v24BackfillsEditorSimulatePermissionAndIsIdempotent` runs `V24` twice (second run inserts 0, total
  unchanged, every `EDITOR` has the permission, `ADMIN`/`SIMULATOR` untouched) and
  `projectMatrixQueryCountDoesNotGrowWithMembersAndRoles` (statement count ≤ 3 and equal with 6 more
  members and 12 more roles; Hibernate statistics).
- **`version.service.VersionMemberServiceTest`** (+10, `changeRole…`): editor→simulator keeps the id and
  replaces permissions; admin→editor leaves no `MANAGE_VERSION`; the shared defaults are not mutated;
  a single-role member is not deleted; duplicate is `400 User already has this role`; same shape is a
  no-op (no save); vertical change; shape errors; `403` before any lookup; the three `404`s.
- **`version.controller.VersionRoleChangeHttpTest`** (new, 10): project admin and platform admin change a
  role (id kept, permissions replaced, member kept); duplicate is `400` like `POST`; `403` for version
  admin/editor/simulator, project editor and unrelated user; `403` before `404`; `404` for unknown or
  other-version member/role; unknown version is `404` for a platform admin and `403` otherwise; invalid body `400`.
- **`version.controller.ProjectVersionMembersHttpTest`** (new, 5): admins read the matrix; everyone else
  `403`; unknown project `403`/`404`; inactive versions left out and empty versions listed with `[]`; each
  version equals `GET /version/{id}/members`. `ProjectControllerTest` gained a `VersionMemberService` mock.


## Project member type EXECUTIVE — 2026-11-15

Backend only. Counts are in the Summary (+25 cases, 1488 executions); this lists
what each test pins. Behaviour: [`backend/README.md`](../backend/README.md).

- **`project.model.ProjectMemberTest`** (new, 3): `EXECUTIVE` has only `VIEW_PROJECT`; every
  `ProjectMemberType` has default permissions (a new type without a mapping fails here);
  `ADMIN` (all three) and `EDITOR` (`EDIT_PROJECT` + `VIEW_PROJECT`) are unchanged.
- **`project.service.ProjectServiceTest`** (+4): `addMember` with `EXECUTIVE` stores only
  `VIEW_PROJECT`; changing a member to `EXECUTIVE` deletes their version memberships of that
  project (`deleteAll`); changing between `ADMIN` and `EDITOR` never touches the version
  repository; the last `ADMIN` cannot become an `EXECUTIVE` (`IllegalArgumentException`, nothing
  saved or deleted, role unchanged).
- **`project.repository.ProjectRepositoryTest`** (+1, Data): `findSummaries` and
  `findSummariesForMember` both report `EXECUTIVE` as `myRole`.
- **`version.service.VersionMemberServiceTest`** (+3): `addRole` answers `400 An EXECUTIVE of the
  project cannot hold version roles` for `SIMULATOR`, `ADMIN` and `EDITOR`+vertical, saving nothing;
  `changeRole` is `400` for an executive and keeps the stored role; an unknown member is still `404`
  before the executive check.
- **`version.service.VersionAccessServiceTest`** (+1): an executive's project access is
  `PROJECT_FALLBACK`, `effectiveRole` `EXECUTIVE`, no roles or editable verticals, `canView` and the
  economic view flags only; every other capability `false`, `canManageProject` and
  `canCreateRootVersions` `false`.
- **`version.controller.VersionAccessHttpTest`** (+1): the same over `GET /projects/{id}/access`.
- **`version.controller.ProjectExecutiveHttpTest`** (new, 12, full context on H2): reads `200` (own
  `myRole` in `GET /projects`, members, versions, version, diagram, node, economic configuration,
  draft, scenario, evaluations, jobs); access says view-only on every version; `403` on node create,
  move, edit and delete, on creating, renaming or deleting versions, on saving configuration, draft
  and scenario, simulating and retrying a job, and on project members, member candidates, the role
  matrix and version members; a project `ADMIN` assigns and revokes an `EXECUTIVE` while an `EDITOR`
  gets `403`; granting and changing a version role of an executive is `400`; `403` → `404` → `400`
  order kept.

### PostgreSQL (`*IT`, by hand, **not run**)

- **`project.repository.ProjectRepositoryPostgreSqlIT`** (+1, 7 in the class):
  `summariesReportExecutiveAsMyRoleOnPostgreSql` persists an executive member and checks that
  `findSummariesForMember` returns `myRole = EXECUTIVE` (the `CASE` with the new `WHEN EXISTS`
  branch on the real database). Written, never run: no PostgreSQL was started.
- **`version.repository.VersionMemberRepositoryPostgreSqlIT`**: the V24 backfill and the
  project-matrix statement-count cases are the two documented in
  [*Structural role, editor simulate permission, role change and project matrix*](#structural-role-editor-simulate-permission-role-change-and-project-matrix--2026-11-15);
  they are also still not run, and are not repeated here.

Run both by hand: `ECONOMIC_TEST_DATABASE_URL=… mvn -B verify` against a disposable
database (see [*PostgreSQL classes*](#postgresql-classes-it-by-hand)).

## FLNG unit: ship capacity and gas consumption — 2026-11-15

Backend only. Counts are in the Summary (+5 executions, 1493 in total). Behaviour and
the data caveat: [`considerations.md`](considerations.md).

- **`node.service.NodeServiceTest`** (+2, Unit):
  - `saveFlngUnitKeepsShipCapacityAndGasConsumptionSeparate` — `saveFLNGUnit` stores
    `shipCapacity` (3) and `gasConsumption` (30.5) from their own DTO fields.
  - `editFlngUnitWritesGasConsumptionFromItsOwnFieldAndDoesNotOverwriteShipCapacity` —
    **regression:** `editFLNGUnit` writes `gasConsumption` from the DTO's own field, not
    from `shipCapacity`; three consecutive edits (both change, only gas changes, only ship
    changes) each leave the unchanged field alone.
- **`version.controller.FlngUnitShipAndGasHttpTest`** (new, 2, full context on H2):
  - `createStoresAndReturnsShipCapacityAndGasConsumptionSeparately` — `POST
    /version/{id}/node` with ship capacity 3 and gas consumption 30.5, then `GET
    /version/{id}/node/{nodeId}`: `attributes.shipCapacity` and `attributes.gasConsumption`
    come back as 3 and 30.5.
  - `editStoresBothValuesAndOneDoesNotOverwriteTheOther` — three `PATCH
    /version/{id}/node/{nodeId}` edits (8/44.25, then only gas → 8/11.5, then only ship →
    2/11.5), each followed by a `GET` that returns exactly those two values.

### Scenario comparison merged with `master` — 2026-11-19

`feat/comparacion-escenarios` merged with `master` (which added its own `V24__editor_simulate_permission.sql`):
the comparison migration is now **V25**, `MigrationCatalogTest` expects 25 files and `ScenarioComparisonMigrationTest`
runs V25. `ScenarioStatusServiceTest.listReportsEachScenarioStatusReadinessAndWhetherTheCallerCanSimulate` now takes
`canSimulate` from `VersionAccessService` (per-version rule) instead of the session. Frontend:
`permissionsNav.golden.json` re-recorded (only the Comparaciones entry changes: it links to the project's comparisons,
or is unavailable without a project) and `sidebarActive.test.mjs` counts three project analysis items.

Validation after the merge: `mvn clean test` — **1540 tests, 2 failures**, both pre-existing on `master` and
unrelated (`VersionGetJson*HttpTest` golden JSON: `dtmcost`/`dtmtime` property order differs on this Windows JDK; the
same two fail on a clean `master` checkout). Frontend `npm test` — **249 cases, 1 failure**, also pre-existing on
Windows (`permissionsCharacterization.test.mjs` compares `/`-separated allowed paths with `\`-separated walked
paths); `npm run build` and `npm run lint` pass.

## Scenario table, expand only (V26) — 2026-11-22 (E3)

`mvn test` runs on H2 with Hibernate-built schema and Flyway disabled, so V26 is verified only by the
PostgreSQL integration test below (`mvn -B verify` with `ECONOMIC_TEST_DATABASE_URL` pointing at a
disposable database whose name contains `enerscope_economic_test`). Validation on this delivery:
`mvn -B clean verify` against a throwaway PostgreSQL 16 — **1598 unit executions** and **58 integration
executions**, zero failures/errors/skips. `node.migration.MigrationCatalogTest.executableCatalogUsesOnlyVersionedMigrations` now expects **26** migrations.

### `scenario.repository.ScenarioMigrationIT` — PostgreSQL/Flyway (10; 6 for V26, 4 for V27 added in E4a)

Each case migrates a disposable schema to V25, inserts rows (one version with every kind of row, one with
only an evaluation, one inactive with a configuration, one with nothing, and a comparison with two items),
applies V26 and checks the result.

| Case | Verifies |
| --- | --- |
| `everyPreviousRowSurvivesAndPointsAtTheDefaultScenarioOfItsVersion` | V26 runs once; row counts of the six linked tables and of `version` are identical before and after; no `scenario_id` is null; every row points at the default scenario of its own version. |
| `everyVersionGetsExactlyOneEscenarioBase` | Four versions give four scenarios, each the single default named "Escenario base"; a version with no economics and an inactive one get one too (the flag follows the version). |
| `aScenarioNameIsUniqueInItsVersionIgnoringCaseAmongActiveScenariosOnly` | Same name in a different case is refused in the same version, allowed in another version and again after the first is deactivated; a blank name is refused. |
| `aVersionCannotHaveTwoActiveDefaultScenarios` | A second active default is refused; it is accepted once the first is deactivated. |
| `runningTheMigrationAgainChangesNothing` | Flyway applies 0 migrations the second time, and executing the V26 file by hand again adds no scenario, moves no link and changes no count. |
| `theUniqueConstraintsOnVersionIdStayUntilV27` | A second economic configuration for the same version is still refused (the `UNIQUE (version_id)` is dropped in V27, not here). |

### `scenario.service.ScenarioDefaultsTest` — Unit (4)

| Case | Verifies |
| --- | --- |
| `createsTheEscenarioBaseOfAVersionThatHasNone` | With no default scenario, one named "Escenario base", flagged default and bound to the version, is saved and returned. |
| `reusesTheExistingDefaultScenarioWithoutCreatingAnother` | An existing default is returned as is; nothing is saved. |
| `lookingForTheDefaultScenarioOfAVersionWithoutOneNeverCreatesIt` | (E4a) `existing` answers empty for a version with no default scenario and saves nothing (reads never write). |
| `lookingForTheDefaultScenarioReturnsTheExistingOne` | (E4a) `existing` returns the stored default scenario without saving. |

### `scenario.service.ScenarioLimitsTest` — Unit (2)

| Case | Verifies |
| --- | --- |
| `theDefaultLimitIsTwentyScenariosPerVersion` | The default is 20. |
| `aLimitBelowOneIsRejectedAtStartup` | A configured maximum below 1 fails fast. |

Existing cases extended (no change in counts): `EconomicServiceTest.editorCanSaveValidatedConfiguration` and
`newEvaluationStoresVersionTwoAndSimulatesOnce`, `EconomicJobServiceTest.submissionFreezesInputsAndQueuesWithoutPublishingEvaluation`
and `ScenarioComparisonServiceTest.saveNeedsEditPermissionNamesItAfterItsScenariosAndStoresTheSimulationsCompared`
now also check that the new row records the default scenario of its version (double write).

## Start-up date and maintenance duration survive an edit — 2026-11-22 (E3b)

Bug: editing a node from the map erased its `startupDate` in all 12 node types (`NodeService.edit*` copied
`dto.getStartupDate()` unconditionally and the editor never sent it), and `maintenanceDuration` could not be
set through the node DTO at all. Now an absent value keeps the stored one (so a start-up date cannot be cleared
with `null`), a present one updates it, and a negative duration is rejected.

### `node.service.NodeServiceOptionalBaseValuesTest` — Unit (4 cases × 12 node types = 48 executions)

Each case runs once per node type (Well, GatheringNetwork, TreatmentPlant, Pipeline, PipelineConnection,
CompressingPlant, GroundBasedLiquefactionPlant, FLNGUnit, SeaportTerminal, LNGCarrier, InternalConsumption,
IndustrialConsumption), creating the node through `NodeService.save*` and editing it through `edit*`.

| Case | Verifies |
| --- | --- |
| `creatingANodeStoresItsStartupDateAndMaintenanceDuration` | Creating a node with both values stores them (the constructors do not take the duration). |
| `editingWithoutStartupDateOrMaintenanceDurationKeepsTheStoredValues` | Editing with neither value keeps both, and the rest of the edit (name) is still applied. |
| `editingWithANewStartupDateOrMaintenanceDurationUpdatesThem` | Editing with new values updates both. |
| `aNegativeMaintenanceDurationIsRejectedOnCreateAndEdit` | A negative duration is refused on create and edit, and the node keeps its value. |

Checked by mutation: restoring the old unconditional copy makes the "keeps the stored values" case fail for all 12 types.

### `version.service.VersionServiceTest` — Unit (66, was 65 before this change)

| Case | Verifies |
| --- | --- |
| `nodeDetailReportsTheStartupDateAndMaintenanceDurationSoTheEditFormCanSendThemBack` | `getNodeDetail` returns the node's `startupDate` and `maintenanceDuration`, which the edit form sends back. |

### `frontend/tests/nodeOperationalEditor.test.mjs` — 3 new cases (frontend `npm test`: 252 cases, 0 failures)

| Case | Verifies |
| --- | --- |
| `a loaded node round-trips its start-up date and maintenance duration into the edit payload` | `baseValuesFromDetail` then `buildNodePayload` keep `startupDate`, `maintenanceDuration` and `wastePercentage`. |
| `a node without a start-up date sends none (never null), so the backend keeps whatever is stored` | The payload omits `startupDate` when there is none, and a missing duration becomes 0. |
| `the edit form shows start-up date, maintenance duration in hours and losses, and states that a date cannot be cleared` | The three new fields render with the loaded values and the "no borrar" hint. |

CI: `backend-it` (PostgreSQL integration tests) no longer has `continue-on-error`, so a failing `*IT` — including `ScenarioMigrationIT` — fails the job.

## Reads by scenario and V27 (E4a) — 2026-11-22

`V27__scenario_unique_per_scenario.sql` drops the `UNIQUE (version_id)` of `economic_configuration`,
`economic_draft` and `scenario_draft` and adds `UNIQUE (scenario_id)`; every read now goes by `scenario_id`
(see `considerations.md`). Validation: `mvn -B clean verify` against a throwaway PostgreSQL 16 on port 55432 —
Surefire **99 classes, 1652 executions**, failsafe **6 classes, 62 executions**, zero failures/errors/skips;
`MigrationCatalogTest` now expects **27** migrations. The table above sums to those 1652 executions and 1420 cases.

### `scenario.repository.ScenarioMigrationIT` — 4 cases added for V27 (PostgreSQL)

| Case | Verifies |
| --- | --- |
| `v27KeepsEveryRowOfAV26DatabaseAndLosesNoLink` | On a V26 database with rows, only V27 is applied; row counts are identical and no `scenario_id` is null. |
| `aSecondScenarioOfTheSameVersionCanHaveItsOwnConfigurationDraftAndScenarioDraft` | A non-default scenario of the same version can hold its own `economic_configuration`, `economic_draft` and `scenario_draft` (two rows per version). |
| `aScenarioStillHasAtMostOneConfigurationDraftAndScenarioDraft` | A second row for the same scenario is refused in all three tables. |
| `v27IsRepeatable` | Flyway applies V26 and V27, then 0; running the V27 file twice by hand changes no count; the three unique indexes exist and no `UNIQUE (version_id)` remains. |

The V26 cases now migrate only up to V26 (`flyway.target("26")`), so they keep describing V26.

### `scenario.service.ScenarioReferenceServiceTest` — Unit (4, was 2)

| Case | Verifies |
| --- | --- |
| `aClonedNodeIsRemappedInTheConfigurationOfEveryScenarioOfTheVersion` | Both scenarios' configurations get the new node id; unrelated nodes are untouched. |
| `aClonedNodeIsRemappedInTheScenarioDraftAndTheLegacyDraftOfEveryScenario` | Two `scenario_draft`s and one legacy `economic_draft` of the version all get the new node id (in `configuration`, `editor` and `draft` sub-trees); another node's reference in the same draft is left alone. |
| `draftsWithoutTheNodeOrWithBlankJsonAreLeftExactlyAsTheyAre` | A draft that references only another node, a blank draft (`""`), `{}` and a draft whose old id is under a key that is not `nodeId`/`deliveryNodeId` keep their exact text (not even re-serialized). |
| `remappingANodeOntoItselfTouchesNothing` | Old id equal to new id reads no configuration and no draft. |

### `economic.service.EconomicServiceTest` — Unit (34, was 33)

| Case | Verifies |
| --- | --- |
| `readingAVersionWithoutAScenarioFindsNothingAndNeverCreatesOne` | Configuration, draft and evaluation list of a version with no default scenario answer not-found / null / empty, never create a scenario and never touch the repositories. |

Existing tests adjusted (no change in their counts): the mocked service tests now stub reads by scenario
(`EconomicExample.scenarioIdOf(versionId)`, `EconomicExample.scenarioDefaults()`), and the tests that persist
rows straight through the `EntityManager` (`EconomicWriteAccessHttpTest`, `ScenarioVerticalAccessHttpTest`,
`ProjectExecutiveHttpTest`, `PostgreSqlEconomicIT`) attach the default scenario (`ScenarioFixtures`).

## Scenario API, roles and the end of node edits from a scenario (E4b) — 2026-11-22

Scenarios can be created, renamed, duplicated and deleted, and their configuration, simulations and results are
served per scenario (`backend/README.md#scenario-api`). Saving a scenario no longer saves node values. Validation:
`mvn -B clean verify` against a throwaway PostgreSQL 16 on port 55432 — Surefire **102 classes, 1756 executions**,
failsafe **7 classes, 82 executions**, zero failures/errors/skips. The table above sums to those 1756 executions
(1464 cases).

### `scenario.controller.ScenarioApiHttpTest` — HTTP, real security and persistence on H2 (16 cases, 78 executions)

| Case | Verifies |
| --- | --- |
| `everyRoleGetsTheExpectedAnswerForEveryAction` (×63) | The role matrix. Roles: Ejecutivo, Simulador, version Editor of Extraction, version Editor of Export, version administrator, project administrator, platform administrator. Actions: list, read, create, rename, duplicate, delete, simulate, save configuration, list evaluations. Ejecutivo reads only; Simulador reads and simulates (202); Editors do everything except delete (403); the three administrators do everything. |
| `anEditorSavesRulesOfItsOwnVerticalAndGets403ForAnotherVerticalsRules` | On the scenario endpoint an Export editor saves a change to the Export node's rules (200) and an Extraction editor gets `403`. |
| `duplicatingAConfiguredScenarioNeedsTheRightToEditEveryVerticalItsRulesTouch` | Duplicating a scenario whose rules touch two verticals is `403` for a one-vertical editor and `201` for a version administrator. |
| `aNonMemberGets403BeforeAnythingIsLookedUpEvenForAScenarioThatDoesNotExist` | A caller with no project permission gets `403` for an existing and for an unknown scenario id (403 before 404). |
| `anUnknownDeletedOrForeignScenarioIsTheSameGeneric404` | Unknown, deleted and other-project scenarios answer the same `404` "Scenario not found" on every route (read, rename, delete, duplicate, configuration get/put, simulate, evaluations). |
| `aMemberOfAnotherProjectCannotReachThisProjectsScenarios` | With the other project's id in the URL the caller is not a member there: `403`. |
| `anInactiveVersionHidesItsScenarios` | List, read and create answer `404` once the version is inactive. |
| `theVersionCapabilityIs403BeforeTheNameIsValidatedAndTheNameIs400BeforeAConflict` | Blank name: Ejecutivo and Simulador `403`, administrator `400`; a repeated name is `409`. |
| `repeatedNamesAndTheLimitAnswer409WithAClearMessage` | Repeated name (different case/accent) `409`; the 21st scenario `409` naming 20; duplicating at the limit `409`. |
| `createReturnsTheScenarioAndNeverCreatesAVersion` | `201` with name, version id, not default, not configured; the project still has the same versions; the list is oldest first. |
| `duplicatingGivesTheCopyTheSameConfigurationAndTheSourceStaysTheSame` | The copy's configuration equals the source's; the source is unchanged. |
| `theLastScenarioCannotBeDeletedAndTheDefaultIsPromotedWhenItIsDeleted` | Deleting the default promotes the remaining one (`defaultScenario: true`); deleting the last is `409`. |
| `theOldVersionEndpointsFollowTheDefaultScenarioAndTheNewOnesKeepToTheirOwn` | Saving a copy does not change what the old `/versions/{v}/economics/configuration` returns; after the default is deleted it returns the promoted scenario's. |
| `aSimulationBelongsToItsScenarioAndAPendingDraftBlocksIt` | A job is listed under its scenario only (another scenario's job id is `404`); a pending draft makes simulating that scenario `400`. |
| `anEvaluationOfAnotherScenarioIsNotFoundUnderThisOne` | Listing another scenario's evaluations is empty and reading one (or its rounds) is `404`. |
| `aScenarioSaveThatWouldChangeNodeValuesIsABadRequestWithAClearMessage` | A save carrying changed node values is `400` with "se editan en cada nodo de la versión". |

### `scenario.service.ScenarioLifecycleTest` — real services on H2 (18); `ScenarioLifecyclePostgreSqlIT` runs the same 18 on PostgreSQL

| Case | Verifies |
| --- | --- |
| `createsAnEmptyNonDefaultScenarioNamedByThePersonAndListsThemOldestFirst` | Trimmed name, not default, not configured; list order. |
| `theFirstScenarioOfAVersionThatHasNoDefaultBecomesIt` | In a version with no scenario the first created is the default. |
| `aNameIsUniqueInTheVersionIgnoringCaseAndTheDefaultNameIsTakenToo` | "AMPLIACIÓN", "escenario BASE" and padded names are refused. |
| `renamingChecksTheNameAgainstTheOthersButNotAgainstItself` | Renaming to another's name is refused; changing only the case of its own is allowed. |
| `aMissingBlankOrTooLongNameIsABadRequest` | Null body, null, blank and 121-character names are `IllegalArgumentException`; 120 is accepted. |
| `aVersionHoldsAtMostTwentyActiveScenariosAndADeletedOneFreesItsPlace` | The 21st (and a duplicate at the limit) is refused; deleting one frees a place. |
| `aDeletedScenarioFreesItsName` | A deleted scenario's name can be reused. |
| `duplicatingCopiesOnlyTheConfigurationAndLeavesTheSourceIntact` | Same configuration, no draft, no evaluations in the copy; source configuration, drafts and evaluations untouched. |
| `duplicatingAnEmptyScenarioGivesAnEmptyOne` | An empty scenario duplicates to an empty one; its name is checked. |
| `theLastScenarioOfAVersionCannotBeDeleted` | `ScenarioDeletionRefusedException` and the scenario stays active. |
| `aScenarioWithAQueuedOrRunningSimulationCannotBeDeleted` | Refused while a job is queued, then while running; allowed once completed. |
| `deletingTheDefaultPromotesTheOldestRemainingOneInTheSameTransaction` | Old mark removed, oldest remaining becomes the only default, old endpoints follow it. |
| `deletingANonDefaultScenarioLeavesTheDefaultAlone` | The default is unchanged. |
| `deletingKeepsItsEvaluationsJobsAndTheSavedComparisonsThatUsedIt` | After deleting, its evaluation, job and a saved comparison with both items are all still there and point at the deleted scenario. |
| `theOldEndpointsKeepActingOnTheDefaultScenarioWhenOthersExist` | Saving a copy's configuration leaves the default's (and the version-level read) unchanged. |
| `aDraftOfAnotherScenarioDoesNotBlockTheDefaultOneNorItsReadiness` | Readiness is the default scenario's; another scenario's draft shows only on that scenario. |
| `editingANodeRemapsTheConfigurationOfEveryScenarioOfTheVersion` | After editing a node from the map (clone-on-write) both scenarios reference the new node and none the old one. |
| `editingANodeLeavesEveryScenarioOutdatedByDesign` | A node edit changes the source token of every scenario of the version. |

### `scenario.service.ScenarioAccessTest` — Unit (7)

| Case | Verifies |
| --- | --- |
| `withoutTheProjectPermissionTheAnswerIs403AndNothingIsLookedUp` | The scenario repository and the version guard are never touched. |
| `anUnknownInactiveOrForeignScenarioIsTheSameGeneric404BeforeTheVersionGuard` | Same "Scenario not found" for unknown, inactive and other-project scenarios; no guard call. |
| `aScenarioOfAnInactiveVersionIs404` | An inactive version hides its scenarios. |
| `readingNeedsOnlyTheProjectPermission` | `VIEW_PROJECT` and no version guard. |
| `editingNeedsEditProjectThenTheRightToEditTheVersion` | `EDIT_PROJECT` first, then `assertCanEditVersion`, in that order. |
| `deletingNeedsToAdministerTheVersionAndSimulatingToSimulateIt` | `assertCanManageVersion` / `assertCanSimulateVersion`, never the edit guard. |
| `aRefusedVersionCapabilityIsA403AfterTheScenarioWasFound` | The version `403` comes after the lookup. |

### Cases added or changed in existing classes

| Class and case | Verifies |
| --- | --- |
| `comparison.service.ScenarioComparisonServiceTest.aSavedComparisonReadsEachItemThroughItsOwnScenarioEvenWhenItIsDeletedOrNoLongerTheDefault` | (14, was 13) A saved item is read through the scenario recorded in it; an item with no scenario still reads through the version. |
| `economic.service.ScenarioConfigurationTest.publishesOnlyTheEconomicsAndLeavesTheNodesOfTheVersionUntouched` | Replaces the old "publishes physics and economics together": a save changes the configuration and never clones or replaces a node. |
| `economic.service.ScenarioConfigurationTest.aSaveThatWouldChangeNodeValuesIsRefusedAndNothingIsWritten` | Changed node values are `IllegalArgumentException`; nothing is written. |
| `economic.service.ScenarioConfigurationTest.incompleteSavePreservesPublishedValuesAndBlocksJobUntilDiscarded` | Rewritten around an incomplete economic save (no configuration) instead of an invalid node value. |
| `economic.service.ScenarioConfigurationTest.aDraftThatCarriesNodeValuesIsMarkedSoTheScreenCanExplainThatTheyAreNoLongerApplied` | A stored draft whose node values differ from the nodes is flagged `draftOperationsIgnored`; discarding clears the flag. |
| `economic.controller.ScenarioVerticalAccessHttpTest` | (22 cases, 26 executions) The vertical tests that changed node values now change the **rules** of a vertical's node (the only thing a scenario saves): own vertical `200`, another vertical `403`, invalid rule of another vertical `403`, invalid rule of its own stored as a draft; the three "changes every vertical" cases and the two child-copy cases no longer rename nodes; `aScenarioSaveThatWouldChangeNodeValuesIsRefusedWithAClearMessageNotSilentlyDropped` is new. The case `anyEditorChangesAnInternalConsumptionNode` was removed (it renamed a node; the internal-consumption exemption stays covered by `ScenarioChangeScopeTest`). |
| `economic.service.EconomicJobServiceTest` | Stubs read the configuration by scenario. |

## Frontend scenario flow (E5) — 2026-11-22

Version → its scenarios → one scenario. Validation: `mvn -B clean verify` against a throwaway PostgreSQL 16 on port 55432
— Surefire **102 classes, 1758 executions**, failsafe **7 classes, 84 executions**, zero failures/errors/skips (the table
above sums to those 1758 executions); frontend `npm test` **265 cases, 0 failures** (252 before), `tsc -b`, `eslint .` and
`npm run build` clean. The browser (Playwright) scripts `frontend/tests/*-browser.mjs` mock the endpoints of the old
version-level flow and were **not run and not updated**; they must be rewritten before they are used again.

### Backend: status per scenario (the one backend change of E5)

`ScenarioDTO` gains `status` (`UP_TO_DATE`, `OUTDATED`, `NEVER_SIMULATED`, `SIMULATING`) and `latestEvaluatedAt`
(`ScenarioStatusService.standingOf`), because the only status the API returned was the comparison's, for the default
scenario only.

| Case | Verifies |
| --- | --- |
| `scenario.service.ScenarioLifecycleTest.eachScenarioReportsItsOwnStatusAndANodeEditOutdatesEveryOneThatWasSimulated` | A never-simulated scenario is `NEVER_SIMULATED` with no date; after simulating the default one it is `UP_TO_DATE` with a date while the copy stays `NEVER_SIMULATED`; after a node of the version changes the simulated one is `OUTDATED`. |
| `scenario.service.ScenarioLifecycleTest.aScenarioWithAQueuedOrRunningSimulationIsSimulating` | A queued job makes the scenario `SIMULATING`. |

### `frontend/tests/scenarioFlow.test.mjs` — 13 cases (pure logic: `utils/scenarioRules.ts`, `utils/scenarioRoutes.ts`, `utils/workspaceRoutes.ts`, `api/errors.ts`)

| Case | Verifies |
| --- | --- |
| `each role gets the scenario permissions the backend matrix gives it` | Ejecutivo, Simulador, Editor, version administrator, project administrator and platform administrator: view / edit / delete / simulate flags, and a reason exactly when an action is denied. |
| `permissions deny by default: no access data, or a version the person has no entry for` | `null`/`undefined` access and an unknown version allow nothing. |
| `a role that cannot edit never edits even with version flags: the project permission is required too` | Version flags without `EDIT_PROJECT` give nothing. |
| `the reason of a blocked action says why: read-only role, missing right, or the state of the scenario` | Read-only, missing right, not configured, draft pending, simulating, limit of 20 reached. |
| `a scenario name is required, trimmed, at most 120 characters and unique in the version ignoring case` | Blank, 120/121 characters, repeated names ignoring case and accents. |
| `renaming lets a scenario keep or re-case its own name but not take another one` | `selfId` handling. |
| `the status the API returns reads in plain words, and a changed base carries the notice` | Four labels and tones, "La base cambió: revisá este escenario.", the ignored-operations and delete notices. |
| `the default scenario is the one marked, else the first` | `defaultScenarioOf`. |
| `every scenario error the backend can answer has its own plain Spanish message` | Repeated name, limit of 20, last scenario, running simulation, stale token, node values in a scenario save, "Scenario not found", name required / too long. |
| `scenario routes are built under the version and keep each module to its own sidebar entry` | Path builders and that configuration and simulation patterns match their own routes only (never version management). |
| `switching projects from any scenario screen opens that module list of the new project, never a foreign id` | `projectDestination` for the new routes. |
| `the old version-level economics routes are read and sent to the default scenario` | `parseLegacyEconomicsPath` / `legacyEconomicsTarget` for configuration, history and results (with evaluation id) and the paths that must not match. |

`frontend/tests/scenarioConfiguration.test.mjs` gains `a recovered draft never resends stale node values: they are always the nodes' current ones`.

## Comparison by scenario and V28 (E6) — 2026-11-22

Items name a scenario; saved comparisons keep the version and scenario names and open after either is deleted
(`scenario-comparison.md`). Validation: `mvn -B clean verify` against a throwaway PostgreSQL 16 on port 55432 — Surefire
**103 classes, 1782 executions**, failsafe **7 classes, 87 executions**, zero failures/errors/skips (the table above sums to those
1782 executions); `MigrationCatalogTest` expects **28** migrations; frontend `npm test` **268 cases, 0 failures** (265 before),
`tsc -b`, `eslint .` and `npm run build` clean. The Playwright scripts are still not updated (see the E5 note).

### `comparison.controller.ScenarioComparisonHttpTest` — HTTP, real security and persistence on H2 (14 executions)

| Case | Verifies |
| --- | --- |
| `everyRolePreviewsButOnlyThoseWhoCanEditTheProjectSave` (×6) | Everyone with `VIEW_PROJECT` previews (200); saving: Ejecutivo `403`, **Simulador `201`**, Editor, version administrator, project administrator and platform administrator `201`. |
| `twoScenariosOfTheSameVersionAreComparedAndNamedByTheirScenario` | Scenarios of one version are compared; the result carries scenario names, scenario ids and the shared version. |
| `thePickerListsEveryScenarioGroupedByVersionWithItsOwnStatus` | `GET /comparisons/scenarios` lists each scenario (default first) with its own status and its version. |
| `anItemThatNamesOnlyAVersionMeansItsDefaultScenario` | The deprecated `{versionId}` item compares that version's default scenario. |
| `anUnknownDeletedOrForeignScenarioIsTheSameGeneric404` | Unknown, deleted and other-project scenarios answer one `404` "Scenario not found". |
| `withoutAccessToTheProjectTheWholeRequestIs403WithNoPartialResult` | A non-member gets `403` with no data, also when an item does not exist, and for the picker. |
| `tooFewTheSameTwiceAndNeverSimulatedAreBadRequests` | One item, the same scenario twice and a never-simulated scenario are `400`. |
| `aSavedComparisonKeepsOpeningWithItsDataAfterItsScenarioIsDeleted` | After deleting a scenario the saved comparison opens (`200`), the item is `deleted` with its stored name, the result keeps its data, the list says it includes deleted scenarios. |
| `aSavedComparisonKeepsOpeningAfterItsVersionIsDeactivated` | Same when the version is deactivated. |

### `comparison.service.ScenarioComparisonServiceTest` — Unit (22, was 14; rewritten around scenarios)

Scenarios of the same version and of different versions; same-named scenarios told apart by version; changed / never simulated;
unknown scenario `404` before any evaluation is read and a single unknown is `404` (not "too few"); other-project, deleted and
inactive-version scenarios are the same generic `404`; `403` for the whole request with nothing looked up; the deprecated version item
and its cross-project `404`; shape errors (null body, no items, item without id, nine items, the same scenario twice, thresholds, name
length); save stores the scenario and the names as they were; a saved comparison reads each item by its own evaluation and keeps the
stored names even if the scenario was renamed; a deleted scenario and a deleted version are flagged `deleted`; an old comparison without
scenario or names still opens and lists by version name; soft delete.

### Other backend changes

| Class and case | Verifies |
| --- | --- |
| `comparison.controller.ScenarioComparisonControllerTest.aRequestThatNamesOnlyAVersionStillBindsForCompatibility` | (6, was 5) `{versionId}` binds to `Item.ofVersion`; `{scenarioId}` to `Item(scenarioId)`. |
| `comparison.service.ScenarioComparisonCalculatorTest` | (16, was 15) The project-name test became two: same scenario names across versions read "versión · escenario"; distinct names read as the scenario; the `deleted` flag reaches the result and a scenario without a name falls back to its version. |
| `comparison.service.ScenarioStatusServiceTest.listReportsEachScenarioItsOwnStatusReadinessAndWhetherTheCallerCanSimulateGroupedByVersion` | The list has one entry per scenario, two scenarios of one version have their own statuses (up to date / simulating), grouped by version. |
| `scenario.repository.ScenarioMigrationIT` | (13, was 10) `v28GivesEveryExistingComparisonItemTheCurrentNamesWithoutLosingARow`, `aSavedItemKeepsItsNamesWhenTheScenarioIsRenamedAndTheVersionDeleted` and `v28IsRepeatableAndNeverOverwritesAStoredName`; the V27 cases now migrate up to V27 only. |

### Frontend (`frontend/tests/comparison.test.mjs`, 5 cases added or rewritten)

| Case | Verifies |
| --- | --- |
| `scenarios are named by their scenario name, and by version · scenario when the names repeat` | `scenarioNames` mirrors the backend. |
| `a deleted scenario of a saved comparison is marked and counted, and the label shows version and scenario` | "(eliminado)", `deletedCount`, `scenarioLabel` and the notice text. |
| `the selection round-trips through the URL and the request keeps its order` | `scenarioId` in the URL and in the request; other projects dropped. |
| `the picker groups scenarios by version in the server order and searches scenario and version names` | `groupByVersion`, `filterScenarios`. |
| `a selection that arrives in the URL keeps scenario ids, turns old version ids into the default scenario and drops the rest` | `resolveSelection` and `selectionNotice`. |

## Every version has its "Escenario base" from creation (E6b) — 2026-11-22

`VersionService.saveVersion` (used by `ProjectService.saveVersion` for root versions and branches, and by `saveOrphanVersion`) calls
`ScenarioDefaults.forVersion` in the same transaction. Validation: `mvn -B clean verify` against a throwaway PostgreSQL 16 on port
55432 — Surefire **104 classes, 1790 executions**, failsafe **8 classes, 93 executions**, zero failures/errors/skips (the table above
sums to those 1790 executions); frontend `npm test` **268 cases** (unchanged), `tsc -b`, `eslint .` and `npm run build` clean.

### `scenario.service.VersionDefaultScenarioTest` — real services on H2 (6); `VersionDefaultScenarioPostgreSqlIT` runs the same 6 on PostgreSQL

| Case | Verifies |
| --- | --- |
| `creatingARootVersionCreatesExactlyOneEmptyDefaultScenario` | A root version has exactly one active scenario, "Escenario base", default, with no configuration. |
| `branchingCreatesItsOwnEmptyBaseScenarioAndInheritsNothingFromTheParent` | A branch has exactly one empty "Escenario base" of its own; the parent keeps its two scenarios and its configuration; the child has none. |
| `aDetachedVersionAlsoGetsItsBaseScenario` | `saveOrphanVersion` also creates it. |
| `severalVersionsEachGetTheirOwnSingleScenario` | Two roots and a branch give three scenarios in total, one each. |
| `deletingAVersionRowAlsoRemovesItsScenariosAndLeavesTheOthersAlone` | Deleting a version (a physical delete today) removes its scenarios and no other version's. |
| `theComparisonPickerListsTheNewVersionsScenario` | The comparison picker lists the new root and its branch, each with "Escenario base", default, `NEVER_SIMULATED` and not ready. |

### Other changes

| Class and case | Verifies |
| --- | --- |
| `version.service.VersionServiceTest.creatingARootVersionAndABranchEachCreateTheirDefaultScenarioInTheSameCall` | (67, was 66) `saveVersion` calls `ScenarioDefaults.forVersion` once for a root and once for a branch, and nothing else. |
| `comparison.controller.ScenarioComparisonHttpTest.aVersionCreatedThroughTheApiAppearsInThePickerWithItsEmptyBaseScenario` | (15, was 14) A root and a branch created with `POST /projects/{id}/version` show up in `GET /comparisons/scenarios` with their base scenario. |
| `version.service.VersionConflictIntegrationTest` | (11, unchanged) four cases that delete versions failed once every version had a scenario (the schema H2 builds had no cascade); `Scenario.version` is now mapped `@OnDelete(CASCADE)`, as V26's foreign key is on PostgreSQL. |

## Deleting a version that has results (E6c) — 2026-11-22

`DELETE /version/{id}` now answers **409** "La versión tiene simulaciones o comparaciones guardadas y no se puede eliminar." when the version, or any
version in the subtree that would be deleted, has simulation jobs, saved evaluations or saved comparison items; nothing is touched. Order: 403, 404, 409.
A version with none of that is deleted as before, with its scenarios. Validation: `mvn -B clean verify` against a throwaway PostgreSQL 16 on port
55432 — Surefire **105 classes, 1797 executions**, failsafe **9 classes, 99 executions**, zero failures/errors/skips (the table above sums to those 1797);
frontend `npm test` **268 cases** (one translation case extended), `tsc -b`, `eslint .` and `npm run build` clean.

### `version.service.VersionDeletionTest` — real services on H2 (9); `VersionDeletionPostgreSqlIT` runs the same 9 on PostgreSQL (real foreign keys)

| Case | Verifies |
| --- | --- |
| `aVersionWithASavedEvaluationIsRefusedAndNothingIsDeleted` | 409 (`VersionHasResultsException`, exact message); the version, its scenario and the evaluation are still there. |
| `aVersionWithASimulationJobIsRefusedAndNothingIsDeleted` | Same for a job. |
| `aVersionInASavedComparisonIsRefusedAndTheComparisonIsKept` | A version that only appears in a saved comparison item is refused; the comparison and the other version are kept. |
| `anEmptyVersionIsDeletedWithAllItsScenariosAndTheRestIsUntouched` | An empty version with two scenarios is deleted with both; another version with an evaluation is untouched. |
| `aSubtreeWithAChildThatHasSimulationsIsRefusedForEverything` | An empty parent with one simulated child and one empty child: deleting the parent is refused and removes nothing; the empty child alone can still be deleted. |
| `permissionIsCheckedBeforeTheConflictAndAnUnknownVersionStaysNotFound` | A caller who cannot manage the version gets 403 (not a hint that results exist); an unknown id stays 404; the administrator gets the 409. |
| `aVersionWithMembersAndRolesIsDeletedTogetherWithThem` | A version with one member holding one role is deleted; no `VersionMember` row is left (the `version_member` FK has no cascade; this was a 500 on PostgreSQL). |
| `deletingASubtreeWithEverythingItCanOwnLeavesNoRowsBehindAndKeepsTheRest` | A parent and a child, each with 3 scenarios, 3 stored configurations, 3 scenario drafts, 3 legacy economic drafts, 2 members with 2 roles each, 2 wells and 1 connection (plus a third, unrelated version filled the same way). The fixture asserts every table is populated (6 scenario drafts and 6 economic drafts in the subtree); after `deleteVersion(parent)` every table is at 0 for the subtree (`scenario`, `economic_configuration`, `economic_draft`, `scenario_draft`, `version_member`, `version_member_role`, `versionxnode`, `versionxconnection`, `version`), there are no orphan roles, role permissions or scenario drafts, and the unrelated version keeps all its rows. |
| `aSubtreeWithEverythingButAChildWithAnEvaluationIsRefusedAndNothingIsDeleted` | Same fixture, but the child has a saved evaluation: `409`, and the row counts of every table above are identical before and after (no draft, member or scenario touched). |

`version.service.VersionServiceTest` (+2, 67 → 69): `deleteVersionRemovesMembershipsAndDraftsOfTheWholeSubtreeBeforeTheVersions` (members, scenario drafts, economic drafts and configurations of the whole subtree are deleted, in that order, before `deleteAllById`) and `deleteVersionKeepsMembershipsAndDraftsWhenTheSubtreeHasSavedResults` (409: none of those deletes runs).

Also: `scenario.controller.ScenarioApiHttpTest.deletingAVersionThatHasSavedResultsIs409WithAClearMessageAndNothingIsDeleted` (79 executions, was 78) checks the
`409` envelope over HTTP (`success: false` and the message), that the scenarios are still listed, and that a Simulador gets `403`.

### `version.service.VersionNodeCsvImportTemplatesTest` — real services on H2 (2); `VersionNodeCsvImportTemplatesPostgreSqlIT` runs the same 2 on PostgreSQL

The two example files of the frontend's «Importar CSV» screen, copied byte for byte (BOM included) from what
`frontend/src/utils/versionCsvImport.ts` generates into `backend/src/test/resources/csv-templates/` (regenerate them if the
frontend templates change), go through the real `VersionNodeCsvImportService` into an empty version made with `ProjectService.saveVersion`.

| Case | Verifies |
| --- | --- |
| `theNodesTemplateImportsOneWellAndNoConnectionsNorEconomics` | `plantilla-nodos.csv` imports without error: 1 node («Pozo Norte»), no connections, no configuration in the «Escenario base», still one scenario. |
| `theEconomicsTemplateImportsTwoNodesOneConnectionAndTheEconomicsOfTheBaseScenario` | `plantilla-nodos-con-economia.csv` imports without error: count 2, nodes «Pozo Norte» and «Gasoducto 1», one connection between them, one scenario («Escenario base») whose configuration (2030, 5 years, USD, boundary `empresa-1`) holds a profile for each generated node id. |

### `version.service.VersionNodeCsvImportAllKindsTest` — real services on H2 (2); `VersionNodeCsvImportAllKindsPostgreSqlIT` runs the same 2 on PostgreSQL

One CSV with a node of each of the 12 kinds goes through the real `VersionNodeCsvImportService` **and the real
`VersionService` save** (nothing mocked), so the importer's `type` pre-check and `VersionService.alignDeclaredVertical`
(vertical and role of `NodeTypeEnum`) run together. Each row's `type` is built from the `NodeTypeEnum` constant itself
(`vertical()`, `role()`, `name()`), not from the importer. Added after `INDUSTRIAL_CONSUMPTION` could not be imported
(pre-check `EXPORT`, save `INTERNAL_CONSUMPTION`); with that mismatch put back by hand, the import case fails on record 13
(checked on 2026-11-22).

| Case | Verifies |
| --- | --- |
| `theKindListCoversEveryNodeType` | The test's kind list has one entry per `NodeTypeEnum` constant, so a new type cannot be left out. |
| `importsOneNodeOfEachOfTheTwelveKindsWithTheTypeOfNodeTypeEnum` | The import returns 12 and the version holds 12 nodes, each with the node type, vertical and role of its `NodeTypeEnum`. |

### `version.service.VersionNodeCsvImportIdentityTest` — real services on H2 (7); `VersionNodeCsvImportIdentityPostgreSqlIT` runs the same 7 on PostgreSQL

A version with three wells and an economic configuration (the "Escenario base") that references them, driven through the real
`VersionNodeCsvImportService`, `VersionService` and `EconomicService` (class-level `@Transactional`; state is re-read after `flush` + `clear`).
The two rejection cases fail if the identity guard is removed, and the draft cases fail if the draft rewrite in `ScenarioReferenceService` is removed
(both checked by hand on 2026-11-22).

| Case | Verifies |
| --- | --- |
| `anIdentityAlreadyInTheVersionIsRejectedAndTheVersionAndItsReferencesStayIntact` | A valid first row plus a second row with the identity of an existing well → `Invalid CSV record 3 (NODE): identity <uuid> already exists in this version`; the version has the same node ids and identities, the scenario configuration is byte for byte the same and still references the original well. |
| `anIdentityRepeatedInTheFileIsRejectedAndNothingIsWritten` | The same new identity twice → `Invalid CSV record 3 (NODE): identity <uuid> is repeated in the file`; nothing written. |
| `aNewIdentityIsImportedAndTheExistingNodesKeepTheirIds` | Control: a fresh identity imports one node and every earlier node keeps its id. |
| `cloningAnInheritedNodeRemapsTheScenarioConfigurations` | Editing an inherited node clones it (new id) and the scenario configuration now references the clone, not the original. |
| `cloningAnInheritedNodeRemapsTheScenarioDraftReferences` | A `scenario_draft` that references the node follows the clone (this was the pending finding; `remap` now rewrites it). |
| `cloningAnInheritedNodeRemapsTheLegacyEconomicDraftReferences` | Same for the legacy `economic_draft`. |
| `cloningAnInheritedNodeRemapsTheDraftsOfEveryScenarioAndLeavesUnrelatedDraftsAlone` | Three scenarios in one version: the `scenario_draft` of the base, the `scenario_draft` and the `economic_draft` of the second follow the clone; the third scenario's draft (another node) and its `{}` legacy draft keep their exact text. |

## Local demo seed (`node --test`)

Added 2026-11-22 with `docs/examples/seed-local-demo.mjs` (see [demo-data.md](demo-data.md)). **20 tests in 2 files.**

`node --test docs/examples/seed-local-demo.test.mjs` — Unit, no server, no network (fetch is replaced by one that fails
the test where it matters). **18 tests**; run 2026-11-22: 18 pass, 0 fail.

| Test | What it verifies |
| --- | --- |
| `guardAcceptsLoopbackHttpWithExplicitConfirmation` | `localhost`, `127.0.0.1` and `[::1]` over `http://` pass when `ENERSCOPE_SEED_LOCAL=yes`; the trailing slash is dropped. |
| `guardRejectsMissingOrWrongLocalConfirmation` | Missing, empty, `true` or `YES` instead of exactly `yes` stops the script. |
| `guardRejectsRemoteHosts` | Public names, private IPs and `localhost.enerscope.org` are refused as non-local. |
| `guardRejectsHttpsInvalidUrlsAndEmbeddedCredentials` | `https://`, a malformed URL and `user:pw@` in the URL are refused. |
| `mainStopsAtTheGuardBeforeAnyNetworkCall` | The seed and `--cleanup --yes` fail at the guard (flag, env var or `ENERSCOPE_API`) without ever calling `fetch`. |
| `passwordsAreRequiredWithoutDefaults` | Both passwords are mandatory for the seed, the demo one needs 8+ characters, cleanup needs only the admin one; the admin mail defaults to `admin@enerscope.org`. |
| `missingPasswordErrorNeverEchoesValues` | The error message never contains the password values. |
| `parseArgsAcceptsTheDocumentedModes` | `--simulate`, `--cleanup --dry-run`, `--cleanup --yes` and `--api` parse as documented. |
| `cleanupRequiresDryRunOrExplicitYes` | `--cleanup` alone, both `--dry-run` and `--yes`, either without `--cleanup`, `--simulate` with `--cleanup`, unknown flags and `--api` without a value are refused. |
| `cleanupSelectsOnlyDemoMarkedData` | Only `Demo - ` organizations, `Demo - ` projects **of a demo organization** and `@enerscope.test` mails (case-insensitive, not `…test.evil.com`) are selected. |
| `cleanupNeverSelectsTheBootstrapAdminEvenWithADemoMail` | The bootstrap admin is excluded even if its mail uses the demo domain. |
| `demoMarkersAreStrict` | The prefix is exact (`Demo - `, case-sensitive) and the domain is exactly `@enerscope.test`. |
| `everyDemoNameCarriesThePrefixAndEveryMailTheDemoDomain` | Organization, projects and versions carry the prefix; 9 distinct demo mails. |
| `usersCoverEveryRoleAndRespectTheRoleRules` | One OWNER, one platform ADMIN, the project roles ADMIN/EDITOR×4/EXECUTIVE; version roles only for project members, never for the EXECUTIVE, vertical only on EDITOR; the version admin is project EDITOR + branch ADMIN; the no-access user has no project. |
| `rootNetworkCoversAllTwelveNodeTypesWithCoherentConnections` | 13 uniquely named nodes of 12 types; every connection exists, no sink feeds anything, nothing feeds a generator, no node is left unconnected (Austral too). |
| `nodeVerticalAndRoleFollowTheBackendTypeTable` | Each node's vertical and role match `NodeTypeEnum.java`, read from the backend source, so a change there fails here. |
| `economicConfigurationPointsTheFixtureAtTheGivenWells` | The backend fixture's placeholder node ids are replaced, the equipment cost and the fixed-expense factor apply, and the fixture is not mutated. |
| `advancedEditorCarriesEveryCollectionAsAnArray` | The editor payload has the eight collections as arrays, as the backend requires for `advanced`. |

`docs/examples/seed-local-demo.integration.test.mjs` — Integration against a running backend; skipped unless
`ENERSCOPE_SEED_IT_API` is set. **2 tests.**

| Test | What it verifies |
| --- | --- |
| `seedRunsOnAnEmptyDatabaseAndASecondRunDuplicatesNothing` | On an empty database the seed (with `--simulate`) creates everything; the second run creates nothing and the API snapshot (users, organization members, projects, members, versions, nodes, connections, version roles, scenarios with configured/draft/evaluations, comparisons) is identical; the promised contents are there (9 users, 13 nodes / 12 connections, 3 scenarios, the branch-only version admin, the comparison, Austral with its admin only). |
| `seededRolesSeeOnlyWhatTheyShould` | Signed in as each user: the no-access user sees no project, the Ejecutivo and an Editor see only Neuquina, the Ejecutivo cannot read Austral's comparisons, and the branch's version admin cannot list version roles (403) but has the branch in `/access`. |

Run 2026-11-22 against the backend of this branch on port 18080 and a throwaway PostgreSQL 16 on 55432 (28 migrations,
starting with only the bootstrap admin): **2 pass, 0 fail** (first run `39 cambio(s)`, second `0 cambio(s)`, about
100 s in total). The first attempt failed: the second run re-applied the branch edit
because the node detail returns its values under `attributes`; fixed in the script, then green. Checked by hand in
the same database: a third CLI run left the exact row count of all 52 tables unchanged; the guard stops the CLI
without `ENERSCOPE_SEED_LOCAL`, with `https://enerscope.org` and with `ENERSCOPE_API=http://qa.enerscope.org`;
`--cleanup --dry-run` changed 0 rows; `--cleanup --yes` left a control organization, user and a `Demo - …` project of
that other organization active and untouched; a seed after the cleanup reactivated the demo users and organization and
the next run was `0 cambio(s)`.


## Inicio: Escenarios, accesos rápidos y layout — 2026-11-23

Frontend total after this change: **435 tests in 46 files** (416 before, plus the 19 below), 0 failures, 0 skips; `tsc -b`,
`eslint .` and `npm run build` clean. `uiLegibility.test.mjs` lost the `QuickActions.tsx` entry of its `ink-400` exceptions
(the chevron it justified is gone from the chips); no case was added or removed there.

### `frontend/tests/homeScenarios.test.mjs` — unit, **19 cases** (`utils/homeScenarios.ts`, `utils/scenarioRules.ts`, `utils/date.ts`, `utils/recentProjects.ts`)

| Case | Verification |
| --- | --- |
| each status chip keeps the app wording and carries a one-line meaning | Al día / Desactualizado / Simulando… / Sin simular, their tones, and a one-line hint for each status (`SCENARIO_STATUS_HINTS`) and for the draft (`DRAFT_HINT`). |
| a draft adds a "Borrador" chip after the status, never instead of it | With `draftPending` every status yields `[status, draft]`; the draft chip is `warn` with its hint; without the flag there is one chip. |
| when only one chip fits, Simulando > Borrador > Desactualizado/Sin simular > Al día | Which chip is `essential` (the one that stays when the row is narrow) for each status with and without a draft. |
| Simular for what needs a run and may be run; Ver (to the right screen) for the rest | Simular for outdated / never simulated with permission; Ver to results (up to date), to the configuration (draft or unconfigured) or to the history. |
| a person who cannot simulate gets no Simular, and nothing while it is running or when the version is not visible | A reader and the read-only Ejecutivo get Ver; a simulating scenario and a version without view access get no action. |
| the version is the oldest visible root; a branch only when no root is visible | `pickHomeVersion` over roots, branches, invisible versions and an empty list. |
| 5 rows, 4 on a short screen; the default scenario first, then the most recently modified | `scenarioRowLimit`, the order and the `hidden` count of `homeScenarioRows`; the input is not reordered. |
| an administrator gets Nuevo proyecto, Comparar escenarios, Usuarios and Organizaciones | `quickActionsFor` for an administrator: ids, labels, addresses, and a hint for every chip. |
| everyone else gets Nuevo proyecto, Comparar escenarios, Mis organizaciones and Mi perfil | The same for a non-administrator. |
| what the sidebar does not reach is left out, and there are never more than four chips | No active project (no Comparar), not an organization member, no admin sections, an empty tree; at most 4. |
| Comparar points at the project Inicio shows when there is one, else at the sidebar address | `compareProjectId` (URL-encoded) over the sidebar leaf's address. |
| relative dates read in plain Spanish and fall back to the short date after a month | `formatRelative`: recién, hace N min / h, ayer, hace N días, the short date past 30 days, future dates, null and invalid input. |
| the recent project is the most recently modified one, and none when recency is unreliable | `mostRecentProject`. |
| no scenario: say so and offer Crear escenario only to who may create | `scenarioFooter` for 0 scenarios, with and without permission. |
| one scenario: suggest a second one, with the button only for who may create | 1 scenario, with and without permission; it wins over a pending state or a saved comparison. |
| two or three rows with something to simulate: just the count, and a singular | `pending` footer, `pendingLabel` singular/plural, `pendingScenarios` (a running scenario is not pending). |
| everything up to date: the last saved comparison when there is one, else nothing | `comparison` footer, or none. |
| four or more rows fill the block and take no footer | `FILLED_ROWS`; no footer with 4+ rows even with pending scenarios and a saved comparison. |
| Comparar warns about the two-scenario minimum below it, and the secondary action has an outline variant | `compareHint` for 0, 1 and 2 scenarios; `buttonClasses('outline')` has an edge and no fill. |

### Browser: `frontend/tests/homeLayout-browser.mjs` (manual, not part of `npm test`)

Mocks the API (no backend, no database) and checks, in light **and** dark: at **1440×800** and **1280×720** with full data
(4 projects, 6 scenarios with drafts, long names, a saved comparison) the `<main>` has no vertical scroll, the Escenarios
block shows **5** rows (**4** at 1280×720), Proyectos recientes **3**, Accesos rápidos **4** chips, no chip title is cut off,
no page error; with **0, 1, 2, 4 and 5** scenarios (light and dark, both sizes) there is no scroll, the first row sits under
the block's header at a small, constant distance, and the footer of the case appears (*Todavía no hay escenarios* + Crear
escenario, *Agregá otro escenario…* + Nuevo escenario, *1 escenario por simular*, none with 4–5 rows) and Comparar carries
the *Se necesitan al menos 2 escenarios* title only below 2; at **1024×768, 768 and 390** there is no horizontal overflow. Run it against a local Vite server on any
port except 8080/5432/5173:

```
cd frontend && npx vite --port 5199 --strictPort &
APP_URL=http://127.0.0.1:5199 PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs node tests/homeLayout-browser.mjs
```

`PW_CHANNEL=chrome` uses an installed Chrome instead of Playwright's Chromium; `SHOT_DIR=/some/dir` also saves a screenshot per
theme and size. Run on 2026-11-23: PASS (it does not need a Vite on 5173 or the backend).
