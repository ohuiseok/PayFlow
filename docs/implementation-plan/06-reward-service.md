# 06. Reward Service

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

reward-service는 기관-청년 참여자 연결, 미션, 지원금 지급을 담당한다.

## Scope

기관-청년 참여자 연결

미션 생성

미션 제출

미션 승인

지원금 지급

청년 지원금 사용 내역 조회

## APIs

### POST /families/links

기관 담당자가 청년 참여자와 연결한다.

요청:

`childUserId`

처리:

1. 요청자가 기관 담당자 역할인지 확인한다.
2. 대상 사용자가 청년 참여자 역할인지 확인한다.
3. `parent_child_links`를 `ACTIVE`로 생성한다.

### GET /families/children

기관 담당자에게 연결된 청년 참여자 목록을 반환한다.

### POST /missions

기관 담당자가 청년 참여자에게 미션을 생성한다.

요청:

`childUserId`, `title`, `description`, `rewardAmount`

### PATCH /missions/{missionId}/submit

청년이 미션을 제출한다.

상태:

`CREATED` -> `SUBMITTED`

### PATCH /missions/{missionId}/approve

기관 담당자가 미션을 승인한다.

상태:

`SUBMITTED` -> `APPROVED`

### POST /missions/{missionId}/pay

승인된 미션의 보상을 지급한다.

처리:

1. 미션이 `APPROVED`인지 확인한다.
2. 기관 담당자와 청년 참여자 연결이 활성 상태인지 확인한다.
3. transfer-service에 송금을 요청한다.
4. 멱등키는 `reward-payment-{missionId}`를 사용한다.
5. 성공 시 `paid_transfer_id`를 저장하고 `PAID`로 변경한다.

### GET /cashbook/children/{childUserId}

지급 완료 미션과 지갑 거래 이력을 기반으로 청년 지원금 수령 내역을 반환한다.

## Status

`CREATED`

`SUBMITTED`

`APPROVED`

`PAID`

`REJECTED`

`CANCELED`

## Tests

기관-청년 참여자 연결 성공

기관 담당자가 아닌 사용자의 연결 요청 실패

미션 생성 성공

청년 정책 미션 제출 성공

기관 정책 미션 승인 성공

승인 전 지원금 지급 실패

지원금 지급 성공

지원금 지급 재시도 시 중복 송금 없음

청년 지원금 사용 내역 조회 성공


