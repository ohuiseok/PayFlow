import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { colors } from '../common';
import { BankOption, bankOptions } from '../../constants/banks';

export function BankSelect({
  selectedBankCode,
  onSelect,
  disabled,
}: {
  selectedBankCode: string;
  onSelect: (bank: BankOption) => void;
  disabled?: boolean;
}) {
  return (
    <View style={styles.wrap}>
      <Text style={styles.label}>은행</Text>
      <View style={styles.options}>
        {bankOptions.map((bank) => {
          const selected = bank.bankCodeStd === selectedBankCode;

          return (
            <TouchableOpacity
              key={bank.bankCodeStd}
              activeOpacity={0.75}
              disabled={disabled}
              onPress={() => onSelect(bank)}
              style={[styles.option, selected && styles.selectedOption, disabled && styles.disabledOption]}
            >
              <Text style={[styles.optionText, selected && styles.selectedOptionText]}>{bank.bankName}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    marginBottom: 14,
  },
  label: {
    color: colors.dark,
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 8,
  },
  options: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  option: {
    alignItems: 'center',
    backgroundColor: '#FBFCFD',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    minHeight: 44,
    paddingHorizontal: 14,
    justifyContent: 'center',
  },
  selectedOption: {
    backgroundColor: colors.primarySoft,
    borderColor: colors.primary,
  },
  disabledOption: {
    opacity: 0.55,
  },
  optionText: {
    color: colors.dark,
    fontSize: 15,
    fontWeight: '800',
  },
  selectedOptionText: {
    color: colors.primary,
    fontWeight: '900',
  },
});
