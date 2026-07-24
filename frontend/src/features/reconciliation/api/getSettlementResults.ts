import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isSettlementResultPage,
  type SettlementResultPage,
} from './settlement'

export interface SettlementResultCursor {
  afterRowNumber: number
}

export function getSettlementResults(
  importId: string,
  cursor: SettlementResultCursor,
  signal?: AbortSignal,
): Promise<SettlementResultPage> {
  const parameters = new URLSearchParams({
    afterRowNumber:
      cursor.afterRowNumber.toString(),
    limit: '50',
  })

  return apiRequestJson(
    `/api/v1/settlement-imports/${importId}/results?${parameters.toString()}`,
    {
      contractName:
        'Settlement reconciliation results',
      validate: isSettlementResultPage,
      signal,
    },
  )
}
