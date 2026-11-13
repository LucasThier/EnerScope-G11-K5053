import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { ComparisonRequest, ComparisonResult, ComparisonSummary, SavedComparison } from '../types/comparison';

const base = (project: string) => `/projects/${encodeURIComponent(project)}/comparisons`;

function data<T>(response: { data: ApiResponse<T> }): T {
  if (!response.data.success || response.data.data == null) throw new Error('La respuesta no contiene los datos esperados.');
  return response.data.data;
}

/** Scenario comparison endpoints (`/projects/{projectId}/comparisons`). */
export const comparisonsApi = {
  preview: (project: string, request: ComparisonRequest) =>
    client.post<ApiResponse<ComparisonResult>>(base(project) + '/preview', request).then(data),
  save: (project: string, request: ComparisonRequest) =>
    client.post<ApiResponse<SavedComparison>>(base(project), request).then(data),
  list: (project: string) => client.get<ApiResponse<ComparisonSummary[]>>(base(project)).then(data),
  get: (project: string, id: string) =>
    client.get<ApiResponse<SavedComparison>>(base(project) + '/' + encodeURIComponent(id)).then(data),
  remove: (project: string, id: string) => client.delete<ApiResponse<void>>(base(project) + '/' + encodeURIComponent(id)),
};
