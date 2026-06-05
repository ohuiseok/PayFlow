# PayFlow Sample React Native

React Native 화면을 붙여보기 위한 Expo 기반 샘플 앱입니다.

## 실행

```bash
npm install
npm run start
```

Android 에뮬레이터를 바로 열려면:

```bash
npm run android
```

## API 설정

API Gateway 주소는 Expo public env로 설정합니다.

```bash
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
EXPO_PUBLIC_USE_DUMMY_DATA=true
```

실기기에서 로컬 백엔드에 붙일 때는 `localhost` 대신 개발 PC의 LAN IP를 사용하세요.

`EXPO_PUBLIC_USE_DUMMY_DATA=true`이면 API를 호출하지 않고 화면 더미데이터만 사용합니다.
백엔드 API 연동 모드로 바꾸려면 `false`로 설정하세요.

## 예상 화면

초기 화면 목업은 `assets/mockups/payflow-expected-screens.svg`에 있습니다.

화면별 목업은 `assets/mockups/screens`에 있습니다.
렌더링된 PNG와 전체 컨택트시트는 `assets/mockups/rendered`에 있습니다.

- `01-login.svg`: 로그인/회원가입
- `02-parent-home.svg`: 부모 홈과 크레딧 요약
- `03-credit-charge.svg`: 부모 크레딧 충전
- `04-mission-create.svg`: 미션과 보상 등록
- `05-child-home.svg`: 자녀 미션 홈
- `06-mission-submit.svg`: 자녀 미션 완료 제출
- `07-parent-approval.svg`: 부모 승인/반려
- `08-cashbook.svg`: 자녀 캐시북
- `09-parent-history.svg`: 부모 지급/정산 내역
- `10-signup-role.svg`: 회원가입과 부모/자녀 역할 선택
- `11-family-link.svg`: 가족 연결과 초대 코드
- `12-mission-detail-status.svg`: 미션 상세 상태 흐름
- `13-reject-resubmit.svg`: 반려 사유와 재제출
- `14-charge-result.svg`: 충전 완료/실패 결과
- `15-notifications.svg`: 알림
- `16-settings-profile.svg`: 설정/프로필
- `17-child-invite-code.svg`: 자녀 초대 코드 입력
- `18-family-connected.svg`: 가족 연결 완료
- `19-mission-calendar.svg`: 날짜별 미션 캘린더
