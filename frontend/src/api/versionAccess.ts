import { client } from './client';
import { invalidatesAccess } from './accessEvents';
import type { ApiResponse } from '../types/auth';
import type {
  AddVersionMemberRoleRequest,
  ProjectAccess,
  VersionMember,
} from '../types/versionAccess';
import type { AxiosResponse } from 'axios';

export const versionAccessApi = {
  projectAccess: (projectId: string): Promise<AxiosResponse<ApiResponse<ProjectAccess>>> =>
    client.get(`/projects/${projectId}/access`),

  members: (versionId: string): Promise<AxiosResponse<ApiResponse<VersionMember[]>>> =>
    client.get(`/version/${versionId}/members`),

  addRole: (
    versionId: string,
    data: AddVersionMemberRoleRequest,
  ): Promise<AxiosResponse<ApiResponse<VersionMember>>> =>
    invalidatesAccess(client.post(`/version/${versionId}/members`, data)),

  removeRole: (
    versionId: string,
    memberId: string,
    roleId: string,
  ): Promise<AxiosResponse<ApiResponse<null>>> =>
    invalidatesAccess(client.delete(`/version/${versionId}/members/${memberId}/roles/${roleId}`)),
};
