import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup as renderMarkup } from 'react-dom/server';

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
const { ThemeContext } = load(path.resolve(directory, '../../hooks/useTheme.ts'));
const renderToStaticMarkup = element => renderMarkup(React.createElement(ThemeContext.Provider, { value: { resolved: 'light', preference: 'light', system: 'light', setPreference: () => {} } }, element));
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
      assert.match(markup, /Configuración del Escenario/);
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
    for (const field of spec.fields) { if(field.key === 'declineCurve') assert.deepEqual(payload[field.key],{type:'CONSTANT',val_a:fields[field.key],val_b:null}); else assert.equal(payload[field.key], fields[field.key]); }
    assert.deepEqual({ base, fields }, original);
  }
});


test('new node numeric fields start blank except the maintenance default',()=>{
 for(const spec of NODE_TYPE_SPECS){
  const markup=renderToStaticMarkup(React.createElement(NodeFormPanel,{mode:'create',busy:false,initialType:spec.type,onSubmit:noop,onClose:noop}));
  const inputs=[...markup.matchAll(/<input[^>]*type="number"[^>]*>/g)].map(m=>m[0]);
  assert.ok(inputs.length>=2);assert.ok(inputs.every(input=>/value="(?:|30)"/.test(input)));
 }
});
test('editing a node preserves saved zero and nonzero numeric values',()=>{
 const markup=renderToStaticMarkup(React.createElement(NodeFormPanel,{mode:'edit',busy:false,initial:{type:'WELL',base:{...defaultBaseValues(),lifespanInMonths:120},typeFields:{maxCollectionCapacity:0,gasRichness:.25}},onSubmit:noop,onClose:noop}));
 assert.match(markup,/value="120"/);assert.match(markup,/value="0"/);assert.match(markup,/value="0.25"/);
});


test('distribution edits preserve parameters and hidden fields in the well payload',()=>{
 const spec=NODE_TYPE_SPECS.find(s=>s.type==='WELL');
 for(const distribution of [{type:'CONSTANT',val_a:1.45,val_b:null},{type:'NORMAL',val_a:2,val_b:.5},{type:'UNIFORM',val_a:1,val_b:3}]){
  const base={...defaultBaseValues(),declineCurve:distribution};
  const payload=buildNodePayload(spec,base,{dtmCost:17},{},'identity');
  assert.deepEqual(payload.declineCurve,distribution);assert.equal(payload.dtmCost,17);assert.equal(payload.identity,'identity');
  const markup=renderToStaticMarkup(React.createElement(NodeFormPanel,{mode:'edit',busy:false,initial:{type:'WELL',base,typeFields:{}},onSubmit:noop,onClose:noop}));
  assert.match(markup,new RegExp('value="'+distribution.type+'" selected=""'));assert.ok(markup.includes('value="'+distribution.val_a+'"'));
 }
});


test('consumers accept treated gas only and remain terminal nodes', () => {
  const { canConnect } = load(path.join(directory, 'nodeCatalog.ts'));
  for (const type of ['INTERNAL_CONSUMPTION', 'INDUSTRIAL_CONSUMPTION']) {
    const spec = NODE_TYPE_SPECS.find(s => s.type === type);
    assert.equal(spec.role, 'SINK');
    assert.equal(spec.vertical, 'INTERNAL_CONSUMPTION');
    const values = Object.fromEntries(spec.fields.map((f, i) => [f.key, i + 0.5]));
    const payload = buildNodePayload(spec, defaultBaseValues(), values, {});
    for (const [key, value] of Object.entries(values)) assert.equal(payload[key], value);
    for (const candidate of NODE_TYPE_SPECS) {
      assert.equal(canConnect(candidate.type, type), ['TREATMENT_PLANT', 'PIPELINE', 'COMPRESSING_PLANT'].includes(candidate.type));
      assert.equal(canConnect(type, candidate.type), false);
    }
  }
});


test('scenario consumer fields use Spanish labels and retain zero demand', () => {
 const { ScenarioOperationsEditor } = load(path.resolve(directory, '../economics/ScenarioOperationsEditor.tsx'));
 const labels = {consumptionSummer:'Consumo de verano',consumptionAutumn:'Consumo de otoño',consumptionWinter:'Consumo de invierno',consumptionSpring:'Consumo de primavera',consumption:'Consumo constante'};
 for (const type of ['INTERNAL_CONSUMPTION','INDUSTRIAL_CONSUMPTION']) {
  const spec=NODE_TYPE_SPECS.find(s=>s.type===type);
  const values=Object.fromEntries(spec.fields.map(f=>[f.key,0]));
  const markup=renderToStaticMarkup(React.createElement(ScenarioOperationsEditor, {
   nodes:[{id:'consumer',name:'Consumer',type,fields:spec.fields.map(f=>({name:f.key,type:'number'})),values}],
   values:{consumer:values},onChange:noop,disabled:false,
  }));
  for(const f of spec.fields) assert.ok(markup.includes(labels[f.key]));
  assert.ok(markup.includes('value="0"'));
 }
});
