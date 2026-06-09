import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { familyApi } from '../../api/familyApi';
import { appConfig } from '../../config/appConfig';
import { BalanceCard, Body, Card, Heading, InfoBox, Label, PrimaryButton, ScreenFrame, SecondaryButton } from '../../components/common';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentFamilyLink'>;

export function ParentFamilyLinkScreen({ navigation }: Props) {
  const { completeFamilyLink, inviteCode } = useAppState();
  const [requested, setRequested] = useState(false);
  const [apiInviteCode, setApiInviteCode] = useState(inviteCode);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (appConfig.useDummyData) {
      return;
    }

    let mounted = true;
    setLoading(true);
    familyApi
      .createInvitation()
      .then((invitation) => {
        if (mounted) {
          setApiInviteCode(invitation.inviteCode);
          setError('');
        }
      })
      .catch((invitationError) => {
        if (mounted) {
          setError(invitationError instanceof Error ? invitationError.message : '초대 코드 생성에 실패했습니다.');
        }
      })
      .finally(() => {
        if (mounted) {
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  const approve = () => {
    completeFamilyLink();
    navigation.replace('ParentHome');
  };

  return (
    <ScreenFrame eyebrow="가족 연결" title="자녀와 연결하기" description="초대 코드로 부모와 자녀 관계를 확인합니다.">
      <BalanceCard label="부모 초대 코드" amount={0} description={loading ? '생성 중' : apiInviteCode} />
      {error ? <InfoBox tone="yellow" title="API 오류" body={error} /> : null}
      <Card>
        <Heading>연결 대기 중</Heading>
        <Body>
          {appConfig.useDummyData
            ? '민지가 코드를 입력하면 승인 요청이 표시됩니다.'
            : '자녀가 코드를 입력해 연결 요청을 보내면 가족 목록 API로 연결 상태를 확인합니다.'}
        </Body>
      </Card>
      <Card tone={requested ? 'green' : 'yellow'}>
        <Label>{requested ? '요청 도착' : '요청 시뮬레이션'}</Label>
        <Heading>민지 · 자녀 계정</Heading>
        <Body>{requested ? '가족으로 연결할까요?' : '자녀 앱에서 코드를 입력한 상태를 만들어봅니다.'}</Body>
      </Card>
      {appConfig.useDummyData ? (
        requested ? (
          <View style={styles.twoButtons}>
            <PrimaryButton title="승인" onPress={approve} />
            <SecondaryButton title="거절" onPress={() => setRequested(false)} />
          </View>
        ) : (
          <PrimaryButton title="요청 도착시키기" onPress={() => setRequested(true)} />
        )
      ) : (
        <PrimaryButton title="가족 연결 확인" onPress={approve} disabled={loading || !!error} />
      )}
    </ScreenFrame>
  );
}

const styles = StyleSheet.create({
  twoButtons: {
    flexDirection: 'row',
    gap: 12,
  },
});
