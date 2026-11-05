import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import ts from 'typescript';
const compile=file=>ts.transpileModule(readFileSync(new URL('../src/utils/'+file,import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const url=code=>'data:text/javascript;base64,'+Buffer.from(code).toString('base64');
const {csv,economicCsv,operationalCsv,summaryCsv}=await import(url(compile('resultsCsv.ts').replace("'./operationalResults'",JSON.stringify(url(compile('operationalResults.ts'))))));
const values={count:2,mean:10,min:0,max:20,p10:2,p50:10,p90:18};
const evaluation={id:'saved',createdAt:'2026-10-01',snapshot:{configuration:{startYear:2030,currency:'USD',conversions:[{nodeId:'a',metric:'OUTPUT_VOLUME',unit:'m³',factor:2}]},physicalNodes:[{id:'a',name:'Nodo, histórico'}],rawMetrics:[{nodeId:'a',period:1,input:0,output:10,exported:null,losses:0,operatingHours:8760,events:0},{nodeId:'b',period:1,output:999}],result:{npv:12.345,periods:[{year:2030,period:0,cashFlow:-12.345,discountedCashFlow:0}]}}};
test('CSV preserves decimals and missing values and escapes text safely',()=>{
 const text=csv([['A,"B"', '=SUM(A1)', 'line\nbreak', -12.345,0,null,NaN]]);
 assert.ok(text.startsWith('\uFEFF'));assert.ok(text.includes('"A,""B"""'));assert.ok(text.includes("' =SUM")===false);
 assert.ok(text.includes("'=SUM(A1)"));assert.ok(text.includes('"-12.345","0",,'));assert.ok(text.endsWith('\r\n'));
});
test('economic CSV uses saved annual values and identifies the selected round',()=>{
 const text=economicCsv(evaluation,3);assert.ok(text.includes('"saved","2026-10-01","3","USD","12.345"'));
 assert.ok(text.includes('"-12.345"'));assert.ok(text.includes('Flujo descontado'));
});
test('operational CSV exports only selected node with saved conversions and units',()=>{
 const text=operationalCsv(evaluation,'a',2);assert.ok(text.includes('Producción / salida (m³)'));
 assert.ok(text.includes('"Nodo, histórico","2031","1","0","20",'));assert.ok(!text.includes('999'));
 assert.equal(evaluation.snapshot.rawMetrics[0].output,10);
});
test('round summary CSV includes statistics, probabilities, statuses and all nodes',()=>{
 const payback={denominator:2,recoveredProbability:.5,recoveryPeriods:values,statuses:{RECOVERED:1},becomesNegativeAgainCount:0};
 const e=structuredClone(evaluation);e.snapshot.summary={rounds:2,npv:values,positiveNpvProbability:.5,irr:{...values,mean:.1},irrStatuses:{CALCULATED:2},simplePayback:payback,discountedPayback:payback,cashFlows:[{period:1,year:2031,values}],operational:[{nodeId:'a',period:1,metric:'output',values},{nodeId:'b',period:1,metric:'output',values}]};
 const text=summaryCsv(e);assert.ok(text.includes('"VAN positivo"'));assert.ok(text.includes('"CALCULATED"'));assert.ok(text.includes('"RECOVERED"'));
 assert.ok(text.includes('"b"'));assert.ok(text.includes('"m³","2","20","0","40","4","20","36"'));
 assert.ok(text.includes('"TIR anual"'));assert.ok(text.includes('"Flujo anual"'));
});
