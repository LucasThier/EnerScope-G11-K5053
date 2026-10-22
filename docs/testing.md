# Test catalog

A registry of the automated tests: what each test class covers and what every
case verifies. **Keep this in sync with the code** — whenever you add, remove or
change a test, update the matching entry here (see `AGENTS.md` → Definition of
done).

Run everything with `cd backend && mvn test`.

The automated suite is **backend only**. The frontend has no test runner, so what
stands in for it is the by-hand checklist at the end of this file,
[*Manual verification (frontend)*](#manual-verification-frontend) — keep that in
sync too when a screen changes.

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

Counts below are the number of `@Test` methods in each class, taken from the
source on this branch. No class uses `@ParameterizedTest`, `@RepeatedTest` or
`@Disabled` any more, so **cases and executions are the same number**.

| Test class | Type | Cases |
| --- | --- | --- |
| `ApplicationContextTest` | Integration | 1 |
| `auth.controller.AuthControllerTest` | Web | 16 |
| `common.CsvUtilTest` | Unit | 5 |
| `jwt.JwtServiceTest` | Unit | 5 |
| `util.AuthUtilTest` | Unit | 7 |
| `logging.ConsoleAppLoggerTest` | Unit | 1 |
| `money.MoneyAmountTest` | Unit | 7 |
| `user.service.UserServiceTest` | Unit | 58 |
| `user.service.PasswordGeneratorTest` | Unit | 4 |
| `user.controller.UserControllerTest` | Web | 30 |
| `user.repository.UserRepositoryTest` | Data | 22 |
| `organization.service.OrganizationServiceTest` | Unit | 77 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 13 |
| `organization.controller.OrganizationControllerTest` | Web | 41 |
| `organization.repository.OrganizationRepositoryTest` | Data | 16 |
| `organization.repository.OrganizationMemberRepositoryTest` | Data | 17 |
| `project.service.ProjectAccessGuardTest` | Unit | 26 |
| `project.service.ProjectServiceTest` | Unit | 80 |
| `project.controller.ProjectControllerTest` | Web | 43 |
| `project.repository.ProjectRepositoryTest` | Data | 17 |
| `project.repository.ProjectMemberRepositoryTest` | Data | 21 |
| `version.service.VersionServiceTest` | Unit | 15 |
| `version.service.VersionConflictServiceTest` | Unit | 20 |
| `version.service.VersionConflictIntegrationTest` | Integration | 11 |
| `version.repository.VersionConflictRepositoryTest` | Data | 6 |
| `version.dto.VersionConflictDTOTest` | Unit | 1 |
| `version.controller.VersionControllerTest` | Web | 11 |
| `node.service.NodeServiceTest` | Unit | 2 |
| `node.controller.NodeControllerTest` | Unit | 1 |
| `simulator.ResultPerRoundTest` | Unit | 12 |
| `simulator.SimulatorTest` | Unit | 15 |
| `strategyCost.CostTest` | Unit | 8 |
| `strategyCost.InvestmentCostTest` | Unit | 2 |
| `strategyCost.CostBasisCalculatorsTest` | Unit | 10 |
| **Total** | | **621** |

> **Reconciled on 2026-10-18, by reading the sources rather than by running the
> suite.** The catalog had drifted badly after several merges: seven classes had
> no entry at all (`organization.repository.OrganizationMemberRepositoryTest`,
> `simulator.ResultPerRoundTest`, `simulator.SimulatorTest`,
> `version.dto.VersionConflictDTOTest`,
> `version.repository.VersionConflictRepositoryTest`,
> `version.service.VersionConflictIntegrationTest` and
> `version.service.VersionConflictServiceTest`), thirteen had grown or shrunk
> past their recorded count, and one (`simulator.ResultMappingTest`) was
> catalogued but is **not present on this branch** — see the note where its
> section used to be. Every row and every case below now matches a method that
> exists in `backend/src/test/java`.
>
> **Not verified:** `mvn test` was not run as part of this pass, so the 621
> above is a static count of `@Test` methods, not an observed execution count.
> If a run reports a different number, trust the run and reconcile again.

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

The frontend has **no automated tests and no mocking layer** — no runner in
`package.json`, no MSW, no fixtures, no mock mode in `src/api/client.ts`. Every
check below therefore runs **against a real backend** (`cd backend && docker
compose up --build`, then `cd frontend && npm run dev`); there is no "with
mocks" variant to choose, and anything a mock *could* have covered is simply not
covered.

So the honest answer to "what is tested against the real backend and what only
with mocks" is:

- **Backend behaviour** — every rule in this catalog above — is covered by
  `mvn test`, which runs against **mocks** (Unit/Web) or **H2** (Data/
  Integration), never against PostgreSQL. The only thing that checks the
  entities against the real schema is Hibernate's `validate` at startup, and no
  test performs it (see the `ResultMappingTest` note).
- **Frontend behaviour** is covered only by the checklist below, by hand,
  against a real backend.

**Status of this pass:** the checklist was written from the code on
2026-10-18 and **none of it was executed as part of writing it**. Every row is
therefore `pendiente de verificación`. Fill the status in as you run them, and
do not infer a pass from the fact that the feature was built.

| # | Check | How | Needs | Status |
| --- | --- | --- | --- | --- |
| M1 | A CSV with the canonical header imports, and only the valid rows | Importar CSV in *Mi organización* (owner) with a file mixing valid rows, a bad address, a duplicate and an unknown role; the preview must mark each bad row with its reason, the button must offer only the valid count | Real backend | Pendiente de verificación |
| M2 | An Excel "CSV UTF-8" file (BOM) imports | Save the template from Excel and upload it unchanged; the BOM must not reach the server and the columns must resolve | Real backend | Pendiente de verificación |
| M3 | A semicolon-delimited CSV imports | Same file saved with `;`; the preview must say `separador «;» convertido a coma` | Real backend | Pendiente de verificación |
| M4 | Over 100 rows and over 1 MB are refused in the browser | Upload a 101-row file, then a >1 MB one; both must block with their own message and leave the import button disabled, **without** a request being sent (check the network tab) | None | Pendiente de verificación |
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
| M17 | The sidebar opens itself once, then stays out of the way | Sign in on a desktop viewport; it must open on `/app` and push the content, and close on the first navigation | None | Pendiente de verificación |
| M18 | The left edge opens it | Move the mouse to the left edge below the top bar and hold ~150 ms; it must slide over the content, and close ~300 ms after leaving it | None | Pendiente de verificación |
| M19 | `Ctrl`/`Cmd`+`B` toggles it, and is ignored where it should be | Press it on a page, then with the caret in a text field, then with a modal open | None | Pendiente de verificación |
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
| M30 | A dropped connection mid-import is survivable | Start an import, kill the backend halfway, and check the warning about partially created accounts appears and the member list reflects whatever was created | Real backend | Pendiente de verificación |
