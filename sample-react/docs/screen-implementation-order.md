# 화면 구현 순서

이 문서는 `sample-react` 앱의 화면을 구현할 때 따라갈 순서를 정리한다.

원칙은 먼저 화면과 상태를 더미 데이터로 완성하고, 마지막 단계에서만 API를 연결하는 것이다. API가 준비되지 않아도 웹/앱 화면 검수, 사용자 흐름 검수, 컴포넌트 구조 정리를 먼저 끝낼 수 있어야 한다.

## 현재 문서의 보강 방향

큰 구현 순서만으로는 충분하지 않다. 목업을 보면 각 화면이 독립 페이지라기보다 하나의 돈 흐름으로 이어진다.

따라서 구현자는 아래 세 가지를 같이 맞춰야 한다.

1. 화면 UI: 목업의 카드, 입력, 버튼, 배지, 안내 박스가 실제로 보인다.
2. 더미 상태: 버튼을 누르면 미션 상태, 잔액, 최근 기록이 실제로 바뀐다.
3. API 교체 가능성: 더미 함수와 API 함수의 반환 형태가 같아서 마지막에 교체할 수 있다.

특히 `충전`, `승인`, `출금`은 금액이 움직이는 화면이다. 이 화면들은 단순 Alert 처리로 끝내지 말고, 더미 상태에서도 잔액 변경과 거래 기록 추가까지 구현한다.

## 기준 자료

- 목업 원본: `../assets/mockups/screens`
- 렌더링 이미지: `../assets/mockups/rendered`
- 전체 흐름 이미지: `../assets/mockups/rendered/payflow-mvp-flow.png`
- API 명세: `../../docs/api-spec.md`
- 서비스 흐름: `../../docs/service-flow.md`

## 구현 원칙

1. `App.tsx` 단일 화면에서 시작하되, 화면 수가 늘어나면 `src/screens`, `src/components`, `src/data`, `src/config`로 분리한다.
2. 화면은 목업 PNG를 기준으로 먼저 정적 구현한다.
3. 버튼, 입력값, 탭, 모달, 토스트 같은 상호작용은 더미 상태로 먼저 연결한다.
4. 더미 데이터는 `src/data/dummyData.ts`에서 관리하고, 화면별 fixture를 충분히 만든다.
5. API 클라이언트, 인증 토큰, 에러 재시도, 로딩 상태는 모든 주요 화면이 끝난 뒤 마지막에 붙인다.
6. 화면 전환은 React Navigation을 도입한다.
7. 부모/자녀 역할 분기는 백엔드가 저장한 `role` 값을 기준으로 한다.

## 추천 폴더 구조

```text
sample-react/src
  components/
    common/
    wallet/
    mission/
    family/
  screens/
    auth/
    family/
    parent/
    child/
    banking/
  data/
    dummyData.ts
  config/
    appConfig.ts
  api/
    client.ts
    authApi.ts
    familyApi.ts
    walletApi.ts
    missionApi.ts
  navigation/
    AppNavigator.tsx
    routes.ts
  storage/
    tokenStorage.ts
```

`api/` 폴더는 마지막 API 연동 단계에서 만든다.

`navigation/` 폴더는 React Navigation 도입 시점에 만든다. 더미 화면 구현 단계에서도 실제 앱 구조와 비슷하게 `Stack` 기반으로 화면을 나눈다.

## 추가 패키지

현재 `package.json`에는 React Navigation과 SecureStore가 없다. 화면 구현을 시작할 때 아래 의존성을 추가한다.

```bash
npm install @react-navigation/native @react-navigation/native-stack react-native-screens react-native-safe-area-context expo-secure-store
```

웹/앱 공용 구현에서는 `tokenStorage`가 플랫폼 차이를 숨긴다.

```text
native: expo-secure-store
web: window.localStorage
```

웹에서는 `localStorage` 접근 전 `typeof window !== 'undefined'`를 확인한다. 토큰 저장 실패 시에는 메모리 저장 fallback을 두되, 새로고침하면 로그아웃될 수 있음을 허용한다.

