import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isOperationalSummary,
  type OperationalSummary,
} from './operationalSummary'

export interface ReportWindow {
  from: string
  to: string
}

export function getOperationalSummary(
  window: ReportWindow,
  signal?: AbortSignal,
): Promise<OperationalSummary> {
  const parameters = new URLSearchParams({
    from: window.from,
    to: window.to,
  })

  return apiRequestJson(
    `/api/v1/reports/operational-summary?${parameters.toString()}`,
    {
      contractName:
        'Operational report summary',
      validate: isOperationalSummary,
      signal,
    },
  )
}
