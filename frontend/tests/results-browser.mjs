// Real UI acceptance with isolated fixture responses; never writes to the backend.
import assert from 'node:assert/strict';
import { mkdir, readFile } from 'node:fs/promises';
import { pathToFileURL, fileURLToPath } from 'node:url';
import ts from 'typescript';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const source = await readFile(new URL('../src/utils/economicDraft.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { emptyDraft, toConfiguration } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const configuration = toConfiguration({ ...emptyDraft(), startYear: '2030', years: '5', currency: 'USD', wacc: '10', entityName: 'Operadora de ejemplo', taxRate: '30' });
const rawMetrics = [1, 2, 3, 4, 5].flatMap((period, i) => [
  { nodeId: 'n1', period, input: 0, output: 120000 - i * 8000, exported: 0, losses: 2000 + i * 100, operatingHours: 8200, events: 2 },
  { nodeId: 'n2', period, input: 100000 - i * 7000, output: 95000 - i * 7000, exported: 90000 - i * 7000, losses: 5000, operatingHours: 7900, events: 3 },
]);
const payback = { status: 'RECOVERED', period: 4, year: 2034, becomesNegativeAgain: false };
const latest = { id: 'e1', createdAt: '2026-10-01T15:30:00Z', snapshot: { schemaVersion: 2, configuration,
  physicalNodes: [{ id: 'n1', name: 'Pozo Norte' }, { id: 'n2', name: 'Terminal de exportación' }], rawMetrics,
  result: { npv: 24184.26, indicators: { irr: { status: 'CALCULATED', rate: 0.2 }, simplePayback: { ...payback, period: 3, year: 2033 }, discountedPayback: payback, origin: 'STORED', calculationVersion: 1 }, pendingBalances: [],
    periods: [-100000, 40000, 36000, 32000, 28000, 24000].map((cashFlow, period) => ({ period, year: 2030 + period, cashFlow, discountedCashFlow: cashFlow / 1.1 ** period, taxableIncome: period ? cashFlow + 10000 : 0, deductibleExpenses: period ? 10000 : 0, taxes: 0, nonCashExpenses: 0, resultBeforeTax: period ? cashFlow : 0, resultAfterTax: period ? cashFlow : 0, nonTaxableIncome: 0, nonTaxableExpenses: period ? 0 : 100000, nonCashAdjustments: 0, cashTimingAdjustment: 0 })) } } };
const historical = structuredClone(latest); historical.id = 'old'; historical.createdAt = '2026-09-25T15:30:00Z'; historical.snapshot.rawMetrics = [rawMetrics[0], { ...rawMetrics[2], output: null }]; historical.snapshot.physicalNodes[0].name = 'Pozo histórico';
const legacy = structuredClone(latest); legacy.id = 'legacy'; delete legacy.snapshot.rawMetrics; delete legacy.snapshot.physicalNodes;
const user = { id: 'viewer', mail: 'example@example.com', firstName: 'Analista', lastName: 'Demo', platformRole: 'USER' };
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const output = process.env.RESULTS_SCREENSHOTS || fileURLToPath(new URL('../node_modules/.cache/results-ui/', import.meta.url));
await mkdir(output, { recursive: true });
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1050 }, locale: 'es-AR' });
  await context.addInitScript(user => { localStorage.setItem('accessToken', 'fixture'); localStorage.setItem('authUser', JSON.stringify(user)); }, user);
  const page = await context.newPage(); const errors = []; let mutations = 0;
  page.on('pageerror', e => errors.push(e.message));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() !== 'GET') { mutations++; return route.fulfill({ status: 500 }); }
    const ok = data => route.fulfill({ json: { success: true, data, message: 'OK' } });
    if (path.endsWith('/projects')) return ok([{ id: 'p1', name: 'Cadena de GNL · Demostración', organizationId: 'o1', organizationName: 'Demo', memberCount: 1 }]);
    if (path.endsWith('/members')) return ok([{ userId: user.id, active: true, permissions: ['VIEW_PROJECT'] }]);
    if (path.endsWith('/versions')) return ok([{ id: 'v1', name: 'Escenario base · Datos ilustrativos', nodes: [{ id: 'n1', name: 'Nombre actual diferente', type: 'WELL' }], lastModified: latest.createdAt }]);
    if (path.endsWith('/draft')) return ok(null);
    if (path.endsWith('/configuration')) return ok({ ...configuration, startYear: 2040 });
    if (path.endsWith('/evaluations')) return ok([latest, historical, legacy]);
    if (path.endsWith('/evaluations/old')) return ok(historical);
    if (path.endsWith('/evaluations/legacy')) return ok(legacy);
    return route.fulfill({ status: 404 });
  });
  const base = (process.env.APP_URL || 'http://127.0.0.1:5173') + '/projects/p1/versions/v1/economics';
  await page.goto(base + '/results');
  await page.getByRole('heading', { name: 'VAN', exact: true }).waitFor();
  await page.screenshot({ path: output + '/02-economic-results.png', fullPage: true });
  await page.getByRole('tab', { name: 'Operativos', exact: true }).click();
  await page.getByLabel('Nodo de la evaluación').selectOption('n2');
  assert.equal(await page.getByRole('option', { name: 'Nombre actual diferente' }).count(), 0);
  await page.getByRole('rowheader', { name: '2031 / 1', exact: true }).waitFor();
  await page.screenshot({ path: output + '/01-operational-results.png', fullPage: true });
  await page.getByRole('tab', { name: 'Operativos', exact: true }).press('ArrowRight');
  assert.equal(await page.getByRole('tab', { name: 'Económicos', exact: true }).getAttribute('aria-selected'), 'true');
  await page.getByRole('tab', { name: 'Económicos', exact: true }).press('Home');
  assert.equal(await page.getByLabel('Nodo de la evaluación').inputValue(), 'n2');
  await page.getByLabel('Nodo de la evaluación').selectOption('n1');
  await page.getByRole('cell', { name: '120.000', exact: true }).waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForFunction(() => document.documentElement.scrollWidth <= window.innerWidth);
  await page.screenshot({ path: output + '/03-operational-mobile.png', fullPage: true });
  await page.getByRole('tab', { name: 'Económicos', exact: true }).click();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  await page.setViewportSize({ width: 1440, height: 1050 });
  await page.getByRole('link', { name: 'Historial', exact: true }).click();
  await page.getByRole('link', { name: 'Ver evaluación →' }).nth(1).click();
  await page.getByRole('tab', { name: 'Operativos', exact: true }).click();
  await page.getByRole('option', { name: 'Pozo histórico' }).waitFor({ state: 'attached' });
  await page.getByRole('cell', { name: 'No disponible', exact: true }).waitFor();
  await page.goto(base + '/results/legacy');
  await page.getByRole('tab', { name: 'Operativos', exact: true }).click();
  await page.getByRole('heading', { name: 'Sin datos operativos guardados' }).waitFor();
  await page.getByRole('tab', { name: 'Económicos', exact: true }).click();
  await page.getByRole('heading', { name: 'VAN', exact: true }).waitFor();
  assert.equal(mutations, 0); assert.deepEqual(errors, []);
  console.log('PASS: result tabs and keyboard, saved node names/year, node filtering, historical/missing metrics, legacy economics, viewer read-only, desktop/mobile; zero mutations. Screenshots: ' + output);
} finally { await browser.close(); }
