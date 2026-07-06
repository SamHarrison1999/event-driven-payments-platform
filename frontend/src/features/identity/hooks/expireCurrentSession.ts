import type { QueryClient } from '@tanstack/react-query'

import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { identityQueryKeys } from './identityQueryKeys'
import { clearCustomerQueries } from './sessionCache'

export function expireCurrentSession(
  queryClient: QueryClient,
): void {
  clearCsrfToken()
  clearCustomerQueries(queryClient)

  queryClient.setQueryData(
    identityQueryKeys.session,
    null,
  )
}
