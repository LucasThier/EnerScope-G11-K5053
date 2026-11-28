// Sidebar layout: a fixed column from 80rem (1280px), a drawer below it.
// Replaces sidebarPin-browser.mjs (the pin and the desktop overlay are gone).
// Like every *-browser.mjs it is NOT part of `npm test` and has NOT been run
// since it was written; run it against a local Vite server with APP_URL and
// PLAYWRIGHT_MODULE set before relying on it.
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const base=process.env.APP_URL??'http://localhost:5175';
const browser=await chromium.launch({channel:'chrome',headless:true});
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'User',platformRole:'ADMIN'};
async function open(viewport,legacyPin=null){
const context=await browser.newContext({viewport});
const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
await context.addInitScript(({user,legacyPin})=>{
if(!sessionStorage.getItem('seeded')){sessionStorage.setItem('seeded','1');
localStorage.setItem('accessToken','test');localStorage.setItem('refreshToken','test');localStorage.setItem('authUser',JSON.stringify(user));
if(legacyPin!==null)localStorage.setItem('sidebarPinned',legacyPin);}
},{user,legacyPin});
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
const header=page=>page.locator('header');
const logo=page=>page.getByRole('link',{name:'EnerScope, ir al inicio'});
const burger=page=>page.getByRole('button',{name:/(Abrir|Cerrar) menú/});
const inert=async page=>(await aside(page).getAttribute('inert'))!==null;
const focusInAside=page=>page.evaluate(()=>document.querySelector('aside')?.contains(document.activeElement)??false);
try {
// >= 1280: a permanent column with the logo, nothing to toggle, the old key removed.
for(const width of [1440,1280]){
const {context,page,errors}=await open({width,height:900},'false');
await page.goto(`${base}/profile`);
await aside(page).locator('nav').waitFor();
assert.equal(await inert(page),false);
assert.equal(await burger(page).count(),0);
assert.equal(await page.getByRole('button',{name:/Fijar menú/}).count(),0);
assert.equal(await aside(page).getByRole('link',{name:'EnerScope, ir al inicio'}).count(),1);
assert.equal(await header(page).getByRole('link',{name:'EnerScope, ir al inicio'}).count(),0);
const box=await aside(page).boundingBox();
assert.equal(box.y,0);
assert.ok((await page.locator('#contenido').boundingBox()).x>=box.x+box.width-1);
await page.keyboard.press('Control+b');
assert.equal(await inert(page),false);
assert.equal(await page.evaluate(()=>localStorage.getItem('sidebarPinned')),null);
await logo(page).click();
await page.waitForURL('**/app');
assert.equal(await inert(page),false);
assert.deepEqual(errors,[]);
await context.close();
}
// 1024-1279: a drawer under a 64px top bar, logo in the bar.
{
const {context,page,errors}=await open({width:1100,height:900});
await page.goto(`${base}/app`);
await burger(page).waitFor();
assert.equal(await page.getByRole('button',{name:'Abrir menú'}).count(),1);
assert.equal(await inert(page),true);
assert.equal(await header(page).getByRole('link',{name:'EnerScope, ir al inicio'}).count(),1);
assert.equal(await aside(page).getByRole('link',{name:'EnerScope, ir al inicio'}).count(),0);
await burger(page).click();
assert.equal(await inert(page),false);
assert.equal(await page.getByRole('button',{name:'Cerrar menú'}).count(),1);
assert.ok(await focusInAside(page));
const headerBox=await header(page).boundingBox();
assert.equal(headerBox.height,64);
assert.ok((await aside(page).boundingBox()).y>=headerBox.y+headerBox.height-0.5,'the drawer must not cover the top bar');
await page.keyboard.press('Escape');
assert.equal(await inert(page),true);
assert.ok(await page.evaluate(()=>document.activeElement?.getAttribute('aria-label')==='Abrir menú'));
await page.keyboard.press('Control+b');
assert.equal(await inert(page),false);
await page.mouse.click(900,500);
assert.equal(await inert(page),true);
await burger(page).click();
await aside(page).getByRole('link').first().click();
assert.equal(await inert(page),true);
await burger(page).click();
await page.setViewportSize({width:1440,height:900});
await page.waitForTimeout(300);
assert.equal(await inert(page),false);
assert.equal(await burger(page).count(),0);
await page.setViewportSize({width:1100,height:900});
await page.waitForTimeout(300);
assert.equal(await inert(page),true,'crossing 80rem starts the drawer closed');
assert.deepEqual(errors,[]);
await context.close();
}
// < 1024: the drawer under a 56px top bar, with Tab trapped inside it.
{
const {context,page,errors}=await open({width:390,height:800});
await page.goto(`${base}/app`);
await burger(page).waitFor();
assert.equal(await inert(page),true);
await burger(page).click();
assert.ok((await aside(page).boundingBox()).y>=56-0.5);
for(let i=0;i<40;i++){await page.keyboard.press('Tab');assert.ok(await focusInAside(page));}
await page.keyboard.press('Escape');
assert.equal(await inert(page),true);
assert.deepEqual(errors,[]);
await context.close();
}
console.log('PASS: fixed column from 80rem, drawer below it, logo placement, keyboard and retired key.');
} finally {
await browser.close();
}
