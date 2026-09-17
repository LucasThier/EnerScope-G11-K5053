# Economic module and reproducible NPV example

The economic module evaluates a **Version of a Project**. It consumes actual annual
simulator quantities, generates auditable entries, computes income tax separately
for each tax entity, consolidates the selected boundary, reconciles accrual with
cash and discounts the resulting cash flows. It does not import legacy node costs
automatically or add a frontend screen.

## Integration and persistence

`EconomicConfiguration` is a typed immutable record aggregate containing tax
entities, boundary, node profiles/ownership, rules, contracts, assets, tax
treatments and conversions. Nested records are domain value objects, not shared
JPA entities. `EconomicConfigurationEntity` stores the complete validated aggregate
as JSON text with a unique Version foreign key and an optimistic-lock revision.
This portable representation works in PostgreSQL and H2; the Java validator owns
cross-reference validation. There is no unvalidated arbitrary JSON calculation path.

`EconomicEvaluation` stores an independent immutable JSON snapshot: schema version,
configuration, physical node/connection parameters, raw annual metrics, converted
metrics, generated entries, tax ledgers, period results, outstanding balances and NPV.
Each evaluation gets a new UUID. Editing the configuration never rewrites history.
Configurations are not automatically inherited by child versions. When explicitly
copying a configuration, replace its node IDs with the destination version's IDs.

The existing Project -> Version association remains unidirectional. The economic
service resolves the version through its owning project's collection, checks node
membership, and checks project permissions. No duplicate Scenario hierarchy is added.

## Economic conventions

- `startYear` is the investment year (`t=0`); `years` is the number of operating
  years, from 1 through 100. The first simulated year is `startYear + 1`.
- A single ISO currency is required. No exchange rate, inflation, financing,
  automatic terminal value or real-world tax regime is inferred.
- WACC is a nonnegative annual fraction, fixed throughout the evaluation. `0.10`
  means 10%. Ownership, deduction and tax rates are also fractions.
- Monetary calculations use `BigDecimal` and DECIMAL128 intermediates. Entries,
  taxes and published cash amounts use two decimals/HALF_UP. Ownership is applied
  once, with the last owner receiving the rounding remainder. NPV is rounded after
  summing unrounded discounted flows, so rounded displayed present values may differ
  from their total by one cent.
- Each rule occurrence specifies recognition and cash dates explicitly. There is
  no hidden recurrence or formula interpreter. Fixed rule amounts are per occurrence;
  quantity drivers use the full annual quantity of the recognition year. To split
  annual revenue into invoices, split the tariff into corresponding fractions.
- `taxEntityId=null` allocates a node rule/contract/asset to that node's owners.
  An explicit entity assigns the whole amount directly and does not apply ownership
  again. General scenario adjustments require an explicit entity.
- An internal rule names the originating entity and counterparty and generates both
  sides. **Do not enter its mirror manually.** Both entities need a valid treatment
  for the concept. Transactions between two entities in the boundary are eliminated
  only from consolidation; the entities' individual tax calculations retain them.
  A transaction with an entity outside the boundary remains in the selected cash flow.

### Drivers and operational units

| Driver | Source |
|---|---|
| `FIXED` | `unitValue` for each occurrence |
| `INPUT_VOLUME`, `OUTPUT_VOLUME` | Actual material received/produced by the selected node |
| `EXPORTED_VOLUME` | Full cargo leaving the selected carrier; loading is not an export |
| `OPERATING_HOURS` | Active simulated hours, including active hours with no material |
| `EVENT_COUNT` | Carrier departure events (zero on other current node types) |
| `INSTALLED_CAPACITY`, `DISTANCE`, `CONNECTION_COUNT` | Explicit static annual quantity in the version configuration |
| `PERCENT_CAPEX` | Explicit monetary CAPEX basis in `annualQuantity`; `unitValue` is the fractional rate |

Every non-fixed rule requires a compatible `MetricConversion`. Raw volume units are
named `SIMULATOR_UNIT` because legacy physical quantities do not establish consistent
m³/tonne/MMBtu semantics. The user supplies the unit and conversion factor explicitly;
no universal gas-to-LNG conversion is assumed. Hours use raw `HOUR`, events use `EVENT`.
Observed drivers cannot override simulation with an `annualQuantity`. Static drivers
require that quantity and cannot silently read an unrelated node field.

The simulator retains its **365-day, 8,760-hour year** convention, now with exclusive
hour bounds and per-year counter deltas. Economic runs respect node startup dates,
lifetimes and activation. Lifetime is converted from months at 8,760/12 hours per
month; a leap year's extra day is not simulated. Well decline uses operating age.
Each evaluation constructs fresh simulator state and does not attach transient
results to the managed Version. Raw losses remain in the originating simulator unit;
they are saved for audit, not converted across gas/liquid phases without a factor.