## 확정된 구현 기준

아래 항목은 MVP 기준으로 확정된 구현 기준이다.

| 항목 | 기준 | 결정 |
|---|---|---|
| 인증/역할 | 기본 API는 `POST /api/users`, `POST /api/users/login`이고, 회원가입/로그인 응답에 `role`이 포함된다. | 백엔드가 `role`을 저장하고 로그인/내 정보 응답에서 내려준다. |
| 화면 전환 방식 | `sample-react`에는 아직 React Navigation 같은 라우팅 라이브러리가 없다. | React Navigation을 도입한다. |
| 토큰 저장 | API 연결 단계에서 인증 토큰 저장/주입이 필요하다. | 웹/앱 공용 `tokenStorage` 추상화를 만들고, 앱은 SecureStore, 웹은 localStorage를 사용한다. |
| 사진 첨부 | 목업에는 인증 사진 첨부가 있다. | 우선 더미 URL로 처리한다. 실제 업로드는 API 연결 후 보강한다. |
| 계좌 검증 | 목업은 은행/계좌/예금주 입력만 보여준다. | MVP는 등록 성공/실패만 보여준다. 실명 검증 UI는 보강으로 둔다. |
| 처리 중 상태 | 충전/출금 API는 `PROCESSING` 후 결과 조회가 필요하다. | 2초 간격 polling, 최대 60초 대기, 화면 이탈 시 polling 중지 후 복귀 시 재조회한다. |
| 날짜/금액 규칙 | 목업은 간단한 예시값만 있다. | 아래 `입력 정책` 값을 기준으로 확정한다. |

백엔드 명세와 다르면 API 명세를 먼저 갱신한 뒤 프론트를 연결한다.

## API 정합성 체크

API 연결은 `../../docs/api-spec.md`의 아래 계약을 기준으로 진행한다.

| 항목 | 필요한 계약 |
|---|---|
| 회원가입 | `POST /api/users` 요청/응답에 `role: PARENT | CHILD`를 포함한다. 루트 API 명세는 이 기준으로 맞췄다. |
| 로그인 | `POST /api/users/login` 응답에 `accessToken`과 `user.userId`, `user.name`, `user.role`, `user.status`를 포함한다. 루트 API 명세는 이 기준으로 맞췄다. |
| 사용자 조회 | 프론트는 `GET /api/users/me`를 우선 사용한다. 기존 `GET /api/users/{userId}`는 호환용으로 유지할 수 있다. |
| JWT/권한 | 화면 분기는 백엔드 저장 `role`을 사용한다. 권한 검증은 서버에서 가족 관계와 인증 사용자 기준으로 수행한다. |
| 가족 연결 상태 | `GET /api/families/me` 응답만으로 가족 연결 전/후를 판단할 수 있어야 한다. |
| `bankAccountId` 타입 | 공개 API에서는 문자열 ID를 사용한다. 예: `bank-account-001`. |
| `walletId` 출처 | 부모 충전은 `GET /api/credits/parent/summary`의 `walletId`, 자녀 출금은 `GET /api/cashbook/children/{childUserId}/summary`의 `walletId`를 사용한다. |
| 처리 상태 | 충전/출금 상태는 `PROCESSING`, `COMPLETED`, `FAILED`, `UNKNOWN`, `COMPENSATION_REQUIRED`를 화면에서 매핑할 수 있게 한다. |
| 파일 첨부 | MVP는 더미 URL을 사용한다. 실제 업로드를 켤 때 `POST /api/files/mission-evidence/upload-url` 응답의 `fileUrl`을 제출/재제출 API에 넣는다. |

이 표의 항목과 루트 API 명세가 달라지면 루트 API 명세를 먼저 갱신한다.

## 루트 docs 대조 결과

`../../docs`의 API 명세, 서비스 흐름, 구현 계획을 함께 확인했다.

