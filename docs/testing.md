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
  - **Integration** — `@SpringBootTest` with the full context on the H2 `test`
    profile.

## Summary

| Test class | Type | Cases |
| --- | --- | --- |
| `ApplicationContextTest` | Integration | 1 |
| `auth.controller.AuthControllerTest` | Web | 11 |
| `common.CsvUtilTest` | Unit | 5 |
| `jwt.JwtServiceTest` | Unit | 4 |
| `logging.ConsoleAppLoggerTest` | Unit | 1 |
| `money.MoneyAmountTest` | Unit | 6 |
| `user.service.UserServiceTest` | Unit | 5 |
| `user.service.PasswordGeneratorTest` | Unit | 4 |
| `user.service.BulkRegistrationServiceTest` | Unit | 7 |
| `organization.service.OrganizationServiceTest` | Unit | 6 |
| `organization.controller.OrganizationControllerTest` | Web | 5 |
| `project.service.ProjectServiceTest` | Unit | 7 |
| `project.controller.ProjectControllerTest` | Web | 5 |
| `version.service.VersionServiceTest` | Unit | 5 |
| `version.controller.VersionControllerTest` | Web | 3 |
| **Total** | | **75** |

## `ApplicationContextTest` — Integration

Smoke test that the whole application wires together.

| Case | Verifies |
| --- | --- |
| `contextLoadsAndSeedsAdmin` | The full Spring context (security, filters, JWT, JPA, OpenAPI, seeder) starts, and `AdminSeeder` creates the default `admin@enerscope.org` on boot. |

## `auth.controller.AuthControllerTest` — Web

Exercises `AuthController` through the real `SecurityConfig`/`AuthFilter` chain
(`/auth/**` is public); `SessionService`, `UserService` and `UserRepository` are
mocked.

| Case | Verifies |
| --- | --- |
| `registerCreatesAccountAndReturnsSession` | `POST /auth/register` with a valid body → `201` and an envelope with `success=true`, message `Registered`, and access/refresh tokens + `expiresAt`. |
| `registerRejectsDuplicateEmailWith400` | When registration throws (duplicate email) → `400`, `success=false`, and the domain error message. |
| `registerRejectsInvalidBodyWithValidationError` | Invalid email, too-short name and too-short password → `400` `Validation error` with field details; `UserService.register` is never called. |
| `loginReturnsSessionForValidCredentials` | `POST /auth/login` with valid credentials → `200` `Authenticated` and tokens. |
| `loginRejectsBadCredentialsWith400` | Wrong credentials → `400` `Invalid email or password`. |
| `loginRejectsBlankFieldsWithValidationError` | Blank mail/password → `400` `Validation error`; `UserService.login` is never called. |
| `refreshIssuesNewSessionForValidToken` | Valid refresh token for an existing user → `200` `Session renewed` with a new refresh token. |
| `refreshRejectsInvalidTokenWith401` | Invalid/expired refresh token → `401` `Invalid or expired refresh token`. |
| `refreshRejectsWhenUserNoLongerExistsWith401` | Token valid but the user no longer exists → `401` `User not found`. |
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
| `accessTokenCarriesUserClaims` | An access token carries `sub`, `mail`, `firstName`, `lastName` and validates successfully. |
| `accessAndRefreshTokensAreNotInterchangeable` | The `typ` claim keeps access and refresh tokens from being accepted in the other's place. |
| `rejectsGarbageAndBlankTokens` | Non-JWT, empty and `null` tokens are rejected. |
| `rejectsTokenSignedWithAnotherKey` | A token signed with a different secret fails validation. |

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

## `user.service.UserServiceTest` — Unit

Registration, login and password logic.

| Case | Verifies |
| --- | --- |
| `registerHashesPasswordAndPersists` | Registration normalises the email, hashes the password, and persists the user. |
| `registerRejectsDuplicateMail` | A duplicate email throws and neither saves nor hashes. |
| `loginReturnsUserWhenPasswordMatches` | Login returns the user when the password matches. |
| `loginRejectsWrongPassword` | A wrong password throws `IllegalArgumentException`. |
| `loginRejectsUnknownMail` | An unknown email throws `IllegalArgumentException`. |

## `user.service.PasswordGeneratorTest` — Unit

Secure password generation.

| Case | Verifies |
| --- | --- |
| `generatesRequestedLength` | Passwords have the default and any requested length. |
| `meetsComplexityRequirements` | Every password contains a lower-case, upper-case, digit and symbol. |
| `generatesDistinctPasswords` | 1000 generated passwords are all distinct (randomness sanity check). |
| `rejectsTooShortLength` | Requesting a length below 8 throws `IllegalArgumentException`. |

## `user.service.BulkRegistrationServiceTest` — Unit

CSV-driven bulk registration.

| Case | Verifies |
| --- | --- |
| `registersValidRowsAndReturnsCredentialsCsv` | Valid rows are created and the returned `credentialsCsv` holds `mail,password` with lower-cased emails. |
| `collectsInvalidRowsAsFailuresWithoutAborting` | Missing/invalid email and too-short name rows are reported as failures (with line + reason) without stopping the batch. |
| `rejectsDuplicateEmailWithinFile` | A second occurrence of the same email in the file is rejected as a duplicate. |
| `propagatesRegistrationFailuresPerRow` | A per-row registration exception (e.g. already registered) becomes a failure entry, not a batch abort. |
| `acceptsHeaderAliasesAndAnyColumnOrder` | Header aliases (`Email`/`Nombre`/`Apellido`) and arbitrary column order are resolved. |
| `throwsWhenRequiredHeaderColumnMissing` | A header missing a required column throws, naming the missing column. |
| `throwsWhenFileIsEmpty` | Empty content throws `IllegalArgumentException`. |

