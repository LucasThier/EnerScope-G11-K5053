import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/utils/economicChart.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { cashFlowScale, recoveryMarkers } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
test('cash flow scale contains mixed, positive, negative and zero flows with a finite baseline', () => {
 for (const values of [[-1500000,650000], [500,100], [-300,-10], [0,0], []]) {
   const scale = cashFlowScale(values);
   assert.ok(Number.isFinite(scale.zero));
   assert.ok(scale.ticks.some(v => Math.abs(v) < 1e-8));
   for (const value of values) { assert.ok(scale.y(value) >= 24); assert.ok(scale.y(value) <= 256); }
 }
});
test('recovery timeline merges coincident recoveries and excludes unavailable or out of horizon markers', () => {
 const recovered = { status:'RECOVERED', period:3, year:2033 };
 assert.deepEqual(recoveryMarkers(recovered, recovered, 10), [{period:3,label:'Simple y descontada'}]);
 assert.equal(recoveryMarkers(recovered, {...recovered,period:4},5).length,2);
 assert.deepEqual(recoveryMarkers({...recovered,status:'NOT_RECOVERED_WITHIN_HORIZON'}, {...recovered,period:11},10),[]);
 assert.deepEqual(recoveryMarkers(undefined,undefined,5),[]);
});
