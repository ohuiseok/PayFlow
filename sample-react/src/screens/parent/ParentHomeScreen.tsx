import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { StyleSheet, Text, View } from 'react-native';

import { creditApi } from '../../api/creditApi';
import { missionApi } from '../../api/missionApi';
import { appConfig } from '../../config/appConfig';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { BalanceCard, colors, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { MissionCard } from '../../components/mission/MissionCard';
import { CashbookEntryItem } from '../../components/wallet/CashbookEntryItem';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentHome'>;

export function ParentHomeScreen({ navigation }: Props) {
  const { loginAs, missions, parentCreditBalance, parentCreditEntries } = useAppState();
  const summaryQuery = useQuery({
    queryKey: ['credit', 'parentSummary'],
    queryFn: creditApi.getParentSummary,
    enabled: !appConfig.useDummyData,
  });
  const missionsQuery = useQuery({
    queryKey: ['missions', 'parent', 'active'],
    queryFn: () => missionApi.getMissions({ role: 'parent' }),
    enabled: !appConfig.useDummyData,
  });
  const summary = summaryQuery.data ?? null;
  const apiMissions = missionsQuery.data ?? null;
  const displayMissions = apiMissions ?? missions;
  const pending = displayMissions.filter((mission) => mission.status === 'submitted');
  const active = displayMissions.filter((mission) => mission.status !== 'paid');
  const displayBalance = summary?.creditBalance ?? parentCreditBalance;
  const displayPendingCount = summary?.pendingApprovalCount ?? pending.length;

  return (
    <ScreenFrame eyebrow="부모 홈" title="오늘의 보상 흐름" description="자녀 미션과 크레딧 상태를 한 번에 확인합니다.">
      <BalanceCard
        label="보상 크레딧"
        amount={displayBalance}
        description={`승인 대기 ${displayPendingCount}건 · 진행 미션 ${active.length}건`}
      />
      {summaryQuery.isLoading || missionsQuery.isLoading ? <LoadingState title="API 조회 중" body="부모 홈 정보를 불러오고 있습니다." /> : null}
      <ApiErrorBox error={summaryQuery.error} fallback="부모 크레딧 요약 조회에 실패했습니다." />
      <ApiErrorBox error={missionsQuery.error} fallback="부모 미션 목록 조회에 실패했습니다." />
      <View style={styles.actionGrid}>
        <PrimaryButton title="충전" onPress={() => navigation.navigate('CreditCharge')} />
        <SecondaryButton title="미션 등록" onPress={() => navigation.navigate('MissionCreate')} />
        <SecondaryButton title="승인" onPress={() => navigation.navigate('ParentApproval')} />
        {appConfig.useDummyData ? (
          <SecondaryButton title="자녀 홈" onPress={() => { loginAs('child'); navigation.navigate('ChildHome'); }} />
        ) : null}
      </View>
      <Text style={styles.sectionTitle}>진행 중 미션</Text>
      {active.length ? active.map((mission) => <MissionCard key={mission.id} mission={mission} />) : <EmptyState body="진행 중인 미션이 없습니다." />}
      <Text style={styles.sectionTitle}>최근 크레딧 기록</Text>
      {parentCreditEntries.slice(0, 3).map((entry) => <CashbookEntryItem key={entry.id} entry={entry} />)}
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
