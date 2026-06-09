import { CreditBankAccount } from '../api/creditApi';
import { BankAccount } from '../types';

export type BankAccountViewModel = {
  id?: string;
  bankName: string;
  accountNumber: string;
  holderName: string;
};

export function toBankAccountViewModel(account: BankAccount | CreditBankAccount | null | undefined): BankAccountViewModel | null {
  if (!account) {
    return null;
  }

  if ('maskedAccountNumber' in account) {
    return {
      id: account.bankAccountId,
      bankName: account.bankName,
      accountNumber: account.maskedAccountNumber,
      holderName: account.accountHolderName,
    };
  }

  return {
    bankName: account.bankName,
    accountNumber: account.accountNumber,
    holderName: account.holderName,
  };
}

export function formatBankAccountLabel(account: BankAccountViewModel | null, emptyLabel = '등록된 계좌 없음') {
  return account ? `${account.bankName} ${account.accountNumber}` : emptyLabel;
}

export function formatBankAccountHolder(account: BankAccountViewModel | null, emptyLabel = '계좌를 먼저 등록하세요.') {
  return account ? `예금주 ${account.holderName}` : emptyLabel;
}
