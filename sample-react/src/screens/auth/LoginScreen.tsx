import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native';

import { RoleSwitch } from '../../components/auth/RoleSwitch';
import { colors, FormField, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { authApi } from '../../api/authApi';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { UserRole } from '../../types';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export function LoginScreen({ navigation }: Props) {
  const { loginAs, familyLinked } = useAppState();
  const [phone, setPhone] = useState('01012345678');
  const [password, setPassword] = useState('password12');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const moveAfterAuth = (role: UserRole) => {
    if (role === 'parent') {
      navigation.replace(familyLinked ? 'ParentHome' : 'ParentFamilyLink');
      return;
    }
    navigation.replace(familyLinked ? 'ChildHome' : 'ChildInviteCode');
  };

  const login = async (dummyRole: UserRole = 'parent') => {
    if (phone.replace(/[^0-9]/g, '').length < 10 || password.length < 8) {
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
        phoneNumber: phone.replace(/[^0-9]/g, ''),
        password,
      });
      loginAs(user.role, user.name, user.userId);
      moveAfterAuth(user.role);
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : '로그인에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame>
      <View style={styles.loginCard}>
        <Text style={styles.brand}>PayFlow Family</Text>
        <Text style={styles.loginTitle}>미션으로 배우는 돈</Text>
        <Text style={styles.loginSub}>부모가 미션을 만들고 자녀가 보상을 벌어요.</Text>
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
        <PrimaryButton title={loading ? '로그인 중' : '로그인'} onPress={() => login('parent')} variant="dark" loading={loading} />
        <TouchableOpacity style={styles.linkWrap} onPress={() => navigation.navigate('SignupRole')}>
          <Text style={styles.linkText}>
            처음이신가요? <Text style={styles.linkStrong}>회원가입</Text>
          </Text>
        </TouchableOpacity>
        {appConfig.useDummyData ? (
          <>
            <InfoBox title="역할 선택" body="로그인은 부모 계정으로 시작합니다. 아래 버튼으로 자녀 홈도 바로 확인할 수 있어요." />
            <RoleSwitch onSelect={login} />
          </>
        ) : (
          <InfoBox title="API 로그인" body="로그인 응답의 role 값으로 부모/자녀 화면을 자동 분기합니다." />
        )}
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
    fontSize: 32,
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
