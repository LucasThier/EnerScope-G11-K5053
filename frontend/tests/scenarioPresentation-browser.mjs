// Acceptance of separate scenario configuration and execution using isolated HTTP fixtures.
import assert from 'node:assert/strict';
import {readFile,mkdir} from 'node:fs/promises';
import {pathToFileURL,fileURLToPath} from 'node:url';
import ts from 'typescript';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const code=ts.transpileModule(await readFile(new URL('../src/utils/economicDraft.ts',import.meta.url),'utf8'),{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText;
const {emptyDraft,toConfiguration}=await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
const initial=toConfiguration({...emptyDraft(),startYear:'2030',years:'5',wacc:'10',entityName:'Operator',taxRate:'30'});
await mkdir(new URL('../node_modules/.cache/scenario-ui/',import.meta.url),{recursive:true});
const screenshot=name=>page.screenshot({path:fileURLToPath(new URL('../node_modules/.cache/scenario-ui/'+name+'.png',import.meta.url)),fullPage:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
const page=await context.newPage(), failures=[];
page.on('pageerror',e=>failures.push(e.message));
page.on('dialog',d=>d.accept());
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'Admin',platformRole:'ADMIN'};
await context.addInitScript(user=>{localStorage.setItem('sidebarPinned','true');localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));},user);
const project={id:'p1',name:'Project',organizationName:'Org',organizationId:'o1',memberCount:1,lastModified:'2030-01-01T00:00:00Z'};
const scenario={id:'v1',name:'Scenario',lastModified:project.lastModified,nodes:[{id:'n1',name:'Well',type:'WELL'}]};
const node={id:'n1',identity:'identity',name:'Well',type:'WELL',fields:[{name:'name',type:'text'},{name:'maxCollectionCapacity',type:'number'},{name:'declineCurve',type:'distribution'}],values:{name:'Well',maxCollectionCapacity:100,declineCurve:{type:'NORMAL',a:4,b:2}}};
let state={token:'1',sourceToken:'1',configuration:structuredClone(initial),nodes:[structuredClone(node)],draft:null,legacyDraft:null,errors:[]};
let copyState=null;
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
 await page.goto((process.env.APP_URL||'http://127.0.0.1:5173')+'/projects/p1/scenario-configurations');
 await page.getByRole('columnheader',{name:'Versiones Disponibles',exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Nuevo escenario',exact:true}).count(),0);
 await page.getByText('Seleccioná una versión disponible para editar la configuración económica y operativa del escenario a simular',{exact:true}).waitFor();
 await page.getByLabel('Buscar Versión').fill('absent');
 assert.equal(await page.getByRole('link',{name:'Configurar Escenario',exact:true}).count(),0);
 await page.getByLabel('Buscar Versión').fill('Scenario');
 await screenshot('available-versions');
 await page.getByRole('link',{name:'Configurar Escenario',exact:true}).click();
 await page.getByLabel('Año de inversión',{exact:true}).waitFor();
 const economy=page.getByRole('button',{name:'Economía',exact:true}),operation=page.getByRole('button',{name:'Operación',exact:true});
 assert.equal(await economy.getAttribute('aria-pressed'),'true');
 assert.match(await economy.getAttribute('class'),/bg-brand-800/);
 await operation.click();
 assert.equal(await operation.getAttribute('aria-pressed'),'true');
 assert.equal(await economy.getAttribute('aria-pressed'),'false');
 assert.match(await operation.getAttribute('class'),/bg-brand-800/);
 await page.getByLabel('Capacidad máxima de recolección',{exact:true}).waitFor();
 await screenshot('active-operation');
 await economy.focus();await page.keyboard.press('Enter');
 assert.equal(await economy.getAttribute('aria-pressed'),'true');
 await page.getByLabel('Año de inversión',{exact:true}).waitFor();
 await page.setViewportSize({width:390,height:844});await screenshot('active-economy-mobile');
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 assert.equal(saves,0);assert.equal(simulations,0);assert.deepEqual(failures,[]);
 console.log('PASS: existing versions only, exact copy, search, configuration link, active tabs, keyboard and mobile; no mutations.');
}finally{await browser.close();}
