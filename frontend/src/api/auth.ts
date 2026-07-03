import { client } from './client';
import { session } from './session';
import type {
  LoginRequest,
  RegisterRequest,
  RefreshRequest,
  NewSessionResponse,
  ApiResponse,
} from '../types/auth';
import type { AxiosResponse } from 'axios';

export const authApi = {
  login: (data: LoginRequest): Promise<AxiosResponse<ApiResponse<NewSessionResponse>>> =>
    client.post('/auth/login', data),

  register: (data: RegisterRequest): Promise<AxiosResponse<ApiResponse<NewSessionResponse>>> =>
    client.post('/auth/register', data),

  refresh: (data: RefreshRequest): Promise<AxiosResponse<ApiResponse<NewSessionResponse>>> =>
    client.post('/auth/refresh', data),

  logout: async (): Promise<void> => {
    try {
      await client.post('/auth/logout');
    } finally {
      session.clear();
    }
  },
};
