import { client } from './client';
import type { EconomicConfiguration } from '../types/economics';
import type { ApiResponse } from '../types/auth';
export interface ScenarioRevisionSummary {
 id: string; number: number; createdAt: string; createdBy: string | null;
 parentRevisionId: string | null; mergeSourceRevisionId: string | null; mergeBaseRevisionId: string | null;
}
export interface ScenarioRevisionDetail {
 revision: ScenarioRevisionSummary;
 content: { configuration: EconomicConfiguration | null; nodes: { type: string; data: Record<string, unknown> }[]; connections: unknown[] };
}
export interface ScenarioRevisionStatus { revisionId: string; number: number; workspaceChanges: boolean; canPublish: boolean; token: string }
const base = (project: string, scenario: string) => `/projects/${project}/scenarios/${scenario}/revisions`;
function unwrap<T>(response: { data: ApiResponse<T> }): T {
 if (!response.data.success || response.data.data == null) throw new Error(response.data.message || 'Respuesta incompleta.');
 return response.data.data;
}
export const scenarioRevisionsApi = {
 fork: (project: string, scenario: string, name: string, token: string, independent: boolean) => client.post<ApiResponse<{id: string}>>(`/projects/${project}/scenarios/${scenario}/${independent ? 'copies' : 'branches'}`, { name }, { headers: { 'If-Match': token } }).then(unwrap),
 get: (project: string, scenario: string, revision: string) => client.get<ApiResponse<ScenarioRevisionDetail>>(base(project, scenario) + '/' + revision).then(unwrap),
 restore: (project: string, scenario: string, revision: string, token: string) => client.post<ApiResponse<ScenarioRevisionStatus>>(base(project, scenario) + '/' + revision + '/restore', { replacePending: true }, { headers: { 'If-Match': token } }).then(unwrap),
 list: (project: string, scenario: string) => client.get<ApiResponse<ScenarioRevisionSummary[]>>(base(project, scenario)).then(unwrap),
 status: (project: string, scenario: string) => client.get<ApiResponse<ScenarioRevisionStatus>>(base(project, scenario) + '/status').then(unwrap),
};
