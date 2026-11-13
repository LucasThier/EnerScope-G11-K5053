import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/utils/comparison.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const {
  formatMetric, cellText, roundsNote, deltaText, assessmentTone, findingTone, groupMetrics, scenarioNames,
  lineSegments, chartScale, draftError, toRequest, thresholdsForForm, nodeTypeLabel,
} = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));

const value = (v, status = 'AVAILABLE', availableRounds = 1, totalRounds = 1) => ({ value: v, status, availableRounds, totalRounds, distribution: null });
const nbsp = text => text.replace(/ /g, ' ');

test('values are formatted by unit and missing values are never shown as zero', () => {
  assert.match(nbsp(formatMetric('FRACTION', 0.1234)), /^12,34 ?%$/);
  assert.equal(formatMetric('YEARS', 1), '1 año');
  assert.equal(formatMetric('YEARS', 3), '3 años');
  assert.equal(formatMetric('HOURS', 8760), '8.760 h');
  assert.match(nbsp(formatMetric('CURRENCY', 1500.5, 'USD')), /USD 1\.500,50/);
  assert.equal(formatMetric('CURRENCY', 1500.5, null), '1.500,50');
  assert.equal(formatMetric('CURRENCY', null, 'USD'), 'No disponible');
  assert.equal(formatMetric('SIMULATOR_UNIT', Number.NaN), 'No disponible');
  assert.equal(cellText({ unit: 'YEARS' }, { value: value(null, 'NOT_RECOVERED_WITHIN_HORIZON') }), 'No se recupera en el horizonte');
  assert.equal(cellText({ unit: 'FRACTION' }, { value: value(null, 'NO_INVESTMENT') }), 'Sin inversión');
  assert.equal(cellText({ unit: 'FRACTION' }, { value: value(null, 'SOMETHING_NEW') }), 'No disponible');
});

test('probabilistic means say how many rounds they cover', () => {
  assert.equal(roundsNote({ value: value(5) }), null);
  assert.equal(roundsNote({ value: value(5, 'AVAILABLE', 10, 10) }), 'Media de 10 rondas');
  assert.equal(roundsNote({ value: value(5, 'AVAILABLE', 7, 10) }), 'Media de 7 de 10 rondas');
  assert.equal(roundsNote({ value: value(null, 'NO_SIGN_CHANGE', 0, 10) }), null);
});

test('deltas use percentage points for rates, years for paybacks and percent otherwise', () => {
  assert.equal(deltaText('FRACTION', { absoluteDelta: 0.0125, relativeDelta: 0.1, assessment: 'BETTER' }), '+1,25 p.p.');
  assert.equal(deltaText('YEARS', { absoluteDelta: -1, relativeDelta: -0.25, assessment: 'BETTER' }), '−1 año');
  assert.equal(deltaText('CURRENCY', { absoluteDelta: 500, relativeDelta: 0.5, assessment: 'BETTER' }), '+50 %');
  assert.equal(deltaText('CURRENCY', { absoluteDelta: -30, relativeDelta: -0.125, assessment: 'WORSE' }), '−12,5 %');
  assert.equal(deltaText('SIMULATOR_UNIT', { absoluteDelta: 70, relativeDelta: null, assessment: 'CHANGED' }), '+70 (desde 0)');
  assert.equal(deltaText('CURRENCY', { absoluteDelta: 0, relativeDelta: 0, assessment: 'EQUAL' }), 'Sin cambios');
  assert.equal(deltaText('CURRENCY', { absoluteDelta: null, relativeDelta: null, assessment: 'NOT_COMPARABLE' }), null);
  assert.equal(deltaText('CURRENCY', { absoluteDelta: null, relativeDelta: null, assessment: 'BASELINE' }), null);
});

test('assessments and findings map to tones', () => {
  assert.equal(assessmentTone('BETTER'), 'good');
  assert.equal(assessmentTone('WORSE'), 'bad');
  assert.equal(assessmentTone('CHANGED'), 'neutral');
  assert.equal(assessmentTone('NOT_COMPARABLE'), 'muted');
  assert.equal(findingTone('NPV_SIGN_CHANGE'), 'bad');
  assert.equal(findingTone('SIGNIFICANT_IMPROVEMENT'), 'good');
  assert.equal(findingTone('NODE_ADDED'), 'neutral');
});

