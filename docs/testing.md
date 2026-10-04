# Test catalog

A registry of the automated tests: what each test class covers and what every
case verifies. **Keep this in sync with the code** — whenever you add, remove or
change a test, update the matching entry here (see `AGENTS.md` → Definition of
done).

Run everything with `cd backend && mvn test`.

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

| Test class | Type | Cases |
| --- | --- | --- |
| `ApplicationContextTest` | Integration | 1 |
| `auth.controller.AuthControllerTest` | Web | 16 |
| `common.CsvUtilTest` | Unit | 5 |
| `jwt.JwtServiceTest` | Unit | 5 |
| `util.AuthUtilTest` | Unit | 7 |
| `logging.ConsoleAppLoggerTest` | Unit | 1 |
| `money.MoneyAmountTest` | Unit | 7 |
| `user.service.UserServiceTest` | Unit | 22 |
| `user.service.PasswordGeneratorTest` | Unit | 4 |
| `user.controller.UserControllerTest` | Web | 9 |
| `organization.service.OrganizationServiceTest` | Unit | 23 |
| `organization.service.OrganizationBulkRegistrationServiceTest` | Unit | 12 |
| `organization.controller.OrganizationControllerTest` | Web | 18 |
| `project.service.ProjectAccessGuardTest` | Unit | 22 |
| `project.service.ProjectServiceTest` | Unit | 48 |
| `project.controller.ProjectControllerTest` | Web | 27 |
| `project.repository.ProjectRepositoryTest` | Data | 11 |
| `version.service.VersionServiceTest` | Unit | 22 [^p] |
| `version.controller.VersionControllerTest` | Web | 10 |
| `node.service.NodeServiceTest` | Unit | 1 |
| `node.controller.NodeControllerTest` | Unit | 1 |
| `strategyCost.CostTest` | Unit | 8 |
| `strategyCost.InvestmentCostTest` | Unit | 2 |
| `strategyCost.CostBasisCalculatorsTest` | Unit | 10 |
| **Total** | | **292 [^p]** |

[^p]: Two cases in `version.service.VersionServiceTest` are
`@ParameterizedTest`s running over the eight mutating version entry points,
so they count as 16 executions rather than 2. Surefire therefore reports
**306** for the 292 cases catalogued here.

> **The catalog matches the code.** `mvn test` reports **306** executions,
> which is what the table above adds up to. The `node.*` and `strategyCost.*`
> classes, never catalogued before, were added on 2026-10-01 along with the
> seventh `money.MoneyAmountTest` case the table had been missing. The two
> `version.*` entries, which used to describe cases that did not exist, were
> rewritten from the code on 2026-09-15.

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

## `user.service.PasswordGeneratorTest` — Unit

Secure password generation.

| Case | Verifies |
| --- | --- |
| `generatesRequestedLength` | Passwords have the default and any requested length. |
| `meetsComplexityRequirements` | Every password contains a lower-case, upper-case, digit and symbol. |
| `generatesDistinctPasswords` | 1000 generated passwords are all distinct (randomness sanity check). |
| `rejectsTooShortLength` | Requesting a length below 8 throws `IllegalArgumentException`. |

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

## `organization.service.OrganizationServiceTest` — Unit

Organization creation and member addition (with role/permission derivation).

| Case | Verifies |
| --- | --- |
| `createOrganizationPersistsAndReturnsOrganization` | Creating an organization persists it and returns it with the given name. |
| `listForCurrentUserReturnsAllForAdmin` | A platform ADMIN caller lists every organization (`findAll`). |
| `listForCurrentUserReturnsMembershipsForRegularUser` | A regular user lists only the organizations they are a member of. |
| `listForCurrentUserRejectsUnauthenticated` | No authenticated caller → `UnauthorizedException`. |
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

## `organization.controller.OrganizationControllerTest` — Web

Exercises `OrganizationController` through the real `SecurityConfig`/
`AuthFilter` chain (a valid Bearer token is required on every request, like
every non-`/auth` route); `OrganizationService` and
`OrganizationBulkRegistrationService` are mocked.

| Case | Verifies |
| --- | --- |
| `listOrganizationsReturnsList` | `GET /organizations` → `200` with the list of organizations (`data[0].name`). |
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
| `existsByIdAndActiveTrueIsTrueForAnActiveProject` | The baseline for the `listMembers` guard. |
| `existsByIdAndActiveTrueIsFalseOnceTheProjectIsDeactivated` | Listing the members of a deleted project answers `400`. |
| `existsByIdAndActiveTrueIsFalseForAnUnknownId` | An id that was never stored is not active either. |

## `version.service.VersionServiceTest` — Unit

Version and node/connection mechanics, plus the authorization every entry point
runs. `ProjectAccessGuard` is mocked here (unlike in `ProjectServiceTest`): the
rules themselves are covered case by case in `ProjectAccessGuardTest`, so what
these cases pin down is that no entry point skips the guard and that a rejected
call touches neither the repositories nor `NodeService`.

| Case | Verifies |
| --- | --- |
| `modifyVersionShouldUpdateNameWithoutCreatingNodeChange` | Renaming a version updates the name and creates no `NodeChange`/`ConnectionChange`. |
| `editNodeInVersion_WhenNodeAddedInThisVersion_ShouldEditInPlaceAndCreateEditChange` | Editing a node that was added in this version edits it in place and records an `EDIT` change. |
| `editNodeInVersion_WhenNodePreviouslyEditedInThisVersion_ShouldEditInPlaceAndCreateAnotherEditChange` | Editing an already-edited node edits in place and records a further `EDIT` change. |
| `editNodeInVersion_WhenNodeCameFromParent_ShouldUpdateSnapshotAndCreateEditChange` | Editing a node inherited from the parent version replaces it in the snapshot and records an `EDIT` change. |
| `editNodeInVersion_WhenNodeDTOIsNull_ShouldThrowNullPointerException` | A null node DTO throws `NullPointerException`. |
| `editNodeInVersion_WhenNodeIdIsNull_ShouldThrowNullPointerException` | A null node id throws `NullPointerException`. |
| `editNodeInVersion_WhenVersionNotFound_ShouldThrowVersionNotFoundException` | An unknown version id throws `VersionNotFoundException`. |
| `editNodeInVersion_WhenNodeNotFound_ShouldThrowEntityNotFoundException` | A node absent from the version throws `EntityNotFoundException`. |
| `addNodeToVersion_WithWellDTO_ShouldAddWellToVersion` | Adding a `WellDTO` saves the well, appends it to the snapshot and records an `ADD` change. |
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

## `version.controller.VersionControllerTest` — Web

Exercises `VersionController` through the real `SecurityConfig`/`AuthFilter`
chain, so a request without a token is refused by the actual filter chain rather
than by a stub; `VersionService` is mocked. These endpoints take nothing but a
version UUID, which is what made guarding them necessary.

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

## `node.service.NodeServiceTest` — Unit

`NodeService` has eleven save methods; only `saveWell` is covered. The case
builds a complete `WellDTO`, saves it through a mocked `WellRepository` that
echoes its argument back, and reads it again through the same mock.

| Case | Verifies |
| --- | --- |
| `saveWellShouldReturnWell` | `saveWell` maps a complete `WellDTO` onto a persisted `Well`, preserving `name`, `maxCollectionCapacity` and `declineCurve`. |

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
