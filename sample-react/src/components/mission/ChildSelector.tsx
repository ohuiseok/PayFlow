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
        <Text style={styles.emptyText}>연결된 자녀가 없습니다. 먼저 자녀를 연결해 주세요.</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.label}>대상 자녀</Text>
      {children.map((child) => {
        const selected = String(child.childUserId) === String(selectedId);
        return (
          <TouchableOpacity
            key={String(child.childUserId)}
            style={[styles.row, selected && styles.rowSelected]}
            onPress={() => onSelect(child)}
            activeOpacity={0.7}
          >
            <View style={[styles.radio, selected && styles.radioSelected]}>
              {selected ? <View style={styles.radioDot} /> : null}
            </View>
            <View style={styles.info}>
              <Text style={[styles.name, selected && styles.nameSelected]}>{child.childName}</Text>
            </View>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
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
