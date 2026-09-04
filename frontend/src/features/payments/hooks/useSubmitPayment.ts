import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { accountQueryKeys } from '../../accounts/hooks/accountQueryKeys'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'
import { useCurrentSession } from '../../identity/hooks/useCurrentSession'
import { submitPaymentIdempotently } from '../api/submitPaymentIdempotently'
import type { PaymentDraft } from '../model/paymentDraft'
import { notificationQueryKeys } from '../../notifications/hooks/notificationQueryKeys'

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
    onError: (error) => {
      if (isApiErrorWithStatus(error, 401)) {
        expireCurrentSession(queryClient)
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: accountQueryKeys.all,
      })

      void queryClient.invalidateQueries({
        queryKey: notificationQueryKeys.all,
      })

      window.setTimeout(() => {
        void queryClient.invalidateQueries({
          queryKey: notificationQueryKeys.all,
        })
      }, 2_500)

      window.setTimeout(() => {
        void queryClient.invalidateQueries({
          queryKey: notificationQueryKeys.all,
        })
      }, 5_000)
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
