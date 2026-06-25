import { apiClient } from './client';
import { CashbookEntry } from '../types';

type CashbookSummaryResponse = {
  childUserId: number | string;
  childName?: string;
  walletId?: number | string;
  walletBalance: number;
  paidRewardAmount: number;
  paidMissionCount: number;
};

type CashbookEntryResponse = {
  missionId: number | string;
  title: string;
  description?: string;
  rewardAmount: number;
  status: string;
};

export type CashbookSummary = {
  childUserId: string;
  childName: string;
  walletId: string | undefined;
  balance: number;
  weeklyEarned: number;
  completedMissionCount: number;
};

export const cashbookApi = {
  async getChildSummary(childUserId: number | string): Promise<CashbookSummary> {
    const response = await apiClient.get<CashbookSummaryResponse>(
      `/api/cashbook/children/${encodeURIComponent(String(childUserId))}/summary`,
    );
    return {
      childUserId: String(response.childUserId),
      childName: response.childName ?? '청년',
      walletId: response.walletId != null ? String(response.walletId) : undefined,
      balance: response.walletBalance,
      weeklyEarned: response.paidRewardAmount,
      completedMissionCount: response.paidMissionCount,
    };
  },

  async getChildEntries(childUserId: number | string): Promise<CashbookEntry[]> {
    const response = await apiClient.get<CashbookEntryResponse[]>(
      `/api/cashbook/children/${encodeURIComponent(String(childUserId))}/entries`,
    );
    return response.map((entry) => ({
      id: String(entry.missionId),
      title: entry.title,
      description: entry.description ?? '정책 미션 지원금 지급',
      amount: entry.rewardAmount,
      type: 'reward',
    }));
  },
};
