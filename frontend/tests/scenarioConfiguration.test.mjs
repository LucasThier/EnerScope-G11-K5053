import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import ts from 'typescript';
const require=createRequire(import.meta.url);
function load(file) {
 const module={exports:{}};
 const code=ts.transpileModule(readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
 new Function('require','module','exports',code)(name=>name.startsWith('.')?load(path.resolve(path.dirname(file),name+'.ts')):require(name),module,module.exports);
 return module.exports;
}
const directory=fileURLToPath(new URL('../src/utils/',import.meta.url));
const {initialScenario,prepareScenario,scenarioConflict}=load(path.join(directory,'scenarioConfiguration.ts'));
const {emptyDraft,toConfiguration,createSimpleCopyDraft,fromConfiguration}=load(path.join(directory,'economicDraft.ts'));
const valid={...emptyDraft(),startYear:'2030',years:'5',wacc:'10',entityName:'Operator',taxRate:'30'};
const base=()=>({token:'1',sourceToken:'source',configuration:toConfiguration(valid),nodes:[{id:'n1',values:{name:'Well',maxCollectionCapacity:123}}],draft:null,legacyDraft:null,errors:[]});
test('new configuration reads shared operational values and preserves incomplete legacy drafts',()=>{
 const state=base();state.legacyDraft={schemaVersion:1,draft:{...valid,years:''}};
 const input=initialScenario(state);
 assert.equal(input.editor.value.years,'');assert.equal(input.operations.n1.maxCollectionCapacity,123);
 assert.equal(input.token,'1');assert.equal(input.sourceToken,'source');
});
test('joint draft keeps its original source token after map changes',()=>{
 const state=base();state.draft={...initialScenario(state),sourceToken:'old',token:'old'};
 const input=initialScenario(state);assert.equal(input.token,'1');assert.equal(input.sourceToken,'old');
 assert.equal(state.draft.token,'old');
});
test('complete simple saves publish economics while incomplete saves retain raw input only',()=>{
 const input=initialScenario(base());assert.ok(prepareScenario(input,[]).configuration);
 input.editor.value.years='';const pending=prepareScenario(input,[]);
 assert.equal(pending.configuration,null);assert.equal(pending.editor.value.years,'');
 assert.equal(pending.operations.n1.maxCollectionCapacity,123);
});
test('advanced saves preserve contracts and ownership without simple conversion',()=>{
 const state=base();state.configuration.taxEntities.push({...state.configuration.taxEntities[0],id:'second'});
 const input=initialScenario(state);assert.equal(input.editor.mode,'advanced');
 assert.deepEqual(prepareScenario(input,[]).configuration,state.configuration);
});
test('remote changes refresh clean forms and conflict with local or saved drafts',()=>{
 const old=base(),next={...old,token:'2'};
 assert.equal(scenarioConflict(next,old,false),false);
 assert.equal(scenarioConflict(next,old,true),true);
 assert.equal(scenarioConflict(next,{...old,draft:{}},false),true);
 assert.equal(scenarioConflict(old,old,true),false);
});

test('simple copy leaves the source untouched and produces a separate single-entity aggregate',()=>{
 const source=JSON.parse(readFileSync(new URL('../../backend/src/test/resources/economic-examples/economic-configuration.json',import.meta.url),'utf8'));
 const original=structuredClone(source),draft=createSimpleCopyDraft(source),copy=toConfiguration(draft);
 assert.deepEqual(source,original);assert.equal(copy.taxEntities.length,1);assert.equal(copy.contracts.length,0);
 assert.ok(fromConfiguration(copy));assert.equal(draft.assets.length,source.assets.length);
 assert.equal(new Set(draft.rules.map(r=>r.concept)).size,draft.rules.length);
 assert.ok(draft.rules.every(r=>!r.concept.includes('internal')));
 assert.ok(draft.rules.every(r=>!source.adjustments.some(old=>old.id===r.id)));
});
test('simple copy with different tax rates requires an explicit choice',()=>{
 const source=base().configuration;
 source.taxEntities.push({...source.taxEntities[0],id:'other',taxRate:.4});
 assert.equal(createSimpleCopyDraft(source).taxRate,'');
});

test('simple copy remaps physical references and preserves gaps in recognition years',()=>{
 const source=JSON.parse(readFileSync(new URL('../../backend/src/test/resources/economic-examples/economic-configuration.json',import.meta.url),'utf8'));
 const mapping=Object.fromEntries(source.conversions.map(c=>[c.nodeId,'copy-'+c.nodeId]));
 const draft=createSimpleCopyDraft(source,mapping);
 assert.ok(draft.assets.every(a=>!a.nodeId || a.nodeId.startsWith('copy-')));
 assert.ok(draft.rules.every(r=>!r.nodeId || r.nodeId.startsWith('copy-')));
 const sales=draft.rules.filter(r=>r.direction==='INCOME' && r.nodeId===mapping[source.contracts[0].deliveryNodeId] && r.value==='1');
 assert.deepEqual(sales.map(r=>[r.from,r.to]),[['2031','2031'],['2033','2035']]);
 assert.ok(!draft.rules.some(r=>r.concept==='working-capital'||r.concept==='grant'));
});
