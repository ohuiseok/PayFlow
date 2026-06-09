import { apiClient } from './client';
import { CashbookEntry } from '../types';

type CashbookSummaryResponse = {
  childUserId: number | string;
  childName: string;
  walletId: number | string;
  balance: number;
  weeklyEarned: number;
  completedMissionCount: number;
};

type CashbookEntriesResponse = {
  entries: Array<{
    entryId: number | string;
    title: string;
    description?: string;
    amount: number;
    type: 'EARNED' | 'SPENT' | 'WITHDRAWAL' | 'CHARGE' | string;
    createdAt?: string;
  }>;
};

export type CashbookSummary = {
  childUserId: string;
  childName: string;
  walletId: string;
  balance: number;
  weeklyEarned: number;
  completedMissionCount: number;
};

function normalizeEntryType(type: string): CashbookEntry['type'] {
  switch (type.toUpperCase()) {
    case 'EARNED':
      return 'reward';
    case 'SPENT':
    case 'WITHDRAWAL':
      return 'withdrawal';
    case 'CHARGE':
      return 'charge';
    default:
      return 'reward';
  }
}

export const cashbookApi = {
  async getChildSummary(childUserId: number | string): Promise<CashbookSummary> {
    const response = await apiClient.get<CashbookSummaryResponse>(
      `/api/cashbook/children/${encodeURIComponent(String(childUserId))}/summary`,
    );
    return {
      childUserId: String(response.childUserId),
      childName: response.childName,
      walletId: String(response.walletId),
      balance: response.balance,
      weeklyEarned: response.weeklyEarned,
      completedMissionCount: response.completedMissionCount,
    };
  },

  async getChildEntries(childUserId: number | string): Promise<CashbookEntry[]> {
    const response = await apiClient.get<CashbookEntriesResponse>(
      `/api/cashbook/children/${encodeURIComponent(String(childUserId))}/entries`,
    );
    return response.entries.map((entry) => ({
      id: String(entry.entryId),
      title: entry.title,
      description: entry.description ?? entry.createdAt ?? '캐시북 기록',
      amount: entry.amount,
      type: normalizeEntryType(entry.type),
    }));
  },
};
