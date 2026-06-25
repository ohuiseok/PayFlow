import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, PrimaryButton, ScreenFrame } from '../../components/common';
import { LoadingState } from '../../components/common/ScreenStates';
import { MissionCard } from '../../components/mission/MissionCard';
import { useResubmitMissionMutation } from '../../hooks/useMissionMutations';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { hasMinLength } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'RejectResubmit'>;

export function RejectResubmitScreen({ navigation, route }: Props) {
  const { missions: dummyMissions, resubmitMission } = useAppState();
  const missionsQuery = useQuery({
    queryKey: ['missions', 'child'],
    queryFn: () => missionApi.getMissions({ role: 'child' }),
    enabled: !appConfig.useDummyData,
  });
  const missions = appConfig.useDummyData ? dummyMissions : (missionsQuery.data ?? []);
  const mission = missions.find((item) => item.id === route.params?.missionId)
    ?? missions.find((item) => item.status === 'rejected')
    ?? missions[0];
  const [memo, setMemo] = useState('부족한 증빙을 보완해 다시 제출합니다.');
  const [apiError, setApiError] = useState('');
  const resubmitMutation = useResubmitMissionMutation({
    missionId: mission?.id ?? '',
    memo,
    onDummyResubmit: () => mission && resubmitMission(mission.id, memo),
    onError: setApiError,
    onSuccess: () => navigation.navigate('ChildHome'),
  });
  const loading = resubmitMutation.isPending;

  const resubmit = () => {
    if (!hasMinLength(memo, 1)) return;
    resubmitMutation.mutate();
  };

  if (!mission) {
    return (
      <ScreenFrame eyebrow="반려 재제출" title="다시 제출하기" description="반려 사유를 확인하고 보완 내용을 보냅니다.">
        <LoadingState body="정책 미션 정보를 불러오는 중입니다." />
      </ScreenFrame>
    );
  }

  return (
    <ScreenFrame eyebrow="반려 재제출" title="다시 제출하기" description="반려 사유를 확인하고 보완 내용을 보냅니다.">
      <MissionCard mission={mission} />
      <ApiErrorBox error={apiError} fallback="정책 미션 재제출에 실패했습니다." />
      <FormField label="재제출 메모" placeholder="보완 내용을 적어주세요." value={memo} onChangeText={setMemo} disabled={loading} />
      <PrimaryButton
        title={loading ? '재제출 중' : '재제출'}
        onPress={resubmit}
        disabled={!hasMinLength(memo, 1) || loading}
        loading={loading}
      />
    </ScreenFrame>
  );
}
