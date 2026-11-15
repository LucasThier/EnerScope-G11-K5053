import { useCallback, useRef, useState } from 'react';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import {
  MAX_FILE_BYTES,
  MAX_ROWS,
  buildTemplateCsv,
  downloadTextFile,
  parseImportCsv,
  timestampedName,
  withBom,
} from '../utils/csvImport';
import type { ImportRow, ParsedImport } from '../utils/csvImport';
import { runImportBatches, type BatchRunStatus } from '../utils/csvImportBatches';
import type { BulkRegistrationResult } from '../types/auth';

export type ImportStage = 'pick' | 'preview' | 'sending' | 'result';

interface UseCsvUserImportOptions {
  organizationId: string;
  onImported: () => Promise<void> | void;
  onClose: () => void;
}

export interface ImportProgress {
  done: number;
  total: number;
}

export interface ImportOutcome {
  status: BatchRunStatus;
  uncertainRows: ImportRow[];
  notSentRows: ImportRow[];
}

export interface CsvUserImport {
  progress: ImportProgress | null;
  outcome: ImportOutcome | null;
  cancel: () => void;
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
  const [progress, setProgress] = useState<ImportProgress | null>(null);
  const [outcome, setOutcome] = useState<ImportOutcome | null>(null);
  const controllerRef = useRef<AbortController | null>(null);

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
    setProgress(null);
    setOutcome(null);
  }, []);

  const selectFile = useCallback((file: File | null) => {
    setSendError(null);
    setMayHavePartialWrites(false);
    setReadError(null);
    setBlockedReason(null);
    setParsed(null);
    setResult(null);
    setOutcome(null);
    setProgress(null);

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
              `${MAX_ROWS}. Dividilo en varios archivos.`,
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

  const cancel = useCallback(() => {
    controllerRef.current?.abort();
  }, []);

  const submit = useCallback(async () => {
    if (!parsed || parsed.validCount === 0) {
      return;
    }
    const controller = new AbortController();
    controllerRef.current = controller;
    setStage('sending');
    setSendError(null);
    setMayHavePartialWrites(false);
    setOutcome(null);
    setProgress({ done: 0, total: parsed.validCount });

    const run = await runImportBatches(parsed.rows, {
      signal: controller.signal,
      send: async (csv, signal) => {
        const file = new File([csv], 'usuarios.csv', { type: 'text/csv' });
        const res = await organizationsApi.bulkRegisterUsers(organizationId, file, signal);
        const data = res.data.data;
        if (!data) {
          throw new Error('Empty response');
        }
        return data;
      },
      isUncertain: (error) => {
        const status = getErrorStatus(error);
        return status === null || status >= 500;
      },
      onProgress: (done, total) => setProgress({ done, total }),
    });
    controllerRef.current = null;
    setProgress(null);

    if (run.result) {
      setResult(run.result);
      setCredentialsDownloaded(false);
      setOutcome({
        status: run.status,
        uncertainRows: run.uncertainRows,
        notSentRows: run.notSentRows,
      });
      setStage('result');
      return;
    }

    if (run.status === 'cancelled') {
      setSendError('Cancelaste la importación.');
    } else if (run.status === 'failed') {
      const status = getErrorStatus(run.error);
      if (status === null) {
        setSendError('Se perdió la conexión con el servidor durante la importación.');
      } else if (status >= 500) {
        setSendError('El servidor no pudo completar la importación.');
      } else {
        setSendError(getErrorMessage(run.error, 'No se pudo completar la importación'));
      }
    }
    setMayHavePartialWrites(run.uncertainRows.length > 0);
    setStage('preview');
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
    progress,
    outcome,
    cancel,
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
