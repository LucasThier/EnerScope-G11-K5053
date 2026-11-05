import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { economicsApi } from '../../api/economics';
import { getErrorMessage } from '../../api/errors';
import type { EconomicJob, RevisionTag } from '../../types/economics';
import { jobLabels, jobPending } from '../../utils/probabilisticResults';
import { dateTime } from '../../utils/economicFormat';
import { Card } from '../ui/Card';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';

export function EconomicJobs({ projectId, versionId, canEdit, refreshToken, revisionFilter = 'all', onRevisions }: {
  projectId: string; versionId: string; canEdit: boolean; refreshToken: number; revisionFilter?: string; onRevisions?: (values: RevisionTag[]) => void;
}) {
  const [jobs, setJobs] = useState<EconomicJob[]>([]);
  const [collapsed, setCollapsed] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [retrying, setRetrying] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false, loading = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function load() {
      if (cancelled || loading || document.visibilityState === 'hidden') return;
      loading = true;
      try {
        const next = await economicsApi.jobs(projectId, versionId);
        if (!cancelled) {
          setJobs(next); setError(''); onRevisions?.(next.flatMap(job => job.revision ? [job.revision] : []));
          if (next.some(jobPending)) timer = setTimeout(() => { void load(); }, 2000);
        }
      } catch (e) { if (!cancelled) setError(getErrorMessage(e, 'No se pudo consultar el avance.')); }
      finally { loading = false; }
    }
    function visibility() { clearTimeout(timer); if (document.visibilityState === 'visible') void load(); }
    void load(); document.addEventListener('visibilitychange', visibility);
    return () => { cancelled = true; clearTimeout(timer); document.removeEventListener('visibilitychange', visibility); };
  }, [projectId, versionId, refreshToken, refresh, onRevisions]);
  async function repeat(job: EconomicJob) {
    setRetrying(job.id); setError('');
    try { await economicsApi.retryJob(projectId, versionId, job.id); setRefresh(n => n + 1); }
    catch (e) { setError(getErrorMessage(e, 'No se pudo repetir la ejecución.')); }
    finally { setRetrying(null); }
  }
  const visibleJobs = jobs.filter(job => revisionFilter === 'all' || (job.revision?.id ?? 'legacy') === revisionFilter);
  if (!jobs.length && !error) return null;
  return <Card><div className="mb-3 flex flex-wrap items-center justify-between gap-3">
    <h2 className="text-lg font-semibold">Ejecuciones de simulación</h2>
    <div className="flex items-center gap-2">
      <Button variant="ghost" onClick={() => setRefresh(n => n + 1)}>Actualizar avance</Button>
      <Button variant="ghost" aria-expanded={!collapsed} onClick={() => setCollapsed(value => !value)}>{collapsed ? 'Mostrar ejecuciones' : 'Minimizar'}</Button>
    </div></div>
    {collapsed && <p className="text-sm text-ink-600">{visibleJobs.length} ejecuciones · {visibleJobs.filter(jobPending).length} en curso</p>}
    {error && <Alert tone="error">{error}</Alert>}
    {!collapsed && <>
    <p className="mb-4 text-sm text-ink-600">Podés navegar mientras se simula. Repetir conserva las entradas y la semilla originales.</p>
    {error && <Alert tone="error">{error}</Alert>}
    {!visibleJobs.length && <p>No hay ejecuciones para esta revisión.</p>}
    <ul className="space-y-4">{visibleJobs.map(job => <li key={job.id} className="rounded-xl border border-ink-100 p-4">
      <div className="flex flex-wrap justify-between gap-2"><span>{dateTime(job.createdAt)}</span><strong>{jobLabels[job.status]}</strong></div>
      <p className="mt-2 text-sm text-ink-600">{job.revision ? `Revisión ${job.revision.number} · ${job.revision.outdated ? 'Desactualizada' : 'Actual'}` : 'Histórico sin revisión'}</p>
      <p className="mt-2 text-sm" role={jobPending(job) ? 'status' : undefined}>{job.completedRounds} de {job.rounds} rondas · Semilla: <span className="break-all">{job.seed}</span></p>
      {jobPending(job) && <progress className="mt-2 w-full accent-brand-800" aria-label="Rondas terminadas" max={job.rounds} value={job.completedRounds} />}
      {job.errorMessage && <p className="mt-2 text-sm text-red-700">{job.errorMessage}</p>}
      {job.evaluationId && <Link className="mt-2 inline-block text-brand-800 underline" to={`/projects/${projectId}/versions/${versionId}/economics/results/${job.evaluationId}`}>Ver resultados →</Link>}
      {canEdit && ['FAILED','INTERRUPTED'].includes(job.status) && <Button className="mt-2" variant="secondary" disabled={retrying !== null} onClick={() => { void repeat(job); }}>Repetir desde el inicio</Button>}
    </li>)}</ul></>
    }
  </Card>;
}
