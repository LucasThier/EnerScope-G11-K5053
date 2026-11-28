import { useState, type FormEvent } from 'react';
import { getErrorMessage } from '../../api/errors';
import type { ScenarioSummary } from '../../types/scenario';
import { DELETE_SCENARIO_NOTE, SCENARIO_NAME_MAX, validateScenarioName } from '../../utils/scenarioRules';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { TextField } from '../ui/TextField';

interface NameDialogProps {
  open: boolean;
  title: string;
  submitLabel: string;
  /** Explains what will happen; shown above the field. */
  description?: string;
  /** The name the field starts with (empty for a new scenario: the name is always typed by the person). */
  initialName?: string;
  /** The version's other scenarios, to catch a repeated name before asking the server. */
  scenarios: ScenarioSummary[];
  /** Set when renaming: the scenario may keep (or re-case) its own name. */
  selfId?: string;
  onSubmit: (name: string) => Promise<void>;
  onClose: () => void;
}

/** Asks for the name of a new, duplicated or renamed scenario. There is no suggested or generated name. */
export function ScenarioNameDialog(props: NameDialogProps) {
  return (
    <Modal open={props.open} onClose={props.onClose} title={props.title}>
      {props.open && <NameForm {...props} />}
    </Modal>
  );
}

function NameForm({ submitLabel, description, initialName = '', scenarios, selfId, onSubmit, onClose }: NameDialogProps) {
  const [name, setName] = useState(initialName);
  const [fieldError, setFieldError] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const checked = validateScenarioName(name, scenarios, selfId);
    if (!checked.ok) {
      setFieldError(checked.error);
      return;
    }
    setFieldError('');
    setError('');
    setBusy(true);
    try {
      await onSubmit(checked.name);
      onClose();
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudo guardar el escenario.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
      {description && <p className="text-sm text-ink-600">{description}</p>}
      {error && <Alert tone="error">{error}</Alert>}
      <TextField
        label="Nombre del escenario"
        value={name}
        onChange={event => setName(event.target.value)}
        error={fieldError || undefined}
        maxLength={SCENARIO_NAME_MAX}
        disabled={busy}
        autoFocus
      />
      <ModalFooter>
        <Button type="button" variant="ghost" onClick={onClose} disabled={busy}>Cancelar</Button>
        <Button type="submit" loading={busy}>{submitLabel}</Button>
      </ModalFooter>
    </form>
  );
}

interface DeleteDialogProps {
  scenario: ScenarioSummary | null;
  onConfirm: (scenario: ScenarioSummary) => Promise<void>;
  onClose: () => void;
}

/** Confirms a logical delete, saying that results and saved comparisons stay. */
export function DeleteScenarioDialog({ scenario, onConfirm, onClose }: DeleteDialogProps) {
  return (
    <Modal open={scenario !== null} onClose={onClose} title="Eliminar escenario">
      {scenario && <DeleteForm key={scenario.id} scenario={scenario} onConfirm={onConfirm} onClose={onClose} />}
    </Modal>
  );
}

function DeleteForm({ scenario, onConfirm, onClose }: { scenario: ScenarioSummary; onConfirm: DeleteDialogProps['onConfirm']; onClose: () => void }) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function confirm() {
    setBusy(true);
    setError('');
    try {
      await onConfirm(scenario);
      onClose();
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudo eliminar el escenario.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}
      <p className="text-sm text-ink-700">
        Se va a eliminar el escenario <span className="font-semibold text-ink-800">{scenario.name}</span>. {DELETE_SCENARIO_NOTE}
      </p>
      {scenario.defaultScenario && (
        <p className="text-sm text-ink-600">Es el escenario por defecto de la versión: el más antiguo de los que queden pasará a serlo.</p>
      )}
      <ModalFooter>
        <Button variant="ghost" onClick={onClose} disabled={busy}>Cancelar</Button>
        <Button variant="danger" onClick={confirm} loading={busy}>Eliminar</Button>
      </ModalFooter>
    </div>
  );
}
