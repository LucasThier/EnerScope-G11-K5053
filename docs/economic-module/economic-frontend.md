# Economic frontend

The Spanish scenario workspace is available at
`/projects/:projectId/versions`. A scenario is an existing physical Version;
this module does not create or edit physical nodes.

## Screens and permissions

- Scenario list: active versions, search, update date and link to economics.
- `/projects/:projectId/versions/:versionId/economics`: editable sections for
  general parameters, assets, income, operating costs and taxes.
- `.../economics/results`: newest saved evaluation.
- `.../economics/results/:evaluationId`: immutable historical evaluation.
- `.../economics/history`: saved evaluations, newest first, with explicit refresh.

Platform administrators can edit. Project members need EDIT_PROJECT for saving
and evaluating; VIEW_PROJECT permits reading. The backend remains authoritative.
Unknown project URLs do not fall back to a different project. Switching projects
from economics navigates to the destination's version list.

React Router uses its data router to block leaving a scenario with unsaved
changes. A beforeunload listener covers closing/reloading the page. Moving between
economic tabs retains the draft. While a save/evaluation is in flight, leaving is
blocked until completion. Unsaved edits are in memory. Guardar persists the draft in the database so it can be restored after reload or a later session.

## Simple editor contract

- Required explicit WACC and tax rate; the UI uses percentages and the API fractions.
  USD and ten operating years are visible initial defaults, editable before saving.
- One hypothetical tax entity owns 100% of participating nodes and is the entire
  consolidation boundary. No opening losses, carry-forward or tax payment lag.
- Fixed rules become scenario adjustments. Variable rules use OUTPUT_VOLUME, or
  EXPORTED_VOLUME for revenue, with a selected node and explicit SIMULATOR_UNIT
  conversion. One conversion is shared by every rule with that node/metric.
- Each rule creates one recognition/cash occurrence on December 31 of each
  selected operating year. No implicit backend recurrence is introduced.
- Income is taxable and operating expenses fully deductible. Assets have identical
  purchase/payment dates and generate capitalizable purchases plus a separate,
  deductible `<concept>.depreciation` treatment. Residual value is not a cash receipt.
- No legacy node costs, inflation, exchange, financing or terminal cash are inferred.
- Assets and rules are edited inline; removing a row only changes the draft.
  Empty asset/income/cost collections are allowed; indicator applicability belongs
  to the engine, not to a frontend assumption.

On reading a configuration, the adapter reconstructs the simple draft and compares
the complete regenerated aggregate with the original, independent of collection
order. Extra fields, contracts, ownership, advanced fiscal policies, delayed dates
or unsupported drivers make it read-only. It must never replace a configuration
whose meaning it cannot preserve. The original is available as a structured,
read-only view.

## Save, evaluation and history

All HTTP uses `src/api/economics.ts` and the existing authenticated Axios client.
Guardar PUTs the incomplete editor draft to /draft. Guardar y simular validates locally then PUTs the complete aggregate. Save-and-simulate awaits
the PUT before issuing exactly one POST. A synchronous in-flight guard and disabled
controls prevent duplicate submission. Network/server failures are not automatically
retried. The existing authentication refresh can replay a request rejected with 401.

A failed save preserves the draft. If evaluation cannot be confirmed after saving,
the UI explains that the configuration was saved and directs the user to refresh
history before resubmitting, because the server may have committed a snapshot.

Results display backend VAN, IRR and annual-close Paybacks without recalculating
indicators. Null/status outcomes remain explicit; zero is a valid calculated IRR.
The table exposes all period accounting fields, a chart shows annual cash flow,
and warnings cover pending balances and cumulative cash becoming negative again.
Historical reads use saved configuration and WACC only.

## Backend addition

`GET /api/v1/projects/{projectId}/versions` returns
`ApiResponse<VersionSummaryDTO[]>`: id, name, lastModified and node summaries
(id, name, type). It checks active project memberships/roles with VIEW_PROJECT,
allows platform administrators, excludes inactive versions and sorts newest first.
Anonymous/forbidden/missing project responses are 401/403/404. Existing authenticated
SecurityConfig coverage applies. No schema or economic engine changes are required.

## Verification

- `cd frontend && npm run build && npm run lint && npm test`
- `cd backend && mvn test`
- Optional browser fixture acceptance: run Vite on 127.0.0.1:5173, then
  `node tests/economic-browser.mjs` from frontend with Playwright and Chrome available.
  If Playwright is installed outside this project, set PLAYWRIGHT_MODULE to its
  absolute index.mjs path. The script intercepts API calls, never writes application
  data, and saves desktop/mobile screenshots in node_modules/.cache/economic-ui.
  This checks UI integration with fixtures, not a live PostgreSQL deployment.

## Operational results

Results now has Operativos and Económicos tabs sharing the same saved evaluation.
Economics remains the default view and retains cash flow, VAN, IRR and paybacks.
Operational data comes exclusively from snapshot.rawMetrics and physicalNodes;
current scenario names and configuration never replace historical metadata.
Select one node to see annual input, output, exports, losses, operating hours and
events. Volumes remain in raw simulator units, without commercial conversions.
Totals accumulate years within one node only; no chain-wide sum or physical net
flow is inferred. Missing values/years produce unavailable totals, not zeros.
Legacy snapshots without metrics display an empty state with economics available.
The tab controls support arrow keys, Home and End; tables and charts scroll on mobile.

Optional acceptance and screenshots: with Vite running, execute
`node tests/results-browser.mjs` (same PLAYWRIGHT_MODULE setup as above).
RESULTS_SCREENSHOTS optionally selects the output directory; APP_URL overrides
http://127.0.0.1:5173. Fixtures are illustrative and all backend writes are blocked.

Export-volume income only offers carriers of the selected scenario. An empty carrier list is explained inline. Incompatible saved nodes block saving/simulation until corrected; changing modality clears incompatible node/unit/factor. Backend save and evaluation reject export conversions on non-carriers; historical evaluations are still readable.

## Saving incomplete work

Guardar stores raw editor strings through GET/PUT economics/draft, even when economic validation has pending items. Drafts are shared within the scenario under the same project permissions as configuration. Draft storage validates format/size and never overwrites validated configurations or historical results. The editor restores a saved draft only when the current configuration is compatible with its simplified capabilities.

Guardar y simular validates every section, opens the first pending section and explains the errors instead of silently disabling the button. A successful configuration save clears the stored draft transactionally before the existing single evaluation request. Failed saves retain unsaved edits. Review-only users cannot save drafts or simulate. Existing authenticated SecurityConfig coverage applies to /draft; service permission checks remain authoritative.
