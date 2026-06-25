import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { creditApi, CreditBankAccount } from '../api/creditApi';
import { requestTossWidgetPayment } from '../api/tossWidget';
import { appConfig } from '../config/appConfig';
import { ProcessingStatus } from '../types';
import { getErrorMessage } from '../utils/apiError';
import { useProcessingPolling } from './useProcessingPolling';

export function useCreditChargeFlow({
  amount,
  selectedBankAccount,
  method = 'bank',
  valid,
  onCompleted,
}: {
  amount: number;
  selectedBankAccount?: CreditBankAccount;
  method?: 'bank' | 'toss';
  valid: boolean;
  onCompleted: () => void;
}) {
  const [status, setStatus] = useState<ProcessingStatus>('idle');
  const [apiError, setApiError] = useState('');
  const [userMessage, setUserMessage] = useState('');
  const processingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const { pollProcessing, polling } = useProcessingPolling();
  const requestChargeMutation = useMutation({
    mutationFn: () => {
      if (method === 'toss') {
        return creditApi.requestTossCharge({ amount }).then((started) => {
          if (appConfig.tossClientKey) {
            return requestTossWidgetPayment({
              clientKey: appConfig.tossClientKey,
              callbackBaseUrl: appConfig.apiBaseUrl,
              amount: started.amount,
              orderId: started.orderId,
              orderName: 'PayFlow 지원금 충전',
              customerName: started.customerKey,
            }).then(() => ({
              chargeId: started.chargeId,
              amount: started.amount,
              status: 'processing' as ProcessingStatus,
            }));
          }
          const paymentKey = `mock-${started.orderId}`;
          return creditApi.confirmTossCharge({
            paymentKey,
            orderId: started.orderId,
            amount: started.amount,
          });
        });
      }

      if (!selectedBankAccount) {
        throw new Error('충전에 사용할 연결 계좌가 없습니다.');
      }

      return creditApi.requestCharge({
        amount,
        bankAccountId: selectedBankAccount.bankAccountId,
      });
    },
    onSuccess: (requested) => {
      if (requested.status !== 'processing') {
        setStatus(requested.status);
        if (requested.status === 'completed') {
          onCompleted();
        }
        return;
      }

      pollProcessing({
        poll: () => method === 'toss' ? creditApi.getTossCharge(requested.chargeId) : creditApi.getCharge(requested.chargeId),
        onResult: (result) => {
          setStatus(result.status);
          if (result.status === 'completed') {
            onCompleted();
          }
        },
        onError: (error) => {
          setStatus('unknown');
          setApiError(getErrorMessage(error, '충전 결과 조회에 실패했습니다.'));
        },
        onTimeout: () => {
          setStatus('processing');
          setUserMessage('충전 처리가 계속 진행 중입니다.\n잠시 후 다시 확인해 주세요.');
        },
      });
    },
    onError: (error) => {
      setStatus('failed');
      setApiError(getErrorMessage(error, '충전 요청에 실패했습니다.'));
    },
  });

  useEffect(() => {
    return () => {
      if (processingTimer.current) {
        clearTimeout(processingTimer.current);
      }
    };
  }, []);

  const charge = (nextStatus: ProcessingStatus = 'completed') => {
    if (!valid) {
      return;
    }

    setStatus('processing');
    setApiError('');
    setUserMessage('');

    if (appConfig.useDummyData) {
      processingTimer.current = setTimeout(() => {
        if (nextStatus === 'completed') {
          onCompleted();
        }
        setStatus(nextStatus);
      }, 900);
      return;
    }

    requestChargeMutation.mutate();
  };

  return {
    apiError,
    charge,
    clearUserMessage: () => setUserMessage(''),
    processing: status === 'processing' || polling || requestChargeMutation.isPending,
    status,
    userMessage,
  };
}
