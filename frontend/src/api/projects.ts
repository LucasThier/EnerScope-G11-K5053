import { client } from './client';
import { invalidatesAccess } from './accessEvents';
import type { ApiResponse } from '../types/auth';
import type { ConflictDecision, VersionConflict, VersionSummary } from '../types/diagram';
import type {
  AddProjectMemberRequest,
  CreateProjectRequest,
  ProjectMemberCandidate,
  UpdateProjectMemberRoleRequest,
  Project,
  ProjectMember,
  ProjectSummary,
  UpdateProjectRequest,
} from '../types/project';
import type { AxiosResponse } from 'axios';

interface CreatedVersion {
  id: string;
  name: string;
}

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

  update: (
    projectId: string,
    data: UpdateProjectRequest,
  ): Promise<AxiosResponse<ApiResponse<Project>>> =>
    client.patch(`/projects/${projectId}`, data),

  remove: (projectId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    invalidatesAccess(client.delete(`/projects/${projectId}`), projectId),

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
  ): Promise<AxiosResponse<ApiResponse<CreatedVersion>>> =>
    invalidatesAccess(client.post(`/projects/${projectId}/version`, data), projectId),

  deleteVersion: (versionId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    invalidatesAccess(client.delete(`/version/${versionId}`)),

  mergeVersion: (subId: string): Promise<AxiosResponse<ApiResponse<unknown>>> =>
    invalidatesAccess(client.patch(`/version/merge/${subId}`)),

  getVersionConflicts: (
    versionId: string,
  ): Promise<AxiosResponse<ApiResponse<VersionConflict[]>>> =>
    client.get(`/version/${versionId}/conflicts`),

  resolveConflict: (
    conflictId: string,
    decision: ConflictDecision,
  ): Promise<AxiosResponse<ApiResponse<VersionConflict>>> =>
    client.patch(`/version/conflicts/${conflictId}/resolve/${decision}`),

  addMember: (
    projectId: string,
    data: AddProjectMemberRequest,
  ): Promise<AxiosResponse<ApiResponse<ProjectMember>>> =>
    invalidatesAccess(client.post(`/projects/${projectId}/members`, data), projectId),

  changeMemberRole: (
    projectId: string,
    memberId: string,
    data: UpdateProjectMemberRoleRequest,
  ): Promise<AxiosResponse<ApiResponse<ProjectMember>>> =>
    invalidatesAccess(client.patch(`/projects/${projectId}/members/${memberId}`, data), projectId),

  removeMember: (projectId: string, memberId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    invalidatesAccess(client.delete(`/projects/${projectId}/members/${memberId}`), projectId),

  memberCandidates: (
    projectId: string,
    q?: string,
  ): Promise<AxiosResponse<ApiResponse<ProjectMemberCandidate[]>>> =>
    client.get(`/projects/${projectId}/member-candidates`, q ? { params: { q } } : undefined),
};
