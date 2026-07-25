import { useQuery } from '@tanstack/react-query'

import {
  getOperationalSummary,
  type ReportWindow,
} from '../api/getOperationalSummary'
import { reportingQueryKeys } from './reportingQueryKeys'
import { useReportingSessionExpiry } from './useReportingSessionExpiry'

export function useOperationalSummary(
  userId: string,
  window: ReportWindow,
) {
  const query = useQuery({
    queryKey:
      reportingQueryKeys
        .operationalSummary(
          userId,
          window,
        ),
    queryFn: ({ signal }) =>
      getOperationalSummary(
        window,
        signal,
      ),
    retry: false,
    staleTime: 10_000,
  })

  useReportingSessionExpiry(query.error)
  return query
}
