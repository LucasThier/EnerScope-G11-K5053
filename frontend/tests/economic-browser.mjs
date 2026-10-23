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
const context=await browser.newContext({viewport:{width:1440,height:1000}});
const page=await context.newPage(), failures=[];
page.on('pageerror',e=>failures.push(e.message));
page.on('dialog',d=>d.accept());
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'Admin',platformRole:'ADMIN'};
await context.addInitScript(user=>{localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));},user);
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
 await page.goto(base);
 await page.getByLabel('Año de inversión',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Guardar y simular',exact:true}).count(),0);
 assert.equal(await page.getByLabel('Rondas (1–100)',{exact:true}).count(),0);
 assert.equal(await page.locator('aside').getByRole('link',{name:'Configuración del Escenario',exact:true}).getAttribute('aria-current'),'page');
 await screenshot('configuration');
 await page.getByRole('button',{name:'Operación',exact:true}).click();
 await screenshot('operation');
 await page.getByLabel('Capacidad máxima de recolección',{exact:true}).fill('250');
 await page.getByRole('button',{name:'Guardar',exact:true}).first().click();
 await page.getByText('Configuración económica y operativa guardada.',{exact:false}).waitFor();
 assert.equal(lastSave.operations.n1.maxCollectionCapacity,'250');assert.equal(simulations,0);
 assert.deepEqual(lastSave.operations.n1.declineCurve,{type:'NORMAL',a:4,b:2});
 state.nodes[0].values.maxCollectionCapacity=333;bump();
 await page.evaluate(()=>window.dispatchEvent(new Event('focus')));
 await page.waitForFunction(()=>[...document.querySelectorAll('input')].some(i=>i.value==='333'));
 await page.getByLabel('Capacidad máxima de recolección',{exact:true}).fill('444');
 state.nodes[0].values.maxCollectionCapacity=555;bump();
 await page.evaluate(()=>window.dispatchEvent(new Event('focus')));
 await page.getByText('El escenario cambió desde otra pantalla.',{exact:false}).waitFor();
 assert.equal(await page.getByLabel('Capacidad máxima de recolección',{exact:true}).inputValue(),'444');
 assert.equal(await page.getByRole('button',{name:'Guardar',exact:true}).first().isDisabled(),true);
 await page.getByRole('button',{name:'Descartar y recargar',exact:true}).click();
 await page.waitForFunction(()=>[...document.querySelectorAll('input')].some(i=>i.value==='555'));
 await page.getByRole('button',{name:'Economía',exact:true}).click();
 await page.getByLabel('Año de inversión',{exact:true}).fill('');
 await page.getByRole('button',{name:'Guardar',exact:true}).first().click();
 await page.getByText('Borrador guardado.',{exact:false}).waitFor();
 await page.reload();await page.getByLabel('Año de inversión',{exact:true}).waitFor();
 assert.equal(await page.getByLabel('Año de inversión',{exact:true}).inputValue(),'');
 await page.getByRole('link',{name:'Simulación de Escenarios',exact:true}).click();
 assert.equal(await page.getByRole('button',{name:'Simular',exact:true}).isDisabled(),true);
 assert.equal(await page.getByRole('button',{name:'Nuevo escenario',exact:true}).count(),0);
 await page.goto(base);
 await page.getByRole('button',{name:'Descartar cambios',exact:true}).first().click();
 await page.getByLabel('Año de inversión',{exact:true}).fill('2031');
 rejectSave=true;await page.getByRole('button',{name:'Guardar',exact:true}).first().click();
 await page.getByText('Save failed',{exact:false}).waitFor();
 assert.equal(await page.getByLabel('Año de inversión',{exact:true}).inputValue(),'2031');
 rejectSave=false;
 await page.getByRole('link',{name:'Simulación de Escenarios',exact:true}).click();
 await page.getByRole('button',{name:'Seguir editando',exact:true}).click();
 await page.getByRole('button',{name:'Descartar cambios',exact:true}).first().click();
 await page.getByRole('link',{name:'Simulación de Escenarios',exact:true}).click();
 await page.getByRole('button',{name:'Simular',exact:true}).click();
 await page.getByRole('dialog').waitFor();
 assert.equal(await page.locator('aside').getByRole('link',{name:'Simulación de Escenarios',exact:true}).getAttribute('aria-current'),'page');
 await screenshot('simulation-dialog');
 assert.equal(await page.getByLabel('Rondas (1–100)',{exact:true}).inputValue(),'10');
 await page.getByRole('button',{name:'Cancelar',exact:true}).click();
 assert.equal(simulations,0);
 await page.getByRole('button',{name:'Simular',exact:true}).click();
 await page.getByLabel('Semilla (opcional)',{exact:true}).fill('9223372036854775808');
 await page.getByRole('dialog').getByRole('button',{name:'Simular',exact:true}).click();
 assert.equal(simulations,0);
 await page.getByLabel('Semilla (opcional)',{exact:true}).fill('9223372036854775807');
 const before=saves;
 await page.getByRole('dialog').getByRole('button',{name:'Simular',exact:true}).click();
 await page.waitForURL('**/economics/history');
 assert.equal(simulations,1);assert.equal(saves,before);assert.equal(lastExecution.seed,'9223372036854775807');
 const advanced=JSON.parse(await readFile(new URL('../../backend/src/test/resources/economic-examples/economic-configuration.json',import.meta.url),'utf8'));
 state.configuration=structuredClone(advanced);state.nodes=advanced.conversions.map(c=>({...structuredClone(node),id:c.nodeId,identity:c.nodeId}));bump();
 await page.goto(base);await page.getByRole('heading',{name:'Configuración original conservada',exact:true}).waitFor();
 assert.equal(await page.getByLabel('WACC anual (%)',{exact:true}).count(),0);
 await page.getByRole('button',{name:'Crear copia simple',exact:true}).click();
 await page.waitForURL('**/versions/v2/economics/configuration');
 await page.getByLabel('WACC anual (%)',{exact:true}).waitFor();
 assert.equal(copyState.configuration.taxEntities.length,1);assert.equal(copyState.configuration.contracts.length,0);
 assert.deepEqual(state.configuration,advanced);
 assert.equal(await page.getByRole('button',{name:'Seguir editando',exact:true}).count(),0);
 await page.setViewportSize({width:390,height:844});
 await page.waitForTimeout(350);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await screenshot('configuration-mobile');
 user.platformRole='USER';
 await page.reload();await page.getByRole('button',{name:'Guardar',exact:true}).first().waitFor();
 assert.equal(await page.getByRole('button',{name:'Guardar',exact:true}).first().isDisabled(),true);
 assert.deepEqual(failures,[]);
 console.log('PASS: separate screens, joint save, map refresh/conflict, drafts, navigation, failures, rounds/seed modal, single submission, original preservation and simple copy, read-only permissions and mobile layout.');
} catch (error) { console.error((await page.locator('body').innerText()).slice(0,6000)); console.error(failures); throw error; } finally {await browser.close();}
