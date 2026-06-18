import { apiClient } from './client';
import { UserRole } from '../types';

type FamilyLinkResponse = {
  familyLinkId: number | string;
  parentUserId: number | string;
  childUserId: number | string;
  status: string;
};

export type LinkedFamily = {
  familyId: number | string;
  childUserId?: number | string;
  childName?: string;
  parentUserId?: number | string;
  parentName?: string;
  status: string;
};

function normalizeFamily(link: FamilyLinkResponse): LinkedFamily {
  return {
    familyId: link.familyLinkId,
    parentUserId: link.parentUserId,
    childUserId: link.childUserId,
    childName: `Child ${link.childUserId}`,
    status: link.status === 'ACTIVE' ? 'CONNECTED' : link.status,
  };
}

export const familyApi = {
  createInvitation() {
    return Promise.resolve({
      inviteCode: 'DIRECT',
      status: 'READY',
    });
  },

  getInvitation(inviteCode: string) {
    return Promise.resolve({
      inviteCode,
      parentName: 'Parent',
      status: 'READY',
    });
  },

  requestLink(inviteCode: string) {
    const childUserId = Number(inviteCode);
    return apiClient.post<FamilyLinkResponse>('/api/families/links', { childUserId });
  },

  approveLinkRequest(requestId: number | string) {
    return Promise.resolve({
      familyId: requestId,
      parentUserId: '',
      childUserId: '',
      status: 'CONNECTED',
    });
  },

  rejectLinkRequest(requestId: number | string, reason: string) {
    return Promise.resolve({
      requestId,
      reason,
      status: 'REJECTED',
    });
  },

  async getMyFamilies() {
    const response = await apiClient.get<FamilyLinkResponse[]>('/api/families/children');
    const families = response.map(normalizeFamily);
    return {
      role: 'parent' as UserRole,
      families,
      linked: families.some((family) => family.status === 'CONNECTED'),
    };
  },
};
