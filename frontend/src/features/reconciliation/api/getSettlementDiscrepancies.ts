import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isSettlementDiscrepancyPage,
  type SettlementDiscrepancyPage,
  type SettlementDiscrepancyStatus,
} from './settlement'

export interface SettlementDiscrepancyCursor {
  afterCreatedAt: string | null
  afterId: string | null
}

export function getSettlementDiscrepancies(
  status: SettlementDiscrepancyStatus,
  cursor: SettlementDiscrepancyCursor,
  signal?: AbortSignal,
): Promise<SettlementDiscrepancyPage> {
  const parameters = new URLSearchParams({
    status,
    limit: '50',
  })

  if (
    cursor.afterCreatedAt !== null &&
    cursor.afterId !== null
  ) {
    parameters.set(
      'afterCreatedAt',
      cursor.afterCreatedAt,
    )
    parameters.set('afterId', cursor.afterId)
  }

  return apiRequestJson(
    `/api/v1/settlement-discrepancies?${parameters.toString()}`,
    {
      contractName:
        'Settlement discrepancy queue',
      validate: isSettlementDiscrepancyPage,
      signal,
    },
  )
}
