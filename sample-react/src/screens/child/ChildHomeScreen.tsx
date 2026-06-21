import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';

import { cashbookApi } from '../../api/cashbookApi';
import { defaultChildUserId, missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { BalanceCard, colors, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { MissionCard } from '../../components/mission/MissionCard';
import { CashbookEntryItem } from '../../components/wallet/CashbookEntryItem';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { Mission } from '../../types';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildHome'>;

export function ChildHomeScreen({ navigation }: Props) {
  const { cashbookEntries, childCashBalance, currentUserId, loginAs, logout, missions } = useAppState();
  const childUserId = appConfig.useDummyData ? defaultChildUserId : currentUserId;
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
  const entriesQuery = useQuery({
    queryKey: ['cashbook', 'entries', childUserId],
    queryFn: () => cashbookApi.getChildEntries(childUserId),
    enabled: !appConfig.useDummyData,
  });
  const apiMissions = missionsQuery.data ?? null;
  const cashbookSummary = summaryQuery.data ?? null;
  const apiCashbookEntries = entriesQuery.data ?? null;
  const displayMissions = apiMissions ?? missions;
  const displayCashbookEntries = apiCashbookEntries ?? cashbookEntries;
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
    <ScreenFrame eyebrow="자녀 홈" title="내 미션과 캐시북" description="받을 수 있는 보상과 최근 기록을 확인합니다.">
      <BalanceCard
        label="내 지갑 잔액"
        amount={displayCashBalance}
        description={`진행 가능 ${todo.length}건 · 반려 ${rejected.length}건`}
      />
      <View style={styles.actionGrid}>
        <PrimaryButton title="계좌 등록" onPress={() => navigation.navigate('BankAccountRegister')} testID="child-home-bank-register-button" />
        <SecondaryButton title="출금" onPress={() => navigation.navigate('ChildWithdrawal')} testID="child-home-withdrawal-button" />
        {appConfig.useDummyData ? (
          <SecondaryButton
            title="부모 홈"
            onPress={() => {
              loginAs('parent');
              navigation.navigate('ParentHome');
            }}
            testID="child-home-switch-parent-button"
          />
        ) : null}
        <SecondaryButton
          title="로그아웃"
          onPress={async () => {
            await logout();
            navigation.replace('Login');
          }}
          testID="child-home-logout-button"
        />
      </View>
      <ApiErrorBox error={missionsQuery.error} fallback="미션 목록 조회에 실패했습니다." />
      <ApiErrorBox error={summaryQuery.error} fallback="캐시북 요약 조회에 실패했습니다." />
      <ApiErrorBox error={entriesQuery.error} fallback="캐시북 내역 조회에 실패했습니다." />
      <Text style={styles.sectionTitle}>미션</Text>
      {displayMissions.map((mission) => (
        <MissionCard
          key={mission.id}
          mission={mission}
          disabled={mission.status !== 'todo' && mission.status !== 'rejected'}
          onPress={() => openMission(mission)}
        />
      ))}
      <Text style={styles.sectionTitle}>최근 캐시북</Text>
      {displayCashbookEntries.map((entry) => <CashbookEntryItem key={entry.id} entry={entry} />)}
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  actionGrid: {
    gap: 12,
    marginBottom: 24,
  },
  sectionTitle: {
    color: colors.text,
    fontSize: 19,
    fontWeight: '900',
    marginBottom: 12,
    marginTop: 6,
  },
});
