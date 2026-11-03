import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { comparisonApi } from '../api/comparison';
import { economicsApi } from '../api/economics';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../hooks/useAuth';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { TextField } from '../components/ui/TextField';
import { comparisonErrors, comparisonMetrics, comparisonNumber, newCriterion } from '../utils/comparison';
import { formatDate } from '../utils/date';
import type { ComparisonRequest, ComparisonResult, ComparisonSummary, ComparisonCell } from '../types/comparison';
import type { Evaluation } from '../types/economics';

interface Choice { versionId: string; scenarioName: string; evaluation: Evaluation }
const inputClass = 'w-full rounded-lg border border-ink-200 bg-surface p-2 text-sm text-ink-800 focus:outline-none focus:ring-2 focus:ring-focus';
const emptyCell = (contextId: string): ComparisonCell => ({ contextId, versionId: '', evaluationId: '', deliveryNodeIds: [] });
const initial = (): ComparisonRequest => ({ name: '', scopeDescription: '', assumptionsConfirmed: false,
  contexts: [{ id: 'base', name: 'Base', probability: 100 }],
  alternatives: ['A', 'B'].map(id => ({ id, name: `Alternativa ${id}`, cells: [emptyCell('base')] })),
  criteria: [newCriterion('NPV', 100)],
});

export function ComparisonsPage() {
  const { projectId = '' } = useParams();
  return <ComparisonWorkspace key={projectId} projectId={projectId} />;
}

