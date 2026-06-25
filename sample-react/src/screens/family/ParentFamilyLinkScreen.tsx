import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, PrimaryButton, ScreenFrame } from '../../components/common';
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
      setApiError(getErrorMessage(error, '청년 참여자 연결에 실패했습니다.'));
    },
  });

  return (
    <ScreenFrame
      eyebrow="참여자 연결"
      title="청년 참여자 연결하기"
      description="청년 사용자 번호를 입력해 기관 담당자와 참여자를 연결합니다."
    >
      <FormField
        label="청년 사용자 번호"
        placeholder="2"
        value={childUserId}
        onChangeText={setChildUserId}
        keyboardType="number-pad"
        disabled={linkMutation.isPending}
        error={valid ? undefined : '올바른 청년 사용자 번호를 입력하세요.'}
      />
      <ApiErrorBox error={apiError} fallback="청년 참여자 연결에 실패했습니다." />
      <PrimaryButton
        title={linkMutation.isPending ? '연결 중' : '참여자 연결'}
        onPress={() => linkMutation.mutate()}
        disabled={!valid || linkMutation.isPending}
        loading={linkMutation.isPending}
      />
    </ScreenFrame>
  );
}
