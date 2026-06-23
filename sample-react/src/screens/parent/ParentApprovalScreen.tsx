import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Modal, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

import { familyApi } from '../../api/familyApi';
import { missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { colors, formatWon, PrimaryButton, ScreenFrame, SecondaryButton, StatusBadge } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { ChildOption, ChildSelector } from '../../components/mission/ChildSelector';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { LinkedChild, Mission } from '../../types';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentApproval'>;

export function ParentApprovalScreen({ navigation }: Props) {
  const { approveMission, linkedChildren, loginAs, missions, rejectMission } = useAppState();
  const queryClient = useQueryClient();

  const missionsQuery = useQuery({
    queryKey: ['missions', 'parent', 'approval'],
    queryFn: () => missionApi.getMissions({ role: 'parent' }),
    enabled: !appConfig.useDummyData,
  });

  const familyQuery = useQuery({
    queryKey: ['family', 'mine'],
    queryFn: familyApi.getMyFamilies,
    enabled: !appConfig.useDummyData,
  });

  const childOptions: ChildOption[] = appConfig.useDummyData
    ? linkedChildren.map((c: LinkedChild) => ({
        childUserId: c.childUserId,
        childName: c.childName,
        phoneNumber: c.phoneNumber,
      }))
    : (familyQuery.data?.families ?? [])
        .filter((f) => f.status === 'CONNECTED' && f.childUserId != null)
        .map((f) => ({
          childUserId: f.childUserId!,
          childName: f.childName ?? `자녀 ${f.childUserId}`,
          phoneNumber: f.childPhoneNumber ?? '-',
        }));

  const [selectedChildId, setSelectedChildId] = useState<string | number | null>(
    () => childOptions[0]?.childUserId ?? null,
  );

  const displayMissions = appConfig.useDummyData ? missions : (missionsQuery.data ?? []);
  const childMissions = selectedChildId
    ? displayMissions.filter((m) => String(m.childId) === String(selectedChildId))
    : displayMissions;
  const pending = childMissions.filter((mission) => mission.status === 'submitted');

  const [reason, setReason] = useState('완료 내용을 더 자세히 적어서 다시 제출해 주세요.');
  const [rejectModalVisible, setRejectModalVisible] = useState(false);
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
      if (!selected) return false;
      if (appConfig.useDummyData) return approveMission(selected.id);
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
      if (!selected) return;
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
      setRejectModalVisible(false);
      invalidateAfterDecision();
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
      description="자녀를 선택하면 해당 자녀의 제출된 미션을 승인하거나 반려합니다."
    >
      <ChildSelector
        children={childOptions}
        selectedId={selectedChildId}
        onSelect={(child) => {
          setSelectedChildId(child.childUserId);
          setMessage('');
          setApiError('');
        }}
      />
      <ApiErrorBox error={familyQuery.error} fallback="연결 자녀 조회에 실패했습니다." />
      {missionsQuery.isLoading ? <LoadingState title="미션 불러오는 중" body="제출된 미션을 확인하고 있습니다." /> : null}
      <ApiErrorBox error={missionsQuery.error} fallback="미션 조회에 실패했습니다." />
      {selected ? (
        <>
          <MissionApprovalCard mission={selected} />
          <ApiErrorBox error={apiError} fallback="미션 처리에 실패했습니다." />
          <View style={styles.twoButtons}>
            <PrimaryButton
              title={loading ? '처리 중' : '승인하고 지급'}
              onPress={() => approveMutation.mutate()}
              disabled={loading}
              loading={approveMutation.isPending}
            />
            <SecondaryButton
              title="반려"
              onPress={() => {
                setReason('완료 내용을 더 자세히 적어서 다시 제출해 주세요.');
                setRejectModalVisible(true);
              }}
            />
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

          <Modal
            animationType="fade"
            transparent
            visible={rejectModalVisible}
            onRequestClose={() => setRejectModalVisible(false)}
          >
            <View style={styles.backdrop}>
              <View style={styles.panel}>
                <Text style={styles.panelTitle}>반려 사유 입력</Text>
                <Text style={styles.panelBody}>자녀에게 전달할 반려 사유를 입력해 주세요.</Text>
                <TextInput
                  autoFocus
                  multiline
                  numberOfLines={3}
                  onChangeText={setReason}
                  placeholder="예) 완료 내용을 더 자세히 적어서 다시 제출해 주세요."
                  placeholderTextColor="#8792A0"
                  style={styles.reasonInput}
                  value={reason}
                />
                <View style={styles.panelActions}>
                  <TouchableOpacity
                    activeOpacity={0.8}
                    onPress={() => setRejectModalVisible(false)}
                    style={styles.cancelButton}
                  >
                    <Text style={styles.cancelText}>취소</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    activeOpacity={0.8}
                    disabled={rejectMutation.isPending || !reason.trim()}
                    onPress={() => rejectMutation.mutate()}
                    style={[
                      styles.rejectButton,
                      (!reason.trim() || rejectMutation.isPending) && styles.buttonDisabled,
                    ]}
                  >
                    <Text style={styles.rejectText}>
                      {rejectMutation.isPending ? '처리 중...' : '반려하기'}
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            </View>
          </Modal>
        </>
      ) : (
        <>
          <EmptyState title="승인 대기 없음" body="선택한 자녀의 제출된 미션이 없습니다." />
          <SecondaryButton title="부모 홈으로" onPress={() => navigation.navigate('ParentHome')} />
        </>
      )}
    </ScreenFrame>
  );
}

