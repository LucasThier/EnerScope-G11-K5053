import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
const code=ts.transpileModule(readFileSync(new URL('../src/utils/visibleDiagram.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext}}).outputText;
const {visibleDiagram}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));

test('removed nodes and touching edges disappear without changing saved history',()=>{
 const nodes=['RUNNING','REMOVED','PROPOSED','PENDING'].map((state,i)=>({id:String(i),state}));
 const connections=[['0','1'],['1','2'],['0','2'],['2','3'],['0','missing']].map(([fromNodeId,toNodeId],i)=>({id:String(i),fromNodeId,toNodeId}));
 const original={versionId:'v',nodes,connections};const before=structuredClone(original);
 const visible=visibleDiagram(original);
 assert.deepEqual(visible.nodes.map(n=>n.id),['0','2','3']);
 assert.deepEqual(visible.connections.map(e=>e.id),['2','3']);
 assert.deepEqual(original,before);
 assert.deepEqual(visibleDiagram(visible),visible);
});
test('empty diagrams and restored nodes render consistently',()=>{
 assert.deepEqual(visibleDiagram({versionId:'v',nodes:[],connections:[]}),{versionId:'v',nodes:[],connections:[]});
 for(const state of ['REMOVED','PROPOSED']) {
  const visible=visibleDiagram({versionId:'v',nodes:[{id:'a',state}],connections:[]});
  assert.equal(visible.nodes.length,state==='REMOVED'?0:1);
 }
});
