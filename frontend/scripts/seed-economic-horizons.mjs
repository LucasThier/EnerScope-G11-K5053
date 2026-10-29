import { readFile, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';
const root = process.cwd();
const projectId = process.env.DEMO_PROJECT_ID;
const parentVersion = process.env.DEMO_PARENT_VERSION;
if (!projectId || !parentVersion || !process.env.DEMO_MAIL || !process.env.DEMO_PASSWORD) throw Error('Set DEMO_PROJECT_ID, DEMO_PARENT_VERSION, DEMO_MAIL and DEMO_PASSWORD');
const base = process.env.DEMO_API_URL || 'http://localhost:8080/api/v1';
let token;
async function api(method,path,body) {
 const response = await fetch(base + path, {method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});
 const envelope = await response.json();
 if (!response.ok || !envelope.success) throw Error(method+' '+path+': '+response.status+' '+envelope.message);
 return envelope.data;
}
token = (await api('POST','/auth/login',{mail:process.env.DEMO_MAIL,password:process.env.DEMO_PASSWORD})).accessToken;
const {default:ts} = await import(pathToFileURL(root+'/frontend/node_modules/typescript/lib/typescript.js'));
const source = await readFile(root+'/frontend/src/utils/economicDraft.ts','utf8');
const code = ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext}}).outputText;
const {emptyDraft,toConfiguration,validateDraft,fromConfiguration} = await import('data:text/javascript;base64,'+Buffer.from(code).toString('base64'));
const records=[];
for (const years of [5,10]) {
 const name='Ejemplo visual · '+years+' años (2031–'+(2030+years)+')';
 const versions=await api('GET','/projects/'+projectId+'/versions');
 let version=versions.find(v=>v.name===name);
 if(!version) version=await api('POST','/projects/'+projectId+'/version',{name,parentVersion});
 const path='/projects/'+projectId+'/versions/'+version.id+'/economics';
 const prior=await api('GET',path+'/evaluations');
 let evaluation=prior[0];
 if(!evaluation) {
   const rule=(concept,direction,value)=>({id:randomUUID(),concept,direction,driver:'FIXED',nodeId:'',value,unit:'',factor:'',from:'2031',to:String(2030+years)});
   const draft={...emptyDraft(),startYear:'2030',years:String(years),currency:'USD',wacc:'10',entityName:'Operadora · Ejemplo de horizonte',taxRate:'30',
     assets:[{id:randomUUID(),concept:'Inversión inicial ilustrativa',nodeId:'',cost:'1500000',residual:'0',purchase:'2030-12-31',service:'2031-01-01',life:'180'}],
     rules:[rule('Ingresos anuales ilustrativos','INCOME','1000000'),rule('Operación y mantenimiento','EXPENSE','200000')]};
   if(Object.values(validateDraft(draft,[])).some(errors=>errors.length)) throw Error('Invalid draft');
   const configuration=toConfiguration(draft);
   if(!fromConfiguration(configuration)) throw Error('Example must remain editable');
   await api('PUT',path+'/configuration',configuration);
   evaluation=await api('POST',path+'/evaluations');
 }
 if(evaluation.snapshot.configuration.years!==years || evaluation.snapshot.result.periods.length!==years+1) throw Error('Unexpected example horizon');
 records.push({name,projectId,versionId:version.id,evaluationId:evaluation.id,years,npv:evaluation.snapshot.result.npv,indicators:evaluation.snapshot.result.indicators,url:'http://localhost:5173/projects/'+projectId+'/versions/'+version.id+'/economics/results'});
}
if(process.env.DEMO_OUTPUT) await writeFile(process.env.DEMO_OUTPUT,JSON.stringify(records,null,2));
console.log(JSON.stringify(records,null,2));
