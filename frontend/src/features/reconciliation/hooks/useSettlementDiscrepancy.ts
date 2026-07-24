import { useQuery } from '@tanstack/react-query'

import { getSettlementDiscrepancy } from '../api/getSettlementDiscrepancy'
import { reconciliationQueryKeys } from './reconciliationQueryKeys'

export function useSettlementDiscrepancy(
  userId: string,
  discrepancyId: string | null,
) {
  return useQuery({
    queryKey:
      reconciliationQueryKeys.discrepancy(
        userId,
        discrepancyId ?? 'none',
      ),
    queryFn: ({ signal }) =>
      getSettlementDiscrepancy(
        discrepancyId ?? '',
        signal,
      ),
    enabled: discrepancyId !== null,
    retry: false,
    staleTime: 10_000,
  })
}
