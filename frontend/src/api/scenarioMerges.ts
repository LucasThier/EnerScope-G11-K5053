import { client } from './client';
import type { ApiResponse } from '../types/auth';
export interface MergeConflict { kind: string; identity: string; base: unknown; source: unknown; target: unknown }
export interface PreparedMerge { id: string; targetId: string; sourceRevisionId: string; targetRevisionId: string; baseRevisionId: string | null; state: string; token: string; conflicts: MergeConflict[] }
const base = (project: string, scenario: string) => `/projects/${project}/scenarios/${scenario}/merge`;
function unwrap<T>(response: { data: ApiResponse<T> }): T | null {
 if (!response.data.success) throw new Error(response.data.message); return response.data.data ?? null;
}
export const scenarioMergesApi = {
 get: (project: string, target: string) => client.get<ApiResponse<PreparedMerge>>(base(project, target)).then(unwrap),
 prepare: (project: string, source: string) => client.post<ApiResponse<PreparedMerge>>(base(project, source)).then(unwrap),
 resolve: (project: string, target: string, token: string, choices: Record<string, 'SOURCE' | 'TARGET'>) => client.put<ApiResponse<PreparedMerge>>(base(project, target), choices, { headers: { 'If-Match': token } }).then(unwrap),
};
