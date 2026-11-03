// Browser acceptance with isolated HTTP fixtures; no live project writes.
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { pathToFileURL, fileURLToPath } from 'node:url';
const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const failures = []; page.on('pageerror', e => failures.push(e.message));
const user = { id: 'u1', mail: 'test@example.com', firstName: 'Test', lastName: 'Admin', platformRole: 'ADMIN' };
await context.addInitScript(user => { localStorage.setItem('sidebarPinned', 'true'); localStorage.setItem('accessToken', 'test'); localStorage.setItem('refreshToken', 'test'); localStorage.setItem('authUser', JSON.stringify(user)); }, user);
const project = { id: 'p1', name: 'Project', organizationName: 'Org', organizationId: 'o1', memberCount: 1, lastModified: '2030-01-01T00:00:00Z' };
const scenarios = ['1', '2'].map(n => ({ id: 'v' + n, name: 'Scenario ' + n, lastModified: project.lastModified, nodes: [] }));
let previews = 0, saves = 0, stored = null, block = false;
const resultFor = r => ({ calculationVersion: 1, status: block ? 'BLOCKED' : 'READY', currency: 'USD', startYear: 2030, years: 5,
  winnerIds: block ? [] : [r.alternatives[0].id], issues: block ? ['Unidades incompatibles para DELIVERED_VOLUME'] : [],
  alternatives: r.alternatives.map((a, i) => ({ id: a.id, name: a.name, score: block ? null : 80 - i * 20, worstScore: block ? null : 80 - i * 20, eligible: true, rank: block ? null : i + 1,
    contexts: a.cells.map(c => ({ contextId: c.contextId, versionId: c.versionId, evaluationId: c.evaluationId, rounds: 1, score: 80 - i * 20, eligible: true, issues: [], values: [{ metric: 'NPV', value: 80 - i * 20, unit: 'USD', score: 80 - i * 20, contribution: 80 - i * 20, issue: null, meetsLimits: true }] })) })) });
await page.route('**/api/v1/**', async route => {
  const p = new URL(route.request().url()).pathname.replace('/api/v1', ''); const method = route.request().method();
  const ok = data => route.fulfill({ json: { success: true, data } });
  if (p === '/auth/refresh') return ok({ user, accessToken: 'test', refreshToken: 'test' });
  if (p === '/projects') return ok([project]);
  if (p === '/organizations' || p.endsWith('/owned') || p.endsWith('/members')) return ok([]);
  if (p === '/projects/p1') return ok(project);
  if (p === '/projects/p1/versions') return ok(scenarios);
  if (/\/economics\/evaluations$/.test(p)) return ok([{ id: p.includes('/v1/') ? 'e1' : 'e2', createdAt: project.lastModified, snapshot: { schemaVersion: 2, configuration: { currency: 'USD', wacc: 0.1, startYear: 2030, years: 5 }, physicalNodes: [], result: null } }]);
  if (p === '/projects/p1/comparisons/preview') { previews++; const request = route.request().postDataJSON(); assert.equal(request.criteria[0].weight, 100); return ok(resultFor(request)); }
  if (p === '/projects/p1/comparisons' && method === 'POST') { saves++; const r = route.request().postDataJSON(); stored = { id: 'saved', createdAt: project.lastModified, configuration: r, result: resultFor(r) }; return ok(stored); }
  if (p === '/projects/p1/comparisons') return ok(stored ? [{ id: stored.id, createdAt: stored.createdAt, name: stored.configuration.name }] : []);
  if (p === '/projects/p1/comparisons/saved') return ok(stored);
  throw new Error(`Unhandled fixture: ${method} ${p}`);
});
try {
  await page.goto((process.env.APP_URL || 'http://127.0.0.1:5179') + '/projects/p1/comparisons');
  await page.getByLabel('Nombre de la comparación', { exact: true }).fill('Decision test');
  await page.getByLabel('Alcance y supuestos de los contextos', { exact: true }).fill('Common scope and base conditions');
  await page.getByLabel(/Confirmo que las evaluaciones/).check();
  const evaluationSelects = page.locator('select').filter({ has: page.locator('option', { hasText: 'Elegir evaluación…' }) });
  await evaluationSelects.nth(0).selectOption('v1:e1'); await evaluationSelects.nth(1).selectOption('v2:e2');
  await page.getByLabel('Referencia 0 puntos (moneda)', { exact: true }).fill('0');
  await page.getByLabel('Referencia 100 puntos (moneda)', { exact: true }).fill('100');
  await page.getByRole('button', { name: 'Calcular comparación', exact: true }).click();
  await page.getByRole('heading', { name: 'Alternativa recomendada: Alternativa A' }).waitFor();
  assert.equal(previews, 1);
  await page.locator('summary').first().click();
  await page.getByRole('link', { name: 'Ver evaluación fuente' }).first().waitFor();
  await page.getByRole('button', { name: 'Guardar comparación', exact: true }).click();
  await page.getByText(/Guardada el/).waitFor(); assert.equal(saves, 1);
  await page.getByLabel('Referencia 100 puntos (moneda)', { exact: true }).fill('200');
  assert.equal(await page.getByRole('heading', { name: /Alternativa recomendada/ }).count(), 0);
  assert.equal(await page.getByRole('button', { name: 'Guardar comparación', exact: true }).isDisabled(), true);
  await page.getByLabel('Comparaciones guardadas', { exact: true }).selectOption('saved');
  await page.getByText(/Resultado histórico del/).waitFor(); assert.equal(previews, 1);
  assert.equal(await page.getByLabel('Referencia 100 puntos (moneda)', { exact: true }).inputValue(), '100');
  await mkdir(new URL('../node_modules/.cache/comparison-ui/', import.meta.url), { recursive: true });
  await page.screenshot({ path: fileURLToPath(new URL('../node_modules/.cache/comparison-ui/desktop.png', import.meta.url)), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForFunction(() => document.querySelector('main').getBoundingClientRect().left < 1);
  await page.screenshot({ path: fileURLToPath(new URL('../node_modules/.cache/comparison-ui/mobile.png', import.meta.url)), fullPage: true });
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1));
  block = true; await page.getByLabel('Referencia 100 puntos (moneda)', { exact: true }).fill('200');
  await page.getByRole('button', { name: 'Calcular comparación', exact: true }).click();
  await page.getByRole('heading', { name: 'Comparación bloqueada' }).waitFor();
  assert.equal(await page.getByRole('button', { name: 'Guardar comparación', exact: true }).isDisabled(), true);
  assert.deepEqual(failures, []);
  console.log('PASS comparison selection, preview, contribution details, save, stale-result clearing, historical reload, blocked recommendation and mobile layout');
} finally { await browser.close(); }