정합성을 맞춘 항목:

- user-service는 MVP에서 `role`을 저장한다.
- 회원가입 요청/응답은 `role`을 포함한다.
- 로그인 응답은 토큰과 함께 화면 분기에 필요한 사용자 정보를 포함한다.
- 프론트는 `GET /api/users/me`를 우선 사용할 수 있다.
- role은 화면 진입에 쓰고, 가족/미션/캐시북 실제 권한은 Family 관계와 소유권으로 다시 검증한다.
- 공개 API의 `bankAccountId`는 문자열로 통일한다.

MVP API 연결 범위에 포함한 항목:

- `13-child-withdrawal` 자녀 출금 화면과 `POST /api/credits/withdrawals`, `GET /api/credits/withdrawals/{withdrawalId}` API 연결은 MVP 범위에 포함한다.
- 프론트는 자녀 출금 화면을 더미 모드로 먼저 구현하고, API 연결 마지막 단계에서 실제 출금 요청/결과 조회를 붙인다.

보강/2차로 유지하는 항목:

- 파일 업로드 URL API는 보강/2차 범위이므로, 미션 제출/재제출은 더미 URL 또는 고정 placeholder URL로 먼저 구현한다.

## 입력 정책

금액과 날짜 입력은 아래 기준으로 통일한다.

| 항목 | 정책 |
|---|---|
| 미션 수행 날짜 | 오늘부터 90일 이내 |
| 미션 보상 금액 | 최소 1,000원, 최대 부모 크레딧 잔액 |
| 크레딧 충전 금액 | 최소 10,000원, 최대 1,000,000원 |
| 크레딧 빠른 선택 | 10,000원, 30,000원, 50,000원 |
| 자녀 출금 금액 | 최소 1,000원, 최대 자녀 지갑 잔액 |
| 출금 빠른 선택 | 5,000원, 10,000원, 30,000원 |
| 계좌번호 | 숫자만 입력, 10~14자리 |
| 휴대폰 번호 | 숫자만 입력, 10~11자리 |
| 비밀번호 | 8자 이상 |

금액은 입력 중에도 천 단위 콤마를 표시하고, API 요청에는 정수 원 단위로 보낸다.

## 처리 중 상태 정책

충전과 출금은 요청 직후 `PROCESSING`이 올 수 있다.

1. 요청 성공 후 결과 조회 API를 2초 간격으로 호출한다.
2. 최대 대기 시간은 60초로 둔다.
3. 60초 안에 `COMPLETED`, `FAILED`, `UNKNOWN`, `COMPENSATION_REQUIRED` 중 하나가 오면 polling을 종료한다.
4. 60초가 지나도 `PROCESSING`이면 화면에는 `처리 중`으로 남기고, 사용자가 나중에 다시 들어왔을 때 상태를 재조회한다.
5. 사용자가 화면을 이탈하면 polling은 중지한다.
6. 부모 홈/자녀 홈으로 돌아왔을 때 pending 충전/출금이 있으면 1회 즉시 재조회한다.
7. 요청 중에는 같은 버튼을 비활성화해서 중복 요청을 막는다.

화면 상태 문구:

| API 상태 | 화면 표시 |
|---|---|
| `PROCESSING` | 처리 중 |
| `COMPLETED` | 완료 |
| `FAILED` | 실패 |
| `UNKNOWN` | 확인 필요 |
| `COMPENSATION_REQUIRED` | 고객센터 확인 필요 |

## 목업 기준 공통 UI

목업 13개 화면에서 반복되는 UI를 먼저 컴포넌트로 만든다.

