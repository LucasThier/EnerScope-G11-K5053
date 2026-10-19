import type { EconomicJob, EconomicRound, Evaluation } from '../types/economics';
export const jobLabels: Record<EconomicJob['status'], string> = {
  QUEUED: 'En espera', RUNNING: 'Simulando', COMPLETED: 'Finalizada', FAILED: 'Fallida', INTERRUPTED: 'Interrumpida',
};
export function jobPending(job: EconomicJob) { return job.status === 'QUEUED' || job.status === 'RUNNING'; }
export function executionError(rounds: string, seed: string): string | null {
  if (!/^\d+$/.test(rounds) || Number(rounds) < 1 || Number(rounds) > 100) return 'Indicá entre 1 y 100 rondas.';
  if (seed.trim()) {
    if (!/^-?\d+$/.test(seed.trim())) return 'La semilla debe ser un número entero.';
    const n = BigInt(seed.trim());
    if (n < -9223372036854775808n || n > 9223372036854775807n) return 'La semilla debe estar dentro del rango de enteros de 64 bits.';
  }
  return null;
}
export function roundEvaluation(evaluation: Evaluation, round: EconomicRound): Evaluation {
  return { ...evaluation, snapshot: { ...evaluation.snapshot, rawMetrics: round.rawMetrics, result: round.result } };
}
