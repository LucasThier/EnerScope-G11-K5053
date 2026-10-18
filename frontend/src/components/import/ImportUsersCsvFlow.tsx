import { useId } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { DownloadIcon } from '../ui/icons';
import { ImportPreviewTable } from './ImportPreviewTable';
import { countAlreadyExisting, type CsvUserImport } from '../../hooks/useCsvUserImport';
import { translateBackendMessage } from '../../api/errors';
import { MAX_ROWS } from '../../utils/csvImport';

interface ImportUsersCsvFlowProps {
  flow: CsvUserImport;
  organizationName: string;
}

export function ImportUsersCsvFlow({ flow, organizationName }: ImportUsersCsvFlowProps) {
  const fileInputId = useId();

  if (flow.confirmingClose) {
    return (
      <div className="flex flex-col gap-4">
        <Alert tone="warning">
          Todavía no descargaste las credenciales. Si cerrás ahora, las contraseñas se
          pierden y no hay forma de volver a verlas.
        </Alert>
        <p className="text-sm text-ink-700">
          Las cuentas ya quedaron creadas. Sin las contraseñas, las personas no van a poder
          iniciar sesión y habrá que pedirle a la plataforma que las restablezca.
        </p>
        <div className="mt-2 flex flex-wrap justify-end gap-2">
          <Button variant="ghost" onClick={flow.confirmClose}>
            Cerrar sin descargar
          </Button>
          <Button onClick={flow.downloadCredentials}>
            <DownloadIcon className="h-4 w-4" />
            Descargar credenciales
          </Button>
        </div>
      </div>
    );
  }

  if (flow.stage === 'result' && flow.result) {
    const alreadyExisting = countAlreadyExisting(flow.result);
    const otherFailures = flow.result.failures.filter(
      (failure) => failure.reason !== 'An account with that email already exists',
    );

    return (
      <div className="flex flex-col gap-4">
        <div aria-live="polite">
          <p className="text-sm text-ink-700">
            Se procesaron {flow.result.total}{' '}
            {flow.result.total === 1 ? 'fila' : 'filas'} en{' '}
            <span className="font-semibold text-ink-800">{organizationName}</span>.
          </p>
          <ul className="mt-3 grid gap-2 sm:grid-cols-3">
            <li className="rounded-lg border border-brand-200 bg-brand-50 px-3 py-2">
              <span className="block text-lg font-semibold text-brand-800">
                {flow.result.created}
              </span>
              <span className="text-xs text-ink-600">
                {flow.result.created === 1 ? 'cuenta creada' : 'cuentas creadas'}
              </span>
            </li>
            <li className="rounded-lg border border-ink-200 bg-ink-50 px-3 py-2">
              <span className="block text-lg font-semibold text-ink-800">
                {alreadyExisting}
              </span>
              <span className="text-xs text-ink-600">ya existían</span>
            </li>
            <li className="rounded-lg border border-ink-200 bg-ink-50 px-3 py-2">
              <span className="block text-lg font-semibold text-ink-800">
                {otherFailures.length}
              </span>
              <span className="text-xs text-ink-600">
                {otherFailures.length === 1 ? 'con error' : 'con errores'}
              </span>
            </li>
          </ul>
        </div>

        {flow.result.created > 0 && (
          <Alert tone="warning">
            Las contraseñas se muestran <strong>una sola vez</strong>. Descargá el archivo
            ahora: si cerrás esta ventana sin hacerlo, no hay manera de recuperarlas.
          </Alert>
        )}

        {flow.result.created > 0 && (
          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={flow.downloadCredentials}>
              <DownloadIcon className="h-4 w-4" />
              Descargar credenciales
            </Button>
            {flow.credentialsDownloaded && (
              <span className="text-sm text-ink-500">Archivo descargado.</span>
            )}
          </div>
        )}

        {alreadyExisting > 0 && (
          <p className="text-sm text-ink-500">
            {alreadyExisting === 1
              ? 'Una cuenta ya existía en la plataforma y no se volvió a crear.'
              : `${alreadyExisting} cuentas ya existían en la plataforma y no se volvieron a crear.`}{' '}
            Si todavía no integran la organización, agregalas desde «Agregar a la
            organización» buscándolas por su email.
          </p>
        )}

        {otherFailures.length > 0 && (
          <div>
            <h3 className="text-sm font-semibold text-ink-800">Filas rechazadas</h3>
            <ul className="mt-2 flex flex-col gap-1">
              {otherFailures.map((failure) => (
                <li key={`${failure.line}-${failure.mail}`} className="text-sm text-ink-700">
                  <span className="font-medium text-ink-800">{failure.mail || 'sin email'}</span>
                  {' — '}
                  {translateBackendMessage(failure.reason)}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="mt-2 flex justify-end">
          <Button variant="secondary" onClick={flow.requestClose}>
            Listo
          </Button>
        </div>
      </div>
    );
  }

  const parsed = flow.parsed;
  const blocked = flow.blockedReason !== null || flow.readError !== null;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-ink-700">
        Subí un archivo <code className="font-mono text-ink-800">.csv</code> con las columnas{' '}
        <span className="font-medium text-ink-800">mail</span>,{' '}
        <span className="font-medium text-ink-800">firstName</span> y{' '}
        <span className="font-medium text-ink-800">lastName</span>, y opcionalmente{' '}
        <span className="font-medium text-ink-800">role</span> (Propietario o Miembro). Cada
        persona se crea con una contraseña generada y queda como integrante de{' '}
        <span className="font-semibold text-ink-800">{organizationName}</span>.
      </p>

      <div className="flex flex-wrap items-center gap-3">
        <div>
          <label htmlFor={fileInputId} className="block text-sm font-medium text-ink-600">
            Archivo CSV
          </label>
          <input
            id={fileInputId}
            type="file"
            accept=".csv,text/csv"
            disabled={flow.stage === 'sending'}
            onChange={(event) => flow.selectFile(event.target.files?.[0] ?? null)}
            className={
              'mt-1 block w-full rounded-lg border border-ink-200 bg-surface text-sm text-ink-700 ' +
              'file:mr-3 file:cursor-pointer file:rounded-l-lg file:border-0 file:bg-ink-50 ' +
              'file:px-3 file:py-2 file:text-sm file:font-semibold file:text-ink-700 ' +
              'focus:border-focus focus:outline-none focus:ring-2 focus:ring-focus/60 ' +
              'disabled:cursor-not-allowed disabled:opacity-60'
            }
          />
        </div>
        <button
          type="button"
          onClick={flow.downloadTemplate}
          className={
            'mt-5 rounded-lg text-sm font-semibold text-brand-800 underline underline-offset-2 ' +
            'hover:text-brand-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'
          }
        >
          Descargar plantilla
        </button>
      </div>

      <p className="text-sm text-ink-500">
        Acepta separador coma o punto y coma, y archivos guardados por Excel como «CSV
        UTF-8». Hasta {MAX_ROWS} filas y 1 MB por archivo.
      </p>

      {flow.readError && <Alert tone="error">{flow.readError}</Alert>}
      {flow.blockedReason && <Alert tone="error">{flow.blockedReason}</Alert>}
      {parsed?.fileError && <Alert tone="error">{parsed.fileError}</Alert>}

      {flow.sendError && (
        <Alert tone="error">
          {flow.sendError}
          {flow.mayHavePartialWrites && (
            <span className="mt-1 block">
              Puede que algunas cuentas se hayan creado antes del corte, y en ese caso sus
              contraseñas se perdieron. Revisá la lista de integrantes antes de reintentar:
              si volvés a subir el mismo archivo, las que ya existan van a quedar
              rechazadas.
            </span>
          )}
        </Alert>
      )}

      {parsed && !blocked && parsed.fileError === null && (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="text-sm font-medium text-ink-800" aria-live="polite">
              {parsed.validCount} {parsed.validCount === 1 ? 'válida' : 'válidas'} /{' '}
              {parsed.invalidCount} con error
            </p>
            {flow.fileName && (
              <p className="text-sm text-ink-500">
                {flow.fileName}
                {parsed.delimiter === ';' && ' · separador «;» convertido a coma'}
              </p>
            )}
          </div>

          <ImportPreviewTable rows={parsed.rows} />

          {parsed.invalidCount > 0 && parsed.validCount > 0 && (
            <Alert tone="info">
              Se van a importar sólo las {parsed.validCount} filas válidas. Las{' '}
              {parsed.invalidCount} con error se omiten: corregilas en el archivo y subilo de
              nuevo si las necesitás.
            </Alert>
          )}
          {parsed.validCount === 0 && (
            <Alert tone="error">
              Ninguna fila es válida, así que no hay nada para importar. Corregí el archivo y
              subilo de nuevo.
            </Alert>
          )}
        </div>
      )}

      {flow.stage === 'sending' && (
        <Alert tone="info">
          Creando las cuentas. Puede tardar hasta un minuto según la cantidad de filas: no
          cierres esta ventana.
        </Alert>
      )}

      <div className="mt-2 flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={flow.requestClose} disabled={flow.stage === 'sending'}>
          Cancelar
        </Button>
        <Button
          onClick={() => void flow.submit()}
          disabled={!flow.canSubmit}
          loading={flow.stage === 'sending'}
        >
          {flow.stage === 'sending'
            ? 'Importando…'
            : flow.canSubmit && parsed
              ? `Importar ${parsed.validCount} ${parsed.validCount === 1 ? 'persona' : 'personas'}`
              : 'Importar'}
        </Button>
      </div>
    </div>
  );
}
