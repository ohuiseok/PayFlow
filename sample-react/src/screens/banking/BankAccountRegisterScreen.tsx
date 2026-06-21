import { useState } from 'react';
import { Linking } from 'react-native';

import { creditApi } from '../../api/creditApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';

export function BankAccountRegisterScreen() {
  const [apiError, setApiError] = useState('');
  const [openBankingLoading, setOpenBankingLoading] = useState(false);

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

  return (
    <ScreenFrame eyebrow="계좌 연결" title="충전 계좌 연결" description="Open Banking 인증으로 계좌를 연결합니다.">
      <InfoBox tone="blue" title="Open Banking 계좌 연결" body="인증을 완료하면 연결 계좌를 자동으로 동기화합니다." />
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
