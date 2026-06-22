import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { LoadingState } from '../../components/common/ScreenStates';
import { MissionCard } from '../../components/mission/MissionCard';
import { appConfig } from '../../config/appConfig';
import { useSubmitMissionMutation } from '../../hooks/useMissionMutations';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { hasMinLength } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'MissionSubmit'>;

export function MissionSubmitScreen({ navigation, route }: Props) {
  const { missions: dummyMissions, submitMission } = useAppState();
  const missionsQuery = useQuery({
    queryKey: ['missions', 'child'],
    queryFn: () => missionApi.getMissions({ role: 'child' }),
    enabled: !appConfig.useDummyData,
  });
  const missions = appConfig.useDummyData ? dummyMissions : (missionsQuery.data ?? []);
  const mission = missions.find((item) => item.id === route.params?.missionId) ?? missions.find((item) => item.status === 'todo') ?? missions[0];
  const [memo, setMemo] = useState('완료 사진을 첨부했어요.');
  const [apiError, setApiError] = useState('');
  const submitMutation = useSubmitMissionMutation({
    missionId: mission?.id ?? '',
    memo,
    onDummySubmit: () => mission && submitMission(mission.id, memo),
    onError: setApiError,
    onSuccess: () => navigation.navigate('ChildHome'),
  });
  const loading = submitMutation.isPending;

  if (!mission) {
    return (
      <ScreenFrame eyebrow="완료 제출" title="미션 완료 알리기" description="완료한 내용을 부모에게 제출합니다.">
        <LoadingState body="미션 정보를 불러오는 중입니다." />
      </ScreenFrame>
    );
  }

  const canSubmit = mission.status === 'todo';

  const submit = () => {
    if (!hasMinLength(memo, 1)) {
      return;
    }

    submitMutation.mutate();
  };

  return (
    <ScreenFrame eyebrow="완료 제출" title="미션 완료 알리기" description="완료한 내용을 부모에게 제출합니다.">
      <MissionCard mission={mission} />
      {canSubmit ? (
        <>          <ApiErrorBox error={apiError} fallback="미션 제출에 실패했습니다." />
          <FormField label="제출 메모" placeholder="완료 내용을 적어주세요." value={memo} onChangeText={setMemo} disabled={loading} />
          <PrimaryButton
            title={loading ? '제출 중' : '제출하기'}
            onPress={submit}
            disabled={!hasMinLength(memo, 1) || loading}
            loading={loading}
          />
        </>
      ) : (
        <>          <SecondaryButton title="자녀 홈으로" onPress={() => navigation.navigate('ChildHome')} />
        </>
      )}
    </ScreenFrame>
  );
}