| 컴포넌트 | 사용 화면 | 구현 포인트 |
|---|---|---|
| `ScreenFrame` | 전체 | 밝은 회색 배경, 중앙 정렬, 모바일 폭 기준 컨테이너 |
| `PageHeader` | 전체 | 작은 eyebrow, 굵은 제목, 짧은 설명 |
| `BalanceCard` | 부모 홈, 자녀 홈, 출금 | 진한 남색 카드, 큰 금액, 보조 설명 |
| `InfoBox` | 로그인, 코드 입력, 충전, 계좌 등록, 출금 | 연녹색/연파랑/연노랑/연빨강 상태 안내 |
| `FormField` | 로그인, 가입, 충전, 미션, 계좌, 출금 | label, value, error, disabled 상태 |
| `PrimaryButton` | 전체 | 초록색 주요 액션, 요청 중 비활성화 |
| `SecondaryButton` | 부모 홈, 승인/반려 | 검정/회색 보조 액션 |
| `StatusBadge` | 미션 목록, 승인, 반려 | 진행 중, 제출, 새 미션, 반려됨 등 |
| `MissionCard` | 부모 홈, 자녀 홈, 승인/반려 | 제목, 자녀명, 보상 금액, 상태 |
| `AmountQuickSelect` | 충전 | 1만원, 3만원, 5만원 빠른 선택 |
| `ConfirmModal` | 출금 | 최종 확인, 취소, 진행 버튼 |
| `Toast` | 충전/계좌/출금 결과 | 성공/실패 짧은 피드백 |

## 더미 상태 모델

API 연결 전에는 아래 형태의 앱 상태를 기준으로 화면을 연결한다.

```text
auth:
  currentUserRole: parent | child | null
  currentUserName

family:
  inviteCode
  linkedParent
  linkedChildren[]
  linkRequestStatus: idle | requested | approved | rejected

wallet:
  parentCreditBalance
  childCashBalance
  linkedBankAccount

missions:
  id
  childId
  title
  rewardAmount
  dueDate
  status: todo | submitted | approved | rejected | paid
  submitMemo
  rejectReason

cashbook:
  id
  title
  amount
  type: reward | withdrawal | charge
```

상태 변경 규칙:

- 크레딧 충전 성공: `parentCreditBalance += amount`, 부모 거래 기록 추가
- 미션 등록: `missions`에 `todo` 상태로 추가
- 자녀 제출: 미션 상태를 `submitted`로 변경
- 부모 반려: 미션 상태를 `rejected`로 변경하고 `rejectReason` 저장
- 자녀 재제출: 미션 상태를 다시 `submitted`로 변경
- 부모 승인: `parentCreditBalance -= rewardAmount`, `childCashBalance += rewardAmount`, 미션 상태를 `paid`로 변경, 자녀 캐시북에 보상 기록 추가
- 자녀 출금 성공: `childCashBalance -= amount`, 자녀 캐시북에 출금 기록 추가

이 규칙이 더미 모드에서 동작해야 API 연결 전에도 실제 서비스처럼 검수할 수 있다.

백엔드 API의 상태값은 대문자(`SUBMITTED`, `PAID`, `REJECTED`, `PROCESSING`, `COMPLETED`, `FAILED`)를 사용할 수 있다. 화면 내부에서는 소문자 union을 쓰더라도 API 변환 레이어에서 명확히 매핑한다.

## 화면 전환 맵

React Navigation 기준으로 아래 전환을 구현한다.

```text
login
-> signupRole
-> parentFamilyLink | childInviteCode
-> parentHome | childHome

parentHome
-> creditCharge
-> missionCreate
-> parentApproval

childHome
-> missionSubmit
-> rejectResubmit
-> bankAccountRegister
-> childWithdrawal
```

권장 네비게이션 구조:

```text
RootStack
  AuthStack
    Login
    SignupRole
  FamilyStack
    ParentFamilyLink
    ChildInviteCode
  ParentStack
    ParentHome
    CreditCharge
    MissionCreate
    ParentApproval
  ChildStack
    ChildHome
    MissionSubmit
    RejectResubmit
    BankAccountRegister
    ChildWithdrawal
```

로그인 후 백엔드가 내려준 `role`과 가족 연결 상태를 기준으로 진입 Stack을 결정한다.

