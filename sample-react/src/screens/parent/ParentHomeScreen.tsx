import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useLayoutEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { DatePickerModal, formatDateLabel, todayString } from '../../components/common/DatePickerModal';

import { creditApi } from '../../api/creditApi';
import { familyApi } from '../../api/familyApi';
import { missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { BalanceCard, colors, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { MissionCard } from '../../components/mission/MissionCard';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentHome'>;

export function ParentHomeScreen({ navigation }: Props) {
  const { loginAs, logout, missions, parentCreditBalance } = useAppState();
  const [selectedDate, setSelectedDate] = useState(todayString);
  const [calendarVisible, setCalendarVisible] = useState(false);

  useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <Pressable
          onPress={async () => {
            await logout();
            navigation.replace('Login');
          }}
          style={({ pressed }) => ({ opacity: pressed ? 0.5 : 1, paddingHorizontal: 4 })}
        >
          <Ionicons name="log-out-outline" size={24} color="#FFFFFF" />
        </Pressable>
      ),
    });
  }, [navigation, logout]);
  const familyQuery = useQuery({
    queryKey: ['families', 'children'],
    queryFn: familyApi.getMyFamilies,
    enabled: !appConfig.useDummyData,
  });
  const summaryQuery = useQuery({
    queryKey: ['credit', 'parentSummary'],
    queryFn: creditApi.getParentSummary,
    enabled: !appConfig.useDummyData,
  });
  const missionsQuery = useQuery({
    queryKey: ['missions', 'parent', 'active', selectedDate],
    queryFn: () => missionApi.getMissions({ role: 'parent', date: selectedDate }),
    enabled: !appConfig.useDummyData,
  });
  const summary = summaryQuery.data ?? null;
  const displayMissions = appConfig.useDummyData ? missions : (missionsQuery.data ?? []);
  const pending = displayMissions.filter((mission) => mission.status === 'submitted');
  const active = displayMissions.filter((mission) => mission.status !== 'paid');
  const displayBalance = appConfig.useDummyData ? parentCreditBalance : (summary?.creditBalance ?? 0);
  const displayPendingCount = appConfig.useDummyData ? pending.length : (summary?.pendingApprovalCount ?? 0);
  const hasLinkedChild = appConfig.useDummyData
    ? true
    : (familyQuery.data?.linked ?? false);

  return (
    <ScreenFrame eyebrow="부모 홈" title="오늘의 보상 흐름" description="자녀 미션, 크레딧, 결제 운영 상태를 한곳에서 확인합니다.">
      <BalanceCard
        label="적립금"
        amount={displayBalance}
        description={`승인 대기 ${displayPendingCount}건 · 진행 미션 ${active.length}건`}
        actions={[
          { label: '충전', onPress: () => navigation.navigate('CreditCharge'), testID: 'parent-home-charge-button' },
          { label: '출금', onPress: () => navigation.navigate('ParentWithdrawal'), testID: 'parent-home-withdrawal-button' },
          { label: '승인', onPress: () => navigation.navigate('ParentApproval'), testID: 'parent-home-approval-button', badge: displayPendingCount },
        ]}
      />
      {!appConfig.useDummyData && (summaryQuery.isLoading || missionsQuery.isLoading) ? <LoadingState title="서버 조회 중" body="부모 홈 정보를 불러오고 있습니다." /> : null}
      <ApiErrorBox error={summaryQuery.error} fallback="부모 크레딧 요약 조회에 실패했습니다." />
      <ApiErrorBox error={missionsQuery.error} fallback="부모 미션 목록 조회에 실패했습니다." />
      {!hasLinkedChild ? (
        <View style={styles.onboardingCard}>
          <View style={styles.onboardingLeft}>
            <Ionicons name="people-outline" size={28} color={colors.primary} />
            <View style={styles.onboardingText}>
              <Text style={styles.onboardingTitle}>자녀를 연결해보세요</Text>
              <Text style={styles.onboardingBody}>자녀를 연결하면 미션과 적립금을 관리할 수 있어요.</Text>
            </View>
          </View>
          <Pressable
            onPress={() => navigation.navigate('ParentFamilyLink')}
            style={({ pressed }) => [styles.onboardingButton, pressed && { opacity: 0.7 }]}
            testID="parent-home-family-link-button"
          >
            <Text style={styles.onboardingButtonText}>연결하기</Text>
          </Pressable>
        </View>
      ) : null}
      <View style={styles.actionGrid}>
        {appConfig.useDummyData ? (
          <SecondaryButton
            title="자녀 화면"
            onPress={() => {
              loginAs('child');
              navigation.navigate('ChildHome');
            }}
            testID="parent-home-switch-child-button"
          />
        ) : null}
      </View>
      <DatePickerModal
        visible={calendarVisible}
        selected={selectedDate}
        onSelect={setSelectedDate}
        onClose={() => setCalendarVisible(false)}
      />
      <View style={styles.sectionHeader}>
        <View>
          <Text style={styles.sectionTitle}>진행 중 미션</Text>
          <Text style={styles.sectionDate}>{formatDateLabel(selectedDate)}</Text>
        </View>
        <View style={styles.sectionActions}>
          <Pressable
            onPress={() => setCalendarVisible(true)}
            style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
          >
            <Ionicons name="calendar-outline" size={20} color={colors.primary} />
          </Pressable>
          {hasLinkedChild ? (
            <Pressable
              onPress={() => navigation.navigate('ParentFamilyLink')}
              style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
              testID="parent-home-family-link-button"
            >
              <Ionicons name="person-add-outline" size={20} color={colors.dark} />
            </Pressable>
          ) : null}
          <Pressable
            onPress={() => navigation.navigate('MissionCreate')}
            style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
            testID="parent-home-create-mission-button"
          >
            <Ionicons name="add-outline" size={22} color={colors.dark} />
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate('ParentCreditHistory')}
            style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
            testID="parent-home-credit-history-button"
          >
            <Ionicons name="receipt-outline" size={20} color={colors.dark} />
          </Pressable>
        </View>
      </View>
      {active.length ? active.map((mission) => <MissionCard key={mission.id} mission={mission} />) : <EmptyState body="진행 중인 미션이 없습니다." />}
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  actionGrid: {
    gap: 12,
    marginBottom: 24,
  },
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
    marginTop: 6,
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 19,
    fontWeight: '900',
  },
  sectionDate: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '600',
    marginTop: 2,
  },
  sectionActions: {
    flexDirection: 'row',
    gap: 8,
  },
  sectionIconButton: {
    alignItems: 'center',
    backgroundColor: '#e9efff',
    borderRadius: 8,
    gap: 4,
    justifyContent: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  onboardingCard: {
    alignItems: 'center',
    backgroundColor: '#e9efff',
    borderColor: 'rgba(155, 166, 221, 0.45)',
    borderRadius: 14,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
    padding: 16,
  },
  onboardingLeft: {
    alignItems: 'center',
    flex: 1,
    flexDirection: 'row',
    gap: 12,
  },
  onboardingText: {
    flex: 1,
    gap: 2,
  },
  onboardingTitle: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '700',
  },
  onboardingBody: {
    color: colors.muted,
    fontSize: 12,
  },
  onboardingButton: {
    backgroundColor: colors.primary,
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  onboardingButtonText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
});
