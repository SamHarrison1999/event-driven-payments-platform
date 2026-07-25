import { formatGbpMinorUnits } from '../../../shared/money/gbp'
import type { ReportWindow } from '../api/getOperationalSummary'
import { useOperationalSummary } from '../hooks/useOperationalSummary'

function Metric({
  label,
  value,
}: {
  label: string
  value: string | number
}) {
  return (
    <div className="report-metric">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function CountMap({
  counts,
  title,
}: {
  counts: Record<string, number>
  title: string
}) {
  return (
    <div className="report-count-group">
      <strong>{title}</strong>
      <dl>
        {Object.entries(counts).map(
          ([name, count]) => (
            <Metric
              key={name}
              label={name}
              value={count}
            />
          ),
        )}
      </dl>
    </div>
  )
}

interface OperationalSummaryPanelProps {
  userId: string
  window: ReportWindow
}

export function OperationalSummaryPanel({
  userId,
  window,
}: OperationalSummaryPanelProps) {
  const summaryQuery =
    useOperationalSummary(userId, window)

  return (
    <section
      aria-labelledby="operational-summary-title"
      className="workspace-card workspace-card--primary reporting-panel"
      id="operational-summary"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Repeatable-read snapshot
          </p>
          <h4 id="operational-summary-title">
            Operational summary
          </h4>
        </div>

        <span className="workspace-status-pill">
          Exact integers
        </span>
      </div>

      <p>
        Each visible section is computed from
        authoritative tables in one consistent
        PostgreSQL snapshot.
      </p>

      {summaryQuery.isPending && (
        <div
          className="reporting-message"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />
          <p>Computing the bounded summary…</p>
        </div>
      )}

      {summaryQuery.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Summary unavailable
            </strong>
            <p>
              The operational snapshot could not
              be retrieved safely.
            </p>
          </div>
          <button
            className="secondary-button"
            onClick={() => {
              void summaryQuery.refetch()
            }}
            type="button"
          >
            Try again
          </button>
        </div>
      )}

      {summaryQuery.isSuccess && (
        <div className="operational-summary-grid">
          {summaryQuery.data.payment !==
            null && (
            <article className="report-summary-card">
              <h5>Payment operations</h5>
              <dl>
                <Metric
                  label="Submitted"
                  value={
                    summaryQuery.data.payment
                      .submittedCount
                  }
                />
                <Metric
                  label="Terminal"
                  value={
                    summaryQuery.data.payment
                      .terminalCount
                  }
                />
                <Metric
                  label="Completed"
                  value={
                    summaryQuery.data.payment
                      .completedCount
                  }
                />
                <Metric
                  label="Rejected"
                  value={
                    summaryQuery.data.payment
                      .rejectedCount
                  }
                />
                <Metric
                  label="Failed"
                  value={
                    summaryQuery.data.payment
                      .failedCount
                  }
                />
                <Metric
                  label="Completed total"
                  value={formatGbpMinorUnits(
                    summaryQuery.data.payment
                      .completedAmountMinorUnits,
                  )}
                />
              </dl>
              <CountMap
                counts={
                  summaryQuery.data.payment
                    .rejectionCodeCounts
                }
                title="Rejection codes"
              />
              <CountMap
                counts={
                  summaryQuery.data.payment
                    .failureCodeCounts
                }
                title="Failure codes"
              />
            </article>
          )}

          {summaryQuery.data.settlement !==
            null && (
            <article className="report-summary-card">
              <h5>Settlement imports</h5>
              <dl>
                <Metric
                  label="Accepted imports"
                  value={
                    summaryQuery.data.settlement
                      .acceptedImportCount
                  }
                />
                <Metric
                  label="Accepted rows"
                  value={
                    summaryQuery.data.settlement
                      .acceptedRowCount
                  }
                />
                <Metric
                  label="Matched"
                  value={
                    summaryQuery.data.settlement
                      .matchedCount
                  }
                />
                <Metric
                  label="Discrepancies"
                  value={
                    summaryQuery.data.settlement
                      .discrepancyCount
                  }
                />
              </dl>
              <CountMap
                counts={
                  summaryQuery.data.settlement
                    .importOutcomeCounts
                }
                title="Import outcomes"
              />
            </article>
          )}

          {summaryQuery.data.reconciliation !==
            null && (
            <article className="report-summary-card">
              <h5>Reconciliation review</h5>
              <CountMap
                counts={
                  summaryQuery.data.reconciliation
                    .discrepancyCodeCounts
                }
                title="Discrepancy codes"
              />
              <CountMap
                counts={
                  summaryQuery.data.reconciliation
                    .lifecycleStateCounts
                }
                title="Lifecycle states"
              />
              <CountMap
                counts={
                  summaryQuery.data.reconciliation
                    .resolutionDecisionCounts
                }
                title="Resolution decisions"
              />
              <CountMap
                counts={
                  summaryQuery.data.reconciliation
                    .openAgeBandCounts
                }
                title="Open age bands"
              />
            </article>
          )}
        </div>
      )}
    </section>
  )
}
