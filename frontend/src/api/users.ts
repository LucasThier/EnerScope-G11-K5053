import { client } from './client';
import type {
  ApiResponse,
  ChangePasswordRequest,
  UpdateProfileRequest,
  UpdateRoleRequest,
  UserDetail,
  UserListItem,
  UserSearchResult,
  UserSummary,
} from '../types/auth';
import type { AxiosResponse } from 'axios';

export const usersApi = {
  list: (): Promise<AxiosResponse<ApiResponse<UserListItem[]>>> => client.get('/users'),

  detail: (userId: string): Promise<AxiosResponse<ApiResponse<UserDetail>>> =>
    client.get(`/users/${userId}`),

  searchByMail: (mail: string): Promise<AxiosResponse<ApiResponse<UserSearchResult>>> =>
    client.get('/users/search', { params: { mail } }),

  updateRole: (
    userId: string,
    data: UpdateRoleRequest,
  ): Promise<AxiosResponse<ApiResponse<UserSummary>>> =>
    client.patch(`/users/${userId}/role`, data),

  deactivate: (userId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.delete(`/users/${userId}`),

  reactivate: (userId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.post(`/users/${userId}/reactivate`),


  updateProfile: (
    data: UpdateProfileRequest,
  ): Promise<AxiosResponse<ApiResponse<UserSummary>>> => client.patch('/users/me', data),

  changePassword: (
    data: ChangePasswordRequest,
  ): Promise<AxiosResponse<ApiResponse<null>>> => client.patch('/users/me/password', data),
};
