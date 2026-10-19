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
 if(path.endsWith('/organizations/owned'))return ok([]);
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
const base='http://127.0.0.1:5173/projects/p1/versions/v1/economics';
const output=new URL('../node_modules/.cache/probabilistic-ui/',import.meta.url);
await mkdir(output,{recursive:true});
try {
 await page.goto(base+'/history');
 await page.getByRole('progressbar').waitFor();
 const beforeHidden=polls;
 await page.evaluate(()=>{Object.defineProperty(document,'visibilityState',{configurable:true,value:'hidden'});document.dispatchEvent(new Event('visibilitychange'));});
 await page.waitForTimeout(2400);assert.equal(polls,beforeHidden,'Polling pauses while hidden');
 job={...job,completedRounds:1};
 await page.evaluate(()=>{Object.defineProperty(document,'visibilityState',{configurable:true,value:'visible'});document.dispatchEvent(new Event('visibilitychange'));});
 await page.getByText('1 de 2 rondas',{exact:false}).waitFor();
 await page.getByRole('link',{name:'Resultados',exact:true}).click();
 job={...job,status:'COMPLETED',completedRounds:2,evaluationId:'e1'};
 await page.getByRole('link',{name:'Ver resultados →'}).click();
 await page.getByLabel('Vista de resultados').waitFor();
 assert.equal(await page.getByLabel('Vista de resultados').inputValue(),'summary');
 await page.getByText('TIR calculable: 2 de 2 rondas.',{exact:false}).waitFor();
 const stopped=polls;await page.waitForTimeout(2400);assert.equal(polls,stopped,'Polling stops after completion');
 await page.screenshot({path:fileURLToPath(new URL('summary-desktop.png',output)),fullPage:true});
 await page.getByLabel('Vista de resultados').selectOption('2');
 await page.getByRole('heading',{name:'VAN Positivo',exact:true}).waitFor();
 await page.getByText('USD 300',{exact:true}).waitFor();
 await page.getByRole('tab',{name:'Operativos',exact:true}).click();
 assert.equal(await page.getByLabel('Vista de resultados').inputValue(),'2');
 assert.equal(await page.getByRole('option',{name:'Pozo guardado',exact:true}).count(),1);
 await page.getByRole('tab',{name:'Económicos',exact:true}).click();
 await page.getByText('USD 300',{exact:true}).waitFor();
 failRound=true;await page.getByLabel('Vista de resultados').selectOption('1');
 await page.getByText('Ronda no disponible',{exact:true}).waitFor();
 failRound=false;await page.getByLabel('Vista de resultados').selectOption('summary');
 await page.setViewportSize({width:390,height:844});
 await page.waitForFunction(()=>document.querySelector('aside').getBoundingClientRect().width===64);
 await page.getByRole('heading',{name:'Rentabilidad',exact:true}).scrollIntoViewIfNeeded();
 await page.screenshot({path:fileURLToPath(new URL('summary-mobile.png',output)),fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 assert.equal(await page.evaluate(()=>{const m=document.querySelector('main');return m.scrollWidth>m.clientWidth;}),false);
 job={...job,status:'INTERRUPTED',evaluationId:null,errorMessage:'Servidor reiniciado'};
 await page.getByRole('button',{name:'Actualizar avance'}).click();
 await page.getByRole('button',{name:'Repetir desde el inicio'}).click();
 await page.getByText('0 de 2 rondas',{exact:false}).waitFor();assert.equal(retries,1);
 job={...job,status:'FAILED',errorMessage:'Physical failure'};
 await page.getByRole('button',{name:'Actualizar avance'}).click();
 await page.getByRole('button',{name:'Repetir desde el inicio'}).click();
 await page.getByText('0 de 2 rondas',{exact:false}).waitFor();assert.equal(retries,2);
 assert.deepEqual(errors,[]);
 console.log('PASS: progress, navigation, polling completion, probabilistic summary, shared round, error, retry, desktop/mobile.');
}finally{await browser.close();}
