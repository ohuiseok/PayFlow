import { apiClient } from './client';
import { toProcessingStatus } from './statusMapper';
import { ProcessingStatus } from '../types';

type ParentSummaryResponse = {
  parentUserId: number | string;
  walletId: number | string;
  creditBalance: number;
  monthlyRewardPaid: number;
  pendingApprovalCount: number;
};

type BankAccountsResponse = {
  bankAccounts: Array<{
    bankAccountId: string;
    bankCodeStd: string;
    bankName: string;
    maskedAccountNumber: string;
    accountHolderName: string;
    primary: boolean;
  }>;
};

type ChargeResponse = {
  chargeId: string;
  amount: number;
  status: string;
  walletId?: number | string;
  balanceAfter?: number;
};

type BankAccountResponse = {
  bankAccountId: string;
  bankCodeStd: string;
  bankName: string;
  maskedAccountNumber: string;
  accountHolderName: string;
  primary: boolean;
};

type WithdrawalResponse = {
  withdrawalId: string;
  walletId: number | string;
  bankAccountId: string;
  maskedAccountNumber?: string;
  amount: number;
  status: string;
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
    chargeId: response.chargeId,
    amount: response.amount,
    status: toProcessingStatus(response.status),
    balanceAfter: response.balanceAfter,
  };
}

function normalizeBankAccount(account: BankAccountResponse): CreditBankAccount {
  return {
    bankAccountId: account.bankAccountId,
    bankName: account.bankName,
    maskedAccountNumber: account.maskedAccountNumber,
    accountHolderName: account.accountHolderName,
    primary: account.primary,
  };
}

function normalizeWithdrawal(response: WithdrawalResponse): WithdrawalResult {
  return {
    withdrawalId: response.withdrawalId,
    walletId: String(response.walletId),
    bankAccountId: response.bankAccountId,
    amount: response.amount,
    status: toProcessingStatus(response.status),
  };
}

export const creditApi = {
  async getParentSummary(): Promise<ParentCreditSummary> {
    const response = await apiClient.get<ParentSummaryResponse>('/api/credits/parent/summary');
    return {
      walletId: String(response.walletId),
      creditBalance: response.creditBalance,
      monthlyRewardPaid: response.monthlyRewardPaid,
      pendingApprovalCount: response.pendingApprovalCount,
    };
  },

  async getBankAccounts(): Promise<CreditBankAccount[]> {
    const response = await apiClient.get<BankAccountsResponse>('/api/credits/bank-accounts');
    return response.bankAccounts.map(normalizeBankAccount);
  },

  async registerBankAccount(input: {
    bankCodeStd: string;
    bankName: string;
    accountNumber: string;
    accountHolderName: string;
  }) {
    const response = await apiClient.post<BankAccountResponse>('/api/credits/bank-accounts', input);
    return normalizeBankAccount(response);
  },

  async requestCharge(input: { amount: number; bankAccountId: string }) {
    const response = await apiClient.post<ChargeResponse>(
      '/api/credits/charges',
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
      `/api/credits/charges/${encodeURIComponent(chargeId)}`,
    );
    return normalizeCharge(response);
  },

  async requestWithdrawal(input: { walletId: string; bankAccountId: string; amount: number }) {
    const response = await apiClient.post<WithdrawalResponse>(
      '/api/credits/withdrawals',
      {
        walletId: input.walletId,
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
      `/api/credits/withdrawals/${encodeURIComponent(withdrawalId)}`,
    );
    return normalizeWithdrawal(response);
  },
};
