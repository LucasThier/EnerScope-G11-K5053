import { useState } from 'react';
import type { ReactNode } from 'react';
import type { Scenario } from '../../types/economics';
import { sections, validateDraft } from '../../utils/economicDraft';
import type { EconomicDraft, RuleDraft, AssetDraft, Section } from '../../utils/economicDraft';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';

function Select({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: ReactNode }) {
  return <label className="flex flex-col gap-2 text-sm font-medium text-ink-600">{label}<select className="rounded-lg border border-ink-300 bg-white px-3 py-2 text-ink-800 focus:ring-2 focus:ring-brand-400" value={value} onChange={e => onChange(e.target.value)}>{children}</select></label>;
}
export const assumptions = 'Una entidad con participación del 100 %. Régimen hipotético: ingresos gravados, costos deducibles y depreciación lineal. Sin traslado de pérdidas. Cobros, pagos operativos e impuestos al cierre del mismo año. Sin inflación, financiamiento, cambio de moneda ni cobro automático del residual. Los costos físicos existentes no se importan.';

export function EconomicEditor({ draft: d, onChange, nodes, disabled, save, busy, dirty, discard }: {
  draft: EconomicDraft; onChange: (d: EconomicDraft) => void; nodes: Scenario['nodes'];
  disabled: boolean; save: (simulate: boolean) => void; busy: boolean; dirty: boolean; discard: () => void;
}) {
  const [section, setSection] = useState<Section>('General');
  const errors = validateDraft(d, nodes);
  const valid = Object.values(errors).every(e => !e.length);
  const field = (key: 'startYear' | 'years' | 'currency' | 'wacc' | 'entityName' | 'taxRate', label: string) =>
    <TextField label={label} value={d[key]} onChange={e => onChange({ ...d, [key]: e.target.value })} />;
  const updateRule = (r: RuleDraft) => {
    // A node/metric has exactly one conversion, shared across income and expense.
    onChange({ ...d, rules: d.rules.map(old => old.id === r.id ? r :
      r.driver !== 'FIXED' && old.nodeId === r.nodeId && old.driver === r.driver
        ? { ...old, unit: r.unit, factor: r.factor } : old) });
  };
  const addRule = () => onChange({ ...d, rules: [...d.rules, {
    id: crypto.randomUUID(), concept: '', direction: section === 'Ingresos' ? 'INCOME' : 'EXPENSE',
    driver: 'FIXED', nodeId: '', value: '', unit: '', factor: '',
    from: String(Number(d.startYear) + 1), to: String(Number(d.startYear) + Number(d.years)),
  }] });
  const addAsset = () => onChange({ ...d, assets: [...d.assets, {
    id: crypto.randomUUID(), concept: '', nodeId: '', cost: '', residual: '0',
    purchase: d.startYear + '-12-31', service: String(Number(d.startYear) + 1) + '-01-01', life: '',
  }] });
  const nodeOptions = <>{nodes.map(n => <option key={n.id} value={n.id}>{n.name} · {n.type}</option>)}</>;
  return <div className="space-y-4">
    <div className="grid gap-4 xl:grid-cols-[9rem_minmax(0,1fr)_13rem]">
      <nav aria-label="Secciones de configuración" className="flex flex-wrap gap-2 xl:flex-col">{sections.map(s =>
        <button key={s} type="button" onClick={() => setSection(s)} aria-current={section === s ? 'page' : undefined}
          className={'rounded-lg px-3 py-2 text-left text-sm ' + (section === s ? 'bg-brand-50 font-semibold text-brand-800' : 'text-ink-600 hover:bg-ink-100')}>{s}</button>)}</nav>
      <Card><h2 className="mb-4 text-lg font-semibold text-ink-800">{section}</h2>
        <fieldset disabled={disabled || busy} className="min-w-0 space-y-5 disabled:opacity-75">
          {section === 'General' && <><div className="grid gap-4 sm:grid-cols-2">{field('startYear', 'Año de inversión')}{field('years', 'Años operativos')}
            <Select label="Moneda" value={d.currency} onChange={currency => onChange({ ...d, currency })}>{Intl.supportedValuesOf('currency').map(c => <option key={c}>{c}</option>)}</Select>
            {field('wacc', 'WACC anual (%)')}</div><p className="text-sm text-ink-500">La inversión corresponde al período 0. La operación comienza el año siguiente y abarca {d.years || '…'} años.</p></>}
          {section === 'Impuestos' && <>{field('entityName', 'Nombre de la entidad')}{field('taxRate', 'Tasa de impuesto (%)')}<p className="text-sm leading-6 text-ink-500">{assumptions}</p></>}
          {section === 'Inversion' && <>
            <p className="text-sm text-ink-500">La compra y el pago se registran en la misma fecha. La depreciación comienza al poner el activo en servicio.</p>
            {d.assets.map((a, i) => {
              const update = (patch: Partial<AssetDraft>) => onChange({ ...d, assets: d.assets.map(old => old.id === a.id ? { ...a, ...patch } : old) });
              return <div key={a.id} className="space-y-4 rounded-lg border border-ink-200 p-4"><div className="flex items-center justify-between"><h3 className="font-semibold">Activo {i + 1}</h3><Button variant="ghost" onClick={() => onChange({ ...d, assets: d.assets.filter(old => old.id !== a.id) })}>Quitar</Button></div>
                <TextField label="Concepto" maxLength={107} value={a.concept} onChange={e => update({ concept: e.target.value })} />
                <Select label="Nodo (opcional)" value={a.nodeId} onChange={nodeId => update({ nodeId })}><option value="">General del escenario</option>{nodeOptions}</Select>
                <div className="grid gap-4 sm:grid-cols-2">
                  <TextField label={'Importe (' + d.currency + ')'} value={a.cost} onChange={e => update({ cost: e.target.value })} />
                  <TextField label="Valor residual" value={a.residual} onChange={e => update({ residual: e.target.value })} />
                  <TextField label="Compra y pago" type="date" value={a.purchase} onChange={e => update({ purchase: e.target.value })} />
                  <TextField label="Puesta en servicio" type="date" value={a.service} onChange={e => update({ service: e.target.value })} />
                  <TextField label="Vida útil (meses)" value={a.life} onChange={e => update({ life: e.target.value })} />
                </div></div>;
            })}
            {!d.assets.length && <p className="text-sm text-ink-500">No agregaste activos. Sin inversión inicial neta, el recupero puede no aplicar.</p>}
            <Button variant="secondary" onClick={addAsset}>Agregar activo</Button>
          </>}
          {(section === 'Ingresos' || section === 'Costos') && <>
            {d.rules.filter(r => r.direction === (section === 'Ingresos' ? 'INCOME' : 'EXPENSE')).map((r, i) => {
              const update = (patch: Partial<RuleDraft>) => updateRule({ ...r, ...patch });
              const changeSource = (patch: Partial<RuleDraft>) => {
                const next = { ...r, ...patch };
                const shared = d.rules.find(old => old.id !== r.id && old.nodeId === next.nodeId && old.driver === next.driver);
                updateRule(shared ? { ...next, unit: shared.unit, factor: shared.factor } : next);
              };
              return <div key={r.id} className="space-y-4 rounded-lg border border-ink-200 p-4"><div className="flex items-center justify-between"><h3 className="font-semibold">{section === 'Ingresos' ? 'Ingreso' : 'Costo'} {i + 1}</h3><Button variant="ghost" onClick={() => onChange({ ...d, rules: d.rules.filter(old => old.id !== r.id) })}>Quitar</Button></div>
                <TextField label="Concepto" maxLength={120} value={r.concept} onChange={e => update({ concept: e.target.value })} />
                <Select label="Modalidad" value={r.driver} onChange={driver => changeSource({ driver: driver as RuleDraft['driver'] })}>
                  <option value="FIXED">Monto anual fijo</option><option value="OUTPUT_VOLUME">Por volumen producido</option>
                  {section === 'Ingresos' && <option value="EXPORTED_VOLUME">Por volumen exportado</option>}
                </Select>
                {r.driver !== 'FIXED' && <><Select label="Nodo" value={r.nodeId} onChange={nodeId => changeSource({ nodeId })}><option value="">Seleccioná un nodo</option>{nodeOptions}</Select>
                  <TextField label="Unidad comercial" value={r.unit} onChange={e => update({ unit: e.target.value })} />
                  <TextField label="Unidades comerciales por unidad del simulador" value={r.factor} onChange={e => update({ factor: e.target.value })} />
                  <p className="text-sm text-ink-500">Conversión compartida por todas las reglas de este nodo y volumen. Se utiliza el volumen anual simulado; exportación corresponde a la salida del buque.</p></>}
                <TextField label={(r.driver === 'FIXED' ? 'Monto anual' : 'Tarifa por unidad') + ' (' + d.currency + ')'} value={r.value} onChange={e => update({ value: e.target.value })} />
                <div className="grid gap-4 sm:grid-cols-2"><TextField label="Desde el año" value={r.from} onChange={e => update({ from: e.target.value })} /><TextField label="Hasta el año" value={r.to} onChange={e => update({ to: e.target.value })} /></div>
                <p className="text-sm text-ink-500">Reconocimiento y {section === 'Ingresos' ? 'cobro' : 'pago'} al cierre de cada año de aplicación.</p>
              </div>;
            })}
            <Button variant="secondary" onClick={addRule}>Agregar {section === 'Ingresos' ? 'ingreso' : 'costo'}</Button>
          </>}
        </fieldset>
      </Card>
      <Card><h2 className="font-semibold">Resumen</h2><ul className="mt-4 space-y-3 text-sm">{sections.map(s => <li key={s}><button className="text-left underline" onClick={() => setSection(s)}>{s}: {errors[s].length ? 'Pendiente' : 'Completo'}</button></li>)}</ul>
        <ul className="mt-4 space-y-2 text-sm text-ink-600">{[...new Set(errors[section])].map(e => <li key={e}>{e}</li>)}</ul>
        {!valid && <p className="mt-4 text-sm text-ink-500">Completá las secciones pendientes para guardar.</p>}
      </Card>
    </div>
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-ink-200 bg-white p-4">
      <Button variant="ghost" disabled={!dirty || busy} onClick={discard}>Descartar cambios</Button>
      <div className="flex flex-wrap gap-3"><Button variant="secondary" disabled={disabled || busy || !valid} onClick={() => save(false)}>Guardar</Button>
        <Button loading={busy} disabled={disabled || !valid} onClick={() => save(true)}>Guardar y simular</Button></div>
    </div>
    <p className="text-xs leading-5 text-ink-500">{assumptions}</p>
  </div>;
}
