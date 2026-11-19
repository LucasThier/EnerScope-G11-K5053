import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/utils/comparison.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const {
  formatMetric, cellText, roundsNote, gapText, rankLabel, standingTone, byRank, findingTone, groupMetrics, scenarioNames,
  lineSegments, chartScale, toRequest, nodeTypeLabel, compactMetric, headline, barScale, seriesColor,
  scenarioStatusView, selectionCheck, selectionToQuery, selectionFromQuery, MAX_SCENARIOS,
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

test('the distance to the best reads in plain words and in the indicator unit', () => {
  const higher = { unit: 'CURRENCY', preference: 'HIGHER_IS_BETTER' };
  assert.equal(nbsp(gapText(higher, { standing: 'WORST', gapToBest: 514 }, 'USD')), 'USD 514 menos que el mejor');
  assert.equal(gapText(higher, { standing: 'BEST', gapToBest: 0 }, 'USD'), 'El mejor');
  assert.equal(gapText(higher, { standing: 'TIED', gapToBest: 0 }, 'USD'), 'Igual en todos');
  assert.equal(gapText({ unit: 'YEARS', preference: 'LOWER_IS_BETTER' }, { standing: 'MIDDLE', gapToBest: 1 }), '1 año más que el mejor');
  assert.equal(gapText({ unit: 'FRACTION', preference: 'HIGHER_IS_BETTER' }, { standing: 'WORST', gapToBest: 0.0873 }), '8,73 p.p. menos que el mejor');
  assert.match(nbsp(gapText({ unit: 'CURRENCY', preference: 'LOWER_IS_BETTER' }, { standing: 'WORST', gapToBest: 800 }, 'USD')), /^USD 800 más que el mejor$/);
  assert.equal(gapText(higher, { standing: 'UNRANKED', gapToBest: null }), null);
  assert.equal(gapText(higher, { standing: 'NOT_COMPARABLE', gapToBest: null }), null);
});

test('ranks read as ordinals and cells sort best first, unranked last', () => {
  assert.equal(rankLabel(1), '1.º');
  assert.equal(rankLabel(null), null);
  const cells = [{ id: 'a', rank: 3 }, { id: 'b', rank: null }, { id: 'c', rank: 1 }, { id: 'd', rank: 1 }];
  assert.deepEqual(byRank(cells).map(c => c.id), ['c', 'd', 'a', 'b']);
});

test('positions and findings map to tones', () => {
  assert.equal(standingTone('BEST'), 'good');
  assert.equal(standingTone('WORST'), 'bad');
  assert.equal(standingTone('MIDDLE'), 'muted');
  assert.equal(findingTone('NPV_SIGN_SPLIT'), 'bad');
  assert.equal(findingTone('AVAILABILITY_SPLIT'), 'bad');
  assert.equal(findingTone('LARGE_GAP'), 'neutral');
  assert.equal(findingTone('NODE_PARTIAL'), 'muted');
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
    { evaluationId: 'a', versionName: 'Base', projectId: 'p' }, { evaluationId: 'b', versionName: 'Base', projectId: 'p' },
    { evaluationId: 'c', versionName: 'GNL', projectId: 'p' },
  ] });
  assert.deepEqual(names, { a: 'Base #1', b: 'Base #2', c: 'GNL' });
});

