export interface UserSummary {
  id: number;
  email: string;
  fullName: string | null;
  employeeId: number | null;
  roles: string[];
  permissions: string[];
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMs: number;
  user: UserSummary;
}

export interface LoginRequest {
  email: string;
  password: string;
}
