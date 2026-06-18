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
  walletId: string;
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
      childName: response.childName ?? 'Child',
      walletId: String(response.walletId ?? ''),
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
      description: entry.description ?? 'Paid mission reward',
      amount: entry.rewardAmount,
      type: 'reward',
    }));
  },
};
