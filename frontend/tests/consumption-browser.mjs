import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const browser=await chromium.launch({channel:'chrome',headless:true,args:['--enable-unsafe-swiftshader']});
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
  if(path==='/version/v1/node/n1/basics'){nodePayload={...nodePayload,...route.request().postDataJSON()};return ok(nodePayload);}
  if(path==='/version/v1/node/n1'){
   if(route.request().method()==='PATCH'){nodePayload=route.request().postDataJSON();return ok({id:'n1',identity:'identity',...nodePayload});}
   return ok({id:'n1',identity:'identity',...nodePayload,attributes:nodePayload});
  }
  if(path==='/version/v1/node'){nodePayload=route.request().postDataJSON();return ok({id:'n1',...nodePayload});}
  throw new Error('Unexpected API '+path);
 });

 const errors=[]; page.on('pageerror', e=>{errors.push(e.message);console.error('Page error:',e.message);});
 await page.goto('http://localhost:5173/editor');
 await page.getByRole('option',{name:'Scenario',exact:true}).waitFor({state:'attached'});
 for (const [type, label, fields] of [
  ['INTERNAL_CONSUMPTION','Internal Consumption', ['Summer consumption','Autumn consumption','Winter consumption','Spring consumption']],
  ['INDUSTRIAL_CONSUMPTION','Industrial Consumption', ['Constant consumption']]
 ]) {
  await page.getByRole('button',{name:label,exact:true}).click();
  assert.equal(await page.getByLabel('Type',{exact:true}).inputValue(),type);
  await page.getByLabel('Name',{exact:true}).fill(label);
  for(let i=0;i<fields.length;i++) await page.getByLabel(fields[i],{exact:true}).fill(String(10+i));
  await page.getByRole('button',{name:'Create node',exact:true}).click();
  await page.getByRole('button',{name:'Create node',exact:true}).waitFor({state:'hidden'});
  assert.equal(nodePayload.type.nodeType,type);assert.equal(nodePayload.type.role,'SINK');
  assert.equal(nodePayload.type.vertical,'INTERNAL_CONSUMPTION');
  await page.locator('.react-flow__node').click();await page.getByRole('button',{name:'Edit',exact:true}).click();
  for(let i=0;i<fields.length;i++) assert.equal(await page.getByLabel(fields[i],{exact:true}).inputValue(),String(10+i));
  await page.getByLabel(fields[0],{exact:true}).fill('22.5');
  await page.getByRole('button',{name:'Save node',exact:true}).click();
  await page.getByRole('button',{name:'Save node',exact:true}).waitFor({state:'hidden'});
  await page.getByRole('button',{name:/^map$/i}).click();
  await page.locator('button').filter({hasText:/^diagram$/i}).click({timeout:5000});
  await page.locator('.react-flow__node').click();await page.getByRole('button',{name:'Edit',exact:true}).click();
  assert.equal(await page.getByLabel(fields[0],{exact:true}).inputValue(),'22.5');
  await page.getByRole('button',{name:'Close',exact:true}).click();
  await page.locator('.react-flow__node').click();
  await page.getByRole('button',{name:'Delete node',exact:true}).click();
  await page.locator('.react-flow__node').waitFor({state:'hidden'});
  assert.equal(nodePayload.state,'REMOVED');
  await page.reload();
  await page.getByRole('option',{name:'Scenario',exact:true}).waitFor({state:'attached'});
  assert.equal(await page.locator('.react-flow__node').count(),0);
 }
 assert.deepEqual(errors,[]);
 console.log('Consumer palette/create/edit/reload/map switching passed.');
} finally {await browser.close();}
