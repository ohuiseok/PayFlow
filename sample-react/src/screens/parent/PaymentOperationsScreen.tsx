import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { LedgerEntry, operationsApi, TossCompensationCharge } from '../../api/operationsApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { Card, colors, PrimaryButton, ScreenFrame, StatusBadge } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'PaymentOperations'>;
type TabKey = 'summary' | 'walletCompensations' | 'ledgerCompensations' | 'ledger';

const tabs: { key: TabKey; label: string }[] = [
  { key: 'summary', label: '요약' },
  { key: 'walletCompensations', label: '지갑 보상' },
  { key: 'ledgerCompensations', label: '원장 보상' },
  { key: 'ledger', label: '원장' },
];

const dummySummary = {
  readyCount: 2,
  completedCount: 18,
  failedCount: 1,
  canceledCount: 3,
  compensationRequiredCount: 1,
  ledgerCompensationRequiredCount: 1,
};

const dummyWalletCompensations: TossCompensationCharge[] = [
  {
    chargeId: '3001',
    userId: '1',
    providerCode: 'TOSS_PAYMENTS',
    amount: 50000,
    status: 'COMPENSATION_REQUIRED',
    failureCode: 'WALLET_DEPOSIT_FAILED',
    failureReason: 'wallet-service timeout',
    compensationRetryCount: 1,
    compensationFailureReason: 'last retry timed out',
    ledgerRecorded: false,
    ledgerRecordType: '',
    ledgerFailureReason: '',
    ledgerRetryCount: 0,
  },
];

const dummyLedgerCompensations: TossCompensationCharge[] = [
  {
    chargeId: '3002',
    userId: '1',
    providerCode: 'TOSS_PAYMENTS',
    amount: 30000,
    status: 'COMPLETED',
    failureCode: '',
    failureReason: '',
    compensationRetryCount: 0,
    compensationFailureReason: '',
    ledgerRecorded: false,
    ledgerRecordType: 'TOSS_CHARGE',
    ledgerFailureReason: 'ledger-service timeout',
    ledgerRetryCount: 1,
  },
];

const dummyLedgerEntries: LedgerEntry[] = [
  {
    id: '501',
    sourceLabel: 'TOSS_CHARGE 3001',
    sourceType: 'TOSS_CHARGE',
    sourceId: '3001',
    entryType: 'USER_WALLET_TOPUP',
    amount: 50000,
    createdAt: '2026-06-20T16:30:00',
    lines: [
      { id: '1001', userId: '-', accountCode: 'PG_CASH', type: 'DEBIT', amount: 50000 },
      { id: '1002', userId: '1', accountCode: 'USER_WALLET', type: 'CREDIT', amount: 50000 },
    ],
  },
];

