import {
  ApiContractError,
  apiRequestBlobResponse,
} from '../../../shared/api/apiClient'
import type { ReportWindow } from './getOperationalSummary'

export const reportFamilies = [
  'audit-events',
  'payments',
  'settlements',
  'reconciliation',
] as const

export type ReportFamily =
  (typeof reportFamilies)[number]

export interface ReportDownload {
  blob: Blob
  filename: string
}

export async function downloadReport(
  family: ReportFamily,
  window: ReportWindow,
  signal?: AbortSignal,
): Promise<ReportDownload> {
  const parameters = new URLSearchParams({
    from: window.from,
    to: window.to,
  })
  const filename = `${family}.csv`
  const response =
    await apiRequestBlobResponse(
      `/api/v1/reports/${filename}?${parameters.toString()}`,
      {
        contractName: `${family} report`,
        expectedMediaType: 'text/csv',
        headers: {
          Accept: 'text/csv',
        },
        signal,
      },
    )
  const disposition =
    response.headers.get(
      'content-disposition',
    )

  if (
    disposition !==
    `attachment; filename="${filename}"`
  ) {
    throw new ApiContractError(
      `${family} report response used an unsafe filename.`,
    )
  }

  return {
    blob: response.blob,
    filename,
  }
}
