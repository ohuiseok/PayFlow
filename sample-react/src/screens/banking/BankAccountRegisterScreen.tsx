import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { creditApi } from '../../api/creditApi';
import { appConfig } from '../../config/appConfig';
import { BankSelect } from '../../components/banking/BankSelect';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, InfoBox, PrimaryButton, ScreenFrame, SecondaryButton, Toast } from '../../components/common';
import { findBankOptionByName } from '../../constants/banks';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';
import { isValidBankAccountNumber, onlyDigits } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'BankAccountRegister'>;

export function BankAccountRegisterScreen({ navigation }: Props) {
  const { linkedBankAccount, registerBankAccount } = useAppState();
  const queryClient = useQueryClient();
  const [selectedBank, setSelectedBank] = useState(() => findBankOptionByName(linkedBankAccount?.bankName ?? '국민은행'));
  const [accountNumber, setAccountNumber] = useState(linkedBankAccount?.accountNumber ?? '123456789012');
  const [holderName, setHolderName] = useState(linkedBankAccount?.holderName ?? '민지');
  const [done, setDone] = useState(false);
  const [apiError, setApiError] = useState('');
  const valid = isValidBankAccountNumber(accountNumber);
  const registerMutation = useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        registerBankAccount({ bankName: selectedBank.bankName, accountNumber, holderName });
        return;
      }

      const account = await creditApi.registerBankAccount({
        bankCodeStd: selectedBank.bankCodeStd,
        bankName: selectedBank.bankName,
        accountNumber: onlyDigits(accountNumber),
        accountHolderName: holderName.trim(),
      });
      registerBankAccount({
        bankName: account.bankName,
        accountNumber: account.maskedAccountNumber,
        holderName: account.accountHolderName,
      });
    },
    onMutate: () => {
      setApiError('');
      setDone(false);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credit', 'bankAccounts'] });
      setDone(true);
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, '계좌 등록에 실패했습니다.'));
    },
  });
  const loading = registerMutation.isPending;

  const submit = () => {
    if (!valid || !holderName.trim()) {
      return;
    }

    registerMutation.mutate();
  };

  return (
    <ScreenFrame eyebrow="계좌 등록" title="받을 계좌 연결" description="출금 받을 계좌를 등록합니다.">
      <InfoBox tone="blue" title="등록 가능" body="본인 명의 계좌만 등록할 수 있습니다." />
      <BankSelect selectedBankCode={selectedBank.bankCodeStd} onSelect={setSelectedBank} disabled={loading} />
      <FormField
        label="계좌번호"
        placeholder="숫자만 10~14자리"
        value={accountNumber}
        onChangeText={(value) => setAccountNumber(onlyDigits(value))}
        keyboardType="number-pad"
        error={accountNumber && !valid ? '계좌번호는 숫자 10~14자리로 입력하세요.' : undefined}
        disabled={loading}
      />
      <FormField label="예금주" placeholder="예금주" value={holderName} onChangeText={setHolderName} disabled={loading} />
      <ApiErrorBox error={apiError} fallback="계좌 등록에 실패했습니다." />
      {done ? <Toast message={`${selectedBank.bankName} ${accountNumber} 계좌가 연결되었습니다.`} /> : null}
      <PrimaryButton
        title={loading ? '등록 중' : '계좌 등록'}
        onPress={submit}
        disabled={!valid || !holderName.trim() || loading}
        loading={loading}
      />
      {done ? <SecondaryButton title="출금 화면으로" onPress={() => navigation.navigate('ChildWithdrawal')} /> : null}
    </ScreenFrame>
  );
}
