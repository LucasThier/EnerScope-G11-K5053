import axios from 'axios';
import { session } from './session';
import type { ApiResponse, NewSessionResponse } from '../types/auth';

export const client = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

client.interceptors.request.use((config) => {
  const token = session.getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// On a 401, try to refresh the session once and replay the original request.
client.interceptors.response.use(
  (res) => res,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = session.getRefreshToken();
      if (refreshToken) {
        try {
          const res = await axios.post<ApiResponse<NewSessionResponse>>(
            '/api/v1/auth/refresh',
            { refreshToken },
          );
          const data = res.data.data;
          if (data) {
            session.save(data);
            originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
            return client(originalRequest);
          }
        } catch {
          session.clear();
        }
      }
    }
    return Promise.reject(error);
  },
);
