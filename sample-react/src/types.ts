export type UserRole = 'parent' | 'child';

export type MissionStatus = 'todo' | 'submitted' | 'approved' | 'rejected' | 'paid';

export type ProcessingStatus =
  | 'idle'
  | 'processing'
  | 'completed'
  | 'failed'
  | 'unknown'
  | 'compensationRequired';

export type Mission = {
  id: string;
  childId: string;
  childName: string;
  title: string;
  description: string;
  rewardAmount: number;
  dueDate: string;
  status: MissionStatus;
  submitMemo?: string;
  rejectReason?: string;
};

export type CashbookEntry = {
  id: string;
  title: string;
  description: string;
  amount: number;
  type: 'reward' | 'withdrawal' | 'charge';
};

export type BankAccount = {
  bankName: string;
  accountNumber: string;
  holderName: string;
};

export type LinkedChild = {
  childUserId: string | number;
  childName: string;
  phoneNumber: string;
};
