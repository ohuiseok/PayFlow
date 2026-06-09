import { StyleSheet, Text, TouchableOpacity } from 'react-native';

import { Body, colors, Heading } from '../common';

export function RoleSelectCard({
  label,
  title,
  description,
  selected,
  onPress,
}: {
  label: string;
  title: string;
  description: string;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity activeOpacity={0.8} onPress={onPress} style={[styles.roleCard, selected && styles.roleSelected]}>
      <Text style={styles.roleLabel}>{label}</Text>
      <Heading>{title}</Heading>
      <Body>{description}</Body>
      {selected ? <Text style={styles.check}>✓</Text> : null}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  roleCard: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    padding: 20,
    position: 'relative',
  },
  roleSelected: {
    backgroundColor: colors.primarySoft,
    borderColor: '#BFE8D4',
  },
  roleLabel: {
    color: colors.primary,
    fontSize: 16,
    fontWeight: '900',
    marginBottom: 12,
  },
  check: {
    backgroundColor: colors.primary,
    borderRadius: 999,
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '900',
    height: 28,
    lineHeight: 28,
    position: 'absolute',
    right: 18,
    textAlign: 'center',
    top: 18,
    width: 28,
  },
});
