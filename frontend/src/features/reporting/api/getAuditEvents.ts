import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isAuditEventPage,
  type AuditEventPage,
  type AuditSearchParameters,
} from './auditEvent'

export function getAuditEvents(
  search: AuditSearchParameters,
  signal?: AbortSignal,
): Promise<AuditEventPage> {
  const parameters = new URLSearchParams({
    from: search.from,
    to: search.to,
    limit: search.limit.toString(),
  })

  const optionalParameters = {
    category: search.category,
    eventType: search.eventType,
    actorIdentityUserId:
      search.actorIdentityUserId,
    subjectType: search.subjectType,
    subjectIdentifier:
      search.subjectIdentifier,
    correlationIdentifier:
      search.correlationIdentifier,
    source: search.source,
    cursor: search.cursor,
  }

  for (
    const [name, value] of
    Object.entries(optionalParameters)
  ) {
    if (value !== undefined) {
      parameters.set(name, value)
    }
  }

  return apiRequestJson(
    `/api/v1/audit-events?${parameters.toString()}`,
    {
      contractName: 'Audit event search',
      validate: isAuditEventPage,
      signal,
    },
  )
}
