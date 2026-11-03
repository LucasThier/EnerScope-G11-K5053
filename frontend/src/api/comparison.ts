import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { ComparisonRequest, ComparisonResult, ComparisonSummary, SavedComparison } from '../types/comparison';

function data<T>(response: { data: ApiResponse<T> }): T {
  if (!response.data.success || response.data.data == null) throw new Error(response.data.message || 'No se recibió la comparación.');
  return response.data.data;
}

const base = (projectId: string) => `/projects/${projectId}/comparisons`;

export const comparisonApi = {
  preview: (projectId: string, request: ComparisonRequest) => client.post<ApiResponse<ComparisonResult>>(base(projectId) + '/preview', request).then(data),
  save: (projectId: string, request: ComparisonRequest) => client.post<ApiResponse<SavedComparison>>(base(projectId), request).then(data),
  list: (projectId: string) => client.get<ApiResponse<ComparisonSummary[]>>(base(projectId)).then(data),
  get: (projectId: string, id: string) => client.get<ApiResponse<SavedComparison>>(base(projectId) + '/' + id).then(data),
};
