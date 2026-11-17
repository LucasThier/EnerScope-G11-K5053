import type { BulkRegistrationResult, FailedRegistration } from '../types/auth';
import { buildNormalizedCsv, type ImportRow } from './csvImport';

export const BATCH_SIZE = 50;

export type BatchRunStatus = 'completed' | 'cancelled' | 'failed';

export interface BatchRun {
  status: BatchRunStatus;
  result: BulkRegistrationResult | null;
  uncertainRows: ImportRow[];
  notSentRows: ImportRow[];
  error: unknown;
}

export interface BatchRunOptions {
  send: (csv: string, signal: AbortSignal) => Promise<BulkRegistrationResult>;
  signal: AbortSignal;
  isUncertain: (error: unknown) => boolean;
  onProgress?: (processedRows: number, totalRows: number) => void;
  batchSize?: number;
}

export function splitIntoBatches<T>(items: T[], size: number = BATCH_SIZE): T[][] {
  const batches: T[][] = [];
  for (let start = 0; start < items.length; start += size) {
    batches.push(items.slice(start, start + size));
  }
  return batches;
}

export function mapFailureLines(
  failures: FailedRegistration[],
  batch: ImportRow[],
): FailedRegistration[] {
  return failures.map((failure) => {
    const row = batch[failure.line - 2];
    return row ? { ...failure, line: row.line } : failure;
  });
}

function stripHeader(csv: string): string {
  return csv.replace(/^[^\r\n]*\r?\n/, '');
}

function headerOf(csv: string): string {
  const match = /^[^\r\n]*\r?\n/.exec(csv);
  return match ? match[0] : 'mail,password\r\n';
}

export function mergeResults(parts: BulkRegistrationResult[]): BulkRegistrationResult {
  const header = parts.length > 0 ? headerOf(parts[0].credentialsCsv) : 'mail,password\r\n';
  return {
    total: parts.reduce((sum, part) => sum + part.total, 0),
    created: parts.reduce((sum, part) => sum + part.created, 0),
    failed: parts.reduce((sum, part) => sum + part.failed, 0),
    credentialsCsv: header + parts.map((part) => stripHeader(part.credentialsCsv)).join(''),
    failures: parts.flatMap((part) => part.failures),
  };
}

export async function runImportBatches(
  rows: ImportRow[],
  options: BatchRunOptions,
): Promise<BatchRun> {
  const valid = rows.filter((row) => row.error === null);
  const batches = splitIntoBatches(valid, options.batchSize ?? BATCH_SIZE);
  const parts: BulkRegistrationResult[] = [];
  let processed = 0;
  options.onProgress?.(0, valid.length);

  for (let index = 0; index < batches.length; index += 1) {
    const batch = batches[index];
    const pending = batches.slice(index + 1).flat();

    if (options.signal.aborted) {
      return finish('cancelled', parts, [], [...batch, ...pending], null);
    }

    try {
      const response = await options.send(buildNormalizedCsv(batch), options.signal);
      parts.push({ ...response, failures: mapFailureLines(response.failures, batch) });
      processed += batch.length;
      options.onProgress?.(processed, valid.length);
    } catch (error) {
      if (options.signal.aborted) {
        return finish('cancelled', parts, batch, pending, error);
      }
      if (options.isUncertain(error)) {
        return finish('failed', parts, batch, pending, error);
      }
      return finish('failed', parts, [], [...batch, ...pending], error);
    }
  }

  return finish('completed', parts, [], [], null);
}

function finish(
  status: BatchRunStatus,
  parts: BulkRegistrationResult[],
  uncertainRows: ImportRow[],
  notSentRows: ImportRow[],
  error: unknown,
): BatchRun {
  return {
    status,
    result: parts.length > 0 ? mergeResults(parts) : null,
    uncertainRows,
    notSentRows,
    error,
  };
}
