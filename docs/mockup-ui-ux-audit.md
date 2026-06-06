# 목업 UI/UX 점검

점검 기준일: 2026-06-06

## 자산 정리 결과

필수 화면만 남기도록 `sample-react/assets/mockups/screens`의 원본 SVG와 `sample-react/assets/mockups/rendered`의 화면 PNG를 정리했습니다.

```text
원본 화면 SVG: 13개
렌더 화면 PNG: 13개
누락된 렌더 PNG: 없음
원본 없는 고아 렌더 PNG: 없음
```

삭제한 화면:

```text
08-cashbook
09-parent-history
12-mission-detail-status
14-charge-result
15-notifications
16-settings-profile
18-family-connected
19-mission-calendar
```

유지하는 파생 이미지:

- `rendered/contact-sheet.png`: 전체 화면을 한 번에 검토하는 컨택트 시트
- `rendered/payflow-mvp-flow.png`: 실제 목업 PNG를 연결한 MVP 플로우 이미지

## 남긴 필수 화면

| 번호 | 파일 | 역할 |
|---|---|---|
| 01 | `01-login.svg` | 로그인 |
| 02 | `02-signup-role.svg` | 회원가입/역할 선택 |
| 03 | `03-family-link.svg` | 부모 초대 코드/연결 승인 |
| 04 | `04-child-invite-code.svg` | 자녀 초대 코드 입력 |
| 05 | `05-parent-home.svg` | 부모 홈 |
| 06 | `06-credit-charge.svg` | 부모 크레딧 충전 |
| 07 | `07-mission-create.svg` | 미션 등록 |
| 08 | `08-child-home.svg` | 자녀 홈, 미션 목록, 캐시북 요약 |
| 09 | `09-mission-submit.svg` | 미션 완료 제출 |
| 10 | `10-parent-approval.svg` | 부모 승인/반려 |
| 11 | `11-reject-resubmit.svg` | 반려 사유/재제출 |
| 12 | `12-bank-account-register.svg` | 연결 계좌 등록 |
| 13 | `13-child-withdrawal.svg` | 자녀 출금 |

## 통합한 화면

| 기존 화면 | 통합 위치 |
|---|---|
| `08-cashbook.svg` | `08-child-home.svg`의 지갑 잔액/최근 돈 기록 섹션 |
| `12-mission-detail-status.svg` | `08-child-home.svg`, `09-mission-submit.svg`의 미션 상태 |
| `14-charge-result.svg` | `06-credit-charge.svg`의 처리 상태 또는 토스트 |
| `18-family-connected.svg` | `03-family-link.svg`, `04-child-invite-code.svg`의 성공 상태 |
| `19-mission-calendar.svg` | 보강/2차 상세 화면 |
| `09-parent-history.svg` | 보강/2차 정산/내역 화면 |
| `15-notifications.svg` | 보강/2차 알림 화면 |
| `16-settings-profile.svg` | 보강/2차 설정 화면 |

## API 스펙 영향

API 명세는 화면 통합과 충돌하지 않습니다.

- 삭제한 화면의 API는 유지해도 됩니다. 화면이 줄어든 것이지 기능 계약을 삭제한 것은 아닙니다.
- `08-child-home.svg`는 자녀 홈 API와 캐시북 요약/최근 내역 API를 함께 사용합니다.
- `06-credit-charge.svg`는 충전 요청 후 같은 화면에서 `PROCESSING/COMPLETED/FAILED` 상태를 표시하거나 토스트로 처리합니다.
- `03-family-link.svg`와 `04-child-invite-code.svg`는 가족 연결 완료 상태를 인라인으로 표시합니다.

## 최소로 남겨야 할 팝업/상태

별도 화면으로 만들지 않고 컴포넌트 상태로 처리할 항목입니다.

| 항목 | 권장 형태 |
|---|---|
| 충전 완료/실패 | `06-credit-charge` 인라인 상태 또는 토스트 |
| 가족 연결 완료 | `03-family-link`/`04-child-invite-code` 인라인 상태 |
| 출금 전 최종 확인 | 확인 모달 |
| 출금 처리 중/완료/실패 | `13-child-withdrawal` 인라인 상태 또는 토스트 |
| 계좌 등록 완료/실패 | `12-bank-account-register` 인라인 상태 또는 토스트 |
| 잔액 부족 | 인라인 에러 |
| 은행 선택 | 바텀시트 또는 선택 목록 |

## 이후 보강 후보

필수 화면에서는 제외했지만, 서비스가 커지면 다시 분리할 수 있습니다.

1. 상세 캐시북
2. 월별 미션 캘린더
3. 부모 지급/정산 내역
4. 알림 목록
5. 설정/프로필
