import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true});
try {
 const context=await browser.newContext();
 const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'User',platformRole:'ADMIN'};
 await context.addInitScript(user=>{localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));localStorage.setItem('lastProjectId',JSON.stringify('p1'));},user);
 const page=await context.newPage(); let projectPayload,nodePayload;
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname.replace('/api/v1','');
  const ok=data=>route.fulfill({json:{success:true,data,message:'OK'}});
  if(path==='/auth/refresh')return ok({user,accessToken:'test',refreshToken:'test'});
  if(path==='/projects' && route.request().method()==='POST'){projectPayload=route.request().postDataJSON();return ok({id:'p2',...projectPayload});}
  if(path==='/projects')return ok([{id:'p1',name:'Demo',organizationId:'o1',organizationName:'Org'}]);
  if(path==='/organizations')return ok([{id:'o1',name:'Org'}]);
  if(path.endsWith('/owned')||path.endsWith('/members'))return ok([]);
  if(path.endsWith('/versions'))return ok([{id:'v1',name:'Scenario',nodes:[]}]);
  if(path.endsWith('/diagram'))return ok({versionId:'v1',nodes:nodePayload?[{id:'n1',identity:'identity',...nodePayload}]:[],connections:[]});
  if(path==='/version/v1/node/n1'){
   if(route.request().method()==='PATCH'){nodePayload=route.request().postDataJSON();return ok({id:'n1',identity:'identity',...nodePayload});}
   return ok({id:'n1',identity:'identity',...nodePayload,attributes:{maxCollectionCapacity:nodePayload.maxCollectionCapacity,gasRichness:nodePayload.gasRichness,dtmTime:nodePayload.dtmTime,dtmCost:nodePayload.dtmCost,surface:nodePayload.surface}});
  }
  if(path==='/version/v1/node'){nodePayload=route.request().postDataJSON();return ok({id:'n1',...nodePayload});}
  throw new Error('Unexpected API '+path);
 });
 await page.goto('http://localhost:5173/projects');
 await page.getByRole('button',{name:'Nuevo proyecto',exact:true}).click();
 await page.getByLabel('Nombre del proyecto').fill('Optional description');
 await page.getByLabel('Organización',{exact:true}).selectOption('o1');
 assert.equal(await page.getByLabel('Descripción (opcional)').inputValue(),'');
 await page.getByRole('button',{name:'Crear proyecto',exact:true}).click();
 await page.getByRole('dialog').waitFor({state:'hidden'});
 assert.equal(projectPayload.description,'');
 await page.goto('http://localhost:5173/editor');
 await page.getByRole('option',{name:'Scenario',exact:true}).waitFor({state:'attached'});
 await page.getByRole('button',{name:'+ Add node',exact:true}).click();
 const lifespan=page.getByLabel('Lifespan (months)',{exact:true});
 assert.equal(await lifespan.inputValue(),'');
 const capacity=page.getByLabel('Max collection capacity',{exact:true});
 assert.equal(await capacity.inputValue(),'');
 await capacity.pressSequentially('123.5');assert.equal(await capacity.inputValue(),'123.5');
 await capacity.press('ControlOrMeta+A');await capacity.press('Backspace');assert.equal(await capacity.inputValue(),'');
 await capacity.pressSequentially('42.5');
 await lifespan.fill('120');await lifespan.press('ControlOrMeta+A');await lifespan.press('Backspace');assert.equal(await lifespan.inputValue(),'');await lifespan.pressSequentially('60');
 await page.getByLabel('Gas richness (0-1)',{exact:true}).pressSequentially('0.25');
 await page.getByLabel('Declinación anual (puntos porcentuales)',{exact:true}).fill('1.45');
 await page.getByLabel('Name',{exact:true}).fill('Keyboard test');
 await page.getByRole('button',{name:'Create node',exact:true}).click();
 await page.getByRole('button',{name:'+ Add node',exact:true}).waitFor();
 assert.equal(nodePayload.maxCollectionCapacity,42.5);assert.equal(nodePayload.lifespanInMonths,60);assert.equal(nodePayload.gasRichness,.25);
 assert.deepEqual(nodePayload.declineCurve,{type:'CONSTANT',val_a:1.45,val_b:null});
 for(const distribution of [{type:'NORMAL',val_a:2,val_b:.5},{type:'UNIFORM',val_a:1,val_b:3}]){
  await page.locator('.react-flow__node').click(); await page.getByRole('button',{name:'Edit',exact:true}).click();
  await page.getByLabel('Distribución de declinación',{exact:true}).selectOption(distribution.type);
  const first=page.getByLabel(distribution.type==='NORMAL'?'Media anual (puntos porcentuales)':'Mínimo anual (puntos porcentuales)',{exact:true});
  const second=page.getByLabel(distribution.type==='NORMAL'?'Desviación estándar':'Máximo anual (puntos porcentuales)',{exact:true});
  await first.fill(String(distribution.val_a));await second.fill('0');
  assert.equal(await page.getByRole('button',{name:'Save node',exact:true}).isDisabled(),true);
  await second.fill(String(distribution.val_b));
  await page.getByRole('button',{name:'Save node',exact:true}).click();
  await page.getByRole('button',{name:'Save node',exact:true}).waitFor({state:'hidden'});
  assert.deepEqual(nodePayload.declineCurve,distribution);
  await page.locator('.react-flow__node').click(); await page.getByRole('button',{name:'Edit',exact:true}).click();
  assert.equal(await page.getByLabel('Distribución de declinación',{exact:true}).inputValue(),distribution.type);
  assert.equal(await first.inputValue(),String(distribution.val_a));assert.equal(await second.inputValue(),String(distribution.val_b));
  await page.getByRole('button',{name:'Close',exact:true}).click();
 }
 console.log('PASS: distribution creation, validation, editing and reopening; optional project description, empty new numeric fields, keyboard clearing/decimals and numeric submission.');
} finally {await browser.close();}
