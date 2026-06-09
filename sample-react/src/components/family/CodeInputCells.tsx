import { StyleSheet, Text, View } from 'react-native';

import { colors } from '../common';

export function CodeInputCells({ code }: { code: string }) {
  return (
    <View style={styles.codeRow}>
      {code.padEnd(6, ' ').slice(0, 6).split('').map((char, index) => (
        <View key={`${char}-${index}`} style={styles.codeCell}>
          <Text style={styles.codeText}>{char.trim() || '-'}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  codeRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 22,
  },
  codeCell: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    flex: 1,
    height: 58,
    justifyContent: 'center',
  },
  codeText: {
    color: colors.text,
    fontSize: 24,
    fontWeight: '900',
  },
});
