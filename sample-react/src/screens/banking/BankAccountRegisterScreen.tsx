import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Linking } from 'react-native';

import { creditApi } from '../../api/creditApi';
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
  const [openBankingLoading, setOpenBankingLoading] = useState(false);
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

  const connectOpenBanking = async () => {
    try {
      setApiError('');
      setOpenBankingLoading(true);
      const response = await creditApi.getOpenBankingAuthorizeUrl();
      await Linking.openURL(response.authorizeUrl);
    } catch (error) {
      setApiError(error instanceof Error ? error.message : 'Open Banking 계좌 연결을 시작하지 못했습니다.');
    } finally {
      setOpenBankingLoading(false);
    }
  };

  const submit = () => {
    if (!valid || !holderName.trim()) {
      return;
    }

    setDone(false);
    registerMutation.mutate();
  };

  return (
    <ScreenFrame eyebrow="계좌 연결" title="충전 계좌 연결" description="Open Banking으로 계좌를 연결하거나 개발용 수기 등록을 사용할 수 있습니다.">
      <InfoBox tone="blue" title="Open Banking 계좌 연결" body="인증을 완료하면 연결 계좌를 자동으로 동기화합니다." />
      <PrimaryButton
        title={openBankingLoading ? '연결 준비 중' : 'Open Banking으로 계좌 연결'}
        onPress={connectOpenBanking}
        disabled={openBankingLoading}
        loading={openBankingLoading}
        testID="open-banking-connect-button"
      />
      <InfoBox tone="yellow" title="개발용 수기 등록" body="실제 운영에서는 Open Banking 연결을 기본 동선으로 사용합니다." />
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
      <ApiErrorBox error={apiError} fallback="계좌 연결에 실패했습니다." />
      {done ? <Toast message={`${selectedBank.bankName} ${accountNumber} 계좌가 연결되었습니다.`} /> : null}
      <PrimaryButton
        title={loading ? '등록 중' : '수기 계좌 등록'}
        onPress={submit}
        disabled={!valid || !holderName.trim() || loading}
        loading={loading}
        testID="bank-register-submit-button"
      />
      {done ? (
        <SecondaryButton
          title="충전 화면으로"
          onPress={() => navigation.navigate('CreditCharge')}
          testID="bank-register-go-charge-button"
        />
      ) : null}
    </ScreenFrame>
  );
}
