import {
  type FormEvent,
  useState,
} from 'react'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import type { OutboxDeadLetter } from '../api/deadLetter'
import type { ReplayDeadLetterInput } from '../api/replayDeadLetter'
import { useDeadLetters } from '../hooks/useDeadLetters'
import { useReplayDeadLetter } from '../hooks/useReplayDeadLetter'

const eventDateFormatter =
  new Intl.DateTimeFormat(
    'en-GB',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'UTC',
    },
  )

function formatTimestamp(
  timestamp: string,
): string {
  return eventDateFormatter.format(
    new Date(timestamp),
  )
}

function eventReference(
  eventId: string,
): string {
  return eventId.slice(-8).toUpperCase()
}

interface DeadLetterCardProps {
  event: OutboxDeadLetter
  isReplaying: boolean
  replayError: unknown
  onReplay: (
    input: ReplayDeadLetterInput,
  ) => void
}

function DeadLetterCard({
  event,
  isReplaying,
  replayError,
  onReplay,
}: DeadLetterCardProps) {
  const [reason, setReason] = useState('')
  const titleId =
    `dead-letter-${event.eventId}-title`
  const reasonId =
    `dead-letter-${event.eventId}-reason`
  const trimmedReason = reason.trim()
  const reasonIsValid =
    trimmedReason.length > 0 &&
    trimmedReason.length <= 500

  function submitReplay(
    formEvent: FormEvent<HTMLFormElement>,
  ) {
    formEvent.preventDefault()

    if (!reasonIsValid || isReplaying) {
      return
    }

    onReplay({
      eventId: event.eventId,
      reason: trimmedReason,
      expectedVersion: event.version,
    })
  }

  let replayErrorMessage =
    'The event could not be queued for replay.'

  if (
    isApiErrorWithStatus(replayError, 409)
  ) {
    replayErrorMessage =
      'The event changed before replay. Refresh the list and try again.'
  }

  return (
    <li className="dead-letter-list__item">
      <article
        aria-labelledby={titleId}
        className="dead-letter-card"
      >
        <header className="dead-letter-card__header">
          <div>
            <p className="dead-letter-card__label">
              Dead-letter event
            </p>

            <h5 id={titleId}>
              {event.eventType}
            </h5>

            <span>
              Reference {eventReference(
                event.eventId,
              )}
            </span>
          </div>

          <span className="dead-letter-status">
            Recovery required
          </span>
        </header>

        <dl className="dead-letter-metadata">
          <div>
            <dt>Aggregate</dt>
            <dd>
              {event.aggregateType} /{' '}
              {event.aggregateId}
            </dd>
          </div>

          <div>
            <dt>Attempts</dt>
            <dd>{event.attemptCount}</dd>
          </div>

          <div>
            <dt>Last failure (UTC)</dt>
            <dd>
              <time dateTime={event.updatedAt}>
                {formatTimestamp(
                  event.updatedAt,
                )}
              </time>
            </dd>
          </div>

          <div>
            <dt>Error category</dt>
            <dd>
              {event.lastErrorCategory ??
                'Unavailable'}
            </dd>
          </div>

          <div>
            <dt>Error message</dt>
            <dd>
              {event.lastErrorMessage ??
                'Unavailable'}
            </dd>
          </div>

          <div>
            <dt>Replay count</dt>
            <dd>{event.replayCount}</dd>
          </div>
        </dl>

        <details className="dead-letter-payload">
          <summary>Inspect immutable payload</summary>
          <pre>{event.payload}</pre>
        </details>

        <form
          className="dead-letter-replay-form"
          onSubmit={submitReplay}
        >
          <div className="form-field">
            <label htmlFor={reasonId}>
              Replay reason for event{' '}
              {eventReference(event.eventId)}
            </label>

            <textarea
              aria-describedby={
                `${reasonId}-hint`
              }
              disabled={isReplaying}
              id={reasonId}
              maxLength={500}
              onChange={(changeEvent) => {
                setReason(
                  changeEvent.target.value,
                )
              }}
              required
              rows={3}
              value={reason}
            />

            <p
              className="form-field__hint"
              id={`${reasonId}-hint`}
            >
              Explain the operational correction.
              The reason is stored in immutable audit
              evidence.
            </p>
          </div>

          {replayError !== null && (
            <div
              className="form-error-summary"
              role="alert"
            >
              <strong>Replay not queued</strong>
              <p>{replayErrorMessage}</p>
            </div>
          )}

          <button
            className="primary-button"
            disabled={
              !reasonIsValid || isReplaying
            }
            type="submit"
          >
            {isReplaying
              ? 'Queuing replay…'
              : 'Replay event'}
          </button>
        </form>
      </article>
    </li>
  )
}

