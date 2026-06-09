import { apiClient } from './client';
import { UserRole } from '../types';

type ApiRole = 'PARENT' | 'CHILD';

type InvitationResponse = {
  invitationId?: number | string;
  inviteCode: string;
  expiresAt?: string;
  parentUserId?: number | string;
  parentName?: string;
  status: string;
};

type LinkRequestResponse = {
  requestId: number | string;
  parentUserId: number | string;
  childUserId: number | string;
  status: string;
};

type FamilyConnectionResponse = {
  familyId: number | string;
  parentUserId: number | string;
  childUserId: number | string;
  childName?: string;
  status: string;
};

type RejectLinkResponse = {
  requestId: number | string;
  status: string;
};

type FamilyListResponse = {
  role: ApiRole;
  families: LinkedFamily[];
};

export type LinkedFamily = {
  familyId: number | string;
  childUserId?: number | string;
  childName?: string;
  parentUserId?: number | string;
  parentName?: string;
  status: string;
};

function normalizeRole(role: ApiRole): UserRole {
  return role === 'PARENT' ? 'parent' : 'child';
}

export const familyApi = {
  createInvitation() {
    return apiClient.post<InvitationResponse>('/api/families/invitations');
  },

  getInvitation(inviteCode: string) {
    return apiClient.get<InvitationResponse>(
      `/api/families/invitations/${encodeURIComponent(inviteCode)}`,
    );
  },

  requestLink(inviteCode: string) {
    return apiClient.post<LinkRequestResponse>('/api/families/link-requests', { inviteCode });
  },

  approveLinkRequest(requestId: number | string) {
    return apiClient.post<FamilyConnectionResponse>(
      `/api/families/link-requests/${encodeURIComponent(String(requestId))}/approve`,
    );
  },

  rejectLinkRequest(requestId: number | string, reason: string) {
    return apiClient.post<RejectLinkResponse>(
      `/api/families/link-requests/${encodeURIComponent(String(requestId))}/reject`,
      { reason },
    );
  },

  async getMyFamilies() {
    const response = await apiClient.get<FamilyListResponse>('/api/families/me');
    return {
      role: normalizeRole(response.role),
      families: response.families,
      linked: response.families.some((family) => family.status === 'CONNECTED'),
    };
  },
};
