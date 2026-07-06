import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { accountQueryKeys } from '../../accounts/hooks/accountQueryKeys'
import { useCurrentSession } from '../../identity/hooks/useCurrentSession'
import { submitPaymentIdempotently } from '../api/submitPaymentIdempotently'
import type { PaymentDraft } from '../model/paymentDraft'

export function useSubmitPayment() {
  const currentSession =
    useCurrentSession()
  const queryClient = useQueryClient()
  const identityUserId =
    currentSession.data?.userId

  const mutation = useMutation({
    mutationFn: (draft: PaymentDraft) => {
      if (identityUserId === undefined) {
        throw new Error(
          'An authenticated customer session is required to submit a payment.',
        )
      }

      return submitPaymentIdempotently(
        identityUserId,
        draft,
      )
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: accountQueryKeys.all,
      })
    },
    retry: false,
  })

  return {
    ...mutation,
    canSubmit:
      currentSession.isSuccess &&
      identityUserId !== undefined,
  }
}
