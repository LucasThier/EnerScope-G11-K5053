import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const base=process.env.APP_URL??'http://localhost:5175';
const browser=await chromium.launch({channel:'chrome',headless:true});
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'User',platformRole:'ADMIN'};
async function open(viewport,seed){
const context=await browser.newContext({viewport});
const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
await context.addInitScript(({user,seed})=>{
if(!sessionStorage.getItem('seeded')){sessionStorage.setItem('seeded','1');
localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));
if(seed!==null)localStorage.setItem('sidebarPinned',seed);}
},{user,seed});
await page.route('**/api/v1/**',route=>{
const path=new URL(route.request().url()).pathname.replace('/api/v1','');
const ok=data=>route.fulfill({json:{success:true,data,message:'OK'}});
if(path==='/auth/refresh')return ok({user,accessToken:'test',refreshToken:'test'});
if(path==='/projects')return ok([{id:'p1',name:'p1',organizationId:'o1',organizationName:'Org'}]);
if(path==='/organizations')return ok([{id:'o1',name:'Org'}]);
return ok([]);
});
return {context,page,errors};
}
const aside=page=>page.locator('aside');
const pin=page=>page.getByRole('button',{name:'Fijar menú'});
const menuButton=page=>page.getByRole('button',{name:/(Mostrar|Ocultar) menú/});
try {
{
const {context,page,errors}=await open({width:1440,height:900},null);
await page.goto(`${base}/app`);
await pin(page).waitFor();
assert.equal(await pin(page).getAttribute('aria-pressed'),'true');
assert.equal(await pin(page).getAttribute('title'),'Desfijar menú');
assert.equal(await menuButton(page).count(),0);
assert.equal(await aside(page).getAttribute('inert'),null);
const box=await aside(page).boundingBox();
const main=await page.locator('#contenido').boundingBox();
assert.ok(main.x>=box.x+box.width-1);
const logo=page.getByRole('link',{name:'EnerScope, ir al inicio'});
assert.equal(await logo.getAttribute('href'),'/app');
await page.goto(`${base}/profile`);
await logo.click();
await page.waitForURL('**/app');
await page.keyboard.press('Escape');
assert.equal(await aside(page).getAttribute('inert'),null);
await pin(page).click();
assert.equal(await pin(page).getAttribute('aria-pressed'),'false');
assert.equal(await pin(page).getAttribute('title'),'Fijar menú');
assert.equal(await aside(page).getAttribute('inert'),null);
await menuButton(page).waitFor();
await page.mouse.move(900,500);
await page.waitForTimeout(600);
assert.notEqual(await aside(page).getAttribute('inert'),null);
assert.equal(await page.evaluate(()=>localStorage.getItem('sidebarPinned')),'false');
await page.reload();
await menuButton(page).waitFor();
assert.equal(await aside(page).getAttribute('inert'),null);
await page.keyboard.press('Control+b');
assert.notEqual(await aside(page).getAttribute('inert'),null);
await page.keyboard.press('Control+b');
assert.equal(await aside(page).getAttribute('inert'),null);
await page.keyboard.press('Escape');
assert.notEqual(await aside(page).getAttribute('inert'),null);
await page.mouse.move(1,500);
await page.waitForTimeout(400);
assert.equal(await aside(page).getAttribute('inert'),null);
await pin(page).click();
assert.equal(await page.evaluate(()=>localStorage.getItem('sidebarPinned')),'true');
assert.equal(await menuButton(page).count(),0);
await page.reload();
await pin(page).waitFor();
assert.equal(await menuButton(page).count(),0);
await page.keyboard.press('Control+b');
assert.notEqual(await aside(page).getAttribute('inert'),null);
assert.equal(await page.evaluate(()=>localStorage.getItem('sidebarPinned')),'false');
assert.equal(await menuButton(page).count(),1);
assert.deepEqual(errors,[]);
await context.close();
}
{
const {context,page,errors}=await open({width:1440,height:900},'garbage');
await page.goto(`${base}/app`);
await pin(page).waitFor();
assert.equal(await pin(page).getAttribute('aria-pressed'),'true');
assert.deepEqual(errors,[]);
await context.close();
}
for(const width of [1280,1100]){
const {context,page,errors}=await open({width,height:900},null);
await page.goto(`${base}/profile`);
if(width>=1280){
await pin(page).waitFor();
assert.equal(await aside(page).getAttribute('inert'),null);
assert.equal(await menuButton(page).count(),0);
}else{
await menuButton(page).waitFor();
assert.equal(await pin(page).count(),0);
assert.notEqual(await aside(page).getAttribute('inert'),null);
await page.keyboard.press('Control+b');
assert.equal(await aside(page).getAttribute('inert'),null);
await page.keyboard.press('Escape');
assert.notEqual(await aside(page).getAttribute('inert'),null);
assert.equal(await page.evaluate(()=>localStorage.getItem('sidebarPinned')),null);
}
assert.deepEqual(errors,[]);
await context.close();
}
for(const preference of ['true','false']){
const {context,page,errors}=await open({width:1440,height:900},preference);
await page.goto(`${base}/profile`);
const fixedAt=async wide=>{
if(wide&&preference==='true'){
await pin(page).waitFor();
assert.equal(await aside(page).getAttribute('inert'),null);
assert.equal(await menuButton(page).count(),0);
}else{
await menuButton(page).waitFor();
assert.notEqual(await aside(page).getAttribute('inert'),null);
assert.equal(await page.locator('aside button[aria-pressed]').count(),wide?1:0);
}
assert.equal(await page.evaluate(()=>localStorage.getItem('sidebarPinned')),preference);
};
await fixedAt(true);
await page.setViewportSize({width:1100,height:900});
await page.waitForTimeout(300);
await fixedAt(false);
await page.setViewportSize({width:1440,height:900});
await page.waitForTimeout(300);
await fixedAt(true);
await context.close();
assert.deepEqual(errors,[]);
}
{
const {context,page,errors}=await open({width:390,height:800},null);
await page.goto(`${base}/app`);
await menuButton(page).or(page.getByRole('button',{name:/(Abrir|Cerrar) menú/})).first().waitFor();
assert.equal(await page.getByRole('button',{name:'Fijar menú'}).count(),0);
assert.notEqual(await aside(page).getAttribute('inert'),null);
assert.deepEqual(errors,[]);
await context.close();
}
console.log('PASS: sidebar pin, logo link, persistence, keyboard toggle and drawer.');
} finally {
await browser.close();
}
