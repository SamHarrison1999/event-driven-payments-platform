import { apiRequestJson } from '../../../shared/api/apiClient'
import { getCsrfHeaders } from '../../../shared/api/csrfToken'
import {
  isOutboxReplayResult,
  type OutboxReplayResult,
} from './deadLetter'

export interface ReplayDeadLetterInput {
  eventId: string
  reason: string
  expectedVersion: number
}

export async function replayDeadLetter(
  input: ReplayDeadLetterInput,
): Promise<OutboxReplayResult> {
  const csrfHeaders = await getCsrfHeaders()

  return apiRequestJson(
    `/api/v1/admin/outbox/dead-letters/${input.eventId}/replay`,
    {
      method: 'POST',
      headers: csrfHeaders,
      body: {
        reason: input.reason,
        expectedVersion:
          input.expectedVersion,
      },
      contractName: 'Outbox dead-letter replay',
      expectedStatus: 200,
      validate: isOutboxReplayResult,
    },
  )
}
