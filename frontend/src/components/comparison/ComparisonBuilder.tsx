import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { economicsApi } from '../../api/economics';
import { getErrorMessage } from '../../api/errors';
import type { Scenario } from '../../types/economics';
import { MAX_SCENARIOS, MIN_SCENARIOS, draftError, type DraftItem } from '../../utils/comparison';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { SelectField } from '../ui/SelectField';
import { TextField } from '../ui/TextField';

export interface ComparisonDraft {
  items: DraftItem[];
  relevancePercent: string;
  ratePoints: string;
}

interface EvaluationOption { id: string; label: string }
type EvaluationState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; options: EvaluationOption[] };

interface ComparisonBuilderProps {
  projectId: string;
  scenarios: Scenario[];
  draft: ComparisonDraft;
  onChange: (draft: ComparisonDraft) => void;
  onCompare: () => void;
  comparing: boolean;
}

function optionLabel(createdAt: string, schemaVersion: number, rounds: number | undefined): string {
  const when = new Date(createdAt).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' });
  return schemaVersion >= 3 ? `${when} · Probabilística (${rounds ?? '?'} rondas)` : `${when} · Determinista`;
}

/**
 * Picks the results to compare: one row per scenario (version + one of its saved
 * evaluations, or its latest), the first row being the baseline, plus the
 * relevance thresholds.
 */
export function ComparisonBuilder({ projectId, scenarios, draft, onChange, onCompare, comparing }: ComparisonBuilderProps) {
  const [evaluations, setEvaluations] = useState<Record<string, EvaluationState>>({});
  const versionIds = [...new Set(draft.items.map(item => item.versionId).filter(Boolean))];
  const missing = versionIds.filter(id => !evaluations[id]);

  useEffect(() => {
    if (!missing.length) return;
    setEvaluations(current => ({ ...current, ...Object.fromEntries(missing.map(id => [id, { status: 'loading' } as const])) }));
    for (const versionId of missing) {
      economicsApi.history(projectId, versionId)
        .then(list => setEvaluations(current => ({
          ...current,
          [versionId]: { status: 'ready', options: list.map(e => ({ id: e.id, label: optionLabel(e.createdAt, e.snapshot.schemaVersion, e.snapshot.simulation?.rounds)})) },
        })))
        .catch(error => setEvaluations(current => ({
          ...current,
          [versionId]: { status: 'error', message: getErrorMessage(error, 'No se pudieron cargar las evaluaciones.') },
        })));
    }
    // `missing` is derived from the draft and the cache; joining it keeps the dependency stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, missing.join(',')]);

  const update = (index: number, item: Partial<DraftItem>) =>
    onChange({ ...draft, items: draft.items.map((current, i) => i === index ? { ...current, ...item } : current) });
  const remove = (index: number) => onChange({ ...draft, items: draft.items.filter((_, i) => i !== index) });
  const makeBaseline = (index: number) =>
    onChange({ ...draft, items: [draft.items[index], ...draft.items.filter((_, i) => i !== index)] });
  const add = () => onChange({ ...draft, items: [...draft.items, { versionId: '', evaluationId: '' }] });

  const withoutEvaluations = draft.items.some(item => {
    const state = evaluations[item.versionId];
    return item.versionId && state?.status === 'ready' && state.options.length === 0;
  });
  const stillLoading = draft.items.some(item => item.versionId && evaluations[item.versionId]?.status !== 'ready');
  const error = draftError(draft.items, draft.relevancePercent, draft.ratePoints);

  return <Card>
    <div className="flex flex-col gap-1">
      <h2 className="text-lg font-semibold text-ink-800">Escenarios a comparar</h2>
      <p className="text-sm text-ink-500">
        Elegí entre {MIN_SCENARIOS} y {MAX_SCENARIOS} resultados. El primero es la línea base contra la que se miden los demás.
      </p>
    </div>

    {!scenarios.length ? <p className="mt-6 text-sm text-ink-500">Este proyecto todavía no tiene escenarios.</p> :
      <ol className="mt-6 flex flex-col gap-4">
        {draft.items.map((item, index) => {
          const state = item.versionId ? evaluations[item.versionId] : undefined;
          const empty = state?.status === 'ready' && state.options.length === 0;
          return <li key={index} className="rounded-control border border-ink-100 p-4">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <span className={`text-sm font-semibold ${index === 0 ? 'text-brand-800' : 'text-ink-700'}`}>
                {index === 0 ? 'Línea base' : `Escenario ${index + 1}`}
              </span>
              <div className="flex gap-2">
                {index > 0 && <Button variant="ghost" size="compact" onClick={() => makeBaseline(index)}>Usar como línea base</Button>}
                {draft.items.length > MIN_SCENARIOS && <Button variant="ghost" size="compact" onClick={() => remove(index)}
                  aria-label={`Quitar ${index === 0 ? 'línea base' : `escenario ${index + 1}`}`}>Quitar</Button>}
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <SelectField label="Escenario (versión)" value={item.versionId}
                onChange={event => update(index, { versionId: event.target.value, evaluationId: '' })}>
                <option value="">Elegí un escenario…</option>
                {scenarios.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </SelectField>
              <SelectField label="Resultado (evaluación)" value={item.evaluationId}
                disabled={!item.versionId || state?.status !== 'ready' || empty}
                error={state?.status === 'error' ? state.message : undefined}
                hint={state?.status === 'loading' ? 'Cargando evaluaciones…' : undefined}
                onChange={event => update(index, { evaluationId: event.target.value })}>
                <option value="">Última evaluación</option>
                {state?.status === 'ready' && state.options.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
              </SelectField>
            </div>
            {empty && <p className="mt-2 text-sm text-danger-700">
              Este escenario todavía no tiene evaluaciones.{' '}
              <Link className="text-brand-800 underline" to={`/projects/${projectId}/simulations`}>Simulalo primero</Link>.
            </p>}
          </li>;
        })}
      </ol>}

    {draft.items.length < MAX_SCENARIOS && scenarios.length > 0 &&
      <Button className="mt-4" variant="ghost" onClick={add}>+ Agregar escenario</Button>}

    <div className="mt-6 grid gap-4 md:grid-cols-2">
      <TextField label="Umbral de relevancia (%)" inputMode="decimal" value={draft.relevancePercent}
        hint="Cambio relativo desde el que una diferencia de montos o volúmenes se marca como relevante."
        onChange={event => onChange({ ...draft, relevancePercent: event.target.value })} />
      <TextField label="Umbral para tasas (puntos porcentuales)" inputMode="decimal" value={draft.ratePoints}
        hint="Para TIR, ROI y ROE. Los paybacks se marcan desde un año de diferencia."
        onChange={event => onChange({ ...draft, ratePoints: event.target.value })} />
    </div>

    {error && draft.items.every(item => item.versionId) && <Alert className="mt-4" tone="warning">{error}</Alert>}
    <div className="mt-6 flex justify-end">
      <Button onClick={onCompare} loading={comparing} disabled={!!error || withoutEvaluations || stillLoading}>Comparar</Button>
    </div>
  </Card>;
}
