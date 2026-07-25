import {
  useEffect,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'

export function useReportingSessionExpiry(
  error: unknown,
): void {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (isApiErrorWithStatus(error, 401)) {
      expireCurrentSession(queryClient)
    }
  }, [error, queryClient])
}
