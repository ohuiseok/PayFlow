export function onlyDigits(value: string) {
  return value.replace(/[^0-9]/g, '');
}

export function onlyAlphaNumeric(value: string) {
  return value.replace(/[^A-Za-z0-9]/g, '');
}

export function hasMinLength(value: string, minLength: number) {
  return value.trim().length >= minLength;
}

export function isValidPhoneNumber(value: string) {
  return onlyDigits(value).length >= 10;
}

export function isValidPassword(value: string) {
  return value.length >= 8;
}

export function isValidInviteCode(value: string) {
  return onlyAlphaNumeric(value).length === 6;
}

export function isValidBankAccountNumber(value: string) {
  return /^\d{10,14}$/.test(onlyDigits(value));
}

export function isAmountInRange(amount: number, min: number, max: number) {
  return amount >= min && amount <= max;
}
