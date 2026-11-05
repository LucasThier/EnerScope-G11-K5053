import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { FilterSelect } from '../ui/FilterSelect';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';
import { getErrorMessage } from '../../api/errors';
import { scenarioConfigurationApi } from '../../api/scenarioConfiguration';
import { createSimpleCopyDraft } from '../../utils/economicDraft';
import { initialScenario, prepareScenario } from '../../utils/scenarioConfiguration';
import { scenarioRevisionsApi } from '../../api/scenarioRevisions';
import { useVersions } from '../../hooks/useVersions';

interface NewVersionModalProps {
  projectId: string;
  open: boolean;
  onClose: () => void;
  onCreated: (versionId: string) => Promise<void> | void;
  defaultParentId?: string;
}

const NAME_MAX = 320;

export function NewVersionModal({
  projectId,
  open,
  onClose,
  onCreated,
  defaultParentId,
}: NewVersionModalProps) {
  const { versions, loading: loadingVersions, createVersion } = useVersions(open ? projectId : null);
  const [mode, setMode] = useState<'empty' | 'branch' | 'copy' | 'simple'>('branch');
  const [name, setName] = useState('');
  const [parentVersionId, setParentVersionId] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const inFlight = useRef(false);
  const [createdCopy, setCreatedCopy] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setName('');
      setCreatedCopy(null);
      setMode('branch');
      setNameError(null);
      setSubmitError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open || loadingVersions || versions.length === 0) {
      return;
    }
    if (defaultParentId && versions.some((version) => version.id === defaultParentId)) {
      setParentVersionId(defaultParentId);
      return;
    }
    const root = versions.find((version) => version.parentVersionId === null) ?? versions[0];
    setParentVersionId(root.id);
  }, [open, loadingVersions, versions, defaultParentId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (inFlight.current || createdCopy || loadingVersions) return;
    setSubmitError(null);
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('El nombre es obligatorio.');
      return;
    }
    setNameError(null);

    if (mode === 'simple' && !window.confirm('La copia simple reemplaza las reglas avanzadas por una entidad al 100 % y cobros al cierre del año. No conserva participaciones, quebrantos ni transferencias internas. Los originales se mantienen. Revisá la configuración antes de simular.')) return;
    inFlight.current = true;
    setSubmitting(true);
    try {
      let createdId: string;
      if (versions.length > 0 && mode !== 'empty') {
        const status = await scenarioRevisionsApi.status(projectId, parentVersionId);
        createdId = (await scenarioRevisionsApi.fork(projectId, parentVersionId, trimmedName, status.token, mode !== 'branch')).id;
      } else createdId = await createVersion({ name: trimmedName, parentVersion: null });
      setCreatedCopy(createdId);
      if (mode === 'simple') {
        const state = await scenarioConfigurationApi.get(projectId, createdId);
        if (state.configuration) {
          const draft = initialScenario(state);
          draft.editor = { mode: 'simple', value: createSimpleCopyDraft(state.configuration, {}) };
          await scenarioConfigurationApi.save(projectId, createdId, prepareScenario(draft, state.nodes.map(node => ({ id: node.id, name: node.name, type: node.type }))));
        }
      }
      await onCreated(createdId);
      onClose();
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo crear el escenario'));
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Nuevo escenario">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError && <Alert tone="error">{submitError}{createdCopy && <p>El escenario ya fue creado. <Link className="underline" to={`/projects/${projectId}/versions/${createdCopy}/economics/configuration`}>Revisar su configuración</Link></p>}</Alert>}

        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={nameError ?? undefined}
          placeholder="Escenario base 2026"
          maxLength={NAME_MAX}
          disabled={submitting}
        />

        {!loadingVersions && versions.length > 0 && <label className="flex flex-col gap-2 text-sm">Tipo de creación
          <select className="rounded-lg border border-ink-300 p-2 focus:ring-2 focus:ring-brand-600" disabled={submitting} value={mode} onChange={event => setMode(event.target.value as typeof mode)}>
            <option value="empty">Escenario vacío</option><option value="branch">Rama del escenario de origen</option><option value="copy">Copia independiente</option><option value="simple">Copia simple editable</option>
          </select>
          <span>Las ramas y copias toman la revisión publicada. No copian borradores ni resultados.</span>
        </label>}
        {!loadingVersions && versions.length > 0 && mode !== 'empty' && (
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium text-ink-600">Escenario de origen</span>
            <FilterSelect
              label="Escenario de origen"
              value={parentVersionId}
              options={versions.map((version) => ({ value: version.id, label: version.name }))}
              onChange={setParentVersionId}
            />
          </div>
        )}

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting} disabled={loadingVersions || !!createdCopy}>
            Crear escenario
          </Button>
        </div>
      </form>
    </Modal>
  );
}
