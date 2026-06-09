import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { RoleSelectCard } from '../../components/auth/RoleSelectCard';
import { authApi } from '../../api/authApi';
import { appConfig } from '../../config/appConfig';
import { FormField, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { UserRole } from '../../types';

type Props = NativeStackScreenProps<RootStackParamList, 'SignupRole'>;

export function SignupRoleScreen({ navigation }: Props) {
  const { signupAs } = useAppState();
  const [role, setRole] = useState<UserRole>('parent');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const canSubmit = name.trim().length > 1 && phone.replace(/[^0-9]/g, '').length >= 10 && password.length >= 8;

  const submit = async () => {
    if (!canSubmit) {
      setError('이름, 휴대폰 번호, 8자리 이상 비밀번호를 입력하세요.');
      return;
    }

    setError('');
    setLoading(true);

    try {
      if (appConfig.useDummyData) {
        signupAs(role, name);
        navigation.replace(role === 'parent' ? 'ParentFamilyLink' : 'ChildInviteCode');
        return;
      }

      const user = await authApi.signup({
        name: name.trim(),
        phoneNumber: phone.replace(/[^0-9]/g, ''),
        password,
        role,
      });
      signupAs(user.role, user.name, user.userId);
      navigation.replace(user.role === 'parent' ? 'ParentFamilyLink' : 'ChildInviteCode');
    } catch (signupError) {
      setError(signupError instanceof Error ? signupError.message : '회원가입에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame eyebrow="회원가입" title="어떤 계정인가요?" description="역할에 따라 홈 화면과 권한이 달라집니다.">
      <View style={styles.roleCards}>
        <RoleSelectCard
          label="부모 계정"
          title="미션을 만들고 보상 지급"
          description="크레딧 충전, 자녀 연결, 제출 승인"
          selected={role === 'parent'}
          onPress={() => setRole('parent')}
        />
        <RoleSelectCard
          label="자녀 계정"
          title="미션을 완료하고 돈 벌기"
          description="미션 확인, 완료 제출, 캐시북 기록"
          selected={role === 'child'}
          onPress={() => setRole('child')}
        />
      </View>
      <FormField placeholder="이름" value={name} onChangeText={setName} disabled={loading} />
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
      {!appConfig.useDummyData ? (
        <InfoBox tone="blue" title="API 회원가입" body="가입 응답의 role 값으로 다음 화면을 결정합니다." />
      ) : null}
      <PrimaryButton title={loading ? '처리 중' : '다음으로'} onPress={submit} disabled={!canSubmit || loading} loading={loading} />
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  roleCards: {
    gap: 14,
    marginBottom: 20,
  },
});
