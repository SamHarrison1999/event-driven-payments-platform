import {
  type FormEvent,
  useState,
} from 'react'

import type { ReportWindow } from '../api/getOperationalSummary'
import {
  auditCategories,
  auditSources,
  type AuditCategory,
  type AuditEvent,
  type AuditSearchParameters,
  type AuditSource,
} from '../api/auditEvent'
import { useAuditEvents } from '../hooks/useAuditEvents'

const eventTypes = [
  'customer.created',
  'customer.status-changed',
  'customer.identity-assigned',
  'account.created',
  'account.status-changed',
  'payment.submitted',
  'payment.completed',
  'payment.rejected',
  'payment.failed',
  'settlement.import-accepted',
  'identity.role-granted',
  'identity.role-revoked',
  'outbox.dead-letter-replayed',
  'reconciliation.discrepancy-resolved',
] as const

const timestampFormatter =
  new Intl.DateTimeFormat(
    'en-GB',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'UTC',
    },
  )

interface AuditFilterDraft {
  category: '' | AuditCategory
  eventType: string
  actorIdentityUserId: string
  subjectType: string
  subjectIdentifier: string
  correlationIdentifier: string
  source: '' | AuditSource
}

const emptyFilters: AuditFilterDraft = {
  category: '',
  eventType: '',
  actorIdentityUserId: '',
  subjectType: '',
  subjectIdentifier: '',
  correlationIdentifier: '',
  source: '',
}

function optional(
  value: string,
): string | undefined {
  const trimmed = value.trim()

  return trimmed.length === 0
    ? undefined
    : trimmed
}

function buildSearch(
  window: ReportWindow,
  filters: AuditFilterDraft,
  cursor?: string,
): AuditSearchParameters {
  return {
    from: window.from,
    to: window.to,
    category:
      filters.category || undefined,
    eventType:
      optional(filters.eventType),
    actorIdentityUserId:
      optional(
        filters.actorIdentityUserId,
      ),
    subjectType:
      optional(filters.subjectType),
    subjectIdentifier:
      optional(
        filters.subjectIdentifier,
      ),
    correlationIdentifier:
      optional(
        filters.correlationIdentifier,
      ),
    source: filters.source || undefined,
    cursor,
    limit: 25,
  }
}

function label(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map(
      (part) =>
        part.charAt(0).toUpperCase() +
        part.slice(1),
    )
    .join(' ')
}

function detailValue(
  value:
    AuditEvent['details'][string],
): string {
  if (value === null) {
    return 'None'
  }

  return value.toString()
}

function AuditEventCard({
  event,
}: {
  event: AuditEvent
}) {
  const details =
    Object.entries(event.details)

  return (
    <li>
      <article className="audit-event-card">
        <header>
          <div>
            <p className="workspace-card__label">
              {label(event.category)}
            </p>
            <h5>{event.eventType}</h5>
          </div>

          <span className="workspace-status-pill">
            {label(event.source)}
          </span>
        </header>

        <dl className="audit-event-metadata">
          <div>
            <dt>Occurred (UTC)</dt>
            <dd>
              <time dateTime={event.occurredAt}>
                {timestampFormatter.format(
                  new Date(event.occurredAt),
                )}
              </time>
            </dd>
          </div>
          <div>
            <dt>Subject</dt>
            <dd>
              {event.subjectType}:{' '}
              {event.subjectIdentifier}
            </dd>
          </div>
          <div>
            <dt>Actor</dt>
            <dd>
              {event.actorIdentityUserId ??
                event.actorKind}
            </dd>
          </div>
          <div>
            <dt>Event identifier</dt>
            <dd>{event.eventId}</dd>
          </div>
        </dl>

        {details.length > 0 && (
          <dl className="audit-event-details">
            {details.map(([name, value]) => (
              <div key={name}>
                <dt>{label(name)}</dt>
                <dd>{detailValue(value)}</dd>
              </div>
            ))}
          </dl>
        )}
      </article>
    </li>
  )
}

interface AuditSearchPanelProps {
  userId: string
  window: ReportWindow
}

