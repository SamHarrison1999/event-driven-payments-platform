import {
  type FormEvent,
  useState,
} from 'react'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { formatGbpMinorUnits } from '../../../shared/money/gbp'
import {
  settlementDiscrepancyStatuses,
  settlementResolutionDecisions,
  type SettlementDiscrepancy,
  type SettlementDiscrepancyStatus,
  type SettlementResolutionDecision,
} from '../api/settlement'
import { useResolveSettlementDiscrepancy } from '../hooks/useResolveSettlementDiscrepancy'
import { useSettlementDiscrepancies } from '../hooks/useSettlementDiscrepancies'
import { useSettlementDiscrepancy } from '../hooks/useSettlementDiscrepancy'
import { reconciliationErrorMessage } from './reconciliationErrorMessage'

const timestampFormatter =
  new Intl.DateTimeFormat(
    'en-GB',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'UTC',
    },
  )

const decisionLabels: Record<
  SettlementResolutionDecision,
  string
> = {
  ACCEPTED: 'Accept external settlement',
  INTERNAL_CORRECTION_REQUIRED:
    'Internal correction required',
  EXTERNAL_CORRECTION_REQUIRED:
    'External correction required',
}

function reference(
  discrepancyId: string,
): string {
  return discrepancyId
    .slice(-8)
    .toUpperCase()
}

function timestamp(value: string): string {
  return timestampFormatter.format(
    new Date(value),
  )
}

interface ResolutionFormProps {
  discrepancy: SettlementDiscrepancy
  etag: string
  userId: string
}

function ResolutionForm({
  discrepancy,
  etag,
  userId,
}: ResolutionFormProps) {
  const [decision, setDecision] =
    useState<SettlementResolutionDecision>(
      'ACCEPTED',
    )
  const [reason, setReason] = useState('')
  const resolutionMutation =
    useResolveSettlementDiscrepancy(userId)
  const trimmedReason = reason.trim()
  const containsControlCharacter =
    Array.from(trimmedReason).some(
      (character) => {
        const codePoint =
          character.codePointAt(0) ?? 0

        return (
          codePoint < 32 ||
          codePoint === 127
        )
      },
    )
  const reasonIsValid =
    trimmedReason.length > 0 &&
    trimmedReason.length <= 500 &&
    !containsControlCharacter

  function submit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (
      !reasonIsValid ||
      resolutionMutation.isPending
    ) {
      return
    }

    resolutionMutation.mutate({
      discrepancyId:
        discrepancy.discrepancyId,
      etag,
      decision,
      reason: trimmedReason,
    })
  }

  let errorMessage =
    reconciliationErrorMessage(
      resolutionMutation.error,
      'The discrepancy could not be resolved safely.',
    )

  if (
    isApiErrorWithStatus(
      resolutionMutation.error,
      412,
    )
  ) {
    errorMessage =
      'This discrepancy changed after it was loaded. The latest version is being restored; review it before trying again.'
  } else if (
    isApiErrorWithStatus(
      resolutionMutation.error,
      409,
    )
  ) {
    errorMessage =
      'This discrepancy has already been resolved. The queue is being refreshed.'
  }

  return (
    <form
      className="settlement-resolution-form"
      onSubmit={submit}
    >
      <div className="form-field">
        <label htmlFor="resolution-decision">
          Resolution decision
        </label>

        <select
          disabled={resolutionMutation.isPending}
          id="resolution-decision"
          onChange={(event) => {
            setDecision(
              event.target
                .value as SettlementResolutionDecision,
            )
            resolutionMutation.reset()
          }}
          value={decision}
        >
          {settlementResolutionDecisions.map(
            (candidate) => (
              <option
                key={candidate}
                value={candidate}
              >
                {decisionLabels[candidate]}
              </option>
            ),
          )}
        </select>
      </div>

      <div className="form-field">
        <label htmlFor="resolution-reason">
          Resolution reason
        </label>

        <textarea
          aria-describedby="resolution-reason-hint"
          disabled={resolutionMutation.isPending}
          id="resolution-reason"
          maxLength={500}
          onChange={(event) => {
            setReason(event.target.value)
            resolutionMutation.reset()
          }}
          required
          rows={4}
          value={reason}
        />

        <p
          className="form-field__hint"
          id="resolution-reason-hint"
        >
          Enter one single-line reason. The actor,
          decision, reason and timestamp become
          immutable evidence.
        </p>
      </div>

      {resolutionMutation.isError && (
        <div
          className="form-error-summary"
          role="alert"
        >
          <strong>
            Discrepancy not resolved
          </strong>
          <p>{errorMessage}</p>
        </div>
      )}

      {resolutionMutation.isSuccess && (
        <div
          className="settlement-success"
          role="status"
        >
          <strong>Discrepancy resolved</strong>
          <p>
            The attributed resolution evidence was
            stored successfully.
          </p>
        </div>
      )}

      <button
        className="primary-button"
        disabled={
          !reasonIsValid ||
          resolutionMutation.isPending
        }
        type="submit"
      >
        {resolutionMutation.isPending
          ? 'Recording resolution…'
          : 'Resolve discrepancy'}
      </button>
    </form>
  )
}

