import { dateTime, irrText, money, percent } from '../utils/economicFormat';
import { useEffect, useRef, useState } from 'react';
import { isAxiosError } from 'axios';
import { Link, useBlocker, useNavigate, useParams } from 'react-router-dom';
import { economicsApi } from '../api/economics';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../hooks/useAuth';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EconomicEditor } from '../components/economics/EconomicEditor';
import { ConfigurationSummary, EconomicResults } from '../components/economics/EconomicResults';
import { emptyDraft, fromConfiguration, toConfiguration, validateDraft } from '../utils/economicDraft';
import type { EconomicDraft } from '../utils/economicDraft';
import type { EconomicConfiguration, Evaluation, Scenario } from '../types/economics';

export function EconomicsPage() {
  const { projectId = '', versionId = '' } = useParams();
  return <EconomicsWorkspace key={projectId + ':' + versionId} projectId={projectId} versionId={versionId} />;
}
function EconomicsWorkspace({ projectId, versionId }: { projectId: string; versionId: string }) {
  const { user } = useAuth();
  const { tab = 'configuration', evaluationId } = useParams();
  const navigate = useNavigate();
  const base = `/projects/${projectId}/versions/${versionId}/economics`;
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const [configuration, setConfiguration] = useState<EconomicConfiguration | null>(null);
  const [draft, setDraft] = useState<EconomicDraft>(emptyDraft);
  const [baseline, setBaseline] = useState('');
  const [editable, setEditable] = useState(false);
  const [canEdit, setCanEdit] = useState(false);
  const [evaluations, setEvaluations] = useState<Evaluation[]>([]);
  const [detail, setDetail] = useState<Evaluation | null>(null);
  const [detailError, setDetailError] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);
  const inFlight = useRef(false);
  const dirty = editable && !!baseline && JSON.stringify(draft) !== baseline;
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    (dirty || busy) && currentLocation.pathname.split('/economics')[0] !== nextLocation.pathname.split('/economics')[0]);
  useEffect(() => {
    if (!dirty && !busy) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, busy]);
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const [scenarios, members, history] = await Promise.all([
          economicsApi.scenarios(projectId),
          user?.platformRole === 'ADMIN' ? Promise.resolve(null) : projectsApi.members(projectId),
          economicsApi.history(projectId, versionId),
        ]);
        const selected = scenarios.find(s => s.id === versionId);
        if (!selected) throw new Error('El escenario no pertenece al proyecto o ya no está disponible.');
        let saved: EconomicConfiguration | null = null;
        try { saved = await economicsApi.configuration(projectId, versionId); }
        catch (e) {
          // The version was independently verified above. Other 404s must not
          // silently open a blank editor.
          if (!isAxiosError(e) || e.response?.status !== 404 || e.response.data?.message !== 'Economic configuration not found') throw e;
        }
        if (cancelled) return;
        const initial = saved ? fromConfiguration(saved) : emptyDraft();
        setScenario(selected); setConfiguration(saved); setEditable(initial !== null);
        if (initial) { setDraft(initial); setBaseline(JSON.stringify(initial)); }
        setEvaluations(history);
        setCanEdit(user?.platformRole === 'ADMIN' || !!members?.data.data?.some(m => m.userId === user?.id && m.active && m.permissions.includes('EDIT_PROJECT')));
      } catch (e) { if (!cancelled) setLoadError(getErrorMessage(e, 'No se pudo cargar la evaluación económica.')); }
      finally { if (!cancelled) setLoading(false); }
    }
    void load();
    return () => { cancelled = true; };
  }, [projectId, versionId, retry, user?.id, user?.platformRole]);
  useEffect(() => {
    if (!evaluationId) return;
    let cancelled = false;
    economicsApi.evaluation(projectId, versionId, evaluationId).then(e => { if (!cancelled) setDetail(e); })
      .catch(e => { if (!cancelled) setDetailError(getErrorMessage(e, 'No se pudo abrir la evaluación.')); });
    return () => { cancelled = true; };
  }, [projectId, versionId, evaluationId]);
  async function save(simulate: boolean) {
    if (inFlight.current || !canEdit || !editable || Object.values(validateDraft(draft, scenario?.nodes ?? [])).some(e => e.length)) return;
    inFlight.current = true; setBusy(true); setError(''); setNotice('');
    let saved = false;
    try {
      const response = await economicsApi.save(projectId, versionId, toConfiguration(draft));
      saved = true; setConfiguration(response); setBaseline(JSON.stringify(draft));
      if (simulate) {
        const evaluation = await economicsApi.evaluate(projectId, versionId);
        setEvaluations(old => [evaluation, ...old]); setDetail(evaluation);
        setNotice('Evaluación guardada. Sus resultados corresponden a la configuración utilizada al simular.');
        navigate(base + '/results');
      } else setNotice('Configuración guardada. Los resultados existentes conservan sus datos históricos.');
    } catch (e) {
      setError((saved && simulate ? 'La configuración se guardó. No se pudo confirmar la evaluación; revisá el historial antes de volver a simular. ' : '') + getErrorMessage(e, 'No se pudo completar la operación.'));
    } finally { inFlight.current = false; setBusy(false); }
  }
  if (loading) return <p role="status">Cargando evaluación económica…</p>;
  if (loadError || !scenario) return <Alert tone="error">{loadError || 'Escenario no disponible.'} <Button variant="ghost" onClick={() => { setLoadError(''); setLoading(true); setRetry(n => n + 1); }}>Reintentar</Button></Alert>;
  const selectedEvaluation = evaluationId ? detail?.id === evaluationId ? detail : null : evaluations[0];
  return <div className="space-y-5">
    <Link className="text-sm font-medium text-brand-800 underline" to={`/projects/${projectId}/versions`}>← Escenarios / {scenario.name}</Link>
    <header className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-2xl font-semibold text-ink-800">Evaluación económica</h1><span role="status" className="text-sm text-ink-500">{busy ? 'Guardando / simulando…' : dirty ? 'Cambios sin guardar' : 'Sin cambios pendientes'}</span></header>
    <nav aria-label="Evaluación económica" className="flex flex-wrap gap-4 border-b border-ink-200">{[['configuration','Configuración'],['results','Resultados'],['history','Historial']].map(([key,label]) => <Link key={key} to={base + (key === 'configuration' ? '' : '/' + key)} aria-current={tab === key ? 'page' : undefined} className={'px-2 py-3 text-sm ' + (tab === key ? 'border-b-2 border-brand-800 font-semibold text-brand-800' : 'text-ink-600')}>{label}</Link>)}</nav>
    {blocker.state === 'blocked' && <Alert>{busy ? 'Esperá a que termine la operación antes de salir.' : 'Tenés cambios sin guardar. ¿Querés salir y descartarlos?'} <Button variant="ghost" onClick={() => blocker.reset()}>Quedarme</Button>{!busy && <Button variant="secondary" onClick={() => blocker.proceed()}>Salir y descartar</Button>}</Alert>}
    {error && <Alert tone="error">{error}</Alert>}{notice && <Alert>{notice}</Alert>}
    {!canEdit && <Alert>Tenés acceso de consulta. No podés modificar la configuración ni ejecutar simulaciones.</Alert>}
    {tab === 'configuration' ? editable ? <EconomicEditor draft={draft} nodes={scenario.nodes} onChange={d => { setDraft(d); setNotice(''); }} disabled={!canEdit} busy={busy} dirty={dirty} save={save} discard={() => { if (window.confirm('¿Descartar todos los cambios sin guardar?')) setDraft(JSON.parse(baseline) as EconomicDraft); }} /> :
      <><Alert>Esta configuración utiliza opciones avanzadas. Se muestra en modo consulta para conservar sus reglas y tratamientos.</Alert>{configuration && <Card><ConfigurationSummary configuration={configuration} /></Card>}</> :
      tab === 'history' ? <Card><div className="mb-4 flex items-center justify-between gap-3"><h2 className="text-lg font-semibold">Evaluaciones guardadas</h2><Button variant="ghost" disabled={busy} onClick={() => { setError(''); void economicsApi.history(projectId, versionId).then(setEvaluations).catch(e => setError(getErrorMessage(e, 'No se pudo actualizar el historial.'))); }}>Actualizar</Button></div><p className="mb-4 text-sm text-ink-500">Cada evaluación conserva la configuración utilizada al simular.</p>
        {!evaluations.length ? <p>Todavía no hay evaluaciones guardadas.</p> : <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Fecha y hora','Moneda','WACC','VAN','TIR','Acción'].map(h => <th key={h} className="p-3">{h}</th>)}</tr></thead><tbody>{evaluations.map(e => <tr className="border-t border-ink-100" key={e.id}><td className="whitespace-nowrap p-3">{dateTime(e.createdAt)}</td><td className="p-3">{e.snapshot.configuration?.currency ?? '—'}</td><td className="p-3">{percent(e.snapshot.configuration?.wacc)}</td><td className="whitespace-nowrap p-3">{money(e.snapshot.result?.npv,e.snapshot.configuration?.currency)}</td><td className="p-3">{irrText(e.snapshot.result?.indicators?.irr)}</td><td className="p-3"><Link className="text-brand-800 underline" to={base + '/results/' + e.id} onClick={() => { setDetail(null); setDetailError(''); }}>Ver evaluación →</Link></td></tr>)}</tbody></table></div>}
      </Card> : tab === 'results' ? <>
        <Alert>Resultados guardados{evaluationId ? ' de la evaluación seleccionada' : ' de la evaluación más reciente'}. Los cambios actuales no modifican esta evaluación.</Alert>
        {evaluationId && detailError ? <Alert tone="error">{detailError}</Alert> : selectedEvaluation ? <EconomicResults key={selectedEvaluation.id} evaluation={selectedEvaluation} /> : <Card>{evaluationId ? 'Cargando evaluación…' : 'Todavía no hay resultados. Completá la configuración y ejecutá Guardar y simular.'}</Card>}
      </> : <Alert>Sección no disponible.</Alert>}
  </div>;
}
