import { updateEconomicField } from '../../utils/advancedEconomicDraft';
import { useRef, useState } from 'react';
import type { EconomicConfiguration, Scenario } from '../../types/economics';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { TextField } from '../ui/TextField';

const sections = ['General', 'Inversión', 'Ingresos', 'Costos', 'Impuestos'] as const;
type Section = typeof sections[number];
type Path = (string | number)[];
const labels: Record<string, string> = {
  startYear: 'Año de inversión', years: 'Años operativos', currency: 'Moneda', wacc: 'WACC anual (%)',
  concept: 'Concepto', cost: 'Importe', residualValue: 'Valor residual', purchaseDate: 'Compra', paymentDate: 'Pago',
  serviceDate: 'Puesta en servicio', usefulLifeMonths: 'Vida útil (meses)', nodeId: 'Nodo', deliveryNodeId: 'Nodo de entrega',
  taxEntityId: 'Entidad fiscal', counterpartyId: 'Contraparte', name: 'Nombre', jurisdiction: 'Jurisdicción',
  taxRate: 'Tasa de impuesto (%)', carryLosses: 'Trasladar pérdidas', openingLoss: 'Pérdida inicial',
  lossExpiryYears: 'Vencimiento de pérdidas (años, opcional)', lossOffsetLimit: 'Límite de compensación (%)',
  taxPaymentLagYears: 'Diferimiento del impuesto (años)', share: 'Participación (%)',
  unitValue: 'Importe / tarifa', unit: 'Unidad', driver: 'Modalidad', cashClassification: 'Movimiento de caja',
  validFrom: 'Vigente desde', validTo: 'Vigente hasta', recognitionDate: 'Reconocimiento', cashDate: 'Cobro / pago',
  classification: 'Tratamiento fiscal', deductibleFraction: 'Fracción deducible (%)',
  metric: 'Métrica', rawUnit: 'Unidad de origen', factor: 'Factor de conversión', annualQuantity: 'Cantidad anual (opcional)',
};
const percentages = new Set(['wacc', 'taxRate', 'lossOffsetLimit', 'share', 'deductibleFraction']);
const numeric = new Set(['startYear', 'years', 'wacc', 'cost', 'residualValue', 'usefulLifeMonths', 'taxRate',
  'openingLoss', 'lossExpiryYears', 'lossOffsetLimit', 'taxPaymentLagYears', 'share', 'unitValue', 'deductibleFraction', 'factor', 'annualQuantity']);
const enums: Record<string, [string, string][]> = {
  cashClassification: [['CASH', 'Con movimiento de caja'], ['NON_CASH', 'Sin movimiento de caja']],
  driver: [['FIXED', 'Importe fijo'], ['INPUT_VOLUME', 'Volumen de entrada'], ['OUTPUT_VOLUME', 'Producción / salida'], ['EXPORTED_VOLUME', 'Volumen exportado'], ['OPERATING_HOURS', 'Horas operativas'], ['EVENT_COUNT', 'Cantidad de eventos']],
  classification: [['TAXABLE', 'Gravado'], ['NON_TAXABLE', 'No gravado'], ['DEDUCTIBLE', 'Deducible'], ['NON_DEDUCTIBLE', 'No deducible'], ['CAPITALIZABLE', 'Capitalizable']],
};

