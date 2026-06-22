# 06. Reward Service

reward-service는 부모-자녀 연결, 미션, 보상 지급을 담당한다.

## Scope

부모-자녀 연결

미션 생성

미션 제출

미션 승인

보상 지급

자녀 사용 기록 조회

## APIs

### POST /families/links

부모가 자녀와 연결한다.

요청:

`childUserId`

처리:

1. 요청자가 부모 역할인지 확인한다.
2. 대상 사용자가 자녀 역할인지 확인한다.
3. `parent_child_links`를 `ACTIVE`로 생성한다.

### GET /families/children

부모에게 연결된 자녀 목록을 반환한다.

### POST /missions

부모가 자녀에게 미션을 생성한다.

요청:

`childUserId`, `title`, `description`, `rewardAmount`

### PATCH /missions/{missionId}/submit

자녀가 미션을 제출한다.

상태:

`CREATED` -> `SUBMITTED`

### PATCH /missions/{missionId}/approve

부모가 미션을 승인한다.

상태:

`SUBMITTED` -> `APPROVED`

### POST /missions/{missionId}/pay

승인된 미션의 보상을 지급한다.

처리:

1. 미션이 `APPROVED`인지 확인한다.
2. 부모와 자녀 연결이 활성 상태인지 확인한다.
3. transfer-service에 송금을 요청한다.
4. 멱등키는 `reward-payment-{missionId}`를 사용한다.
5. 성공 시 `paid_transfer_id`를 저장하고 `PAID`로 변경한다.

### GET /cashbook/children/{childUserId}

지급 완료 미션과 지갑 거래 이력을 기반으로 자녀 수입 내역을 반환한다.

## Status

`CREATED`

`SUBMITTED`

`APPROVED`

`PAID`

`REJECTED`

`CANCELED`

## Tests

부모-자녀 연결 성공

부모가 아닌 사용자의 연결 요청 실패

미션 생성 성공

자녀 미션 제출 성공

부모 미션 승인 성공

승인 전 보상 지급 실패

보상 지급 성공

보상 지급 재시도 시 중복 송금 없음

자녀 사용 기록 조회 성공
