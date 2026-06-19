import { LinkingOptions } from '@react-navigation/native';

export type RootStackParamList = {
  Login: undefined;
  SignupRole: undefined;
  ParentFamilyLink: undefined;
  ChildInviteCode: undefined;
  ParentHome: undefined;
  CreditCharge: undefined;
  MissionCreate: undefined;
  ParentApproval: undefined;
  ChildHome: undefined;
  MissionSubmit: { missionId?: string } | undefined;
  RejectResubmit: { missionId?: string } | undefined;
  BankAccountRegister: undefined;
  ChildWithdrawal: undefined;
};

export const linking: LinkingOptions<RootStackParamList> = {
  prefixes: [],
  config: {
    initialRouteName: 'Login',
    screens: {
      Login: 'login',
      SignupRole: 'signup',
      ParentFamilyLink: 'parent/family-link',
      ChildInviteCode: 'child/invite-code',
      ParentHome: 'parent/home',
      CreditCharge: 'parent/credit-charge',
      MissionCreate: 'parent/missions/new',
      ParentApproval: 'parent/approvals',
      ChildHome: 'child/home',
      MissionSubmit: 'child/missions/:missionId?/submit',
      RejectResubmit: 'child/missions/:missionId?/resubmit',
      BankAccountRegister: 'child/bank-account',
      ChildWithdrawal: 'child/withdrawal',
    },
  },
} as const;
