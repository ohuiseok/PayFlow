import { apiClient } from './client';
import { toProcessingStatus } from './statusMapper';
import { CashbookEntry, ProcessingStatus } from '../types';

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

type TossChargeCreateResponse = {
  chargeId: number | string;
  providerCode: string;
  orderId: string;
  orderName: string;
  amount: number;
  currency: string;
  status: string;
  customerKey: string;
};

type TossChargeResponse = {
  chargeId: number | string;
  providerCode: string;
  orderId: string;
  paymentKey?: string;
  amount: number;
  status: string;
  tossStatus: string;
  walletId?: number | string;
  walletTransactionId?: number | string;
  failureCode?: string | null;
  failureReason?: string | null;
  receiptUrl?: string | null;
};

type OpenBankingAuthorizeUrlResponse = {
  authorizeUrl: string;
  state: string;
};

type WalletTransactionResponse = {
  walletTransactionId: number | string;
  transactionType: 'DEPOSIT' | 'WITHDRAW' | string;
  amount: number;
  balanceAfter: number;
  referenceType: string;
  referenceId: string;
  createdAt?: string;
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

export type TossChargeStart = {
  chargeId: string;
  orderId: string;
  amount: number;
  status: ProcessingStatus;
  customerKey: string;
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

function normalizeTossCharge(response: TossChargeResponse): ChargeResult {
  return {
    chargeId: String(response.chargeId),
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

function walletTransactionTitle(referenceType: string, transactionType: string) {
  switch (referenceType) {
    case 'TOSS_PAYMENT_CHARGE':
      return 'Toss 적립금 충전';
    case 'OPEN_BANKING_CHARGE':
    case 'MANUAL_CHARGE':
      return '적립금 충전';
    case 'OPEN_BANKING_WITHDRAWAL':
      return '계좌 출금';
    case 'OPEN_BANKING_REFUND':
      return '출금 보정 환급';
    case 'TRANSFER':
      return transactionType === 'WITHDRAW' ? '미션 보상 지급' : '미션 보상 수령';
    case 'TOSS_PAYMENT_CANCEL':
      return 'Toss 충전 취소';
    default:
      return transactionType === 'WITHDRAW' ? '크레딧 사용' : '크레딧 입금';
  }
}

function normalizeWalletTransaction(transaction: WalletTransactionResponse): CashbookEntry {
  const signedAmount = transaction.transactionType === 'WITHDRAW' ? -transaction.amount : transaction.amount;
  return {
    id: String(transaction.walletTransactionId),
    title: walletTransactionTitle(transaction.referenceType, transaction.transactionType),
    description: transaction.createdAt
      ? `${transaction.createdAt.slice(0, 10)} · 잔액 ${transaction.balanceAfter.toLocaleString()}원`
      : `잔액 ${transaction.balanceAfter.toLocaleString()}원`,
    amount: signedAmount,
    type: signedAmount < 0 ? 'withdrawal' : 'charge',
  };
}

export const creditApi = {
  async getParentSummary(): Promise<ParentCreditSummary> {
    const response = await apiClient.get<ParentSummaryResponse>('/api/cashbook/parent/summary');
    return {
      walletId: String(response.walletId ?? ''),
      creditBalance: response.creditBalance ?? 0,
      monthlyRewardPaid: response.monthlyRewardPaid ?? 0,
      pendingApprovalCount: response.pendingApprovalCount ?? 0,
    };
  },

  async getRecentCreditEntries(): Promise<CashbookEntry[]> {
    const response = await apiClient.get<WalletTransactionResponse[]>('/api/wallets/me/transactions');
    return response.map(normalizeWalletTransaction);
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

  async requestTossCharge(input: { amount: number }): Promise<TossChargeStart> {
    const response = await apiClient.post<TossChargeCreateResponse>(
      '/api/payments/toss/charges',
      {
        amount: input.amount,
        orderName: 'PayFlow 크레딧 충전',
      },
      {
        headers: {
          'Idempotency-Key': makeIdempotencyKey('charge'),
        },
      },
    );

    return {
      chargeId: String(response.chargeId),
      orderId: response.orderId,
      amount: response.amount,
      status: toProcessingStatus(response.status),
      customerKey: response.customerKey,
    };
  },

  async confirmTossCharge(input: { paymentKey: string; orderId: string; amount: number }) {
    const response = await apiClient.post<TossChargeResponse>('/api/payments/toss/confirm', input);
    return normalizeTossCharge(response);
  },

  async getTossCharge(chargeId: string) {
    const response = await apiClient.get<TossChargeResponse>(
      `/api/payments/toss/charges/${encodeURIComponent(chargeId)}`,
    );
    return normalizeTossCharge(response);
  },

  async getOpenBankingAuthorizeUrl() {
    return apiClient.get<OpenBankingAuthorizeUrlResponse>('/api/bank/openbanking/authorize-url');
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
