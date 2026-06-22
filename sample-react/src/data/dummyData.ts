export const parentSummary = {
  parentName: '지훈',
  creditBalance: 85000,
  monthlyRewardPaid: 12000,
  pendingApprovals: 1,
};

export const childSummary = {
  childName: '민지',
  cashBalance: 17000,
  weeklyEarned: 7000,
  completedMissionCount: 3,
};

export const missions = [
  {
    id: 'mission-001',
    childName: '민지',
    title: '수학 문제집 3쪽 풀기',
    rewardAmount: 3000,
    dueLabel: '2026년 6월 5일',
    status: '진행 중',
  },
  {
    id: 'mission-002',
    childName: '서연',
    title: '방 청소하기',
    rewardAmount: 2000,
    dueLabel: '2026년 6월 8일',
    status: '제출',
  },
  {
    id: 'mission-003',
    childName: '민지',
    title: '재활용 버리기',
    rewardAmount: 2000,
    dueLabel: '2026년 6월 20일',
    status: '시작 가능',
  },
];

export const cashbookEntries = [
  {
    id: 'cash-001',
    title: '수학 문제집 3쪽',
    description: '미션 보상 지급 완료',
    amount: 3000,
  },
  {
    id: 'cash-002',
    title: '방 청소하기',
    description: '미션 보상 지급 완료',
    amount: 2000,
  },
  {
    id: 'cash-003',
    title: '간식 사기',
    description: '사용 기록 승인 완료',
    amount: -1500,
  },
];

export const flows = [
  {
    title: '부모 크레딧 충전',
    description: '오픈뱅킹 충전 성공 후 부모 지갑 잔액에 반영합니다.',
    stage: '충전',
  },
  {
    title: '미션과 보상 등록',
    description: '부모가 아이에게 할 일과 보상 금액을 걸고 미션을 보냅니다.',
    stage: '미션',
  },
  {
    title: '승인 후 보상 지급',
    description: '아이가 완료 제출하면 부모 승인 후 부모 지갑에서 아이 지갑으로 지급합니다.',
    stage: '보상',
  },
  {
    title: '미션 캘린더',
    description: '부모와 아이가 수행 날짜 기준으로 월별 미션과 지급 상태를 확인합니다.',
    stage: '캘린더',
  },
  {
    title: '아이 사용 기록 기록',
    description: '아이가 어떤 미션으로 돈을 벌었는지 기록과 잔액을 확인합니다.',
    stage: '기록',
  },
];
