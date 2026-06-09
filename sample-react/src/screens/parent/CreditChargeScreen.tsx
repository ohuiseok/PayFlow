import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';

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
import { ProcessingTestActions } from '../../components/common/ProcessingTestActions';
import { creditApi } from '../../api/creditApi';
import { appConfig } from '../../config/appConfig';
import { useProcessingPolling } from '../../hooks/useProcessingPolling';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { ProcessingStatus } from '../../types';
import { processingLabel } from '../shared/processingStatus';

type Props = NativeStackScreenProps<RootStackParamList, 'CreditCharge'>;

export function CreditChargeScreen({ navigation }: Props) {
  const { chargeCredit, parentChargeAccount, parentCreditBalance } = useAppState();
  const [amountText, setAmountText] = useState('30000');
  const [status, setStatus] = useState<ProcessingStatus>('idle');
  const [apiError, setApiError] = useState('');
  const queryClient = useQueryClient();
  const processingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { pollProcessing, polling } = useProcessingPolling();
  const amount = parseAmount(amountText);
  const valid = amount >= 10000 && amount <= 1000000;
  const statusCopy = processingLabel(status);
  const bankAccountsQuery = useQuery({
    queryKey: ['credit', 'bankAccounts'],
    queryFn: creditApi.getBankAccounts,
    enabled: !appConfig.useDummyData,
  });
  const bankAccounts = bankAccountsQuery.data ?? [];
  const selectedBankAccount = bankAccounts.find((account) => account.primary) ?? bankAccounts[0];

  useEffect(() => {
    return () => {
      if (processingTimer.current) {
        clearTimeout(processingTimer.current);
      }
    };
  }, []);

  const charge = async (nextStatus: ProcessingStatus = 'completed') => {
    if (!valid) return;
    setStatus('processing');
    setApiError('');

    if (appConfig.useDummyData) {
      processingTimer.current = setTimeout(() => {
        if (nextStatus === 'completed') {
          chargeCredit(amount);
        }
        setStatus(nextStatus);
      }, 900);
      return;
    }

    if (!selectedBankAccount) {
      setStatus('failed');
      setApiError('충전에 사용할 연결 계좌가 없습니다.');
      return;
    }

    try {
      const requested = await creditApi.requestCharge({
        amount,
        bankAccountId: selectedBankAccount.bankAccountId,
      });

      if (requested.status !== 'processing') {
        setStatus(requested.status);
        return;
      }

      pollProcessing({
        poll: () => creditApi.getCharge(requested.chargeId),
        onResult: (result) => {
          setStatus(result.status);
          if (result.status === 'completed') {
            queryClient.invalidateQueries({ queryKey: ['credit', 'parentSummary'] });
          }
        },
        onError: (error) => {
          setStatus('unknown');
          setApiError(error instanceof Error ? error.message : '충전 결과 조회에 실패했습니다.');
        },
        onTimeout: () => {
          setStatus('processing');
          setApiError('충전 처리가 계속 진행 중입니다. 잠시 후 다시 확인해 주세요.');
        },
      });
    } catch (error) {
      setStatus('failed');
      setApiError(error instanceof Error ? error.message : '충전 요청에 실패했습니다.');
    }
  };

  return (
    <ScreenFrame eyebrow="크레딧 충전" title="보상 지갑 채우기" description="부모 계좌에서 보상 크레딧을 충전합니다.">
      <BalanceCard label="현재 보상 크레딧" amount={parentCreditBalance} description="충전 후 미션 승인에 사용할 수 있습니다." />
      <Card>
        <Label>충전 계좌</Label>
        {appConfig.useDummyData ? (
          <>
            <Body>
              {parentChargeAccount.bankName} {parentChargeAccount.accountNumber}
            </Body>
            <Body>예금주 {parentChargeAccount.holderName}</Body>
          </>
        ) : bankAccountsQuery.isLoading ? (
          <Body>연결 계좌를 불러오고 있습니다.</Body>
        ) : selectedBankAccount ? (
          <>
            <Body>
              {selectedBankAccount.bankName} {selectedBankAccount.maskedAccountNumber}
            </Body>
            <Body>예금주 {selectedBankAccount.accountHolderName}</Body>
          </>
        ) : (
          <Body>연결 계좌를 불러오지 못했습니다.</Body>
        )}
      </Card>
      {bankAccountsQuery.error ? (
        <InfoBox tone="yellow" title="API 오류" body={bankAccountsQuery.error instanceof Error ? bankAccountsQuery.error.message : '연결 계좌 조회에 실패했습니다.'} />
      ) : null}
      {apiError ? <InfoBox tone="yellow" title="API 오류" body={apiError} /> : null}
      <FormField
        label="충전 금액"
        placeholder="10,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        disabled={status === 'processing' || polling}
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
      <PrimaryButton title={status === 'processing' ? '처리 중' : '충전하기'} onPress={() => charge()} disabled={!valid || status === 'processing' || polling} loading={status === 'processing' || polling} />
      {appConfig.useDummyData ? (
        <ProcessingTestActions disabled={status === 'processing' || !valid} onSelect={(nextStatus) => charge(nextStatus)} />
      ) : null}
      {status === 'completed' ? <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} /> : null}
    </ScreenFrame>
  );
}
