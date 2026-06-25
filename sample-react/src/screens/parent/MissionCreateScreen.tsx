import { Ionicons } from '@expo/vector-icons';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { creditApi } from '../../api/creditApi';
import { familyApi } from '../../api/familyApi';
import { defaultChildUserId, missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { DatePickerModal, formatDateLabel, todayString } from '../../components/common/DatePickerModal';
import {
  colors,
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
  const [title, setTitle] = useState('청년 금융 교육 참여');
  const [description, setDescription] = useState('교육 수료 화면 또는 참여 후기를 제출해 주세요.');
  const [amountText, setAmountText] = useState('5000');
  const [dueDate, setDueDate] = useState(todayString);
  const [calendarVisible, setCalendarVisible] = useState(false);
  const [apiError, setApiError] = useState('');

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
          childName: f.childName ?? `청년 ${f.childUserId}`,
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
  const valid =
    hasMinLength(title, 1) &&
    isAmountInRange(amount, 1000, parentCreditBalance) &&
    !!dueDate &&
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
      setApiError(getErrorMessage(error, '정책 미션 등록에 실패했습니다.'));
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
    <ScreenFrame eyebrow="정책 미션 등록" title="새 정책 미션 만들기" description="청년 참여자에게 수행할 과제와 지원금 금액을 보냅니다.">
      <DatePickerModal
        visible={calendarVisible}
        selected={dueDate}
        onSelect={setDueDate}
        onClose={() => setCalendarVisible(false)}
      />
      <ChildSelector
        children={childOptions}
        selectedId={selectedChildId}
        onSelect={(child) => setSelectedChildId(child.childUserId)}
      />
      <ApiErrorBox error={familyQuery.error} fallback="연결 참여자 조회에 실패했습니다." />
      <ApiErrorBox error={apiError} fallback="정책 미션 등록에 실패했습니다." />
      <FormField label="미션 이름" placeholder="예: 청년 금융 교육 참여" value={title} onChangeText={setTitle} disabled={loading} />
      <FormField label="참여 조건 안내" placeholder="완료 기준" value={description} onChangeText={setDescription} disabled={loading} />
      <View style={styles.fieldWrap}>
        <Text style={styles.fieldLabel}>수행 날짜</Text>
        <Pressable
          onPress={() => !loading && setCalendarVisible(true)}
          style={({ pressed }) => [styles.dateButton, loading && styles.dateButtonDisabled, pressed && { opacity: 0.7 }]}
        >
          <Ionicons name="calendar-outline" size={18} color={loading ? colors.muted : colors.primary} />
          <Text style={[styles.dateText, loading && styles.dateTextMuted]}>
            {formatDateLabel(dueDate)}
          </Text>
        </Pressable>
      </View>
      <FormField
        label="지원금 금액"
        placeholder="1,000원 이상"
        value={formatAmountInput(amountText)}
        onChangeText={(value) => setAmountText(formatAmountInput(value))}
        keyboardType="number-pad"
        error={amount > parentCreditBalance ? '지원금 잔액보다 큰 금액은 등록할 수 없습니다.' : undefined}
        disabled={loading}
      />
      <PrimaryButton title={loading ? '등록 중' : '정책 미션 등록'} onPress={submit} disabled={!valid || loading} loading={loading} />
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  fieldWrap: {
    marginBottom: 14,
  },
  fieldLabel: {
    color: colors.dark,
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 8,
  },
  dateButton: {
    alignItems: 'center',
    backgroundColor: colors.primarySoft,
    borderColor: 'rgba(4, 113, 233, 0.25)',
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 10,
    minHeight: 58,
    paddingHorizontal: 16,
  },
  dateButtonDisabled: {
    opacity: 0.5,
  },
  dateText: {
    color: colors.primary,
    fontSize: 16,
    fontWeight: '700',
  },
  dateTextMuted: {
    color: colors.muted,
  },
});
