import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';

import { cashbookApi } from '../../api/cashbookApi';
import { defaultChildUserId } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { ScreenFrame } from '../../components/common';
import { EmptyState, LoadingState } from '../../components/common/ScreenStates';
import { CashbookEntryItem } from '../../components/wallet/CashbookEntryItem';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildCashbook'>;

export function ChildCashbookScreen(_props: Props) {
  const { cashbookEntries, currentUserId } = useAppState();
  const childUserId = appConfig.useDummyData ? defaultChildUserId : currentUserId;

  const entriesQuery = useQuery({
    queryKey: ['cashbook', 'entries', childUserId],
    queryFn: () => cashbookApi.getChildEntries(childUserId),
    enabled: !appConfig.useDummyData,
  });

  const displayEntries = appConfig.useDummyData ? cashbookEntries : (entriesQuery.data ?? []);

  return (
    <ScreenFrame eyebrow="사용 기록" title="최근 사용 기록" description="지갑에서 출금되거나 미션으로 받은 내역입니다.">
      <ApiErrorBox error={entriesQuery.error} fallback="사용 기록 조회에 실패했습니다." />
      {entriesQuery.isLoading ? (
        <LoadingState title="불러오는 중" body="사용 기록을 불러오고 있습니다." />
      ) : displayEntries.length === 0 ? (
        <EmptyState body="사용 기록이 없습니다." />
      ) : (
        displayEntries.map((entry) => <CashbookEntryItem key={entry.id} entry={entry} />)
      )}
    </ScreenFrame>
  );
}
