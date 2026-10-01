import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/utils/operationalResults.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { operationalNodes, operationalRows, operationalTotal, operationalNumber, operationalYear } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const row = (nodeId, period, output) => ({ nodeId, period, output, input: 0, exported: 0, losses: 0, operatingHours: 8760, events: 0 });
const evaluation = { snapshot: { configuration: { startYear: 2030, years: 2 }, physicalNodes: [{ id: 'a', name: 'Nombre histórico' }], rawMetrics: [row('a', 2, 40), row('b', 1, 900), row('a', 1, 60)] } };
test('uses saved node names and falls back to identifiers without current scenario data', () => {
  assert.deepEqual(operationalNodes(evaluation), [{ id: 'b', name: 'Nodo b' }, { id: 'a', name: 'Nombre histórico' }]);
});
test('isolates node series and sorts periods without mutating snapshots', () => {
  const rows = operationalRows(evaluation, 'a');
  assert.deepEqual(rows.map(r => r.period), [1, 2]);
  assert.equal(operationalTotal(rows, 'output', 2), 100);
  assert.equal(evaluation.snapshot.rawMetrics[0].period, 2);
});
test('maps operational periods to the saved investment year', () => {
  assert.equal(operationalYear(evaluation, 1), '2031');
  assert.equal(operationalYear({ snapshot: {} }, 1), 'Año no disponible');
});
test('does not replace missing metrics with zero and preserves real zero', () => {
  assert.equal(operationalTotal([row('a', 1, 0)], 'output', 1), 0);
  for (const value of [null, undefined, NaN, Infinity]) {
    assert.equal(operationalNumber(value), 'No disponible');
    assert.equal(operationalTotal([row('a', 1, value)], 'output', 1), null);
  }
  assert.equal(operationalNumber(0), '0');
});
test('incomplete or duplicate annual periods cannot produce a horizon total', () => {
  assert.equal(operationalTotal([row('a', 1, 10)], 'output', 2), null);
  assert.equal(operationalTotal([row('a', 1, 10), row('a', 1, 20)], 'output', 2), null);
  assert.equal(operationalTotal([], 'output', 2), null);
});
test('legacy evaluations without raw metrics retain an empty operational view', () => {
  assert.deepEqual(operationalNodes({ snapshot: {} }), []);
  assert.deepEqual(operationalRows({ snapshot: { rawMetrics: null } }, 'a'), []);
});
