import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/utils/summaryVisuals.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { plotScale, bandSegments, noDispersion, recoveryView } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const stats = (p10, p50 = p10, p90 = p50, count = 10) => ({ count, p10, p50, p90 });
const point = (period, values) => ({ period, label: String(2030 + period), values });
test('scale includes negative positive and zero values without clipping', () => {
  const scale = plotScale([-1080, 0, 491], true);
  assert.ok(scale.position(-1080) > 0); assert.ok(scale.position(491) < 1);
  assert.ok(scale.position(-1080) < scale.position(0)); assert.ok(scale.position(0) < scale.position(491));
});
test('scale stays finite for constant zero negative and extreme finite values', () => {
  for (const values of [[0], [-5], [5], [-1e308, 1e308]]) {
    const scale = plotScale(values);
    for (const value of values) assert.ok(Number.isFinite(scale.position(value)) && scale.position(value) > 0 && scale.position(value) < 1);
  }
});
test('absent or nonfinite values never become a zero-valued chart', () => {
  assert.equal(plotScale([null, undefined, NaN, Infinity], true), null);
  assert.ok(plotScale([0]));
  assert.deepEqual(bandSegments([point(0, stats(null))], ['p50']), []);
});
test('annual lines and bands split at missing values and omitted periods', () => {
  const points = [point(0,stats(-100)),point(1,stats(null)),point(2,stats(20)),point(4,stats(40))];
  assert.deepEqual(bandSegments(points,['p50']).map(s=>s.map(p=>p.period)),[[0],[2],[4]]);
  assert.deepEqual(bandSegments([point(1,stats(null,10,null))],['p50']).length,1);
  assert.deepEqual(bandSegments([point(1,stats(null,10,null))],['p10','p90']),[]);
});
test('series sorting preserves the saved input and the investment period', () => {
  const points=[point(1,stats(20)),point(0,stats(-100))];
  assert.deepEqual(bandSegments(points,['p50'])[0].map(p=>p.period),[0,1]);
  assert.equal(points[0].period,1);
});
test('dispersion distinguishes coincident percentiles one round and incomplete data', () => {
  assert.equal(noDispersion([point(0,stats(0,0,0,1)),point(1,stats(30,30,30,1))]),true);
  assert.equal(noDispersion([point(0,stats(-10,0,10))]),false);
  assert.equal(noDispersion([point(0,stats(null))]),false);
  assert.equal(noDispersion([]),false);
  assert.equal(noDispersion([point(0,stats(0,0,0,0))]),false);
});
test('payback uses determined applicable denominator and separate status counts', () => {
  const value={denominator:5,recoveredProbability:.4,recoveryPeriods:stats(3,4,5,2),statuses:{RECOVERED:2,NOT_RECOVERED_WITHIN_HORIZON:3,NOT_APPLICABLE:1,INSUFFICIENT_DATA:2,NUMERICAL_FAILURE:1},becomesNegativeAgainCount:1};
  assert.deepEqual(recoveryView(value,2030),{probability:.4,period:4,year:2034,notApplicable:1,undetermined:3});
});
test('no recovered rounds and no applicable rounds remain distinct from zero', () => {
  const value={denominator:5,recoveredProbability:0,recoveryPeriods:stats(null,null,null,0),statuses:{NOT_RECOVERED_WITHIN_HORIZON:5}};
  assert.equal(recoveryView(value).probability,0); assert.equal(recoveryView(value).period,null);
  assert.equal(recoveryView({...value,denominator:0}).probability,null);
});
test('missing investment year never invents a recovery calendar year', () => {
  const value={denominator:1,recoveredProbability:1,recoveryPeriods:stats(5,5,5,1),statuses:{RECOVERED:1}};
  assert.equal(recoveryView(value).year,null); assert.equal(recoveryView(value,2030).year,2035);
});
