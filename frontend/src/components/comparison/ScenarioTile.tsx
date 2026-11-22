import { Link } from 'react-router-dom';
import type { ComparableScenario } from '../../types/comparison';
import { TONE_CLASSES, scenarioStatusView, seriesColor } from '../../utils/comparison';
import { Button } from '../ui/Button';
import { Spinner } from '../ui/Spinner';
import { AlertIcon, CheckIcon, InfoIcon } from '../ui/icons';

interface ScenarioTileProps {
  scenario: ComparableScenario;
  /** Position in the selection (it decides the colour), or -1 when not selected. */
  position: number;
  disabled: boolean;
  starting: boolean;
  error: string | null;
  onToggle: () => void;
  onSimulate: () => void;
}

/** One scenario in the picker: select it, see whether its latest simulation is usable, simulate it in place. */
export function ScenarioTile({ scenario, position, disabled, starting, error, onToggle, onSimulate }: ScenarioTileProps) {
  const selected = position >= 0;
  const view = scenarioStatusView(scenario);
  const simulating = scenario.status === 'SIMULATING' || starting;
  const needsRun = scenario.status === 'OUTDATED' || scenario.status === 'NEVER_SIMULATED';
  const Icon = view.tone === 'good' ? CheckIcon : view.tone === 'bad' ? AlertIcon : InfoIcon;

  return <li className={`relative flex flex-col rounded-card border bg-surface shadow-raise transition-colors motion-reduce:transition-none ${
    selected ? 'border-brand-700 ring-2 ring-brand-700' : 'border-ink-100 hover:border-ink-300'}`}>
    <label className={`flex flex-1 items-start gap-3 p-4 ${disabled && !selected ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'}`}>
      <input type="checkbox" className="mt-1 h-5 w-5 shrink-0 accent-brand-800" checked={selected}
        aria-label={`Comparar ${scenario.versionName} (${scenario.projectName})`}
        disabled={disabled && !selected} onChange={onToggle} />
      <span className="flex min-w-0 flex-1 flex-col gap-2">
        <span className="flex items-start justify-between gap-2">
          <span className="min-w-0">
            <span className="block truncate text-base font-semibold text-ink-800">{scenario.versionName}</span>
            <span className="block truncate text-xs text-ink-500">{scenario.projectName}</span>
          </span>
          {selected && <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-ink-50 px-2 py-0.5 text-xs font-medium text-ink-700">
            <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${seriesColor(position).bg}`} />
            Elegido
          </span>}
        </span>
        <span className={`inline-flex w-fit items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${
          TONE_CLASSES[simulating ? 'neutral' : view.tone]}`}>
          {simulating ? <Spinner className="h-3.5 w-3.5" /> : <Icon className="h-3.5 w-3.5" />}
          {starting && scenario.status !== 'SIMULATING' ? 'Iniciando simulación…' : view.label}
        </span>
        {view.detail && <span className="text-xs text-ink-500">{view.detail}</span>}
      </span>
    </label>

    {(needsRun || error) && !simulating && <div className="flex flex-col gap-2 border-t border-ink-100 px-4 py-3">
      {error && <p className="text-xs text-danger-700">{error}</p>}
      {needsRun && (!scenario.ready ? <p className="text-xs text-ink-600">
          No se puede simular todavía: {scenario.readinessErrors.join(' ') || 'la configuración está incompleta.'}{' '}
          <Link className="text-brand-800 underline" to={`/projects/${scenario.projectId}/versions/${scenario.versionId}/economics/configuration`}>
            Completar configuración</Link>
        </p>
        : !scenario.canSimulate ? <p className="text-xs text-ink-600">No tenés permiso para simular en este proyecto.</p>
        : <Button size="compact" variant="secondary" className="self-start" onClick={onSimulate}>
            {scenario.status === 'OUTDATED' ? 'Volver a simular' : 'Simular ahora'}
          </Button>)}
    </div>}
  </li>;
}
