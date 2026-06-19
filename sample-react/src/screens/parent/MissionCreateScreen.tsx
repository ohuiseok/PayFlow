import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { familyApi } from '../../api/familyApi';
import { defaultChildUserId, missionApi } from '../../api/missionApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import {
  colors,
  FormField,
  formatAmountInput,
  parseAmount,
  PrimaryButton,
  ScreenFrame,
} from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { LinkedChild } from '../../types';
import { getErrorMessage } from '../../utils/apiError';
import { validateMissionDueDate } from '../../utils/dateValidation';
import { hasMinLength, isAmountInRange } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'MissionCreate'>;

type ChildOption = {
  childUserId: string | number;
  childName: string;
  phoneNumber: string;
};

function ChildSelector({
  children,
  selectedId,
  onSelect,
}: {
  children: ChildOption[];
  selectedId: string | number | null;
  onSelect: (child: ChildOption) => void;
}) {
  if (children.length === 0) {
    return (
      <View style={selectorStyles.emptyBox}>
        <Text style={selectorStyles.emptyText}>연결된 자녀가 없습니다. 먼저 자녀를 연결해 주세요.</Text>
      </View>
    );
  }

  return (
    <View style={selectorStyles.container}>
      <Text style={selectorStyles.label}>미션 대상 자녀</Text>
      {children.map((child) => {
        const selected = String(child.childUserId) === String(selectedId);
        return (
          <TouchableOpacity
            key={String(child.childUserId)}
            style={[selectorStyles.row, selected && selectorStyles.rowSelected]}
            onPress={() => onSelect(child)}
            activeOpacity={0.7}
          >
            <View style={[selectorStyles.radio, selected && selectorStyles.radioSelected]}>
              {selected ? <View style={selectorStyles.radioDot} /> : null}
            </View>
            <View style={selectorStyles.info}>
              <Text style={[selectorStyles.name, selected && selectorStyles.nameSelected]}>{child.childName}</Text>
              <Text style={selectorStyles.phone}>{child.phoneNumber}</Text>
            </View>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const selectorStyles = StyleSheet.create({
  container: {
    marginBottom: 4,
  },
  label: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '600',
    marginBottom: 8,
  },
  row: {
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    flexDirection: 'row',
    gap: 12,
    marginBottom: 8,
    paddingHorizontal: 14,
    paddingVertical: 14,
  },
  rowSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  radio: {
    alignItems: 'center',
    borderColor: colors.line,
    borderRadius: 10,
    borderWidth: 2,
    height: 20,
    justifyContent: 'center',
    width: 20,
  },
  radioSelected: {
    borderColor: colors.primary,
  },
  radioDot: {
    backgroundColor: colors.primary,
    borderRadius: 5,
    height: 10,
    width: 10,
  },
  info: {
    flex: 1,
  },
  name: {
    color: colors.text,
    fontSize: 15,
    fontWeight: '700',
  },
  nameSelected: {
    color: colors.primary,
  },
  phone: {
    color: colors.muted,
    fontSize: 13,
    marginTop: 2,
  },
  emptyBox: {
    backgroundColor: colors.surface,
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 8,
    padding: 16,
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
  },
});

export function MissionCreateScreen({ navigation }: Props) {
  const { createMission, linkedChildren, parentCreditBalance } = useAppState();
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
        error={amount > parentCreditBalance ? '보상 크레딧보다 큰 금액은 등록할 수 없습니다.' : undefined}
        disabled={loading}
      />
      <PrimaryButton title={loading ? '등록 중' : '미션 등록'} onPress={submit} disabled={!valid || loading} loading={loading} />
    </ScreenFrame>
  );
}
