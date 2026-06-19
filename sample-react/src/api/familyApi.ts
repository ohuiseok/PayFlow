import { apiClient } from './client';
import { UserRole } from '../types';

type FamilyLinkResponse = {
  familyLinkId: number | string;
  parentUserId: number | string;
  childUserId: number | string;
  childName?: string;
  childPhoneNumber?: string;
  status: string;
};

export type LinkedFamily = {
  familyId: number | string;
  childUserId?: number | string;
  childName?: string;
  childPhoneNumber?: string;
  parentUserId?: number | string;
  parentName?: string;
  status: string;
};

function normalizeFamily(link: FamilyLinkResponse): LinkedFamily {
  return {
    familyId: link.familyLinkId,
    parentUserId: link.parentUserId,
    childUserId: link.childUserId,
    childName: link.childName ?? `자녀 ${link.childUserId}`,
    childPhoneNumber: link.childPhoneNumber,
    status: link.status === 'ACTIVE' ? 'CONNECTED' : link.status,
  };
}

export const familyApi = {
  requestLink(childUserIdInput: number | string) {
    const childUserId = Number(childUserIdInput);
    return apiClient.post<FamilyLinkResponse>('/api/families/links', { childUserId });
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

  async getMyParents() {
    const response = await apiClient.get<FamilyLinkResponse[]>('/api/families/parents');
    const families = response.map(normalizeFamily);
    return {
      role: 'child' as UserRole,
      families,
      linked: families.some((family) => family.status === 'CONNECTED'),
    };
  },
};
