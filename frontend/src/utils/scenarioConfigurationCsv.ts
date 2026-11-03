import { emptyDraft, validateDraft, type EconomicDraft, type AssetDraft, type RuleDraft } from './economicDraft';
import { prepareScenario } from './scenarioConfiguration';
import type { OperationalNode, OperationalValue, ScenarioSave } from '../types/scenarioConfiguration';

export const MAX_CSV_BYTES = 2_000_000;
export const csvHeaders = ['Sección', 'Elemento', 'Nodo', 'Campo', 'Valor'];
export interface ConfigurationRow { section: string; element: string; node: string; field: string; value: string; line: number }
export interface FileNode { key: string; identity: string; type: string; name: string }
export interface ConfigurationFile { rows: ConfigurationRow[]; nodes: FileNode[] }
export class ConfigurationCsvError extends Error {
 constructor(public issues: string[]) { super(issues.join('\n')); }
}
function fail(message: string): never { throw new ConfigurationCsvError([message]); }
const key = (r: ConfigurationRow) => JSON.stringify([r.section,r.element,r.node,r.field]);
const scalarFields = { General: ['startYear','years','currency','wacc'], Impuestos: ['entityName','taxRate'] };
const assetFields = ['concept','cost','residual','purchase','service','life'];
const ruleFields = ['concept','driver','value','unit','factor','from','to'];
const numericFields = new Set(['startYear','years','wacc','taxRate','cost','residual','life','value','factor','from','to']);
function numberText(value: unknown): string {
 const text=String(value??'');
 const match=/^(-?)(\d+)(?:\.(\d+))?[eE]([+-]?\d+)$/.exec(text);
 if(!match)return text.replace('.',',');
 const [,sign,whole,fraction='',exponent]=match,digits=whole+fraction,position=whole.length+Number(exponent);
 return sign+(position<=0?'0,'+'0'.repeat(-position)+digits:position>=digits.length?digits+'0'.repeat(position-digits.length):digits.slice(0,position)+','+digits.slice(position));
}
const textCell = (value: string) => /^[\s]*[=+@-]/.test(value) || value.startsWith("'") ? "'"+value : value;
const unprotect = (value: string) => value.startsWith("'") && (value[1] === "'" || /^[\s]*[=+@-]/.test(value.slice(1))) ? value.slice(1) : value;

/** Strict, bounded CSV reader supporting quoted separators and multiline cells. */
export function parseCsv(text: string): { cells: string[]; line: number }[] {
 if(new TextEncoder().encode(text).length>MAX_CSV_BYTES) fail('El archivo supera el límite de 2 MB.');
 text=text.replace(/^\uFEFF/,'');
 const result: {cells:string[];line:number}[]=[];
 let cells:string[]=[],cell='',quoted=false,closed=false,line=1,start=1;
 const finishCell=()=>{cells.push(unprotect(cell));cell='';closed=false;};
 const finishRow=()=>{finishCell();if(cells.some(c=>c!==''))result.push({cells,line:start});cells=[];};
 for(let i=0;i<text.length;i++) {
  const c=text[i];
  if(quoted) {
   if(c==='"') { if(text[i+1]==='"'){cell+='"';i++;}else {quoted=false;closed=true;} }
   else {cell+=c;if(c==='\n')line++;}
  } else if(c===';') finishCell();
  else if(c==='\n'||c==='\r') {if(c==='\r'&&text[i+1]==='\n')i++;finishRow();line++;start=line;}
  else if(c==='"'&&!cell&&!closed) quoted=true;
  else {if(closed||c==='"')fail(`Fila ${line}: comillas o caracteres fuera de una celda inválidos.`);cell+=c;}
 }
 if(quoted)fail(`Fila ${start}: falta cerrar las comillas.`);
 if(cell||cells.length||closed)finishRow();
 return result;
}

