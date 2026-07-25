import { useQuery } from '@tanstack/react-query'

import type { AuditSearchParameters } from '../api/auditEvent'
import { getAuditEvents } from '../api/getAuditEvents'
import { reportingQueryKeys } from './reportingQueryKeys'
import { useReportingSessionExpiry } from './useReportingSessionExpiry'

export function useAuditEvents(
  userId: string,
  search: AuditSearchParameters,
) {
  const query = useQuery({
    queryKey:
      reportingQueryKeys.auditEvents(
        userId,
        search,
      ),
    queryFn: ({ signal }) =>
      getAuditEvents(search, signal),
    retry: false,
    staleTime: 10_000,
  })

  useReportingSessionExpiry(query.error)
  return query
}
