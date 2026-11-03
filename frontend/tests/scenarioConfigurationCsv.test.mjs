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
const {configurationCsv,readConfigurationCsv,automaticNodeMapping,importConfiguration,configurationChanges,parseCsv}=load(path.join(directory,'scenarioConfigurationCsv.ts'));
const {emptyDraft}=load(path.join(directory,'economicDraft.ts'));
const nodes=[{id:'n1',identity:'stable1',name:'Pozo; "Norte"\nÁrea',type:'WELL',fields:[{name:'name',type:'text'},{name:'declineCurve',type:'distribution'},{name:'maxCollectionCapacity',type:'number'},{name:'startupDate',type:'instant'},{name:'state',type:'state'}]}];
const form=()=>({token:'revision',sourceToken:'source',configuration:null,editor:{mode:'simple',value:{...emptyDraft(),startYear:'2030',years:'5',currency:'USD',wacc:'10',entityName:'=Operadora',taxRate:'30',assets:[{id:'asset',concept:'Planta',nodeId:'n1',cost:'123,45',residual:'0',purchase:'2030-01-01',service:'2031-01-01',life:'60'}],rules:[{id:'rule',concept:'Venta',direction:'INCOME',driver:'OUTPUT_VOLUME',nodeId:'n1',value:'2,5',unit:'m3',factor:'1',from:'2031',to:'2035'}]}},operations:{n1:{name:nodes[0].name,declineCurve:{type:'NORMAL',a:'1,45',b:'0,1'},maxCollectionCapacity:100,startupDate:null,state:'RUNNING'}}});
const read=(f=form())=>readConfigurationCsv(configurationCsv(f,nodes));
const apply=(file)=>importConfiguration(file,nodes,automaticNodeMapping(file,nodes),form());

test('configuration CSV round trip preserves Spanish text, decimals, dates and distributions',()=>{
 const f=form(),text=configurationCsv(f,nodes);assert.ok(text.startsWith('\uFEFF'));assert.ok(text.includes("'=Operadora"));
 const result=apply(read(f));assert.equal(result.editor.value.entityName,'=Operadora');
 assert.equal(result.editor.value.assets[0].cost,'123,45');assert.equal(result.editor.value.assets[0].purchase,'2030-01-01');
 assert.deepEqual(result.operations.n1.declineCurve,f.operations.n1.declineCurve);assert.equal(result.operations.n1.name,nodes[0].name);
 assert.equal(result.configuration.wacc,0.1);assert.equal(result.configuration.assets[0].cost,123.45);
 assert.equal(configurationChanges(f,result,nodes).length,0);
});
test('configuration CSV parser rejects invalid headers, quotes, duplicate fields and oversized files',()=>{
 assert.throws(()=>readConfigurationCsv('a;b;c'),/Encabezado/);
 assert.throws(()=>parseCsv('"unclosed'),/comillas/);
 assert.throws(()=>parseCsv('"a"x;b'),/comillas/);
 const text=configurationCsv(form(),nodes),row=text.split('\r\n')[1];assert.throws(()=>readConfigurationCsv(text+row),/duplicado/);
 assert.throws(()=>parseCsv('a'.repeat(2_000_001)),/2 MB/);
});
test('configuration import requires complete sections, rejects unknown fields and invalid values',()=>{
 let file=read();file.rows=file.rows.filter(r=>r.field!=='wacc');assert.throws(()=>apply(file),/wacc.*falta/);
 file=read();file.rows.push({...file.rows[1],field:'unknown',line:999});assert.throws(()=>apply(file),/999.*desconocido/);
 file=read();file.rows.find(r=>r.field==='taxRate').value='101';assert.throws(()=>apply(file),/Impuestos/);
 file=read();file.rows.find(r=>r.field==='cost').value='1.234,5';assert.throws(()=>apply(file),/miles/);
 file=read();file.rows.find(r=>r.field==='state').value='REMOVED';assert.throws(()=>apply(file),/estado inválido/);
});
test('configuration import maps identities and supports manual mapping across projects without sharing IDs',()=>{
 const file=read(),target=[{...nodes[0],id:'other',identity:'different'}];
 assert.equal(automaticNodeMapping(file,target).n1,'');
 const result=importConfiguration(file,target,{n1:'other'},form());
 assert.equal(result.editor.value.assets[0].nodeId,'other');assert.ok(result.operations.other);assert.equal(result.operations.n1,undefined);
 assert.equal(result.configuration.nodeProfiles[0].nodeId,'other');
 assert.throws(()=>importConfiguration(file,[{...target[0],type:'PIPELINE'}],{n1:'other'},form()),/mismo tipo/);
 assert.throws(()=>importConfiguration(file,[],{},form()),/todos los nodos/);
 assert.throws(()=>importConfiguration(file,[...target,{...target[0],id:'extra'}],{n1:'other'},form()),/todos los nodos/);
});
test('configuration replacement removes absent financial collections and rejects advanced mode',()=>{
 const f=form();f.editor.value.assets=[];f.editor.value.rules=[];
 const result=apply(read(f));assert.deepEqual(result.editor.value.assets,[]);assert.deepEqual(result.editor.value.rules,[]);
 assert.ok(configurationChanges(form(),result,nodes).some(c=>c.field.includes('Planta')&&c.after==='—'));
 assert.throws(()=>configurationCsv({...f,editor:{mode:'advanced',value:{}}},nodes),/avanzada/);
});
test('configuration CSV handles both consumers and constant and uniform distributions',()=>{
 const f=form();f.operations={internal:{name:'Interno',consumptionSummer:12,consumptionAutumn:15,consumptionWinter:25,consumptionSpring:10},industrial:{name:'Industrial',consumption:30}};
 f.editor.value.rules=[];f.editor.value.assets=[];
 const consumers=Object.entries(f.operations).map(([id,values])=>({id,identity:id,type:id==='internal'?'INTERNAL_CONSUMPTION':'INDUSTRIAL_CONSUMPTION',name:values.name,fields:Object.keys(values).map(name=>({name,type:name==='name'?'text':'number'}))}));
 const file=readConfigurationCsv(configurationCsv(f,consumers));const result=importConfiguration(file,consumers,automaticNodeMapping(file,consumers),f);
 assert.equal(result.operations.internal.consumptionWinter,'25');assert.equal(result.operations.industrial.consumption,'30');
 for(const distribution of [{type:'CONSTANT',a:'1'},{type:'UNIFORM',a:'0',b:'2'}]){const v=form();v.operations.n1.declineCurve=distribution;assert.deepEqual(apply(read(v)).operations.n1.declineCurve,distribution);}
});
