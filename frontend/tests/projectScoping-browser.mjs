import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const browser = await chromium.launch({channel:'chrome',headless:true});
try {
const context = await browser.newContext();
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'User',platformRole:'ADMIN'};
await context.addInitScript(user=>{
 localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));localStorage.setItem('lastProjectId',JSON.stringify('p1'));
},user);
const page=await context.newPage();
const errors=[];page.on('pageerror',e=>errors.push(e.message));
const projects=['p1','p2'].map((id,i)=>({id,name:i?'Prueba1':'Demo',organizationId:'o1',organizationName:'Org'}));
let created=false,submitted=null,delay=0,oldRequested=false;
await page.route('**/api/v1/**',async route=>{
 const path=new URL(route.request().url()).pathname.replace('/api/v1','');
 const ok=data=>route.fulfill({json:{success:true,data,message:'OK'}});
 if(path==='/auth/refresh')return ok({user,accessToken:'test',refreshToken:'test'});
 if(path==='/projects')return ok(projects);
 if(path==='/organizations')return ok([{id:'o1',name:'Org'}]);
 if(path.endsWith('/organizations/owned')||path.endsWith('/members'))return ok([]);
 if(path==='/projects/p1/versions'){oldRequested=true;if(delay)await new Promise(r=>setTimeout(r,delay));return ok([{id:'v1',name:'Demo version',nodes:[]}]);}
 if(path==='/projects/p2/versions')return ok(created?[{id:'v2',name:'Nuevo escenario',nodes:[]}]:[]);
 if(path==='/projects/p2/version'){submitted=route.request().postDataJSON();created=true;return ok({id:'v2',name:'Nuevo escenario'});}
 if(path.endsWith('/diagram'))return ok({versionId:path.split('/')[2],nodes:[],connections:[]});
 if(path.endsWith('/scenario-configuration'))return ok({token:'1',sourceToken:'1',configuration:null,nodes:[],draft:null,legacyDraft:null,errors:[]});
 if(path.endsWith('/configuration'))return route.fulfill({status:404,json:{success:false,message:'Economic configuration not found'}});
 if(path.endsWith('/draft'))return ok(null);
 if(path.endsWith('/evaluations')||path.endsWith('/evaluation-jobs'))return ok([]);
 throw new Error('Unexpected API '+path);
});
await page.goto((process.env.APP_URL || 'http://localhost:5173') + '/editor');
const project=page.locator('select').nth(1);
const version=page.locator('select').nth(2);
await page.getByRole('option',{name:'Demo version',exact:true}).waitFor({state:'attached'});
await project.selectOption('p2');
await page.getByRole('button',{name:'Prueba1',exact:true}).waitFor({state:'attached'});
assert.equal(await version.locator('option[value="v1"]').count(),0);
assert.equal(await page.getByRole('button',{name:'+ Add node',exact:true}).isDisabled(),true);
delay=500;oldRequested=false;
await page.getByRole('button',{name:'Prueba1',exact:true}).click();
await page.getByRole('listbox').getByRole('button',{name:/Demo/}).click();
await page.waitForFunction(()=>document.querySelector('select')!==null);
for(let i=0;!oldRequested && i<100;i++)await new Promise(r=>setTimeout(r,20));
assert.ok(oldRequested);
await page.getByRole('button',{name:'Demo',exact:true}).click();
await page.getByRole('listbox').getByRole('button',{name:/Prueba1/}).click();
await page.waitForTimeout(700);
assert.equal(await project.inputValue(),'p2');
assert.equal(await version.locator('option[value="v1"]').count(),0);
await page.getByRole('link',{name:'Configuración del Escenario',exact:true}).click();
await page.getByRole('button',{name:'Nuevo escenario',exact:true}).click();
assert.equal(await page.getByLabel('Versión de origen').locator('option').count(),1);
await page.getByLabel('Nombre del escenario').fill('Nuevo escenario');
await page.getByRole('button',{name:'Crear y configurar',exact:true}).click();
await page.waitForURL('**/projects/p2/versions/v2/economics/configuration');
assert.deepEqual(submitted,{name:'Nuevo escenario',parentVersion:null});
await page.getByRole('button',{name:'Guardar',exact:true}).first().waitFor({state:'attached'});
await page.getByRole('link',{name:'Configuración del Escenario',exact:true}).click();
await page.getByRole('link',{name:'Mapa de la Cadena de Valor',exact:true}).click();
await page.getByRole('option',{name:'Nuevo escenario',exact:true}).waitFor({state:'attached'});
assert.equal(await project.inputValue(),'p2');
assert.equal(await version.inputValue(),'v2');
assert.deepEqual(errors,[]);
console.log('PASS: project isolation, stale response, empty-project creation, configuration navigation and scoped map.');
} finally {await browser.close();}
