// Inicio layout: the whole page fits without a vertical scroll at 1440x800 and 1280x720 (light and dark) with full
// data, and nothing overflows horizontally at 1024x768. Like every *-browser.mjs it is NOT part of `npm test`: run it
// against a local Vite server (any port but 5173 if the backend dev setup uses it) with the API mocked here:
//   cd frontend && npx vite --port 5199 --strictPort &
//   APP_URL=http://127.0.0.1:5199 PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs node tests/homeLayout-browser.mjs
// (see docs/testing.md, "Inicio layout"). PW_CHANNEL=chrome uses an installed Chrome instead of Playwright's Chromium;
// SHOT_DIR=/some/dir also saves a screenshot per theme and size.
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const base = process.env.APP_URL ?? 'http://127.0.0.1:5199';
const browser = await chromium.launch(process.env.PW_CHANNEL ? { channel: process.env.PW_CHANNEL, headless: true } : { headless: true });

const user = { id: 'u1', mail: 'demo-admin-plataforma@enerscope.test', firstName: 'Demo', lastName: 'Admin', platformRole: 'ADMIN' };
const now = Date.now();
const iso = (ms) => new Date(now - ms).toISOString();
const H = 3600e3, D = 864e5;
const LONG = 'Demo - Escenario con un nombre larguísimo para comprobar que se corta con puntos suspensivos y no empuja nada';

const projects = [
  { id: 'p1', name: 'Demo - Cuenca Austral', description: '', organizationId: 'o1', organizationName: 'Demo - EnerScope', memberCount: 3, lastModified: iso(2 * H), myRole: 'ADMIN' },
  { id: 'p2', name: 'Demo - Cuenca Neuquina', description: '', organizationId: 'o1', organizationName: 'Demo - EnerScope', memberCount: 6, lastModified: iso(5 * D), myRole: 'ADMIN' },
  { id: 'p3', name: 'Demo - Proyecto con un nombre bastante largo para probar el truncado', description: '', organizationId: 'o1', organizationName: 'Demo - EnerScope', memberCount: 1, lastModified: iso(9 * D), myRole: 'ADMIN' },
  { id: 'p4', name: 'Demo - Cuarto proyecto', description: '', organizationId: 'o1', organizationName: 'Demo - EnerScope', memberCount: 1, lastModified: iso(12 * D), myRole: 'ADMIN' },
];
const orgs = [{ id: 'o1', name: 'Demo - EnerScope', createdAt: '2026-01-01T00:00:00Z', active: true, memberCount: 9, memberType: 'OWNER' }];
const versions = [
  { id: 'v2', name: 'Demo - Ampliación', parentVersionId: 'v1', createdAt: iso(20 * D) },
  { id: 'v1', name: 'Demo - Base', parentVersionId: null, createdAt: iso(40 * D) },
];
const sc = (id, name, status, extra = {}) => ({ id, versionId: 'v1', name, defaultScenario: false, configured: true, draftPending: false, status, latestEvaluatedAt: iso(3 * D), createdAt: iso(30 * D), lastModified: iso(1 * H), ...extra });
const scenarios = [
  sc('s1', 'Escenario base', 'UP_TO_DATE', { defaultScenario: true, lastModified: iso(2 * H) }),
  sc('s2', 'Demo - Costos altos', 'OUTDATED', { lastModified: iso(3 * D) }),
  sc('s3', 'Demo - Borrador', 'NEVER_SIMULATED', { configured: false, draftPending: true, latestEvaluatedAt: null, lastModified: iso(1 * D) }),
  sc('s4', LONG, 'OUTDATED', { draftPending: true, lastModified: iso(4 * D) }),
  sc('s5', 'Demo - En curso', 'SIMULATING', { lastModified: iso(5 * 60e3) }),
  sc('s6', 'Demo - Sexto (no entra)', 'UP_TO_DATE', { lastModified: iso(9 * D) }),
];
const access = {
  projectId: 'p1', platformAdmin: true, projectRole: null, projectPermissions: [], canManageProject: true, canCreateRootVersions: true,
  versions: versions.map((v) => ({ versionId: v.id, versionName: v.name, source: 'PLATFORM_ADMIN', roles: [], effectiveRole: null, editableVerticals: [],
    canView: true, canEdit: true, canEditConnections: true, canSimulate: true, canManage: true, canManageRoles: true,
    canViewEconomicResults: true, canViewEconomicConfiguration: true, canEditEconomicConfiguration: true })),
};
const comparisons = [{ id: 'c1', name: 'Demo - Base vs Costos altos', description: null, scenarios: [], hasDeletedScenarios: false, createdAt: iso(1 * D) }];

