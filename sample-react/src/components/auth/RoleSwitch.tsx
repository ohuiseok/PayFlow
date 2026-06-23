import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { colors } from '../common';
import { UserRole } from '../../types';

export function RoleSwitch({ onSelect }: { onSelect: (role: UserRole) => void }) {
  return (
    <View style={styles.switchRow}>
      <TouchableOpacity style={styles.switchButton} onPress={() => onSelect('parent')}>
        <Text style={styles.switchText}>부모로 보기</Text>
      </TouchableOpacity>
      <TouchableOpacity style={styles.switchButton} onPress={() => onSelect('child')}>
        <Text style={styles.switchText}>자녀로 보기</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  switchRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 10,
  },
  switchButton: {
    alignItems: 'center',
    backgroundColor: '#e9efff',
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    minHeight: 44,
  },
  switchText: {
    color: colors.dark,
    fontSize: 13,
    fontWeight: '900',
  },
});
