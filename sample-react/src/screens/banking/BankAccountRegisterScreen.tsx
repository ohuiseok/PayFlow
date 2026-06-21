import { useEffect, useState } from 'react';
import { Linking, Platform } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';

import { creditApi } from '../../api/creditApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';

export function BankAccountRegisterScreen() {
  const [apiError, setApiError] = useState('');
  const [openBankingLoading, setOpenBankingLoading] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');
  const queryClient = useQueryClient();

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const openBankingStatus = params.get('openbankingStatus');
    if (!openBankingStatus) {
      return;
    }
    if (openBankingStatus === 'completed') {
      queryClient.invalidateQueries({ queryKey: ['credit', 'bankAccounts'] });
      setSuccessMessage('계좌 연결이 완료되었습니다.');
    } else {
      setApiError('계좌 연결에 실패했습니다. 다시 시도해주세요.');
    }
    window.history.replaceState({}, document.title, window.location.pathname);
  }, [queryClient]);

  const connectOpenBanking = async () => {
    try {
      setApiError('');
      setSuccessMessage('');
      setOpenBankingLoading(true);
      const response = await creditApi.getOpenBankingAuthorizeUrl();
      await Linking.openURL(response.authorizeUrl);
    } catch (error) {
      setApiError(error instanceof Error ? error.message : 'Open Banking 계좌 연결을 시작하지 못했습니다.');
    } finally {
      setOpenBankingLoading(false);
    }
  };

  return (
    <ScreenFrame eyebrow="계좌 연결" title="충전 계좌 연결" description="Open Banking 인증으로 계좌를 연결합니다.">
      {successMessage ? (
        <InfoBox tone="blue" title="연결 완료" body={successMessage} />
      ) : (
        <InfoBox tone="blue" title="Open Banking 계좌 연결" body="인증을 완료하면 연결 계좌를 자동으로 동기화합니다." />
      )}
      <PrimaryButton
        title={openBankingLoading ? '연결 준비 중' : 'Open Banking으로 계좌 연결'}
        onPress={connectOpenBanking}
        disabled={openBankingLoading}
        loading={openBankingLoading}
        testID="open-banking-connect-button"
      />
      <ApiErrorBox error={apiError} fallback="계좌 연결에 실패했습니다." />
    </ScreenFrame>
  );
}