interface DiscrepancyDetailProps {
  discrepancyId: string
  userId: string
}

function DiscrepancyDetail({
  discrepancyId,
  userId,
}: DiscrepancyDetailProps) {
  const detailQuery =
    useSettlementDiscrepancy(
      userId,
      discrepancyId,
    )

  if (detailQuery.isPending) {
    return (
      <div
        className="settlement-message"
        role="status"
      >
        <span
          aria-hidden="true"
          className="status-spinner"
        />
        <p>Loading discrepancy evidence…</p>
      </div>
    )
  }

  if (detailQuery.isError) {
    return (
      <div
        className="status-message status-message--error"
        role="alert"
      >
        <div>
          <strong>
            Discrepancy unavailable
          </strong>
          <p>
            The selected discrepancy could not be
            retrieved safely.
          </p>
        </div>

        <button
          className="secondary-button"
          onClick={() => {
            void detailQuery.refetch()
          }}
          type="button"
        >
          Try again
        </button>
      </div>
    )
  }

  const { discrepancy, etag } =
    detailQuery.data

  return (
    <article
      aria-labelledby="discrepancy-detail-title"
      className="settlement-discrepancy-detail"
    >
      <header>
        <div>
          <p className="workspace-card__label">
            Selected discrepancy
          </p>
          <h5 id="discrepancy-detail-title">
            {discrepancy.code}
          </h5>
          <span>
            Reference{' '}
            {reference(
              discrepancy.discrepancyId,
            )}
          </span>
        </div>

        <span className="workspace-status-pill">
          {discrepancy.status}
        </span>
      </header>

      <dl className="settlement-detail-metadata">
        <div>
          <dt>External record</dt>
          <dd>
            {discrepancy.settlementRecordId}
          </dd>
        </div>
        <div>
          <dt>Payment</dt>
          <dd>{discrepancy.paymentId}</dd>
        </div>
        <div>
          <dt>Amount</dt>
          <dd>
            {formatGbpMinorUnits(
              discrepancy.amountMinorUnits,
            )}
          </dd>
        </div>
        <div>
          <dt>Settled (UTC)</dt>
          <dd>
            <time
              dateTime={discrepancy.settledAt}
            >
              {timestamp(discrepancy.settledAt)}
            </time>
          </dd>
        </div>
      </dl>

      {discrepancy.status === 'OPEN' && (
        <ResolutionForm
          discrepancy={discrepancy}
          etag={etag}
          userId={userId}
        />
      )}

      {discrepancy.resolution !== null && (
        <div className="settlement-resolution-evidence">
          <strong>
            {decisionLabels[
              discrepancy.resolution.decision
            ]}
          </strong>
          <p>{discrepancy.resolution.reason}</p>
          <small>
            Recorded{' '}
            {timestamp(
              discrepancy.resolution.decidedAt,
            )}{' '}
            UTC
          </small>
        </div>
      )}
    </article>
  )
}

interface SettlementDiscrepancyPanelProps {
  userId: string
}

