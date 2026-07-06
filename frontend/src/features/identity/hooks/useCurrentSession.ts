import { useQuery } from '@tanstack/react-query'

import { getCurrentSession } from '../api/getCurrentSession'
import { identityQueryKeys } from './identityQueryKeys'

export function useCurrentSession() {
  return useQuery({
    queryKey: identityQueryKeys.session,
    queryFn: ({ signal }) =>
      getCurrentSession(signal),
    retry: false,
    staleTime: 30_000,
  })
}
