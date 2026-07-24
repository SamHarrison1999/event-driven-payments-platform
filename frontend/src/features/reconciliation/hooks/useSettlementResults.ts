import { useInfiniteQuery } from '@tanstack/react-query'

import { getSettlementResults } from '../api/getSettlementResults'
import { reconciliationQueryKeys } from './reconciliationQueryKeys'

export function useSettlementResults(
  userId: string,
  importId: string | null,
) {
  return useInfiniteQuery({
    queryKey:
      reconciliationQueryKeys.results(
        userId,
        importId ?? 'none',
      ),
    queryFn: ({ pageParam, signal }) =>
      getSettlementResults(
        importId ?? '',
        pageParam,
        signal,
      ),
    initialPageParam: {
      afterRowNumber: 0,
    },
    getNextPageParam: (lastPage) =>
      lastPage.nextAfterRowNumber === null
        ? undefined
        : {
            afterRowNumber:
              lastPage.nextAfterRowNumber,
          },
    enabled: importId !== null,
    retry: false,
    staleTime: Number.POSITIVE_INFINITY,
  })
}
