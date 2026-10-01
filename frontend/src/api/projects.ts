import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { VersionSummary } from '../types/diagram';
import type {
  CreateProjectRequest,
  Project,
  ProjectMember,
  ProjectSummary,
} from '../types/project';
import type { AxiosResponse } from 'axios';

export const projectsApi = {
  /**
   * Projects visible to the caller: every project for platform admins, own
   * memberships for everyone else. Belonging to the owning organization is not
   * enough — the backend filters by project membership.
   */
  list: (organizationId?: string): Promise<AxiosResponse<ApiResponse<ProjectSummary[]>>> =>
    client.get('/projects', organizationId ? { params: { organizationId } } : undefined),

  create: (data: CreateProjectRequest): Promise<AxiosResponse<ApiResponse<Project>>> =>
    client.post('/projects', data),

  /** Members of a project: readable by platform admins and by members of the project. */
  members: (projectId: string): Promise<AxiosResponse<ApiResponse<ProjectMember[]>>> =>
    client.get(`/projects/${projectId}/members`),

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
