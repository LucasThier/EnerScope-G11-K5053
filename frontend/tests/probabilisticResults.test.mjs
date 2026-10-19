import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/utils/probabilisticResults.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { jobPending, executionError, roundEvaluation } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
test('execution accepts default range and preserves 64-bit seed strings', () => {
  for (const seed of ['', '-9223372036854775808', '9223372036854775807', '0'])
    assert.equal(executionError('10', seed), null);
  assert.equal(executionError('1', ''), null); assert.equal(executionError('100', ''), null);
});
test('execution rejects invalid round counts and unsafe seed syntax', () => {
  for (const rounds of ['0','101','1.5','abc','']) assert.ok(executionError(rounds,''));
  for (const seed of ['9223372036854775808','-9223372036854775809','1e3','1.1']) assert.ok(executionError('10',seed));
});
test('only unfinished jobs are polled', () => {
  for (const status of ['QUEUED','RUNNING']) assert.equal(jobPending({status}),true);
  for (const status of ['COMPLETED','FAILED','INTERRUPTED']) assert.equal(jobPending({status}),false);
});
test('round selection keeps immutable historical metadata and replaces both result tabs together', () => {
  const original={id:'x',snapshot:{schemaVersion:3,configuration:{wacc:.1},physicalNodes:[{id:'a',name:'Historical'}],result:null}};
  const round={round:2,rawMetrics:[{nodeId:'a',output:20}],result:{npv:123}};
  const selected=roundEvaluation(original,round);
  assert.equal(selected.snapshot.rawMetrics,round.rawMetrics);
  assert.equal(selected.snapshot.result,round.result);
  assert.equal(selected.snapshot.configuration,original.snapshot.configuration);
  assert.equal(original.snapshot.result,null);
});
