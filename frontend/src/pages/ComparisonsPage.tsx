import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { comparisonsApi } from '../api/comparisons';
import { economicsApi } from '../api/economics';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { ComparisonBuilder, type ComparisonDraft } from '../components/comparison/ComparisonBuilder';
import { ComparisonResults } from '../components/comparison/ComparisonResults';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Modal, ModalFooter } from '../components/ui/Modal';
import { TextArea } from '../components/ui/TextArea';
import { TextField } from '../components/ui/TextField';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';
import type { ComparisonResult, ComparisonSummary, SavedComparison } from '../types/comparison';
import type { Scenario } from '../types/economics';
import { DEFAULT_RATE_POINTS, DEFAULT_RELEVANCE_PERCENT, thresholdsForForm, toRequest } from '../utils/comparison';

const EMPTY_DRAFT: ComparisonDraft = {
  items: [{ versionId: '', evaluationId: '' }, { versionId: '', evaluationId: '' }],
  relevancePercent: String(DEFAULT_RELEVANCE_PERCENT),
  ratePoints: String(DEFAULT_RATE_POINTS),
};

const draftKey = (draft: ComparisonDraft) => JSON.stringify(toRequest(draft.items, draft.relevancePercent, draft.ratePoints));

export function ComparisonsPage() {
  const { projectId = '', comparisonId } = useParams();
  // Remount per project so no state of one project leaks into another.
  return <ComparisonsWorkspace key={projectId} projectId={projectId} comparisonId={comparisonId ?? null} />;
}

function ComparisonsWorkspace({ projectId, comparisonId }: { projectId: string; comparisonId: string | null }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { activeProject } = useActiveProject();
  const canEdit = user?.platformRole === 'ADMIN' || (activeProject?.id === projectId && activeProject.myRole != null);

  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [saved, setSaved] = useState<ComparisonSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);

  const [draft, setDraft] = useState<ComparisonDraft>(EMPTY_DRAFT);
  const [result, setResult] = useState<ComparisonResult | null>(null);
  const [comparedKey, setComparedKey] = useState('');
  const [open, setOpen] = useState<SavedComparison | null>(null);
  const [comparing, setComparing] = useState(false);
  const [compareError, setCompareError] = useState('');

  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<ComparisonSummary | null>(null);

  const reloadSaved = useCallback(() => comparisonsApi.list(projectId).then(setSaved), [projectId]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([economicsApi.scenarios(projectId), comparisonsApi.list(projectId)])
      .then(([versions, comparisons]) => {
        if (cancelled) return;
        setScenarios(versions); setSaved(comparisons); setLoadError(''); setLoading(false);
      })
      .catch(error => {
        if (cancelled) return;
        setLoadError(getErrorMessage(error, 'No se pudieron cargar los escenarios del proyecto.')); setLoading(false);
      });
    return () => { cancelled = true; };
  }, [projectId, retry]);

  // Opening a saved comparison shows its stored definition and its recalculated result.
  useEffect(() => {
    if (!comparisonId) { setOpen(null); return; }
    let cancelled = false;
    setComparing(true); setCompareError('');
    comparisonsApi.get(projectId, comparisonId).then(comparison => {
      if (cancelled) return;
      const next: ComparisonDraft = {
        items: comparison.items.map(item => ({ versionId: item.versionId, evaluationId: item.evaluationId ?? '' })),
        ...thresholdsForForm(comparison.relevanceThreshold, comparison.rateThreshold),
      };
      setOpen(comparison); setDraft(next); setResult(comparison.result); setComparedKey(draftKey(next));
    }).catch(error => {
      if (!cancelled) { setOpen(null); setResult(null); setCompareError(getErrorMessage(error, 'No se pudo abrir la comparación.')); }
    }).finally(() => { if (!cancelled) setComparing(false); });
    return () => { cancelled = true; };
  }, [projectId, comparisonId]);

  async function compare() {
    setComparing(true); setCompareError('');
    try {
      const next = await comparisonsApi.preview(projectId, toRequest(draft.items, draft.relevancePercent, draft.ratePoints));
      setResult(next); setComparedKey(draftKey(draft));
      if (comparisonId) { setOpen(null); navigate(`/projects/${projectId}/comparisons`, { replace: true }); }
    } catch (error) {
      setCompareError(getErrorMessage(error, 'No se pudo comparar los escenarios.'));
    } finally {
      setComparing(false);
    }
  }

  function startNew() {
    setDraft(EMPTY_DRAFT); setResult(null); setComparedKey(''); setCompareError(''); setOpen(null);
    if (comparisonId) navigate(`/projects/${projectId}/comparisons`);
  }

  const stale = result !== null && comparedKey !== draftKey(draft);

  return <div className="space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold text-ink-800">Comparación de Escenarios</h1>
        <p className="mt-1 text-sm text-ink-500">
          Contrastá los resultados guardados de dos o más escenarios del proyecto: indicadores financieros, costos y resultados físicos.
        </p>
      </div>
      {(result || comparisonId) && <Button variant="ghost" onClick={startNew}>Nueva comparación</Button>}
    </header>

    {loading ? <p role="status">Cargando escenarios…</p> : loadError ? <Alert tone="error">{loadError}{' '}
      <Button variant="ghost" onClick={() => { setLoading(true); setRetry(n => n + 1); }}>Reintentar</Button></Alert> : <>
      <SavedComparisons projectId={projectId} saved={saved} openId={comparisonId} canEdit={canEdit} onDelete={setDeleting} />

      {open && <Card>
        <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Comparación guardada</p>
        <h2 className="mt-1 text-lg font-semibold text-ink-800">{open.name}</h2>
        {open.description && <p className="mt-1 text-sm text-ink-600">{open.description}</p>}
        <p className="mt-2 text-xs text-ink-500">Guardada el {new Date(open.createdAt).toLocaleString('es-AR')}. El resultado se recalcula a partir de las evaluaciones guardadas, que no cambian.</p>
      </Card>}

      <ComparisonBuilder projectId={projectId} scenarios={scenarios} draft={draft} onChange={setDraft}
        onCompare={compare} comparing={comparing} />

      {compareError && <Alert tone="error">{compareError}</Alert>}
      {stale && <Alert tone="info">Cambiaste la selección. Volvé a comparar para actualizar el resultado.</Alert>}

      {result && <>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-xl font-semibold text-ink-800">Resultado</h2>
          {canEdit && !open && <Button onClick={() => setSaving(true)} disabled={stale}>Guardar comparación</Button>}
        </div>
        <ComparisonResults result={result} />
      </>}
    </>}

    <SaveComparisonDialog open={saving} onClose={() => setSaving(false)} onSave={async (name, description) => {
      const stored = await comparisonsApi.save(projectId, toRequest(draft.items, draft.relevancePercent, draft.ratePoints, name, description));
      await reloadSaved();
      setSaving(false);
      navigate(`/projects/${projectId}/comparisons/${stored.id}`);
    }} />

    <DeleteComparisonDialog comparison={deleting} onClose={() => setDeleting(null)} onDelete={async id => {
      try {
        await comparisonsApi.remove(projectId, id);
      } catch (error) {
        if (getErrorStatus(error) !== 404) throw error;
      }
      await reloadSaved();
      setDeleting(null);
      if (comparisonId === id) startNew();
    }} />
  </div>;
}

