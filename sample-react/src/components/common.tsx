import { PropsWithChildren } from 'react';
import {
  ActivityIndicator,
  Modal,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

export const colors = {
  background: '#F5F7F8',
  surface: '#FFFFFF',
  text: '#111820',
  muted: '#6B7682',
  line: '#DDE4EA',
  primary: '#248763',
  primarySoft: '#E7F5EE',
  dark: '#20262D',
  blueSoft: '#EAF4FF',
  blue: '#2F6FB2',
  yellowSoft: '#FFF7E7',
  yellow: '#95690A',
  danger: '#D84F45',
};

export function formatWon(amount: number) {
  return `${amount.toLocaleString('ko-KR')}원`;
}

export function parseAmount(value: string) {
  return Number(value.replace(/[^0-9]/g, ''));
}

export function formatAmountInput(value: string) {
  const amount = parseAmount(value);
  return amount ? amount.toLocaleString('ko-KR') : '';
}

type ScreenFrameProps = PropsWithChildren<{
  eyebrow?: string;
  title?: string;
  description?: string;
}>;

export function ScreenFrame({ children, eyebrow, title, description }: ScreenFrameProps) {
  return (
    <ScrollView contentContainerStyle={styles.frame} keyboardShouldPersistTaps="handled">
      {(eyebrow || title || description) && (
        <View style={styles.header}>
          {eyebrow ? <Text style={styles.eyebrow}>{eyebrow}</Text> : null}
          {title ? <Text style={styles.title}>{title}</Text> : null}
          {description ? <Text style={styles.description}>{description}</Text> : null}
        </View>
      )}
      {children}
    </ScrollView>
  );
}

export function Card({ children, tone = 'default' }: PropsWithChildren<{ tone?: 'default' | 'green' | 'blue' | 'dark' | 'yellow' }>) {
  return <View style={[styles.card, styles[`${tone}Card`]]}>{children}</View>;
}

export function Label({ children }: PropsWithChildren) {
  return <Text style={styles.label}>{children}</Text>;
}

export function Heading({ children }: PropsWithChildren) {
  return <Text style={styles.heading}>{children}</Text>;
}

export function Body({ children }: PropsWithChildren) {
  return <Text style={styles.body}>{children}</Text>;
}

export function PrimaryButton({
  title,
  onPress,
  disabled,
  loading,
  testID,
  accessibilityLabel,
  variant = 'primary',
}: {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  testID?: string;
  accessibilityLabel?: string;
  variant?: 'primary' | 'dark';
}) {
  return (
    <TouchableOpacity
      activeOpacity={0.8}
      accessibilityLabel={accessibilityLabel ?? title}
      disabled={disabled || loading}
      onPress={onPress}
      style={[styles.button, variant === 'dark' && styles.darkButton, (disabled || loading) && styles.buttonDisabled]}
      testID={testID}
    >
      {loading ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>{title}</Text>}
    </TouchableOpacity>
  );
}

export function SecondaryButton({
  title,
  onPress,
  testID,
  accessibilityLabel,
}: {
  title: string;
  onPress: () => void;
  testID?: string;
  accessibilityLabel?: string;
}) {
  return (
    <TouchableOpacity
      activeOpacity={0.8}
      accessibilityLabel={accessibilityLabel ?? title}
      onPress={onPress}
      style={styles.secondaryButton}
      testID={testID}
    >
      <Text style={styles.secondaryButtonText}>{title}</Text>
    </TouchableOpacity>
  );
}

export function FormField({
  label,
  value,
  onChangeText,
  placeholder,
  secureTextEntry,
  keyboardType,
  error,
  disabled,
}: {
  label?: string;
  value: string;
  onChangeText: (value: string) => void;
  placeholder: string;
  secureTextEntry?: boolean;
  keyboardType?: 'default' | 'number-pad' | 'phone-pad';
  error?: string;
  disabled?: boolean;
}) {
  return (
    <View style={styles.fieldWrap}>
      {label ? <Text style={styles.fieldLabel}>{label}</Text> : null}
      <TextInput
        keyboardType={keyboardType}
        editable={!disabled}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor="#8792A0"
        secureTextEntry={secureTextEntry}
        style={[styles.input, disabled && styles.inputDisabled, error && styles.inputError]}
        value={value}
      />
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

export function StatusBadge({ label, tone = 'green' }: { label: string; tone?: 'green' | 'blue' | 'yellow' | 'danger' }) {
  return (
    <View style={[styles.badge, styles[`${tone}Badge`]]}>
      <Text style={[styles.badgeText, styles[`${tone}BadgeText`]]}>{label}</Text>
    </View>
  );
}

export function BalanceCard({ label, amount, description }: { label: string; amount: number; description: string }) {
  return (
    <Card tone="dark">
      <Text style={styles.darkLabel}>{label}</Text>
      <Text style={styles.balance}>{formatWon(amount)}</Text>
      <Text style={styles.darkBody}>{description}</Text>
    </Card>
  );
}

export function AmountQuickSelect({ amounts, onSelect }: { amounts: number[]; onSelect: (amount: number) => void }) {
  return (
    <View style={styles.quickRow}>
      {amounts.map((amount) => (
        <TouchableOpacity key={amount} activeOpacity={0.75} onPress={() => onSelect(amount)} style={styles.quickChip}>
          <Text style={styles.quickChipText}>{formatWon(amount)}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );
}

export function Toast({
  message,
  tone = 'green',
}: {
  message: string;
  tone?: 'green' | 'yellow' | 'danger';
}) {
  if (!message) {
    return null;
  }

  return (
    <View style={[styles.toast, styles[`${tone}Toast`]]}>
      <Text style={[styles.toastText, styles[`${tone}ToastText`]]}>{message}</Text>
    </View>
  );
}

export function ConfirmModal({
  visible,
  title,
  body,
  confirmTitle = '진행',
  cancelTitle = '취소',
  loading,
  onConfirm,
  onCancel,
}: {
  visible: boolean;
  title: string;
  body: string;
  confirmTitle?: string;
  cancelTitle?: string;
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  return (
    <Modal animationType="fade" transparent visible={visible} onRequestClose={onCancel}>
      <View style={styles.modalBackdrop}>
        <View style={styles.modalPanel}>
          <Text style={styles.modalTitle}>{title}</Text>
          <Text style={styles.modalBody}>{body}</Text>
          <View style={styles.modalActions}>
            <TouchableOpacity activeOpacity={0.8} onPress={onCancel} style={styles.modalCancelButton}>
              <Text style={styles.modalCancelText}>{cancelTitle}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              activeOpacity={0.8}
              disabled={loading}
              onPress={onConfirm}
              style={[styles.modalConfirmButton, loading && styles.buttonDisabled]}
            >
              {loading ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.modalConfirmText}>{confirmTitle}</Text>}
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  frame: {
    alignSelf: 'center',
    backgroundColor: colors.background,
    maxWidth: 430,
    minHeight: '100%',
    padding: 24,
    paddingBottom: 40,
    width: '100%',
    ...Platform.select({
      web: { minHeight: 820 },
      default: {},
    }),
  },
  header: {
    marginBottom: 28,
    paddingTop: 28,
  },
  eyebrow: {
    color: colors.muted,
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 14,
  },
  title: {
    color: colors.text,
    fontSize: 34,
    fontWeight: '900',
    letterSpacing: 0,
    lineHeight: 43,
  },
  description: {
    color: colors.muted,
    fontSize: 16,
    lineHeight: 25,
    marginTop: 10,
  },
  card: {
    backgroundColor: colors.surface,
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginBottom: 16,
    padding: 20,
  },
  defaultCard: {},
  greenCard: {
    backgroundColor: colors.primarySoft,
    borderColor: '#BFE8D4',
  },
  blueCard: {
    backgroundColor: colors.blueSoft,
    borderColor: '#C6DDF6',
  },
  yellowCard: {
    backgroundColor: colors.yellowSoft,
    borderColor: '#F1CC70',
  },
  darkCard: {
    backgroundColor: '#17202A',
    borderColor: '#17202A',
  },
  label: {
    color: colors.primary,
    fontSize: 15,
    fontWeight: '900',
    marginBottom: 10,
  },
  heading: {
    color: colors.text,
    fontSize: 23,
    fontWeight: '900',
    lineHeight: 30,
    marginBottom: 8,
  },
  body: {
    color: colors.muted,
    fontSize: 16,
    lineHeight: 24,
  },
  button: {
    alignItems: 'center',
    backgroundColor: colors.primary,
    borderRadius: 8,
    justifyContent: 'center',
    minHeight: 58,
    paddingHorizontal: 18,
  },
  darkButton: {
    backgroundColor: colors.dark,
  },
  buttonDisabled: {
    opacity: 0.5,
  },
  buttonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '900',
  },
  secondaryButton: {
    alignItems: 'center',
    backgroundColor: '#EEF1F4',
    borderRadius: 8,
    justifyContent: 'center',
    minHeight: 58,
    paddingHorizontal: 18,
  },
  secondaryButtonText: {
    color: colors.dark,
    fontSize: 18,
    fontWeight: '900',
  },
  fieldWrap: {
    marginBottom: 14,
  },
  fieldLabel: {
    color: colors.dark,
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 8,
  },
  input: {
    backgroundColor: '#FBFCFD',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    color: colors.text,
    fontSize: 17,
    minHeight: 58,
    paddingHorizontal: 16,
  },
  inputDisabled: {
    backgroundColor: '#EEF1F4',
    color: colors.muted,
  },
  inputError: {
    borderColor: colors.danger,
  },
  error: {
    color: colors.danger,
    fontSize: 13,
    fontWeight: '700',
    marginTop: 6,
  },
  badge: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  greenBadge: { backgroundColor: colors.primarySoft },
  blueBadge: { backgroundColor: colors.blueSoft },
  yellowBadge: { backgroundColor: colors.yellowSoft },
  dangerBadge: { backgroundColor: '#FDEEEE' },
  badgeText: {
    fontSize: 12,
    fontWeight: '900',
  },
  greenBadgeText: { color: colors.primary },
  blueBadgeText: { color: colors.blue },
  yellowBadgeText: { color: colors.yellow },
  dangerBadgeText: { color: colors.danger },
  darkLabel: {
    color: '#B7C1CB',
    fontSize: 15,
    fontWeight: '800',
    marginBottom: 10,
  },
  balance: {
    color: '#FFFFFF',
    fontSize: 36,
    fontWeight: '900',
    marginBottom: 8,
  },
  darkBody: {
    color: '#B7C1CB',
    fontSize: 15,
  },
  infoTitle: {
    color: colors.primary,
    fontSize: 18,
    fontWeight: '900',
    marginBottom: 10,
  },
  infoBody: {
    color: colors.dark,
    fontSize: 18,
    fontWeight: '800',
    lineHeight: 27,
  },
  blueText: {
    color: colors.blue,
  },
  yellowText: {
    color: colors.yellow,
  },
  quickRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 16,
  },
  quickChip: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    flex: 1,
    minHeight: 46,
    justifyContent: 'center',
  },
  quickChipText: {
    color: colors.dark,
    fontSize: 14,
    fontWeight: '900',
  },
  toast: {
    borderRadius: 8,
    marginBottom: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  greenToast: {
    backgroundColor: colors.primarySoft,
  },
  yellowToast: {
    backgroundColor: colors.yellowSoft,
  },
  dangerToast: {
    backgroundColor: '#FDEEEE',
  },
  toastText: {
    fontSize: 14,
    fontWeight: '900',
  },
  greenToastText: {
    color: colors.primary,
  },
  yellowToastText: {
    color: colors.yellow,
  },
  dangerToastText: {
    color: colors.danger,
  },
  modalBackdrop: {
    alignItems: 'center',
    backgroundColor: 'rgba(17, 24, 32, 0.42)',
    flex: 1,
    justifyContent: 'center',
    padding: 24,
  },
  modalPanel: {
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    maxWidth: 380,
    padding: 22,
    width: '100%',
  },
  modalTitle: {
    color: colors.text,
    fontSize: 22,
    fontWeight: '900',
    lineHeight: 29,
    marginBottom: 8,
  },
  modalBody: {
    color: colors.muted,
    fontSize: 16,
    lineHeight: 24,
    marginBottom: 20,
  },
  modalActions: {
    flexDirection: 'row',
    gap: 10,
  },
  modalCancelButton: {
    alignItems: 'center',
    backgroundColor: '#EEF1F4',
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    minHeight: 50,
  },
  modalConfirmButton: {
    alignItems: 'center',
    backgroundColor: colors.primary,
    borderRadius: 8,
    flex: 1,
    justifyContent: 'center',
    minHeight: 50,
  },
  modalCancelText: {
    color: colors.dark,
    fontSize: 16,
    fontWeight: '900',
  },
  modalConfirmText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '900',
  },
});
