import {
  useState,
} from 'react'

import type { IdentityRole } from '../../identity/api/identitySession'
import type {
  ReportFamily,
} from '../api/downloadReport'
import type { ReportWindow } from '../api/getOperationalSummary'
import { useReportDownload } from '../hooks/useReportDownload'

const reportLabels: Record<
  ReportFamily,
  string
> = {
  'audit-events': 'Audit events',
  payments: 'Payments',
  settlements: 'Settlements',
  reconciliation: 'Reconciliation',
}

function permittedReports(
  roles: IdentityRole[],
): ReportFamily[] {
  if (roles.includes('ADMIN')) {
    return [
      'audit-events',
      'payments',
      'settlements',
      'reconciliation',
    ]
  }

  const reports: ReportFamily[] = [
    'audit-events',
  ]

  if (roles.includes('OPERATIONS')) {
    reports.push('payments')
  }

  if (
    roles.includes(
      'RECONCILIATION_ANALYST',
    )
  ) {
    reports.push(
      'settlements',
      'reconciliation',
    )
  }

  return reports
}

function saveDownload(
  blob: Blob,
  filename: string,
) {
  const objectUrl =
    URL.createObjectURL(blob)
  const anchor =
    document.createElement('a')

  anchor.href = objectUrl
  anchor.download = filename
  document.body.append(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(objectUrl)
}

interface ReportDownloadsPanelProps {
  roles: IdentityRole[]
  window: ReportWindow
}

export function ReportDownloadsPanel({
  roles,
  window,
}: ReportDownloadsPanelProps) {
  const downloadMutation =
    useReportDownload()
  const [activeReport, setActiveReport] =
    useState<ReportFamily | null>(null)
  const reports = permittedReports(roles)

  function startDownload(
    family: ReportFamily,
  ) {
    setActiveReport(family)
    downloadMutation.reset()
    downloadMutation.mutate(
      {
        family,
        window,
      },
      {
        onSuccess: (download) => {
          saveDownload(
            download.blob,
            download.filename,
          )
          setActiveReport(null)
        },
        onError: () => {
          setActiveReport(null)
        },
      },
    )
  }

  return (
    <section
      aria-labelledby="report-downloads-title"
      className="workspace-card workspace-card--primary reporting-panel"
      id="report-downloads"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Fixed CSV schemas
          </p>
          <h4 id="report-downloads-title">
            Report downloads
          </h4>
        </div>
        <span className="workspace-status-pill">
          Up to 10,000 rows
        </span>
      </div>

      <p>
        Downloads use the active UTC window and
        contain only the report families permitted
        by the backend role policy.
      </p>

      <div className="report-download-grid">
        {reports.map((report) => (
          <button
            className="report-download-button"
            disabled={
              downloadMutation.isPending
            }
            key={report}
            onClick={() => {
              startDownload(report)
            }}
            type="button"
          >
            <strong>
              {reportLabels[report]}
            </strong>
            <span>
              {activeReport === report
                ? 'Preparing download…'
                : `${report}.csv`}
            </span>
          </button>
        ))}
      </div>

      {downloadMutation.isError && (
        <div
          className="form-error-summary"
          role="alert"
        >
          <strong>Download unavailable</strong>
          <p>
            The report could not be generated.
            Check the bounded window and try again.
          </p>
        </div>
      )}
    </section>
  )
}
