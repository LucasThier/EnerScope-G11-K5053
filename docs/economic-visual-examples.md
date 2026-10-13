# Economic result examples

Two independent scenarios were added to the local Demo · Cadena de GNL project on 2026-10-13. Existing scenarios and evaluations were preserved. Figures are calculated by the backend, not copied from the mockups.

| Scenario | Operating years | Initial investment | Annual income | Annual operating cost | WACC | Tax |
| --- | --- | --- | --- | --- | --- | --- |
| Ejemplo visual · 5 años (2031–2035) | 5 | USD 1,500,000 | USD 1,000,000 | USD 200,000 | 10% | 30% |
| Ejemplo visual · 10 años (2031–2040) | 10 | USD 1,500,000 | USD 1,000,000 | USD 200,000 | 10% | 30% |

Both use investment year 2030 and a 180-month depreciable asset with zero residual, entering service on 2031-01-01. Annual after-tax cash flow is USD 590,000. These are illustrative fixed revenues/costs, not calibrated commercial forecasts. Their physical diagrams are independent copies of the source scenario.

The 5-year example has 6 bars including investment, NPV USD 736,564.19 and IRR 27.79%. The 10-year example has 11 bars, NPV USD 2,125,294.59 and IRR 37.73%. Both recover in period 3 (simple) and period 4 (discounted). Displayed NPV rounds upward to a whole unit.

## Reproduce in a local test database

From the repository root, with the backend running and frontend dependencies installed, set DEMO_MAIL, DEMO_PASSWORD, DEMO_PROJECT_ID and DEMO_PARENT_VERSION in the environment, then run:

    node frontend/scripts/seed-economic-horizons.mjs

Optional: DEMO_API_URL (default http://localhost:8080/api/v1), DEMO_OUTPUT (JSON report path). Credentials are not stored in the script. The script uses authenticated APIs, reuses examples by their exact names and does not rerun an existing evaluation. It creates each example from the selected parent and saves/evaluates only that example. Run only against the intended test project.

## Visual review

Profitability groups NPV and IRR; recovery groups both periods/calendar years and a sparse timeline. Identical recovery periods share a marker. Unavailable metrics retain their explanations. Annual details are collapsible; chart tooltips, keyboard access and reduced-motion settings are retained. Narrow screens stack the cards and scroll the chart horizontally.
