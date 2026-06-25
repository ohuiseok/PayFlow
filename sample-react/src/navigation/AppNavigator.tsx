import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { StackActions, useNavigation } from '@react-navigation/native';
import { ActivityIndicator, View } from 'react-native';
import { ComponentType, PropsWithChildren, useEffect } from 'react';

import { RootStackParamList } from './routes';
import { BankAccountRegisterScreen } from '../screens/banking/BankAccountRegisterScreen';
import { ChildWithdrawalScreen } from '../screens/banking/ChildWithdrawalScreen';
import { ChildCashbookScreen } from '../screens/child/ChildCashbookScreen';
import { ChildHomeScreen } from '../screens/child/ChildHomeScreen';
import { MissionSubmitScreen } from '../screens/child/MissionSubmitScreen';
import { RejectResubmitScreen } from '../screens/child/RejectResubmitScreen';
import { LoginScreen } from '../screens/auth/LoginScreen';
import { SignupRoleScreen } from '../screens/auth/SignupRoleScreen';
import { ChildInviteCodeScreen } from '../screens/family/ChildInviteCodeScreen';
import { ParentFamilyLinkScreen } from '../screens/family/ParentFamilyLinkScreen';
import { CreditChargeScreen } from '../screens/parent/CreditChargeScreen';
import { MissionCreateScreen } from '../screens/parent/MissionCreateScreen';
import { PaymentOperationsScreen } from '../screens/parent/PaymentOperationsScreen';
import { ParentApprovalScreen } from '../screens/parent/ParentApprovalScreen';
import { ParentCreditHistoryScreen } from '../screens/parent/ParentCreditHistoryScreen';
import { ParentHomeScreen } from '../screens/parent/ParentHomeScreen';
import { ParentWithdrawalScreen } from '../screens/parent/ParentWithdrawalScreen';
import { useAppState } from '../state/AppState';
import { UserRole } from '../types';

const Stack = createNativeStackNavigator<RootStackParamList>();
type RouteName = keyof RootStackParamList;

const screenTitles: Partial<Record<RouteName, string>> = {
  Login: '로그인',
  SignupRole: '회원가입',
  ParentFamilyLink: '참여자 연결',
  ChildInviteCode: '기관 연결',
  ParentHome: '기관 홈',
  CreditCharge: '지원금 충전',
  MissionCreate: '정책 미션 등록',
  ParentApproval: '승인 대기',
  ChildHome: '청년 홈',
  MissionSubmit: '완료 제출',
  RejectResubmit: '재제출',
  BankAccountRegister: '계좌 등록',
  ChildWithdrawal: '출금',
  ParentWithdrawal: '지원금 출금',
};

const screenOptions = {
  contentStyle: { backgroundColor: '#F0F4FF' },
  headerBackTitle: '뒤로',
  headerShadowVisible: false,
  headerStyle: { backgroundColor: '#0471e9' },
  headerTintColor: '#FFFFFF',
  headerTitleAlign: 'center' as const,
  headerTitleStyle: { fontFamily: 'Pretendard', fontSize: 19, fontWeight: '900' as const },
};

function RoleGuard({
  allowedRoles,
  fallbackRoute,
  requireFamilyLinked,
  children,
}: PropsWithChildren<{ allowedRoles: UserRole[]; fallbackRoute: RouteName; requireFamilyLinked?: boolean }>) {
  const { familyLinked, role } = useAppState();
  const navigation = useNavigation();
  const roleAllowed = role ? allowedRoles.includes(role) : false;
  const allowed = roleAllowed && (!requireFamilyLinked || familyLinked);

  useEffect(() => {
    if (!allowed) {
      const nextRoute = !role
        ? 'Login'
        : !roleAllowed
          ? fallbackRoute
          : role === 'parent'
            ? 'ParentFamilyLink'
            : 'ChildInviteCode';
      navigation.dispatch(StackActions.replace(nextRoute));
    }
  }, [allowed, fallbackRoute, familyLinked, navigation, role, roleAllowed]);

  if (!allowed) {
    return null;
  }

  return <>{children}</>;
}

function withRoleGuard<Name extends RouteName>(
  Component: ComponentType<NativeStackScreenProps<RootStackParamList, Name>>,
  allowedRoles: UserRole[],
  fallbackRoute: RouteName,
  requireFamilyLinked = false,
) {
  return function GuardedScreen(props: NativeStackScreenProps<RootStackParamList, Name>) {
    return (
      <RoleGuard allowedRoles={allowedRoles} fallbackRoute={fallbackRoute} requireFamilyLinked={requireFamilyLinked}>
        <Component {...props} />
      </RoleGuard>
    );
  };
}

