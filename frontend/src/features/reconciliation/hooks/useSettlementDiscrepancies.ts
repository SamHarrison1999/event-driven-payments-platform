import { useInfiniteQuery } from '@tanstack/react-query'

import {
  getSettlementDiscrepancies,
  type SettlementDiscrepancyCursor,
} from '../api/getSettlementDiscrepancies'
import type { SettlementDiscrepancyStatus } from '../api/settlement'
import { reconciliationQueryKeys } from './reconciliationQueryKeys'

export function useSettlementDiscrepancies(
  userId: string,
  status: SettlementDiscrepancyStatus,
) {
  return useInfiniteQuery({
    queryKey:
      reconciliationQueryKeys.discrepancies(
        userId,
        status,
      ),
    queryFn: ({ pageParam, signal }) =>
      getSettlementDiscrepancies(
        status,
        pageParam,
        signal,
      ),
    initialPageParam: {
      afterCreatedAt: null,
      afterId: null,
    } as SettlementDiscrepancyCursor,
    getNextPageParam: (
      lastPage,
    ): SettlementDiscrepancyCursor | undefined =>
      lastPage.nextAfterCreatedAt === null ||
      lastPage.nextAfterId === null
        ? undefined
        : {
            afterCreatedAt:
              lastPage.nextAfterCreatedAt,
            afterId: lastPage.nextAfterId,
          },
    retry: false,
    staleTime: 10_000,
  })
}
