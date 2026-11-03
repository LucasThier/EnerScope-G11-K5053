// Acceptance of separate scenario configuration and execution using isolated HTTP fixtures.
import assert from 'node:assert/strict';
import {readFile,mkdir} from 'node:fs/promises';
import {pathToFileURL,fileURLToPath} from 'node:url';
import ts from 'typescript';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require=createRequire(import.meta.url);
function load(file){const module={exports:{}};const code=ts.transpileModule(readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;new Function('require','module','exports',code)(name=>name.startsWith('.')?load(path.resolve(path.dirname(file),name+'.ts')):require(name),module,module.exports);return module.exports;}
const {configurationCsv}=load(fileURLToPath(new URL('../src/utils/scenarioConfigurationCsv.ts',import.meta.url)));
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const code=ts.transpileModule(await readFile(new URL('../src/utils/economicDraft.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {emptyDraft,toConfiguration}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
const initial=toConfiguration({...emptyDraft(),startYear:'2030',years:'5',wacc:'10',entityName:'Operator',taxRate:'30'});
await mkdir(new URL('../node_modules/.cache/scenario-ui/',import.meta.url),{recursive:true});
const screenshot=name=>page.screenshot({path:fileURLToPath(new URL('../node_modules/.cache/scenario-ui/'+name+'.png',import.meta.url)),fullPage:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1000}});
const page=await context.newPage(), failures=[];
page.on('pageerror',e=>failures.push(e.message));
let acceptDialog=true;page.on('dialog',d=>acceptDialog?d.accept():d.dismiss());
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'Admin',platformRole:'ADMIN'};
await context.addInitScript(user=>{localStorage.setItem('sidebarPinned','true');localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));},user);
const project={id:'p1',name:'Project',organizationName:'Org',organizationId:'o1',memberCount:1,lastModified:'2030-01-01T00:00:00Z'};
const scenario={id:'v1',name:'Scenario',lastModified:project.lastModified,nodes:[{id:'n1',name:'Well',type:'WELL'}]};
const node={id:'n1',identity:'identity',name:'Well',type:'WELL',fields:[{name:'name',type:'text'},{name:'maxCollectionCapacity',type:'number'},{name:'declineCurve',type:'distribution'}],values:{name:'Well',maxCollectionCapacity:100,declineCurve:{type:'NORMAL',a:4,b:2}}};
let state={token:'1',sourceToken:'1',configuration:structuredClone(initial),nodes:[structuredClone(node)],draft:null,legacyDraft:null,errors:[]};
let copyState=null;
let validations=0,validationErrors=[];
let saves=0,simulations=0,rejectSave=false,lastSave,lastExecution;
function bump(){state.token=String(Number(state.token)+1);state.sourceToken=state.token;}
await page.route('**/api/v1/**',async route=>{
 const p=new URL(route.request().url()).pathname.replace('/api/v1',''),method=route.request().method();
 const ok=data=>route.fulfill({json:{success:true,data}});
 if(p==='/auth/refresh')return ok({user,accessToken:'test',refreshToken:'test'});
 if(p==='/projects')return ok([project]);
 if(p==='/organizations'||p.endsWith('/owned'))return ok([]);
 if(p==='/projects/p1')return ok(project);
 if(p.endsWith('/members'))return ok([]);
 if(p==='/projects/p1/versions')return ok([scenario,...(copyState?[{...scenario,id:'v2',name:'Scenario · copia simple'}]:[])]);
 if(p==='/projects/p1/version' && method==='POST'){assert.equal(route.request().postDataJSON().parentVersion,'v1');copyState={...structuredClone(state),configuration:null,token:'copy',sourceToken:'copy',nodes:state.nodes.map(n=>({...structuredClone(n),id:'copy-'+n.id}))};return ok({id:'v2'});}
 if(p==='/projects/p1/versions/v2/scenario-configuration'){
  if(method==='PUT'){lastSave=route.request().postDataJSON();copyState.configuration=lastSave.configuration;copyState.draft=lastSave.configuration?null:lastSave;saves++;}
  return ok(copyState);
 }
 if(p.endsWith('/simulation-readiness'))return ok([{versionId:'v1',ready:!state.draft,errors:state.draft?['Hay un borrador pendiente.']:[]}]);
 if(p.endsWith('/scenario-configuration/draft')){state.draft=null;state.errors=[];bump();return ok(state);}
 if(p.endsWith('/scenario-configuration/validate')){validations++;return ok({errors:validationErrors});}
 if(p.endsWith('/scenario-configuration')){
  if(method==='GET')return ok(state);
  saves++;lastSave=route.request().postDataJSON();
  if(rejectSave)return route.fulfill({status:500,json:{success:false,message:'Save failed'}});
  if(lastSave.token!==state.token)return route.fulfill({status:409,json:{success:false,message:'Concurrent change'}});
  if(!lastSave.configuration){state.draft=lastSave;state.token=String(Number(state.token)+1);state.errors=['Completá los datos pendientes.'];}
  else {state.configuration=lastSave.configuration;state.nodes[0].values=lastSave.operations.n1;state.draft=null;state.errors=[];bump();}
  return ok(state);
 }
 if(p.endsWith('/evaluation-jobs')){
  if(method==='POST'){simulations++;lastExecution=route.request().postDataJSON();await new Promise(r=>setTimeout(r,200));return ok({id:'j1',status:'QUEUED',seed:lastExecution.seed||'42'});}
  return ok([]);
 }
 if(p.endsWith('/evaluations'))return ok([]);
 return ok(null);
});
const base=(process.env.APP_URL||'http://127.0.0.1:5173')+'/projects/p1/versions/v1/economics/configuration';

