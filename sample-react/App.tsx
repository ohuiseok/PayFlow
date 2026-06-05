import { StatusBar } from 'expo-status-bar';
import {
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';

import { appConfig } from './src/config/appConfig';
import {
  cashbookEntries,
  childSummary,
  flows,
  missions,
  parentSummary,
} from './src/data/dummyData';

const quickActions = ['부모 충전', '미션 등록', '캘린더', '보상 승인'];

function formatWon(amount: number) {
  return `${amount.toLocaleString('ko-KR')}원`;
}

export default function App() {
  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar style="dark" />
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <Text style={styles.eyebrow}>PayFlow Mobile</Text>
          <Text style={styles.title}>미션 보상 지갑</Text>
          <Text style={styles.subtitle}>
            부모가 크레딧을 충전하고 자녀에게 미션과 보상을 걸면, 자녀가 완료 후 돈을 버는 흐름입니다.
          </Text>
        </View>

        <View style={styles.summary}>
          <View>
            <Text style={styles.summaryLabel}>
              {appConfig.useDummyData ? '데이터 모드' : 'API 연결'}
            </Text>
            <Text style={styles.summaryValue}>
              {appConfig.useDummyData ? '더미데이터만 사용' : appConfig.apiBaseUrl}
            </Text>
          </View>
          <View style={styles.statusBadge}>
            <Text style={styles.statusText}>{appConfig.useDummyData ? '더미' : '연동'}</Text>
          </View>
        </View>

        {appConfig.useDummyData && (
          <>
            <View style={styles.balanceCard}>
              <Text style={styles.balanceLabel}>부모 보상 크레딧</Text>
              <Text style={styles.balanceValue}>{formatWon(parentSummary.creditBalance)}</Text>
              <Text style={styles.balanceMeta}>
                이번 달 지급 {formatWon(parentSummary.monthlyRewardPaid)} · 승인 대기{' '}
                {parentSummary.pendingApprovals}건
              </Text>
            </View>

            <View style={styles.childCard}>
              <View>
                <Text style={styles.childName}>{childSummary.childName}의 캐시북</Text>
                <Text style={styles.childMeta}>
                  이번 주 {formatWon(childSummary.weeklyEarned)} · 완료 미션{' '}
                  {childSummary.completedMissionCount}개
                </Text>
              </View>
              <Text style={styles.childBalance}>{formatWon(childSummary.cashBalance)}</Text>
            </View>
          </>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>빠른 작업</Text>
          <View style={styles.actionGrid}>
            {quickActions.map((action) => (
              <TouchableOpacity key={action} style={styles.actionButton} activeOpacity={0.75}>
                <Text style={styles.actionText}>{action}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {appConfig.useDummyData && (
          <>
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>더미 미션</Text>
              {missions.map((mission) => (
                <View key={mission.id} style={styles.missionItem}>
                  <View style={styles.flowHeader}>
                    <Text style={styles.flowTitle}>{mission.title}</Text>
                    <Text style={styles.rewardText}>{formatWon(mission.rewardAmount)}</Text>
                  </View>
                  <Text style={styles.flowDescription}>
                    {mission.childName} · {mission.dueLabel}
                  </Text>
                  <View style={styles.missionBadge}>
                    <Text style={styles.missionBadgeText}>{mission.status}</Text>
                  </View>
                </View>
              ))}
            </View>

            <View style={styles.section}>
              <Text style={styles.sectionTitle}>더미 캐시북</Text>
              {cashbookEntries.map((entry) => (
                <View key={entry.id} style={styles.cashbookItem}>
                  <View>
                    <Text style={styles.flowTitle}>{entry.title}</Text>
                    <Text style={styles.flowDescription}>{entry.description}</Text>
                  </View>
                  <Text style={[styles.amountText, entry.amount < 0 && styles.negativeAmount]}>
                    {entry.amount > 0 ? '+' : ''}
                    {entry.amount.toLocaleString('ko-KR')}
                  </Text>
                </View>
              ))}
            </View>
          </>
        )}

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>연동 예정 흐름</Text>
          {flows.map((flow) => (
            <View key={flow.title} style={styles.flowItem}>
              <View style={styles.flowHeader}>
                <Text style={styles.flowTitle}>{flow.title}</Text>
                <Text style={styles.endpoint}>{flow.stage}</Text>
              </View>
              <Text style={styles.flowDescription}>{flow.description}</Text>
            </View>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F7F8FA',
  },
  container: {
    padding: 20,
    paddingBottom: 36,
  },
  header: {
    marginBottom: 24,
  },
  eyebrow: {
    color: '#1F7A5C',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 8,
  },
  title: {
    color: '#161A1D',
    fontSize: 30,
    fontWeight: '800',
    lineHeight: 38,
  },
  subtitle: {
    color: '#5F6872',
    fontSize: 15,
    lineHeight: 22,
    marginTop: 10,
  },
  summary: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#E3E7EB',
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 24,
    padding: 16,
  },
  summaryLabel: {
    color: '#7A838C',
    fontSize: 12,
    fontWeight: '700',
    marginBottom: 6,
  },
  summaryValue: {
    color: '#20262D',
    fontSize: 14,
    fontWeight: '600',
  },
  statusBadge: {
    backgroundColor: '#EAF6EF',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  statusText: {
    color: '#1F7A5C',
    fontSize: 12,
    fontWeight: '800',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    color: '#20262D',
    fontSize: 18,
    fontWeight: '800',
    marginBottom: 12,
  },
  actionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  actionButton: {
    alignItems: 'center',
    backgroundColor: '#20262D',
    borderRadius: 8,
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 14,
    width: '48%',
  },
  actionText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '800',
  },
  flowItem: {
    backgroundColor: '#FFFFFF',
    borderColor: '#E3E7EB',
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 10,
    padding: 16,
  },
  flowHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  flowTitle: {
    color: '#20262D',
    fontSize: 16,
    fontWeight: '800',
  },
  endpoint: {
    color: '#1F7A5C',
    fontSize: 12,
    fontWeight: '700',
  },
  flowDescription: {
    color: '#66717C',
    fontSize: 14,
    lineHeight: 20,
  },
  balanceCard: {
    backgroundColor: '#17202A',
    borderRadius: 8,
    marginBottom: 14,
    padding: 18,
  },
  balanceLabel: {
    color: '#A9B7C4',
    fontSize: 13,
    fontWeight: '700',
    marginBottom: 8,
  },
  balanceValue: {
    color: '#FFFFFF',
    fontSize: 32,
    fontWeight: '800',
    marginBottom: 8,
  },
  balanceMeta: {
    color: '#A9B7C4',
    fontSize: 13,
  },
  childCard: {
    alignItems: 'center',
    backgroundColor: '#EEF5FC',
    borderColor: '#CFE0F4',
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 24,
    padding: 16,
  },
  childName: {
    color: '#20262D',
    fontSize: 16,
    fontWeight: '800',
    marginBottom: 6,
  },
  childMeta: {
    color: '#66717C',
    fontSize: 13,
  },
  childBalance: {
    color: '#2B6CB0',
    fontSize: 16,
    fontWeight: '800',
  },
  missionItem: {
    backgroundColor: '#FFFFFF',
    borderColor: '#E3E7EB',
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 10,
    padding: 16,
  },
  rewardText: {
    color: '#1F7A5C',
    fontSize: 14,
    fontWeight: '800',
  },
  missionBadge: {
    alignSelf: 'flex-start',
    backgroundColor: '#FFF8E8',
    borderRadius: 999,
    marginTop: 12,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  missionBadgeText: {
    color: '#8A6517',
    fontSize: 12,
    fontWeight: '800',
  },
  cashbookItem: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: '#E3E7EB',
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
    padding: 16,
  },
  amountText: {
    color: '#1F7A5C',
    fontSize: 16,
    fontWeight: '800',
  },
  negativeAmount: {
    color: '#D84F45',
  },
});