test('indicators are grouped in a fixed category order and can be limited to relevant ones', () => {
  const rows = [
    { metric: 'LOSSES', category: 'PHYSICAL', relevant: false },
    { metric: 'CAPEX', category: 'COST', relevant: true },
    { metric: 'NPV', category: 'FINANCIAL', relevant: true },
    { metric: 'IRR', category: 'FINANCIAL', relevant: false },
  ];
  assert.deepEqual(groupMetrics(rows).map(g => g.category), ['FINANCIAL', 'COST', 'PHYSICAL']);
  const relevant = groupMetrics(rows, true);
  assert.deepEqual(relevant.map(g => g.category), ['FINANCIAL', 'COST']);
  assert.deepEqual(relevant[0].rows.map(r => r.metric), ['NPV']);
});

test('two evaluations of the same version get distinguishable names', () => {
  const names = scenarioNames({ scenarios: [
    { evaluationId: 'a', versionName: 'Base' }, { evaluationId: 'b', versionName: 'Base' }, { evaluationId: 'c', versionName: 'GNL' },
  ] });
  assert.deepEqual(names, { a: 'Base #1', b: 'Base #2', c: 'GNL' });
});

test('chart lines break at missing years instead of drawing zero', () => {
  const points = [
    { year: 2030, cashFlow: -100 }, { year: 2031, cashFlow: null }, { year: 2032, cashFlow: 50 },
    { year: 2033, cashFlow: 60 }, { year: 2035, cashFlow: 80 },
  ];
  assert.deepEqual(lineSegments(points, 'cashFlow').map(s => s.map(p => p.year)), [[2030], [2032, 2033], [2035]]);
  assert.deepEqual(lineSegments([{ year: 2030, cashFlow: null }], 'cashFlow'), []);
});

test('chart scale includes zero and every series, and is null without data', () => {
  const scale = chartScale([[{ year: 2030, cumulativeCashFlow: -1000 }], [{ year: 2032, cumulativeCashFlow: 450 }]], 'cumulativeCashFlow');
  assert.equal(scale.minYear, 2030);
  assert.equal(scale.maxYear, 2032);
  assert.ok(scale.min <= -1000 && scale.max >= 450);
  assert.ok(scale.ticks.includes(0));
  assert.equal(chartScale([[{ year: 2030, cumulativeCashFlow: null }]], 'cumulativeCashFlow'), null);
  const flat = chartScale([[{ year: 2030, producedVolume: 0 }]], 'producedVolume');
  assert.ok(flat.max > flat.min);
});

test('the draft needs two to ten distinct results and positive thresholds', () => {
  const a = { versionId: 'v1', evaluationId: '' };
  const b = { versionId: 'v2', evaluationId: 'e2' };
  assert.equal(draftError([a, b], '5', '1'), null);
  assert.match(draftError([a], '5', '1'), /al menos 2/);
  assert.match(draftError(Array.from({ length: 11 }, (_, i) => ({ versionId: 'v' + i, evaluationId: '' })), '5', '1'), /hasta 10/);
  assert.match(draftError([a, { versionId: '', evaluationId: '' }], '5', '1'), /cada fila/);
  assert.match(draftError([a, { ...a }], '5', '1'), /mismo resultado/);
  assert.equal(draftError([a, { versionId: 'v1', evaluationId: 'e9' }], '5', '1'), null);
  assert.match(draftError([a, b], '0', '1'), /relevancia/);
  assert.match(draftError([a, b], '5', 'abc'), /tasas/);
  assert.equal(draftError([a, b], '', ''), null);
});

test('the request converts % and p.p. to fractions and blank to the server default', () => {
  const items = [{ versionId: 'v1', evaluationId: '' }, { versionId: 'v2', evaluationId: 'e2' }];
  assert.deepEqual(toRequest(items, '7,5', '2'), {
    relevanceThreshold: 0.075, rateThreshold: 0.02,
    items: [{ versionId: 'v1', evaluationId: null }, { versionId: 'v2', evaluationId: 'e2' }],
  });
  const named = toRequest(items, '', '', '  Base vs B ', '  ');
  assert.equal(named.relevanceThreshold, null);
  assert.equal(named.name, 'Base vs B');
  assert.equal(named.description, null);
  assert.deepEqual(thresholdsForForm(0.05, 0.01), { relevancePercent: '5', ratePoints: '1' });
  assert.deepEqual(thresholdsForForm(0.075, 0.0125), { relevancePercent: '7.5', ratePoints: '1.25' });
});

test('node types are shown in Spanish and unknown ones as they come', () => {
  assert.equal(nodeTypeLabel('WELL'), 'Pozo');
  assert.equal(nodeTypeLabel('LNG_CAMER'), 'Buque metanero');
  assert.equal(nodeTypeLabel('NEW_TYPE'), 'NEW_TYPE');
  assert.equal(nodeTypeLabel(null), null);
});
