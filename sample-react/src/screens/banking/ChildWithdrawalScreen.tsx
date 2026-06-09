import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import {
  AmountQuickSelect,
  BalanceCard,
  Body,
  Card,
  ConfirmModal,
  FormField,
  formatAmountInput,
  formatWon,
  Heading,
  InfoBox,
  Label,
  parseAmount,
  PrimaryButton,
  ScreenFrame,
  SecondaryButton,
  Toast,
} from '../../components/common';
import { ProcessingTestActions } from '../../components/common/ProcessingTestActions';
import { cashbookApi } from '../../api/cashbookApi';
import { creditApi } from '../../api/creditApi';
import { defaultChildUserId } from '../../api/missionApi';
import { appConfig } from '../../config/appConfig';
import { useProcessingPolling } from '../../hooks/useProcessingPolling';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { ProcessingStatus } from '../../types';
import { processingLabel } from '../shared/processingStatus';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildWithdrawal'>;

export function ChildWithdrawalScreen({ navigation }: Props) {
  const { childCashBalance, currentUserId, linkedBankAccount, withdrawCash } = useAppState();
  const [amountText, setAmountText] = useState('5000');
  const [confirming, setConfirming] = useState(false);
  const [message, setMessage] = useState('');
  const [status, setStatus] = useState<ProcessingStatus>('idle');
  const [apiError, setApiError] = useState('');
  const processingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const queryClient = useQueryClient();
  const { pollProcessing, polling } = useProcessingPolling();
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
  const valid = amount >= 1000 && amount <= displayBalance;
  const statusCopy = processingLabel(status);

  useEffect(() => {
    return () => {
      if (processingTimer.current) {
        clearTimeout(processingTimer.current);
      }
    };
  }, []);

  const withdraw = async (nextStatus: ProcessingStatus = 'completed') => {
    setConfirming(false);
    setMessage('');
    setStatus('processing');
    setApiError('');

    if (appConfig.useDummyData) {
      processingTimer.current = setTimeout(() => {
        if (nextStatus !== 'completed') {
          setStatus(nextStatus);
          return;
        }

        const ok = withdrawCash(amount);
        setStatus(ok ? 'completed' : 'failed');
        setMessage(ok ? '출금 완료 · 잔액이 차감되었습니다.' : '출금 가능 금액을 초과했습니다.');
      }, 900);
      return;
    }

    if (!summaryQuery.data?.walletId || !selectedBankAccount) {
      setStatus('failed');
      setApiError('출금할 지갑 또는 계좌 정보를 찾을 수 없습니다.');
      return;
    }

    try {
      const requested = await creditApi.requestWithdrawal({
        walletId: summaryQuery.data.walletId,
        bankAccountId: selectedBankAccount.bankAccountId,
        amount,
      });

      if (requested.status !== 'processing') {
        setStatus(requested.status);
        setMessage(requested.status === 'completed' ? '출금 완료 · 잔액이 차감되었습니다.' : '');
        if (requested.status === 'completed') {
          queryClient.invalidateQueries({ queryKey: ['cashbook'] });
        }
        return;
      }

      pollProcessing({
        poll: () => creditApi.getWithdrawal(requested.withdrawalId),
        onResult: (result) => {
          setStatus(result.status);
          if (result.status === 'completed') {
            setMessage('출금 완료 · 잔액이 차감되었습니다.');
            queryClient.invalidateQueries({ queryKey: ['cashbook'] });
          }
        },
        onError: (error) => {
          setStatus('unknown');
          setApiError(error instanceof Error ? error.message : '출금 결과 조회에 실패했습니다.');
        },
        onTimeout: () => {
          setStatus('processing');
          setApiError('출금 처리가 계속 진행 중입니다. 잠시 후 다시 확인해 주세요.');
        },
      });
    } catch (error) {
      setStatus('failed');
      setApiError(error instanceof Error ? error.message : '출금 요청에 실패했습니다.');
    }
  };

  return (
    <ScreenFrame eyebrow="계좌 출금" title="캐시북에서 출금" description="모은 보상을 등록 계좌로 보냅니다.">
      <BalanceCard label="출금 가능 잔액" amount={displayBalance} description="요청 후 처리 중 상태를 거쳐 완료됩니다." />
      <Card>
        <Label>받을 계좌</Label>
        <Heading>
          {appConfig.useDummyData
            ? linkedBankAccount
              ? `${linkedBankAccount.bankName} ${linkedBankAccount.accountNumber}`
              : '등록된 계좌 없음'
            : selectedBankAccount
              ? `${selectedBankAccount.bankName} ${selectedBankAccount.maskedAccountNumber}`
              : '등록된 계좌 없음'}
        </Heading>
        <Body>
          {appConfig.useDummyData
            ? linkedBankAccount?.holderName ?? '계좌를 먼저 등록하세요.'
            : selectedBankAccount?.accountHolderName ?? '계좌를 먼저 등록하세요.'}
        </Body>
      </Card>
      {summaryQuery.error ? (
        <InfoBox tone="yellow" title="API 오류" body={summaryQuery.error instanceof Error ? summaryQuery.error.message : '자녀 지갑 정보를 불러오지 못했습니다.'} />
      ) : null}
      {bankAccountsQuery.error ? (
        <InfoBox tone="yellow" title="API 오류" body={bankAccountsQuery.error instanceof Error ? bankAccountsQuery.error.message : '연결 계좌를 불러오지 못했습니다.'} />
      ) : null}
      {apiError ? <InfoBox tone="yellow" title="API 오류" body={apiError} /> : null}
      <FormField
        label="출금 금액"
        placeholder="1,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        disabled={status === 'processing' || polling}
        error={amountText && !valid ? '잔액 안에서 1,000원 이상 출금할 수 있습니다.' : undefined}
      />
      <AmountQuickSelect amounts={[5000, 10000, 30000]} onSelect={(value) => setAmountText(String(value))} />
      <InfoBox tone="yellow" title="주의" body="출금 요청 중에는 같은 요청을 다시 보낼 수 없습니다." />
      {status !== 'idle' ? (
        <InfoBox tone={statusCopy.tone} title={statusCopy.title} body={statusCopy.body} />
      ) : null}
      {status === 'completed' ? <Toast message="출금 완료 · 잔액이 차감되었습니다." /> : null}
      {status === 'failed' ? <Toast tone="danger" message="출금에 실패했습니다. 금액과 계좌를 확인해 주세요." /> : null}
      <PrimaryButton
        title={status === 'processing' ? '처리 중' : (appConfig.useDummyData ? linkedBankAccount : selectedBankAccount) ? '출금 요청' : '계좌 등록하기'}
        onPress={() => ((appConfig.useDummyData ? linkedBankAccount : selectedBankAccount) ? setConfirming(true) : navigation.navigate('BankAccountRegister'))}
        disabled={(appConfig.useDummyData ? linkedBankAccount : selectedBankAccount) ? !valid || status === 'processing' || polling : false}
        loading={status === 'processing' || polling}
      />
      <ConfirmModal
        visible={confirming}
        title={`${formatWon(amount)} 출금할까요?`}
        body="확인하면 자녀 지갑 잔액에서 바로 차감되고 캐시북에 출금 기록이 남습니다."
        confirmTitle="출금 진행"
        onConfirm={() => withdraw()}
        onCancel={() => setConfirming(false)}
      />
      {appConfig.useDummyData && linkedBankAccount ? (
        <ProcessingTestActions disabled={status === 'processing' || !valid} onSelect={(nextStatus) => withdraw(nextStatus)} />
      ) : null}
    </ScreenFrame>
  );
}
