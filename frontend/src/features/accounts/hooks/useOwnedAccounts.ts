import { useQuery } from '@tanstack/react-query'

import { getOwnedAccounts } from '../api/getOwnedAccounts'
import { accountQueryKeys } from './accountQueryKeys'

export function useOwnedAccounts() {
  return useQuery({
    queryKey: accountQueryKeys.owned,
    queryFn: ({ signal }) =>
      getOwnedAccounts(signal),
    retry: false,
    staleTime: 30_000,
  })
}
