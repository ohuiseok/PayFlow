import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { appConfig } from '../../config/appConfig';
import { missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, InfoBox, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { MissionCard } from '../../components/mission/MissionCard';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';
import { hasMinLength } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'MissionSubmit'>;

export function MissionSubmitScreen({ navigation, route }: Props) {
  const { missions, submitMission } = useAppState();
  const queryClient = useQueryClient();
  const mission = missions.find((item) => item.id === route.params?.missionId) ?? missions.find((item) => item.status === 'todo') ?? missions[0];
  const [memo, setMemo] = useState('완료 사진을 첨부했어요.');
  const [loading, setLoading] = useState(false);
  const [apiError, setApiError] = useState('');
  const canSubmit = mission.status === 'todo';

  const submit = async () => {
    if (!hasMinLength(memo, 1)) {
      return;
    }

    setLoading(true);
    setApiError('');

    try {
      if (appConfig.useDummyData) {
        submitMission(mission.id, memo);
      } else {
        await missionApi.submitMission({ missionId: mission.id, memo });
      }
      queryClient.invalidateQueries({ queryKey: ['missions'] });
      navigation.navigate('ChildHome');
    } catch (error) {
      setApiError(getErrorMessage(error, '미션 제출에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame eyebrow="완료 제출" title="미션 완료 알리기" description="완료한 내용을 부모에게 제출합니다.">
      <MissionCard mission={mission} />
      {canSubmit ? (
        <>
          <InfoBox tone="blue" title="사진 첨부" body="MVP에서는 사진 URL placeholder로 제출합니다." />
          <ApiErrorBox error={apiError} fallback="미션 제출에 실패했습니다." />
          <FormField label="제출 메모" placeholder="완료 내용을 적어주세요." value={memo} onChangeText={setMemo} disabled={loading} />
          <PrimaryButton
            title={loading ? '제출 중' : '제출하기'}
            onPress={submit}
            disabled={!hasMinLength(memo, 1) || loading}
            loading={loading}
          />
        </>
      ) : (
        <>
          <InfoBox tone="yellow" title="제출할 수 없는 상태" body="이미 제출했거나 지급 완료된 미션은 다시 제출할 수 없습니다." />
          <SecondaryButton title="자녀 홈으로" onPress={() => navigation.navigate('ChildHome')} />
        </>
      )}
    </ScreenFrame>
  );
}
