import { apiClient } from './client';
import { tokenStorage } from '../storage/tokenStorage';
import { UserRole } from '../types';

type ApiRole = 'PARENT' | 'CHILD';

type ApiUser = {
  userId: number | string;
  name: string;
  phoneNumber?: string;
  role: ApiRole;
  status?: string;
};

type LoginResponse = {
  accessToken: string;
  user: ApiUser;
};

type SignupResponse = {
  userId: number | string;
  name: string;
  phoneNumber?: string;
  role: ApiRole;
  status?: string;
};

export type AuthUser = {
  userId: string;
  name: string;
  role: UserRole;
  status?: string;
};

function toApiRole(role: UserRole): ApiRole {
  return role === 'parent' ? 'PARENT' : 'CHILD';
}

function toUserRole(role: ApiRole): UserRole {
  return role === 'PARENT' ? 'parent' : 'child';
}

function normalizeUser(user: ApiUser): AuthUser {
  return {
    userId: String(user.userId),
    name: user.name,
    role: toUserRole(user.role),
    status: user.status,
  };
}

export const authApi = {
  async signup(input: { name: string; phoneNumber: string; password: string; role: UserRole }) {
    const response = await apiClient.post<SignupResponse>(
      '/api/users',
      {
        name: input.name,
        phoneNumber: input.phoneNumber,
        password: input.password,
        role: toApiRole(input.role),
      },
      { auth: false },
    );

    return normalizeUser(response);
  },

  async login(input: { phoneNumber: string; password: string }) {
    const response = await apiClient.post<LoginResponse>(
      '/api/users/login',
      {
        phoneNumber: input.phoneNumber,
        password: input.password,
      },
      { auth: false },
    );

    await tokenStorage.setAccessToken(response.accessToken);
    return normalizeUser(response.user);
  },

  async me() {
    const response = await apiClient.get<ApiUser>('/api/users/me');
    return normalizeUser(response);
  },

  async logout() {
    try {
      await apiClient.post('/api/users/logout');
    } finally {
      await tokenStorage.clearAccessToken();
    }
  },
};
