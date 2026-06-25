import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { creditApi, CreditBankAccount } from '../api/creditApi';
import { appConfig } from '../config/appConfig';
import { ProcessingStatus } from '../types';
import { getErrorMessage } from '../utils/apiError';
import { useProcessingPolling } from './useProcessingPolling';

export function useWithdrawalFlow({
  amount,
  selectedBankAccount,
  valid,
  walletId,
  onCompleted,
}: {
  amount: number;
  selectedBankAccount?: CreditBankAccount;
  valid: boolean;
  walletId?: string;
  onCompleted: () => boolean | void;
}) {
  const [status, setStatus] = useState<ProcessingStatus>('idle');
  const [apiError, setApiError] = useState('');
  const [message, setMessage] = useState('');
  const [userMessage, setUserMessage] = useState('');
  const processingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onCompletedRef = useRef(onCompleted);
  useEffect(() => { onCompletedRef.current = onCompleted; });
  const { pollProcessing, polling } = useProcessingPolling();
  const requestWithdrawalMutation = useMutation({
    mutationFn: () => {
      if (!walletId || !selectedBankAccount) {
        throw new Error('출금할 지갑 또는 계좌 정보를 찾을 수 없습니다.');
      }

      return creditApi.requestWithdrawal({
        walletId,
        bankAccountId: selectedBankAccount.bankAccountId,
        amount,
      });
    },
    onSuccess: (requested) => {
      if (requested.status !== 'processing') {
        setStatus(requested.status);
        if (requested.status === 'completed') {
          setMessage('출금 완료 · 잔액이 차감되었습니다.');
          onCompletedRef.current();
        }
        return;
      }

      pollProcessing({
        poll: () => creditApi.getWithdrawal(requested.withdrawalId),
        onResult: (result) => {
          setStatus(result.status);
          if (result.status === 'completed') {
            setMessage('출금 완료 · 잔액이 차감되었습니다.');
            onCompletedRef.current();
          }
        },
        onError: (error) => {
          setStatus('unknown');
          setApiError(getErrorMessage(error, '출금 결과 조회에 실패했습니다.'));
        },
        onTimeout: () => {
          setStatus('processing');
          setUserMessage('출금 처리가 계속 진행 중입니다.\n잠시 후 다시 확인해 주세요.');
        },
      });
    },
    onError: (error) => {
      setStatus('failed');
      setApiError(getErrorMessage(error, '출금 요청에 실패했습니다.'));
    },
  });

  useEffect(() => {
    return () => {
      if (processingTimer.current) {
        clearTimeout(processingTimer.current);
      }
    };
  }, []);

  const withdraw = (nextStatus: ProcessingStatus = 'completed') => {
    if (!valid) {
      return;
    }

    setMessage('');
    setStatus('processing');
    setApiError('');
    setUserMessage('');

    if (appConfig.useDummyData) {
      processingTimer.current = setTimeout(() => {
        if (nextStatus !== 'completed') {
          setStatus(nextStatus);
          return;
        }

        const ok = onCompleted();
        setStatus(ok === false ? 'failed' : 'completed');
        setMessage(ok === false ? '출금 가능 금액을 초과했습니다.' : '출금 완료 · 잔액이 차감되었습니다.');
      }, 900);
      return;
    }

    requestWithdrawalMutation.mutate();
  };

  return {
    apiError,
    clearUserMessage: () => setUserMessage(''),
    message,
    processing: status === 'processing' || polling || requestWithdrawalMutation.isPending,
    status,
    userMessage,
    withdraw,
  };
}
