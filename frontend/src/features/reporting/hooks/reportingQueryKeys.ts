import type { AuditSearchParameters } from '../api/auditEvent'
import type { ReportWindow } from '../api/getOperationalSummary'

export const reportingQueryKeys = {
  all: (userId: string) =>
    ['reporting', userId] as const,
  auditEvents: (
    userId: string,
    search: AuditSearchParameters,
  ) =>
    [
      'reporting',
      userId,
      'audit-events',
      search,
    ] as const,
  operationalSummary: (
    userId: string,
    window: ReportWindow,
  ) =>
    [
      'reporting',
      userId,
      'operational-summary',
      window,
    ] as const,
}
