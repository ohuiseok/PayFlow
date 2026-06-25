export const parentSummary = {
  parentName: '서울청년센터',
  creditBalance: 85000,
  monthlyRewardPaid: 12000,
  pendingApprovals: 1,
};

export const childSummary = {
  childName: '김민지',
  cashBalance: 17000,
  weeklyEarned: 7000,
  completedMissionCount: 3,
};

export const missions = [
  {
    id: 'mission-001',
    childName: '김민지',
    title: '청년 금융 교육 참여',
    rewardAmount: 5000,
    dueLabel: '2026년 6월 5일',
    status: '진행 중',
  },
  {
    id: 'mission-002',
    childName: '이서연',
    title: '정책 설문 응답',
    rewardAmount: 3000,
    dueLabel: '2026년 6월 8일',
    status: '제출',
  },
  {
    id: 'mission-003',
    childName: '김민지',
    title: '취업 상담 후기 제출',
    rewardAmount: 7000,
    dueLabel: '2026년 6월 20일',
    status: '시작 가능',
  },
];

export const cashbookEntries = [
  {
    id: 'cash-001',
    title: '청년 금융 교육 참여',
    description: '정책 미션 지원금 지급 완료',
    amount: 5000,
  },
  {
    id: 'cash-002',
    title: '정책 설문 응답',
    description: '정책 미션 지원금 지급 완료',
    amount: 3000,
  },
  {
    id: 'cash-003',
    title: '계좌 출금',
    description: '지원금 사용 기록',
    amount: -1500,
  },
];

export const flows = [
  {
    title: '기관 지원금 예산 충전',
    description: '오픈뱅킹 또는 Toss 충전 성공 후 기관 지갑 잔액에 반영합니다.',
    stage: '충전',
  },
  {
    title: '정책 미션과 지원금 등록',
    description: '기관 담당자가 청년 참여자에게 수행 과제와 지원금 금액을 보냅니다.',
    stage: '미션',
  },
  {
    title: '승인 후 지원금 지급',
    description: '청년이 완료 제출하면 기관 승인 후 기관 지갑에서 청년 지갑으로 지급합니다.',
    stage: '지원금',
  },
  {
    title: '정책 미션 캘린더',
    description: '기관과 청년이 수행 날짜 기준으로 월별 미션과 지급 상태를 확인합니다.',
    stage: '캘린더',
  },
  {
    title: '청년 지원금 사용 기록',
    description: '청년이 어떤 정책 미션으로 지원금을 받았는지 기록과 잔액을 확인합니다.',
    stage: '기록',
  },
];
