# Scenario comparison

The project workspace exposes **Comparar Escenarios** at
`/projects/:projectId/comparisons`. It compares immutable economic evaluations;
it never edits scenarios or submits simulations. Users map each decision
alternative to one existing evaluation per context. Different cells can refer
to different versions or different historical evaluations of the same version.
Context labels and probabilities describe user assumptions, not automatically
generated market forecasts or physical simulation rounds.

## Workflow and scoring

1. Name the comparison and describe its common scope and context assumptions.
2. Choose 2–20 alternatives and 1–10 contexts. Every context has a positive
   probability and probabilities total exactly 100 percent.
3. Assign a saved evaluation to every cell. Confirm that the scope is equivalent
   and that the selected evaluations represent the stated contexts. Software
   cannot infer semantic context equivalence from a user-entered label.
4. Choose indicators and fixed unfavorable/favorable reference values. Reference
   values are deliberately blank initially; no financial targets are invented.
   Weights are nonnegative and total exactly 100 percent. Weight-zero criteria
   can enforce a limit without contributing to the score.
5. Calculate, inspect source values and contributions, and optionally save an
   immutable comparison. Changing settings invalidates the displayed result.
   Recalculate to explore sensitivity without running the simulator. Saving an
   edited historical comparison creates a new record.

For indicator value x, unfavorable reference b and favorable reference g,
`score = clamp(100 * (x-b)/(g-b), 0, 100)`. Favorable is higher for VAN/NPV,
IRR and delivered volume; lower for investment, operating cost and simple
payback. A context score is the sum of `score * weight / 100`. The expected
score is the sum of `contextScore * probability / 100`. Decimal128 arithmetic
is used; display rounding does not determine ranking. Exact ties retain the
same competition rank. The worst context score is displayed alongside the
expected score. Reference values never depend on selected competitors.

Optional inclusive minimum/maximum bounds apply in **every context**. An
alternative violating any bound is excluded even if its average score is high.
No eligible alternatives produces `NO_ELIGIBLE_ALTERNATIVE`, with no winner.
Any missing required measurement or incompatible source produces `BLOCKED`
for the complete comparison: no ranking, no implicit zero, no silent weight
redistribution and no persistence of an incomplete recommendation.

## Sources and comparability

- Evaluate only existing authorized snapshots, never the live economic form.
- Currency, start year (valuation year), horizon, WACC and economic boundary IDs
  must match. When both snapshots record simulator metadata, algorithm and
  calculation version must match. These checks complement the explicit scope
  confirmation; the application does not convert currencies or extrapolate years.
- Probabilistic schema-3 evaluations are read from **all saved rounds**. Each
  indicator is extracted per round, then averaged before applying the fixed
  scoring function. Therefore the expected score is over context probabilities
  using each context's mean indicators, not expected utility over individual
  physical realizations. A criterion must be defined in every round; conditional
  IRR/payback means that omit unsuccessful rounds are never substituted.
- Limits also apply to the mean indicator in each context. They do not certify
  that every stochastic realization meets a bound; the UI states this explicitly.
  Chance constraints and worst-realization constraints are outside this version.
- Historical schema-1/2 snapshots use the existing read adapter and are treated
  as one saved realization. Missing values still block comparison.

| Indicator | Definition | Unit |
| --- | --- | --- |
| NPV | Saved net present value | Common currency |
| IRR | Saved annual IRR, only CALCULATED | Percentage points (15 means 15%) |
| Simple payback | Saved recovered period; all rounds must recover | Years |
| Investment | Capitalizable cash expense entries recognized within the horizon and boundary | Common currency |
| Operating cost | Non-capitalizable cash expense entries recognized within the horizon and boundary | Common currency |
| Delivered volume | Total observed output at explicitly selected independent delivery nodes, years 1..N | Explicit conversion unit |

Investment and operating cost follow the economic engine's consolidation rule:
entries outside the boundary and transfers between included entities are
excluded. Operating cost excludes non-cash depreciation and separately calculated
taxes; it is an undiscounted accrual measure, not a cash-payment total.

Delivered volume requires each selected node's saved `OUTPUT_VOLUME` conversion
with an observed driver (no static annual quantity), positive factor and common
output unit. Exactly one raw output per operating year is required. Selected
nodes must exist in the snapshot and no selected node may be reachable from
another selected node, preventing double counting across a serial chain.
Missing historical topology blocks this indicator. Users define the equivalent
delivery boundary for alternatives with different topology. This metric is
output at the chosen delivery points, not an inferred energy quantity.

Deferred production and capacity utilization remain visible as unavailable:
`AnnualNodeMetrics` does not save the necessary deferred/max-capacity series.
Enabling them requires versioned simulator instrumentation and historical
availability rules; this module does not estimate them from output or losses.

## API and storage

All routes use `/api/v1/projects/{projectId}/comparisons` and `ApiResponse<T>`.
SecurityConfig's authenticated fallback covers every route; project permissions
and version/evaluation ownership are checked in services.

| Method | Suffix | Permission | Behavior |
| --- | --- | --- | --- |
| POST | `/preview` | VIEW_PROJECT | Validate configuration, load sources, calculate without writes |
| POST | root | EDIT_PROJECT | Recalculate server-side and save an immutable configuration/result |
| GET | root | VIEW_PROJECT | List saved names, IDs and creation timestamps |
| GET | `/{id}` | VIEW_PROJECT | Read original stored configuration/result without recalculation |

`ComparisonDTO.Request` holds name, scope description, explicit confirmation,
contexts, alternatives/cells and criteria. Cells store version/evaluation IDs
and selected delivery-node IDs. Results store calculation version, common time
and currency basis, original measurements, units, scores, weighted contributions,
limit outcomes, source references, round counts, ranks and winner IDs.

Flyway V24 creates `scenario_comparison` with project FK, audit fields and
immutable configuration/result JSON. Existing migrations and evaluations are
unchanged. The result retains its source measurements even if a scenario later
changes. There is no update/delete comparison endpoint in this version.

Deploy the backend with normal Flyway startup before opening the new frontend
route. No migration repair, production reset or backfill is required.
