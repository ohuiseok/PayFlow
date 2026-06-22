import { useEffect, useState } from 'react';
import { Linking, Platform } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import { StackActions } from '@react-navigation/native';

import { creditApi } from '../../api/creditApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { AlertModal, PrimaryButton, ScreenFrame } from '../../components/common';

export function BankAccountRegisterScreen() {
  const [apiError, setApiError] = useState('');
  const [userMessage, setUserMessage] = useState('');
  const [openBankingLoading, setOpenBankingLoading] = useState(false);
  const queryClient = useQueryClient();
  const navigation = useNavigation();

  useEffect(() => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') {
      return;
    }
    const params = new URLSearchParams(window.location.search);
    const openBankingStatus = params.get('openbankingStatus');
    if (!openBankingStatus) {
      return;
    }
    window.history.replaceState({}, document.title, window.location.pathname);
    if (openBankingStatus === 'completed') {
      queryClient.invalidateQueries({ queryKey: ['credit', 'bankAccounts'] });
      navigation.dispatch(StackActions.replace('ChildHome'));
    } else {
      setUserMessage('계좌 연결에 실패했습니다.\n\r다시 시도해주세요.');
    }
  }, [queryClient, navigation]);

  const connectOpenBanking = async () => {
    try {
      setApiError('');
      setOpenBankingLoading(true);
      const response = await creditApi.getOpenBankingAuthorizeUrl();
      await Linking.openURL(response.authorizeUrl);
    } catch (error) {
      console.error(error instanceof Error ? error.message : 'Open Banking 계좌 연결을 시작하지 못했습니다.', error);
      setApiError(error instanceof Error ? error.message : 'Open Banking 계좌 연결을 시작하지 못했습니다.');
    } finally {
      setOpenBankingLoading(false);
    }
  };

  return (
    <ScreenFrame eyebrow="계좌 연결" title="충전 계좌 연결" description="Open Banking 인증으로 계좌를 연결합니다.">
      <PrimaryButton
        title={openBankingLoading ? '연결 준비 중' : 'Open Banking으로 계좌 연결'}
        onPress={connectOpenBanking}
        disabled={openBankingLoading}
        loading={openBankingLoading}
        testID="open-banking-connect-button"
      />
      <ApiErrorBox error={apiError} fallback="계좌 연결에 실패했습니다." />
      <AlertModal
        visible={Boolean(userMessage)}
        title="알림"
        body={userMessage}
        onConfirm={() => setUserMessage('')}
      />
    </ScreenFrame>
  );
}