function formatWon(amount: number) {
  return `${amount.toLocaleString('ko-KR')}원`;
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function statusTone(status: string): 'green' | 'blue' | 'yellow' | 'danger' {
  if (status.includes('FAILED') || status.includes('REQUIRED')) {
    return 'danger';
  }
  if (status.includes('READY') || status.includes('WAIT')) {
    return 'yellow';
  }
  if (status.includes('CANCEL')) {
    return 'blue';
  }
  return 'green';
}

function Metric({ label, value, tone = 'default' }: { label: string; value: number; tone?: 'default' | 'danger' }) {
  return (
    <View style={[styles.metric, tone === 'danger' && styles.metricDanger]}>
      <Text style={[styles.metricValue, tone === 'danger' && styles.metricDangerText]}>{value.toLocaleString('ko-KR')}</Text>
      <Text style={styles.metricLabel}>{label}</Text>
    </View>
  );
}

function TabButton({ active, label, onPress }: { active: boolean; label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={[styles.tabButton, active && styles.tabButtonActive]}>
      <Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text>
    </Pressable>
  );
}

export function PaymentOperationsScreen({ navigation }: Props) {
  const [activeTab, setActiveTab] = useState<TabKey>('summary');
  const [actionMessage, setActionMessage] = useState('');
  const queryClient = useQueryClient();
  const useServer = !appConfig.useDummyData;

  const summaryQuery = useQuery({
    queryKey: ['operations', 'tossSummary'],
    queryFn: operationsApi.getTossSummary,
    enabled: useServer,
  });
  const walletCompensationsQuery = useQuery({
    queryKey: ['operations', 'tossCompensations'],
    queryFn: operationsApi.getTossCompensations,
    enabled: useServer,
  });
  const ledgerCompensationsQuery = useQuery({
    queryKey: ['operations', 'tossLedgerCompensations'],
    queryFn: operationsApi.getTossLedgerCompensations,
    enabled: useServer,
  });
  const ledgerQuery = useQuery({
    queryKey: ['operations', 'ledgerEntries'],
    queryFn: operationsApi.getLedgerEntries,
    enabled: useServer,
  });
  const walletRetryMutation = useMutation({
    mutationFn: operationsApi.retryTossCompensation,
    onSuccess: () => handleRetrySuccess('지갑 보상 재처리를 요청했습니다.'),
    onError: (error) => setActionMessage(getErrorMessage(error, '지갑 보상 재처리에 실패했습니다.')),
  });
  const ledgerRetryMutation = useMutation({
    mutationFn: operationsApi.retryTossLedgerCompensation,
    onSuccess: () => handleRetrySuccess('원장 보상 재처리를 요청했습니다.'),
    onError: (error) => setActionMessage(getErrorMessage(error, '원장 보상 재처리에 실패했습니다.')),
  });

  async function handleRetrySuccess(message: string) {
    setActionMessage(message);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['operations', 'tossSummary'] }),
      queryClient.invalidateQueries({ queryKey: ['operations', 'tossCompensations'] }),
      queryClient.invalidateQueries({ queryKey: ['operations', 'tossLedgerCompensations'] }),
      queryClient.invalidateQueries({ queryKey: ['operations', 'ledgerEntries'] }),
    ]);
  }

  const summary = summaryQuery.data ?? dummySummary;
  const walletCompensations = walletCompensationsQuery.data ?? dummyWalletCompensations;
  const ledgerCompensations = ledgerCompensationsQuery.data ?? dummyLedgerCompensations;
  const ledgers = ledgerQuery.data ?? dummyLedgerEntries;
  const loading = summaryQuery.isLoading || walletCompensationsQuery.isLoading || ledgerCompensationsQuery.isLoading || ledgerQuery.isLoading;

  return (
    <ScreenFrame eyebrow="결제 운영" title="PG 정합성 점검" description="Toss 승인, 지갑 반영, 원장 기록과 보상 재처리를 한 화면에서 확인합니다.">
      <View style={styles.tabs}>
        {tabs.map((tab) => (
          <TabButton key={tab.key} active={activeTab === tab.key} label={tab.label} onPress={() => setActiveTab(tab.key)} />
        ))}
      </View>

      {loading ? <LoadingState title="운영 지표 조회 중" body="결제와 원장 상태를 불러오고 있습니다." /> : null}
      <ApiErrorBox error={summaryQuery.error} fallback="Toss 운영 요약 조회에 실패했습니다." />
      <ApiErrorBox error={walletCompensationsQuery.error} fallback="지갑 보상 목록 조회에 실패했습니다." />
      <ApiErrorBox error={ledgerCompensationsQuery.error} fallback="원장 보상 목록 조회에 실패했습니다." />
      <ApiErrorBox error={ledgerQuery.error} fallback="원장 목록 조회에 실패했습니다." />
      {actionMessage ? (
        <Card tone={actionMessage.includes('실패') ? 'yellow' : 'green'}>
          <Text style={styles.messageText}>{actionMessage}</Text>
        </Card>
      ) : null}

      {activeTab === 'summary' ? (
        <View style={styles.metricGrid}>
          <Metric label="대기" value={summary.readyCount} />
          <Metric label="완료" value={summary.completedCount} />
          <Metric label="실패" value={summary.failedCount} tone={summary.failedCount ? 'danger' : 'default'} />
          <Metric label="취소" value={summary.canceledCount} />
          <Metric label="지갑 보상" value={summary.compensationRequiredCount} tone={summary.compensationRequiredCount ? 'danger' : 'default'} />
          <Metric label="원장 보상" value={summary.ledgerCompensationRequiredCount} tone={summary.ledgerCompensationRequiredCount ? 'danger' : 'default'} />
        </View>
      ) : null}

      {activeTab === 'walletCompensations' ? (
        <View>
          {walletCompensations.length ? (
            walletCompensations.map((charge) => (
              <CompensationCard
                key={charge.chargeId}
                charge={charge}
                mode="wallet"
                processing={walletRetryMutation.isPending}
                onRetry={() => walletRetryMutation.mutate(charge.chargeId)}
              />
            ))
          ) : (
            <EmptyState body="지갑 보상 재처리가 필요한 Toss 충전이 없습니다." />
          )}
        </View>
      ) : null}

      {activeTab === 'ledgerCompensations' ? (
        <View>
          {ledgerCompensations.length ? (
            ledgerCompensations.map((charge) => (
              <CompensationCard
                key={charge.chargeId}
                charge={charge}
                mode="ledger"
                processing={ledgerRetryMutation.isPending}
                onRetry={() => ledgerRetryMutation.mutate(charge.chargeId)}
              />
            ))
          ) : (
            <EmptyState body="원장 보상 재처리가 필요한 Toss 충전이 없습니다." />
          )}
        </View>
      ) : null}

      {activeTab === 'ledger' ? (
        <View>
          {ledgers.length ? ledgers.map((entry) => <LedgerCard key={entry.id} entry={entry} />) : <EmptyState body="최근 원장 기록이 없습니다." />}
        </View>
      ) : null}

      <PrimaryButton title="충전 화면으로" variant="dark" onPress={() => navigation.navigate('CreditCharge')} />
    </ScreenFrame>
  );
}

