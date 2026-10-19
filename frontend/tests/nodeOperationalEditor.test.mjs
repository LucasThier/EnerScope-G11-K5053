import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const require = createRequire(import.meta.url);
const cache = new Map();
function load(file) {
  if (cache.has(file)) return cache.get(file);
  const code = ts.transpileModule(readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  const resolve = name => {
    if (!name.startsWith('.')) return require(name);
    const base = path.resolve(path.dirname(file), name);
    const target = [base, base + '.ts', base + '.tsx'].find(existsSync);
    return load(target);
  };
  new Function('require', 'module', 'exports', code)(resolve, module, module.exports);
  cache.set(file, module.exports);
  return module.exports;
}
const directory = fileURLToPath(new URL('../src/components/editor/', import.meta.url));
const { NODE_TYPE_SPECS, defaultBaseValues, buildNodePayload } = load(path.join(directory, 'nodeCatalog.ts'));
const { NodeFormPanel } = load(path.join(directory, 'NodeFormPanel.tsx'));
const { NodeDataPanel } = load(path.join(directory, 'NodeDataPanel.tsx'));
const costs = /Upkeep cost|Operating cost|DTM cost|Treatment cost|Hiring cost/;
const noop = () => {};

test('node create and edit forms expose operational fields only for every node type', () => {
  for (const spec of NODE_TYPE_SPECS) {
    for (const mode of ['create', 'edit']) {
      const markup = renderToStaticMarkup(React.createElement(NodeFormPanel, {
        mode, busy: false, initialType: spec.type,
        ...(mode === 'edit' ? { initial: { type: spec.type, base: defaultBaseValues(), typeFields: {} } } : {}),
        onSubmit: noop, onClose: noop,
      }));
      assert.doesNotMatch(markup, costs);
      assert.match(markup, /Lifespan \(months\)/);
      assert.match(markup, /Simulaciones \/ Escenarios/);
      for (const field of spec.fields.filter(f => !f.economic)) assert.ok(markup.includes(field.label));
    }
  }
});
test('node detail panels omit legacy monetary fields while retaining physical data', () => {
  for (const spec of NODE_TYPE_SPECS) {
    const node = { id: 'n1', name: 'Node', state: 'RUNNING', type: { nodeType: spec.type, vertical: spec.vertical } };
    const detail = { ...node, ...defaultBaseValues(), attributes: Object.fromEntries(spec.fields.map(f => [f.key, 12])) };
    const markup = renderToStaticMarkup(React.createElement(NodeDataPanel, {
      node, detail, busy: false, onUpdateBasics: noop, onEditData: noop, onStartConnect: noop, onRestore: noop, onDelete: noop,
    }));
    assert.doesNotMatch(markup, costs);
    assert.match(markup, /Lifespan \(mo\)/);
  }
});
test('operational saves retain hidden legacy costs and complete subtype payloads', () => {
  for (const spec of NODE_TYPE_SPECS) {
    const base = { ...defaultBaseValues(), upkeepCosts: 123, operatingCosts: 456 };
    const fields = Object.fromEntries(spec.fields.map((f, i) => [f.key, i + 10]));
    const original = structuredClone({ base, fields });
    const payload = buildNodePayload(spec, { ...base, name: 'Renamed' }, fields, {}, 'identity');
    assert.equal(payload.upkeepCosts, 123);
    assert.equal(payload.operatingCosts, 456);
    for (const field of spec.fields) assert.equal(payload[field.key], fields[field.key]);
    assert.deepEqual({ base, fields }, original);
  }
});
