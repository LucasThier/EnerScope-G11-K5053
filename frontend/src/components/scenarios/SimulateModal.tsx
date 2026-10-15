import { useEffect, useState, type FormEvent } from 'react';
import { simulationsApi } from '../../api/simulations';
import { getErrorMessage } from '../../api/errors';
import type { SimulationDetail } from '../../types/simulation';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';

interface SimulateModalProps {
  open: boolean;
  onClose: () => void;
  versionId: string;
  versionName: string;
  /** Horizon to start from, e.g. the one the comparison already uses. */
  defaultYears?: number;
  onDone: (detail: SimulationDetail) => void;
}

// The backend caps both (RunSimulationRequestDTO): the run happens inside the request.
const MAX_YEARS = 40;
const MAX_ROUNDS = 50;

/**
 * Runs the simulation engine on a scenario. The run is synchronous on the
 * backend, so the dialog stays open with a spinner until it returns, then hands
 * the stored run to the caller (usually to open its results).
 */
export function SimulateModal({
  open,
  onClose,
  versionId,
  versionName,
  defaultYears = 20,
  onDone,
}: SimulateModalProps) {
  const [years, setYears] = useState(defaultYears);
  const [rounds, setRounds] = useState(10);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setYears(defaultYears);
      setError(null);
    }
  }, [open, defaultYears]);

  const invalid =
    !Number.isInteger(years) || years < 1 || years > MAX_YEARS ||
    !Number.isInteger(rounds) || rounds < 1 || rounds > MAX_ROUNDS;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (invalid) return;
    setRunning(true);
    setError(null);
    try {
      const res = await simulationsApi.run(versionId, { years, rounds });
      if (res.data.data) {
        onDone(res.data.data);
      }
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo simular el escenario'));
    } finally {
      setRunning(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={running ? () => undefined : onClose}
      title="Simular escenario"
      subtitle={versionName}
    >
      <form onSubmit={submit} className="flex flex-col gap-4">
        <p className="text-sm text-ink-600">
          El motor recorre el modelo hora por hora durante el horizonte elegido y repite la corrida
          varias veces (rondas Monte Carlo) para sortear las curvas de declinación. Junto con la
          producción se calculan los costos del modelo en el mismo horizonte.
        </p>
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Horizonte (años)"
            type="number"
            min={1}
            max={MAX_YEARS}
            value={years}
            onChange={(e) => setYears(Number(e.target.value))}
          />
          <TextField
            label="Rondas Monte Carlo"
            type="number"
            min={1}
            max={MAX_ROUNDS}
            value={rounds}
            onChange={(e) => setRounds(Number(e.target.value))}
          />
        </div>
        {invalid && (
          <p className="text-xs text-danger-700">
            El horizonte va de 1 a {MAX_YEARS} años y las rondas de 1 a {MAX_ROUNDS}.
          </p>
        )}
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={running}>
            Cancelar
          </Button>
          <Button type="submit" loading={running} disabled={invalid}>
            {running ? 'Simulando…' : 'Simular'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
