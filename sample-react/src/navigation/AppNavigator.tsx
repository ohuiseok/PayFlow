import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { StackActions, useNavigation } from '@react-navigation/native';
import { ComponentType, PropsWithChildren, useEffect } from 'react';

import { RootStackParamList } from './routes';
import { BankAccountRegisterScreen } from '../screens/banking/BankAccountRegisterScreen';
import { ChildWithdrawalScreen } from '../screens/banking/ChildWithdrawalScreen';
import { ChildHomeScreen } from '../screens/child/ChildHomeScreen';
import { MissionSubmitScreen } from '../screens/child/MissionSubmitScreen';
import { RejectResubmitScreen } from '../screens/child/RejectResubmitScreen';
import { LoginScreen } from '../screens/auth/LoginScreen';
import { SignupRoleScreen } from '../screens/auth/SignupRoleScreen';
import { ChildInviteCodeScreen } from '../screens/family/ChildInviteCodeScreen';
import { ParentFamilyLinkScreen } from '../screens/family/ParentFamilyLinkScreen';
import { CreditChargeScreen } from '../screens/parent/CreditChargeScreen';
import { MissionCreateScreen } from '../screens/parent/MissionCreateScreen';
import { ParentApprovalScreen } from '../screens/parent/ParentApprovalScreen';
import { ParentHomeScreen } from '../screens/parent/ParentHomeScreen';
import { useAppState } from '../state/AppState';
import { UserRole } from '../types';

const Stack = createNativeStackNavigator<RootStackParamList>();
type RouteName = keyof RootStackParamList;

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
const GuardedParentFamilyLinkScreen = withRoleGuard(ParentFamilyLinkScreen, parentOnly, 'ChildHome');
const GuardedParentHomeScreen = withRoleGuard(ParentHomeScreen, parentOnly, 'ChildHome', true);
const GuardedCreditChargeScreen = withRoleGuard(CreditChargeScreen, parentOnly, 'ChildHome', true);
const GuardedMissionCreateScreen = withRoleGuard(MissionCreateScreen, parentOnly, 'ChildHome', true);
const GuardedParentApprovalScreen = withRoleGuard(ParentApprovalScreen, parentOnly, 'ChildHome', true);
const GuardedChildInviteCodeScreen = withRoleGuard(ChildInviteCodeScreen, childOnly, 'ParentHome');
const GuardedChildHomeScreen = withRoleGuard(ChildHomeScreen, childOnly, 'ParentHome', true);
const GuardedMissionSubmitScreen = withRoleGuard(MissionSubmitScreen, childOnly, 'ParentHome', true);
const GuardedRejectResubmitScreen = withRoleGuard(RejectResubmitScreen, childOnly, 'ParentHome', true);
const GuardedBankAccountRegisterScreen = withRoleGuard(BankAccountRegisterScreen, childOnly, 'ParentHome', true);
const GuardedChildWithdrawalScreen = withRoleGuard(ChildWithdrawalScreen, childOnly, 'ParentHome', true);

export function AppNavigator() {
  return (
    <Stack.Navigator
      initialRouteName="Login"
      screenOptions={{
        contentStyle: { backgroundColor: '#F5F7F8' },
        headerShown: false,
      }}
    >
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="SignupRole" component={SignupRoleScreen} />
      <Stack.Screen name="ParentFamilyLink" component={GuardedParentFamilyLinkScreen} />
      <Stack.Screen name="ChildInviteCode" component={GuardedChildInviteCodeScreen} />
      <Stack.Screen name="ParentHome" component={GuardedParentHomeScreen} />
      <Stack.Screen name="CreditCharge" component={GuardedCreditChargeScreen} />
      <Stack.Screen name="MissionCreate" component={GuardedMissionCreateScreen} />
      <Stack.Screen name="ParentApproval" component={GuardedParentApprovalScreen} />
      <Stack.Screen name="ChildHome" component={GuardedChildHomeScreen} />
      <Stack.Screen name="MissionSubmit" component={GuardedMissionSubmitScreen} />
      <Stack.Screen name="RejectResubmit" component={GuardedRejectResubmitScreen} />
      <Stack.Screen name="BankAccountRegister" component={GuardedBankAccountRegisterScreen} />
      <Stack.Screen name="ChildWithdrawal" component={GuardedChildWithdrawalScreen} />
    </Stack.Navigator>
  );
}
