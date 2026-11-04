import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync} from 'node:fs';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import ts from 'typescript';
import React from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
const require=createRequire(import.meta.url),cache=new Map();
function load(file){
 if(cache.has(file))return cache.get(file);
 const code=ts.transpileModule(readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,target:ts.ScriptTarget.ES2022}}).outputText;
 const module={exports:{}};
 const resolve=name=>{if(!name.startsWith('.'))return require(name);const base=path.resolve(path.dirname(file),name);return load([base,base+'.ts',base+'.tsx'].find(existsSync));};
 new Function('require','module','exports',code)(resolve,module,module.exports);cache.set(file,module.exports);return module.exports;
}
const {validBand,numericDomain,bandSegments,recoveryProbability}=load(fileURLToPath(new URL('../src/utils/summaryVisuals.ts',import.meta.url)));
const {PercentileRange,AnnualBandChart,RecoveryBar,StatisticsDetails}=load(fileURLToPath(new URL('../src/components/economics/SummaryVisuals.tsx',import.meta.url)));
const stats=(low,mid,high)=>({count:10,mean:mid,min:low,max:high,p10:low,p50:mid,p90:high});
const point=(period,values)=>({period,label:String(2030+period),values});
const render=(component,props)=>renderToStaticMarkup(React.createElement(component,props));
const format=value=>value==null?'No disponible':String(value);
test('summary domains contain negative positive and zero values without degenerate scales',()=>{
 assert.deepEqual(numericDomain([-20,10]),[-20,10]);assert.deepEqual(numericDomain([5,20]),[0,20]);assert.deepEqual(numericDomain([-20,-5]),[-20,0]);
 assert.deepEqual(numericDomain([0,0]),[-1,1]);assert.deepEqual(numericDomain([null,NaN]),[0,1]);assert.ok(numericDomain([5,5],false)[0]<5);
});
test('summary bands preserve zero and reject absent unordered or empty statistics',()=>{
 assert.equal(validBand(stats(0,0,0)),true);
 for(const values of [{...stats(1,2,3),p50:null},{...stats(1,2,3),p90:Infinity},{...stats(1,2,3),count:0},stats(3,2,1)])assert.equal(validBand(values),false);
});
test('annual bands split missing years and missing values without mutating their input',()=>{
 const points=[point(4,stats(1,2,3)),point(0,stats(-5,-3,-1)),point(1,stats(1,2,3)),point(2,{...stats(1,2,3),p50:null}),point(6,stats(1,2,3))];
 const before=structuredClone(points);assert.deepEqual(bandSegments(points).map(s=>s.map(p=>p.period)),[[0,1],[4],[6]]);assert.deepEqual(points,before);
});
test('recovery probability distinguishes zero recovery from a zero denominator',()=>{
 assert.equal(recoveryProbability(10,0),0);assert.equal(recoveryProbability(0,0),null);assert.equal(recoveryProbability(0,1),null);assert.equal(recoveryProbability(4,.5),.5);
 for(const value of [null,NaN,Infinity,-1,2])assert.equal(recoveryProbability(10,value),null);
});
test('percentile ranges retain exact values and show no dispersion only when extrema coincide',()=>{
 let markup=render(PercentileRange,{label:'VAN',values:{...stats(10,10,10),count:1},format});assert.match(markup,/Sin dispersión observada/);assert.match(markup,/Muestra: 1 rondas/);
 markup=render(PercentileRange,{label:'TIR',values:{...stats(1,1,1),min:-2,max:4},format});assert.doesNotMatch(markup,/Sin dispersión observada/);
 assert.match(render(PercentileRange,{label:'VAN',values:stats(-50,0,100),format}),/P10 -50, P50 0, P90 100/);
});
test('annual chart supports a single point empty data and keyboard values',()=>{
 const markup=render(AnnualBandChart,{points:[point(0,{...stats(0,0,0),count:1})],label:'Flow',unit:'USD',format});
 assert.match(markup,/tabindex="0"/);assert.match(markup,/P10 0, P50 0, P90 0/);assert.doesNotMatch(markup,/NaN|Infinity/);
 assert.match(render(AnnualBandChart,{points:[],label:'Flow',unit:'USD',format}),/No hay datos disponibles/);
});
test('recovery meters expose their percentage and omit indeterminate meters',()=>{
 assert.match(render(RecoveryBar,{label:'Recovery',value:0}),/aria-valuenow="0"/);assert.match(render(RecoveryBar,{label:'Recovery',value:.75}),/aria-valuenow="75"/);
 assert.doesNotMatch(render(RecoveryBar,{label:'Recovery',value:null}),/role="meter"/);
});
test('statistical details are initially closed and retain their children',()=>{
 const markup=render(StatisticsDetails,{children:'Exact statistics'});assert.match(markup,/Exact statistics/);assert.doesNotMatch(markup,/<details[^>]*open/);
});
