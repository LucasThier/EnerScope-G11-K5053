// Optional browser acceptance check. Run against local Vite with PLAYWRIGHT_MODULE
// pointing to an installed playwright module; API calls use isolated fixtures.
import assert from 'node:assert/strict';
import { readFile, mkdir } from 'node:fs/promises';
import { pathToFileURL, fileURLToPath } from 'node:url';
import ts from 'typescript';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const source = await readFile(new URL('../src/utils/economicDraft.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { emptyDraft, toConfiguration } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage();
const failures = [];
page.on('pageerror', e => failures.push(e.message));
const user = { id: 'u1', mail: 'test@example.com', firstName: 'Analista', lastName: 'Prueba', platformRole: 'ADMIN' };
await context.addInitScript(user => {
  localStorage.setItem('accessToken','test'); localStorage.setItem('refreshToken','test');
  localStorage.setItem('authUser',JSON.stringify(user));
},user);
const projects = ['p1','p2'].map((id,i) => ({ id, name: 'Proyecto ' + (i + 1), organizationName: 'Organización', organizationId: 'o1', memberCount: 1, lastModified: '2030-01-01T00:00:00Z' }));
const scenario = { id: 'v1', name: 'Base', lastModified: '2030-01-01T00:00:00Z', nodes: [{ id: 'n1', name: 'Pozo A', type: 'WELL' }] };
let configuration = null, history = [], saves = 0, simulations = 0, rejectSave = false;
const fixtureDraft = { ...emptyDraft(), startYear: '2030', years: '2', wacc: '10', entityName: 'Entidad de prueba', taxRate: '30' };
const fixture = toConfiguration(fixtureDraft);
const evaluation = () => ({
  id: 'e' + simulations, createdAt: '2030-01-01T12:00:00Z',
  snapshot: { schemaVersion: 2, configuration: structuredClone(configuration), result: {
    npv: 74.38, indicators: { irr: { status: 'CALCULATED', rate: 0.2 },
      simplePayback: { status: 'RECOVERED', period: 2, year: 2032, becomesNegativeAgain: false },
      discountedPayback: { status: 'NOT_RECOVERED_WITHIN_HORIZON', period: null, year: null, becomesNegativeAgain: false },
      origin: 'STORED', calculationVersion: 1 },
    periods: [-1000, 600, 600].map((cashFlow,i) => ({ period: i, year: 2030 + i, cashFlow, discountedCashFlow: cashFlow / 1.1 ** i,
      taxableIncome: i ? 600 : 0, deductibleExpenses: 0, taxes: 0 })), pendingBalances: [],
  } },
});
await page.route('**/api/v1/**', async route => {
  const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1',''), method = route.request().method();
  const ok = data => route.fulfill({ json: { success: true, data, message: 'OK' } });
  if (path === '/auth/refresh') return ok({ user, accessToken:'test', refreshToken:'test' });
  if (path === '/projects') return ok(projects);
  if (path.endsWith('/members')) return ok([]);
  if (path === '/projects/p1/versions') return ok([scenario]);
  if (path === '/projects/p2/versions') return ok([]);
  if (path.endsWith('/configuration')) {
    if (method === 'PUT') {
      saves++;
      if (rejectSave) return route.fulfill({ status: 400, json: { success: false, message: 'Configuración inválida' } });
      configuration = route.request().postDataJSON(); return ok(configuration);
    }
    return configuration ? ok(configuration) : route.fulfill({ status: 404, json: { success: false, message: 'Economic configuration not found' } });
  }
  if (path.endsWith('/evaluations')) {
    if (method === 'POST') { simulations++; await new Promise(r => setTimeout(r,150)); const e=evaluation(); history.unshift(e); return ok(e); }
    return ok(history);
  }
  if (path.includes('/evaluations/')) return ok(history.find(e => path.endsWith('/' + e.id)));
  throw new Error('Unexpected request: ' + path);
});
const base = 'http://127.0.0.1:5173/projects/p1/versions/v1/economics';
try {
  await page.goto(base);
  await page.getByLabel('WACC anual (%)', { exact: true }).fill('10');
  await page.getByRole('button', { name: 'Impuestos', exact: true }).click();
  await page.getByLabel('Nombre de la entidad').fill('Entidad de prueba');
  await page.getByLabel('Tasa de impuesto (%)').fill('30');
  // Failed saves retain the draft and cannot trigger evaluation.
  rejectSave = true;
  await page.getByRole('button',{ name:'Guardar y simular', exact:true }).click();
  await page.getByText('Configuración inválida',{exact:true}).waitFor();
  assert.equal(simulations,0);
  assert.equal(await page.getByLabel('Nombre de la entidad').inputValue(),'Entidad de prueba');
  rejectSave = false;
  await page.getByRole('button',{ name:'Guardar y simular', exact:true }).evaluate(button => { button.click(); button.click(); });
  await page.getByRole('heading',{name:'VAN',exact:true}).waitFor();
  assert.equal(simulations,1);
  assert.equal(saves,2);
  const output = new URL('../node_modules/.cache/economic-ui/',import.meta.url);
  await mkdir(output,{recursive:true});
  await page.screenshot({path: fileURLToPath(new URL('results-desktop.png',output)),fullPage:true});
  await page.getByRole('link',{name:'Historial',exact:true}).click();
  await page.getByRole('link',{name:'Ver evaluación →'}).click();
  await page.getByRole('heading',{name:'VAN',exact:true}).waitFor();
  assert.equal(simulations,1);
  await page.getByRole('link',{name:'Configuración',exact:true}).click();
  await page.getByLabel('WACC anual (%)',{exact:true}).fill('15');
  await page.getByRole('link',{name:'← Escenarios / Base'}).click();
  await page.getByRole('button',{name:'Quedarme',exact:true}).click();
  assert.equal(await page.getByLabel('WACC anual (%)',{exact:true}).inputValue(),'15');
  await page.getByRole('button',{name:'Proyecto 1',exact:true}).click();
  await page.getByRole('option').filter({hasText:'Proyecto 2'}).getByRole('button').click();
  await page.getByRole('button',{name:'Salir y descartar'}).click();
  await page.getByText('Este proyecto todavía no tiene escenarios.',{exact:false}).waitFor();
  assert.equal(new URL(page.url()).pathname,'/projects/p2/versions');
  configuration = fixture;
  await page.goto(base);
  await page.getByLabel('WACC anual (%)',{exact:true}).waitFor();
  await page.screenshot({path: fileURLToPath(new URL('configuration-desktop.png',output)),fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.waitForFunction(() => document.querySelector('aside').getBoundingClientRect().width === 64);
  await page.screenshot({path: fileURLToPath(new URL('configuration-mobile.png',output)),fullPage:true});
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  assert.equal(overflow,false,'Mobile must not overflow the page');
  configuration = structuredClone(fixture); configuration.taxEntities[0].carryLosses = true;
  await page.goto(base);
  await page.getByText('Esta configuración utiliza opciones avanzadas.',{exact:false}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Guardar y simular'}).count(),0);
  user.platformRole = 'USER'; configuration = fixture;
  await page.goto(base);
  await page.getByText('Tenés acceso de consulta.',{exact:false}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Guardar y simular'}).isDisabled(),true);
  assert.deepEqual(failures,[]);
  console.log('PASS: save failure, single evaluation, immutable history, dirty navigation, project switching, advanced read-only, viewer permissions, desktop/mobile layout.');
} finally { await browser.close(); }