The physical engine remains a simplified model: phases follow its existing ordering
and LNG exports are recognized at departure, not at destination arrival. Contracts
using `OUTPUT_VOLUME` explicitly choose a sale at the producing node (as in the
wellhead example). They do not imply that all intermediate nodes earn revenue.

### Assets, taxes and cash reconciliation

`CapitalAsset` generates one capitalizable purchase and monthly straight-line
depreciation, aggregated by year. The purchase is ENAI when paid; it never reduces
RAI directly. The depreciation concept is `<asset concept>.depreciation` and needs
its own deductible treatment. The service month counts as a full depreciation month.
The residual value reduces the depreciable basis; disposal proceeds require a separate
explicit rule. Intangible amortization uses the same asset schedule mechanism.
Capitalizable purchases cannot be duplicated as ordinary expense rules.

Treatments are identified by concept, entity and validity dates. Overlapping or
missing coverage fails validation. Deductibility can be partial: the deductible
cash expense goes to EAI on recognition, the remainder to ENAI when paid. Deductible
non-cash expense goes to GND and is added back through AGND. Non-cash taxable income
is reversed with a negative AGND. Non-cash non-taxable/non-deductible movements do
not produce cash.

Tax losses are tracked FIFO by entity. Opening losses are treated as originating
in `startYear - 1`. A loss originating in year Y with expiry K can be used through
Y+K; it expires before Y+K+1. The offset limit caps use as a fraction of positive
RAI. Negative RAI never creates a tax refund. Taxes are paid at year-end plus the
entity's explicit whole-year payment lag. The configuration implements a hypothetical
flat-rate regime, not a statement of any country's law.

For each year:

```text
RAI = IAI - EAI - GND
RDI = RAI - accruedTaxes
baseCashFlow = RDI + INAI - ENAI + AGND
cashTimingAdjustment = actualNetCash - taxesPaid - baseCashFlow
cashFlow = baseCashFlow + cashTimingAdjustment
NPV = sum(cashFlow[t] / (1 + WACC)^t), t=0..N
```

INAI/ENAI cash movements use cash dates. Taxable/deductible accrual movements use
recognition dates. The cash adjustment reconciles receivables, payables, tax timing
and cent rounding explicitly. Working-capital requirements and recovery are separate
non-deductible/non-taxable cash rules; do not duplicate balances already generated
by delayed invoice dates. Receivables, payables and taxes due after the horizon are
reported as `pendingBalances`, without assumed terminal collection.

## API

All routes use `/api/v1/projects/{projectId}/versions/{versionId}/economics`.
The servlet prefix is configured globally; controller mappings omit `/api/v1`.

| Method and suffix | Result | Permission |
|---|---|---|
| `PUT /configuration` | Validates and replaces the complete aggregate | `EDIT_PROJECT` |
| `GET /configuration` | Reads the saved aggregate | `VIEW_PROJECT` |
| `POST /evaluations` | Runs simulation and calculation; returns 201 and snapshot | `EDIT_PROJECT` |
| `GET /evaluations` | Reads the version's saved evaluations, newest first | `VIEW_PROJECT` |
| `GET /evaluations/{id}` | Reads one evaluation scoped to the version | `VIEW_PROJECT` |

Platform administrators have access. JWT authentication is covered by the existing
`anyRequest().authenticated()` rule in `SecurityConfig`. Service authorization adds
project permissions and ownership checks. Responses use `ApiResponse`; invalid
configuration returns 400, unauthenticated 401, unauthorized 403, missing resource
or version/project mismatch 404. Failures flow through `GlobalExceptionHandler`.
Successful saves/evaluations log through `AppLogger` without logging configuration payloads.

## Complete example: NPV USD 169.20

Use [economic-configuration.json](../backend/src/test/resources/economic_resources_examples/economic-configuration.json). The golden
test reads this file and asserts independently specified expected amounts.

Two hypothetical tax entities A/B own 60%/40% of both wells. WACC is 10%, tax is 30%,
losses carry forward without expiration with full offset, and taxes are paid in the
same year. This is a didactic wellhead sale, not a full LNG-chain investment estimate.

The first well produces 100 raw units/hour from 2031; the second produces 500 from
2032. Each operates 8,760 hours/year without losses, decline or maintenance. Conversion
factor `0.000114155251` is the explicit rounded reciprocal of 8,760. Monetary rounding
therefore gives 100 sale units in 2031 and 600 in each later year at USD 1/unit.
Both wells have a 120-month physical life; the economic horizon ends in 2035.

