import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { AlertModal, Body, Card, Heading, PrimaryButton, ScreenFrame } from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildInviteCode'>;

export function ChildInviteCodeScreen({ navigation }: Props) {
  const { completeFamilyLink, currentUserId } = useAppState();
  const [apiError, setApiError] = useState('');
  const [userMessage, setUserMessage] = useState('');
  const [loading, setLoading] = useState(false);

  const checkLink = useCallback(async () => {
    if (appConfig.useDummyData) {
      completeFamilyLink();
      navigation.replace('ChildHome');
      return;
    }

    setLoading(true);
    setApiError('');

    try {
      const family = await familyApi.getMyParents();
      if (!family.linked) {
        setUserMessage('아직 기관 연결이 완료되지 않았습니다.\n기관 담당자에게 내 사용자 번호를 알려주세요.');
        return;
      }

      completeFamilyLink();
      navigation.replace('ChildHome');
    } catch (error) {
      setApiError(getErrorMessage(error, '기관 연결 확인에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  }, [completeFamilyLink, navigation]);

  useEffect(() => {
    checkLink();
  }, [checkLink]);

  return (
    <ScreenFrame
      eyebrow="기관 연결"
      title="내 사용자 번호 공유"
      description="기관 담당자가 이 사용자 번호를 입력하면 정책 참여자로 연결됩니다."
    >
      <Card tone="blue">
        <Heading>내 사용자 번호</Heading>
        <Body>{currentUserId}</Body>
      </Card>
      <ApiErrorBox error={apiError} fallback="기관 연결 확인에 실패했습니다." />
      <AlertModal
        visible={Boolean(userMessage)}
        title="알림"
        body={userMessage}
        onConfirm={() => setUserMessage('')}
      />
      <PrimaryButton
        title={loading ? '확인 중' : '연결 상태 확인'}
        onPress={checkLink}
        disabled={loading}
        loading={loading}
      />
    </ScreenFrame>
  );
}
