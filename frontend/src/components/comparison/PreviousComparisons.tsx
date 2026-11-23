import { Link } from 'react-router-dom';
import type { ComparisonSummary } from '../../types/comparison';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';

interface PreviousComparisonsProps {
  projectId: string;
  comparisons: ComparisonSummary[];
  canDelete: boolean;
  onDelete: (comparison: ComparisonSummary) => void;
}

/** Earlier comparison runs of the project, newest first; each one opens with the simulations it was made with. */
export function PreviousComparisons({ projectId, comparisons, canDelete, onDelete }: PreviousComparisonsProps) {
  return <section aria-labelledby="previous-comparisons" className="flex flex-col gap-3">
    <h2 id="previous-comparisons" className="text-lg font-semibold text-ink-800">Comparaciones anteriores</h2>
    {!comparisons.length ? <p className="text-sm text-ink-500">Todavía no se hizo ninguna comparación en este proyecto.</p> :
      <Card padded={false}>
        <ul className="divide-y divide-ink-100">
          {comparisons.map(comparison => <li key={comparison.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-4">
            <div className="min-w-0 flex-1">
              <Link to={`/projects/${projectId}/comparisons/${comparison.id}`}
                className="font-medium text-brand-800 underline-offset-2 hover:underline">{comparison.name}</Link>
              <p className="mt-1 text-xs text-ink-500">
                {comparison.scenarios.length} escenarios · {new Date(comparison.createdAt).toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })}
                {comparison.hasDeletedScenarios && <span className="text-warn-800"> · incluye escenarios eliminados</span>}
              </p>
              <p className="mt-1 truncate text-xs text-ink-500">{comparison.scenarios.join(' · vs · ')}</p>
            </div>
            <Link to={`/projects/${projectId}/comparisons/${comparison.id}`}
              className="text-sm font-semibold text-brand-800 hover:underline">Ver resultado</Link>
            {canDelete && <Button variant="ghost" size="compact" onClick={() => onDelete(comparison)}
              aria-label={`Eliminar ${comparison.name}`}>Eliminar</Button>}
          </li>)}
        </ul>
      </Card>}
  </section>;
}