- `role=PARENT`, 가족 연결 전: `ParentFamilyLink`
- `role=PARENT`, 가족 연결 후: `ParentHome`
- `role=CHILD`, 가족 연결 전: `ChildInviteCode`
- `role=CHILD`, 가족 연결 후: `ChildHome`

역할 전환 검수를 쉽게 하기 위해 더미 모드에서는 개발용 `역할 전환` 액션을 숨김 메뉴나 임시 버튼으로 둘 수 있다. 실제 배포 화면에서는 제거한다.

## 화면별 구현 체크리스트

| 번호 | 화면 | 필수 UI | 필수 상태/액션 |
|---|---|---|---|
| 01 | 로그인 | 휴대폰 번호, 비밀번호, 로그인 버튼, 회원가입 링크, 역할 안내 박스 | 더미 로그인 성공, 미입력 에러, 역할별 홈 이동 |
| 02 | 역할 선택 | 부모/자녀 선택 카드, 이름, 휴대폰 번호, 다음 버튼 | 선택 카드 활성화, 입력 검증, 부모는 초대 화면으로 이동 |
| 03 | 부모 초대 | 초대 코드 카드, 연결 대기 안내, 요청 도착 박스, 승인/거절 버튼 | 자녀 요청 도착 시뮬레이션, 승인 시 가족 연결 완료 |
| 04 | 자녀 코드 입력 | 6자리 코드 입력칸, 부모 정보 확인, 요청 상태, 연결 요청 버튼 | 코드 길이 검증, 요청 대기 상태, 승인 후 자녀 홈 이동 |
| 05 | 부모 홈 | 보상 크레딧 카드, 충전/미션 등록 버튼, 진행 중 미션 목록 | 충전/등록/승인 화면 이동, 미션 상태별 배지 |
| 06 | 크레딧 충전 | 충전 계좌, 금액 입력, 빠른 금액 버튼, 예상 잔액, 충전 버튼 | 처리 중/성공/실패, 잔액 증가, 거래 기록 추가 |
| 07 | 미션 등록 | 자녀 선택, 미션 이름, 보상 금액, 수행 날짜, 조건 안내 | 금액 검증, 잔액 부족 경고, 등록 후 부모/자녀 홈 반영 |
| 08 | 자녀 홈 | 지갑 잔액, 출금/계좌등록 버튼, 새 미션, 최근 돈 기록 | 미션 제출 진입, 캐시북 요약 반영 |
| 09 | 완료 제출 | 미션 요약, 사진 첨부 영역, 메모, 제출 상태 안내, 제출 버튼 | 첨부 placeholder, 제출 후 `submitted` 전환 |
| 10 | 제출 확인 | 제출 미션, 인증 사진/메모, 승인 시 지급 안내, 승인/반려 버튼 | 승인 시 잔액 이동, 반려 시 사유 입력 흐름 |
| 11 | 재제출 | 반려 미션, 반려 사유, 재제출 메모, 사진 다시 첨부, 재제출 버튼 | 반려 사유 표시, 재제출 후 `submitted` 전환 |
| 12 | 계좌 등록 | 등록 가능 안내, 은행 선택, 계좌번호, 예금주, 주의 박스 | 계좌 입력 검증, 등록 완료 후 출금 화면 반영 |
| 13 | 계좌 출금 | 출금 가능 잔액, 받을 계좌, 금액 입력, 주의 박스, 출금 버튼 | 잔액 초과 방지, 확인 모달, 성공 시 잔액 차감 |

## 현실적인 구현 마일스톤

화면을 번호 순서대로만 만들면 부모 화면과 자녀 화면의 상태가 나중에 어긋나기 쉽다. 아래 마일스톤 단위로 구현하고 검수한다.

