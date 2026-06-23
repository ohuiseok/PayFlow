import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { authApi } from '../../api/authApi';
import { cashbookApi } from '../../api/cashbookApi';
import { defaultChildUserId, missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { BalanceCard, colors, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { LoadingState } from '../../components/common/ScreenStates';
import { DatePickerModal, formatDateLabel, todayString } from '../../components/common/DatePickerModal';
import { MissionCard } from '../../components/mission/MissionCard';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { Mission } from '../../types';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildHome'>;

export function ChildHomeScreen({ navigation }: Props) {
  const { childCashBalance, currentUserId, hasBankAccount: hasBankAccountState, markBankAccountRegistered, loginAs, logout, missions } = useAppState();
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
  const childUserId = appConfig.useDummyData ? defaultChildUserId : currentUserId;
  const meQuery = useQuery({
    queryKey: ['users', 'me'],
    queryFn: authApi.me,
    enabled: !appConfig.useDummyData,
  });
  const hasBankAccount = meQuery.data?.hasBankAccount ?? hasBankAccountState;
  useEffect(() => {
    if (meQuery.data?.hasBankAccount && !hasBankAccountState) {
      markBankAccountRegistered();
    }
  }, [meQuery.data?.hasBankAccount, hasBankAccountState, markBankAccountRegistered]);
  const missionsQuery = useQuery({
    queryKey: ['missions', 'child', 'active', childUserId, selectedDate],
    queryFn: () => missionApi.getMissions({ role: 'child', date: selectedDate }),
    enabled: !appConfig.useDummyData,
  });
  const summaryQuery = useQuery({
    queryKey: ['cashbook', 'summary', childUserId],
    queryFn: () => cashbookApi.getChildSummary(childUserId),
    enabled: !appConfig.useDummyData,
  });
  const cashbookSummary = summaryQuery.data ?? null;
  const displayMissions = appConfig.useDummyData ? missions : (missionsQuery.data ?? []);
  const displayCashBalance = appConfig.useDummyData ? childCashBalance : (cashbookSummary?.balance ?? 0);
  const todo = displayMissions.filter((mission) => mission.status === 'todo');
  const rejected = displayMissions.filter((mission) => mission.status === 'rejected');

  const openMission = (mission: Mission) => {
    if (mission.status === 'todo') {
      navigation.navigate('MissionSubmit', { missionId: mission.id });
      return;
    }

    if (mission.status === 'rejected') {
      navigation.navigate('RejectResubmit', { missionId: mission.id });
    }
  };

  return (
    <ScreenFrame eyebrow="자녀 홈" title="내 미션과 사용 기록" description="받을 수 있는 보상과 최근 기록을 확인합니다.">
      <BalanceCard
        label="내 지갑 잔액"
        amount={displayCashBalance}
        description={`진행 가능 ${todo.length}건 · 반려 ${rejected.length}건`}
        actions={[
          ...(hasBankAccount ? [] : [{ label: '계좌 등록', onPress: () => navigation.navigate('BankAccountRegister'), testID: 'child-home-bank-register-button' }]),
          { label: '출금', onPress: () => navigation.navigate('ChildWithdrawal'), testID: 'child-home-withdrawal-button' },
        ]}
      />
      {appConfig.useDummyData ? (
        <View style={styles.actionGrid}>
          <SecondaryButton
            title="부모 홈"
            onPress={() => {
              loginAs('parent');
              navigation.navigate('ParentHome');
            }}
            testID="child-home-switch-parent-button"
          />
        </View>
      ) : null}
      {!appConfig.useDummyData && (missionsQuery.isLoading || summaryQuery.isLoading) ? (
        <LoadingState title="서버 조회 중" body="미션 정보를 불러오고 있습니다." />
      ) : null}
      <ApiErrorBox error={missionsQuery.error} fallback="미션 목록 조회에 실패했습니다." />
      <ApiErrorBox error={summaryQuery.error} fallback="사용 기록 요약 조회에 실패했습니다." />
      <DatePickerModal
        visible={calendarVisible}
        selected={selectedDate}
        onSelect={setSelectedDate}
        onClose={() => setCalendarVisible(false)}
      />
      <View style={styles.sectionHeader}>
        <View>
          <Text style={styles.sectionTitle}>오늘의 미션</Text>
          <Text style={styles.sectionDate}>{formatDateLabel(selectedDate)}</Text>
        </View>
        <View style={styles.sectionActions}>
          <Pressable
            onPress={() => setCalendarVisible(true)}
            style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
          >
            <Ionicons name="calendar-outline" size={20} color={colors.primary} />
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate('ChildCashbook')}
            style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
            testID="child-home-cashbook-button"
          >
            <Ionicons name="list-outline" size={20} color={colors.dark} />
          </Pressable>
        </View>
      </View>
      {displayMissions.map((mission) => (
        <MissionCard
          key={mission.id}
          mission={mission}
          disabled={mission.status !== 'todo' && mission.status !== 'rejected'}
          onPress={() => openMission(mission)}
        />
      ))}
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
    justifyContent: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
});
