import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { login } from '../api/login'
import { identityQueryKeys } from './identityQueryKeys'
import { clearCustomerQueries } from './sessionCache'

export function useLogin() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,
    onSuccess: (session) => {
      clearCustomerQueries(queryClient)

      queryClient.setQueryData(
        identityQueryKeys.session,
        session,
      )
    },
  })
}