export function DeadLetterPanel() {
  const deadLettersQuery = useDeadLetters()
  const replayMutation =
    useReplayDeadLetter()

  let statusText = 'Loading'

  if (deadLettersQuery.isError) {
    statusText = 'Unavailable'
  } else if (deadLettersQuery.isSuccess) {
    statusText =
      deadLettersQuery.data.length === 1
        ? '1 event'
        : `${deadLettersQuery.data.length} events`
  }

  return (
    <section
      aria-labelledby="dead-letter-recovery-title"
      className="workspace-card workspace-card--primary dead-letter-panel"
      id="outbox-dead-letters"
    >
      <div className="workspace-card__heading">
        <div>
          <p className="workspace-card__label">
            Administrator operations
          </p>

          <h4 id="dead-letter-recovery-title">
            Outbox dead-letter recovery
          </h4>
        </div>

        <span
          aria-live="polite"
          className="workspace-status-pill"
        >
          {statusText}
        </span>
      </div>

      <p>
        Inspect bounded publication failures and
        replay one immutable event after recording
        an operational reason.
      </p>

      {replayMutation.isSuccess && (
        <div
          aria-live="polite"
          className="dead-letter-success"
          role="status"
        >
          <strong>Replay queued</strong>
          <p>
            Event{' '}
            {eventReference(
              replayMutation.data.event.eventId,
            )}{' '}
            returned to pending publication with
            audit evidence.
          </p>
        </div>
      )}

      {deadLettersQuery.isPending && (
        <div
          aria-live="polite"
          className="dead-letter-message"
          role="status"
        >
          <span
            aria-hidden="true"
            className="status-spinner"
          />

          <div>
            <strong>
              Loading dead-letter events
            </strong>

            <p>
              Retrieving the current bounded
              administrator recovery queue.
            </p>
          </div>
        </div>
      )}

      {deadLettersQuery.isError && (
        <div
          className="status-message status-message--error"
          role="alert"
        >
          <div>
            <strong>
              Dead-letter queue unavailable
            </strong>

            <p>
              Recovery events could not be
              retrieved safely.
            </p>
          </div>

          <button
            className="secondary-button"
            disabled={
              deadLettersQuery.isFetching
            }
            onClick={() => {
              void deadLettersQuery.refetch()
            }}
            type="button"
          >
            {deadLettersQuery.isFetching
              ? 'Trying again…'
              : 'Try again'}
          </button>
        </div>
      )}

      {deadLettersQuery.isSuccess &&
        deadLettersQuery.data.length === 0 && (
          <div className="dead-letter-empty-state">
            <span
              aria-hidden="true"
              className="dead-letter-empty-state__icon"
            >
              OK
            </span>

            <div>
              <strong>
                No dead-letter events
              </strong>

              <p>
                No outbox publication failures
                currently require administrator
                recovery.
              </p>
            </div>
          </div>
        )}

      {deadLettersQuery.isSuccess &&
        deadLettersQuery.data.length > 0 && (
          <ul
            aria-label="Outbox dead-letter events"
            className="dead-letter-list"
          >
            {deadLettersQuery.data.map(
              (event) => {
                const isCurrentReplay =
                  replayMutation.variables
                    ?.eventId === event.eventId

                return (
                  <DeadLetterCard
                    event={event}
                    isReplaying={
                      isCurrentReplay &&
                      replayMutation.isPending
                    }
                    key={event.eventId}
                    onReplay={(input) => {
                      replayMutation.mutate(input)
                    }}
                    replayError={
                      isCurrentReplay &&
                      replayMutation.isError
                        ? replayMutation.error
                        : null
                    }
                  />
                )
              },
            )}
          </ul>
        )}
    </section>
  )
}