Initial CAPEX is 1,000; economic asset life is 60 months, residual zero, service starts
January 2031, producing depreciation 200/year. Initial working capital is 100 and is
recovered in 2035. A non-taxable grant contributes 20 in 2030. Annual fixed expense is
50, general deductible expense 10, variable expense 0.10/unit, and non-deductible
expense 5. Of the first well's 2032 sale, 60 is collected in 2033 (40 is collected
in 2032). An internal annual charge of 20 is income for A and expense for B.

| Section | 2030 (t=0) | 2031 | 2032 | 2033 | 2034 | 2035 |
|---|---:|---:|---:|---:|---:|---:|
| IAI | 0 | 100 | 600 | 600 | 600 | 600 |
| EAI | 0 | 70 | 120 | 120 | 120 | 120 |
| GND | 0 | 200 | 200 | 200 | 200 | 200 |
| RAI | 0 | -170 | 280 | 280 | 280 | 280 |
| Taxes | 0 | 0 | 33 | 84 | 84 | 84 |
| RDI | 0 | -170 | 247 | 196 | 196 | 196 |
| INAI | 20 | 0 | 0 | 0 | 0 | 100 |
| ENAI | 1,100 | 5 | 5 | 5 | 5 | 5 |
| AGND | 0 | 200 | 200 | 200 | 200 | 200 |
| Cash adjustment | 0 | 0 | -60 | 60 | 0 | 0 |
| **Cash flow** | **-1,080** | **25** | **382** | **451** | **391** | **491** |

Tax reconciliation before consolidation:

| Entity | 2031 RAI / carried loss | 2032 RAI | Loss used | Taxable base | 2032 tax |
|---|---:|---:|---:|---:|---:|
| A | -82 / 82 | 188 | 82 | 106 | 31.80 |
| B | -88 / 88 | 92 | 88 | 4 | 1.20 |
| Total | -170 / 170 | 280 | 170 | 110 | 33.00 |

From 2033, A pays 56.40 and B pays 27.60 annually. Internal transfers disappear
from consolidated IAI/EAI but are included in the tax ledgers. All outstanding
balances are settled by 2035.

```text
NPV = -1080 + 25/1.10 + 382/1.10^2 + 451/1.10^3
            + 391/1.10^4 + 491/1.10^5
    = USD 169.20
```

### Reproduce through the API

Start the backend against a migrated PostgreSQL database. Obtain a Bearer token for
an administrator or an editor of an existing project. The script below creates one
new version and two nodes, replaces fixture UUIDs with their actual IDs, saves the
configuration and evaluates it. It does not modify an existing version.

```powershell
./docs/examples/run-economic-example.ps1 -ProjectId $projectId -AccessToken $token
```

For a version already containing the example nodes, replace the two fixture UUIDs in
the JSON and issue the `PUT /configuration`, then `POST /evaluations` with an empty
body. `snapshot.result.periods` contains the table; `snapshot.result.npv` is 169.20.

## Verification and migrations

```powershell
cd backend
mvn test
# Separate real PostgreSQL verification: use a dedicated, disposable database.
$env:ECONOMIC_TEST_DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55439/enerscope_economic_test'
# Set ECONOMIC_TEST_DATABASE_USER/PASSWORD if required by your local server.
mvn '-Dtest=PostgreSqlEconomicIT' test
cd ../frontend
npm ci
npm run build
```

`PostgreSqlEconomicIT` deliberately fails if the test database URL is absent. It is
an explicit integration suite, not a silently skipped default test. It applies
Flyway and uses Hibernate `validate`; test data is transactionally rolled back.

V8 introduces version-owned economic tables and renames the legacy result year
column to avoid the H2 reserved identifier. V9 adds the missing physical fields
`base_node.maintenance_duration` and `flng_unit.gas_consumption`. Java mappings now
use the existing `result.version_id`, `result_per_node.result_id` and `node_id` columns.
Applied V1-V7 migrations are unchanged. Back up the database as usual before deployment;
run the new migrations before starting the new backend binary.

The editable integrated diagram is in `backend/docs/backend-class-diagram.drawio`.
The Spanish PDF and its diagram pages are generated by `docs/tools/build-economic-document.py`
into `output/pdf/economic-module-class-diagram.pdf`.

Verified on 2026-09-16: 185 default cases plus 2 PostgreSQL cases, zero failures,
errors or skipped cases; frontend production build successful. The HTTP integration
case creates the version and wells using the same JSON fixture as the PowerShell
script, then saves and evaluates the configuration and asserts NPV 169.20. The PDF's
eight pages were rendered and visually inspected. PostgreSQL verification used a
temporary local PostgreSQL 16 instance, not the application's existing database.
