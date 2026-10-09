import { client } from './client';
import type {
  AddOrganizationMemberRequest,
  UpdateOrganizationMemberRoleRequest,
  ApiResponse,
  OrganizationMemberSummary,
  OrganizationSummary,
  RegisterOrganizationUserRequest,
  UpdateOrganizationRequest,
} from '../types/auth';
import type { AxiosResponse } from 'axios';

export const organizationsApi = {
  /** Organizations visible to the caller (all for admins, own for others). */
  list: (): Promise<AxiosResponse<ApiResponse<OrganizationSummary[]>>> =>
    client.get('/organizations'),

  create: (data: { name: string }): Promise<AxiosResponse<ApiResponse<OrganizationSummary>>> =>
    client.post('/organizations', data),

  /**
   * Register a brand new user directly into an organization. Allowed for
   * platform admins and organization owners (MANAGE_ORGANIZATION permission).
   */
  owned: (): Promise<AxiosResponse<ApiResponse<OrganizationSummary[]>>> =>
    client.get('/organizations/owned'),

  addMember: (
    organizationId: string,
    data: AddOrganizationMemberRequest,
  ): Promise<AxiosResponse<ApiResponse<OrganizationMemberSummary>>> =>
    client.post(`/organizations/${organizationId}/members`, data),

  update: (
    organizationId: string,
    data: UpdateOrganizationRequest,
  ): Promise<AxiosResponse<ApiResponse<OrganizationSummary>>> =>
    client.patch(`/organizations/${organizationId}`, data),

  deactivate: (organizationId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.delete(`/organizations/${organizationId}`),

  reactivate: (organizationId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.post(`/organizations/${organizationId}/reactivate`),

  members: (
    organizationId: string,
  ): Promise<AxiosResponse<ApiResponse<OrganizationMemberSummary[]>>> =>
    client.get(`/organizations/${organizationId}/members`),

  removeMember: (
    organizationId: string,
    memberId: string,
  ): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.delete(`/organizations/${organizationId}/members/${memberId}`),

  changeMemberRole: (
    organizationId: string,
    memberId: string,
    data: UpdateOrganizationMemberRoleRequest,
  ): Promise<AxiosResponse<ApiResponse<OrganizationMemberSummary>>> =>
    client.patch(`/organizations/${organizationId}/members/${memberId}`, data),

  registerUser: (
    organizationId: string,
    data: RegisterOrganizationUserRequest,
  ): Promise<AxiosResponse<ApiResponse<OrganizationMemberSummary>>> =>
    client.post(`/organizations/${organizationId}/users`, data),
};
