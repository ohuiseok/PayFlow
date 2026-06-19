import { ProcessingStatus } from '../types';

type ApiProcessingStatus =
  | 'REQUESTED'
  | 'SUCCEEDED'
  | 'PROCESSING'
  | 'WALLET_WITHDRAWING'
  | 'BANK_PROCESSING'
  | 'BANK_SUCCEEDED'
  | 'WALLET_REFLECTING'
  | 'COMPLETED'
  | 'COMPENSATED'
  | 'FAILED'
  | 'UNKNOWN'
  | 'COMPENSATION_REQUIRED';

export function toProcessingStatus(status: string): ProcessingStatus {
  const normalized = status.toUpperCase() as ApiProcessingStatus;

  switch (normalized) {
    case 'REQUESTED':
    case 'PROCESSING':
    case 'WALLET_WITHDRAWING':
    case 'BANK_PROCESSING':
    case 'BANK_SUCCEEDED':
    case 'WALLET_REFLECTING':
      return 'processing';
    case 'SUCCEEDED':
    case 'COMPLETED':
    case 'COMPENSATED':
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
