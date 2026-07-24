import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'
import { uploadSettlementFile } from '../api/uploadSettlementFile'
import { reconciliationQueryKeys } from './reconciliationQueryKeys'

export function useUploadSettlementFile(
  userId: string,
) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: uploadSettlementFile,
    onError: (error) => {
      if (isApiErrorWithStatus(error, 401)) {
        expireCurrentSession(queryClient)
      }
    },
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey:
          reconciliationQueryKeys
            .discrepancyQueues(userId),
      }),
    retry: false,
  })
}
