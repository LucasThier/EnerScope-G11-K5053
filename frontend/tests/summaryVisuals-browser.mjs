// Browser acceptance against isolated API fixtures, without backend writes.
import assert from 'node:assert/strict';
import { readFile, mkdir } from 'node:fs/promises';
import { pathToFileURL, fileURLToPath } from 'node:url';
const { chromium } = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE).href);
const configuration=JSON.parse(await readFile(new URL('../../backend/src/test/resources/economic-examples/economic-configuration.json',import.meta.url),'utf8'));
const n1=configuration.conversions[0].nodeId;
const stats=(value,count=2)=>({count,mean:value,min:value,max:value,p10:value,p50:value,p90:value});
const payback={status:'RECOVERED',period:4,year:2034,becomesNegativeAgain:false};
const indicators={irr:{status:'CALCULATED',rate:.1483447840},simplePayback:payback,discountedPayback:{...payback,period:5,year:2035},calculationVersion:1,origin:'STORED'};
const result={npv:169.20,indicators,pendingBalances:[],periods:[-1080,25,382,451,391,491].map((cashFlow,period)=>({period,year:2030+period,cashFlow,discountedCashFlow:cashFlow/1.1**period,taxableIncome:0,deductibleExpenses:0,nonCashExpenses:0,resultBeforeTax:0,taxes:0,resultAfterTax:0,nonTaxableIncome:0,nonTaxableExpenses:0,nonCashAdjustments:0,cashTimingAdjustment:0}))};
const rawMetrics=[1,2,3,4,5].map(period=>({nodeId:n1,period,input:0,output:876000,exported:0,losses:0,operatingHours:8760,events:0}));
const paybackStats={denominator:2,recoveredProbability:1,recoveryPeriods:stats(4),statuses:{RECOVERED:2},becomesNegativeAgainCount:0};
const evaluation={id:'e1',createdAt:'2030-01-01T00:00:00Z',snapshot:{schemaVersion:3,configuration,physicalNodes:[{id:n1,name:'Pozo guardado'}],simulation:{rounds:2,seed:'9223372036854775807',algorithm:'JAVA_RANDOM_V1',calculationVersion:1},summary:{rounds:2,npv:stats(169.20),positiveNpvProbability:1,irr:stats(.1483447840),irrStatuses:{CALCULATED:2},simplePayback:paybackStats,discountedPayback:{...paybackStats,recoveryPeriods:stats(5)},cashFlows:result.periods.map(p=>({period:p.period,year:p.year,values:stats(p.cashFlow)})),operational:rawMetrics.flatMap(m=>['input','output','exported','losses','operatingHours','events'].map(metric=>({nodeId:n1,period:m.period,metric,values:stats(m[metric])})))}}};
let job={id:'j1',status:'RUNNING',rounds:2,completedRounds:0,seed:'9223372036854775807',createdAt:evaluation.createdAt,evaluationId:null};
let polls=0,retries=0,failRound=false;
const user={id:'u1',mail:'test@example.com',firstName:'Test',lastName:'User',platformRole:'ADMIN'};
const browser=await chromium.launch({channel:'chrome',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
await context.addInitScript(user=>{localStorage.setItem('accessToken','test');localStorage.setItem('authUser',JSON.stringify(user));},user);
const page=await context.newPage(),errors=[];
page.on('pageerror',e=>errors.push(e.message));
await page.route('**/api/v1/**',async route=>{
 const path=new URL(route.request().url()).pathname;
 const ok=data=>route.fulfill({json:{success:true,data,message:'OK'}});
 if(path.endsWith('/organizations/owned') || path.endsWith('/organizations'))return ok([]);
 if(path.endsWith('/projects'))return ok([{id:'p1',name:'Proyecto',organizationId:'o1',memberCount:1}]);
 if(path.endsWith('/versions'))return ok([{id:'v1',name:'Probabilístico',nodes:[{id:n1,name:'Pozo actual',type:'WELL'}]}]);
 if(path.endsWith('/members'))return ok([]);
 if(path.endsWith('/draft'))return ok(null);
 if(path.endsWith('/configuration'))return ok(configuration);
 if(path.endsWith('/evaluation-jobs')){polls++;return ok([job]);}
 if(path.endsWith('/retry')){retries++;job={...job,id:'j2',status:'QUEUED',completedRounds:0,errorMessage:null};return route.fulfill({status:202,json:{success:true,data:job}});}
 if(path.endsWith('/evaluations'))return ok(job.status==='COMPLETED'?[evaluation]:[]);
 if(path.endsWith('/evaluations/e1'))return ok(evaluation);
 if(path.includes('/rounds/')){
   if(failRound)return route.fulfill({status:500,json:{success:false,message:'Ronda no disponible'}});
   const round=Number(path.split('/').at(-1));return ok({round,seed:String(round),rawMetrics:rawMetrics.map(m=>({...m,output:m.output*round})),result:{...result,npv:round===1?169.20:300}});
 }
 throw new Error('Unexpected request '+path);
});
const base=(process.env.APP_URL || 'http://127.0.0.1:5173')+'/projects/p1/versions/v1/economics';
const output=new URL('../node_modules/.cache/probabilistic-ui/',import.meta.url);
await mkdir(output,{recursive:true});
try {
 job={...job,status:'COMPLETED',completedRounds:2,evaluationId:'e1'};
 evaluation.snapshot.summary.npv={count:2,mean:169.2,min:-100,max:400,p10:-100,p50:169.2,p90:400};
 evaluation.snapshot.summary.irr={count:1,mean:0,min:0,max:0,p10:0,p50:0,p90:0};
 evaluation.snapshot.summary.irrStatuses={CALCULATED:1,NO_SIGN_CHANGE:1};
 const summary=evaluation.snapshot.summary;
 summary.discountedPayback={denominator:1,recoveredProbability:0,recoveryPeriods:stats(null,0),statuses:{NOT_RECOVERED_WITHIN_HORIZON:1,NOT_APPLICABLE:1},becomesNegativeAgainCount:0};
 summary.cashFlows[2].values={...stats(null,0)};
 summary.cashFlows[3].values={count:2,mean:451,min:300,max:600,p10:300,p50:451,p90:600};
 evaluation.snapshot.physicalNodes.push({id:'second',name:'Terminal guardada'});
 summary.operational.push(...[1,2,3,4,5].map(period=>({nodeId:'second',period,metric:'output',values:stats(123)})));
 await page.goto(base+'/results/e1');
 await page.getByRole('heading',{name:'VAN medio',exact:true}).waitFor();
 assert.equal(await page.getByRole('heading',{name:'Ejecuciones de simulación'}).count(),0);
 assert.equal(await page.locator('details[open]').count(),0);
 assert.equal(await page.getByText('TIR calculable:',{exact:false}).count(),0);
 await page.getByText('Datos de la ejecución',{exact:true}).click();
 await page.getByText('Versión: 1',{exact:true}).waitFor();
 assert.equal(await page.getByText('Algoritmo:',{exact:false}).count(),0);
 await page.getByText('Datos de la ejecución',{exact:true}).click();
 assert.ok(await page.locator('svg .stroke-brand-800').count()>0);
 assert.ok(await page.locator('svg .fill-brand-800').count()>0);
 await page.getByText('1 de 2 rondas calculables',{exact:false}).waitFor();
 assert.equal(await page.getByLabel('Métrica',{exact:true}).inputValue(),'output');
 assert.equal(await page.getByLabel('Nodo',{exact:true}).inputValue(),n1);
 const tirCard=page.getByRole('heading',{name:'TIR mediana',exact:true}).locator('..');
 assert.match(await tirCard.innerText(),/0\s*%/);
 const recovery=page.getByRole('heading',{name:'Recuperación descontada',exact:true}).locator('..');
 assert.match(await recovery.innerText(),/0\s*%/);
 await page.getByText('Los datos ausentes se muestran como huecos.').waitFor();
 const chart=page.getByRole('group',{name:'Flujos anuales.',exact:false});
 const initial=chart.getByRole('button').first();
 assert.match(await initial.getAttribute('aria-label'),/2030.*1[.,]080/);
 await initial.focus();
 assert.match(await chart.locator('..').locator('..').getByRole('status').innerText(),/2030/);
 await page.keyboard.press('Escape');
 assert.match(await chart.locator('..').locator('..').getByRole('status').innerText(),/Seleccioná un punto/);
 await page.keyboard.press('Enter');
 assert.match(await chart.locator('..').locator('..').getByRole('status').innerText(),/2030/);
 await page.getByLabel('Nodo',{exact:true}).selectOption('second');
 await page.getByRole('group',{name:'Terminal guardada',exact:false}).getByRole('button',{name:/P50 123/}).first().waitFor();
 await page.getByLabel('Métrica',{exact:true}).selectOption('events');
 await page.getByText('No hay datos disponibles para este gráfico.').waitFor();
 await page.getByLabel('Nodo',{exact:true}).selectOption(n1);
 await page.getByLabel('Métrica',{exact:true}).selectOption('operatingHours');
 await page.getByRole('group',{name:'Pozo guardado · Horas operativas',exact:false}).getByRole('button',{name:/8[.,]760/}).first().waitFor();
 await page.getByLabel('Métrica',{exact:true}).selectOption('output');
 const factor=configuration.conversions.find(c=>c.nodeId===n1 && c.metric==='OUTPUT_VOLUME')?.factor ?? 1;
 const formatted=new Intl.NumberFormat('es-AR',{maximumFractionDigits:2}).format(876000*factor);
 const outputChart=page.getByRole('group',{name:'Pozo guardado · Producción / salida',exact:false});
 assert.ok((await outputChart.getByRole('button').first().getAttribute('aria-label')).includes('P50 '+formatted));
 const [download]=await Promise.all([page.waitForEvent('download'),page.getByRole('button',{name:'Descargar resumen CSV'}).click()]);
 const csv=await readFile(await download.path(),'utf8');
 assert.ok(csv.includes(String(876000*factor)));
 assert.match(csv,/"169.2"/); assert.match(csv,/"second"/); assert.match(csv,/NO_SIGN_CHANGE/);
 await page.getByText('Ver detalle estadístico',{exact:true}).first().click();
 assert.equal(await page.locator('details[open]').count(),1);
 await page.getByText('Ver detalle estadístico',{exact:true}).first().click();
 for (const [section,name] of [['header','VAN medio'],['flows','Flujos anuales'],['operation','Operación por nodo']]) {
   await page.getByRole('heading',{name,exact:true}).scrollIntoViewIfNeeded();
   await page.screenshot({path:fileURLToPath(new URL('visual-summary-desktop-'+section+'.png',output)),fullPage:true});
 }
 await page.setViewportSize({width:390,height:844});
 for (const [section,name] of [['header','VAN medio'],['flows','Flujos anuales'],['operation','Operación por nodo']]) {
   await page.getByRole('heading',{name,exact:true}).scrollIntoViewIfNeeded();
   await page.screenshot({path:fileURLToPath(new URL('visual-summary-mobile-'+section+'.png',output)),fullPage:true});
 }
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 assert.equal(await page.evaluate(()=>{const m=document.querySelector('main');return m.scrollWidth>m.clientWidth;}),false);
 summary.rounds=1;
 summary.npv=stats(0,1);summary.irr=stats(null,0);summary.irrStatuses={NO_SIGN_CHANGE:1};
 summary.discountedPayback={denominator:0,recoveredProbability:null,recoveryPeriods:stats(null,0),statuses:{NOT_APPLICABLE:1},becomesNegativeAgainCount:0};
 await page.reload();
 await page.getByRole('heading',{name:'TIR mediana',exact:true}).waitFor();
 assert.match(await page.getByRole('heading',{name:'TIR mediana',exact:true}).locator('..').innerText(),/No disponible/);
 assert.match(await page.getByRole('heading',{name:'Recuperación descontada',exact:true}).locator('..').innerText(),/No disponible/);
 assert.match(await page.getByRole('heading',{name:'VAN medio',exact:true}).locator('..').innerText(),/0,00/);
 assert.ok(await page.getByText('Sin dispersión observada',{exact:true}).count()>0);
 assert.deepEqual(errors,[]);
 console.log('PASS: visual summary, partial IRR, missing values, keyboard/Escape, saved nodes, metric selection, CSV, desktop/mobile, zero and unavailable denominators.');
} finally { await browser.close(); }
