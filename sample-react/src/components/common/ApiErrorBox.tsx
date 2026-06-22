import { useEffect } from 'react';

import { getErrorMessage } from '../../utils/apiError';

export function ApiErrorBox({ error, fallback }: { error: unknown; fallback: string }) {
  useEffect(() => {
    if (!error) {
      return;
    }

    console.error(getErrorMessage(error, fallback), error);
  }, [error, fallback]);

  return null;
}
