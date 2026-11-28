import { useCallback, useMemo, useRef, useState } from 'react';
import { projectsApi } from '../api/projects';
import { getBackendMessage, getErrorMessage, getErrorStatus, isRequestCanceled } from '../api/errors';
import {
  MAX_FILE_BYTES,
  analyzeNodesCsv,
  buildTemplateCsv,
  canStartImport,
  classifyImportFailure,
  importPermissionBlockers,
  importWarnings,
  templateFileName,
  type CsvAnalysis,
  type ImportFailure,
  type ImportWarning,
  type TemplateKind,
} from '../utils/versionCsvImport';
import { downloadTextFile, withBom } from '../utils/csvImport';
import type { VersionAccess } from '../types/versionAccess';

export type VersionImportStage = 'pick' | 'review' | 'sending' | 'result';

interface Options {
  versionId: string;
  versionName: string;
  /** The caller's access to this version, from `/access`; null denies everything. */
  access: VersionAccess | null;
  onImported: () => Promise<void> | void;
}

export interface VersionCsvImport {
  stage: VersionImportStage;
  fileName: string | null;
  readError: string | null;
  analysis: CsvAnalysis | null;
  blockers: string[];
  warnings: ImportWarning[];
  confirmed: boolean;
  setConfirmed: (value: boolean) => void;
  failure: ImportFailure | null;
  importedCount: number | null;
  canSubmit: boolean;
  selectFile: (file: File | null) => void;
  submit: () => Promise<void>;
  cancel: () => void;
  downloadTemplate: (kind: TemplateKind) => void;
}

export function useVersionCsvImport({ versionId, versionName, access, onImported }: Options): VersionCsvImport {
  const [stage, setStage] = useState<VersionImportStage>('pick');
  const [file, setFile] = useState<File | null>(null);
  const [readError, setReadError] = useState<string | null>(null);
  const [analysis, setAnalysis] = useState<CsvAnalysis | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [failure, setFailure] = useState<ImportFailure | null>(null);
  const [importedCount, setImportedCount] = useState<number | null>(null);
  const controllerRef = useRef<AbortController | null>(null);

  const blockers = useMemo(
    () => (analysis ? importPermissionBlockers(analysis, access) : []),
    [analysis, access],
  );
  const warnings = useMemo(() => (analysis ? importWarnings(analysis, versionName) : []), [analysis, versionName]);

  const selectFile = useCallback((next: File | null) => {
    setFile(next);
    setReadError(null);
    setAnalysis(null);
    setConfirmed(false);
    setFailure(null);
    if (!next) {
      setStage('pick');
      return;
    }
    if (next.size > MAX_FILE_BYTES) {
      setReadError(
        `El archivo pesa ${(next.size / 1024 / 1024).toFixed(1)} MB y el máximo es 10 MB. Dividilo en varios archivos.`,
      );
      setStage('pick');
      return;
    }
    next
      .text()
      .then((raw) => {
        setAnalysis(analyzeNodesCsv(raw));
        setStage('review');
      })
      .catch(() => {
        setReadError('No se pudo leer el archivo. Revisá que sea un .csv de texto.');
        setStage('pick');
      });
  }, []);

  const canSubmit = canStartImport({ analysis, blockers, confirmed, sending: stage === 'sending' });

  const submit = useCallback(async () => {
    if (!file || !canSubmit) {
      return;
    }
    const controller = new AbortController();
    controllerRef.current = controller;
    setStage('sending');
    setFailure(null);
    try {
      const response = await projectsApi.importVersionNodesCsv(versionId, file, controller.signal);
      setImportedCount(response.data.data ?? analysis?.nodeCount ?? 0);
      setStage('result');
    } catch (error) {
      setFailure(
        classifyImportFailure({
          status: getErrorStatus(error),
          canceled: isRequestCanceled(error),
          message: getBackendMessage(error),
          fallback: getErrorMessage(error, 'No se pudo importar el archivo'),
        }),
      );
      setConfirmed(false);
      setStage('review');
      return;
    } finally {
      controllerRef.current = null;
    }
    // The import is done whatever happens to the refresh of the list behind this window.
    try {
      await onImported();
    } catch {
      /* the list reloads on its own the next time it is opened */
    }
  }, [analysis, canSubmit, file, onImported, versionId]);

  const cancel = useCallback(() => controllerRef.current?.abort(), []);

  const downloadTemplate = useCallback((kind: TemplateKind) => {
    downloadTextFile(templateFileName(kind), withBom(buildTemplateCsv(kind)));
  }, []);

  return {
    stage,
    fileName: file?.name ?? null,
    readError,
    analysis,
    blockers,
    warnings,
    confirmed,
    setConfirmed,
    failure,
    importedCount,
    canSubmit,
    selectFile,
    submit,
    cancel,
    downloadTemplate,
  };
}
