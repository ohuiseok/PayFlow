# PayFlow Sample React Native

Expo 기반 React Native 샘플 앱입니다. 같은 코드베이스로 웹을 먼저 배포하고, 이후 iOS/Android 앱 빌드로 확장할 수 있습니다.

## 실행

```bash
npm install
npm run web
```

Android 또는 iOS 시뮬레이터로 실행하려면:

```bash
npm run android
npm run ios
```

## 웹 배포 빌드

```bash
npm run build:web
```

빌드 결과는 `dist` 폴더에 생성됩니다. Netlify, Vercel, S3, Nginx 같은 정적 호스팅에 `dist` 폴더를 배포하면 됩니다.

로컬에서 빌드 결과를 확인하려면:

```bash
npm run preview:web
```

## 앱 배포 준비

네이티브 앱 배포는 Expo EAS Build 기준으로 준비되어 있습니다.

```bash
npm install -g eas-cli
eas login
npm run build:android
npm run build:ios
```

앱 식별자는 현재 `com.payflow.app`으로 설정되어 있습니다. 실제 스토어 배포 전에는 `app.json`의 `ios.bundleIdentifier`, `android.package`, 앱 이름, 아이콘을 확정하세요.

## API 설정

API Gateway 주소는 Expo public env로 설정합니다.

```bash
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
EXPO_PUBLIC_USE_DUMMY_DATA=false
```

실기기에서 로컬 백엔드에 붙일 때는 `localhost` 대신 개발 PC의 LAN IP를 사용하세요.

`EXPO_PUBLIC_USE_DUMMY_DATA=true`이면 API를 호출하지 않고 더미 데이터만 사용합니다. 백엔드 API 연동 모드로 바꾸려면 `false`로 설정하세요.

현재 API 연동 모드의 가족 연결은 초대코드가 아니라 자녀 사용자 ID 기반입니다.
자녀 계정에서 표시되는 user ID를 부모 계정의 가족 연결 화면에 입력하면 즉시 ACTIVE 링크가 생성됩니다.
회원가입 시 백엔드가 지갑을 자동 생성하므로, 신규 계정도 충전/보상/출금 플로우를 바로 시작할 수 있습니다.

## 예상 화면

필수 화면 흐름 이미지는 `assets/mockups/rendered/payflow-mvp-flow.png`에 있습니다.

화면별 목업은 `assets/mockups/screens`에 있고, 렌더링된 PNG와 전체 컨택트 시트는 `assets/mockups/rendered`에 있습니다.
