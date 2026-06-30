# Screen Implementation Order

`sample-react`는 PayFlow MVP 흐름을 보여주는 샘플 프론트다.

화면은 API가 모두 준비되기 전에도 더미 데이터로 먼저 완성하고, 마지막에 `docs/api-spec.md`의 MVP API에 연결한다.

## MVP Screens

1. Login
2. Signup Role
3. Family Link
4. Child Link Input
5. Parent Home
6. Bank Account Register
7. Parent Deposit
8. Mission Create
9. Child Home
10. Mission Submit
11. Parent Approval
12. Cashbook Summary

## Data Rules

역할은 `PARENT`, `CHILD`만 사용한다.

금액은 정수 원화 단위로 표시한다.

충전, 송금, 보상 지급 요청 버튼은 처리 중 중복 클릭을 막는다.

미션 상태는 `CREATED`, `SUBMITTED`, `APPROVED`, `PAID`, `REJECTED`, `CANCELED`만 사용한다.

## API Mapping

| Screen | API |
| --- | --- |
| Login | `POST /api/users/login` |
| Signup Role | `POST /api/users` |
| My Profile | `GET /api/users/me` |
| Wallet Summary | `GET /api/wallets/me` |
| Wallet History | `GET /api/wallets/me/transactions` |
| Bank Account Register | `GET /api/bank/accounts`, `POST /api/bank/accounts` |
| Parent Deposit | `POST /api/bank/deposits`, `GET /api/bank/transfers/{bankingTransferId}` |
| Family Link | `POST /api/families/links`, `GET /api/families/children` |
| Mission Create | `POST /api/missions` |
| Mission List | `GET /api/missions` |
| Mission Detail | `GET /api/missions/{missionId}` |
| Mission Submit | `PATCH /api/missions/{missionId}/submit` |
| Parent Approval | `PATCH /api/missions/{missionId}/approve`, `PATCH /api/missions/{missionId}/reject` |
| Reward Payment | `POST /api/missions/{missionId}/pay` |
| Parent Credit Summary | `GET /api/cashbook/parent/summary` |
| Cashbook | `GET /api/cashbook/children/{childUserId}/summary`, `GET /api/cashbook/children/{childUserId}/entries` |

## Implementation Order

1. 더미 데이터와 화면 navigation을 만든다.
2. 로그인/회원가입 화면을 연결한다.
3. 역할별 홈 화면을 분기한다.
4. 지갑 요약과 거래 이력을 연결한다.
5. 계좌 등록과 충전 화면을 연결한다.
6. 가족 연결 화면을 연결한다.
7. 미션 생성, 제출, 승인, 지급 화면을 연결한다.
8. 자녀 사용 기록 요약을 연결한다.
9. 에러, 로딩, 빈 상태를 정리한다.

## Done Criteria

부모가 가입하고 충전할 수 있다.

부모가 자녀와 연결할 수 있다.

부모가 미션을 만들고 자녀가 제출할 수 있다.

부모가 승인 후 보상을 지급할 수 있다.

자녀 화면에서 잔액과 최근 돈 기록을 확인할 수 있다.

정산 실행/조회는 현재 운영 API이며 sample-react 화면 범위에 포함하지 않는다.