function ComparisonWorkspace({ projectId }: { projectId: string }) {
  const { user } = useAuth();
  const [request, setRequest] = useState(initial);
  const [choices, setChoices] = useState<Choice[]>([]);
  const [history, setHistory] = useState<ComparisonSummary[]>([]);
  const [canEdit, setCanEdit] = useState(false);
  const [result, setResult] = useState<ComparisonResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [retry, setRetry] = useState(0);
  const alive = useRef(true);
  const inFlight = useRef(false);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [scenarios, stored, members] = await Promise.all([economicsApi.scenarios(projectId), comparisonApi.list(projectId), user?.platformRole === 'ADMIN' ? Promise.resolve(null) : projectsApi.members(projectId)]);
        const all = await Promise.all(scenarios.map(async s => (await economicsApi.history(projectId, s.id)).map(evaluation => ({ versionId: s.id, scenarioName: s.name, evaluation }))));
        if (!cancelled) { setChoices(all.flat()); setHistory(stored); setCanEdit(user?.platformRole === 'ADMIN' || !!members?.data.data?.some(m => m.userId === user?.id && m.active && m.permissions.includes('EDIT_PROJECT'))); setError(''); }
      } catch (e) { if (!cancelled) setError(getErrorMessage(e, 'No se pudieron cargar las evaluaciones.')); }
      finally { if (!cancelled) setLoading(false); }
    }
    void load(); return () => { cancelled = true; };
  }, [projectId, user?.id, user?.platformRole, retry]);

  function change(next: ComparisonRequest) { setRequest(next); setResult(null); setSaved(''); setError(''); }
  function cellChange(alternativeId: string, contextId: string, patch: Partial<ComparisonCell>) {
    change({ ...request, alternatives: request.alternatives.map(a => a.id !== alternativeId ? a : { ...a, cells: a.cells.map(c => c.contextId === contextId ? { ...c, ...patch } : c) }) });
  }
  async function calculate(save: boolean) {
    if (inFlight.current) return;
    const errors = comparisonErrors(request);
    if (errors.length) { setError(errors.join(' ')); return; }
    inFlight.current = true; setBusy(true); setError(''); setSaved('');
    try {
      if (save) {
        const stored = await comparisonApi.save(projectId, request);
        if (alive.current) { setResult(stored.result); setSaved(`Guardada el ${formatDate(stored.createdAt)}. Los cambios siguientes crearán una nueva comparación.`); setHistory(h => [{ id: stored.id, name: stored.configuration.name, createdAt: stored.createdAt }, ...h]); }
      } else { const calculated = await comparisonApi.preview(projectId, request); if (alive.current) setResult(calculated); }
    } catch (e) { if (alive.current) { setResult(null); setError(getErrorMessage(e, 'No se pudo calcular la comparación.')); } }
    finally { inFlight.current = false; if (alive.current) setBusy(false); }
  }
  async function open(id: string) {
    if (inFlight.current || !id) return;
    inFlight.current = true; setBusy(true); setError('');
    try { const stored = await comparisonApi.get(projectId, id); if (alive.current) { setRequest(stored.configuration); setResult(stored.result); setSaved(`Resultado histórico del ${formatDate(stored.createdAt)}. Editar y guardar crea una nueva comparación.`); } }
    catch (e) { if (alive.current) setError(getErrorMessage(e, 'No se pudo abrir la comparación.')); }
    finally { inFlight.current = false; if (alive.current) setBusy(false); }
  }
  if (loading) return <p role="status">Cargando evaluaciones y comparaciones…</p>;
  const hasVolume = request.criteria.some(c => c.metric === 'DELIVERED_VOLUME');
  return <div className="space-y-6">
    <header><h1 className="text-2xl font-semibold text-ink-800">Comparar escenarios</h1><p className="mt-2 text-sm text-ink-500">Compará decisiones bajo contextos comunes y explicá la recomendación con tus prioridades.</p></header>
    {error && <Alert tone="error">{error} {!choices.length && <Button variant="ghost" onClick={() => { setLoading(true); setRetry(n => n + 1); }}>Reintentar carga</Button>}</Alert>}
    {saved && <p role="status" className="text-sm text-brand-800">{saved}</p>}
    <label className="block text-sm">Comparaciones guardadas<select aria-label="Comparaciones guardadas" className={inputClass} value="" disabled={busy} onChange={e => void open(e.target.value)}><option value="">Abrir una comparación…</option>{history.map(h => <option key={h.id} value={h.id}>{h.name} · {formatDate(h.createdAt)}</option>)}</select></label>
    {!choices.length && <Alert>No hay evaluaciones disponibles. <Link className="underline" to={`/projects/${projectId}/simulations`}>Ir a simulación</Link>.</Alert>}
    <fieldset disabled={busy} className="space-y-6">
      <Card><div className="space-y-4"><TextField label="Nombre de la comparación" maxLength={500} value={request.name} onChange={e => change({ ...request, name: e.target.value })} />
        <label className="block text-sm">Alcance y supuestos de los contextos<textarea className={inputClass} maxLength={500} rows={3} placeholder="Qué decisión se compara, qué incluye el proyecto y qué cambia en cada contexto." value={request.scopeDescription} onChange={e => change({ ...request, scopeDescription: e.target.value })} /></label>
        <p className="text-sm text-ink-500">Se exige igual moneda, año inicial, horizonte, WACC y perímetro económico. Los precios y las condiciones operativas pueden variar según el contexto.</p>
        <label className="flex gap-2 text-sm"><input type="checkbox" checked={request.assumptionsConfirmed} onChange={e => change({ ...request, assumptionsConfirmed: e.target.checked })} />Confirmo que las evaluaciones tienen alcance equivalente y representan los supuestos del contexto asignado.</label>
      </div></Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Contextos y probabilidades</h2>
        <p className="mb-4 text-sm text-ink-500">Estas probabilidades expresan tus supuestos. Son independientes de las rondas aleatorias del simulador.</p>
        <div className="space-y-3">{request.contexts.map(c => <div key={c.id} className="grid items-end gap-3 sm:grid-cols-[1fr_180px_auto]">
          <TextField label="Contexto" value={c.name} maxLength={500} onChange={e => change({ ...request, contexts: request.contexts.map(x => x.id === c.id ? { ...x, name: e.target.value } : x) })} />
          <NumberInput label={`Probabilidad de ${c.name} (%)`} value={c.probability} onChange={v => change({ ...request, contexts: request.contexts.map(x => x.id === c.id ? { ...x, probability: v ?? NaN } : x) })} />
          <Button variant="ghost" disabled={request.contexts.length === 1} onClick={() => change({ ...request, contexts: request.contexts.filter(x => x.id !== c.id), alternatives: request.alternatives.map(a => ({ ...a, cells: a.cells.filter(x => x.contextId !== c.id) })) })}>Quitar contexto</Button>
        </div>)}</div>
        <div className="mt-4 flex flex-wrap items-center gap-4"><Button variant="ghost" disabled={request.contexts.length >= 10} onClick={() => { const id = crypto.randomUUID(); change({ ...request, contexts: [...request.contexts, { id, name: '', probability: NaN }], alternatives: request.alternatives.map(a => ({ ...a, cells: [...a.cells, emptyCell(id)] })) }); }}>Agregar contexto</Button><span className="text-sm">Total: {comparisonNumber(request.contexts.reduce((s, c) => s + c.probability, 0))} %</span></div>
      </Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Alternativas y resultados históricos</h2>
        <p className="mb-4 text-sm text-ink-500">Asigná una evaluación por alternativa y contexto. Para probar otros precios o demanda, generá previamente las evaluaciones correspondientes.</p>
        <div className="space-y-5">{request.alternatives.map(a => <section key={a.id} className="space-y-3 rounded-lg border border-ink-200 p-4">
          <div className="flex items-end gap-3"><div className="flex-1"><TextField label="Alternativa" maxLength={500} value={a.name} onChange={e => change({ ...request, alternatives: request.alternatives.map(x => x.id === a.id ? { ...x, name: e.target.value } : x) })} /></div><Button variant="ghost" disabled={request.alternatives.length <= 2} onClick={() => change({ ...request, alternatives: request.alternatives.filter(x => x.id !== a.id) })}>Quitar</Button></div>
          {a.cells.map(c => { const selected = choices.find(x => x.versionId === c.versionId && x.evaluation.id === c.evaluationId); return <div key={c.contextId} className="space-y-2">
            <label className="block text-sm">{request.contexts.find(x => x.id === c.contextId)?.name || 'Contexto sin nombre'}<select className={inputClass} value={c.versionId + ':' + c.evaluationId} onChange={e => { const selected = choices.find(x => x.versionId + ':' + x.evaluation.id === e.target.value); cellChange(a.id, c.contextId, { versionId: selected?.versionId || '', evaluationId: selected?.evaluation.id || '', deliveryNodeIds: [] }); }}>
              <option value=":">Elegir evaluación…</option>{c.evaluationId && !selected && <option value={c.versionId + ':' + c.evaluationId}>Evaluación histórica {c.evaluationId} (no disponible para recalcular)</option>}
              {choices.map(x => <option key={x.versionId + x.evaluation.id} value={x.versionId + ':' + x.evaluation.id}>{x.scenarioName} · {formatDate(x.evaluation.createdAt)} · {x.evaluation.id.slice(0, 8)} · {x.evaluation.snapshot.simulation?.rounds || 1} ronda(s)</option>)}
            </select></label>
            {selected && <p className="text-xs text-ink-500">{selected.evaluation.snapshot.configuration?.currency} · {selected.evaluation.snapshot.configuration?.startYear} · {selected.evaluation.snapshot.configuration?.years} años · WACC {comparisonNumber((selected.evaluation.snapshot.configuration?.wacc ?? NaN) * 100)} %</p>}
            {hasVolume && selected && <div className="rounded-lg bg-ink-50 p-3"><p className="mb-2 text-sm">Puntos de entrega: seleccioná salidas independientes, nunca nodos consecutivos.</p>{selected.evaluation.snapshot.physicalNodes?.map(n => <label key={n.id} className="mr-4 inline-flex gap-2 text-sm"><input type="checkbox" checked={c.deliveryNodeIds.includes(n.id)} onChange={e => cellChange(a.id, c.contextId, { deliveryNodeIds: e.target.checked ? [...c.deliveryNodeIds, n.id] : c.deliveryNodeIds.filter(id => id !== n.id) })} />{n.name || n.id}</label>)}</div>}
          </div>; })}
        </section>)}</div>
        <Button className="mt-4" variant="ghost" disabled={request.alternatives.length >= 20} onClick={() => change({ ...request, alternatives: [...request.alternatives, { id: crypto.randomUUID(), name: '', cells: request.contexts.map(c => emptyCell(c.id)) }] })}>Agregar alternativa</Button>
      </Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Indicadores, pesos y límites</h2>
        <p className="mb-4 text-sm text-ink-500">Las referencias asignan 0 y 100 puntos; los valores intermedios se interpolan y los extremos se limitan. Los límites son obligatorios en todos los contextos, incluso con peso 0. Para ejecuciones probabilísticas, se compara el promedio de las rondas: cumplir un límite no garantiza cumplirlo en cada realización.</p>
        <div className="space-y-4">{comparisonMetrics.map(meta => { const criterion = request.criteria.find(c => c.metric === meta.metric); return <section key={meta.metric} className="rounded-lg border border-ink-200 p-3">
          <label className="flex items-center gap-2 font-medium"><input type="checkbox" checked={!!criterion} onChange={e => change({ ...request, criteria: e.target.checked ? [...request.criteria, newCriterion(meta.metric)] : request.criteria.filter(c => c.metric !== meta.metric) })} />{meta.label} · {meta.maximize ? 'Maximizar' : 'Minimizar'}</label>
          <p className="mt-1 text-xs text-ink-500">{meta.description}</p>
          {criterion && <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">{(['weight', 'unfavorable', 'favorable', 'minimum', 'maximum'] as const).map(field => <NumberInput key={field} label={`${{ weight: 'Peso (%)', unfavorable: 'Referencia 0 puntos', favorable: 'Referencia 100 puntos', minimum: 'Mínimo obligatorio', maximum: 'Máximo obligatorio' }[field]}${field === 'weight' ? '' : ` (${meta.unit})`}`} value={criterion[field]} optional={field === 'minimum' || field === 'maximum'} onChange={value => change({ ...request, criteria: request.criteria.map(c => c.metric === meta.metric ? { ...c, [field]: value ?? (field === 'minimum' || field === 'maximum' ? null : NaN) } : c) })} />)}</div>}
        </section>; })}</div>
        <p className="mt-4 text-sm">Total de pesos: {comparisonNumber(request.criteria.reduce((s, c) => s + c.weight, 0))} %. VAN, TIR y recupero representan objetivos relacionados: revisá sus pesos conjuntos.</p>
        <p className="mt-2 text-sm text-ink-500">Producción diferida y utilización de capacidad: no disponibles en los resultados históricos actuales.</p>
      </Card>
      <div className="flex flex-wrap gap-3"><Button disabled={!choices.length} onClick={() => void calculate(false)}>Calcular comparación</Button><Button variant="ghost" disabled={!canEdit || !result || result.status === 'BLOCKED' || !!saved} onClick={() => void calculate(true)}>Guardar comparación</Button><Button variant="ghost" onClick={() => change(initial())}>Nueva comparación</Button></div>
    </fieldset>
    {busy && <p role="status">Procesando comparación…</p>}
    {result && <ComparisonResults projectId={projectId} request={request} result={result} />}
  </div>;
}

