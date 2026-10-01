import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

async function sourceModule(path) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  return import('data:text/javascript;base64,' + Buffer.from(output).toString('base64'));
}
const { emptyDraft, toConfiguration, fromConfiguration, validateDraft, nodesForDriver, changeRuleDriver } = await sourceModule('../src/utils/economicDraft.ts');
const { irrText, money, percent, paybackText } = await sourceModule('../src/utils/economicFormat.ts');
const nodes = [{ id: 'node-1', name: 'Well', type: 'WELL' }];
function draft() {
  return { ...emptyDraft(), startYear: '2030', years: '5', wacc: '7', entityName: 'Example', taxRate: '30',
    rules: [{ id: 'r1', concept: 'Sale', direction: 'INCOME', driver: 'OUTPUT_VOLUME', nodeId: 'node-1',
      value: '10,5', unit: 'UNIT', factor: '0,01', from: '2031', to: '2035' }],
    assets: [{ id: 'a1', concept: 'Plant', nodeId: '', cost: '1000', residual: '100',
      purchase: '2030-12-31', service: '2031-01-01', life: '60' }] };
}
test('maps percentage and comma decimals into annual occurrences and explicit conversion', () => {
  const c = toConfiguration(draft());
  assert.equal(c.wacc, 0.07);
  assert.equal(c.taxEntities[0].taxRate, 0.3);
  assert.equal(c.nodeProfiles[0].rules[0].unitValue, 10.5);
  assert.equal(c.nodeProfiles[0].rules[0].occurrences.length, 5);
  assert.deepEqual(c.nodeProfiles[0].rules[0].occurrences[0], { recognitionDate: '2031-12-31', cashDate: '2031-12-31' });
  assert.equal(c.conversions[0].factor, 0.01);
  assert.equal(c.conversions[0].annualQuantity, null);
});
test('capitalizes purchase separately from deductible depreciation', () => {
  const c = toConfiguration(draft());
  assert.equal(c.assets[0].paymentDate, c.assets[0].purchaseDate);
  assert.equal(c.taxTreatments.find(t => t.concept === 'Plant').classification, 'CAPITALIZABLE');
  assert.equal(c.taxTreatments.find(t => t.concept === 'Plant.depreciation').classification, 'DEDUCTIBLE');
  assert.equal(c.adjustments.length, 0);
});
test('shares one conversion for income and expense on the same node and metric', () => {
  const d = draft(); d.rules.push({ ...d.rules[0], id: 'r2', concept: 'Cost', direction: 'EXPENSE' });
  assert.equal(toConfiguration(d).conversions.length, 1);
  assert.ok(Object.values(validateDraft(d, nodes)).every(e => !e.length));
});
test('rejects inconsistent shared conversions and foreign nodes', () => {
  const d = draft(); d.rules.push({ ...d.rules[0], id: 'r2', concept: 'Cost', direction: 'EXPENSE', factor: '2' });
  assert.ok(validateDraft(d, nodes).Costos.length);
  assert.ok(validateDraft(d, []).Ingresos.length);
});
test('rejects invalid dates, horizon, rates and residual above cost', () => {
  const d = draft(); d.assets[0].purchase = '2030-02-30'; d.assets[0].residual = '2000'; d.years = '101'; d.taxRate = '101'; d.wacc = '';
  const e = validateDraft(d, nodes);
  assert.ok(e.General.length && e.Inversion.length && e.Impuestos.length);
});
test('simple configuration round trips without changing amounts or policies', () => {
  const c = toConfiguration(draft());
  assert.deepEqual(toConfiguration(fromConfiguration(c)), c);
});
test('advanced tax policy cannot be overwritten by simple editor', () => {
  const c = toConfiguration(draft()); c.taxEntities[0].carryLosses = true;
  assert.equal(fromConfiguration(c), null);
});
test('delayed payment cannot be overwritten by simple editor', () => {
  const c = toConfiguration(draft()); c.nodeProfiles[0].rules[0].occurrences[0].cashDate = '2032-12-31';
  assert.equal(fromConfiguration(c), null);
});
test('unknown fields and internal counterparts force read only mode', () => {
  const c = toConfiguration(draft()); c.futurePolicy = true;
  assert.equal(fromConfiguration(c), null);
  delete c.futurePolicy; c.nodeProfiles[0].rules[0].counterpartyId = 'other';
  assert.equal(fromConfiguration(c), null);
});
test('zero IRR is a calculated result while unavailable statuses never display zero', () => {
  assert.match(irrText({ status: 'CALCULATED', rate: 0 }), /0/);
  for (const status of ['NO_SIGN_CHANGE','NON_CONVENTIONAL','NUMERICAL_FAILURE','INSUFFICIENT_DATA'])
    assert.ok(irrText({ status, rate: null }) && !irrText({ status, rate: null }).includes('0'));
  assert.equal(money(null), 'No disponible');
  assert.equal(percent(null), 'No disponible');
});
test('changing horizon cannot silently truncate rule occurrences', () => {
  const d = draft(); d.years = '2';
  assert.ok(validateDraft(d, nodes).Ingresos.length);
});
test('depreciation concepts cannot collide with operating expenses', () => {
  const d = draft(); d.rules[0].concept = 'Plant.depreciation';
  assert.ok(validateDraft(d, nodes).Ingresos.length);
});


