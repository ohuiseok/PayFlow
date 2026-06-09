import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { RootStackParamList } from './routes';
import {
  BankAccountRegisterScreen,
  ChildHomeScreen,
  ChildInviteCodeScreen,
  ChildWithdrawalScreen,
  CreditChargeScreen,
  LoginScreen,
  MissionCreateScreen,
  MissionSubmitScreen,
  ParentApprovalScreen,
  ParentFamilyLinkScreen,
  ParentHomeScreen,
  RejectResubmitScreen,
  SignupRoleScreen,
} from '../screens/FlowScreens';

const Stack = createNativeStackNavigator<RootStackParamList>();

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
      <Stack.Screen name="ParentFamilyLink" component={ParentFamilyLinkScreen} />
      <Stack.Screen name="ChildInviteCode" component={ChildInviteCodeScreen} />
      <Stack.Screen name="ParentHome" component={ParentHomeScreen} />
      <Stack.Screen name="CreditCharge" component={CreditChargeScreen} />
      <Stack.Screen name="MissionCreate" component={MissionCreateScreen} />
      <Stack.Screen name="ParentApproval" component={ParentApprovalScreen} />
      <Stack.Screen name="ChildHome" component={ChildHomeScreen} />
      <Stack.Screen name="MissionSubmit" component={MissionSubmitScreen} />
      <Stack.Screen name="RejectResubmit" component={RejectResubmitScreen} />
      <Stack.Screen name="BankAccountRegister" component={BankAccountRegisterScreen} />
      <Stack.Screen name="ChildWithdrawal" component={ChildWithdrawalScreen} />
    </Stack.Navigator>
  );
}
