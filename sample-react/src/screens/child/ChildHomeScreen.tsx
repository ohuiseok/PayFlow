import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useLayoutEffect } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { authApi } from '../../api/authApi';
import { cashbookApi } from '../../api/cashbookApi';
import { defaultChildUserId, missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { BalanceCard, colors, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { MissionCard } from '../../components/mission/MissionCard';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { Mission } from '../../types';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildHome'>;

export function ChildHomeScreen({ navigation }: Props) {
  const { childCashBalance, currentUserId, hasBankAccount: hasBankAccountState, markBankAccountRegistered, loginAs, logout, missions } = useAppState();

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
          <Ionicons name="log-out-outline" size={24} color="#FAFAF8" />
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
    queryKey: ['missions', 'child', 'active', childUserId],
    queryFn: () => missionApi.getMissions({ role: 'child' }),
    enabled: !appConfig.useDummyData,
  });
  const summaryQuery = useQuery({
    queryKey: ['cashbook', 'summary', childUserId],
    queryFn: () => cashbookApi.getChildSummary(childUserId),
    enabled: !appConfig.useDummyData,
  });
  const apiMissions = missionsQuery.data ?? null;
  const cashbookSummary = summaryQuery.data ?? null;
  const displayMissions = apiMissions ?? missions;
  const displayCashBalance = cashbookSummary?.balance ?? childCashBalance;
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
      <ApiErrorBox error={missionsQuery.error} fallback="미션 목록 조회에 실패했습니다." />
      <ApiErrorBox error={summaryQuery.error} fallback="사용 기록 요약 조회에 실패했습니다." />
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>오늘의 미션</Text>
        <Pressable
          onPress={() => navigation.navigate('ChildCashbook')}
          style={({ pressed }) => [styles.sectionIconButton, pressed && { opacity: 0.5 }]}
          testID="child-home-cashbook-button"
        >
          <Ionicons name="list-outline" size={20} color={colors.dark} />
        </Pressable>
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
  sectionIconButton: {
    alignItems: 'center',
    backgroundColor: '#EEF1F4',
    borderRadius: 8,
    justifyContent: 'center',
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
});