function CompensationCard({
  charge,
  mode,
  processing,
  onRetry,
}: {
  charge: TossCompensationCharge;
  mode: 'wallet' | 'ledger';
  processing: boolean;
  onRetry: () => void;
}) {
  const isLedger = mode === 'ledger';
  const reason = isLedger
    ? `${charge.ledgerRecordType || 'TOSS_CHARGE'} · ${charge.ledgerFailureReason || '상세 사유 없음'}`
    : `${charge.failureCode || '원인 미상'} · ${charge.failureReason || '상세 사유 없음'}`;
  const retryCount = isLedger ? charge.ledgerRetryCount : charge.compensationRetryCount;

  return (
    <Card>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>충전 #{charge.chargeId}</Text>
        <StatusBadge label={isLedger ? 'LEDGER_REQUIRED' : charge.status} tone={statusTone(isLedger ? 'REQUIRED' : charge.status)} />
      </View>
      <Text style={styles.amount}>{formatWon(charge.amount)}</Text>
      <Text style={styles.meta}>사용자 {charge.userId} · {charge.providerCode}</Text>
      <Text style={styles.reason}>{reason}</Text>
      <Text style={styles.meta}>재시도 {retryCount}회</Text>
      <PrimaryButton title={isLedger ? '원장 재처리' : '지갑 보상 재처리'} loading={processing} disabled={processing} onPress={onRetry} />
    </Card>
  );
}

function LedgerCard({ entry }: { entry: LedgerEntry }) {
  return (
    <Card>
      <View style={styles.cardHeader}>
        <View style={styles.titleWrap}>
          <Text style={styles.cardTitle}>{entry.sourceLabel}</Text>
          <Text style={styles.meta}>{formatDate(entry.createdAt)}</Text>
        </View>
        <StatusBadge label={entry.entryType} tone={entry.entryType.includes('CANCEL') ? 'blue' : 'green'} />
      </View>
      <Text style={styles.amount}>{formatWon(entry.amount)}</Text>
      {entry.lines.map((line) => (
        <View key={line.id} style={styles.lineRow}>
          <View>
            <Text style={styles.lineAccount}>{line.accountCode}</Text>
            <Text style={styles.meta}>user {line.userId}</Text>
          </View>
          <View style={styles.lineRight}>
            <Text style={styles.lineType}>{line.type}</Text>
            <Text style={styles.lineAmount}>{formatWon(line.amount)}</Text>
          </View>
        </View>
      ))}
    </Card>
  );
}

const styles = StyleSheet.create({
  tabs: {
    backgroundColor: '#e0e7ff',
    borderRadius: 8,
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    marginBottom: 16,
    padding: 4,
  },
  tabButton: {
    alignItems: 'center',
    borderRadius: 6,
    flexBasis: '48%',
    flexGrow: 1,
    justifyContent: 'center',
    minHeight: 44,
  },
  tabButtonActive: {
    backgroundColor: '#FFFFFF',
  },
  tabText: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: '900',
  },
  tabTextActive: {
    color: colors.dark,
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
    marginBottom: 16,
  },
  metric: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    flexBasis: '47%',
    flexGrow: 1,
    minHeight: 94,
    padding: 16,
  },
  metricDanger: {
    backgroundColor: '#FDEEEE',
    borderColor: '#F6C5C0',
  },
  metricValue: {
    color: colors.text,
    fontSize: 28,
    fontWeight: '900',
  },
  metricDangerText: {
    color: colors.danger,
  },
  metricLabel: {
    color: colors.muted,
    fontSize: 14,
    fontWeight: '800',
    marginTop: 6,
  },
  messageText: {
    color: colors.dark,
    fontSize: 16,
    fontWeight: '900',
    lineHeight: 23,
  },
  cardHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: 10,
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  titleWrap: {
    flex: 1,
  },
  cardTitle: {
    color: colors.text,
    flexShrink: 1,
    fontSize: 19,
    fontWeight: '900',
  },
  amount: {
    color: colors.text,
    fontSize: 24,
    fontWeight: '900',
    marginBottom: 8,
  },
  meta: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '700',
    lineHeight: 20,
  },
  reason: {
    color: colors.dark,
    fontSize: 15,
    fontWeight: '800',
    lineHeight: 22,
    marginVertical: 10,
  },
  lineRow: {
    alignItems: 'center',
    borderTopColor: colors.line,
    borderTopWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12,
  },
  lineAccount: {
    color: colors.text,
    fontSize: 15,
    fontWeight: '900',
  },
  lineRight: {
    alignItems: 'flex-end',
  },
  lineType: {
    color: colors.blue,
    fontSize: 12,
    fontWeight: '900',
    marginBottom: 3,
  },
  lineAmount: {
    color: colors.text,
    fontSize: 15,
    fontWeight: '900',
  },
});
