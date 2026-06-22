import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';

import { creditApi } from '../../api/creditApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { ScreenFrame } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { CashbookEntryItem } from '../../components/wallet/CashbookEntryItem';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentCreditHistory'>;

export function ParentCreditHistoryScreen(_props: Props) {
  const { parentCreditEntries } = useAppState();

  const entriesQuery = useQuery({
    queryKey: ['credit', 'recentEntries'],
    queryFn: creditApi.getRecentCreditEntries,
    enabled: !appConfig.useDummyData,
  });

  const displayEntries = appConfig.useDummyData ? parentCreditEntries : (entriesQuery.data ?? []);

  return (
    <ScreenFrame eyebrow="충전금 기록" title="최근 충전금 기록" description="적립금 충전 및 미션 보상 지급 내역입니다.">
      <ApiErrorBox error={entriesQuery.error} fallback="충전금 기록 조회에 실패했습니다." />
      {entriesQuery.isLoading ? (
        <LoadingState title="불러오는 중" body="충전금 기록을 불러오고 있습니다." />
      ) : displayEntries.length === 0 ? (
        <EmptyState body="충전금 기록이 없습니다." />
      ) : (
        displayEntries.map((entry) => <CashbookEntryItem key={entry.id} entry={entry} />)
      )}
    </ScreenFrame>
  );
}