export function configurationRows(form: ScenarioSave,nodes: OperationalNode[]): ConfigurationRow[] {
 if(form.editor.mode!=='simple')fail('La configuración avanzada requiere crear una copia simple.');
 const d=form.editor.value,rows:ConfigurationRow[]=[];
 const add=(section:string,element:string,node:string,field:string,value:unknown)=>rows.push({section,element,node,field,value:String(value??''),line:rows.length+2});
 add('Metadatos','','','version','1');
 for(const [section,fields] of Object.entries(scalarFields))for(const field of fields) add(section,'','',field,numericFields.has(field)?numberText(d[field as keyof EconomicDraft]):d[field as keyof EconomicDraft]);
 for(const section of ['Inversion','Ingresos','Costos']) {
  const items=section==='Inversion'?d.assets:d.rules.filter(r=>r.direction===(section==='Ingresos'?'INCOME':'EXPENSE'));
  add('Colecciones','','',section,items.length);
  for(const item of items) for(const field of section==='Inversion'?assetFields:ruleFields) {
   const value=(item as unknown as Record<string,string>)[field];
   add(section,item.id,item.nodeId,field,numericFields.has(field)?numberText(value):value);
  }
 }
 add('Colecciones','','','Nodos',nodes.length);
 for(const n of nodes) {
  for(const field of ['identity','type','name'] as const)add('Nodos','',n.id,field,n[field]);
  for(const field of n.fields) {
   const value=form.operations[n.id]?.[field.name];
   if(field.type==='distribution'&&value&&typeof value==='object') {
    add('Operacion','',n.id,field.name+'.type',value.type);
    add('Operacion','',n.id,field.name+'.a',numberText(value.a));
    if(value.type!=='CONSTANT')add('Operacion','',n.id,field.name+'.b',numberText(value.b));
   } else add('Operacion','',n.id,field.name,field.type==='number'||field.type==='integer'?numberText(value):value);
  }
 }
 return rows;
}
export function configurationCsv(form:ScenarioSave,nodes:OperationalNode[]):string {
 const rows=configurationRows(form,nodes).map(r=>[r.section,r.element,r.node,r.field,r.value]);
 return '\uFEFF'+[csvHeaders,...rows].map(row=>row.map(c=>'"'+textCell(c).replace(/"/g,'""')+'"').join(';')).join('\r\n')+'\r\n';
}
export function readConfigurationCsv(text:string):ConfigurationFile {
 const all=parseCsv(text),header=all.shift();
 if(!header||JSON.stringify(header.cells)!==JSON.stringify(csvHeaders))fail('Encabezado inválido. Usá la plantilla de EnerScope con separador punto y coma.');
 const rows:ConfigurationRow[]=[],seen=new Set<string>();
 for(const {cells,line} of all) {
  if(cells.length!==5)fail(`Fila ${line}: se esperaban cinco columnas.`);
  const [section,element,node,field,value]=cells,r={section,element,node,field,value,line};
  if(seen.has(key(r)))fail(`Fila ${line} / ${field}: parámetro duplicado.`);
  seen.add(key(r));rows.push(r);
 }
 if(!rows.some(r=>r.section==='Metadatos'&&!r.element&&!r.node&&r.field==='version'&&r.value==='1'))fail('Versión de formato ausente o incompatible.');
 const nodes:FileNode[]=[];
 for(const id of new Set(rows.filter(r=>r.section==='Nodos').map(r=>r.node))) {
  const get=(field:string)=>{const r=rows.find(r=>r.section==='Nodos'&&r.node===id&&r.field===field&&!r.element);if(!r||!r.value)fail(`Nodo ${id}: falta ${field}.`);return r.value;};
  if(!id)fail('La identificación de un nodo está vacía.');
  nodes.push({key:id,identity:get('identity'),type:get('type'),name:get('name')});
 }
 if(new Set(nodes.map(n=>n.identity)).size!==nodes.length)fail('El archivo contiene identidades de nodo duplicadas.');
 return {rows,nodes};
}
export function automaticNodeMapping(file:ConfigurationFile,nodes:OperationalNode[]):Record<string,string> {
 return Object.fromEntries(file.nodes.map(n=>{const matches=nodes.filter(other=>other.identity===n.identity&&other.type===n.type);return [n.key,matches.length===1?matches[0].id:''];}));
}

export function importConfiguration(file:ConfigurationFile,nodes:OperationalNode[],mapping:Record<string,string>,base:ScenarioSave):ScenarioSave {
 if(base.editor.mode!=='simple')fail('Creá una copia simple para importar esta configuración.');
 if(file.nodes.length!==nodes.length)fail('El archivo debe cubrir todos los nodos del escenario, sin nodos adicionales.');
 const targets=file.nodes.map(n=>mapping[n.key]);
 if(new Set(targets).size!==nodes.length||file.nodes.some(n=>!nodes.some(t=>t.id===mapping[n.key]&&t.type===n.type)))fail('Vinculá cada nodo con un único nodo del mismo tipo.');
 const consumed=new Set<string>();
 const indexed=new Map(file.rows.map(r=>[key(r),r]));
 const get=(section:string,element:string,node:string,field:string)=>{
  const r=indexed.get(JSON.stringify([section,element,node,field]));
  if(!r)fail(`${section} / ${element||node||'General'} / ${field}: falta el parámetro obligatorio.`);
  consumed.add(key(r));return r;
 };
 get('Metadatos','','','version');
 const d=emptyDraft();d.entityId=base.editor.value.entityId;
 for(const [section,fields] of Object.entries(scalarFields))for(const field of fields) (d as unknown as Record<string,unknown>)[field]=get(section,'','',field).value;
 for(const section of ['Inversion','Ingresos','Costos']) {
  const items=file.rows.filter(r=>r.section===section),ids=[...new Set(items.map(r=>r.element))];
  const count=get('Colecciones','','',section);
  if(!/^\d+$/.test(count.value)||Number(count.value)!==ids.length||ids.length>500)fail(`Fila ${count.line}: cantidad de elementos inválida para ${section}.`);
  for(const id of ids) {
   if(!id)fail(`${section}: falta la clave del elemento.`);
   const refs=[...new Set(items.filter(r=>r.element===id).map(r=>r.node))];
   if(refs.length!==1||(refs[0]&&!mapping[refs[0]]))fail(`${section} / ${id}: referencia de nodo inválida.`);
   const fields=section==='Inversion'?assetFields:ruleFields;
   const values=Object.fromEntries(fields.map(field=>[field,get(section,id,refs[0],field).value]));
   const item={...values,id:crypto.randomUUID(),nodeId:refs[0]?mapping[refs[0]]:''};
   if(section==='Inversion')d.assets.push(item as unknown as AssetDraft);
   else {
    if(!['FIXED','OUTPUT_VOLUME','EXPORTED_VOLUME'].includes(values.driver)||(section==='Costos'&&values.driver==='EXPORTED_VOLUME'))fail(`${section} / ${id}: base de cálculo inválida.`);
    if(values.driver==='FIXED'&&refs[0])fail(`${section} / ${id}: un importe fijo no debe referenciar un nodo.`);
    d.rules.push({...item,direction:section==='Ingresos'?'INCOME':'EXPENSE'} as unknown as RuleDraft);
   }
  }
 }
 const count=get('Colecciones','','','Nodos');
 if(!/^\d+$/.test(count.value)||Number(count.value)!==nodes.length)fail(`Fila ${count.line}: cantidad de nodos inválida.`);
 const operations:ScenarioSave['operations']={};
 const problems:string[]=[];
 for(const source of file.nodes) {
  for(const field of ['identity','type','name'])get('Nodos','',source.key,field);
  const target=nodes.find(n=>n.id===mapping[source.key])!;const values:Record<string,OperationalValue>={};
  for(const field of target.fields) {
   if(field.type==='distribution') {
    const type=get('Operacion','',source.key,field.name+'.type');
    const a=get('Operacion','',source.key,field.name+'.a'),b=type.value==='CONSTANT'?null:get('Operacion','',source.key,field.name+'.b');
    const numeric=(s:string)=>/^-?\d+(?:,\d+)?$/.test(s)&&Number.isFinite(Number(s.replace(',','.')));
    if(!['CONSTANT','NORMAL','UNIFORM'].includes(type.value)||!numeric(a.value)||(b&&!numeric(b.value)))problems.push(`Fila ${type.line} / ${field.name}: distribución inválida.`);
    values[field.name]={type:type.value,a:a.value,...(b?{b:b.value}:{})};
   } else {
    const r=get('Operacion','',source.key,field.name);
    if(['number','integer'].includes(field.type)&&(!/^\d+(?:,\d+)?$/.test(r.value)||!Number.isFinite(Number(r.value.replace(',','.')))||(field.type==='integer'&&!Number.isInteger(Number(r.value.replace(',','.'))))))problems.push(`Fila ${r.line} / ${field.name}: número inválido; usá coma decimal y sin miles.`);
    if(field.type==='state'&&!['PROPOSED','PENDING','RUNNING'].includes(r.value))problems.push(`Fila ${r.line} / ${field.name}: estado inválido.`);
    values[field.name]=r.value;
   }
  }
  operations[target.id]=values;
 }
 for(const r of file.rows)if(!consumed.has(key(r)))problems.push(`Fila ${r.line} / ${r.field}: sección, elemento o campo desconocido.`);
 for(const [section,errors] of Object.entries(validateDraft(d,nodes)))for(const error of errors) {
  const lines=file.rows.filter(r=>r.section===section).map(r=>r.line);
  problems.push(`${section} (filas ${lines.join(', ')}): ${error}`);
 }
 // Economic input uses the same locale as operational numbers.
 for(const r of file.rows)if(['General','Impuestos','Inversion','Ingresos','Costos'].includes(r.section)&&numericFields.has(r.field)&&r.value.includes('.'))problems.push(`Fila ${r.line} / ${r.field}: usá coma decimal, sin separadores de miles.`);
 if(problems.length)throw new ConfigurationCsvError(problems);
 return prepareScenario({...base,editor:{mode:'simple',value:d},operations},nodes);
}
export function configurationChanges(before:ScenarioSave,after:ScenarioSave,nodes:OperationalNode[]) {
 // Match financial rows by section/concept so generated IDs do not obscure a useful comparison.
 const flatten=(form:ScenarioSave)=>{
  const rows=configurationRows(form,nodes);const concepts=new Map(rows.filter(r=>r.field==='concept').map(r=>[r.section+':'+r.element,r.value]));
  return new Map(rows.filter(r=>!['Metadatos','Nodos','Colecciones'].includes(r.section)).map(r=>{
   const element=concepts.get(r.section+':'+r.element)||r.element;
   const match=nodes.find(n=>n.id===r.node);
   const node=match?match.name+' ('+match.id+')':r.node;
   return [[r.section,element,node,r.field].filter(Boolean).join(' / '),r.value];
  }));
 };
 const old=flatten(before),next=flatten(after);
 return [...new Set([...old.keys(),...next.keys()])].filter(k=>old.get(k)!==next.get(k)).map(field=>({field,before:old.get(field)??'—',after:next.get(field)??'—'}));
}
