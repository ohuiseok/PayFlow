import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { colors, formatWon, StatusBadge } from '../common';
import { Mission, MissionStatus } from '../../types';

const missionStatusLabels: Record<MissionStatus, { label: string; tone: 'green' | 'blue' | 'yellow' | 'danger' }> = {
  todo: { label: '진행 중', tone: 'blue' },
  submitted: { label: '승인 대기', tone: 'yellow' },
  approved: { label: '승인', tone: 'green' },
  rejected: { label: '반려', tone: 'danger' },
  paid: { label: '지급 완료', tone: 'green' },
};

export function MissionCard({
  mission,
  onPress,
  disabled,
}: {
  mission: Mission;
  onPress?: () => void;
  disabled?: boolean;
}) {
  const status = missionStatusLabels[mission.status];
  return (
    <TouchableOpacity
      activeOpacity={onPress && !disabled ? 0.75 : 1}
      disabled={disabled || !onPress}
      onPress={onPress}
      style={[styles.missionCard, disabled && styles.missionCardDisabled]}
    >
      <View style={styles.rowBetween}>
        <Text style={styles.itemTitle}>{mission.title}</Text>
        <Text style={styles.reward}>{formatWon(mission.rewardAmount)}</Text>
      </View>
      <Text style={styles.itemMeta}>
        {mission.childName} · {mission.dueDate}
      </Text>
      <View style={styles.badgeLine}>
        <StatusBadge label={status.label} tone={status.tone} />
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  missionCard: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 12,
    padding: 18,
  },
  missionCardDisabled: {
    opacity: 0.78,
  },
  rowBetween: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    justifyContent: 'space-between',
  },
  itemTitle: {
    color: colors.text,
    flex: 1,
    fontSize: 17,
    fontWeight: '900',
  },
  itemMeta: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 6,
  },
  reward: {
    color: colors.primary,
    fontSize: 15,
    fontWeight: '900',
  },
  badgeLine: {
    marginTop: 12,
  },
});