function MissionApprovalCard({ mission }: { mission: Mission }) {
  return (
    <View style={cardStyles.card}>
      <View style={cardStyles.topRow}>
        <StatusBadge label="승인 대기" tone="yellow" />
        <Text style={cardStyles.reward}>{formatWon(mission.rewardAmount)}</Text>
      </View>
      <Text style={cardStyles.title}>{mission.title}</Text>
      <Text style={cardStyles.meta}>{mission.childName} · {mission.dueDate}</Text>
      {mission.description ? (
        <Text style={cardStyles.description}>{mission.description}</Text>
      ) : null}
      <View style={cardStyles.divider} />
      <Text style={cardStyles.memoLabel}>제출 메모</Text>
      <Text style={cardStyles.memoText}>
        {mission.submitMemo || '제출 메모가 없습니다.'}
      </Text>
      <Text style={cardStyles.hint}>승인하면 미션 보상이 자녀 지갑으로 즉시 지급됩니다.</Text>
    </View>
  );
}

const cardStyles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderColor: colors.line,
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 16,
    padding: 18,
  },
  topRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  reward: {
    color: colors.primary,
    fontSize: 17,
    fontWeight: '900',
  },
  title: {
    color: colors.text,
    fontSize: 20,
    fontWeight: '900',
    lineHeight: 27,
    marginBottom: 6,
  },
  meta: {
    color: colors.muted,
    fontSize: 14,
    marginBottom: 6,
  },
  description: {
    color: colors.muted,
    fontSize: 15,
    lineHeight: 22,
    marginTop: 2,
  },
  divider: {
    backgroundColor: colors.line,
    height: 1,
    marginVertical: 14,
  },
  memoLabel: {
    color: colors.primary,
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 0.5,
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  memoText: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '700',
    lineHeight: 24,
    marginBottom: 12,
  },
  hint: {
    color: colors.muted,
    fontSize: 13,
    lineHeight: 19,
  },
});

const styles = StyleSheet.create({
  twoButtons: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 12,
  },
  backdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(17, 24, 32, 0.42)',
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  panel: {
    backgroundColor: colors.surface,
    borderRadius: 10,
    maxWidth: 380,
    padding: 22,
    width: '100%',
  },
  panelTitle: {
    color: colors.text,
    fontSize: 20,
    fontWeight: '900',
    marginBottom: 6,
  },
  panelBody: {
    color: colors.muted,
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 16,
  },
  reasonInput: {
    backgroundColor: '#f8faff',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    color: colors.text,
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 16,
    minHeight: 90,
    paddingHorizontal: 14,
    paddingVertical: 12,
    textAlignVertical: 'top',
  },
  panelActions: {
    flexDirection: 'row',
    gap: 10,
  },
  cancelButton: {
    alignItems: 'center',
    backgroundColor: '#e9efff',
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    paddingVertical: 14,
  },
  cancelText: {
    color: colors.dark,
    fontSize: 16,
    fontWeight: '900',
  },
  rejectButton: {
    alignItems: 'center',
    backgroundColor: colors.danger,
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    paddingVertical: 14,
  },
  rejectText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '900',
  },
  buttonDisabled: {
    opacity: 0.45,
  },
});
