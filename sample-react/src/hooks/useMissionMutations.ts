import { useMutation, useQueryClient } from '@tanstack/react-query';

import { missionApi } from '../api/missionApi';
import { appConfig } from '../config/appConfig';
import { getErrorMessage } from '../utils/apiError';

export function useSubmitMissionMutation({
  missionId,
  memo,
  onDummySubmit,
  onError,
  onSuccess,
}: {
  missionId: string;
  memo: string;
  onDummySubmit: () => void;
  onError: (message: string) => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        onDummySubmit();
        return;
      }

      await missionApi.submitMission({ missionId, memo });
    },
    onMutate: () => {
      onError('');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['missions'] });
      onSuccess();
    },
    onError: (error) => {
      onError(getErrorMessage(error, '미션 제출에 실패했습니다.'));
    },
  });
}

export function useResubmitMissionMutation({
  missionId,
  memo,
  onDummyResubmit,
  onError,
  onSuccess,
}: {
  missionId: string;
  memo: string;
  onDummyResubmit: () => void;
  onError: (message: string) => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      if (appConfig.useDummyData) {
        onDummyResubmit();
        return;
      }

      await missionApi.resubmitMission({ missionId, memo });
    },
    onMutate: () => {
      onError('');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['missions'] });
      onSuccess();
    },
    onError: (error) => {
      onError(getErrorMessage(error, '미션 재제출에 실패했습니다.'));
    },
  });
}
