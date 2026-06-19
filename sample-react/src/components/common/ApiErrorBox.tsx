import { InfoBox } from '../common';
import { getErrorMessage } from '../../utils/apiError';

export function ApiErrorBox({ error, fallback }: { error: unknown; fallback: string }) {
  if (!error) {
    return null;
  }

  return <InfoBox tone="yellow" title="서버 오류" body={getErrorMessage(error, fallback)} />;
}
