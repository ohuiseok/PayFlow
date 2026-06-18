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
  const [reason, setReason] = useState('Please add a clearer note and submit again.');
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
      setMessage(ok ? 'Mission approved and reward paid.' : 'Not enough parent credit.');
      invalidateAfterDecision();
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, 'Mission approval failed.'));
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
      setApiError(getErrorMessage(error, 'Mission rejection failed.'));
    },
  });
  const loading = approveMutation.isPending || rejectMutation.isPending;

  return (
    <ScreenFrame
      eyebrow="Review"
      title="Submitted missions"
      description="Approve a submitted mission to pay the reward, or reject it with a reason."
    >
      {missionsQuery.isLoading ? <LoadingState title="Loading missions" body="Checking submitted missions." /> : null}
      <ApiErrorBox error={missionsQuery.error} fallback="Mission lookup failed." />
      {selected ? (
        <>
          <MissionCard mission={selected} />
          <Card>
            <Label>Submission note</Label>
            <Heading>{selected.submitMemo || 'No submission note.'}</Heading>
            <Body>Approval calls the mission approval API and then the reward payment API.</Body>
          </Card>
          <FormField label="Reject reason" placeholder="Reject reason" value={reason} onChangeText={setReason} disabled={loading} />
          <ApiErrorBox error={apiError} fallback="Mission decision failed." />
          {message ? <InfoBox tone="yellow" title="Result" body={message} /> : null}
          <View style={styles.twoButtons}>
            <PrimaryButton
              title={loading ? 'Processing' : 'Approve and pay'}
              onPress={() => approveMutation.mutate()}
              disabled={loading}
              loading={approveMutation.isPending}
            />
            <SecondaryButton title="Reject" onPress={() => rejectMutation.mutate()} />
          </View>
          {message && appConfig.useDummyData ? (
            <SecondaryButton
              title="Switch to child"
              onPress={() => {
                loginAs('child');
                navigation.navigate('ChildHome');
              }}
            />
          ) : null}
        </>
      ) : (
        <>
          <EmptyState title="No pending approvals" body="There are no submitted missions right now." />
          <SecondaryButton title="Back to parent home" onPress={() => navigation.navigate('ParentHome')} />
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
