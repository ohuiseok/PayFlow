import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { cashbookApi } from '../../api/cashbookApi';
import { creditApi } from '../../api/creditApi';
import { defaultChildUserId } from '../../api/missionApi';
import {
  AlertModal,
  AmountQuickSelect,
  BalanceCard,
  Body,
  Card,
  ConfirmModal,
  FormField,
  formatAmountInput,
  formatWon,
  Heading,
  Label,
  parseAmount,
  PrimaryButton,
  ScreenFrame,
  Toast,
} from '../../components/common';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { ProcessingTestActions } from '../../components/common/ProcessingTestActions';
import { appConfig } from '../../config/appConfig';
import { useWithdrawalFlow } from '../../hooks/useWithdrawalFlow';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { isAmountInRange } from '../../utils/validators';
import { formatBankAccountHolder, formatBankAccountLabel, toBankAccountViewModel } from '../../viewModels/bankAccountViewModel';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildWithdrawal'>;

export function ChildWithdrawalScreen({ navigation }: Props) {
  const { childCashBalance, currentUserId, linkedBankAccount, withdrawCash } = useAppState();
  const [amountText, setAmountText] = useState('5000');
  const [confirming, setConfirming] = useState(false);
  const queryClient = useQueryClient();
  const childUserId = appConfig.useDummyData ? defaultChildUserId : currentUserId || defaultChildUserId;
  const summaryQuery = useQuery({
    queryKey: ['cashbook', 'summary', childUserId],
    queryFn: () => cashbookApi.getChildSummary(childUserId),
    enabled: !appConfig.useDummyData,
  });
  const bankAccountsQuery = useQuery({
    queryKey: ['credit', 'bankAccounts'],
    queryFn: creditApi.getBankAccounts,
    enabled: !appConfig.useDummyData,
  });
  const amount = parseAmount(amountText);
  const displayBalance = summaryQuery.data?.balance ?? childCashBalance;
  const bankAccounts = bankAccountsQuery.data ?? [];
  const selectedBankAccount = bankAccounts.find((account) => account.primary) ?? bankAccounts[0];
  const displayBankAccount = toBankAccountViewModel(appConfig.useDummyData ? linkedBankAccount : selectedBankAccount);
  const valid = isAmountInRange(amount, 1000, displayBalance);
  const { apiError, clearUserMessage, message, processing, status, userMessage, withdraw } = useWithdrawalFlow({
    amount,
    selectedBankAccount,
    valid,
    walletId: summaryQuery.data?.walletId,
    onCompleted: () => {
      if (appConfig.useDummyData) {
        return withdrawCash(amount);
      }

      queryClient.invalidateQueries({ queryKey: ['cashbook'] });
    },
  });
  return (
    <ScreenFrame eyebrow="계좌 출금" title="사용 기록에서 출금" description="모은 보상을 등록 계좌로 보냅니다.">
      <BalanceCard label="출금 가능 잔액" amount={displayBalance} description="요청 후 처리 상태를 거쳐 완료됩니다." />
      <Card>
        <Label>받을 계좌</Label>
        <Heading>{formatBankAccountLabel(displayBankAccount)}</Heading>
        <Body>{formatBankAccountHolder(displayBankAccount, '계좌를 먼저 등록하세요.')}</Body>
      </Card>
      <ApiErrorBox error={summaryQuery.error} fallback="자녀 지갑 정보를 불러오지 못했습니다." />
      <ApiErrorBox error={bankAccountsQuery.error} fallback="연결 계좌를 불러오지 못했습니다." />
      <ApiErrorBox error={apiError} fallback="출금 처리 중 오류가 발생했습니다." />
      <FormField
        label="출금 금액"
        placeholder="1,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        disabled={processing}
        error={amountText && !valid ? '잔액 안에서 1,000원 이상 출금할 수 있습니다.' : undefined}
      />
      <AmountQuickSelect amounts={[5000, 10000, 30000]} onSelect={(value) => setAmountText(String(value))} />
      {status === 'completed' ? <Toast message={message || '출금 완료 · 잔액이 차감되었습니다.'} /> : null}
      {status === 'failed' ? <Toast tone="danger" message="출금에 실패했습니다. 금액과 계좌를 확인해 주세요." /> : null}
      <PrimaryButton
        title={processing ? '처리 중' : displayBankAccount ? '출금 요청' : '계좌 등록하기'}
        onPress={() => (displayBankAccount ? setConfirming(true) : navigation.navigate('BankAccountRegister'))}
        disabled={displayBankAccount ? !valid || processing : false}
        loading={processing}
      />
      <ConfirmModal
        visible={confirming}
        title={`${formatWon(amount)} 출사용 기록요?`}
        body="확인하면 자녀 지갑 잔액에서 바로 차감하고 캐시북에 출금 기록을 남깁니다."
        confirmTitle="출금 진행"
        onConfirm={() => {
          setConfirming(false);
          withdraw();
        }}
        onCancel={() => setConfirming(false)}
      />
      {appConfig.useDummyData && linkedBankAccount ? (
        <ProcessingTestActions disabled={processing || !valid} onSelect={(nextStatus) => withdraw(nextStatus)} />
      ) : null}
      <AlertModal
        visible={Boolean(userMessage)}
        title="알림"
        body={userMessage}
        onConfirm={clearUserMessage}
      />
    </ScreenFrame>
  );
}
