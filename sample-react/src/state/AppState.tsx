import { createContext, PropsWithChildren, useContext, useMemo, useState } from 'react';

import { BankAccount, CashbookEntry, LinkedChild, Mission, UserRole } from '../types';

const defaultParentName = '지수';
const defaultChildName = '민지';

const dummyLinkedChildren: LinkedChild[] = [
  { childUserId: 'child-001', childName: '민지', phoneNumber: '010-1234-5678' },
  { childUserId: 'child-002', childName: '서연', phoneNumber: '010-8765-4321' },
];

const initialMissions: Mission[] = [
  {
    id: 'mission-001',
    childId: 'child-001',
    childName: defaultChildName,
    title: '수학 문제집 3쪽 풀기',
    description: '오늘 배운 단원 문제를 차분히 풀고 사진을 올려주세요.',
    rewardAmount: 3000,
    dueDate: '2026-06-12',
    status: 'todo',
  },
  {
    id: 'mission-002',
    childId: 'child-001',
    childName: defaultChildName,
    title: '방 청소하기',
    description: '책상 위와 침대 주변을 정리하고 완료 사진을 제출하세요.',
    rewardAmount: 2000,
    dueDate: '2026-06-14',
    status: 'submitted',
    submitMemo: '책상과 침대 아래까지 정리했어요.',
  },
  {
    id: 'mission-003',
    childId: 'child-001',
    childName: defaultChildName,
    title: '분리수거 버리기',
    description: '분리수거함을 비우고 현관 앞을 정리하세요.',
    rewardAmount: 2000,
    dueDate: '2026-06-20',
    status: 'rejected',
    submitMemo: '비닐을 따로 모아뒀어요.',
    rejectReason: '종이류가 아직 남아 있어요. 한 번만 더 확인해 주세요.',
  },
];

const initialCashbook: CashbookEntry[] = [
  {
    id: 'cash-001',
    title: '지난주 독서 미션',
    description: '미션 보상 지급 완료',
    amount: 7000,
    type: 'reward',
  },
  {
    id: 'cash-002',
    title: '간식 사기',
    description: '사용 기록',
    amount: -1500,
    type: 'withdrawal',
  },
];

const initialParentCreditEntries: CashbookEntry[] = [
  {
    id: 'parent-credit-001',
    title: '보상 크레딧 충전',
    description: '국민은행 1234-56-789012',
    amount: 50000,
    type: 'charge',
  },
  {
    id: 'parent-credit-002',
    title: '지난주 독서 미션',
    description: `${defaultChildName}에게 보상 지급`,
    amount: -7000,
    type: 'reward',
  },
];

const parentChargeAccount: BankAccount = {
  bankName: '국민은행',
  accountNumber: '1234-56-789012',
  holderName: defaultParentName,
};

type AppStateValue = {
  role: UserRole | null;
  currentUserId: string;
  currentUserName: string;
  familyLinked: boolean;
  inviteCode: string;
  linkedChildren: LinkedChild[];
  parentCreditBalance: number;
  parentChargeAccount: BankAccount;
  parentCreditEntries: CashbookEntry[];
  childCashBalance: number;
  linkedBankAccount: BankAccount | null;
  missions: Mission[];
  cashbookEntries: CashbookEntry[];
  loginAs: (role: UserRole, name?: string, userId?: string) => void;
  signupAs: (role: UserRole, name: string, userId?: string) => void;
  completeFamilyLink: () => void;
  chargeCredit: (amount: number) => void;
  createMission: (mission: Omit<Mission, 'id' | 'childId' | 'childName' | 'status'>) => void;
  submitMission: (missionId: string, memo: string) => void;
  resubmitMission: (missionId: string, memo: string) => void;
  approveMission: (missionId: string) => boolean;
  rejectMission: (missionId: string, reason: string) => void;
  registerBankAccount: (account: BankAccount) => void;
  withdrawCash: (amount: number) => boolean;
};

const AppStateContext = createContext<AppStateValue | null>(null);

