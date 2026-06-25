import { createContext, PropsWithChildren, useContext, useEffect, useMemo, useState } from 'react';

import { authApi } from '../api/authApi';
import { familyApi } from '../api/familyApi';
import { tokenStorage } from '../storage/tokenStorage';
import { appConfig } from '../config/appConfig';
import { BankAccount, CashbookEntry, LinkedChild, Mission, UserRole } from '../types';

const DUMMY_ROLE_KEY = 'payflow_dummy_role';

const defaultParentName = '서울청년센터';
const defaultChildName = '김민지';

const dummyLinkedChildren: LinkedChild[] = [
  { childUserId: 'child-001', childName: '김민지', phoneNumber: '010-1234-5678' },
  { childUserId: 'child-002', childName: '이서연', phoneNumber: '010-8765-4321' },
];

const initialMissions: Mission[] = [
  {
    id: 'mission-001',
    childId: 'child-001',
    childName: defaultChildName,
    title: '청년 금융 교육 참여',
    description: '온라인 금융 교육을 수강하고 수료 화면을 제출해 주세요.',
    rewardAmount: 5000,
    dueDate: '2026-06-12',
    status: 'todo',
  },
  {
    id: 'mission-002',
    childId: 'child-001',
    childName: defaultChildName,
    title: '정책 설문 응답',
    description: '청년 주거 정책 설문에 참여하고 완료 화면을 제출해 주세요.',
    rewardAmount: 3000,
    dueDate: '2026-06-14',
    status: 'submitted',
    submitMemo: '설문 응답을 완료했고 완료 화면을 첨부했습니다.',
  },
  {
    id: 'mission-003',
    childId: 'child-001',
    childName: defaultChildName,
    title: '취업 상담 후기 제출',
    description: '취업 상담 참여 후기를 100자 이상 작성해 주세요.',
    rewardAmount: 7000,
    dueDate: '2026-06-20',
    status: 'rejected',
    submitMemo: '상담을 다녀왔습니다.',
    rejectReason: '후기 내용이 부족합니다. 상담에서 얻은 내용을 조금 더 적어주세요.',
  },
];

const initialCashbook: CashbookEntry[] = [
  {
    id: 'cash-001',
    title: '청년 정책 참여 지원금',
    description: '정책 미션 지원금 지급 완료',
    amount: 7000,
    type: 'reward',
  },
  {
    id: 'cash-002',
    title: '계좌 출금',
    description: '지원금 사용 기록',
    amount: -1500,
    type: 'withdrawal',
  },
];

const initialParentCreditEntries: CashbookEntry[] = [
  {
    id: 'parent-credit-001',
    title: '지원금 예산 충전',
    description: '국민은행 1234-56-789012',
    amount: 50000,
    type: 'charge',
  },
  {
    id: 'parent-credit-002',
    title: '청년 정책 참여 지원금',
    description: `${defaultChildName}에게 지원금 지급`,
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
  isRestoringSession: boolean;
  role: UserRole | null;
  currentUserId: string;
  currentUserName: string;
  hasBankAccount: boolean;
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
  logout: () => Promise<void>;
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
  markBankAccountRegistered: () => void;
};

const AppStateContext = createContext<AppStateValue | null>(null);

export function AppStateProvider({ children }: PropsWithChildren) {
  const [isRestoringSession, setIsRestoringSession] = useState(true);
  const [role, setRole] = useState<UserRole | null>(null);
  const [currentUserId, setCurrentUserId] = useState('1');
  const [currentUserName, setCurrentUserName] = useState(defaultParentName);
  const [hasBankAccount, setHasBankAccount] = useState(false);
  const [familyLinked, setFamilyLinked] = useState(false);
  const [parentCreditBalance, setParentCreditBalance] = useState(0);
  const [parentCreditEntries, setParentCreditEntries] = useState<CashbookEntry[]>(initialParentCreditEntries);
  const [childCashBalance, setChildCashBalance] = useState(17000);
  const [linkedBankAccount, setLinkedBankAccount] = useState<BankAccount | null>(null);
  const [missions, setMissions] = useState<Mission[]>(initialMissions);
  const [cashbookEntries, setCashbookEntries] = useState<CashbookEntry[]>(initialCashbook);

  useEffect(() => {
    let cancelled = false;

    async function restoreSession() {
      if (appConfig.useDummyData) {
        const savedRole = localStorage.getItem(DUMMY_ROLE_KEY) as UserRole | null;
        if (!cancelled) {
          setRole(savedRole);
          if (savedRole === 'child') setFamilyLinked(true);
          setIsRestoringSession(false);
        }
        return;
      }

      try {
        const token = await tokenStorage.getAccessToken();
        if (token) {
          const user = await authApi.me();
          if (!cancelled) {
            setRole(user.role);
            setCurrentUserId(user.userId);
            setCurrentUserName(user.name);
            setHasBankAccount(user.hasBankAccount ?? false);
          }

          if (user.role === 'child') {
            try {
              const family = await familyApi.getMyParents();
              if (!cancelled) {
                setFamilyLinked(family.linked);
              }
            } catch (familyError) {
              console.error('참여자 연결 상태를 복원하지 못했습니다.', familyError);
            }
          }
        }
      } catch {
        await tokenStorage.clearAccessToken();
      } finally {
        if (!cancelled) {
          setIsRestoringSession(false);
        }
      }
    }

    restoreSession();
    return () => { cancelled = true; };
  }, []);

  const value = useMemo<AppStateValue>(
    () => ({
      isRestoringSession,
      role,
      currentUserId,
      currentUserName,
      hasBankAccount,
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
        if (appConfig.useDummyData) {
          localStorage.setItem(DUMMY_ROLE_KEY, nextRole);
          if (nextRole === 'child') setFamilyLinked(true);
        }
      },
      async logout() {
        if (appConfig.useDummyData) {
          localStorage.removeItem(DUMMY_ROLE_KEY);
        }
        try {
          await authApi.logout();
        } catch {
          await tokenStorage.clearAccessToken();
        }
        setRole(null);
        setCurrentUserId('1');
        setCurrentUserName(defaultParentName);
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
            title: '지원금 예산 충전',
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
            description: `${mission.childName}에게 지원금 지급`,
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
            description: '정책 미션 지원금 지급 완료',
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
      markBankAccountRegistered() {
        setHasBankAccount(true);
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
      hasBankAccount,
      isRestoringSession,
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
