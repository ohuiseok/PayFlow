import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { appConfig } from '../../config/appConfig';
import { CodeInputCells } from '../../components/family/CodeInputCells';
import { Body, Card, FormField, Heading, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';
import { isValidInviteCode, onlyAlphaNumeric } from '../../utils/validators';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildInviteCode'>;

export function ChildInviteCodeScreen({ navigation }: Props) {
  const { completeFamilyLink } = useAppState();
  const [code, setCode] = useState('PF4829');
  const [requested, setRequested] = useState(false);
  const [parentName, setParentName] = useState('지훈');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const valid = isValidInviteCode(code);

  const request = async () => {
    if (appConfig.useDummyData) {
      if (!requested) {
        setRequested(true);
        return;
      }
      completeFamilyLink();
      navigation.replace('ChildHome');
      return;
    }

    if (!valid) {
      setError('초대 코드 6자리를 입력하세요.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      if (!requested) {
        const invitation = await familyApi.getInvitation(onlyAlphaNumeric(code));
        setParentName(invitation.parentName ?? '부모님');
        await familyApi.requestLink(invitation.inviteCode);
        setRequested(true);
        return;
      }

      const family = await familyApi.getMyFamilies();
      if (family.linked) {
        completeFamilyLink();
        navigation.replace('ChildHome');
        return;
      }

      setError('아직 부모 승인 대기 중입니다.');
    } catch (requestError) {
      setError(getErrorMessage(requestError, '연결 요청에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame eyebrow="자녀 가족 연결" title="초대 코드 입력" description="부모님 앱에 표시된 코드를 입력하세요.">
      <InfoBox tone="blue" title="연결 방법" body="부모 초대 코드 6자리를 입력하면 부모 승인 후 가족으로 연결됩니다." />
      <CodeInputCells code={code} />
      <FormField
        placeholder="초대 코드 6자리"
        value={code}
        onChangeText={setCode}
        disabled={loading || requested}
        error={error}
      />
      <Card>
        <Heading>부모 정보 확인</Heading>
        <Body>{parentName}님 가족으로 연결 요청을 보냅니다.</Body>
      </Card>
      <InfoBox tone="yellow" title="요청 후 상태" body={requested ? '요청 완료 · 부모 승인 대기' : '아직 요청하지 않았습니다.'} />
      <PrimaryButton
        title={loading ? '처리 중' : requested ? '승인 상태 확인' : '연결 요청 보내기'}
        onPress={request}
        disabled={!valid || loading}
        loading={loading}
      />
    </ScreenFrame>
  );
}
