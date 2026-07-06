import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { logout } from '../api/logout'
import { identityQueryKeys } from './identityQueryKeys'
import { clearCustomerQueries } from './sessionCache'

export function useLogout() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      clearCustomerQueries(queryClient)

      queryClient.setQueryData(
        identityQueryKeys.session,
        null,
      )
    },
  })
}