export function SettlementDiscrepancyPanel({
  userId,
}: SettlementDiscrepancyPanelProps) {
  const [status, setStatus] =
    useState<SettlementDiscrepancyStatus>(
      'OPEN',
    )
  const [selectedId, setSelectedId] =
    useState<string | null>(null)
  const queueQuery =
    useSettlementDiscrepancies(
      userId,
      status,
    )
  const discrepancies =
    queueQuery.data?.pages.flatMap(
      (page) => page.discrepancies,
    ) ?? []

  return (
    <section
      aria-labelledby="settlement-discrepancies-title"
      className="workspace-card workspace-card--primary settlement-panel"
      id="settlement-discrepancies"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Analyst review
          </p>
          <h4 id="settlement-discrepancies-title">
            Settlement discrepancies
          </h4>
        </div>

        <span className="workspace-status-pill">
          {queueQuery.isSuccess
            ? `${discrepancies.length} loaded`
            : 'Loading'}
        </span>
      </div>

      <p>
        Review deterministic mismatches and record
        one immutable resolution without changing
        payment or ledger history.
      </p>

      <div
        aria-label="Discrepancy status"
        className="settlement-status-tabs"
        role="group"
      >
        {settlementDiscrepancyStatuses.map(
          (candidate) => (
            <button
              aria-pressed={
                status === candidate
              }
              className={
                status === candidate
                  ? 'settlement-status-tab settlement-status-tab--active'
                  : 'settlement-status-tab'
              }
              key={candidate}
              onClick={() => {
                setStatus(candidate)
                setSelectedId(null)
              }}
              type="button"
            >
              {candidate === 'OPEN'
                ? 'Open'
                : 'Resolved'}
            </button>
          ),
        )}
      </div>

      {queueQuery.isPending && (
        <div
          className="settlement-message"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />
          <p>Loading the bounded review queue…</p>
        </div>
      )}

      {queueQuery.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Discrepancy queue unavailable
            </strong>
            <p>
              The analyst queue could not be
              retrieved safely.
            </p>
          </div>
          <button
            className="secondary-button"
            onClick={() => {
              void queueQuery.refetch()
            }}
            type="button"
          >
            Try again
          </button>
        </div>
      )}

      {queueQuery.isSuccess &&
        discrepancies.length === 0 && (
          <div className="settlement-empty-state">
            <span aria-hidden="true">
              {status === 'OPEN' ? 'OK' : '—'}
            </span>
            <div>
              <strong>
                No {status.toLowerCase()}{' '}
                discrepancies
              </strong>
              <p>
                This bounded queue currently has no
                records to review.
              </p>
            </div>
          </div>
        )}

      {discrepancies.length > 0 && (
        <div className="settlement-discrepancy-layout">
          <div>
            <ul
              aria-label={`${status.toLowerCase()} settlement discrepancies`}
              className="settlement-discrepancy-list"
            >
              {discrepancies.map(
                (discrepancy) => (
                  <li
                    key={
                      discrepancy.discrepancyId
                    }
                  >
                    <button
                      aria-pressed={
                        selectedId ===
                        discrepancy.discrepancyId
                      }
                      onClick={() => {
                        setSelectedId(
                          discrepancy.discrepancyId,
                        )
                      }}
                      type="button"
                    >
                      <strong>
                        {discrepancy.code}
                      </strong>
                      <span>
                        Row {discrepancy.rowNumber}
                        {' · '}
                        {reference(
                          discrepancy.discrepancyId,
                        )}
                      </span>
                    </button>
                  </li>
                ),
              )}
            </ul>

            {queueQuery.hasNextPage && (
              <button
                className="secondary-button"
                disabled={
                  queueQuery.isFetchingNextPage
                }
                onClick={() => {
                  void queueQuery.fetchNextPage()
                }}
                type="button"
              >
                {queueQuery.isFetchingNextPage
                  ? 'Loading more…'
                  : 'Load more discrepancies'}
              </button>
            )}
          </div>

          <div>
            {selectedId === null ? (
              <div className="settlement-selection-prompt">
                <strong>
                  Select a discrepancy
                </strong>
                <p>
                  Choose a queue entry to inspect its
                  immutable settlement evidence.
                </p>
              </div>
            ) : (
              <DiscrepancyDetail
                discrepancyId={selectedId}
                userId={userId}
              />
            )}
          </div>
        </div>
      )}
    </section>
  )
}
