import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isOutboxDeadLetterList,
  type OutboxDeadLetter,
} from './deadLetter'

export function getDeadLetters(
  signal?: AbortSignal,
): Promise<OutboxDeadLetter[]> {
  return apiRequestJson(
    '/api/v1/admin/outbox/dead-letters?limit=50',
    {
      contractName: 'Outbox dead letters',
      validate: isOutboxDeadLetterList,
      signal,
    },
  )
}