| 마일스톤 | 범위 | 산출물 |
|---|---|---|
| A. 공통 UI | 레이아웃, 버튼, 입력, 카드, 배지, 안내 박스 | 목업과 비슷한 시각 언어 확보 |
| B. 진입 흐름 | 01, 02, 03, 04 | 부모/자녀 역할과 가족 연결 상태 확보 |
| C. 부모 돈 준비 | 05, 06 | 부모 홈 잔액, 충전 성공/실패, 거래 기록 |
| D. 미션 생성 | 07, 08 | 부모가 만든 미션이 자녀 홈에 노출 |
| E. 미션 처리 | 09, 10, 11 | 제출, 승인, 반려, 재제출 상태 전이 |
| F. 잔액 이동 | 05, 08, 10 | 승인 시 부모 차감, 자녀 증가, 캐시북 기록 |
| G. 출금 | 12, 13 | 계좌 등록, 출금, 자녀 잔액 차감 |
| H. API 연결 | 전체 | 더미 저장소를 API 저장소로 교체 |

각 마일스톤은 `npm run web`으로 직접 클릭해서 끝까지 확인한다. 테스트 자동화 전이라도 최소한 브라우저에서 역할 전환과 전체 흐름을 손으로 검수한다.

## 1단계. 앱 골격과 공통 UI

목표:

- 네비게이션 또는 임시 화면 전환 구조를 만든다.
- 공통 레이아웃, 색상, 타이포그래피, 버튼, 입력, 카드, 상태 배지를 만든다.
- 웹과 모바일에서 깨지지 않는 기본 반응형 폭을 잡는다.

완료 기준:

- `npm run web`에서 첫 화면이 열린다.
- 목업의 공통 스타일을 반복 구현하지 않고 재사용할 수 있다.
- 로딩, 빈 상태, 에러 메시지 표현 방식이 정해져 있다.

## 2단계. 인증 화면

대상 화면:

```text
01-login
02-signup-role
```

구현 내용:

- 로그인 화면
- 회원가입/역할 선택 화면
- 부모/자녀 역할 선택 상태
- 더미 로그인 성공 후 역할별 홈 이동

완료 기준:

- API 없이도 부모 홈과 자녀 홈으로 진입할 수 있다.
- 잘못된 입력, 미입력 상태를 인라인 에러로 보여준다.
- 백엔드 회원가입/로그인 응답에서 받은 `role` 기준으로 부모/자녀 Stack을 분기한다.

## 3단계. 가족 연결 화면

대상 화면:

```text
03-family-link
04-child-invite-code
```

구현 내용:

- 부모 초대 코드 발급/복사 화면
- 자녀 초대 코드 입력 화면
- 가족 연결 성공/실패 인라인 상태
- 연결 완료 후 홈 이동

완료 기준:

- 초대 코드 생성, 입력, 성공/실패 시나리오를 더미 상태로 검수할 수 있다.
- 별도 완료 화면 없이 현재 화면 안에서 완료 상태를 보여준다.

## 4단계. 부모 홈

대상 화면:

```text
05-parent-home
```

구현 내용:

- 부모 크레딧 잔액
- 자녀별 미션/보상 요약
- 승인 대기 미션
- 주요 액션 진입 버튼

완료 기준:

- 더미 데이터만으로 부모의 현재 상태를 한 화면에서 이해할 수 있다.
- 충전, 미션 등록, 승인 화면으로 이동할 수 있다.

## 5단계. 부모 크레딧 충전

대상 화면:

```text
06-credit-charge
```

구현 내용:

- 충전 금액 입력
- 결제/오픈뱅킹 처리 중 상태
- 성공/실패 인라인 상태 또는 토스트
- 충전 후 부모 홈 잔액 반영

완료 기준:

- 실제 결제 API 없이도 `PROCESSING`, `COMPLETED`, `FAILED` 상태를 전환해 볼 수 있다.
- 금액 미입력, 최소/최대 금액, 잔액 반영 UX가 확인된다.

## 6단계. 미션 등록

대상 화면:

```text
07-mission-create
```

구현 내용:

