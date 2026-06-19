import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentFamilyLink'>;

export function ParentFamilyLinkScreen({ navigation }: Props) {
  const { completeFamilyLink } = useAppState();
  const queryClient = useQueryClient();
  const [childUserId, setChildUserId] = useState('2');
  const [apiError, setApiError] = useState('');
  const valid = Number(childUserId) > 0;
  const linkMutation = useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        completeFamilyLink();
        return;
      }

      await familyApi.requestLink(childUserId);
    },
    onMutate: () => {
      setApiError('');
    },
    onSuccess: () => {
      completeFamilyLink();
      queryClient.invalidateQueries({ queryKey: ['family'] });
      navigation.replace('ParentHome');
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, '자녀 연결에 실패했습니다.'));
    },
  });

  return (
    <ScreenFrame
      eyebrow="가족 연결"
      title="자녀 연결하기"
      description="자녀 사용자 번호를 입력해 부모와 자녀를 연결합니다."
    >
      <InfoBox
        tone="blue"
        title="현재 서버 흐름"
        body="보상 서비스는 부모가 자녀 사용자 번호를 입력하면 바로 가족 연결을 생성합니다."
      />
      <FormField
        label="자녀 사용자 번호"
        placeholder="2"
        value={childUserId}
        onChangeText={setChildUserId}
        keyboardType="number-pad"
        disabled={linkMutation.isPending}
        error={valid ? undefined : '올바른 자녀 사용자 번호를 입력하세요.'}
      />
      <ApiErrorBox error={apiError} fallback="자녀 연결에 실패했습니다." />
      <PrimaryButton
        title={linkMutation.isPending ? '연결 중' : '자녀 연결'}
        onPress={() => linkMutation.mutate()}
        disabled={!valid || linkMutation.isPending}
        loading={linkMutation.isPending}
      />
    </ScreenFrame>
  );
}
