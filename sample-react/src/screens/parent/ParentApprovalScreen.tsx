import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { Body, Card, FormField, Heading, InfoBox, Label, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { MissionCard } from '../../components/mission/MissionCard';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentApproval'>;

export function ParentApprovalScreen({ navigation }: Props) {
  const { approveMission, loginAs, missions, rejectMission } = useAppState();
  const queryClient = useQueryClient();
  const missionsQuery = useQuery({
    queryKey: ['missions', 'parent', 'approval'],
    queryFn: () => missionApi.getMissions({ role: 'parent' }),
    enabled: !appConfig.useDummyData,
  });
  const displayMissions = missionsQuery.data ?? missions;
  const pending = displayMissions.filter((mission) => mission.status === 'submitted');
  const [reason, setReason] = useState('완료 내용을 더 자세히 적어서 다시 제출해 주세요.');
  const [message, setMessage] = useState('');
  const [apiError, setApiError] = useState('');
  const selected = pending[0];
  const invalidateAfterDecision = () => {
    queryClient.invalidateQueries({ queryKey: ['missions'] });
    queryClient.invalidateQueries({ queryKey: ['credit', 'parentSummary'] });
    queryClient.invalidateQueries({ queryKey: ['cashbook'] });
  };
  const approveMutation = useMutation({
    mutationFn: async () => {
      if (!selected) {
        return false;
      }

      if (appConfig.useDummyData) {
        return approveMission(selected.id);
      }

      await missionApi.approveMission(selected.id);
      return true;
    },
    onMutate: () => {
      setApiError('');
      setMessage('');
    },
    onSuccess: (ok) => {
      setMessage(ok ? '미션을 승인했고 보상을 지급했습니다.' : '부모 크레딧이 부족합니다.');
      invalidateAfterDecision();
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, '미션 승인에 실패했습니다.'));
    },
  });
  const rejectMutation = useMutation({
    mutationFn: async () => {
      if (!selected) {
        return;
      }

      if (appConfig.useDummyData) {
        rejectMission(selected.id, reason);
        return;
      }

      await missionApi.rejectMission({ missionId: selected.id, reason });
    },
    onMutate: () => {
      setApiError('');
      setMessage('');
    },
    onSuccess: () => {
      invalidateAfterDecision();
      navigation.navigate('ParentHome');
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, '미션 반려에 실패했습니다.'));
    },
  });
  const loading = approveMutation.isPending || rejectMutation.isPending;

  return (
    <ScreenFrame
      eyebrow="승인 검토"
      title="제출된 미션"
      description="제출된 미션을 승인해 보상을 지급하거나, 사유를 적어 반려합니다."
    >
      {missionsQuery.isLoading ? <LoadingState title="미션 불러오는 중" body="제출된 미션을 확인하고 있습니다." /> : null}
      <ApiErrorBox error={missionsQuery.error} fallback="미션 조회에 실패했습니다." />
      {selected ? (
        <>
          <MissionCard mission={selected} />
          <Card>
            <Label>제출 메모</Label>
            <Heading>{selected.submitMemo || '제출 메모가 없습니다.'}</Heading>
            <Body>승인하면 미션 승인 처리 후 보상 지급이 이어집니다.</Body>
          </Card>
          <FormField label="반려 사유" placeholder="반려 사유" value={reason} onChangeText={setReason} disabled={loading} />
          <ApiErrorBox error={apiError} fallback="미션 처리에 실패했습니다." />
          {message ? <InfoBox tone="yellow" title="처리 결과" body={message} /> : null}
          <View style={styles.twoButtons}>
            <PrimaryButton
              title={loading ? '처리 중' : '승인하고 지급'}
              onPress={() => approveMutation.mutate()}
              disabled={loading}
              loading={approveMutation.isPending}
            />
            <SecondaryButton title="반려" onPress={() => rejectMutation.mutate()} />
          </View>
          {message && appConfig.useDummyData ? (
            <SecondaryButton
              title="자녀로 보기"
              onPress={() => {
                loginAs('child');
                navigation.navigate('ChildHome');
              }}
            />
          ) : null}
        </>
      ) : (
        <>
          <EmptyState title="승인 대기 없음" body="현재 제출된 미션이 없습니다." />
          <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} />
        </>
      )}
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  twoButtons: {
    flexDirection: 'row',
    gap: 12,
  },
});