const parentOnly = ['parent'] as UserRole[];
const childOnly = ['child'] as UserRole[];
const parentOrChild = ['parent', 'child'] as UserRole[];
const GuardedParentFamilyLinkScreen = withRoleGuard(ParentFamilyLinkScreen, parentOnly, 'ChildHome');
const GuardedParentHomeScreen = withRoleGuard(ParentHomeScreen, parentOnly, 'ChildHome');
const GuardedCreditChargeScreen = withRoleGuard(CreditChargeScreen, parentOnly, 'ChildHome');
const GuardedPaymentOperationsScreen = withRoleGuard(PaymentOperationsScreen, parentOnly, 'ChildHome');
const GuardedMissionCreateScreen = withRoleGuard(MissionCreateScreen, parentOnly, 'ChildHome');
const GuardedParentApprovalScreen = withRoleGuard(ParentApprovalScreen, parentOnly, 'ChildHome');
const GuardedChildInviteCodeScreen = withRoleGuard(ChildInviteCodeScreen, childOnly, 'ParentHome');
const GuardedChildHomeScreen = withRoleGuard(ChildHomeScreen, childOnly, 'ParentHome', true);
const GuardedMissionSubmitScreen = withRoleGuard(MissionSubmitScreen, childOnly, 'ParentHome', true);
const GuardedRejectResubmitScreen = withRoleGuard(RejectResubmitScreen, childOnly, 'ParentHome', true);
const GuardedBankAccountRegisterScreen = withRoleGuard(BankAccountRegisterScreen, parentOrChild, 'Login');
const GuardedChildWithdrawalScreen = withRoleGuard(ChildWithdrawalScreen, childOnly, 'ParentHome', true);
const GuardedParentWithdrawalScreen = withRoleGuard(ParentWithdrawalScreen, parentOnly, 'ChildHome');
const GuardedChildCashbookScreen = withRoleGuard(ChildCashbookScreen, childOnly, 'ParentHome', true);
const GuardedParentCreditHistoryScreen = withRoleGuard(ParentCreditHistoryScreen, parentOnly, 'ChildHome');

export function AppNavigator() {
  const { isRestoringSession } = useAppState();

  if (isRestoringSession) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#F0F4FF' }}>
        <ActivityIndicator size="large" color="#0471e9" />
      </View>
    );
  }

  return (
    <Stack.Navigator
      initialRouteName="Login"
      screenOptions={screenOptions}
    >
      <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false, title: screenTitles.Login }} />
      <Stack.Screen name="SignupRole" component={SignupRoleScreen} options={{ title: screenTitles.SignupRole }} />
      <Stack.Screen name="ParentFamilyLink" component={GuardedParentFamilyLinkScreen} options={{ title: screenTitles.ParentFamilyLink }} />
      <Stack.Screen name="ChildInviteCode" component={GuardedChildInviteCodeScreen} options={{ title: screenTitles.ChildInviteCode }} />
      <Stack.Screen
        name="ParentHome"
        component={GuardedParentHomeScreen}
        options={{ headerBackVisible: false, headerLeft: () => null, title: screenTitles.ParentHome }}
      />
      <Stack.Screen name="CreditCharge" component={GuardedCreditChargeScreen} options={{ title: screenTitles.CreditCharge }} />
      {/* <Stack.Screen name="PaymentOperations" component={GuardedPaymentOperationsScreen} options={{ title: '결제 운영' }} /> */}
      <Stack.Screen name="MissionCreate" component={GuardedMissionCreateScreen} options={{ title: screenTitles.MissionCreate }} />
      <Stack.Screen name="ParentApproval" component={GuardedParentApprovalScreen} options={{ title: screenTitles.ParentApproval }} />
      <Stack.Screen name="ChildHome" component={GuardedChildHomeScreen} options={{ title: screenTitles.ChildHome }} />
      <Stack.Screen name="MissionSubmit" component={GuardedMissionSubmitScreen} options={{ title: screenTitles.MissionSubmit }} />
      <Stack.Screen name="RejectResubmit" component={GuardedRejectResubmitScreen} options={{ title: screenTitles.RejectResubmit }} />
      <Stack.Screen name="BankAccountRegister" component={GuardedBankAccountRegisterScreen} options={{ title: screenTitles.BankAccountRegister }} />
      <Stack.Screen name="ChildWithdrawal" component={GuardedChildWithdrawalScreen} options={{ title: screenTitles.ChildWithdrawal }} />
      <Stack.Screen name="ParentWithdrawal" component={GuardedParentWithdrawalScreen} options={{ title: screenTitles.ParentWithdrawal }} />
      <Stack.Screen name="ChildCashbook" component={GuardedChildCashbookScreen} options={{ title: '지원금 내역' }} />
      <Stack.Screen name="ParentCreditHistory" component={GuardedParentCreditHistoryScreen} options={{ title: '예산 기록' }} />
    </Stack.Navigator>
  );
}
