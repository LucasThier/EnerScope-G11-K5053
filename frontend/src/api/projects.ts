import type { AxiosResponse } from 'axios';
import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { Project, VersionSummary } from '../types/diagram';

export const projectsApi = {
  /** Projects of an organization. */
  listByOrganization: (
    organizationId: string,
  ): Promise<AxiosResponse<ApiResponse<Project[]>>> =>
    client.get('/projects', { params: { organizationId } }),

  /** Create a project under an organization. */
  create: (data: {
    name: string;
    description: string;
    organizationId: string;
  }): Promise<AxiosResponse<ApiResponse<Project>>> => client.post('/projects', data),

  /** Versions of a project (summaries), so the editor can pick one to open. */
  listVersions: (projectId: string): Promise<AxiosResponse<ApiResponse<VersionSummary[]>>> =>
    client.get(`/projects/${projectId}/versions`),

  /** Create a new version under a project. */
  createVersion: (
    projectId: string,
    data: { name: string; parentVersion?: string | null },
  ): Promise<AxiosResponse<ApiResponse<unknown>>> =>
    client.post(`/projects/${projectId}/version`, data),
};