try {
 await page.goto(base);await page.getByLabel('Año de inversión',{exact:true}).waitFor();
 const imported={token:'1',sourceToken:'1',configuration:null,editor:{mode:'simple',value:{...emptyDraft(),startYear:'2030',years:'10',wacc:'12',entityName:'Importada',taxRate:'30'}},operations:{n1:{...node.values,maxCollectionCapacity:250}}};
 const upload=async content=>page.getByLabel('Archivo CSV de configuración').setInputFiles({name:'configuracion.csv',mimeType:'text/csv',buffer:Buffer.from(content)});
 const csv=configurationCsv(imported,[node]);
 await upload(csv);await page.getByRole('button',{name:'Validar CSV',exact:true}).click();
 await page.getByRole('button',{name:'Cargar en el formulario',exact:true}).waitFor();
 assert.equal(saves,0);assert.equal(simulations,0);assert.equal(validations,1);
 await screenshot('csv-preview');
 await page.getByRole('button',{name:'Cancelar importación'}).click();
 assert.equal(await page.getByLabel('Años operativos',{exact:true}).inputValue(),'5');
 await page.getByLabel('WACC anual (%)',{exact:true}).fill('11');
 await upload(csv);await page.getByRole('button',{name:'Validar CSV',exact:true}).click();await page.getByRole('button',{name:'Cargar en el formulario',exact:true}).waitFor();
 acceptDialog=false;await page.getByRole('button',{name:'Cargar en el formulario',exact:true}).click();
 assert.equal(await page.getByLabel('WACC anual (%)',{exact:true}).inputValue(),'11');
 acceptDialog=true;await page.getByRole('button',{name:'Cargar en el formulario',exact:true}).click();
 assert.equal(await page.getByLabel('Años operativos',{exact:true}).inputValue(),'10');assert.equal(saves,0);
 await page.getByRole('button',{name:'Guardar',exact:true}).first().click();
 await page.getByText('Configuración económica y operativa guardada.',{exact:false}).waitFor();assert.equal(saves,1);assert.equal(simulations,0);
 assert.equal(lastSave.configuration.years,10);assert.equal(lastSave.operations.n1.maxCollectionCapacity,'250');
 await page.reload();await page.getByLabel('Años operativos',{exact:true}).waitFor();assert.equal(await page.getByLabel('Años operativos',{exact:true}).inputValue(),'10');
 await page.getByRole('button',{name:'Operación',exact:true}).click();assert.equal(await page.getByLabel('Capacidad máxima de recolección',{exact:true}).inputValue(),'250');
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'Exportar configuración CSV',exact:true}).click();assert.equal((await download).suggestedFilename(),'configuracion-escenario-v1.csv');
 validationErrors=[{nodeId:'n1',field:'maxCollectionCapacity',message:'Valor operativo inválido.'}];
 await upload(csv);await page.getByRole('button',{name:'Validar CSV',exact:true}).click();await page.getByText(/Valor operativo inválido/).waitFor();assert.equal(await page.getByRole('button',{name:'Cargar en el formulario',exact:true}).count(),0);assert.equal(saves,1);
 await page.getByRole('button',{name:'Cancelar importación'}).click();
 await upload('bad;header');await page.getByText(/Encabezado inválido/).waitFor();assert.equal(saves,1);
 await page.setViewportSize({width:390,height:844});await screenshot('csv-mobile');
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));
 assert.deepEqual(failures,[]);console.log('PASS CSV preview, cancellation, local replacement confirmation, explicit save/reload, download, invalid files, server validation and mobile layout');
} finally {await browser.close();}