export function AppStateProvider({ children }: PropsWithChildren) {
  const [role, setRole] = useState<UserRole | null>(null);
  const [currentUserId, setCurrentUserId] = useState('1');
  const [currentUserName, setCurrentUserName] = useState(defaultParentName);
  const [familyLinked, setFamilyLinked] = useState(false);
  const [parentCreditBalance, setParentCreditBalance] = useState(85000);
  const [parentCreditEntries, setParentCreditEntries] = useState<CashbookEntry[]>(initialParentCreditEntries);
  const [childCashBalance, setChildCashBalance] = useState(17000);
  const [linkedBankAccount, setLinkedBankAccount] = useState<BankAccount | null>(null);
  const [missions, setMissions] = useState<Mission[]>(initialMissions);
  const [cashbookEntries, setCashbookEntries] = useState<CashbookEntry[]>(initialCashbook);

  const value = useMemo<AppStateValue>(
    () => ({
      role,
      currentUserId,
      currentUserName,
      familyLinked,
      inviteCode: 'PF-4829',
      linkedChildren: dummyLinkedChildren,
      parentCreditBalance,
      parentChargeAccount,
      parentCreditEntries,
      childCashBalance,
      linkedBankAccount,
      missions,
      cashbookEntries,
      loginAs(nextRole, name, userId) {
        setRole(nextRole);
        setCurrentUserId(userId ?? (nextRole === 'parent' ? '1' : '2'));
        setCurrentUserName(name?.trim() || (nextRole === 'parent' ? defaultParentName : defaultChildName));
      },
      signupAs(nextRole, name, userId) {
        setRole(nextRole);
        setCurrentUserId(userId ?? (nextRole === 'parent' ? '1' : '2'));
        setCurrentUserName(name.trim() || (nextRole === 'parent' ? defaultParentName : defaultChildName));
      },
      completeFamilyLink() {
        setFamilyLinked(true);
      },
      chargeCredit(amount) {
        setParentCreditBalance((balance) => balance + amount);
        setParentCreditEntries((items) => [
          {
            id: `parent-credit-${Date.now()}`,
            title: '보상 크레딧 충전',
            description: `${parentChargeAccount.bankName} ${parentChargeAccount.accountNumber}`,
            amount,
            type: 'charge',
          },
          ...items,
        ]);
      },
      createMission(mission) {
        setMissions((items) => [
          {
            ...mission,
            id: `mission-${Date.now()}`,
            childId: 'child-001',
            childName: defaultChildName,
            status: 'todo',
          },
          ...items,
        ]);
      },
      submitMission(missionId, memo) {
        setMissions((items) =>
          items.map((item) =>
            item.id === missionId ? { ...item, status: 'submitted', submitMemo: memo } : item,
          ),
        );
      },
      resubmitMission(missionId, memo) {
        setMissions((items) =>
          items.map((item) =>
            item.id === missionId
              ? { ...item, status: 'submitted', submitMemo: memo, rejectReason: undefined }
              : item,
          ),
        );
      },
      approveMission(missionId) {
        const mission = missions.find((item) => item.id === missionId);
        if (!mission || parentCreditBalance < mission.rewardAmount) {
          return false;
        }

        setParentCreditBalance((balance) => balance - mission.rewardAmount);
        setParentCreditEntries((items) => [
          {
            id: `parent-credit-${Date.now()}`,
            title: mission.title,
            description: `${mission.childName}에게 보상 지급`,
            amount: -mission.rewardAmount,
            type: 'reward',
          },
          ...items,
        ]);
        setChildCashBalance((balance) => balance + mission.rewardAmount);
        setMissions((items) =>
          items.map((item) => (item.id === missionId ? { ...item, status: 'paid' } : item)),
        );
        setCashbookEntries((items) => [
          {
            id: `cash-${Date.now()}`,
            title: mission.title,
            description: '미션 보상 지급 완료',
            amount: mission.rewardAmount,
            type: 'reward',
          },
          ...items,
        ]);
        return true;
      },
      rejectMission(missionId, reason) {
        setMissions((items) =>
          items.map((item) =>
            item.id === missionId
              ? { ...item, status: 'rejected', rejectReason: reason || '보완이 필요해요.' }
              : item,
          ),
        );
      },
      registerBankAccount(account) {
        setLinkedBankAccount(account);
      },
      withdrawCash(amount) {
        if (amount > childCashBalance) {
          return false;
        }

        setChildCashBalance((balance) => balance - amount);
        setCashbookEntries((items) => [
          {
            id: `cash-${Date.now()}`,
            title: '계좌 출금',
            description: linkedBankAccount
              ? `${linkedBankAccount.bankName} ${linkedBankAccount.accountNumber}`
              : '등록 계좌',
            amount: -amount,
            type: 'withdrawal',
          },
          ...items,
        ]);
        return true;
      },
    }),
    [
      cashbookEntries,
      childCashBalance,
      currentUserId,
      currentUserName,
      familyLinked,
      linkedBankAccount,
      missions,
      parentCreditBalance,
      parentCreditEntries,
      role,
    ],
  );

  return <AppStateContext.Provider value={value}>{children}</AppStateContext.Provider>;
}

export function useAppState() {
  const value = useContext(AppStateContext);
  if (!value) {
    throw new Error('useAppState must be used inside AppStateProvider');
  }
  return value;
}
