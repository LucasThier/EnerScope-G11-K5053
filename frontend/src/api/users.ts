import { client } from './client';
import type {
  ApiResponse,
  ChangePasswordRequest,
  UpdateProfileRequest,
  UserSummary,
} from '../types/auth';
import type { AxiosResponse } from 'axios';

export const usersApi = {
  updateProfile: (
    data: UpdateProfileRequest,
  ): Promise<AxiosResponse<ApiResponse<UserSummary>>> => client.patch('/users/me', data),

  changePassword: (
    data: ChangePasswordRequest,
  ): Promise<AxiosResponse<ApiResponse<null>>> => client.patch('/users/me/password', data),
};
