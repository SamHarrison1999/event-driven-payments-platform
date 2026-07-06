import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

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
    retry: false,
  })
}
