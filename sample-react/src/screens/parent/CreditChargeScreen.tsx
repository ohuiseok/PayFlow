import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { creditApi } from '../../api/creditApi';
import {
  AmountQuickSelect,
  BalanceCard,
  Body,
  Card,
  FormField,
  formatAmountInput,
  formatWon,
  InfoBox,
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
import { processingLabel } from '../shared/processingStatus';

type Props = NativeStackScreenProps<RootStackParamList, 'CreditCharge'>;

export function CreditChargeScreen({ navigation }: Props) {
  const { chargeCredit, parentChargeAccount, parentCreditBalance } = useAppState();
  const [amountText, setAmountText] = useState('30000');
  const queryClient = useQueryClient();
  const amount = parseAmount(amountText);
  const valid = isAmountInRange(amount, 10000, 1000000);
  const bankAccountsQuery = useQuery({
    queryKey: ['credit', 'bankAccounts'],
    queryFn: creditApi.getBankAccounts,
    enabled: !appConfig.useDummyData,
  });
  const bankAccounts = bankAccountsQuery.data ?? [];
  const selectedBankAccount = bankAccounts.find((account) => account.primary) ?? bankAccounts[0];
  const displayBankAccount = toBankAccountViewModel(appConfig.useDummyData ? parentChargeAccount : selectedBankAccount);
  const { apiError, charge, processing, status } = useCreditChargeFlow({
    amount,
    selectedBankAccount,
    valid,
    onCompleted: () => {
      if (appConfig.useDummyData) {
        chargeCredit(amount);
      } else {
        queryClient.invalidateQueries({ queryKey: ['credit', 'parentSummary'] });
      }
    },
  });
  const statusCopy = processingLabel(status);

  return (
    <ScreenFrame eyebrow="크레딧 충전" title="보상 지갑 채우기" description="부모 계좌에서 보상 크레딧을 충전합니다.">
      <BalanceCard label="현재 보상 크레딧" amount={parentCreditBalance} description="충전 후 미션 승인에 사용할 수 있습니다." />
      <Card>
        <Label>충전 계좌</Label>
        {appConfig.useDummyData ? (
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
          <EmptyState body="연결 계좌를 불러오지 못했습니다." />
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
        error={amountText && !valid ? '10,000원부터 1,000,000원까지 충전할 수 있습니다.' : undefined}
      />
      <AmountQuickSelect amounts={[10000, 30000, 50000]} onSelect={(value) => setAmountText(String(value))} />
      <InfoBox
        tone={status === 'idle' ? 'green' : statusCopy.tone}
        title={status === 'idle' ? '예상 잔액' : statusCopy.title}
        body={
          status === 'idle'
            ? `${formatWon(parentCreditBalance + (valid ? amount : 0))}`
            : `${statusCopy.body} · 현재 잔액 ${formatWon(parentCreditBalance)}`
        }
      />
      {status === 'completed' ? <Toast message="충전 완료 · 보상 크레딧이 증가했습니다." /> : null}
      {status === 'failed' ? <Toast tone="danger" message="충전에 실패했습니다. 다시 시도해 주세요." /> : null}
      <PrimaryButton
        title={processing ? '처리 중' : '충전하기'}
        onPress={() => charge()}
        disabled={!valid || processing}
        loading={processing}
      />
      {appConfig.useDummyData ? (
        <ProcessingTestActions disabled={processing || !valid} onSelect={(nextStatus) => charge(nextStatus)} />
      ) : null}
      {status === 'completed' ? <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} /> : null}
    </ScreenFrame>
  );
}
