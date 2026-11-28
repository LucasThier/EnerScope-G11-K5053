import { useNavigate } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { Modal, ModalFooter } from '../ui/Modal';
import { useField } from '../ui/useField';
import { FilePicker } from '../ui/FilePicker';
import { DownloadIcon } from '../ui/icons';
import { useVersionCsvImport } from '../../hooks/useVersionCsvImport';
import {
  BASE_SCENARIO_NAME,
  IDENTITY_HELP,
  MAX_DATA_ROWS,
  NODE_TYPE_HELP,
  formatProblem,
  hiddenProblemsMessage,
  importButtonLabel,
  importDestinations,
  importSummary,
  importedNodesMessage,
} from '../../utils/versionCsvImport';
import type { VersionAccess } from '../../types/versionAccess';

interface ImportVersionCsvModalProps {
  projectId: string;
  version: { id: string; name: string };
  /** The caller's access to the version (from `/access`); null denies the import. */
  access: VersionAccess | null;
  onImported: () => Promise<void> | void;
  onClose: () => void;
}

export function ImportVersionCsvModal({ projectId, version, access, onImported, onClose }: ImportVersionCsvModalProps) {
  const flow = useVersionCsvImport({
    versionId: version.id,
    versionName: version.name,
    access,
    onImported,
  });
  const navigate = useNavigate();
  const { fieldId, hintId, errorId, controlProps } = useField(undefined, {});
  const sending = flow.stage === 'sending';
  const destinations = importDestinations(projectId, version.id);
  const analysis = flow.analysis;
  const clean = analysis !== null && analysis.problems.length === 0;
  const canEditEconomics = access?.canEditEconomicConfiguration === true;
  const economicsHintId = `${fieldId}-economics-template`;
  // Once a file is ready to confirm, the examples and the column help give their room to the warnings.
  const readyToConfirm = clean && flow.blockers.length === 0;

  function go(path: string) {
    onClose();
    navigate(path);
  }

  return (
    <Modal
      open
      onClose={() => {
        if (!sending) {
          onClose();
        }
      }}
      title="Importar CSV"
      subtitle={`Versión «${version.name}»`}
      size="lg"
    >
      {flow.stage === 'result' ? (
        <div className="flex flex-col gap-4">
          <Alert tone="success">
            <span className="font-medium">{importedNodesMessage(flow.importedCount ?? 0)}</span>
            {analysis && <span className="mt-1 block">El archivo traía: {importSummary(analysis)}.</span>}
          </Alert>
          {analysis && analysis.scenarioCount > 0 && (
            <p className="text-sm text-ink-700">
              La configuración económica quedó en el «Escenario base» de esta versión. Revisala en Escenarios antes de
              simular.
            </p>
          )}
          <ModalFooter>
            <Button variant="ghost" onClick={onClose}>
              Cerrar
            </Button>
            <Button variant="secondary" onClick={() => go(destinations.scenarios)}>
              Ver escenarios
            </Button>
            <Button onClick={() => go(destinations.editor)}>Ver en el editor</Button>
          </ModalFooter>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {access === null || !access.canEdit ? (
            <Alert tone="info">No tenés permiso para importar nodos en esta versión.</Alert>
          ) : null}

          <p className="text-sm text-ink-700">
            Subí un solo archivo <code className="font-mono text-ink-800">.csv</code>. En el mismo archivo van los nodos
            y, si querés, sus conexiones y la economía del «{BASE_SCENARIO_NAME}». Se{' '}
            <span className="font-medium text-ink-800">agregan</span> a esta versión. Hasta 10 MB y{' '}
            {MAX_DATA_ROWS.toLocaleString('es-AR')} filas.
          </p>

          <Field label="Archivo CSV" fieldId={fieldId} hintId={hintId} errorId={errorId}>
            <FilePicker
              controlProps={controlProps}
              accept=".csv,text/csv"
              fileName={flow.fileName}
              disabled={sending || access === null || !access.canEdit}
              onSelect={flow.selectFile}
            />
          </Field>
          {!readyToConfirm && (
            <>
              <div className="-mt-2 flex flex-col items-start gap-1">
                <Button variant="link" className="min-h-6" onClick={() => flow.downloadTemplate('connections')}>
                  <DownloadIcon className="h-4 w-4 shrink-0" />
                  Descargar plantilla (nodos y conexiones)
                </Button>
                <div className="flex flex-wrap items-center gap-x-2 text-sm text-ink-600">
                  <span>Otros ejemplos:</span>
                  <Button variant="link" className="min-h-6" onClick={() => flow.downloadTemplate('operational')}>
                    solo nodos
                  </Button>
                  <span aria-hidden="true">·</span>
                  <Button
                    variant="link"
                    className="min-h-6"
                    disabled={!canEditEconomics}
                    aria-describedby={canEditEconomics ? undefined : economicsHintId}
                    onClick={() => flow.downloadTemplate('full')}
                  >
                    con economía
                  </Button>
                  {!canEditEconomics && (
                    <span id={economicsHintId} className="text-ink-500">
                      (necesita permiso para editar la economía)
                    </span>
                  )}
                </div>
              </div>
              <details className="text-sm">
                <summary className="min-h-6 cursor-pointer rounded-control py-0.5 font-medium text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus">
                  Ayuda sobre las columnas type e identity
                </summary>
                <div className="mt-2 flex flex-col gap-2 text-ink-500">
                  <p>{NODE_TYPE_HELP}</p>
                  <p>{IDENTITY_HELP}</p>
                </div>
              </details>
            </>
          )}

          {flow.readError && <Alert tone="error">{flow.readError}</Alert>}

          {analysis && analysis.problems.length > 0 && (
            <Alert tone="error">
              <p className="font-medium">El archivo tiene problemas y no se puede importar:</p>
              <ul className="mt-1 list-disc pl-5">
                {analysis.problems.map((problem) => (
                  <li key={`${problem.line}-${problem.message}`}>{formatProblem(problem)}</li>
                ))}
              </ul>
              {analysis.hiddenProblemCount > 0 && (
                <p className="mt-1">{hiddenProblemsMessage(analysis.hiddenProblemCount)}</p>
              )}
              <p className="mt-1">Corregilos en el archivo y subilo de nuevo.</p>
            </Alert>
          )}

          {clean && flow.fileName && (
            <div className="rounded-control border border-ink-200 px-3 py-2 text-sm text-ink-700" aria-live="polite">
              <span className="font-medium text-ink-800">{flow.fileName}</span>: {importSummary(analysis)}.
            </div>
          )}

          {clean && flow.blockers.length > 0 && (
            <Alert tone="error">
              <ul className="list-disc pl-5">
                {flow.blockers.map((blocker) => (
                  <li key={blocker}>{blocker}</li>
                ))}
              </ul>
            </Alert>
          )}

          {flow.failure && (
            <Alert tone="error">
              <p className="font-medium">
                {flow.failure.kind === 'rejected' ? 'No se importó nada.' : 'Resultado incierto.'}
              </p>
              <p className="mt-1">{flow.failure.text}</p>
              {flow.failure.detail && (
                <p className="mt-1 text-xs text-ink-600">
                  Detalle del servidor: <span className="font-mono">{flow.failure.detail}</span>
                </p>
              )}
            </Alert>
          )}

          {clean && flow.blockers.length === 0 && (
            <div className="flex flex-col gap-2">
              <Alert tone="warning">
                <p className="font-medium">Antes de importar</p>
                <ul className="mt-1 list-disc pl-5">
                  {flow.warnings.map((warning) => (
                    <li key={warning.id}>{warning.text}</li>
                  ))}
                </ul>
              </Alert>
              <label className="flex items-start gap-2 text-sm text-ink-800">
                <input
                  type="checkbox"
                  checked={flow.confirmed}
                  disabled={sending}
                  onChange={(event) => flow.setConfirmed(event.target.checked)}
                  className="mt-0.5 h-4 w-4"
                />
                <span>Entiendo lo anterior y quiero importar este archivo.</span>
              </label>
            </div>
          )}

          {sending && (
            <p className="text-sm text-ink-700" aria-live="polite">
              Importando… El servidor valida el archivo completo antes de guardar. No cierres esta ventana.
            </p>
          )}

          <ModalFooter>
            <Button variant="ghost" onClick={sending ? flow.cancel : onClose}>
              {sending ? 'Cancelar importación' : 'Cancelar'}
            </Button>
            <Button
              onClick={() => void flow.submit()}
              disabled={!flow.canSubmit}
              loading={sending}
              // A long count wraps onto a second line instead of overflowing a narrow window.
              className="h-auto! min-h-control max-w-full py-2 text-center"
            >
              {sending ? 'Importando…' : importButtonLabel(analysis)}
            </Button>
          </ModalFooter>
        </div>
      )}
    </Modal>
  );
}