- 대상 자녀 선택
- 미션 제목/설명/마감일/보상 금액 입력
- 등록 전 검증
- 등록 완료 후 부모 홈 또는 미션 목록 반영

완료 기준:

- 입력값 검증과 등록 완료 상태를 API 없이 확인할 수 있다.
- 부모 크레딧 잔액보다 큰 보상 금액을 막거나 경고한다.

## 7단계. 자녀 홈과 캐시북 요약

대상 화면:

```text
08-child-home
```

구현 내용:

- 자녀 지갑 잔액
- 진행 중/제출 가능/완료 미션 목록
- 최근 캐시북 기록
- 미션 상세 또는 제출 화면 진입

완료 기준:

- 자녀가 해야 할 일과 받을 수 있는 보상이 바로 보인다.
- 캐시북 상세 화면 없이도 최근 돈 기록을 확인할 수 있다.

## 8단계. 미션 제출과 반려 재제출

대상 화면:

```text
09-mission-submit
11-reject-resubmit
```

구현 내용:

- 미션 완료 제출
- 제출 내용 입력
- 제출 후 승인 대기 상태
- 반려 사유 확인
- 반려된 미션 재제출

완료 기준:

- `진행 중 -> 제출 -> 승인 대기 -> 반려 -> 재제출` 흐름을 더미 상태로 검수할 수 있다.
- 파일 업로드가 필요하면 이 단계에서는 더미 첨부 또는 로컬 placeholder로 처리한다.

## 9단계. 부모 승인/반려

대상 화면:

```text
10-parent-approval
```

구현 내용:

- 승인 대기 미션 목록
- 제출 내용 확인
- 승인 처리
- 반려 사유 입력
- 승인 후 부모 크레딧 차감 및 자녀 캐시 잔액 증가

완료 기준:

- `승인`과 `반려` 결과가 부모 홈, 자녀 홈, 캐시북 요약에 즉시 반영된다.
- 잔액 부족, 중복 승인 방지 상태를 더미 로직으로 검수한다.

## 10단계. 계좌 등록과 자녀 출금

대상 화면:

```text
12-bank-account-register
13-child-withdrawal
```

구현 내용:

- 은행 선택
- 계좌번호 입력
- 계좌 등록 완료/실패 상태
- 출금 금액 입력
- 출금 전 확인 모달
- 출금 처리 중/완료/실패 상태

완료 기준:

- 실제 은행 API 없이 계좌 등록과 출금 상태를 시뮬레이션할 수 있다.
- 자녀 잔액보다 큰 출금 요청을 막는다.

## 11단계. 화면 간 상태 통합

목표:

- 각 화면에서 만든 더미 상태를 하나의 앱 상태 흐름으로 묶는다.
- 부모/자녀 역할을 전환하며 전체 MVP 플로우를 끝까지 검수한다.

검수 흐름:

```text
회원가입/로그인
-> 가족 연결
-> 부모 크레딧 충전
-> 미션 등록
-> 자녀 미션 확인
-> 자녀 제출
-> 부모 승인 또는 반려
-> 보상 지급 반영
-> 자녀 캐시북 확인
-> 계좌 등록
-> 자녀 출금
```

완료 기준:

- `EXPO_PUBLIC_USE_DUMMY_DATA=true` 상태에서 전체 플로우가 끊기지 않는다.
- 웹 화면과 모바일 화면 모두 레이아웃이 깨지지 않는다.
- 목업과 다른 부분은 의도와 사유를 문서나 주석으로 남긴다.

## 12단계. API 연결

API 연결은 마지막에 진행한다.

API 연결은 백엔드 명세가 `role` 저장을 지원하는 현재 기준으로 진행한다. 화면이 부모/자녀 역할을 필수로 요구하므로 회원가입, 로그인, 내 정보 조회 응답에서 `role`을 사용한다.

구현 순서:

