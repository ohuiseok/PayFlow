import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { familyApi } from '../../api/familyApi';
import { ApiErrorBox } from '../../components/common/ApiErrorBox';
import { FormField, InfoBox, PrimaryButton, ScreenFrame } from '../../components/common';
import { appConfig } from '../../config/appConfig';
import { RootStackParamList } from '../../navigation/routes';
import { useAppState } from '../../state/AppState';
import { getErrorMessage } from '../../utils/apiError';

type Props = NativeStackScreenProps<RootStackParamList, 'ParentFamilyLink'>;

export function ParentFamilyLinkScreen({ navigation }: Props) {
  const { completeFamilyLink } = useAppState();
  const queryClient = useQueryClient();
  const [childUserId, setChildUserId] = useState('2');
  const [apiError, setApiError] = useState('');
  const valid = Number(childUserId) > 0;
  const linkMutation = useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        completeFamilyLink();
        return;
      }

      await familyApi.requestLink(childUserId);
    },
    onMutate: () => {
      setApiError('');
    },
    onSuccess: () => {
      completeFamilyLink();
      queryClient.invalidateQueries({ queryKey: ['family'] });
      navigation.replace('ParentHome');
    },
    onError: (error) => {
      setApiError(getErrorMessage(error, 'Family link failed.'));
    },
  });

  return (
    <ScreenFrame
      eyebrow="Family link"
      title="Connect a child"
      description="Enter the child user ID and create the parent-child link."
    >
      <InfoBox
        tone="blue"
        title="Current backend flow"
        body="The reward service links a parent directly to a child user ID."
      />
      <FormField
        label="Child user ID"
        placeholder="2"
        value={childUserId}
        onChangeText={setChildUserId}
        keyboardType="number-pad"
        disabled={linkMutation.isPending}
        error={valid ? undefined : 'Enter a valid child user ID.'}
      />
      <ApiErrorBox error={apiError} fallback="Family link failed." />
      <PrimaryButton
        title={linkMutation.isPending ? 'Connecting' : 'Connect child'}
        onPress={() => linkMutation.mutate()}
        disabled={!valid || linkMutation.isPending}
        loading={linkMutation.isPending}
      />
    </ScreenFrame>
  );
}
