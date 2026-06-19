import { apiClient } from './client';
import { toProcessingStatus } from './statusMapper';
import { ProcessingStatus } from '../types';

type ParentSummaryResponse = {
  walletId?: number | string;
  creditBalance?: number;
  monthlyRewardPaid?: number;
  pendingApprovalCount?: number;
};

type ChargeResponse = {
  bankingTransferId: number | string;
  bankAccountId: number | string;
  walletId?: number | string;
  amount: number;
  status: string;
  walletTransactionId?: number | string;
  failureReason?: string | null;
};

type BankAccountResponse = {
  bankAccountId: number | string;
  bankCode: string;
  accountNumberMasked: string;
  accountHolderName: string;
  status: string;
};

type WithdrawalResponse = {
  bankingTransferId: number | string;
  walletId?: number | string;
  bankAccountId: number | string;
  maskedAccountNumber?: string;
  amount: number;
  status: string;
  failureReason?: string | null;
  compensationRetryCount?: number;
  compensationFailureReason?: string | null;
  compensatedAt?: string | null;
};

export type CreditBankAccount = {
  bankAccountId: string;
  bankName: string;
  maskedAccountNumber: string;
  accountHolderName: string;
  primary: boolean;
};

export type ParentCreditSummary = {
  walletId: string;
  creditBalance: number;
  monthlyRewardPaid: number;
  pendingApprovalCount: number;
};

export type ChargeResult = {
  chargeId: string;
  amount: number;
  status: ProcessingStatus;
  balanceAfter?: number;
};

export type WithdrawalResult = {
  withdrawalId: string;
  walletId: string;
  bankAccountId: string;
  amount: number;
  status: ProcessingStatus;
};

function makeIdempotencyKey(kind: 'charge' | 'withdrawal') {
  return `${new Date().toISOString()}-${kind}-${Math.random().toString(16).slice(2)}`;
}

function normalizeCharge(response: ChargeResponse): ChargeResult {
  return {
    chargeId: String(response.bankingTransferId),
    amount: response.amount,
    status: toProcessingStatus(response.status),
  };
}

function normalizeBankAccount(account: BankAccountResponse): CreditBankAccount {
  return {
    bankAccountId: String(account.bankAccountId),
    bankName: account.bankCode,
    maskedAccountNumber: account.accountNumberMasked,
    accountHolderName: account.accountHolderName,
    primary: account.status === 'ACTIVE',
  };
}

function normalizeWithdrawal(response: WithdrawalResponse): WithdrawalResult {
  return {
    withdrawalId: String(response.bankingTransferId),
    walletId: String(response.walletId ?? ''),
    bankAccountId: String(response.bankAccountId),
    amount: response.amount,
    status: toProcessingStatus(response.status),
  };
}

export const creditApi = {
  async getParentSummary(): Promise<ParentCreditSummary> {
    // Dedicated parent summary API is not available yet; keep real API mode usable
    // with a neutral summary while mission/banking endpoints carry the MVP flow.
    const response: ParentSummaryResponse = {};
    return {
      walletId: String(response.walletId ?? ''),
      creditBalance: response.creditBalance ?? 0,
      monthlyRewardPaid: response.monthlyRewardPaid ?? 0,
      pendingApprovalCount: response.pendingApprovalCount ?? 0,
    };
  },

  async getBankAccounts(): Promise<CreditBankAccount[]> {
    const response = await apiClient.get<BankAccountResponse[]>('/api/bank/accounts');
    return response.map(normalizeBankAccount);
  },

  async registerBankAccount(input: {
    bankCodeStd: string;
    bankName: string;
    accountNumber: string;
    accountHolderName: string;
  }) {
    const response = await apiClient.post<BankAccountResponse>('/api/bank/accounts', {
      bankCode: input.bankCodeStd || input.bankName,
      accountNumber: input.accountNumber,
      accountHolderName: input.accountHolderName,
    });
    return normalizeBankAccount(response);
  },

  async requestCharge(input: { amount: number; bankAccountId: string }) {
    const response = await apiClient.post<ChargeResponse>(
      '/api/bank/deposits',
      {
        amount: input.amount,
        bankAccountId: input.bankAccountId,
      },
      {
        headers: {
          'Idempotency-Key': makeIdempotencyKey('charge'),
        },
      },
    );

    return normalizeCharge(response);
  },

  async getCharge(chargeId: string) {
    const response = await apiClient.get<ChargeResponse>(
      `/api/bank/transfers/${encodeURIComponent(chargeId)}`,
    );
    return normalizeCharge(response);
  },

  async requestWithdrawal(input: { walletId: string; bankAccountId: string; amount: number }) {
    const response = await apiClient.post<WithdrawalResponse>(
      '/api/bank/withdrawals',
      {
        bankAccountId: input.bankAccountId,
        amount: input.amount,
      },
      {
        headers: {
          'Idempotency-Key': makeIdempotencyKey('withdrawal'),
        },
      },
    );
    return normalizeWithdrawal(response);
  },

  async getWithdrawal(withdrawalId: string) {
    const response = await apiClient.get<WithdrawalResponse>(
      `/api/bank/transfers/${encodeURIComponent(withdrawalId)}`,
    );
    return normalizeWithdrawal(response);
  },
};
