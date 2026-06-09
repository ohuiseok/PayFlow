import { ProcessingStatus } from '../types';

type ApiProcessingStatus =
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'UNKNOWN'
  | 'COMPENSATION_REQUIRED';

export function toProcessingStatus(status: string): ProcessingStatus {
  const normalized = status.toUpperCase() as ApiProcessingStatus;

  switch (normalized) {
    case 'PROCESSING':
      return 'processing';
    case 'COMPLETED':
      return 'completed';
    case 'FAILED':
      return 'failed';
    case 'UNKNOWN':
      return 'unknown';
    case 'COMPENSATION_REQUIRED':
      return 'compensationRequired';
    default:
      return 'unknown';
  }
}
