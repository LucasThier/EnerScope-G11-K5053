import { useCallback, useState } from 'react';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import {
  MAX_FILE_BYTES,
  MAX_ROWS,
  buildNormalizedCsv,
  buildTemplateCsv,
  downloadTextFile,
  parseImportCsv,
  timestampedName,
  withBom,
} from '../utils/csvImport';
import type { ParsedImport } from '../utils/csvImport';
import type { BulkRegistrationResult } from '../types/auth';

export type ImportStage = 'pick' | 'preview' | 'sending' | 'result';

interface UseCsvUserImportOptions {
  organizationId: string;
  onImported: () => Promise<void> | void;
  onClose: () => void;
}

export interface CsvUserImport {
  stage: ImportStage;
  fileName: string | null;
  parsed: ParsedImport | null;
  blockedReason: string | null;
  readError: string | null;
  result: BulkRegistrationResult | null;
  sendError: string | null;
  mayHavePartialWrites: boolean;
  credentialsDownloaded: boolean;
  confirmingClose: boolean;
  canSubmit: boolean;
  reset: () => void;
  selectFile: (file: File | null) => void;
  submit: () => Promise<void>;
  downloadTemplate: () => void;
  downloadCredentials: () => void;
  requestClose: () => void;
  confirmClose: () => void;
  cancelClose: () => void;
}

const ALREADY_EXISTS = 'An account with that email already exists';

export function countAlreadyExisting(result: BulkRegistrationResult): number {
  return result.failures.filter((failure) => failure.reason === ALREADY_EXISTS).length;
}

export function useCsvUserImport({
  organizationId,
  onImported,
  onClose,
}: UseCsvUserImportOptions): CsvUserImport {
  const [stage, setStage] = useState<ImportStage>('pick');
  const [fileName, setFileName] = useState<string | null>(null);
  const [parsed, setParsed] = useState<ParsedImport | null>(null);
  const [blockedReason, setBlockedReason] = useState<string | null>(null);
  const [readError, setReadError] = useState<string | null>(null);
  const [result, setResult] = useState<BulkRegistrationResult | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [mayHavePartialWrites, setMayHavePartialWrites] = useState(false);
  const [credentialsDownloaded, setCredentialsDownloaded] = useState(false);
  const [confirmingClose, setConfirmingClose] = useState(false);

  const reset = useCallback(() => {
    setStage('pick');
    setFileName(null);
    setParsed(null);
    setBlockedReason(null);
    setReadError(null);
    setResult(null);
    setSendError(null);
    setMayHavePartialWrites(false);
    setCredentialsDownloaded(false);
    setConfirmingClose(false);
  }, []);

  const selectFile = useCallback((file: File | null) => {
    setSendError(null);
    setMayHavePartialWrites(false);
    setReadError(null);
    setBlockedReason(null);
    setParsed(null);
    setResult(null);

    if (!file) {
      setFileName(null);
      setStage('pick');
      return;
    }

    setFileName(file.name);

    if (file.size > MAX_FILE_BYTES) {
      setBlockedReason(
        `El archivo pesa ${(file.size / 1024 / 1024).toFixed(1)} MB y el máximo es 1 MB. ` +
          'Dividilo en varios archivos más chicos.',
      );
      setStage('preview');
      return;
    }

    file
      .text()
      .then((raw) => {
        const next = parseImportCsv(raw);
        setParsed(next);
        if (next.rows.length > MAX_ROWS) {
          setBlockedReason(
            `El archivo tiene ${next.rows.length} filas y el máximo por importación es ` +
              `${MAX_ROWS}. Cada cuenta lleva unos segundos de procesamiento, así que ` +
              'conviene dividirlo en varios archivos.',
          );
        }
        setStage('preview');
      })
      .catch(() => {
        setReadError('No se pudo leer el archivo. Revisá que sea un .csv de texto.');
        setStage('preview');
      });
  }, []);

  const canSubmit =
    stage === 'preview' &&
    blockedReason === null &&
    readError === null &&
    parsed !== null &&
    parsed.fileError === null &&
    parsed.validCount > 0;

  const submit = useCallback(async () => {
    if (!parsed || parsed.validCount === 0) {
      return;
    }
    setStage('sending');
    setSendError(null);
    setMayHavePartialWrites(false);
    try {
      const csv = buildNormalizedCsv(parsed.rows);
      const file = new File([csv], 'usuarios.csv', { type: 'text/csv' });
      const res = await organizationsApi.bulkRegisterUsers(organizationId, file);
      const data = res.data.data;
      if (!data) {
        setSendError('El servidor respondió sin datos. Revisá la lista de integrantes.');
        setMayHavePartialWrites(true);
        setStage('preview');
        return;
      }
      setResult(data);
      setCredentialsDownloaded(false);
      setStage('result');
    } catch (err) {
      const status = getErrorStatus(err);
      if (status === null) {
        setSendError('Se perdió la conexión con el servidor durante la importación.');
        setMayHavePartialWrites(true);
      } else if (status >= 500) {
        setSendError('El servidor no pudo completar la importación.');
        setMayHavePartialWrites(true);
      } else {
        setSendError(getErrorMessage(err, 'No se pudo completar la importación'));
        setMayHavePartialWrites(false);
      }
      setStage('preview');
    }
  }, [organizationId, parsed]);

  const downloadTemplate = useCallback(() => {
    downloadTextFile('plantilla-usuarios.csv', buildTemplateCsv());
  }, []);

  const downloadCredentials = useCallback(() => {
    if (!result) {
      return;
    }
    downloadTextFile(timestampedName('credenciales'), withBom(result.credentialsCsv));
    setCredentialsDownloaded(true);
    setConfirmingClose(false);
  }, [result]);

  const finish = useCallback(() => {
    if (result && result.created > 0) {
      void onImported();
    }
    setConfirmingClose(false);
    onClose();
  }, [onClose, onImported, result]);

  const requestClose = useCallback(() => {
    const hasUnsavedCredentials =
      stage === 'result' && result !== null && result.created > 0 && !credentialsDownloaded;
    if (stage === 'sending') {
      return;
    }
    if (hasUnsavedCredentials) {
      setConfirmingClose(true);
      return;
    }
    finish();
  }, [credentialsDownloaded, finish, result, stage]);

  return {
    stage,
    fileName,
    parsed,
    blockedReason,
    readError,
    result,
    sendError,
    mayHavePartialWrites,
    credentialsDownloaded,
    confirmingClose,
    canSubmit,
    reset,
    selectFile,
    submit,
    downloadTemplate,
    downloadCredentials,
    requestClose,
    confirmClose: finish,
    cancelClose: () => setConfirmingClose(false),
  };
}
