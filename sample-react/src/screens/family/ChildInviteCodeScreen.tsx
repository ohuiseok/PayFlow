import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useCallback, useEffect, useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { Body, Card, Heading, PrimaryButton, ScreenFrame } from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildInviteCode'>;

export function ChildInviteCodeScreen({ navigation }: Props) {
  const { completeFamilyLink, currentUserId } = useAppState();
  const [apiError, setApiError] = useState('');
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
        setApiError('아직 보호자 연결이 완료되지 않았습니다. 먼저 보호자에게 내 사용자 번호를 알려주세요.');
        return;
      }

      completeFamilyLink();
      navigation.replace('ChildHome');
    } catch (error) {
      setApiError(getErrorMessage(error, '가족 연결 확인에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  }, [completeFamilyLink, navigation]);

  useEffect(() => {
    checkLink();
  }, [checkLink]);

  return (
    <ScreenFrame
      eyebrow="가족 연결"
      title="내 사용자 번호 공유"
      description="보호자 계정에서 이 사용자 번호를 입력해 달라고 요청하세요."
    >
      <Card tone="blue">
        <Heading>내 사용자 번호</Heading>
        <Body>{currentUserId}</Body>
      </Card>      <ApiErrorBox error={apiError} fallback="가족 연결 확인에 실패했습니다." />
      <PrimaryButton
        title={loading ? '확인 중' : '연결 상태 확인'}
        onPress={checkLink}
        disabled={loading}
        loading={loading}
      />
    </ScreenFrame>
  );
}