test('scenarios from several projects are named with their project', () => {
  const names = scenarioNames({ scenarios: [
    { evaluationId: 'a', versionName: 'Base', projectId: 'p1', projectName: 'Neuquina' },
    { evaluationId: 'b', versionName: 'Base', projectId: 'p2', projectName: 'Austral' },
  ] });
  assert.deepEqual(names, { a: 'Base (Neuquina)', b: 'Base (Austral)' });
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



test('node types are shown in Spanish and unknown ones as they come', () => {
  assert.equal(nodeTypeLabel('WELL'), 'Pozo');
  assert.equal(nodeTypeLabel('LNG_CAMER'), 'Buque metanero');
  assert.equal(nodeTypeLabel('NEW_TYPE'), 'NEW_TYPE');
  assert.equal(nodeTypeLabel(null), null);
});

const cell = (evaluationId, value, standing = 'MIDDLE', status = 'AVAILABLE') =>
  ({ evaluationId, value: { value, status, availableRounds: 1, totalRounds: 1, distribution: null }, standing });

test('the headline is the highest comparable NPV and its margin over the second one', () => {
  const result = { metrics: [{ metric: 'NPV', values: [
    cell('austral', 300.8), cell('expansion', 815, 'BEST'), cell('conservative', -418, 'WORST'), cell('foreign', 9000, 'NOT_COMPARABLE')] }] };
  const best = headline(result);
  assert.equal(best.evaluationId, 'expansion');
  assert.equal(best.runnerUpId, 'austral');
  assert.ok(Math.abs(best.margin - 514.2) < 1e-9);
  assert.ok(Math.abs(best.relative - 514.2 / 300.8) < 1e-9);
  assert.equal(best.tied, false);
  assert.equal(best.allNegative, false);
});

test('the headline says when two scenarios tie, when every NPV is negative, and when there is none', () => {
  const tie = headline({ metrics: [{ metric: 'NPV', values: [cell('a', 500, 'BEST'), cell('b', 500, 'BEST')] }] });
  assert.equal(tie.tied, true);
  const losing = headline({ metrics: [{ metric: 'NPV', values: [cell('a', -50), cell('b', -10)] }] });
  assert.equal(losing.evaluationId, 'b');
  assert.equal(losing.allNegative, true);
  const alone = headline({ metrics: [{ metric: 'NPV', values: [cell('a', 10), cell('b', null, 'UNRANKED', 'NOT_AVAILABLE')] }] });
  assert.equal(alone.runnerUpId, null);
  assert.equal(alone.margin, null);
  assert.equal(headline({ metrics: [{ metric: 'NPV', values: [cell('a', null, 'UNRANKED', 'NOT_AVAILABLE')] }] }), null);
});

test('bar scales always include zero so negative values grow to the left', () => {
  const mixed = barScale([-400, 600, null]);
  assert.equal(mixed.zero, 0.4);
  assert.equal(mixed.position(600), 1);
  assert.equal(mixed.position(-400), 0);
  const positive = barScale([50, 100]);
  assert.equal(positive.zero, 0);
  assert.equal(positive.position(50), 0.5);
  assert.equal(barScale([null, undefined]), null);
  assert.ok(barScale([0]).position(0) >= 0);
});

test('headline numbers are compact but rates and years keep their full form', () => {
  assert.match(nbsp(compactMetric('CURRENCY', 1250000, 'USD')), /^USD 1,3\s?M/);
  assert.equal(compactMetric('CURRENCY', 1233.03, 'USD'), 'USD 1.233');
  assert.equal(compactMetric('CURRENCY', -418.03, 'USD'), 'USD -418');
  assert.equal(compactMetric('CURRENCY', 42.5, 'USD'), 'USD 42,5');
  assert.equal(compactMetric('YEARS', 3), '3 años');
  assert.match(nbsp(compactMetric('FRACTION', 0.2356)), /^23,56 ?%$/);
  assert.equal(compactMetric('CURRENCY', null, 'USD'), 'No disponible');
});

test('scenario colours follow the position and never cycle past the palette', () => {
  assert.equal(seriesColor(0).bg, 'bg-series-1');
  assert.equal(seriesColor(7).stroke, 'stroke-series-8');
  assert.equal(seriesColor(12).bg, 'bg-series-8');
  assert.equal(MAX_SCENARIOS, 8);
});

const scenario = (versionId, status, extra = {}) => ({ projectId: 'p', projectName: 'P', versionId, versionName: versionId, status,
  latestEvaluationId: null, latestEvaluatedAt: null, mode: null, rounds: null, ready: true, readinessErrors: [], canSimulate: true, job: null, ...extra });

test('comparing needs two to eight scenarios, all simulated and up to date', () => {
  assert.equal(selectionCheck([scenario('a', 'UP_TO_DATE'), scenario('b', 'UP_TO_DATE')]).canCompare, true);
  assert.match(selectionCheck([scenario('a', 'UP_TO_DATE')]).reason, /al menos 2/);
  const pending = selectionCheck([scenario('a', 'UP_TO_DATE'), scenario('b', 'OUTDATED'), scenario('c', 'NEVER_SIMULATED', { ready: false }),
    scenario('d', 'NEVER_SIMULATED', { canSimulate: false })]);
  assert.equal(pending.canCompare, false);
  assert.match(pending.reason, /3 escenarios necesitan simularse/);
  assert.deepEqual(pending.toSimulate.map(s => s.versionId), ['b']);
  assert.deepEqual(pending.blocked.map(s => s.versionId), ['c', 'd']);
  assert.match(selectionCheck([scenario('a', 'UP_TO_DATE'), scenario('b', 'SIMULATING')]).reason, /simulación en curso/);
});

test('scenario status reads in plain words, with progress and failed runs', () => {
  assert.equal(scenarioStatusView(scenario('a', 'UP_TO_DATE', { latestEvaluatedAt: '2026-11-12T10:00:00Z', mode: 'PROBABILISTIC', rounds: 10 })).label, 'Simulación al día');
  assert.match(scenarioStatusView(scenario('a', 'UP_TO_DATE', { latestEvaluatedAt: '2026-11-12T10:00:00Z', mode: 'PROBABILISTIC', rounds: 10 })).detail, /10 rondas/);
  assert.equal(scenarioStatusView(scenario('a', 'OUTDATED')).tone, 'bad');
  assert.equal(scenarioStatusView(scenario('a', 'NEVER_SIMULATED')).label, 'Sin simular');
  assert.equal(scenarioStatusView(scenario('a', 'SIMULATING', { job: { id: 'j', status: 'RUNNING', completedRounds: 4, rounds: 10, errorMessage: null } })).detail, '4 de 10 rondas');
  assert.match(scenarioStatusView(scenario('a', 'NEVER_SIMULATED', { job: { id: 'j', status: 'FAILED', completedRounds: 0, rounds: 10, errorMessage: 'x' } })).detail, /falló/);
});

test('the selection round-trips through the URL and the request keeps its order', () => {
  const refs = [{ projectId: 'p1', versionId: 'a' }, { projectId: 'p2', versionId: 'b' }];
  assert.deepEqual(selectionFromQuery(selectionToQuery(refs)), refs);
  assert.deepEqual(selectionFromQuery('p1:a,p1:a,broken,:x,p2:b'), refs);
  assert.deepEqual(selectionFromQuery(null), []);
  assert.deepEqual(toRequest(['b', 'a']), { items: [{ versionId: 'b' }, { versionId: 'a' }] });
});
