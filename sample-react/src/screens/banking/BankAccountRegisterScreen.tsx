import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { creditApi } from '../../api/creditApi';
import { appConfig } from '../../config/appConfig';
import { FormField, InfoBox, PrimaryButton, ScreenFrame, SecondaryButton, Toast } from '../../components/common';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';

type Props = NativeStackScreenProps<RootStackParamList, 'BankAccountRegister'>;

const bankCodeByName: Record<string, string> = {
  국민은행: '004',
  국민: '004',
  신한은행: '088',
  신한: '088',
  우리은행: '020',
  우리: '020',
  하나은행: '081',
  하나: '081',
  농협은행: '011',
  농협: '011',
};

function resolveBankCode(bankName: string) {
  return bankCodeByName[bankName.trim()] ?? '004';
}

export function BankAccountRegisterScreen({ navigation }: Props) {
  const { linkedBankAccount, registerBankAccount } = useAppState();
  const queryClient = useQueryClient();
  const [bankName, setBankName] = useState(linkedBankAccount?.bankName ?? '국민은행');
  const [accountNumber, setAccountNumber] = useState(linkedBankAccount?.accountNumber ?? '123456789012');
  const [holderName, setHolderName] = useState(linkedBankAccount?.holderName ?? '민지');
  const [done, setDone] = useState(false);
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const valid = /^\d{10,14}$/.test(accountNumber.replace(/[^0-9]/g, ''));

  const submit = async () => {
    if (!valid || !bankName.trim() || !holderName.trim()) {
      return;
    }

    setLoading(true);
    setApiError('');

    try {
      if (appConfig.useDummyData) {
        registerBankAccount({ bankName, accountNumber, holderName });
      } else {
        const account = await creditApi.registerBankAccount({
          bankCodeStd: resolveBankCode(bankName),
          bankName: bankName.trim(),
          accountNumber: accountNumber.replace(/[^0-9]/g, ''),
          accountHolderName: holderName.trim(),
        });
        registerBankAccount({
          bankName: account.bankName,
          accountNumber: account.maskedAccountNumber,
          holderName: account.accountHolderName,
        });
      }
      queryClient.invalidateQueries({ queryKey: ['credit', 'bankAccounts'] });
      setDone(true);
    } catch (error) {
      setApiError(error instanceof Error ? error.message : '계좌 등록에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame eyebrow="계좌 등록" title="받을 계좌 연결" description="출금 받을 계좌를 등록합니다.">
      <InfoBox tone="blue" title="등록 가능" body="본인 명의 계좌만 등록할 수 있습니다." />
      <FormField label="은행" placeholder="은행 선택" value={bankName} onChangeText={setBankName} disabled={loading} />
      <FormField
        label="계좌번호"
        placeholder="숫자만 10~14자리"
        value={accountNumber}
        onChangeText={(value) => setAccountNumber(value.replace(/[^0-9]/g, ''))}
        keyboardType="number-pad"
        error={accountNumber && !valid ? '계좌번호는 숫자 10~14자리로 입력하세요.' : undefined}
        disabled={loading}
      />
      <FormField label="예금주" placeholder="예금주" value={holderName} onChangeText={setHolderName} disabled={loading} />
      {apiError ? <InfoBox tone="yellow" title="API 오류" body={apiError} /> : null}
      {done ? <Toast message={`${bankName} ${accountNumber} 계좌가 연결되었습니다.`} /> : null}
      <PrimaryButton
        title={loading ? '등록 중' : '계좌 등록'}
        onPress={submit}
        disabled={!valid || !bankName.trim() || !holderName.trim() || loading}
        loading={loading}
      />
      {done ? <SecondaryButton title="출금 화면으로" onPress={() => navigation.navigate('ChildWithdrawal')} /> : null}
    </ScreenFrame>
  );
}
