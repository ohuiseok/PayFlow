import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { Body, Card, Heading, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ChildInviteCode'>;

export function ChildInviteCodeScreen({ navigation }: Props) {
  const { completeFamilyLink, currentUserId } = useAppState();
  const [apiError, setApiError] = useState('');
  const [loading, setLoading] = useState(false);

  const checkLink = async () => {
    if (appConfig.useDummyData) {
      completeFamilyLink();
      navigation.replace('ChildHome');
      return;
    }

    setLoading(true);
    setApiError('');

    try {
      const family = await familyApi.getMyParents();
      if (!family.linked) {
        setApiError('Parent link is not active yet. Share your user ID with your parent first.');
        return;
      }

      completeFamilyLink();
      navigation.replace('ChildHome');
    } catch (error) {
      setApiError(getErrorMessage(error, 'Failed to check family link.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScreenFrame
      eyebrow="Family link"
      title="Share your user ID"
      description="Ask your parent to enter this user ID from the parent account."
    >
      <Card tone="blue">
        <Heading>Your user ID</Heading>
        <Body>{currentUserId}</Body>
      </Card>
      <InfoBox
        tone="yellow"
        title="Backend flow"
        body="The current API creates an active family link when a parent enters the child user ID."
      />
      <ApiErrorBox error={apiError} fallback="Failed to check family link." />
      <PrimaryButton
        title={loading ? 'Checking' : 'Check link status'}
        onPress={checkLink}
        disabled={loading}
        loading={loading}
      />
    </ScreenFrame>
  );
}
