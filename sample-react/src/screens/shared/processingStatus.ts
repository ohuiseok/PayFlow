import { ProcessingStatus } from '../../types';

export const processingStatusLabels: Record<
  ProcessingStatus,
  { title: string; body: string; tone: 'green' | 'blue' | 'yellow' }
> = {
  idle: { title: '대기', body: '요청 전입니다.', tone: 'blue' },
  processing: { title: '처리 중', body: '요청 결과를 확인하고 있습니다.', tone: 'yellow' },
  completed: { title: '완료', body: '요청이 정상 처리되었습니다.', tone: 'green' },
  failed: { title: '실패', body: '요청 처리에 실패했습니다. 다시 시도해 주세요.', tone: 'yellow' },
  unknown: { title: '확인 필요', body: '결과를 확정하지 못했습니다. 잠시 후 다시 확인해 주세요.', tone: 'yellow' },
  compensationRequired: { title: '고객센터 확인 필요', body: '보상 처리가 필요한 상태입니다.', tone: 'yellow' },
};

export function processingLabel(status: ProcessingStatus) {
  return processingStatusLabels[status];
}
