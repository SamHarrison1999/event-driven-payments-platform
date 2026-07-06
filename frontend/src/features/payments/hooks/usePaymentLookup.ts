import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'
import { getPayment } from '../api/getPayment'
import { paymentQueryKeys } from './paymentQueryKeys'

export function usePaymentLookup() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (paymentId: string) =>
      queryClient.fetchQuery({
        queryKey:
          paymentQueryKeys.detail(
            paymentId,
          ),
        queryFn: ({ signal }) =>
          getPayment(paymentId, signal),
        staleTime: 0,
      }),
    onError: (error) => {
      if (isApiErrorWithStatus(error, 401)) {
        expireCurrentSession(queryClient)
      }
    },
    retry: false,
  })
}
