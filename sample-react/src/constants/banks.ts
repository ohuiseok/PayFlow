export type BankOption = {
  bankCodeStd: string;
  bankName: string;
};

export const bankOptions: BankOption[] = [
  { bankCodeStd: '004', bankName: '국민은행' },
  { bankCodeStd: '088', bankName: '신한은행' },
  { bankCodeStd: '020', bankName: '우리은행' },
  { bankCodeStd: '081', bankName: '하나은행' },
  { bankCodeStd: '011', bankName: '농협은행' },
];

export function findBankOptionByName(bankName: string) {
  return bankOptions.find((option) => option.bankName === bankName.trim()) ?? bankOptions[0];
}
