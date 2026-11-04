import assert from 'node:assert/strict';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
const user = { id: 'u1', mail: 'test@example.com', firstName: 'Test', lastName: 'Admin', platformRole: 'ADMIN' };
await context.addInitScript(user => { localStorage.setItem('accessToken','test');localStorage.setItem('authUser',JSON.stringify(user)); },user);
const page = await context.newPage(), errors = [];
page.on('pageerror', error => errors.push(error.message));
const now='2030-01-01T00:00:00Z';
const versions=[{id:'v1',name:'Base',parentVersionId:null,createdAt:now,lastModified:now,nodes:[]},{id:'v2',name:'Rama',parentVersionId:'v1',createdAt:now,lastModified:now,nodes:[]}];
let merge=null, preparations=0, resolutions=0, restores=0;
await page.route('**/api/v1/**',async route=>{
 const request=route.request(),path=new URL(request.url()).pathname;
 const ok=data=>route.fulfill({json:{success:true,data,message:'OK'}});
 if(path.endsWith('/organizations/owned'))return ok([]);
 if(path.endsWith('/projects'))return ok([{id:'p1',name:'Proyecto',organizationId:'o1',myRole:'ADMIN'}]);
 if(path.endsWith('/versions'))return ok(versions);
 if(path.endsWith('/conflicts')||path.endsWith('/members'))return ok([]);
 if(path.endsWith('/revisions/status'))return ok({revisionId:'r2',number:2,workspaceChanges:false,canPublish:true,token:'reviewed'});
 if(path.endsWith('/revisions'))return ok([{id:'r2',number:2,createdAt:now,parentRevisionId:'r1'},{id:'r1',number:1,createdAt:now,parentRevisionId:null}]);
 if(path.endsWith('/restore')) {assert.equal(request.headers()['if-match'],'reviewed');assert.equal(request.postDataJSON().replacePending,true);restores++;return ok({revisionId:'r2',number:2,workspaceChanges:true,canPublish:true,token:'restored'});}
 if(path.endsWith('/scenario-configuration'))return ok({token:'form',sourceToken:'source',configuration:null,nodes:[],draft:null,legacyDraft:null,errors:[],revision:{revisionId:'r2',number:2,workspaceChanges:true,canPublish:true}});
 if(path.endsWith('/merge')) {
  if(request.method()==='POST') {preparations++;merge={id:'m1',targetId:'v1',sourceRevisionId:'r3',targetRevisionId:'r2',baseRevisionId:'r1',state:'PREPARED',token:'m1:0',conflicts:[{kind:'nodes',identity:'n1',base:{capacity:1},source:{capacity:2},target:{capacity:3}}]};}
  if(request.method()==='PUT') {assert.equal(request.headers()['if-match'],'m1:0');assert.deepEqual(request.postDataJSON(),{'nodes:n1':'SOURCE'});resolutions++;merge={...merge,token:'m1:1',conflicts:[]};}
  return ok(merge);
 }
 throw new Error('Unexpected request '+request.method()+' '+path);
});
const url=process.env.APP_URL||'http://127.0.0.1:5173';
const output=new URL('../node_modules/.cache/revisions-ui/',import.meta.url);await mkdir(output,{recursive:true});
try {
 await page.goto(url+'/projects/p1/versions');
 await page.getByRole('button',{name:'Mergear Rama con Base',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Integrar operación'});
 await dialog.getByRole('button',{name:'Preparar integración'}).click();
 await dialog.getByRole('combobox',{name:'Conservar'}).selectOption('SOURCE');
 await dialog.getByRole('button',{name:'Guardar resoluciones'}).click();
 await dialog.getByRole('link',{name:'Revisar configuración y publicar →'}).waitFor();
 assert.equal(preparations,1);assert.equal(resolutions,1);
 await page.screenshot({path:fileURLToPath(new URL('merge-desktop.png',output)),fullPage:true});
 await dialog.getByRole('button',{name:'Cerrar',exact:true}).last().click();
 const history=page.getByRole('region',{name:'Revisiones publicadas'});
 await history.getByRole('combobox',{name:'Escenario',exact:true}).selectOption('v1');
 await history.getByText('Revisión 1',{exact:true}).waitFor();
 await page.setViewportSize({width:390,height:844});
 await page.screenshot({path:fileURLToPath(new URL('revisions-mobile.png',output)),fullPage:true});
 page.once('dialog',dialog=>dialog.accept());
 await history.getByRole('button',{name:'Restaurar en borrador'}).last().click();
 await page.waitForURL('**/economics/configuration');
 await page.getByRole('button',{name:'Guardar y publicar',exact:true}).first().waitFor();
 assert.equal(restores,1);assert.deepEqual(errors,[]);
 console.log('PASS: operational merge preparation/resolution, revision history, mobile and confirmed restoration with concurrency token.');
} finally {await browser.close();}
