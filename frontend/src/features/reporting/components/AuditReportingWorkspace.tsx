import {
  type FormEvent,
  useState,
} from 'react'

import type { IdentityRole } from '../../identity/api/identitySession'
import type { ReportWindow } from '../api/getOperationalSummary'
import { AuditSearchPanel } from './AuditSearchPanel'
import { OperationalSummaryPanel } from './OperationalSummaryPanel'
import { ReportDownloadsPanel } from './ReportDownloadsPanel'

const dayMilliseconds =
  24 * 60 * 60 * 1000

function toInputValue(date: Date): string {
  return date
    .toISOString()
    .slice(0, 16)
}

function initialWindow() {
  const to = new Date()
  to.setUTCSeconds(0, 0)
  const from = new Date(
    to.getTime() -
      7 * dayMilliseconds,
  )

  return {
    from: toInputValue(from),
    to: toInputValue(to),
  }
}

function toInstant(value: string): string {
  return `${value}:00Z`
}

interface AuditReportingWorkspaceProps {
  roles: IdentityRole[]
  userId: string
}

export function AuditReportingWorkspace({
  roles,
  userId,
}: AuditReportingWorkspaceProps) {
  const initial = initialWindow()
  const [from, setFrom] =
    useState(initial.from)
  const [to, setTo] =
    useState(initial.to)
  const [window, setWindow] =
    useState<ReportWindow>({
      from: toInstant(initial.from),
      to: toInstant(initial.to),
    })
  const [windowError, setWindowError] =
    useState<string | null>(null)

  function applyWindow(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    const fromInstant = toInstant(from)
    const toInstantValue = toInstant(to)
    const duration =
      Date.parse(toInstantValue) -
      Date.parse(fromInstant)

    if (
      !Number.isFinite(duration) ||
      duration <= 0 ||
      duration >
        31 * dayMilliseconds
    ) {
      setWindowError(
        'Choose a UTC window where From is earlier than To and the duration is no more than 31 days.',
      )
      return
    }

    setWindowError(null)
    setWindow({
      from: fromInstant,
      to: toInstantValue,
    })
  }

  const windowKey =
    `${window.from}:${window.to}`

  return (
    <>
      <section
        aria-labelledby="reporting-window-title"
        className="workspace-card workspace-card--primary reporting-window"
        id="audit-reporting"
      >
        <div className="workspace-card__heading">
          <div>
            <p className="workspace-card__label">
              Audit and reporting
            </p>
            <h4 id="reporting-window-title">
              Bounded UTC window
            </h4>
          </div>
          <span className="workspace-status-pill">
            Maximum 31 days
          </span>
        </div>

        <p>
          Apply one half-open UTC window to audit
          search, operational summaries and report
          exports.
        </p>

        <form
          className="report-window-form"
          onSubmit={applyWindow}
        >
          <div className="form-field">
            <label htmlFor="report-window-from">
              From (UTC)
            </label>
            <input
              id="report-window-from"
              onChange={(event) => {
                setFrom(event.target.value)
              }}
              required
              type="datetime-local"
              value={from}
            />
          </div>

          <div className="form-field">
            <label htmlFor="report-window-to">
              To (UTC)
            </label>
            <input
              id="report-window-to"
              onChange={(event) => {
                setTo(event.target.value)
              }}
              required
              type="datetime-local"
              value={to}
            />
          </div>

          <button
            className="primary-button"
            type="submit"
          >
            Apply UTC window
          </button>
        </form>

        {windowError !== null && (
          <p
            className="form-field__error"
            role="alert"
          >
            {windowError}
          </p>
        )}
      </section>

      <AuditSearchPanel
        key={`audit:${windowKey}`}
        userId={userId}
        window={window}
      />

      <OperationalSummaryPanel
        key={`summary:${windowKey}`}
        userId={userId}
        window={window}
      />

      <ReportDownloadsPanel
        roles={roles}
        window={window}
      />
    </>
  )
}
