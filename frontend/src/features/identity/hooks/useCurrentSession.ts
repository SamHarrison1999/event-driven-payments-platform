import { useQuery } from '@tanstack/react-query'

import {
  ApiHttpError,
  ApiNetworkError,
} from '../../../shared/api/apiClient'
import { ApiProblemError } from '../../../shared/api/apiProblem'
import { getCurrentSession } from '../api/getCurrentSession'
import { identityQueryKeys } from './identityQueryKeys'

function shouldRetryCurrentSession(
  failureCount: number,
  error: unknown,
): boolean {
  if (failureCount >= 1) {
    return false
  }

  if (error instanceof ApiNetworkError) {
    return true
  }

  if (
    error instanceof ApiHttpError ||
    error instanceof ApiProblemError
  ) {
    return [502, 503, 504].includes(error.status)
  }

  return false
}

export function useCurrentSession() {
  return useQuery({
    queryKey: identityQueryKeys.session,
    queryFn: ({ signal }) =>
      getCurrentSession(signal),
    retry: shouldRetryCurrentSession,
    retryDelay: 1_000,
    staleTime: 30_000,
  })
}