## `organization.service.OrganizationServiceTest` — Unit

Organization creation and member addition (with role/permission derivation).

| Case | Verifies |
| --- | --- |
| `createOrganizationPersistsAndReturnsOrganization` | Creating an organization persists it and returns it with the given name. |
| `addMemberGrantsOwnerFullPermissions` | Adding a member with `memberType=OWNER` creates a role with both `MANAGE_ORGANIZATION` and `VIEW_ORGANIZATION`. |
| `addMemberGrantsMemberViewOnlyPermission` | Adding a member with `memberType=MEMBER` creates a role with only `VIEW_ORGANIZATION`. |
| `addMemberRejectsUnknownOrganization` | An unknown organization id throws `IllegalArgumentException` before the user is looked up or anything is saved. |
| `addMemberRejectsUnknownUser` | An unknown user id throws `IllegalArgumentException`; nothing is saved. |
| `addMemberRejectsDuplicateMembership` | Adding a user already in the organization throws `IllegalArgumentException`; nothing is saved. |

## `organization.controller.OrganizationControllerTest` — Web

Exercises `OrganizationController` through the real `SecurityConfig`/
`AuthFilter` chain (a valid Bearer token is required on every request, like
every non-`/auth` route); `OrganizationService` is mocked.

| Case | Verifies |
| --- | --- |
| `createOrganizationReturnsCreatedOrganization` | `POST /organizations` with a valid body → `201` and an envelope with `success=true`, message `Organization created`, and the created organization's name. |
| `createOrganizationRejectsBlankNameWithValidationError` | Blank `name` → `400` `Validation error`; `OrganizationService.createOrganization` is never called. |
| `addMemberReturnsCreatedMember` | `POST /organizations/{id}/members` with a valid body → `201` with the member's `memberType` and `userMail`. |
| `addMemberRejectsUnknownOrganizationWith400` | When the service throws for an unknown organization → `400` with the domain error message. |
| `addMemberRejectsInvalidBodyWithValidationError` | Missing `userId`/`memberType` → `400` `Validation error`; the service is never called. |

## `project.service.ProjectServiceTest` — Unit

Project creation and member addition (with role/permission derivation).

| Case | Verifies |
| --- | --- |
| `createProjectPersistsAndLinksToOrganization` | Creating a project persists it linked to the organization and appends it to `Organization.projects`. |
| `createProjectRejectsUnknownOrganization` | An unknown organization id throws `IllegalArgumentException`; the project is never saved. |
| `addMemberGrantsAdminFullPermissions` | Adding a member with `memberType=ADMIN` creates a role with `MANAGE_PROJECT`, `EDIT_PROJECT` and `VIEW_PROJECT`. |
| `addMemberGrantsEditorEditAndViewPermissions` | Adding a member with `memberType=EDITOR` creates a role with only `EDIT_PROJECT` and `VIEW_PROJECT`. |
| `addMemberRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException` before the user is looked up or anything is saved. |
| `addMemberRejectsUnknownUser` | An unknown user id throws `IllegalArgumentException`; nothing is saved. |
| `addMemberRejectsDuplicateMembership` | Adding a user already in the project throws `IllegalArgumentException`; nothing is saved. |

## `project.controller.ProjectControllerTest` — Web

Exercises `ProjectController` through the real `SecurityConfig`/`AuthFilter`
chain (a valid Bearer token is required on every request, like every
non-`/auth` route); `ProjectService` is mocked.

| Case | Verifies |
| --- | --- |
| `createProjectReturnsCreatedProject` | `POST /projects` with a valid body → `201` and an envelope with `success=true`, message `Project created`, and the created project's `name`/`description`. |
| `createProjectRejectsBlankFieldsWithValidationError` | Blank `name`/`description` → `400` `Validation error`; `ProjectService.createProject` is never called. |
| `addMemberReturnsCreatedMember` | `POST /projects/{id}/members` with a valid body → `201` with the member's `memberType` and `userMail`. |
| `addMemberRejectsUnknownProjectWith400` | When the service throws for an unknown project → `400` with the domain error message. |
| `addMemberRejectsInvalidBodyWithValidationError` | Missing `userId`/`memberType` → `400` `Validation error`; the service is never called. |

## `version.service.VersionServiceTest` — Unit

Version creation, including the parent-version-same-project validation.

| Case | Verifies |
| --- | --- |
| `createVersionPersistsAndLinksToProjectWithoutParent` | Creating a version without a `parentVersionId` persists it linked to the project and appends it to `Project.versions`. |
| `createVersionPersistsWithValidParentVersion` | Creating a version with a `parentVersionId` that belongs to the same project links the new version to that parent. |
| `createVersionRejectsUnknownProject` | An unknown project id throws `IllegalArgumentException`; nothing is saved. |
| `createVersionRejectsUnknownParentVersion` | An unknown `parentVersionId` throws `IllegalArgumentException`; nothing is saved. |
| `createVersionRejectsParentVersionFromDifferentProject` | A `parentVersionId` belonging to a different project throws `IllegalArgumentException`; nothing is saved. |

## `version.controller.VersionControllerTest` — Web

Exercises `VersionController` through the real `SecurityConfig`/`AuthFilter`
chain (a valid Bearer token is required on every request, like every
non-`/auth` route); `VersionService` is mocked.

| Case | Verifies |
| --- | --- |
| `createVersionReturnsCreatedVersion` | `POST /projects/{projectId}/versions` with a valid body → `201` and an envelope with `success=true`, message `Version created`, and the created version's `name`. |
| `createVersionRejectsBlankNameWithValidationError` | Blank `name` → `400` `Validation error`; `VersionService.createVersion` is never called. |
| `createVersionRejectsUnknownProjectWith400` | When the service throws for an unknown project → `400` with the domain error message. |
