import type { ComparableScenario } from '../../types/comparison';
import { MAX_SCENARIOS, MIN_SCENARIOS, scenarioLabel, selectionCheck, seriesColor } from '../../utils/comparison';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { CloseIcon } from '../ui/icons';

interface SelectionPanelProps {
  selected: ComparableScenario[];
  /** Scenarios whose simulation is being submitted right now. */
  starting: Record<string, boolean>;
  comparing: boolean;
  onRemove: (scenarioId: string) => void;
  onSimulatePending: (scenarios: ComparableScenario[]) => void;
  onCompare: () => void;
}

/** "Tu comparación": the chosen scenarios in order, what is still missing and the Compare button. */
export function SelectionPanel({ selected, starting, comparing, onRemove, onSimulatePending, onCompare }: SelectionPanelProps) {
  const check = selectionCheck(selected.map(s => starting[s.scenarioId] ? { ...s, status: 'SIMULATING' as const } : s));
  return <Card className="flex flex-col gap-4">
    <div>
      <h2 className="text-lg font-semibold text-ink-800">Tu comparación</h2>
      <p className="mt-1 text-sm text-ink-500">
        {selected.length ? `${selected.length} de hasta ${MAX_SCENARIOS} escenarios, comparados entre sí.`
          : `Elegí entre ${MIN_SCENARIOS} y ${MAX_SCENARIOS} escenarios de la lista.`}
      </p>
    </div>

    {selected.length > 0 && <ol className="flex flex-col gap-2">
      {selected.map((scenario, index) => <li key={scenario.scenarioId}
        className="flex items-center gap-3 rounded-control border border-ink-100 bg-surface px-3 py-2">
        <span aria-hidden="true" className={`h-3 w-3 shrink-0 rounded-full ${seriesColor(index).bg}`} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium text-ink-800">{scenarioLabel(scenario)}</span>
          {scenario.status !== 'UP_TO_DATE' && <span className="block truncate text-xs text-danger-700">{
            scenario.status === 'SIMULATING' ? 'Simulando' : 'Falta simular'}</span>}
        </span>
        <button type="button" onClick={() => onRemove(scenario.scenarioId)} aria-label={`Quitar ${scenarioLabel(scenario)}`}
          className="shrink-0 rounded-control p-1 text-ink-500 hover:bg-ink-50 hover:text-ink-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus">
          <CloseIcon className="h-4 w-4" />
        </button>
      </li>)}
    </ol>}

    {check.toSimulate.length > 0 && <Button variant="secondary" onClick={() => onSimulatePending(check.toSimulate)}>
      Simular {check.toSimulate.length === 1 ? 'el escenario pendiente' : `los ${check.toSimulate.length} escenarios pendientes`}
    </Button>}
    {check.blocked.length > 0 && <p className="text-xs text-ink-600">
      {check.blocked.map(scenarioLabel).join(', ')} no se {check.blocked.length === 1 ? 'puede' : 'pueden'} simular
      desde acá: revisá su configuración o tus permisos.
    </p>}

    <div className="flex flex-col gap-2">
      <Button className="w-full" onClick={onCompare} loading={comparing} disabled={!check.canCompare}>Comparar escenarios</Button>
      {check.reason && <p className="text-center text-xs text-ink-500" role="status">{check.reason}</p>}
    </div>
  </Card>;
}
