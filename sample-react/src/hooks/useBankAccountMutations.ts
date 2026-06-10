import { useMutation, useQueryClient } from '@tanstack/react-query';

import { creditApi } from '../api/creditApi';
import { appConfig } from '../config/appConfig';
import { BankOption } from '../constants/banks';
import { BankAccount } from '../types';
import { getErrorMessage } from '../utils/apiError';
import { onlyDigits } from '../utils/validators';

export function useRegisterBankAccountMutation({
  accountNumber,
  holderName,
  selectedBank,
  onError,
  onRegister,
  onSuccess,
}: {
  accountNumber: string;
  holderName: string;
  selectedBank: BankOption;
  onError: (message: string) => void;
  onRegister: (account: BankAccount) => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        onRegister({ bankName: selectedBank.bankName, accountNumber, holderName });
        return;
      }

      const account = await creditApi.registerBankAccount({
        bankCodeStd: selectedBank.bankCodeStd,
        bankName: selectedBank.bankName,
        accountNumber: onlyDigits(accountNumber),
        accountHolderName: holderName.trim(),
      });
      onRegister({
        bankName: account.bankName,
        accountNumber: account.maskedAccountNumber,
        holderName: account.accountHolderName,
      });
    },
    onMutate: () => {
      onError('');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credit', 'bankAccounts'] });
      onSuccess();
    },
    onError: (error) => {
      onError(getErrorMessage(error, '계좌 등록에 실패했습니다.'));
    },
  });
}
