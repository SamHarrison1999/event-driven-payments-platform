import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'
import {
  resolveSettlementDiscrepancy,
  type ResolveSettlementDiscrepancyInput,
} from '../api/resolveSettlementDiscrepancy'
import { reconciliationQueryKeys } from './reconciliationQueryKeys'

export function useResolveSettlementDiscrepancy(
  userId: string,
) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (
      input: ResolveSettlementDiscrepancyInput,
    ) =>
      resolveSettlementDiscrepancy(input),
    onError: (error, input) => {
      if (isApiErrorWithStatus(error, 401)) {
        expireCurrentSession(queryClient)
        return
      }

      if (isApiErrorWithStatus(error, 412)) {
        void queryClient.invalidateQueries({
          queryKey:
            reconciliationQueryKeys
              .discrepancy(
                userId,
                input.discrepancyId,
              ),
        })
      }

      if (
        isApiErrorWithStatus(error, 412) ||
        isApiErrorWithStatus(error, 409)
      ) {
        void queryClient.invalidateQueries({
          queryKey:
            reconciliationQueryKeys
              .discrepancyQueues(userId),
        })
      }
    },
    onSuccess: (discrepancy) => {
      queryClient.setQueryData(
        reconciliationQueryKeys.discrepancy(
          userId,
          discrepancy.discrepancyId,
        ),
        {
          discrepancy,
          etag: `"${discrepancy.version.toString()}"`,
        },
      )

      return queryClient.invalidateQueries({
        queryKey:
          reconciliationQueryKeys
            .discrepancyQueues(userId),
      })
    },
    retry: false,
  })
}
