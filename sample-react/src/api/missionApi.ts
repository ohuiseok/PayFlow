import { apiClient } from './client';
import { Mission, MissionStatus, UserRole } from '../types';

type ApiMissionStatus =
  | 'CREATED'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PAID'
  | 'CANCELED';

type ApiMission = {
  missionId: number | string;
  childUserId?: number | string;
  childName?: string;
  title: string;
  description?: string;
  rewardAmount: number;
  status: ApiMissionStatus | string;
  missionDate?: string;
  submissionNote?: string | null;
  rejectReason?: string | null;
  rejectionReason?: string | null;
};

type MissionActionResponse = {
  missionId: number | string;
  status: ApiMissionStatus | string;
  rejectReason?: string | null;
  rejectionReason?: string | null;
};

type CreateMissionRequest = {
  childUserId: number | string;
  title: string;
  description: string;
  rewardAmount: number;
  missionDate?: string;
  evidenceRequired?: boolean;
};

function toMissionStatus(status: string): MissionStatus {
  switch (status.toUpperCase()) {
    case 'CREATED':
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
    dueDate: mission.missionDate ?? '',
    status: toMissionStatus(mission.status),
    submitMemo: mission.submissionNote ?? undefined,
    rejectReason: mission.rejectReason ?? mission.rejectionReason ?? undefined,
  };
}

export const defaultChildUserId = 2;

export const missionApi = {
  async getMissions(input: { role: UserRole; status?: 'active'; date?: string }) {
    const params = new URLSearchParams();
    if (input.date) params.append('date', input.date);
    const qs = params.toString();
    const response = await apiClient.get<ApiMission[]>(qs ? `/api/missions?${qs}` : '/api/missions');
    return response.map(normalizeMission);
  },

  async createMission(input: CreateMissionRequest) {
    const response = await apiClient.post<ApiMission>('/api/missions', input);
    return normalizeMission(response);
  },

  async submitMission(input: { missionId: string; memo: string; evidenceImageUrl?: string }) {
    const response = await apiClient.patch<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(input.missionId)}/submit`,
      {
        submissionNote: input.memo,
      },
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
    };
  },

  async approveMission(missionId: string) {
    await apiClient.patch<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(missionId)}/approve`,
    );
    const response = await apiClient.post<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(missionId)}/pay`,
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
    };
  },

  async rejectMission(input: { missionId: string; reason: string }) {
    const response = await apiClient.patch<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(input.missionId)}/reject`,
      {
        reason: input.reason,
      },
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
      rejectReason: response.rejectReason ?? response.rejectionReason ?? input.reason,
    };
  },

  async resubmitMission(input: { missionId: string; memo: string; evidenceImageUrl?: string }) {
    const response = await apiClient.patch<MissionActionResponse>(
      `/api/missions/${encodeURIComponent(input.missionId)}/submit`,
      {
        submissionNote: input.memo,
      },
    );
    return {
      missionId: String(response.missionId),
      status: toMissionStatus(response.status),
    };
  },
};
