import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';

import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { MissionCard } from '../../components/mission/MissionCard';
import { useResubmitMissionMutation } from '../../hooks/useMissionMutations';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { hasMinLength } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'RejectResubmit'>;

export function RejectResubmitScreen({ navigation, route }: Props) {
  const { missions, resubmitMission } = useAppState();
  const mission = missions.find((item) => item.id === route.params?.missionId) ?? missions.find((item) => item.status === 'rejected') ?? missions[0];
  const [memo, setMemo] = useState('빠진 부분까지 다시 완료했어요.');
  const [apiError, setApiError] = useState('');
  const resubmitMutation = useResubmitMissionMutation({
    missionId: mission.id,
    memo,
    onDummyResubmit: () => resubmitMission(mission.id, memo),
    onError: setApiError,
    onSuccess: () => navigation.navigate('ChildHome'),
  });
  const loading = resubmitMutation.isPending;

  const resubmit = () => {
    if (!hasMinLength(memo, 1)) {
      return;
    }

    resubmitMutation.mutate();
  };

  return (
    <ScreenFrame eyebrow="반려 재제출" title="다시 제출하기" description="반려 사유를 확인하고 보완 내용을 보냅니다.">
      <MissionCard mission={mission} />
      <InfoBox tone="yellow" title="반려 사유" body={mission.rejectReason || '보완이 필요합니다.'} />
      <ApiErrorBox error={apiError} fallback="미션 재제출에 실패했습니다." />
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
