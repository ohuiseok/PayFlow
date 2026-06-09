import { apiClient } from './client';
import { Mission, MissionStatus, UserRole } from '../types';

type ApiMissionStatus =
  | 'REGISTERED'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID'
  | 'CANCELED';

type MissionListResponse = {
  missions: ApiMission[];
};

type ApiMission = {
  missionId: number | string;
  childUserId?: number | string;
  childName?: string;
  title: string;
  description?: string;
  rewardAmount: number;
  status: ApiMissionStatus | string;
  missionDate: string;
  rejectionReason?: string | null;
};

type MissionActionResponse = {
  missionId: number | string;
  status: ApiMissionStatus | string;
  rejectionReason?: string | null;
};

type CreateMissionRequest = {
  childUserId: number | string;
  title: string;
  description: string;
  rewardAmount: number;
  missionDate: string;
  evidenceRequired: boolean;
};

function toMissionStatus(status: string): MissionStatus {
  switch (status.toUpperCase()) {
    case 'REGISTERED':
      return 'todo';
    case 'SUBMITTED':
      return 'submitted';
    case 'APPROVED':
      return 'approved';
    case 'REJECTED':
      return 'rejected';
    case 'PAID':
      return 'paid';
    default:
      return 'todo';
  }
}

function normalizeMission(mission: ApiMission): Mission {
  return {
    id: String(mission.missionId),
    childId: String(mission.childUserId ?? 'child-unknown'),
    childName: mission.childName ?? '자녀',
    title: mission.title,
    description: mission.description ?? '',
    rewardAmount: mission.rewardAmount,
    dueDate: mission.missionDate,
    status: toMissionStatus(mission.status),
    rejectReason: mission.rejectionReason ?? undefined,
  };
}

export const defaultChildUserId = 2;

export const missionApi = {
  async getMissions(input: { role: UserRole; status?: 'active' }) {
    const params = new URLSearchParams({
      role: input.role,
      status: input.status ?? 'active',
    });
    const response = await apiClient.get<MissionListResponse>(`/api/missions?${params.toString()}`);
    return response.missions.map(normalizeMission);
  },

  async createMission(input: CreateMissionRequest) {
    const response = await apiClient.post<ApiMission>('/api/missions', input);
    return normalizeMission(response);
  },

  async submitMission(input: { missionId: string; memo: string; evidenceImageUrl?: string }) {
    const response = await apiClient.post<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(input.missionId)}/submit`,
      {
        memo: input.memo,
        evidenceImageUrl: input.evidenceImageUrl ?? 'https://example.com/evidence/mission-placeholder.jpg',
      },
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
    };
  },

  async approveMission(missionId: string) {
    const response = await apiClient.post<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(missionId)}/approve`,
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
    };
  },

  async rejectMission(input: { missionId: string; reason: string }) {
    const response = await apiClient.post<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(input.missionId)}/reject`,
      {
        reason: input.reason,
      },
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
      rejectReason: response.rejectionReason ?? input.reason,
    };
  },

  async resubmitMission(input: { missionId: string; memo: string; evidenceImageUrl?: string }) {
    const response = await apiClient.post<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(input.missionId)}/resubmit`,
      {
        memo: input.memo,
        evidenceImageUrl: input.evidenceImageUrl ?? 'https://example.com/evidence/mission-placeholder-v2.jpg',
      },
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
    };
  },
};
