import { useQuery } from '@tanstack/react-query'

import { getDeadLetters } from '../api/getDeadLetters'
import { deadLetterQueryKeys } from './deadLetterQueryKeys'

export function useDeadLetters() {
  return useQuery({
    queryKey: deadLetterQueryKeys.list,
    queryFn: ({ signal }) =>
      getDeadLetters(signal),
    retry: false,
    staleTime: 10_000,
  })
}