function NumberInput({ label, value, optional = false, onChange }: { label: string; value: number | null; optional?: boolean; onChange: (value: number | null) => void }) {
  return <label className="block text-sm">{label}<input className={inputClass} type="number" step="any" placeholder={optional ? 'Sin límite' : 'Ingresar valor'} value={value == null || !Number.isFinite(value) ? '' : value} onChange={e => onChange(e.target.value === '' ? null : e.target.valueAsNumber)} /></label>;
}

function ComparisonResults({ projectId, request, result }: { projectId: string; request: ComparisonRequest; result: ComparisonResult }) {
  const winners = result.alternatives.filter(a => result.winnerIds.includes(a.id));
  return <Card><div className="space-y-5" aria-live="polite">
    <h2 className="text-xl font-semibold">{result.status === 'BLOCKED' ? 'Comparación bloqueada' : result.status === 'NO_ELIGIBLE_ALTERNATIVE' ? 'Ninguna alternativa cumple todos los límites' : winners.length > 1 ? `Empate: ${winners.map(a => a.name).join(', ')}` : `Alternativa recomendada: ${winners[0]?.name}`}</h2>
    <p className="text-sm text-ink-500">{result.currency} · Año inicial {result.startYear} · Horizonte {result.years} años. El ranking pondera los puntajes por las probabilidades de contexto; la recomendación depende de estos supuestos.</p>
    {!!result.issues.length && <Alert tone="error"><ul className="list-disc pl-5">{result.issues.map((issue, i) => <li key={i}>{issue}</li>)}</ul></Alert>}
    <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Posición</th><th className="p-2">Alternativa</th><th className="p-2">Puntaje esperado</th><th className="p-2">Peor contexto</th><th className="p-2">Estado</th></tr></thead><tbody>{[...result.alternatives].sort((a, b) => (a.rank ?? Infinity) - (b.rank ?? Infinity)).map(a => <tr key={a.id} className="border-t border-ink-100"><td className="p-2">{a.rank ?? '—'}</td><td className="p-2">{a.name}</td><td className="p-2">{comparisonNumber(a.score)}</td><td className="p-2">{comparisonNumber(a.worstScore)}</td><td className="p-2">{result.status === 'BLOCKED' ? 'Sin recomendación' : a.eligible ? 'Elegible' : 'Fuera de límites'}</td></tr>)}</tbody></table></div>
    {request.contexts.map(context => <section key={context.id} className="space-y-3"><h3 className="font-semibold">{context.name} · Probabilidad {comparisonNumber(context.probability)} %</h3>{[...result.alternatives].sort((a, b) => (b.contexts.find(c => c.contextId === context.id)?.score ?? -1) - (a.contexts.find(c => c.contextId === context.id)?.score ?? -1)).map(a => { const c = a.contexts.find(x => x.contextId === context.id)!; return <details key={a.id} className="rounded-lg border border-ink-200 p-3"><summary className="cursor-pointer font-medium">{a.name}: {comparisonNumber(c.score)} puntos · {c.eligible ? 'Cumple' : 'Revisar datos o límites'} · {c.rounds} ronda(s)</summary>
      <Link className="mt-2 inline-block text-sm text-brand-800 underline" to={`/projects/${projectId}/versions/${c.versionId}/economics/results/${c.evaluationId}`}>Ver evaluación fuente</Link>
      <div className="mt-3 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Indicador</th><th className="p-2">Valor {c.rounds > 1 ? 'promedio' : ''}</th><th className="p-2">Puntaje</th><th className="p-2">Aporte ponderado</th><th className="p-2">Límites</th></tr></thead><tbody>{c.values.map(v => <tr key={v.metric} className="border-t border-ink-100"><td className="p-2">{comparisonMetrics.find(m => m.metric === v.metric)?.label}</td><td className="p-2">{comparisonNumber(v.value)} {v.unit}{v.issue && <p className="text-red-700">{v.issue}</p>}</td><td className="p-2">{comparisonNumber(v.score)}</td><td className="p-2"><span>{comparisonNumber(v.contribution)}</span>{v.contribution != null && <progress aria-label={`Aporte de ${v.metric}`} max={100} value={v.contribution} className="ml-2 h-2 w-24 accent-green-700" />}</td><td className="p-2">{v.meetsLimits ? 'Cumple' : 'No cumple / no disponible'}</td></tr>)}</tbody></table></div>
    </details>; })}</section>)}
    <p className="text-sm text-ink-500">Para explorar sensibilidad, cambiá pesos o probabilidades y volvé a calcular. Se reutilizan resultados guardados; no se ejecuta el simulador.</p>
  </div></Card>;
}
