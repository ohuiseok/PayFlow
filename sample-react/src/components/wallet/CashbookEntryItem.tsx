import { StyleSheet, Text, View } from 'react-native';

import { Card, colors, formatWon } from '../common';
import { CashbookEntry } from '../../types';

export function CashbookEntryItem({ entry }: { entry: CashbookEntry }) {
  return (
    <Card>
      <View style={styles.rowBetween}>
        <View>
          <Text style={styles.itemTitle}>{entry.title}</Text>
          <Text style={styles.itemMeta}>{entry.description}</Text>
        </View>
        <Text style={[styles.amount, entry.amount < 0 && styles.negative]}>
          {entry.amount > 0 ? '+' : ''}
          {formatWon(entry.amount)}
        </Text>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  rowBetween: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 12,
    justifyContent: 'space-between',
  },
  itemTitle: {
    color: colors.text,
    fontSize: 17,
    fontWeight: '900',
  },
  itemMeta: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 21,
    marginTop: 6,
  },
  amount: {
    color: colors.primary,
    fontSize: 16,
    fontWeight: '900',
  },
  negative: {
    color: colors.danger,
  },
});
