import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { authApi } from '../../api/authApi';
import { RoleSwitch } from '../../components/auth/RoleSwitch';
import { colors, FormField, PrimaryButton, ScreenFrame } from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { UserRole } from '../../types';
import { getErrorMessage } from '../../utils/apiError';
import { isValidPassword, isValidPhoneNumber, onlyDigits } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const { isRestoringSession, loginAs, familyLinked, role } = useAppState();
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // 이미 로그인 상태면 홈으로 자동 이동
  // 딥링크로 [Login, CreditCharge] 같은 스택이 생긴 경우, reset으로 한 번에 재구성해
  // 뒤로가기 시 Login이 아닌 홈으로 가도록 보장한다.
  useEffect(() => {
    if (isRestoringSession || !role) return;
    const homeRoute = role === 'parent' ? 'ParentHome' : (familyLinked ? 'ChildHome' : 'ChildInviteCode');
    const state = navigation.getState();
    if (state && state.routes.length > 1) {
      // Login 아래에 다른 스크린이 있는 딥링크 케이스
      navigation.reset({
        index: state.routes.length - 1,
        routes: [
          { name: homeRoute },
          ...state.routes.slice(1).map((r) => ({ name: r.name, params: r.params })),
        ],
      });
    } else {
      navigation.replace(homeRoute);
    }
  }, [isRestoringSession, role, familyLinked, navigation]);

  const moveAfterAuth = (role: UserRole) => {
    if (role === 'parent') {
      navigation.replace('ParentHome');
      return;
    }
    navigation.replace(familyLinked ? 'ChildHome' : 'ChildInviteCode');
  };

  const login = async (dummyRole: UserRole = 'parent') => {
    if (!isValidPhoneNumber(phone) || !isValidPassword(password)) {
      setError('휴대폰 번호와 8자리 이상 비밀번호를 입력하세요.');
      return;
    }

    setError('');
    setLoading(true);

    try {
      if (appConfig.useDummyData) {
        loginAs(dummyRole);
        moveAfterAuth(dummyRole);
        return;
      }

      const user = await authApi.login({
        phoneNumber: onlyDigits(phone),
        password,
      });
      loginAs(user.role, user.name, user.userId);
      moveAfterAuth(user.role);
    } catch (loginError) {
      console.error(getErrorMessage(loginError, '로그인에 실패했습니다.'), loginError);
      setError(getErrorMessage(loginError, '로그인에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame>
      <View style={styles.loginCard}>
        <Text style={styles.brand}>PayFlow</Text>
        <Text style={styles.loginTitle}>미션으로 배우는 용돈 관리</Text>
        <Text style={styles.loginSub}>부모가 미션을 만들고 자녀가 보상을 받아요.</Text>
        <View style={styles.spacer} />
        <FormField
          placeholder="휴대폰 번호"
          value={phone}
          onChangeText={setPhone}
          keyboardType="phone-pad"
          disabled={loading}
        />
        <FormField
          placeholder="비밀번호"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          error={error}
          disabled={loading}
        />
        <PrimaryButton
          title={loading ? '로그인 중' : '로그인'}
          onPress={() => login('parent')}
          variant="dark"
          loading={loading}
          testID="login-submit-button"
        />
        <TouchableOpacity style={styles.linkWrap} onPress={() => navigation.navigate('SignupRole')} testID="login-signup-link">
          <Text style={styles.linkText}>
            처음이신가요? <Text style={styles.linkStrong}>회원가입</Text>
          </Text>
        </TouchableOpacity>
      </View>
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  loginCard: {
    backgroundColor: '#FFFFFF',
    borderColor: colors.line,
    borderRadius: 8,
    borderWidth: 1,
    marginTop: 30,
    padding: 24,
  },
  brand: {
    color: colors.primary,
    fontSize: 15,
    fontWeight: '900',
    marginBottom: 16,
  },
  loginTitle: {
    color: colors.text,
    fontSize: 28,
    fontWeight: '900',
    lineHeight: 39,
  },
  loginSub: {
    color: colors.muted,
    fontSize: 16,
    marginTop: 8,
  },
  spacer: {
    height: 34,
  },
  linkWrap: {
    alignItems: 'center',
    paddingVertical: 22,
  },
  linkText: {
    color: colors.muted,
    fontSize: 16,
  },
  linkStrong: {
    color: colors.primary,
    fontWeight: '900',
  },
});
