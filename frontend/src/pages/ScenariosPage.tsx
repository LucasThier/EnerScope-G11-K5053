import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { economicsApi } from '../api/economics';
import { getErrorMessage } from '../api/errors';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { TextField } from '../components/ui/TextField';
import { formatDate } from '../utils/date';
import type { Scenario } from '../types/economics';

export function ScenariosPage() {
  const { projectId = '' } = useParams();
  const [state, setState] = useState<{ project: string; scenarios: Scenario[]; error: string; loading: boolean }>({ project: '', scenarios: [], error: '', loading: true });
  const [search, setSearch] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let cancelled = false;
    economicsApi.scenarios(projectId).then(scenarios => {
      if (!cancelled) setState({ project: projectId, scenarios, error: '', loading: false });
    }).catch(error => { if (!cancelled) setState({ project: projectId, scenarios: [], error: getErrorMessage(error, 'No se pudieron cargar los escenarios.'), loading: false }); });
    return () => { cancelled = true; };
  }, [projectId, retry]);
  const loading = state.loading || state.project !== projectId;
  const filtered = state.scenarios.filter(s => s.name.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
  return <div className="space-y-6">
    <header><h1 className="text-2xl font-semibold text-ink-800">Simulaciones / Escenarios</h1>
      <p className="mt-1 text-sm text-ink-500">Seleccioná un escenario para evaluar su economía.</p></header>
    {loading ? <p role="status">Cargando escenarios…</p> : state.error ? <Alert tone="error">{state.error} <Button variant="ghost" onClick={() => { setState(s => ({ ...s, loading: true })); setRetry(n => n + 1); }}>Reintentar</Button></Alert> :
      <Card><TextField label="Buscar escenario" value={search} onChange={e => setSearch(e.target.value)} />
        {!state.scenarios.length ? <p className="mt-6 text-sm text-ink-500">Este proyecto todavía no tiene escenarios. La evaluación económica estará disponible cuando se haya creado un escenario físico.</p> :
          <div className="mt-6 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b border-ink-200"><th className="p-3">Escenario</th><th className="p-3">Actualizado</th><th className="p-3">Acción</th></tr></thead><tbody>
            {filtered.map(s => <tr key={s.id} className="border-b border-ink-100"><td className="p-3 font-medium">{s.name}</td><td className="p-3">{formatDate(s.lastModified)}</td><td className="p-3"><Link className="font-semibold text-brand-800 underline" to={s.id + '/economics'}>Abrir →</Link></td></tr>)}
          </tbody></table>{!filtered.length && <p className="p-3">No hay escenarios que coincidan con la búsqueda.</p>}</div>}
      </Card>}
  </div>;
}