function SavedComparisons({ projectId, saved, openId, canEdit, onDelete }: {
  projectId: string; saved: ComparisonSummary[]; openId: string | null; canEdit: boolean;
  onDelete: (comparison: ComparisonSummary) => void;
}) {
  if (!saved.length) return null;
  return <Card padded={false}>
    <h2 className="p-6 pb-3 text-lg font-semibold text-ink-800">Comparaciones guardadas</h2>
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead><tr className="border-y border-ink-200 bg-ink-50">
          <th scope="col" className="p-3">Nombre</th><th scope="col" className="p-3">Escenarios</th>
          <th scope="col" className="p-3">Guardada</th><th scope="col" className="p-3"><span className="sr-only">Acciones</span></th>
        </tr></thead>
        <tbody>{saved.map(c => <tr key={c.id} className={`border-b border-ink-100 ${c.id === openId ? 'bg-brand-50' : ''}`}>
          <td className="p-3 font-medium">
            <Link className="text-brand-800 underline" to={`/projects/${projectId}/comparisons/${c.id}`}
              aria-current={c.id === openId ? 'page' : undefined}>{c.name}</Link>
            {c.description && <p className="mt-1 text-xs text-ink-500">{c.description}</p>}
          </td>
          <td className="p-3">{c.scenarioCount}</td>
          <td className="p-3">{new Date(c.createdAt).toLocaleDateString('es-AR')}</td>
          <td className="p-3 text-right">{canEdit && <Button variant="ghost" size="compact" onClick={() => onDelete(c)}
            aria-label={`Eliminar ${c.name}`}>Eliminar</Button>}</td>
        </tr>)}</tbody>
      </table>
    </div>
  </Card>;
}

function SaveComparisonDialog({ open, onClose, onSave }: {
  open: boolean; onClose: () => void; onSave: (name: string, description: string) => Promise<void>;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { if (open) { setName(''); setDescription(''); setError(''); } }, [open]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) { setError('Poné un nombre para guardar la comparación.'); return; }
    setBusy(true); setError('');
    try {
      await onSave(name, description);
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudo guardar la comparación.'));
    } finally {
      setBusy(false);
    }
  }

  return <Modal open={open} onClose={onClose} title="Guardar comparación"
    subtitle="Se guarda qué resultados se comparan y los umbrales, para volver a abrirla después.">
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      {error && <Alert tone="error">{error}</Alert>}
      <TextField label="Nombre" value={name} maxLength={120} required onChange={event => setName(event.target.value)} />
      <TextArea label="Descripción (opcional)" value={description} rows={3} onChange={event => setDescription(event.target.value)} />
      <ModalFooter>
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button type="submit" loading={busy}>Guardar</Button>
      </ModalFooter>
    </form>
  </Modal>;
}

function DeleteComparisonDialog({ comparison, onClose, onDelete }: {
  comparison: ComparisonSummary | null; onClose: () => void; onDelete: (id: string) => Promise<void>;
}) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (comparison) setError(''); }, [comparison]);

  async function confirm() {
    if (!comparison) return;
    setBusy(true); setError('');
    try {
      await onDelete(comparison.id);
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudo eliminar la comparación.'));
    } finally {
      setBusy(false);
    }
  }

  return <Modal open={comparison !== null} onClose={onClose} title="Eliminar comparación">
    <div className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}
      <p className="text-sm text-ink-700">Se va a eliminar la comparación <span className="font-semibold text-ink-800">{comparison?.name}</span>. Los escenarios y sus evaluaciones no se modifican.</p>
      <ModalFooter>
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button variant="danger" onClick={confirm} loading={busy}>Eliminar</Button>
      </ModalFooter>
    </div>
  </Modal>;
}
