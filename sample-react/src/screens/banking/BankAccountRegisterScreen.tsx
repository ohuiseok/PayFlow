import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';

import { BankSelect } from '../../components/banking/BankSelect';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, InfoBox, PrimaryButton, ScreenFrame, SecondaryButton, Toast } from '../../components/common';
import { findBankOptionByName } from '../../constants/banks';
import { useRegisterBankAccountMutation } from '../../hooks/useBankAccountMutations';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { isValidBankAccountNumber, onlyDigits } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'BankAccountRegister'>;

export function BankAccountRegisterScreen({ navigation }: Props) {
  const { linkedBankAccount, registerBankAccount } = useAppState();
  const [selectedBank, setSelectedBank] = useState(() => findBankOptionByName(linkedBankAccount?.bankName ?? '국민은행'));
  const [accountNumber, setAccountNumber] = useState(linkedBankAccount?.accountNumber ?? '123456789012');
  const [holderName, setHolderName] = useState(linkedBankAccount?.holderName ?? '민지');
  const [done, setDone] = useState(false);
  const [apiError, setApiError] = useState('');
  const valid = isValidBankAccountNumber(accountNumber);
  const registerMutation = useRegisterBankAccountMutation({
    accountNumber,
    holderName,
    selectedBank,
    onError: setApiError,
    onRegister: registerBankAccount,
    onSuccess: () => setDone(true),
  });
  const loading = registerMutation.isPending;

  const submit = () => {
    if (!valid || !holderName.trim()) {
      return;
    }

    setDone(false);
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
        testID="bank-register-submit-button"
      />
      {done ? <SecondaryButton title="출금 화면으로" onPress={() => navigation.navigate('ChildWithdrawal')} testID="bank-register-go-withdrawal-button" /> : null}
    </ScreenFrame>
  );
}
