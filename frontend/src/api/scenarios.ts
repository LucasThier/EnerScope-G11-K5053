import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { EconomicJob, EconomicRound, EconomicRoundSummary, Evaluation } from '../types/economics';
import type { ScenarioSummary } from '../types/scenario';
import type { ScenarioConfiguration, ScenarioSave } from '../types/scenarioConfiguration';

const project = (p: string) => `/projects/${encodeURIComponent(p)}`;
const scenario = (p: string, s: string) => `${project(p)}/scenarios/${encodeURIComponent(s)}`;

function data<T>(response: { data: ApiResponse<T> }): T {
  if (!response.data.success || response.data.data == null) throw new Error(response.data.message || 'La respuesta no contiene los datos esperados.');
  return response.data.data;
}

/**
 * The scenarios of a version and everything that belongs to one scenario (configuration, simulations, results).
 * A scenario is the economic and simulation part; the version is the physical base.
 */
export const scenariosApi = {
  list: (p: string, v: string) =>
    client.get<ApiResponse<ScenarioSummary[]>>(`${project(p)}/versions/${encodeURIComponent(v)}/scenarios`).then(data),
  /** Creates an empty scenario; the name is typed by the person (never generated). */
  create: (p: string, v: string, name: string) =>
    client.post<ApiResponse<ScenarioSummary>>(`${project(p)}/versions/${encodeURIComponent(v)}/scenarios`, { name }).then(data),
  get: (p: string, s: string) => client.get<ApiResponse<ScenarioSummary>>(scenario(p, s)).then(data),
  rename: (p: string, s: string, name: string) => client.patch<ApiResponse<ScenarioSummary>>(scenario(p, s), { name }).then(data),
  duplicate: (p: string, s: string, name: string) =>
    client.post<ApiResponse<ScenarioSummary>>(scenario(p, s) + '/duplicate', { name }).then(data),
  remove: (p: string, s: string) => client.delete<ApiResponse<void>>(scenario(p, s)),

  configuration: (p: string, s: string) => client.get<ApiResponse<ScenarioConfiguration>>(scenario(p, s) + '/configuration').then(data),
  saveConfiguration: (p: string, s: string, request: ScenarioSave) =>
    client.put<ApiResponse<ScenarioConfiguration>>(scenario(p, s) + '/configuration', request).then(data),
  discardDraft: (p: string, s: string, token: string) =>
    client.delete<ApiResponse<ScenarioConfiguration>>(scenario(p, s) + '/configuration/draft', { headers: { 'If-Match': token } }).then(data),

  simulate: (p: string, s: string, rounds: number, seed: string) =>
    client.post<ApiResponse<EconomicJob>>(scenario(p, s) + '/simulations', { rounds, seed: seed.trim() || null }).then(data),
  jobs: (p: string, s: string) => client.get<ApiResponse<EconomicJob[]>>(scenario(p, s) + '/simulations').then(data),
  retryJob: (p: string, s: string, jobId: string) =>
    client.post<ApiResponse<EconomicJob>>(scenario(p, s) + '/simulations/' + encodeURIComponent(jobId) + '/retry').then(data),
  evaluations: (p: string, s: string) => client.get<ApiResponse<Evaluation[]>>(scenario(p, s) + '/evaluations').then(data),
  evaluation: (p: string, s: string, id: string) =>
    client.get<ApiResponse<Evaluation>>(scenario(p, s) + '/evaluations/' + encodeURIComponent(id)).then(data),
  rounds: (p: string, s: string, id: string) =>
    client.get<ApiResponse<EconomicRoundSummary[]>>(scenario(p, s) + '/evaluations/' + encodeURIComponent(id) + '/rounds').then(data),
  round: (p: string, s: string, id: string, round: number) =>
    client.get<ApiResponse<EconomicRound>>(scenario(p, s) + '/evaluations/' + encodeURIComponent(id) + '/rounds/' + round).then(data),
};
