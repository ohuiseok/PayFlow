import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Platform } from 'react-native';

import { creditApi } from '../../api/creditApi';
import {
  AlertModal,
  AmountQuickSelect,
  BalanceCard,
  Body,
  Card,
  FormField,
  formatAmountInput,
  Label,
  parseAmount,
  PrimaryButton,
  ScreenFrame,
  SecondaryButton,
  Toast,
} from '../../components/common';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { ProcessingTestActions } from '../../components/common/ProcessingTestActions';
import { EmptyState } from '../../components/common/ScreenStates';
import { appConfig } from '../../config/appConfig';
import { useCreditChargeFlow } from '../../hooks/useCreditChargeFlow';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { isAmountInRange } from '../../utils/validators';
import { formatBankAccountHolder, formatBankAccountLabel, toBankAccountViewModel } from '../../viewModels/bankAccountViewModel';

type Props = NativeStackScreenProps<RootStackParamList, 'CreditCharge'>;

export function CreditChargeScreen({ navigation }: Props) {
  const { chargeCredit, parentChargeAccount, parentCreditBalance } = useAppState();
  const [amountText, setAmountText] = useState('30000');
  const [chargeMethod, setChargeMethod] = useState<'toss' | 'bank'>('toss');
  const queryClient = useQueryClient();
  const amount = parseAmount(amountText);
  const validAmount = isAmountInRange(amount, 10000, 1000000);
  const summaryQuery = useQuery({
    queryKey: ['credit', 'parentSummary'],
    queryFn: creditApi.getParentSummary,
    enabled: !appConfig.useDummyData,
  });
  const displayBalance = summaryQuery.data?.creditBalance ?? parentCreditBalance;
  const bankAccountsQuery = useQuery({
    queryKey: ['credit', 'bankAccounts'],
    queryFn: creditApi.getBankAccounts,
    enabled: !appConfig.useDummyData,
  });
  const bankAccounts = bankAccountsQuery.data ?? [];
  const selectedBankAccount = bankAccounts.find((account) => account.primary) ?? bankAccounts[0];
  const displayBankAccount = toBankAccountViewModel(appConfig.useDummyData ? parentChargeAccount : selectedBankAccount);
  const canCharge = validAmount && (chargeMethod === 'toss' || Boolean(displayBankAccount));
  const { apiError, charge, clearUserMessage, processing, status, userMessage } = useCreditChargeFlow({
    amount,
    selectedBankAccount,
    method: chargeMethod,
    valid: canCharge,
    onCompleted: () => {
      if (appConfig.useDummyData) {
        chargeCredit(amount);
      } else {
        queryClient.invalidateQueries({ queryKey: ['credit', 'parentSummary'] });
        queryClient.invalidateQueries({ queryKey: ['credit', 'recentEntries'] });
      }
    },
  });
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const tossStatus = params.get('tossStatus');
    const openBankingStatus = params.get('openbankingStatus');
    if (!tossStatus && !openBankingStatus) {
      return;
    }
    if (tossStatus === 'completed') {
      queryClient.invalidateQueries({ queryKey: ['credit', 'parentSummary'] });
      queryClient.invalidateQueries({ queryKey: ['credit', 'recentEntries'] });
    }
    if (openBankingStatus === 'completed') {
      queryClient.invalidateQueries({ queryKey: ['credit', 'bankAccounts'] });
      setChargeMethod('bank');
    }
    window.history.replaceState({}, document.title, window.location.pathname);
  }, [queryClient]);

  return (
    <ScreenFrame eyebrow="적립금 충전" title="적립금 채우기" description="">
      <BalanceCard label="현재 적립금" amount={displayBalance} description="충전 후 미션 승인에 사용할 수 있습니다." />
      <Card>
        <Label>충전 방식</Label>
        <PrimaryButton
          title="Toss 충전"
          onPress={() => setChargeMethod('toss')}
          variant={chargeMethod === 'toss' ? 'primary' : 'secondary'}
          disabled={processing}
          testID="charge-method-toss-button"
        />
        <PrimaryButton
          title="연결 계좌"
          onPress={() => setChargeMethod('bank')}
          variant={chargeMethod === 'bank' ? 'primary' : 'secondary'}
          disabled={processing}
          testID="charge-method-bank-button"
        />
      </Card>
      <Card>
        <Label>{chargeMethod === 'toss' ? 'Toss 결제' : '충전 계좌'}</Label>
        {chargeMethod === 'toss' ? (
          <Body>Toss 결제 승인 후 지갑에 바로 충전됩니다.</Body>
        ) : appConfig.useDummyData ? (
          <>
            <Body>{formatBankAccountLabel(displayBankAccount)}</Body>
            <Body>{formatBankAccountHolder(displayBankAccount)}</Body>
          </>
        ) : bankAccountsQuery.isLoading ? (
          <Body>연결 계좌를 불러오고 있습니다.</Body>
        ) : displayBankAccount ? (
          <>
            <Body>{formatBankAccountLabel(displayBankAccount)}</Body>
            <Body>{formatBankAccountHolder(displayBankAccount)}</Body>
          </>
        ) : (
          <>
            <EmptyState body="연결된 계좌가 없습니다." />
            <SecondaryButton title="Open Banking 계좌 연결" onPress={() => navigation.navigate('BankAccountRegister')} />
          </>
        )}
      </Card>
      <ApiErrorBox error={bankAccountsQuery.error} fallback="연결 계좌 조회에 실패했습니다." />
      <ApiErrorBox error={apiError} fallback="충전 처리 중 오류가 발생했습니다." />
      <FormField
        label="충전 금액"
        placeholder="10,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        disabled={processing}
        error={amountText && !validAmount ? '10,000원부터 1,000,000원까지 충전할 수 있습니다.' : undefined}
      />
      <AmountQuickSelect amounts={[10000, 30000, 50000]} onSelect={(value) => setAmountText(String(value))} />
      {status === 'completed' ? <Toast message="충전 완료 · 보상 크레딧이 증가했습니다." /> : null}
      {status === 'failed' ? <Toast tone="danger" message="충전에 실패했습니다. 다시 시도해 주세요." /> : null}
      <PrimaryButton
        title={processing ? '처리 중' : chargeMethod === 'toss' ? 'Toss로 충전하기' : '계좌로 충전하기'}
        onPress={() => charge()}
        disabled={!canCharge || processing}
        loading={processing}
      />
      {appConfig.useDummyData ? (
        <ProcessingTestActions disabled={processing || !canCharge} onSelect={(nextStatus) => charge(nextStatus)} />
      ) : null}
      {status === 'completed' ? <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} /> : null}
      <AlertModal
        visible={Boolean(userMessage)}
        title="알림"
        body={userMessage}
        onConfirm={clearUserMessage}
      />
    </ScreenFrame>
  );
}