export function AuditSearchPanel({
  userId,
  window,
}: AuditSearchPanelProps) {
  const [draft, setDraft] =
    useState(emptyFilters)
  const [applied, setApplied] =
    useState(emptyFilters)
  const [cursor, setCursor] =
    useState<string | undefined>()
  const [previousCursors, setPreviousCursors] =
    useState<(string | undefined)[]>([])
  const [filterError, setFilterError] =
    useState<string | null>(null)
  const search = buildSearch(
    window,
    applied,
    cursor,
  )
  const auditQuery =
    useAuditEvents(userId, search)

  function updateFilter(
    name: keyof AuditFilterDraft,
    value: string,
  ) {
    setDraft((current) => ({
      ...current,
      [name]: value,
    }))
  }

  function submit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (
      (
        draft.subjectType.trim().length ===
        0
      ) !==
      (
        draft.subjectIdentifier
          .trim().length === 0
      )
    ) {
      setFilterError(
        'Subject type and subject identifier must be entered together.',
      )
      return
    }

    setFilterError(null)
    setApplied(draft)
    setCursor(undefined)
    setPreviousCursors([])
  }

  return (
    <section
      aria-labelledby="audit-search-title"
      className="workspace-card workspace-card--primary reporting-panel"
      id="audit-search"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Immutable evidence
          </p>
          <h4 id="audit-search-title">
            Audit event search
          </h4>
        </div>

        <span className="workspace-status-pill">
          {auditQuery.isSuccess
            ? `${auditQuery.data.events.length} events`
            : 'Bounded search'}
        </span>
      </div>

      <p>
        Search the evidence categories permitted
        by your server-provided role. Results are
        ordered newest first.
      </p>

      <form
        className="audit-filter-form"
        onSubmit={submit}
      >
        <div className="form-field">
          <label htmlFor="audit-category">
            Category
          </label>
          <select
            id="audit-category"
            onChange={(event) => {
              updateFilter(
                'category',
                event.target.value,
              )
            }}
            value={draft.category}
          >
            <option value="">All permitted</option>
            {auditCategories.map(
              (category) => (
                <option
                  key={category}
                  value={category}
                >
                  {label(category)}
                </option>
              ),
            )}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="audit-event-type">
            Event type
          </label>
          <select
            id="audit-event-type"
            onChange={(event) => {
              updateFilter(
                'eventType',
                event.target.value,
              )
            }}
            value={draft.eventType}
          >
            <option value="">All permitted</option>
            {eventTypes.map((eventType) => (
              <option
                key={eventType}
                value={eventType}
              >
                {eventType}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="audit-source">
            Evidence source
          </label>
          <select
            id="audit-source"
            onChange={(event) => {
              updateFilter(
                'source',
                event.target.value,
              )
            }}
            value={draft.source}
          >
            <option value="">All permitted</option>
            {auditSources.map((source) => (
              <option
                key={source}
                value={source}
              >
                {label(source)}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="audit-actor">
            Actor identifier
          </label>
          <input
            id="audit-actor"
            onChange={(event) => {
              updateFilter(
                'actorIdentityUserId',
                event.target.value,
              )
            }}
            placeholder="UUID (optional)"
            value={
              draft.actorIdentityUserId
            }
          />
        </div>

        <div className="form-field">
          <label htmlFor="audit-subject-type">
            Subject type
          </label>
          <input
            id="audit-subject-type"
            onChange={(event) => {
              updateFilter(
                'subjectType',
                event.target.value,
              )
            }}
            placeholder="payment"
            value={draft.subjectType}
          />
        </div>

        <div className="form-field">
          <label htmlFor="audit-subject-id">
            Subject identifier
          </label>
          <input
            id="audit-subject-id"
            onChange={(event) => {
              updateFilter(
                'subjectIdentifier',
                event.target.value,
              )
            }}
            value={draft.subjectIdentifier}
          />
        </div>

        <div className="form-field">
          <label htmlFor="audit-correlation">
            Correlation identifier
          </label>
          <input
            id="audit-correlation"
            onChange={(event) => {
              updateFilter(
                'correlationIdentifier',
                event.target.value,
              )
            }}
            value={
              draft.correlationIdentifier
            }
          />
        </div>

        <button
          className="primary-button"
          type="submit"
        >
          Apply audit filters
        </button>
      </form>

      {filterError !== null && (
        <p
          className="form-field__error"
          role="alert"
        >
          {filterError}
        </p>
      )}

      {auditQuery.isPending && (
        <div
          className="reporting-message"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />
          <p>Loading role-scoped evidence…</p>
        </div>
      )}

      {auditQuery.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Audit search unavailable
            </strong>
            <p>
              The bounded evidence search could
              not be completed safely.
            </p>
          </div>
          <button
            className="secondary-button"
            onClick={() => {
              void auditQuery.refetch()
            }}
            type="button"
          >
            Try again
          </button>
        </div>
      )}

      {auditQuery.isSuccess &&
        auditQuery.data.events.length === 0 && (
          <div className="reporting-empty-state">
            <strong>No audit events found</strong>
            <p>
              No permitted evidence matched this
              time window and filter set.
            </p>
          </div>
        )}

      {auditQuery.isSuccess &&
        auditQuery.data.events.length > 0 && (
          <>
            <ol
              aria-label="Audit events"
              className="audit-event-list"
            >
              {auditQuery.data.events.map(
                (auditEvent) => (
                  <AuditEventCard
                    event={auditEvent}
                    key={auditEvent.eventId}
                  />
                ),
              )}
            </ol>

            <div className="report-pagination">
              <button
                className="secondary-button"
                disabled={
                  previousCursors.length === 0 ||
                  auditQuery.isFetching
                }
                onClick={() => {
                  const history =
                    previousCursors.slice()
                  const previous =
                    history.pop()
                  setPreviousCursors(history)
                  setCursor(previous)
                }}
                type="button"
              >
                Previous page
              </button>

              <button
                className="secondary-button"
                disabled={
                  auditQuery.data.nextCursor ===
                    null ||
                  auditQuery.isFetching
                }
                onClick={() => {
                  setPreviousCursors(
                    (history) => [
                      ...history,
                      cursor,
                    ],
                  )
                  setCursor(
                    auditQuery.data.nextCursor ??
                      undefined,
                  )
                }}
                type="button"
              >
                Next page
              </button>
            </div>
          </>
        )}
    </section>
  )
}
