import { useCallback, useEffect, useRef, useState } from 'react';

import { ProcessingStatus } from '../types';

type PollResult = {
  status: ProcessingStatus;
};

type PollOptions<T extends PollResult> = {
  poll: () => Promise<T>;
  onResult: (result: T) => void;
  onError: (error: unknown) => void;
  onTimeout?: () => void;
  intervalMs?: number;
  timeoutMs?: number;
};

function isTerminal(status: ProcessingStatus) {
  return status !== 'processing';
}

export function useProcessingPolling() {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startedAtRef = useRef(0);
  const [polling, setPolling] = useState(false);

  const stopPolling = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setPolling(false);
  }, []);

  const pollProcessing = useCallback(
    <T extends PollResult>({
      poll,
      onResult,
      onError,
      onTimeout,
      intervalMs = 2000,
      timeoutMs = 60000,
    }: PollOptions<T>) => {
      stopPolling();
      startedAtRef.current = Date.now();
      setPolling(true);

      const tick = async () => {
        try {
          const result = await poll();
          onResult(result);

          if (isTerminal(result.status)) {
            stopPolling();
            return;
          }

          if (Date.now() - startedAtRef.current >= timeoutMs) {
            onTimeout?.();
            stopPolling();
            return;
          }

          timerRef.current = setTimeout(tick, intervalMs);
        } catch (error) {
          onError(error);
          stopPolling();
        }
      };

      timerRef.current = setTimeout(tick, intervalMs);
    },
    [stopPolling],
  );

  useEffect(() => stopPolling, [stopPolling]);

  return {
    pollProcessing,
    polling,
    stopPolling,
  };
}