1. `src/api/client.ts`를 만들고 `EXPO_PUBLIC_API_BASE_URL`을 사용한다.
2. `src/storage/tokenStorage.ts`를 만들고 앱은 SecureStore, 웹은 localStorage를 사용한다.
3. 화면별 더미 함수와 API 함수를 같은 반환 형태로 맞춘다.
4. `EXPO_PUBLIC_USE_DUMMY_DATA=false`일 때만 API를 호출한다.
5. 로그인/회원가입 API를 연결한다.
6. 가족 연결 API를 연결한다.
7. 지갑/잔액/충전 API를 연결한다.
8. 미션 등록/조회/제출/승인/반려 API를 연결한다.
9. 캐시북/거래 내역 API를 연결한다.
10. 계좌 등록/출금 API를 연결한다.

화면별 API 후보:

| 화면 | API |
|---|---|
| 로그인/회원가입 | `POST /api/users`, `POST /api/users/login` |
| 가족 연결 | `POST /api/families/invitations`, `GET /api/families/invitations/{inviteCode}`, `POST /api/families/link-requests`, `POST /api/families/link-requests/{requestId}/approve`, `POST /api/families/link-requests/{requestId}/reject`, `GET /api/families/me` |
| 부모 홈 | `GET /api/credits/parent/summary`, `GET /api/missions?role=parent&status=active` |
| 크레딧 충전 | `GET /api/credits/bank-accounts`, `POST /api/credits/charges`, `GET /api/credits/charges/{chargeId}` |
| 미션 등록 | `POST /api/missions`, `PATCH /api/missions/{missionId}` |
| 자녀 홈 | `GET /api/missions?role=child&status=active`, `GET /api/cashbook/children/{childUserId}/summary`, `GET /api/cashbook/children/{childUserId}/entries` |
| 미션 제출 | `POST /api/files/mission-evidence/upload-url`, `POST /api/missions/{missionId}/submit` |
| 승인/반려 | `GET /api/missions/{missionId}`, `POST /api/missions/{missionId}/approve`, `POST /api/missions/{missionId}/reject` |
| 재제출 | `POST /api/files/mission-evidence/upload-url`, `POST /api/missions/{missionId}/resubmit` |
| 계좌 등록 | `GET /api/credits/bank-accounts`, `POST /api/credits/bank-accounts`, `DELETE /api/credits/bank-accounts/{bankAccountId}` |
| 자녀 출금 | `POST /api/credits/withdrawals`, `GET /api/credits/withdrawals/{withdrawalId}` |

금액이 움직이는 API 주의사항:

- `POST /api/credits/charges`는 `Idempotency-Key`를 붙인다.
- `POST /api/credits/withdrawals`는 `Idempotency-Key`를 붙인다.
- `POST /api/missions/{missionId}/approve`는 프론트가 `Idempotency-Key`를 직접 만들지 않는다. reward-service가 승인 대상 제출 건의 `missionSubmissionId` 기준으로 내부 멱등키를 만든다.
- 요청 중에는 버튼을 비활성화하고, 완료/실패 상태가 확정되기 전까지 같은 요청을 다시 보내지 않는다.
- `PROCESSING` 상태가 있는 충전/출금은 즉시 완료로 단정하지 말고 상태 조회 API로 갱신할 수 있게 만든다.

완료 기준:

- `.env`에서 `EXPO_PUBLIC_USE_DUMMY_DATA=true/false`를 바꿔도 화면 코드는 크게 변하지 않는다.
- API 실패 시 로딩 해제, 에러 메시지, 재시도 버튼이 동작한다.
- 같은 사용자 액션이 중복 전송되지 않도록 버튼 비활성화 또는 요청 중 상태를 둔다.
- 송금/충전/출금처럼 금액이 움직이는 요청은 백엔드 멱등성 정책과 맞춰 중복 요청을 방지한다.

## 2차 보강 후보

아래 화면은 MVP 필수 화면 이후에 분리한다.

```text
상세 캐시북
월별 미션 캘린더
부모 지급/정산 내역
알림 목록
설정/프로필
```
