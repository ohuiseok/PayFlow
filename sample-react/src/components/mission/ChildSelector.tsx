import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { colors } from '../common';

export type ChildOption = {
  childUserId: string | number;
  childName: string;
  phoneNumber: string;
};

export function ChildSelector({
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
      <View style={styles.emptyBox}>
        <Text style={styles.emptyText}>연결된 청년 참여자가 없습니다. 먼저 참여자를 연결해 주세요.</Text>
      </View>
    );
  }

  if (children.length === 1) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text style={styles.label}>대상 청년</Text>
      <View style={styles.segmentWrap}>
        {children.map((child, index) => {
          const isSelected = String(child.childUserId) === String(selectedId);
          const isFirst = index === 0;
          const isLast = index === children.length - 1;
          return (
            <TouchableOpacity
              key={String(child.childUserId)}
              activeOpacity={0.75}
              onPress={() => onSelect(child)}
              style={[
                styles.segment,
                isFirst && styles.segmentFirst,
                isLast && styles.segmentLast,
                isSelected && styles.segmentSelected,
              ]}
            >
              <Text style={[styles.segmentText, isSelected && styles.segmentTextSelected]}>
                {child.childName}
              </Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 20,
  },
  label: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.3,
    marginBottom: 10,
    textTransform: 'uppercase',
  },
  segmentWrap: {
    backgroundColor: '#e9efff',
    borderRadius: 10,
    flexDirection: 'row',
    padding: 3,
  },
  segment: {
    alignItems: 'center',
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    paddingVertical: 10,
  },
  segmentFirst: {},
  segmentLast: {},
  segmentSelected: {
    backgroundColor: colors.surface,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 2,
    elevation: 1,
  },
  segmentText: {
    color: colors.muted,
    fontSize: 15,
    fontWeight: '700',
  },
  segmentTextSelected: {
    color: colors.text,
    fontWeight: '900',
  },
  emptyBox: {
    backgroundColor: colors.surface,
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 16,
    padding: 16,
  },
  emptyText: {
    color: colors.muted,
    fontSize: 14,
  },
});
