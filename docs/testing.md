# Test catalog

A registry of the automated tests: what each test class covers and what every
case verifies. **Keep this in sync with the code** — whenever you add, remove or
change a test, update the matching entry here (see `AGENTS.md` → Definition of
done).

Run everything with `cd backend && mvn test`.

Two automated suites exist:

- **Backend** — `cd backend && mvn test` (JUnit 5, H2). Catalogued class by
  class below.
- **Frontend** — `cd frontend && npm test` (the dependency-free `node:test`
  runner, `node --test tests/*.test.mjs`). It covers pure functions, API
  adapters and a few components rendered through a small TypeScript loader; it
  does **not** render the app in a browser. See
  [*Frontend unit tests*](#frontend-unit-tests-npm-test) for the per-file
  counts.

What neither suite covers — layout, focus, keyboard and the screens as a user
sees them — stands on the by-hand checklist at the end of the backend catalog,
[*Manual verification (frontend)*](#manual-verification-frontend), plus the
Playwright scripts `frontend/tests/*-browser.mjs` (not part of `npm test`; they
need a local Vite server and a Playwright install, see
[`frontend/README.md`](../frontend/README.md)). Keep those in sync when a screen
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
| `common.CsvUtilTest` | Unit | 5 | 5 |
| `economic.controller.EconomicControllerTest` | Web | 12 | 12 |
| `economic.controller.EconomicJobControllerTest` | Web | 4 | 4 |
| `economic.controller.EconomicWriteAccessHttpTest` | Integration | 9 | 9 |
| `economic.controller.ScenarioConfigurationControllerTest` | Web | 4 | 4 |
| `economic.controller.ScenarioVerticalAccessHttpTest` | Integration | 19 | 25 |
| `economic.service.EconomicEngineTest` | Unit | 21 | 21 |
| `economic.service.EconomicIndicatorsCalculatorTest` | Unit | 17 | 17 |
| `economic.service.EconomicJobServiceTest` | Unit | 10 | 10 |
| `economic.service.EconomicJobWorkerTest` | Unit | 4 | 4 |
| `economic.service.EconomicServiceTest` | Unit | 28 | 28 |
| `economic.service.EconomicSimulationAdapterTest` | Unit | 2 | 2 |
| `economic.service.EconomicStatisticsCalculatorTest` | Unit | 5 | 5 |
| `economic.service.ProbabilisticSimulationTest` | Unit | 8 | 8 |
| `economic.service.ScenarioConfigurationTest` | Integration | 14 | 14 |
| `economic.service.ScenarioOperationsTest` | Unit | 6 | 6 |
| `jwt.JwtServiceTest` | Unit | 5 | 5 |
| `logging.ConsoleAppLoggerTest` | Unit | 1 | 1 |
| `money.MoneyAmountTest` | Unit | 7 | 7 |
| `node.controller.NodeControllerTest` | Unit | 1 | 1 |
| `node.controller.NodeCreateAccessHttpTest` | Integration | 8 | 43 |
| `node.dto.NodeRequestDeserializerTest` | Unit | 3 | 3 |
| `node.migration.ExportVerticalMigrationTest` | Data (JDBC/H2) | 2 | 2 |
| `node.migration.MigrationCatalogTest` | Unit | 3 | 3 |
| `node.model.enums.NodeTypeEnumTest` | Unit | 4 | 26 |
| `node.repository.ConsumptionPersistenceTest` | Data | 1 | 1 |
| `node.service.NodeServiceTest` | Unit | 5 | 5 |
| `organization.controller.OrganizationControllerTest` | Web | 46 | 46 |
| `organization.repository.OrganizationMemberRepositoryTest` | Data | 17 | 17 |
| `organization.repository.OrganizationRepositoryTest` | Data | 24 | 24 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 18 | 18 |
| `organization.service.OrganizationServiceTest` | Unit | 88 | 88 |
| `project.controller.ProjectControllerTest` | Web | 45 | 45 |
| `project.repository.ProjectMemberRepositoryTest` | Data | 21 | 21 |
| `project.repository.ProjectRepositoryTest` | Data | 17 | 17 |
| `project.service.ProjectAccessGuardTest` | Unit | 26 | 26 |
| `project.service.ProjectServiceTest` | Unit | 104 | 104 |
| `scenario.service.ScenarioChangeScopeTest` | Unit | 16 | 16 |
| `session.service.SessionServiceTest` | Unit | 9 | 9 |
| `simulator.EconomicMetricsTest` | Unit | 7 | 7 |
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
| `version.controller.VersionAccessControllerTest` | Web | 3 | 3 |
| `version.controller.VersionBranchAccessHttpTest` | Integration | 25 | 29 |
| `version.controller.VersionConflictsListAccessHttpTest` | Integration | 8 | 8 |
| `version.controller.VersionControllerTest` | Web | 20 | 20 |
| `version.controller.VersionMergeAccessHttpTest` | Integration | 15 | 27 |
| `version.controller.VersionNodeVerticalAccessHttpTest` | Integration | 18 | 58 |
| `version.controller.VersionQueryControllerTest` | Web | 4 | 4 |
| `version.controller.VersionReadAccessHttpTest` | Integration | 11 | 11 |
| `version.controller.VersionRoleAfterProjectRemovalHttpTest` | Integration | 7 | 7 |
| `version.dto.VersionConflictDTOTest` | Unit | 1 | 1 |
| `version.repository.VersionConflictRepositoryTest` | Data | 6 | 6 |
| `version.service.VersionAccessGuardTest` | Unit | 49 | 49 |
| `version.service.VersionAccessServiceTest` | Unit | 13 | 13 |
| `version.service.VersionAccessTest` | Unit | 10 | 10 |
| `version.service.VersionConflictIntegrationTest` | Integration | 11 | 11 |
| `version.service.VersionConflictServiceTest` | Unit | 30 | 30 |
| `version.service.VersionLineageTest` | Unit | 10 | 10 |
| `version.service.VersionMemberServiceTest` | Unit | 19 | 19 |
| `version.service.VersionQueryServiceTest` | Unit | 10 | 10 |
| `version.service.VersionServiceTest` | Unit | 43 | 43 |
| **Total (73 classes)** | | **1153** | **1272** |

> **`mvn test` on 2026-11-15: 1272 executions, 0 failures, 0 errors, 0 skipped**,
> in 73 classes (`1153` methods). Read from the 73 Surefire reports that run
> produced (13:02) and from the sources; the table rows were re-checked against
> both. The five PostgreSQL classes — the `*IT` — are **not** part of that run and
> not in the table; they are run by hand against a disposable PostgreSQL (see
> below). `simulator.ResultTest` and `simulator.ResultMappingTest` no longer
> exist on this branch. The per-class sections below keep their historical case
> catalogs; where one disagrees with this table, this table wins. The cases
> added since the 2026-11-14 catalogs are in
> [*Version roles, cascade delete, root versions and CSV limit — 2026-11-15*](#version-roles-cascade-delete-root-versions-and-csv-limit--2026-11-15).

### PostgreSQL classes (`*IT`, by hand)

Not run by `mvn test`; they need a PostgreSQL the test connects to (see each
class). 47 executions in total, taken from the Surefire reports a manual run
left in `backend/target/surefire-reports` (12:52 on 2026-11-15; 0 failures,
0 errors, 0 skipped in each). They were **not** re-run when this catalog was
updated.

| Test class | Cases | Executions |
| --- | --- | --- |
| `economic.service.PostgreSqlEconomicIT` | 4 | 4 |
| `economic.service.ScenarioConfigurationPostgreSqlIT` | 1 own + 14 inherited from `ScenarioConfigurationTest` | 15 |
| `project.repository.ProjectRepositoryPostgreSqlIT` | 6 | 6 |
| `project.service.ProjectVersionCreationPostgreSqlIT` | 10 | 10 |
| `version.repository.VersionMemberRepositoryPostgreSqlIT` | 12 | 12 |
| **Total (5 classes)** | | **47** |

`ProjectRepositoryPostgreSqlIT` and `ProjectVersionCreationPostgreSqlIT` are new;
their cases are in the 2026-11-15 section.

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

Covers the in-repo CSV reader/writer used by bulk registration.

| Case | Verifies |
| --- | --- |
| `parsesSimpleRowsAndTrims` | Header + data rows parse into fields, each trimmed of surrounding spaces. |
| `handlesQuotedFieldsWithCommasAndEscapedQuotes` | Quoted fields keep embedded commas, and `""` decodes to a literal quote. |
| `skipsBlankLinesAndHandlesCrlfAndMissingTrailingNewline` | `\r\n` endings, fully blank lines skipped, and a final row without a trailing newline are handled. |
| `parseEmptyContentReturnsNoRows` | `null`/empty input yields no rows. |
| `writeQuotesOnlyWhenNecessaryAndRoundTrips` | The writer quotes only fields that need it (comma/quote), and output re-parses back to the original values. |

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
| `getVersionReturnsTheVersion` | `GET /version/{id}` → `200` with the envelope and the version's `name`. |
| `getVersionPropagatesForbiddenWith403` | When the service refuses the caller → `403` carrying the domain message. |
| `getVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `deleteVersionPropagatesForbiddenWith403` | `DELETE /version/{id}` for a caller without `EDIT_PROJECT` → `403`. |
| `deleteVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `addNodeToVersionPropagatesForbiddenWith403` | `POST /version/{id}/node` for a caller without `EDIT_PROJECT` → `403`. |
| `addNodeToVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `createDetachedVersionReturnsTheVersionForAPlatformAdmin` | `POST /version/createtest` → `200` with the created version. |
| `createDetachedVersionPropagatesForbiddenWith403` | The same call for a non-admin → `403` with `Only platform admins can create detached versions`. |
| `createDetachedVersionRequiresAuthenticationWith401` | The same call without a token → `401`; the service is never reached. |
| `mergeSubVersionIntoParentPropagatesNoParentWith400` | `PATCH /version/merge/{subId}` on a root version → `400` with the domain message. |

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

> **`simulator.ResultMappingTest` is not on this branch.** The catalog carried a
> seven-case section for it (it pinned the JPA mapping of the results to the
> columns `V8__create_results.sql` creates, and opted into `NON_KEYWORDS=YEAR`
> because `year` is reserved in H2 2.x — see `considerations.md`, 2026-10-05).
> The file does not exist under `backend/src/test/java/org/enerscope/simulator/`
> here; it was last present in commit `f66a681` and was lost in a merge. The
> rows were removed from this catalog so it matches the code, **but the mapping
> it protected is still only protected by Hibernate's `validate` on a real
> PostgreSQL**, which no test in this suite runs. Restoring the class (or
> replacing it with the Testcontainers test that 2026-10-05 called for) is an
> open card.

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
| M17 | The sidebar is fixed at wide widths and opens itself once below that | At ≥ `80rem` (and with the pin on, which is the default) the sidebar must be fixed and push the content on every page. Between `64rem` and `80rem`, or with the pin off, it must open on `/app` and push the content, and close on the first navigation | None | Pendiente de verificación |
| M18 | The pin and the left edge | The pin button at the foot of the sidebar (only shown at ≥ `80rem`) must toggle `sidebarPinned` and survive a reload. With the sidebar hidden, move the mouse to the left edge below the top bar and hold ~150 ms; it must slide over the content, and close ~300 ms after leaving it | None | Pendiente de verificación |
| M19 | `Ctrl`/`Cmd`+`B` toggles it, and is ignored where it should be | Press it on a page, then with the caret in a text field, then with a modal open. When the sidebar is fixed it must unpin it (and set `sidebarPinned` to false); otherwise it opens or closes the overlay | None | Pendiente de verificación |
| M20 | Below `64rem` it is a drawer | Narrow the window; it must overlay under a scrim, trap `Tab`, close on a row click and on `Escape`, and closing the breakpoint gap must not leave it open | None | Pendiente de verificación |
| M21 | Sections the caller cannot reach are hidden, not disabled | Compare the sidebar as a platform admin, an organization owner and a plain member: *Usuarios* and *Todas las organizaciones* only for the admin, *Mi organización* only for an owner, *Equipo del proyecto* only where the caller administers the active project | Real backend, three accounts | Pendiente de verificación |
| M22 | A module left with one child collapses into a plain row | As a plain member with one project, *Proyectos* must be a single row and not a disclosure | Real backend | Pendiente de verificación |
| M23 | Locked sections are legible | Check *Simulaciones*, *Comparaciones* and *Reportes*: padlock or "próximamente", full-contrast label, not dimmed | None | Pendiente de verificación |
| M24 | The tab title follows the route | Walk `/app`, `/projects`, a project's team, a project's versions, `/editor`, `/admin/users`, an organization, and the sign-in page; each must read `<section> · EnerScope`, with the project or organization name inserted where the route is scoped to one | Real backend | Pendiente de verificación |
| M25 | The favicon renders at small sizes in both OS themes | Check the tab in a light and a dark OS appearance — **and note that it follows the OS, not the in-app theme toggle** (known debt). It is also PROVISIONAL artwork | None | Pendiente de verificación |
| M26 | The two themes hold | Switch Configuración between Claro, Oscuro and Sistema and walk the screens above: no hard-coded white or black surface, every control's border visible, focus rings visible on every interactive element | None | Pendiente de verificación |
| M27 | Keyboard-only pass over a dialog | Open any modal (Nuevo proyecto, Cambiar rol, Importar CSV): focus must land on the first field, `Tab` must not escape the panel, `Escape` must close it, and focus must return to the button that opened it | None | Pendiente de verificación |
| M28 | Field errors are announced | Submit an invalid form and check with a screen reader that the error is tied to its input (`aria-describedby`) and the input is marked `aria-invalid` | None | Pendiente de verificación |
| M29 | The sign-in hero's subtitle contrast | Measure it in the **light** theme at 1440px and wider; it currently reads 4.21:1 against a 4.5:1 bar (known debt) | None | Pendiente de verificación |
| M30 | A dropped connection mid-import is survivable | Start an import of more than 50 rows (so it goes out in several batches of 50), kill the backend halfway, and check: the progress stopped, the warning about possibly partial writes appears, the credentials of the batches already confirmed are downloadable, and the member list reflects whatever was created. Also press *Cancelar* mid-import and check the same | Real backend | Pendiente de verificación |

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

`node.controller.NodeCreateAccessHttpTest` — Integration, 9 cases / 43
executions (12 of the 13 `POST /nodes/*` endpoints; `/nodes/pipeline-connection`
is not covered by any test, so its behaviour is **not verified**):

| Case | Verifies |
| --- | --- |
| `platformAdminCreatesTheNode` (×12) | Platform admin → `200` and one more row. |
| `platformAdminDeclaringAVerticalThatDoesNotMatchTheTypeGets400AndNothingIsCreated` | An `LNG_CAMER` declared as `EXTRACTION` → `400 Node vertical does not match its type: …`, no row. |
| `projectAdminGets403AndNothingIsCreated` (×12) | Project `ADMIN` → `403 Only platform admins can create nodes outside a version`, no row. |
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
`tests/permissionsCharacterization.test.mjs`). Run on 2026-11-15:
**157 tests in 24 files, 157 pass, 0 fail, 0 skipped** (`ℹ tests 157` from the
runner; per-file counts from running each file on its own).

| File | Tests | Covers |
| --- | --- | --- |
| `activeProjectResolution.test.mjs` | 8 | `resolveActiveProject` (route → stored → first, a foreign route id resolves to none), `projectRouteRedirect`, `ProjectRouteGuard` (page / loader / redirect), no redirect loops, `versionsIndexRedirect`, and when a lost-access response reloads the project list. |
| `advancedEconomicDraft.test.mjs` | 3 | Advanced economic editor draft. |
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
| `projectTeamNav.test.mjs` | 4 | The *Equipo del proyecto* entry: visible to every member with an active project, a group for an editor, absent without a project or while projects load. |
| `resultsCsv.test.mjs` | 4 | Results CSV export. |
| `scenarioConfiguration.test.mjs` | 8 | Scenario configuration adapter. |
| `sidebarActive.test.mjs` | 6 | At most one current sidebar leaf on every route, with and without a project; *Proyectos* current on `/projects`; the route → current item matrix; *Versionado* as a single entry; analysis items without a project; the short and full label of *Configuración*. |
| `summaryVisuals.test.mjs` | 9 | Summary charts. |
| `versionAccessApi.test.mjs` | 4 | `versionAccessApi`: `GET /projects/{id}/access`, list members, grant a role (with the vertical when there is one), revoke by member and role id. |
| `versionRoleErrors.test.mjs` | 7 | The Spanish translation of the version endpoints' `403`/`400`/`404` messages, including "ask a project administrator" for managing roles and the cascading-delete `403` that names no version. |
| `versionRoles.test.mjs` | 18 | `utils/versionRoles.ts`: role labels, grant ordering, "project administrator administers every version", explicit versus inherited access, `canManageRoles` (not `canManage`) deciding who manages roles, assignable people, role choices, one request per vertical, last-role detection, the version tree and its filters, default version, sorting. |
| `versionRolesPanel.test.mjs` | 2 | `VersionRolesPanel`: a version administrator who cannot manage roles sees only their own access and a notice; someone who can loads the members and gets the assignment form. |
| `visibleDiagram.test.mjs` | 2 | Removed nodes and touching edges disappear without changing saved history; empty and restored diagrams. |
| `workspaceRoutes.test.mjs` | 2 | Configuration and results routes never activate version management; switching projects keeps the module and drops foreign ids. |
| **Total (24 files)** | **157** | |

Per-file counts of the older files (economic, results, node editor) come from
running each file; their case-by-case catalogs are in the dated sections above.
`tests/loadModule.mjs` is a helper, not a test file.

**The navigation golden.** `permissionsNav.golden.json` records what
`useNavigationTree` returns for every combination; the test fails on any
difference. When the menu changes **on purpose**, regenerate it with
`RECORD_PERMISSIONS_GOLDEN=1 npm test`, read the diff of the JSON and commit it
with the change — never regenerate it to silence a failure you did not expect.

**Playwright scripts (not run for this catalog).** `tests/*-browser.mjs`
(`sidebarPin`, `startup`, `routes-live`, `projectScoping`, `economic`,
`results`, `probabilistic`, `consumption`, `formInputs`, `scenarioPresentation`,
`summaryVisuals`) drive a local Vite server with isolated HTTP fixtures; set
`APP_URL` and `PLAYWRIGHT_MODULE`. `sidebarPin-browser.mjs` is the one that
exercises the pin and the `80rem` threshold.

## Version roles, cascade delete, root versions and CSV limit — 2026-11-15

Backend changes on `update-frontend` since the 2026-11-14 catalogs (commits
`22c4504`, `158532e`, `e651e24`, `81f93ac`, and the `mine=true` listing and
per-vertical save checks that were never catalogued). Case counts are the
current ones from the sources and the 2026-11-15 `mvn test`; one-line
descriptions were written from the method names and a read of their
assertions, **not** by running each case on its own.

| Test class | Type | Cases now | Catalogued here |
| --- | --- | --- | --- |
| `version.service.VersionAccessGuardTest` | Unit | 49 | 26 |
| `version.service.VersionAccessServiceTest` | Unit | 13 | 4 |
| `version.service.VersionMemberServiceTest` | Unit | 19 | 19 (all) |
| `version.service.VersionServiceTest` | Unit | 43 | 7 |
| `version.controller.VersionControllerTest` | Web | 20 | 7 |
| `version.controller.VersionBranchAccessHttpTest` | Integration | 25 (29 executions) | 10 |
| `version.controller.VersionReadAccessHttpTest` | Integration | 11 | 2 |
| `project.service.ProjectAccessGuardTest` | Unit | 26 | 10 |
| `project.service.ProjectServiceTest` | Unit | 104 | 11 |
| `organization.service.OrganizationServiceTest` | Unit | 88 | 8 |
| `organization.controller.OrganizationControllerTest` | Web | 46 | 5 |
| `organization.repository.OrganizationRepositoryTest` | Data | 24 | 8 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 18 | 5 |
| `scenario.service.ScenarioChangeScopeTest` | Unit | 16 | 16 (all) |
| `economic.controller.ScenarioVerticalAccessHttpTest` | Integration | 19 (25 executions) | 18 |
| `project.service.ProjectVersionCreationPostgreSqlIT` | PostgreSQL (by hand) | 10 | 10 (all) |
| `project.repository.ProjectRepositoryPostgreSqlIT` | PostgreSQL (by hand) | 6 | 6 (all) |
| `version.repository.VersionMemberRepositoryPostgreSqlIT` | PostgreSQL (by hand) | 12 | 5 |

### 1. Who manages version roles

`version.service.VersionAccessGuardTest` — Unit. `assertCanManageVersionRoles`
has **no case of its own in this class**: it is exercised through
`VersionMemberServiceTest` (mocked guard) and the `*HttpTest` classes below
(real guard). Cases on the other guard methods:

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
not catalogued before (19 in the class, 25 executions):

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
catalogued before (12 in the class):

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
