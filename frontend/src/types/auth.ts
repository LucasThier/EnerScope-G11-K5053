export interface LoginRequest {
  mail: string;
  password: string;
}

export interface RegisterRequest {
  mail: string;
  firstName: string;
  lastName: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface NewSessionResponse {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  timestamp: string;
}
