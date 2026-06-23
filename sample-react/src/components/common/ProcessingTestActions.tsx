import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { colors } from '../common';
import { ProcessingStatus } from '../../types';

const testStatuses: Array<{
  status: Extract<ProcessingStatus, 'failed' | 'unknown' | 'compensationRequired'>;
  label: string;
}> = [
  { status: 'failed', label: '실패' },
  { status: 'unknown', label: '확인 필요' },
  { status: 'compensationRequired', label: '고객센터 확인' },
];

export function ProcessingTestActions({
  disabled,
  onSelect,
}: {
  disabled?: boolean;
  onSelect: (status: Extract<ProcessingStatus, 'failed' | 'unknown' | 'compensationRequired'>) => void;
}) {
  return (
    <View style={styles.wrap}>
      <Text style={styles.label}>처리 상태 테스트</Text>
      <View style={styles.row}>
        {testStatuses.map((item) => (
          <TouchableOpacity
            activeOpacity={0.75}
            disabled={disabled}
            key={item.status}
            onPress={() => onSelect(item.status)}
            style={[styles.chip, disabled && styles.disabled]}
          >
            <Text style={styles.chipText}>{item.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    marginTop: 12,
  },
  label: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: '900',
    marginBottom: 8,
  },
  row: {
    flexDirection: 'row',
    gap: 8,
  },
  chip: {
    alignItems: 'center',
    backgroundColor: '#e9efff',
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    minHeight: 40,
    paddingHorizontal: 8,
  },
  disabled: {
    opacity: 0.5,
  },
  chipText: {
    color: colors.dark,
    fontSize: 12,
    fontWeight: '900',
  },
});
