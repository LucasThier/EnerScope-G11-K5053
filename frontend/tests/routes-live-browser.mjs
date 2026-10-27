// Read-only acceptance against a running API. Supply TEST_EMAIL and TEST_PASSWORD.
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const app=process.env.APP_URL||'http://localhost:5173';
assert.ok(process.env.TEST_EMAIL && process.env.TEST_PASSWORD,'Supply TEST_EMAIL and TEST_PASSWORD');
const response=await fetch(app+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mail:process.env.TEST_EMAIL,password:process.env.TEST_PASSWORD})});
assert.equal(response.status,200);
const auth=(await response.json()).data;
async function get(path){const r=await fetch(app+'/api/v1'+path,{headers:{Authorization:'Bearer '+auth.accessToken}});assert.equal(r.status,200,path);return (await r.json()).data;}
const projects=await get('/projects');assert.ok(projects.length);
const p=projects[0], versions=await get('/projects/'+p.id+'/versions');assert.ok(versions.length);
const v=versions[0];
const evaluations=await get('/projects/'+p.id+'/versions/'+v.id+'/economics/evaluations');
const browser=await chromium.launch({channel:'chrome',headless:true});
try {
const context=await browser.newContext({viewport:{width:1440,height:1000}});
await context.addInitScript(({auth,p})=>{localStorage.setItem('accessToken',auth.accessToken);localStorage.setItem('refreshToken',auth.refreshToken);localStorage.setItem('authUser',JSON.stringify(auth.user));localStorage.setItem('lastProjectId',JSON.stringify(p.id));localStorage.setItem('sidebarPinned','true');},{auth,p});
const page=await context.newPage();const errors=[];page.on('response',r=>{if(r.url().includes('/api/')&&r.status()>=500)errors.push('API '+r.status()+' '+r.url());});page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error'&&!m.text().includes('Failed to load resource'))errors.push(m.text());});
const root='/projects/'+p.id, economics=root+'/versions/'+v.id+'/economics';
const paths=['/','/app','/projects','/editor','/versions',root+'/versions',root+'/team',root+'/scenario-configurations',root+'/simulations',economics,economics+'/configuration',economics+'/history',economics+'/results','/profile','/settings','/admin/users','/admin/organizations','/organizations/'+p.organizationId];
if(evaluations.length)paths.push(economics+'/results/'+evaluations[0].id);
for(const path of paths){
await page.goto(app+path);await page.waitForTimeout(650);
assert.ok((await page.locator('body').innerText()).trim().length>0,path);
assert.equal(await page.getByText('Unexpected Application Error!',{exact:true}).count(),0,path);
assert.deepEqual(errors,[],path);
console.log('PASS route',path);
}
await page.goto(app+economics+'/history');
await page.getByRole('link',{name:/← Simulación de Escenarios/}).click();
await page.waitForURL('**/simulations');
await page.goto(app+root+'/versions');
await page.getByRole('button',{name:'Nueva versión',exact:true}).click();
await page.getByRole('dialog').waitFor();
console.log('PASS master version creation dialog; no changes submitted');
const guest=await browser.newContext();const guestPage=await guest.newPage();
await guestPage.goto(app+economics+'/configuration');await guestPage.waitForURL('**/login');
console.log('PASS anonymous guard');
assert.deepEqual(errors,[]);
}finally{await browser.close();}
