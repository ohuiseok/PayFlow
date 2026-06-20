import { apiClient } from './client';

type TossOperationalSummaryResponse = {
  readyCount: number;
  completedCount: number;
  failedCount: number;
  canceledCount: number;
  compensationRequiredCount: number;
  ledgerCompensationRequiredCount: number;
};

type TossChargeSummaryResponse = {
  chargeId: number | string;
  userId: number | string;
  providerCode: string;
  amount: number;
  status: string;
  failureCode?: string | null;
  failureReason?: string | null;
  compensationRetryCount: number;
  compensationFailureReason?: string | null;
  ledgerRecorded: boolean;
  ledgerRecordType?: string | null;
  ledgerFailureReason?: string | null;
  ledgerRetryCount: number;
};

type LedgerLineResponse = {
  id: number | string;
  userId?: number | string | null;
  accountCode: string;
  type: string;
  amount: number;
};

type LedgerEntryResponse = {
  id: number | string;
  transferId?: number | string | null;
  sourceType: string;
  sourceId: number | string;
  entryType: string;
  amount: number;
  createdAt: string;
  lines: LedgerLineResponse[];
};

export type TossOperationalSummary = TossOperationalSummaryResponse;

export type TossCompensationCharge = {
  chargeId: string;
  userId: string;
  providerCode: string;
  amount: number;
  status: string;
  failureCode: string;
  failureReason: string;
  compensationRetryCount: number;
  compensationFailureReason: string;
  ledgerRecorded: boolean;
  ledgerRecordType: string;
  ledgerFailureReason: string;
  ledgerRetryCount: number;
};

export type LedgerLine = {
  id: string;
  userId: string;
  accountCode: string;
  type: string;
  amount: number;
};

export type LedgerEntry = {
  id: string;
  sourceLabel: string;
  sourceType: string;
  sourceId: string;
  entryType: string;
  amount: number;
  createdAt: string;
  lines: LedgerLine[];
};

function normalizeCompensation(response: TossChargeSummaryResponse): TossCompensationCharge {
  return {
    chargeId: String(response.chargeId),
    userId: String(response.userId),
    providerCode: response.providerCode,
    amount: response.amount,
    status: response.status,
    failureCode: response.failureCode ?? '',
    failureReason: response.failureReason ?? '',
    compensationRetryCount: response.compensationRetryCount,
    compensationFailureReason: response.compensationFailureReason ?? '',
    ledgerRecorded: response.ledgerRecorded,
    ledgerRecordType: response.ledgerRecordType ?? '',
    ledgerFailureReason: response.ledgerFailureReason ?? '',
    ledgerRetryCount: response.ledgerRetryCount,
  };
}

function normalizeLedgerEntry(response: LedgerEntryResponse): LedgerEntry {
  return {
    id: String(response.id),
    sourceLabel: response.transferId ? `송금 ${response.transferId}` : `${response.sourceType} ${response.sourceId}`,
    sourceType: response.sourceType,
    sourceId: String(response.sourceId),
    entryType: response.entryType,
    amount: response.amount,
    createdAt: response.createdAt,
    lines: response.lines.map((line) => ({
      id: String(line.id),
      userId: line.userId == null ? '-' : String(line.userId),
      accountCode: line.accountCode,
      type: line.type,
      amount: line.amount,
    })),
  };
}

export const operationsApi = {
  async getTossSummary(): Promise<TossOperationalSummary> {
    return apiClient.get<TossOperationalSummaryResponse>('/api/payments/toss/operations/summary');
  },

  async getTossCompensations(): Promise<TossCompensationCharge[]> {
    const response = await apiClient.get<TossChargeSummaryResponse[]>('/api/payments/toss/operations/compensations');
    return response.map(normalizeCompensation);
  },

  async getTossLedgerCompensations(): Promise<TossCompensationCharge[]> {
    const response = await apiClient.get<TossChargeSummaryResponse[]>('/api/payments/toss/operations/ledger-compensations');
    return response.map(normalizeCompensation);
  },

  async retryTossCompensation(chargeId: string) {
    return apiClient.post(`/api/payments/toss/charges/${encodeURIComponent(chargeId)}/compensate`);
  },

  async retryTossLedgerCompensation(chargeId: string) {
    return apiClient.post(`/api/payments/toss/charges/${encodeURIComponent(chargeId)}/ledger-compensate`);
  },

  async getLedgerEntries(): Promise<LedgerEntry[]> {
    const response = await apiClient.get<LedgerEntryResponse[]>('/api/ledgers/entries');
    return response.map(normalizeLedgerEntry);
  },
};
