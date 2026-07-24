import { apiRequestJson } from '../../../shared/api/apiClient'
import { getCsrfHeaders } from '../../../shared/api/csrfToken'
import {
  isSettlementDiscrepancy,
  type SettlementDiscrepancy,
  type SettlementResolutionDecision,
} from './settlement'

export interface ResolveSettlementDiscrepancyInput {
  discrepancyId: string
  etag: string
  decision: SettlementResolutionDecision
  reason: string
}

export async function resolveSettlementDiscrepancy(
  input: ResolveSettlementDiscrepancyInput,
): Promise<SettlementDiscrepancy> {
  const csrfHeaders = new Headers(
    await getCsrfHeaders(),
  )
  csrfHeaders.set('If-Match', input.etag)

  return apiRequestJson(
    `/api/v1/settlement-discrepancies/${input.discrepancyId}/resolution`,
    {
      method: 'PUT',
      headers: csrfHeaders,
      body: {
        decision: input.decision,
        reason: input.reason,
      },
      contractName:
        'Settlement discrepancy resolution',
      expectedStatus: 200,
      validate: isSettlementDiscrepancy,
    },
  )
}
