import type { AxiosResponse } from 'axios';
import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type {
  CostEstimate,
  RunSimulationRequest,
  SimulationDetail,
  SimulationSummary,
  VersionComparison,
} from '../types/simulation';

/** Simulation runs of a version, its live cost estimate, and version comparisons. */
export const simulationsApi = {
  /** Runs the engine on the version's current model and stores the run (needs edit rights). */
  run: (
    versionId: string,
    data: RunSimulationRequest,
  ): Promise<AxiosResponse<ApiResponse<SimulationDetail>>> =>
    client.post(`/version/${versionId}/simulations`, data),

  /** Runs of a version, newest first. */
  list: (versionId: string): Promise<AxiosResponse<ApiResponse<SimulationSummary[]>>> =>
    client.get(`/version/${versionId}/simulations`),

  get: (
    versionId: string,
    simulationId: string,
  ): Promise<AxiosResponse<ApiResponse<SimulationDetail>>> =>
    client.get(`/version/${versionId}/simulations/${simulationId}`),

  remove: (versionId: string, simulationId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.delete(`/version/${versionId}/simulations/${simulationId}`),

  /** CAPEX/OPEX/upkeep of the version's current model over `years`, without simulating. */
  estimateCosts: (
    versionId: string,
    years: number,
  ): Promise<AxiosResponse<ApiResponse<CostEstimate>>> =>
    client.get(`/version/${versionId}/costs`, { params: { years } }),

  /** Two or more versions of the project side by side; the first one is the baseline. */
  compare: (
    projectId: string,
    versionIds: string[],
  ): Promise<AxiosResponse<ApiResponse<VersionComparison>>> =>
    client.get(`/projects/${projectId}/comparison`, { params: { versionIds: versionIds.join(',') } }),
};
