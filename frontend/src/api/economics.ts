import type { EconomicDraft } from '../utils/economicDraft';
import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { EconomicConfiguration, Evaluation, Scenario } from '../types/economics';

const base = (project: string, version: string) => `/projects/${project}/versions/${version}/economics`;
function data<T>(response: { data: ApiResponse<T> }): T {
  if (!response.data.success || response.data.data == null) throw new Error('La respuesta no contiene los datos esperados.');
  return response.data.data;
}
export const economicsApi = {
  draft: (p: string, v: string) => client.get<ApiResponse<{ schemaVersion: number; draft: EconomicDraft } | null>>(base(p, v) + '/draft').then(r => {
    if (!r.data.success) throw new Error(r.data.message || 'No se pudo cargar el borrador.');
    return r.data.data;
  }),
  saveDraft: (p: string, v: string, draft: EconomicDraft) => client.put<ApiResponse<{ schemaVersion: number; draft: EconomicDraft }>>(base(p, v) + '/draft', { schemaVersion: 1, draft }).then(data),
  scenarios: (project: string) => client.get<ApiResponse<Scenario[]>>(`/projects/${project}/versions`).then(data),
  configuration: (p: string, v: string) => client.get<ApiResponse<EconomicConfiguration>>(base(p, v) + '/configuration').then(data),
  save: (p: string, v: string, configuration: EconomicConfiguration) =>
    client.put<ApiResponse<EconomicConfiguration>>(base(p, v) + '/configuration', configuration).then(data),
  evaluate: (p: string, v: string) => client.post<ApiResponse<Evaluation>>(base(p, v) + '/evaluations').then(data),
  history: (p: string, v: string) => client.get<ApiResponse<Evaluation[]>>(base(p, v) + '/evaluations').then(data),
  evaluation: (p: string, v: string, id: string) => client.get<ApiResponse<Evaluation>>(base(p, v) + '/evaluations/' + id).then(data),
};
