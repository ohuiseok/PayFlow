import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { creditApi } from '../../api/creditApi';
import { familyApi } from '../../api/familyApi';
import { defaultChildUserId, missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import {
  FormField,
  formatAmountInput,
  parseAmount,
  PrimaryButton,
  ScreenFrame,
} from '../../components/common';
import { ChildOption, ChildSelector } from '../../components/mission/ChildSelector';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { LinkedChild } from '../../types';
import { getErrorMessage } from '../../utils/apiError';
import { validateMissionDueDate } from '../../utils/dateValidation';
import { hasMinLength, isAmountInRange } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'MissionCreate'>;

export function MissionCreateScreen({ navigation }: Props) {
  const { createMission, linkedChildren, parentCreditBalance: parentCreditBalanceFallback } = useAppState();
  const summaryQuery = useQuery({
    queryKey: ['credit', 'parentSummary'],
    queryFn: () => creditApi.getParentSummary(),
  });
  const parentCreditBalance = summaryQuery.data?.creditBalance ?? parentCreditBalanceFallback;
  const queryClient = useQueryClient();
  const [title, setTitle] = useState('영어 단어 20개 외우기');
  const [description, setDescription] = useState('단어와 사진과 쓰기 결과를 올려주세요.');
  const [amountText, setAmountText] = useState('5000');
  const [dueDate, setDueDate] = useState('2026-06-30');
  const [apiError, setApiError] = useState('');

  // 더미 모드: AppState의 linkedChildren 사용
  // API 모드: familyApi에서 조회
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

  useEffect(() => {
    if (selectedChildId !== null || childOptions.length === 0) {
      return;
    }
    setSelectedChildId(childOptions[0].childUserId);
  }, [childOptions, selectedChildId]);

  const selectedChild = childOptions.find((c) => String(c.childUserId) === String(selectedChildId)) ?? null;

  const amount = parseAmount(amountText);
  const dueDateError = validateMissionDueDate(dueDate);
  const valid =
    hasMinLength(title, 1) &&
    isAmountInRange(amount, 1000, parentCreditBalance) &&
    !dueDateError &&
    selectedChild !== null;

  const createMissionMutation = useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        createMission({ title, description, rewardAmount: amount, dueDate });
        return;
      }

      const targetChildUserId = selectedChild?.childUserId ?? defaultChildUserId;
      await missionApi.createMission({
        childUserId: targetChildUserId,
        title: title.trim(),
        description: description.trim(),
        rewardAmount: amount,
        missionDate: dueDate,
        evidenceRequired: true,
      });
    },
    onMutate: () => {
      setApiError('');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['missions'] });
      navigation.navigate('ParentHome');
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, '미션 등록에 실패했습니다.'));
    },
  });
  const loading = createMissionMutation.isPending;

  const submit = () => {
    if (!valid) {
      return;
    }
    createMissionMutation.mutate();
  };

  return (
    <ScreenFrame eyebrow="미션 등록" title="새 미션 만들기" description="자녀에게 할 일과 보상 금액을 보냅니다.">
      <ChildSelector
        children={childOptions}
        selectedId={selectedChildId}
        onSelect={(child) => setSelectedChildId(child.childUserId)}
      />
      <ApiErrorBox error={familyQuery.error} fallback="연결 자녀 조회에 실패했습니다." />
      <ApiErrorBox error={apiError} fallback="미션 등록에 실패했습니다." />
      <FormField label="미션 이름" placeholder="예: 수학 문제집 3쪽" value={title} onChangeText={setTitle} disabled={loading} />
      <FormField label="조건 안내" placeholder="완료 기준" value={description} onChangeText={setDescription} disabled={loading} />
      <FormField
        label="수행 날짜"
        placeholder="예: 2026-06-30"
        value={dueDate}
        onChangeText={setDueDate}
        error={dueDateError}
        disabled={loading}
      />
      <FormField
        label="보상 금액"
        placeholder="1,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        error={amount > parentCreditBalance ? '적립금보다 큰 금액은 등록할 수 없습니다.' : undefined}
        disabled={loading}
      />
      <PrimaryButton title={loading ? '등록 중' : '미션 등록'} onPress={submit} disabled={!valid || loading} loading={loading} />
    </ScreenFrame>
  );
}
