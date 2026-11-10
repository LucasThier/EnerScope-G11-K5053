import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/utils/advancedEconomicDraft.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText;
const { updateEconomicField } = await import('data:text/javascript;base64,' + Buffer.from(code).toString('base64'));
const fixture = JSON.parse(await readFile(new URL('../../backend/src/test/resources/economic-examples/economic-configuration.json', import.meta.url), 'utf8'));
test('advanced edits retain contracts, shared ownership and unrelated fiscal policies without mutating the input', () => {
  const original = structuredClone(fixture);
  const changed = updateEconomicField(fixture, ['taxEntities', 1, 'taxRate'], 0.25);
  const expected = structuredClone(fixture); expected.taxEntities[1].taxRate = 0.25;
  assert.deepEqual(changed, expected); assert.deepEqual(fixture, original);
});
test('advanced edits preserve deferred payment schedules and unknown fields', () => {
  const original = { ...structuredClone(fixture), futurePolicy: { enabled: true } };
  const changed = updateEconomicField(original, ['contracts', 0, 'revenueRule', 'unitValue'], 3);
  const expected = structuredClone(original); expected.contracts[0].revenueRule.unitValue = 3;
  assert.deepEqual(changed, expected);
});

test('renaming an investment retains its tax and depreciation treatments under the new concept', () => {
  const changed = updateEconomicField(fixture, ['assets', 0, 'concept'], 'New equipment');
  assert.equal(changed.assets[0].concept, 'New equipment');
  assert.deepEqual(changed.taxTreatments.filter(t => t.concept.startsWith('New equipment')), fixture.taxTreatments.filter(t => t.concept === fixture.assets[0].concept || t.concept === fixture.assets[0].concept + '.depreciation').map(t => ({ ...t, concept: t.concept.replace(fixture.assets[0].concept, 'New equipment') })));
});