async function open(viewport, theme, list = scenarios) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(({ user, theme }) => {
    localStorage.setItem('accessToken', 't'); localStorage.setItem('refreshToken', 't'); localStorage.setItem('authUser', JSON.stringify(user));
    localStorage.setItem('theme', JSON.stringify(theme));
  }, { user, theme });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const ok = (data) => route.fulfill({ json: { success: true, data, message: 'OK' } });
    if (path === '/auth/refresh') return ok({ user, accessToken: 't', refreshToken: 't' });
    if (path === '/projects') return ok(projects);
    if (path === '/organizations' || path === '/organizations/owned') return ok(orgs);
    if (path === '/projects/p1/access') return ok(access);
    if (path === '/projects/p1/versions') return ok(versions);
    if (path === '/projects/p1/versions/v1/scenarios') return ok(list);
    if (path === '/projects/p1/comparisons') return ok(comparisons);
    return ok([]);
  });
  return { context, page, errors };
}

const overflow = (page) => page.evaluate(() => {
  const main = document.querySelector('main');
  return { vertical: main.scrollHeight - main.clientHeight, horizontalMain: main.scrollWidth - main.clientWidth,
    horizontalDocument: document.documentElement.scrollWidth - document.documentElement.clientWidth };
});

try {
  for (const theme of ['light', 'dark']) {
    for (const [width, height, rows] of [[1440, 800, 5], [1280, 720, 4]]) {
      const { context, page, errors } = await open({ width, height }, theme);
      await page.goto(`${base}/app`);
      await page.getByRole('heading', { name: 'Escenarios' }).waitFor();
      await page.getByText('Cargando escenarios…').waitFor({ state: 'detached' });
      await page.waitForTimeout(300);
      const card = (name) => page.locator('.rounded-card', { has: page.getByRole('heading', { name, exact: true }) }).last();
      const items = card('Escenarios').locator('ul > li');
      assert.equal(await items.count(), rows, `${width}x${height}: ${rows} scenario rows`);
      assert.equal(await card('Proyectos recientes').locator('ul > li').count(), 3, 'at most 3 recent projects');
      assert.equal(await card('Accesos rápidos').locator('ul > li').count(), 4, 'four quick access chips');
      if (process.env.SHOT_DIR) await page.screenshot({ path: `${process.env.SHOT_DIR}/inicio-${theme}-${width}x${height}.png` });
      const o = await overflow(page);
      assert.ok(o.vertical <= 1, `${theme} ${width}x${height}: vertical scroll of ${o.vertical}px`);
      assert.equal(o.horizontalMain, 0, `${theme} ${width}x${height}: horizontal overflow in main`);
      const clipped = await card('Accesos rápidos').locator('ul > li span.line-clamp-2')
        .evaluateAll((spans) => spans.filter((span) => span.scrollWidth > span.clientWidth || span.scrollHeight > span.clientHeight).map((span) => `${span.textContent} (cut off)`));
      assert.deepEqual(clipped, [], `${theme} ${width}x${height}: quick access titles cut off`);
      assert.deepEqual(errors, []);
      await context.close();
    }
  }
  // 0, 1, 2, 4 and 5 scenarios: no scroll, rows pinned under the header at a constant distance, and the footer of the case.
  const FOOTERS = { 0: 'Todavía no hay escenarios', 1: 'Agregá otro escenario para poder compararlos', 2: '1 escenario por simular', 4: null, 5: null };
  for (const [theme, width, height] of [['light', 1440, 800], ['light', 1280, 720], ['dark', 1440, 800], ['dark', 1280, 720]]) {
    const distances = [];
    for (const count of [0, 1, 2, 4, 5]) {
      const { context, page, errors } = await open({ width, height }, theme, scenarios.slice(0, count));
      await page.goto(`${base}/app`);
      await page.getByRole('heading', { name: 'Escenarios' }).waitFor();
      await page.getByText('Cargando escenarios…').waitFor({ state: 'detached' });
      await page.waitForTimeout(300);
      if (process.env.SHOT_DIR) await page.screenshot({ path: `${process.env.SHOT_DIR}/inicio-${count}-escenarios-${theme}-${width}x${height}.png` });
      const o = await overflow(page);
      assert.ok(o.vertical <= 1, `${theme} ${width}x${height} with ${count} scenarios: vertical scroll of ${o.vertical}px`);
      const info = await page.evaluate(() => {
        const h2 = [...document.querySelectorAll('h2')].find((e) => e.textContent === 'Escenarios');
        const card = h2.closest('.rounded-card');
        const header = h2.parentElement.parentElement.getBoundingClientRect();
        const first = card.querySelector('ul > li');
        return { rows: card.querySelectorAll('ul > li').length, gap: first ? Math.round(first.getBoundingClientRect().top - header.bottom) : null, text: card.innerText };
      });
      assert.equal(info.rows, Math.min(count, width === 1440 ? 5 : 4), `${width}x${height} with ${count}: rows`);
      const footer = FOOTERS[count];
      if (footer) assert.ok(info.text.includes(footer), `${count} scenarios: footer "${footer}"`);
      else assert.ok(!/por simular|Agregá otro|Última comparación/.test(info.text), `${count} scenarios: no footer`);
      if (count === 0) assert.ok(info.text.includes('Crear escenario'), '0 scenarios: Crear escenario');
      if (count === 1) assert.ok(info.text.includes('Nuevo escenario'), '1 scenario: Nuevo escenario');
      assert.ok((await page.getByRole('link', { name: /^Comparar/ }).first().getAttribute('title') ?? '').includes('2 escenarios') === (count < 2), `${count} scenarios: Comparar hint`);
      if (info.gap !== null) distances.push(info.gap);
      assert.deepEqual(errors, []);
      await context.close();
    }
    assert.ok(distances.every((d) => d >= 0 && d <= 16), `${width}x${height}: first row is under the header (${distances})`);
    assert.ok(Math.max(...distances) - Math.min(...distances) <= 1, `${width}x${height}: the distance to the first row is constant (${distances})`);
  }
  // 1024x768 (drawer layout, one column from the grid below lg... lg is 1024 so the grid still applies): no horizontal overflow.
  for (const [width, height] of [[1024, 768], [768, 900], [390, 800]]) {
    const { context, page, errors } = await open({ width, height }, 'light');
    await page.goto(`${base}/app`);
    await page.getByRole('heading', { name: 'Escenarios' }).waitFor();
    await page.waitForTimeout(300);
    const o = await overflow(page);
    assert.equal(o.horizontalMain, 0, `${width}x${height}: horizontal overflow in main`);
    assert.equal(o.horizontalDocument, 0, `${width}x${height}: horizontal overflow in the document`);
    assert.deepEqual(errors, []);
    await context.close();
  }
  console.log('PASS: Inicio fits without a vertical scroll at 1440x800 and 1280x720 (light and dark); no horizontal overflow at 1024x768, 768 and 390.');
} finally {
  await browser.close();
}
