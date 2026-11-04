# Route integration after the master merge

The merge 7d3c8fe combined the economic branch (first parent) with master (second parent). Both features used the project versions listing. 
The resulting application retains master's version-management URL and moves simulation entry to an independent URL. 
Existing individual configuration, history and result URLs remain valid.

## Frontend route inventory

| Route | Purpose and access |
| --- | --- |
| /login | Public login; authenticated users return home. |
| / and /app | Authenticated workspace. |
| /projects | Project listing; project team and version-management actions remain available. |
| /projects/:projectId/team | Project team, retaining existing permission checks. |
| /editor?versionId=:versionId | Physical editor; optional version must belong to the active project. |
| /versions | Master project selector for version management. |
| /projects/:projectId/versions | Master version tree, creation, merge, conflicts and deletion dialogs. |
| /projects/:projectId/scenario-configurations | Economic and operational configuration listing. |
| /projects/:projectId/simulations | Simulation listing, readiness, rounds and seed dialog. |
| /projects/:projectId/versions/:versionId/economics | Preserved direct entry to configuration. |
| /projects/:projectId/versions/:versionId/economics/configuration | Joint scenario editor with unsaved-change protection. |
| /projects/:projectId/versions/:versionId/economics/history | Evaluation history and running jobs. |
| /projects/:projectId/versions/:versionId/economics/results/:evaluationId? | Current or selected immutable evaluation, including probabilistic rounds. |
| /profile | Profile and theme preferences from master. |
| /settings | Redirect to /profile#configuracion. |
| /organizations/:organizationId | Organization team; existing ownership guard applies. |
| /admin/users and /admin/organizations | Platform administrator only. |
| Other routes | Existing home fallback. Unknown economic tabs redirect to history. |

The application uses a data router again because the economic editor calls useBlocker. ThemeProvider, AuthProvider and master's sidebar remain in place. Both the menu and mobile module titles use the same module patterns; economic pages no longer activate Versionado.

Switching projects preserves the module but removes the previous version/evaluation ID. From configuration it goes to the new project's configuration listing; from results/history to simulations; from the version tree to version management. Version management remounts its workspace on project change to discard dialogs and stale asynchronous state.

## Backend/API route audit

Existing API families remain unchanged: /auth, /projects, /organizations, /users; /version for diagram/node/connection editing, version merge/conflicts and version membership/roles; /projects/:p/versions for the project-scoped query; /projects/:p/versions/:v/scenario-configuration for joint saves; /projects/:p/versions/simulation-readiness; and /projects/:p/versions/:v/economics for configuration, drafts, evaluations, jobs, retries and rounds.

SecurityConfig keeps authenticated API access and service-level authorization. No permissions were broadened. Master's version-member endpoints remain available; master did not provide a separate frontend membership page, so this integration does not invent a page or a link to one. No backend route or schema change is required by this routing correction.

## Verification

- TypeScript/production build and ESLint pass.
- workspaceRoutes.test.mjs covers module isolation and cross-project destinations.
- startup-browser.mjs covers menu highlighting, project changes, unknown tabs, version tree, and denied admin/organization access.
- economic-browser.mjs covers joint saves, drafts, dirty navigation, simulation submission, preserved originals and simple copies.
- results-browser.mjs covers old result snapshots, charts, keyboard interaction and read-only access.
- routes-live-browser.mjs reads the principal routes and existing evaluation details against the running instance, opens master's create-version dialog without submitting and verifies anonymous login redirection. Credentials are supplied through TEST_EMAIL/TEST_PASSWORD, never saved in the script.