test('payback statuses distinguish no recovery, not applicable, insufficient data and failure', () => {
  for (const status of ['NOT_RECOVERED_WITHIN_HORIZON', 'NOT_APPLICABLE', 'INSUFFICIENT_DATA', 'NUMERICAL_FAILURE'])
    assert.ok(paybackText({ status, period: null, year: null, becomesNegativeAgain: false }).length);
  assert.equal(paybackText({ status: 'RECOVERED', period: 2, year: 2032, becomesNegativeAgain: true }), 'Período 2 · año 2032');
});
test('unsupported drivers remain read only and missing currency is never assumed', () => {
  const c = toConfiguration(draft());
  c.nodeProfiles[0].rules[0].driver = 'INPUT_VOLUME';
  c.conversions[0].metric = 'INPUT_VOLUME';
  assert.equal(fromConfiguration(c), null);
  assert.match(money(100), /moneda no disponible/);
});

test('export selection includes only scenario carriers and production keeps other nodes', () => {
  const list = [...nodes, { id: 'ship', name: 'Ship', type: 'LNG_CAMER' }, { id: 'port', name: 'Port', type: 'SEAPORT_TERMINAL' }];
  assert.deepEqual(nodesForDriver(list, 'EXPORTED_VOLUME').map(n => n.id), ['ship']);
  assert.equal(nodesForDriver(list, 'OUTPUT_VOLUME').length, 3);
  assert.deepEqual(nodesForDriver(nodes, 'EXPORTED_VOLUME'), []);
});
test('switching to exports clears incompatible node and conversion but preserves eligible carrier', () => {
  const r = draft().rules[0];
  const changed = changeRuleDriver(r, 'EXPORTED_VOLUME', nodes);
  assert.equal(changed.nodeId, ''); assert.equal(changed.unit, ''); assert.equal(changed.factor, '');
  const ships = [{ ...nodes[0], type: 'LNG_CAMER' }];
  assert.equal(changeRuleDriver(r, 'EXPORTED_VOLUME', ships).nodeId, r.nodeId);
});
test('saved export rules on noncarriers cannot be saved while carrier rules remain valid', () => {
  const d = draft(); d.rules[0].driver = 'EXPORTED_VOLUME';
  assert.ok(validateDraft(d, nodes).Ingresos.includes('El volumen exportado requiere un buque del escenario.'));
  assert.deepEqual(validateDraft(d, [{ ...nodes[0], type: 'LNG_CAMER' }]).Ingresos, []);
});

test('asset validation identifies purchase range and service date independently', () => {
  const d = draft(); d.assets[0].purchase = '2029-12-31'; d.assets[0].service = '2029-01-01';
  const errors = validateDraft(d, nodes).Inversion;
  assert.ok(errors.some(e => e.includes('Plant: compra y pago') && e.includes('2030 y 2035')));
  assert.ok(errors.some(e => e.includes('Plant: la puesta en servicio')));
});
