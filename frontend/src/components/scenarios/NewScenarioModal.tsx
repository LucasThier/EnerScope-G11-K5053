import { useEffect, useState, type FormEvent } from 'react';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';
import { controlClasses } from '../ui/controlClasses';

interface NewScenarioModalProps {
  open: boolean;
  onClose: () => void;
  projectId: string;
  versions: VersionSummary[];
  /** Pre-selected parent; empty starts a blank model. */
  defaultParentId?: string | null;
  onCreated: (version: VersionSummary) => void;
}

const NO_PARENT = '';

/**
 * Creates a scenario (a version of the project). With a parent it starts as a
 * copy of the parent's model — a branch — so it can be changed and compared
 * against it; without one it starts empty.
 */
export function NewScenarioModal({
  open,
  onClose,
  projectId,
  versions,
  defaultParentId,
  onCreated,
}: NewScenarioModalProps) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState<string>(defaultParentId ?? NO_PARENT);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setName('');
      setParentId(defaultParentId ?? NO_PARENT);
      setError(null);
    }
  }, [open, defaultParentId]);

  const trimmed = name.trim();
  const nameError = trimmed.length > 0 && trimmed.length < 2 ? 'Usá al menos 2 caracteres.' : undefined;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (trimmed.length < 2) return;
    setSaving(true);
    setError(null);
    try {
      const res = await projectsApi.createVersion(projectId, {
        name: trimmed,
        parentVersion: parentId || null,
      });
      if (res.data.data) {
        onCreated(res.data.data);
      }
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo crear el escenario'));
    } finally {
      setSaving(false);
    }
  }

  const parent = versions.find((v) => v.id === parentId);

  return (
    <Modal open={open} onClose={onClose} title="Nuevo escenario">
      <form onSubmit={submit} className="flex flex-col gap-4">
        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Ej.: Ampliación de licuefacción"
          maxLength={120}
          error={nameError}
        />
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-ink-600">Parte de</span>
          <select
            value={parentId}
            onChange={(e) => setParentId(e.target.value)}
            className={controlClasses}
          >
            <option value={NO_PARENT}>Ninguno (modelo vacío)</option>
            {versions.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
        <p className="text-sm text-ink-500">
          {parent
            ? `Se copia el modelo de "${parent.name}" (${parent.nodeCount} nodos). Los cambios que hagas en el nuevo escenario no afectan al original.`
            : 'El escenario empieza sin nodos; lo modelás desde el editor.'}
        </p>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={saving} disabled={trimmed.length < 2}>
            Crear escenario
          </Button>
        </div>
      </form>
    </Modal>
  );
}
