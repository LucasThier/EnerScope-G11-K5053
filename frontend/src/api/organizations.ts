import { client } from './client';
import type {
  ApiResponse,
  OrganizationMemberSummary,
  OrganizationSummary,
  RegisterOrganizationUserRequest,
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
  registerUser: (
    organizationId: string,
    data: RegisterOrganizationUserRequest,
  ): Promise<AxiosResponse<ApiResponse<OrganizationMemberSummary>>> =>
    client.post(`/organizations/${organizationId}/users`, data),
};