export function AdvancedEconomicEditor({ configuration: c, nodes, onChange, disabled, busy, dirty, save, discard, saveOnly = false }: {
  configuration: EconomicConfiguration; nodes: Scenario['nodes']; onChange: (c: EconomicConfiguration) => void;
  saveOnly?: boolean;
  disabled: boolean; busy: boolean; dirty: boolean; save: (simulate: boolean) => void; discard: () => void;
}) {
  const [section, setSection] = useState<Section>('General');
  const root = useRef<HTMLDivElement>(null);
  const change = (path: Path, value: unknown) => onChange(updateEconomicField(c, path, value));
  const nodeName = (id: string) => nodes.find(n => n.id === id)?.name ?? id;
  function submit(simulate: boolean) {
    const invalid = Array.from(root.current?.querySelectorAll<HTMLInputElement | HTMLSelectElement>('input,select') ?? []).find(input => !input.checkValidity());
    if (invalid) {
      setSection(invalid.closest<HTMLElement>('[data-section]')!.dataset.section as Section);
      requestAnimationFrame(() => { let parent = invalid.parentElement; while (parent) { if (parent instanceof HTMLDetailsElement) parent.open = true; parent = parent.parentElement; } invalid.reportValidity(); });
      return;
    }
    save(simulate);
  }
  function field(key: string, value: unknown, path: Path) {
    const label = labels[key] ?? key;
    if (key === 'id' || key === 'direction') return null;
    if (typeof value === 'boolean') return <label key={key} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={value} onChange={e => change(path, e.target.checked)} />{label}</label>;
    let options = enums[key];
    if (['nodeId', 'deliveryNodeId'].includes(key)) options = nodes.map(n => [n.id, n.name]);
    if (['taxEntityId', 'counterpartyId'].includes(key)) options = c.taxEntities.map(e => [e.id, e.name]);
    if (options) {
      if (value && !options.some(([id]) => id === value)) options = [...options, [String(value), String(value)]];
      return <label key={key} className="flex min-w-0 flex-col gap-2 text-sm font-medium text-ink-600">{label}<select className="min-w-0 rounded-lg border border-ink-200 bg-white p-3" value={String(value ?? '')} onChange={e => change(path, e.target.value || null)}>
        <option value="">Sin asignación explícita</option>{options.map(([id, text]) => <option key={id} value={id}>{text}</option>)}
      </select></label>;
    }
    const optional = ['lossExpiryYears', 'annualQuantity', 'cashDate'].includes(key);
    const isNumber = numeric.has(key);
    const isPercent = percentages.has(key);
    return <TextField key={key} label={label} className="min-w-0 w-full" type={isNumber ? 'number' : /Date$/.test(key) || key === 'validFrom' || key === 'validTo' ? 'date' : 'text'}
      step={isNumber ? 'any' : undefined} required={!optional} min={isNumber ? 0 : undefined}
      value={value == null || typeof value === 'number' && !Number.isFinite(value) ? '' : isPercent ? Number((Number(value) * 100).toPrecision(15)) : String(value)}
      onChange={e => change(path, isNumber ? e.target.value === '' ? optional ? null : NaN : Number(e.target.value) / (isPercent ? 100 : 1) : e.target.value || (optional ? null : ''))} />;
  }
  function fields(value: object, path: Path) {
    return <div className="grid min-w-0 gap-4 sm:grid-cols-2">{Object.entries(value).map(([key, item]) => {
      if (Array.isArray(item)) return <div key={key} className="space-y-3 sm:col-span-2"><h4 className="text-sm font-semibold">{key === 'occurrences' ? 'Fechas de reconocimiento y cobro / pago' : key}</h4>
        {item.map((entry, i) => <div key={i} className="rounded-lg border border-ink-100 p-3">{typeof entry === 'object' ? fields(entry, [...path, key, i]) : field(key, entry, [...path, key, i])}</div>)}
        {key === 'occurrences' && <Button variant="secondary" onClick={() => change([...path, key], [...item, { recognitionDate: (c.startYear + 1) + '-12-31', cashDate: (c.startYear + 1) + '-12-31' }])}>Agregar fecha</Button>}
      </div>;
      return field(key, item, [...path, key]);
    })}</div>;
  }
  function ruleCards(direction: 'INCOME' | 'EXPENSE') {
    const entries = [
      ...c.adjustments.map((rule, i) => ({ rule, path: ['adjustments', i] as Path, source: 'General del escenario' })),
      ...c.nodeProfiles.flatMap((p, i) => p.rules.map((rule, j) => ({ rule, path: ['nodeProfiles', i, 'rules', j] as Path, source: nodeName(p.nodeId) }))),
      ...c.contracts.map((contract, i) => ({ rule: contract.revenueRule, path: ['contracts', i, 'revenueRule'] as Path, source: 'Contrato · ' + nodeName(contract.deliveryNodeId) })),
    ].filter(({ rule }) => rule.direction === direction);
    return <div className="space-y-5">{entries.map(({ rule, path, source }) => <div key={path.join('.')} className="space-y-4 rounded-xl border border-ink-200 p-4"><h3 className="font-semibold">{rule.concept}</h3><p className="text-sm text-ink-500">{source}</p>{fields(rule, path)}</div>)}
      {!entries.length && <p className="text-sm text-ink-500">No hay conceptos cargados en esta sección.</p>}
      <Button variant="secondary" onClick={() => {
        const concept = (direction === 'INCOME' ? 'Ingreso ' : 'Costo ') + crypto.randomUUID().slice(0, 8);
        const next = structuredClone(c);
        next.adjustments.push({ id: crypto.randomUUID(), concept, taxEntityId: c.taxEntities[0].id, counterpartyId: null, direction, cashClassification: 'CASH', driver: 'FIXED', unitValue: 0, unit: c.currency, currency: c.currency, validFrom: (c.startYear + 1) + '-01-01', validTo: (c.startYear + c.years) + '-12-31', occurrences: Array.from({ length: c.years }, (_, i) => ({ recognitionDate: (c.startYear + i + 1) + '-12-31', cashDate: (c.startYear + i + 1) + '-12-31' })) });
        next.taxTreatments.push({ concept, taxEntityId: c.taxEntities[0].id, validFrom: c.startYear + '-01-01', validTo: (c.startYear + c.years) + '-12-31', classification: direction === 'INCOME' ? 'TAXABLE' : 'DEDUCTIBLE', deductibleFraction: direction === 'INCOME' ? 0 : 1 });
        onChange(next);
      }}>{direction === 'INCOME' ? 'Agregar ingreso' : 'Agregar costo'}</Button>
    </div>;
  }
  const actions = <div className="flex flex-wrap justify-between gap-3 rounded-lg border border-ink-200 bg-white p-4">
    <Button variant="ghost" disabled={disabled || busy || !dirty} onClick={discard}>Descartar cambios</Button><div className="flex flex-wrap gap-3">
      <Button variant="secondary" disabled={disabled || busy} onClick={() => saveOnly ? save(false) : submit(false)}>Guardar</Button>
      {!saveOnly && <Button disabled={disabled || busy} loading={busy} onClick={() => submit(true)}>Guardar y simular</Button>}</div></div>;
  return <div ref={root} className="space-y-4">
    <p className="text-sm text-ink-600">Esta configuración incluye entidades y reglas propias. Podés editarlas por sección. Guardar conserva como borrador los datos incompletos; se conservan las participaciones, los contratos y las fechas de cobro y pago.</p>
    {actions}<div className="grid gap-4 xl:grid-cols-[9rem_minmax(0,1fr)]">
      <nav aria-label="Secciones de configuración" className="flex flex-wrap gap-2 xl:flex-col">{sections.map(s => <button key={s} type="button" aria-current={s === section ? 'page' : undefined} onClick={() => setSection(s)} className={'rounded-lg px-3 py-2 text-left text-sm ' + (s === section ? 'bg-brand-50 font-semibold text-brand-800' : 'text-ink-600')}>{s}</button>)}</nav>
      <Card>{sections.map(s => <fieldset key={s} data-section={s} hidden={section !== s} disabled={disabled || busy} className="min-w-0 space-y-5 disabled:opacity-75"><h2 className="text-lg font-semibold">{s}</h2>
        {s === 'General' && <>{fields({ startYear: c.startYear, years: c.years, currency: c.currency, wacc: c.wacc }, [])}<p className="text-sm text-ink-500">Si cambiás el horizonte, revisá también las fechas de las reglas y los tratamientos fiscales.</p><details><summary className="cursor-pointer text-sm font-semibold">Conversiones de unidades</summary><div className="mt-4 space-y-4">{c.conversions.map((v, i) => <div key={i} className="rounded-lg border border-ink-200 p-4">{fields(v, ['conversions', i])}</div>)}</div></details></>}
        {s === 'Inversión' && <>{c.assets.map((a, i) => <div key={a.id} className="space-y-3 rounded-lg border border-ink-200 p-4"><h3 className="font-semibold">{a.concept}</h3>{fields(a, ['assets', i])}</div>)}{!c.assets.length && <p>No hay activos cargados.</p>}<Button variant="secondary" onClick={() => {
          const next = structuredClone(c), concept = 'Activo ' + crypto.randomUUID().slice(0, 8), entity = c.taxEntities[0].id;
          next.assets.push({ id: crypto.randomUUID(), concept, nodeId: null, taxEntityId: entity, currency: c.currency, cost: 0, residualValue: 0, purchaseDate: c.startYear + '-12-31', paymentDate: c.startYear + '-12-31', serviceDate: (c.startYear + 1) + '-01-01', usefulLifeMonths: c.years * 12 });
          next.taxTreatments.push(...[{ concept, classification: 'CAPITALIZABLE', deductibleFraction: 0 }, { concept: concept + '.depreciation', classification: 'DEDUCTIBLE', deductibleFraction: 1 }].map(t => ({ ...t, taxEntityId: entity, validFrom: c.startYear + '-01-01', validTo: (c.startYear + c.years) + '-12-31' })));
          onChange(next);
        }}>Agregar activo</Button></>}
        {s === 'Ingresos' && ruleCards('INCOME')}{s === 'Costos' && ruleCards('EXPENSE')}
        {s === 'Impuestos' && <>{c.taxEntities.map((e, i) => <div key={e.id} className="space-y-3 rounded-lg border border-ink-200 p-4"><h3 className="font-semibold">{e.name}</h3>{fields(e, ['taxEntities', i])}</div>)}
          <h3 className="font-semibold">Entidades consolidadas</h3>{c.taxEntities.map(e => <label key={e.id} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={c.boundary.includes(e.id)} onChange={event => change(['boundary'], event.target.checked ? [...c.boundary, e.id] : c.boundary.filter(id => id !== e.id))} />{e.name}</label>)}
          <details><summary className="cursor-pointer font-semibold">Participaciones por nodo</summary><div className="mt-4 space-y-4">{c.nodeProfiles.map((p, i) => <div key={p.nodeId} className="space-y-3"><h4>{nodeName(p.nodeId)}</h4>{p.ownership.map((o, j) => <div key={j}>{fields(o, ['nodeProfiles', i, 'ownership', j])}</div>)}</div>)}</div></details>
          <details><summary className="cursor-pointer font-semibold">Tratamientos por concepto</summary><div className="mt-4 space-y-4">{c.taxTreatments.map((t, i) => <div key={i} className="rounded-lg border border-ink-200 p-4">{fields(t, ['taxTreatments', i])}</div>)}</div></details></>}
      </fieldset>)}</Card>
    </div>{actions}
  </div>;
}
